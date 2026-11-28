package com.example.ui

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Comment
import com.example.model.RepositoryManager
import com.example.utils.SoundManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class AudioSeat(
    val seatIndex: Int,
    val userId: String? = null,
    val username: String = "",
    val avatarUrl: String = "",
    val isMuted: Boolean = false,
    val isTalking: Boolean = false,
    val isHost: Boolean = false
)

data class AudioRoomModel(
    val roomId: String = "",
    val hostUid: String = "",
    val hostUsername: String = "Host",
    val title: String = "Live Audio Party",
    val status: String = "ACTIVE",
    val speakers: List<String> = emptyList(),
    val mutedSpeakers: List<String> = emptyList(),
    val pendingRequests: List<String> = emptyList(),
    val listenerCount: Int = 1
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeLiveAudioRoomScreen(
    roomId: String,
    roomTitle: String,
    isHost: Boolean,
    onLeaveRoom: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = RepositoryManager.getCurrentUserId()
    var currentUsername by remember { mutableStateOf("User") }
    var currentUserAvatar by remember { mutableStateOf("") }

    var roomData by remember { mutableStateOf(AudioRoomModel(roomId = roomId, title = roomTitle, hostUid = if (isHost) currentUserId else "")) }
    var seats by remember { mutableStateOf<List<AudioSeat>>(List(8) { AudioSeat(seatIndex = it) }) }
    var isMicMuted by remember { mutableStateOf(false) }
    var isUserTalking by remember { mutableStateOf(false) }
    var isUserOnStage by remember { mutableStateOf(isHost) }
    var isRequestPending by remember { mutableStateOf(false) }

    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var commentText by remember { mutableStateOf("") }
    var showGiftSheet by remember { mutableStateOf(false) }
    var showSpeakerRequestsSheet by remember { mutableStateOf(false) }
    var floatingEmojis by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }

    var hasMicPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (!granted) {
            Toast.makeText(context, "Microphone permission is required for speaking", Toast.LENGTH_SHORT).show()
        }
    }

    // Load current user profile
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            try {
                val doc = RepositoryManager.firestore.collection("users").document(currentUserId).get().await()
                currentUsername = doc.getString("username") ?: doc.getString("displayName") ?: "User"
                currentUserAvatar = doc.getString("profilePicUrl") ?: doc.getString("avatar_url") ?: ""
            } catch (ignored: Exception) {}
        }
    }

    // Real-time Audio Level Detection when user is on stage & unmuted
    LaunchedEffect(isUserOnStage, isMicMuted, hasMicPermission) {
        if (isUserOnStage && !isMicMuted && hasMicPermission) {
            scope.launch(Dispatchers.Default) {
                var audioRecord: AudioRecord? = null
                try {
                    val sampleRate = 44100
                    val channelConfig = AudioFormat.CHANNEL_IN_MONO
                    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                    val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )

                    if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                        audioRecord.startRecording()
                        val buffer = ShortArray(bufferSize)

                        while (isActive && isUserOnStage && !isMicMuted) {
                            val read = audioRecord.read(buffer, 0, bufferSize)
                            if (read > 0) {
                                var maxAmplitude = 0
                                for (i in 0 until read) {
                                    val abs = kotlin.math.abs(buffer[i].toInt())
                                    if (abs > maxAmplitude) maxAmplitude = abs
                                }
                                isUserTalking = maxAmplitude > 2500
                            }
                            delay(120)
                        }
                    }
                } catch (e: Exception) {
                    isUserTalking = false
                } finally {
                    try {
                        audioRecord?.stop()
                        audioRecord?.release()
                    } catch (ignored: Exception) {}
                    isUserTalking = false
                }
            }
        } else {
            isUserTalking = false
        }
    }

    // Real-time Firestore Audio Room Listener
    DisposableEffect(roomId) {
        val roomRef = RepositoryManager.firestore.collection("liveAudioRooms").document(roomId)

        // Initialize / Register room
        scope.launch {
            try {
                if (isHost) {
                    val initialData = hashMapOf(
                        "roomId" to roomId,
                        "hostUid" to currentUserId,
                        "hostUsername" to currentUsername,
                        "title" to roomTitle,
                        "status" to "ACTIVE",
                        "startedAt" to FieldValue.serverTimestamp(),
                        "listenerCount" to 1,
                        "speakers" to listOf(currentUserId),
                        "mutedSpeakers" to emptyList<String>(),
                        "pendingRequests" to emptyList<String>()
                    )
                    roomRef.set(initialData, SetOptions.merge())
                } else {
                    roomRef.update("listenerCount", FieldValue.increment(1))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val listenerRegistration: ListenerRegistration = roomRef.addSnapshotListener { snapshot, error ->
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data ?: return@addSnapshotListener
                val hostUid = data["hostUid"] as? String ?: ""
                val hostName = data["hostUsername"] as? String ?: "Host"
                val title = data["title"] as? String ?: roomTitle
                val status = data["status"] as? String ?: "ACTIVE"
                val speakersList = (data["speakers"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val mutedList = (data["mutedSpeakers"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val pendingList = (data["pendingRequests"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val count = (data["listenerCount"] as? Long)?.toInt() ?: 1

                roomData = AudioRoomModel(
                    roomId = roomId,
                    hostUid = hostUid,
                    hostUsername = hostName,
                    title = title,
                    status = status,
                    speakers = speakersList,
                    mutedSpeakers = mutedList,
                    pendingRequests = pendingList,
                    listenerCount = count
                )

                isUserOnStage = isHost || speakersList.contains(currentUserId)
                isRequestPending = pendingList.contains(currentUserId)

                // Populate 8 Stage Seats
                val updatedSeats = mutableListOf<AudioSeat>()
                // Seat 0: Host
                updatedSeats.add(
                    AudioSeat(
                        seatIndex = 0,
                        userId = hostUid,
                        username = hostName,
                        avatarUrl = if (hostUid == currentUserId) currentUserAvatar else "https://api.dicebear.com/7.x/avataaars/png?seed=$hostUid",
                        isMuted = mutedList.contains(hostUid),
                        isTalking = if (hostUid == currentUserId) isUserTalking else false,
                        isHost = true
                    )
                )

                // Seats 1 to 7: Guest Speakers
                val guestSpeakers = speakersList.filter { it != hostUid }
                for (i in 1..7) {
                    val speakerUid = guestSpeakers.getOrNull(i - 1)
                    if (speakerUid != null) {
                        updatedSeats.add(
                            AudioSeat(
                                seatIndex = i,
                                userId = speakerUid,
                                username = if (speakerUid == currentUserId) currentUsername else "Speaker $i",
                                avatarUrl = if (speakerUid == currentUserId) currentUserAvatar else "https://api.dicebear.com/7.x/avataaars/png?seed=$speakerUid",
                                isMuted = mutedList.contains(speakerUid),
                                isTalking = if (speakerUid == currentUserId) isUserTalking else false,
                                isHost = false
                            )
                        )
                    } else {
                        updatedSeats.add(AudioSeat(seatIndex = i))
                    }
                }
                seats = updatedSeats

                if (status == "ENDED" && !isHost) {
                    Toast.makeText(context, "Audio Room has ended", Toast.LENGTH_SHORT).show()
                    onLeaveRoom()
                }
            }
        }

        // Real-time Chat listener
        val chatRegistration = roomRef.collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .limit(50)
            .addSnapshotListener { chatSnapshot, _ ->
                if (chatSnapshot != null) {
                    comments = chatSnapshot.documents.mapNotNull { doc ->
                        Comment(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            username = doc.getString("username") ?: "User",
                            avatarUrl = doc.getString("avatarUrl") ?: "",
                            text = doc.getString("text") ?: ""
                        )
                    }
                }
            }

        onDispose {
            listenerRegistration.remove()
            chatRegistration.remove()
            if (isHost) {
                roomRef.update("status", "ENDED", "endedAt", FieldValue.serverTimestamp())
            } else {
                roomRef.update("listenerCount", FieldValue.increment(-1))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0F0C20),
                        Color(0xFF1B143A),
                        Color(0xFF0A0815)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- TOP BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onLeaveRoom,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "LIVE AUDIO STAGE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }
                        Text(
                            text = roomData.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Listener count badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Headphones, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${roomData.listenerCount.coerceAtLeast(1)}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Host Controls or Leave Room button
                    Button(
                        onClick = onLeaveRoom,
                        colors = ButtonDefaults.buttonColors(containerColor = if (isHost) Color(0xFFE53935) else Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            if (isHost) "End Stage" else "Leave ✌️",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- 8-SEAT AUDIO PODIUM STAGE ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Row 1: 4 Seats (Host + 3 Speakers)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (i in 0..3) {
                            AudioPodiumSeatItem(
                                seat = seats.getOrElse(i) { AudioSeat(seatIndex = i) },
                                isCurrentLoggedInUser = seats.getOrNull(i)?.userId == currentUserId,
                                onSeatClick = {
                                    if (seats.getOrNull(i)?.userId == null) {
                                        // Empty seat
                                        if (!isUserOnStage) {
                                            if (!hasMicPermission) {
                                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            } else {
                                                // Request seat or join
                                                scope.launch {
                                                    RepositoryManager.firestore.collection("liveAudioRooms")
                                                        .document(roomId)
                                                        .update("speakers", FieldValue.arrayUnion(currentUserId))
                                                    Toast.makeText(context, "Joined speaker seat!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Row 2: 4 Seats (Guest Speakers 4 to 7)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (i in 4..7) {
                            AudioPodiumSeatItem(
                                seat = seats.getOrElse(i) { AudioSeat(seatIndex = i) },
                                isCurrentLoggedInUser = seats.getOrNull(i)?.userId == currentUserId,
                                onSeatClick = {
                                    if (seats.getOrNull(i)?.userId == null && !isUserOnStage) {
                                        if (!hasMicPermission) {
                                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        } else {
                                            scope.launch {
                                                RepositoryManager.firestore.collection("liveAudioRooms")
                                                    .document(roomId)
                                                    .update("speakers", FieldValue.arrayUnion(currentUserId))
                                                Toast.makeText(context, "Joined speaker seat!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- REAL-TIME LIVE CHAT FEED ---
            val chatListState = rememberLazyListState()
            LaunchedEffect(comments.size) {
                if (comments.isNotEmpty()) {
                    chatListState.animateScrollToItem(comments.size - 1)
                }
            }

            LazyColumn(
                state = chatListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF6200EE).copy(alpha = 0.25f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "🎉 Welcome to ${roomData.title}! Tap an empty podium or 'Request Mic' to speak.",
                            color = Color(0xFFBB86FC),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                items(comments, key = { it.id }) { comment ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        AsyncImage(
                            model = comment.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${comment.userId}" },
                            contentDescription = null,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            comment.username,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            comment.text,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // --- FLOATING REACTION PARTICLES ---
            floatingEmojis.forEach { (id, emoji) ->
                var visible by remember { mutableStateOf(true) }
                val animProgress = remember { Animatable(0f) }
                LaunchedEffect(id) {
                    animProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 1500, easing = LinearEasing)
                    )
                    visible = false
                }
                if (visible) {
                    Text(
                        text = emoji,
                        fontSize = (24 + (animProgress.value * 8)).sp,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 24.dp)
                            .offset(y = (-150 * animProgress.value).dp)
                    )
                }
            }

            // --- BOTTOM CONTROLS DOCK ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141026))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Chat Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            placeholder = { Text("Say something in room...", color = Color.Gray, fontSize = 13.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (commentText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val textToSend = commentText.trim()
                                    commentText = ""
                                    scope.launch {
                                        try {
                                            RepositoryManager.firestore.collection("liveAudioRooms")
                                                .document(roomId)
                                                .collection("messages")
                                                .add(
                                                    hashMapOf(
                                                        "userId" to currentUserId,
                                                        "username" to currentUsername,
                                                        "avatarUrl" to currentUserAvatar,
                                                        "text" to textToSend,
                                                        "timestamp" to FieldValue.serverTimestamp()
                                                    )
                                                )
                                        } catch (ignored: Exception) {}
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF6200EE), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            // Mic Mute/Unmute Toggle (If on stage) or Request Mic Button (If audience)
                            if (isUserOnStage) {
                                IconButton(
                                    onClick = {
                                        if (!hasMicPermission) {
                                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        } else {
                                            isMicMuted = !isMicMuted
                                            SoundManager.playClickSound()
                                            scope.launch {
                                                val action = if (isMicMuted) FieldValue.arrayUnion(currentUserId) else FieldValue.arrayRemove(currentUserId)
                                                RepositoryManager.firestore.collection("liveAudioRooms")
                                                    .document(roomId)
                                                    .update("mutedSpeakers", action)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(if (isMicMuted) Color(0xFFE53935) else Color(0xFF00E676), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = "Mic Toggle",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (!hasMicPermission) {
                                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        } else {
                                            scope.launch {
                                                if (isRequestPending) {
                                                    RepositoryManager.firestore.collection("liveAudioRooms")
                                                        .document(roomId)
                                                        .update("pendingRequests", FieldValue.arrayRemove(currentUserId))
                                                    Toast.makeText(context, "Cancelled mic request", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    RepositoryManager.firestore.collection("liveAudioRooms")
                                                        .document(roomId)
                                                        .update("pendingRequests", FieldValue.arrayUnion(currentUserId))
                                                    Toast.makeText(context, "✋ Raised hand! Host notified.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isRequestPending) Color(0xFFFFA000) else Color(0xFF6200EE)),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.height(44.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        if (isRequestPending) "✋ Hand Raised" else "🎙️ Ask to Speak",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Gift Button
                            IconButton(
                                onClick = { showGiftSheet = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFFFFD700).copy(alpha = 0.2f), CircleShape)
                                    .border(1.dp, Color(0xFFFFD700), CircleShape)
                            ) {
                                Icon(Icons.Default.CardGiftcard, contentDescription = "Send Gift", tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Reaction Button
                            IconButton(
                                onClick = {
                                    val emoji = listOf("❤️", "🔥", "👏", "💯", "🎙️", "⭐").random()
                                    floatingEmojis = (floatingEmojis + (System.currentTimeMillis() to emoji)).takeLast(10)
                                    SoundManager.playClickSound()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Text("❤️", fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPodiumSeatItem(
    seat: AudioSeat,
    isCurrentLoggedInUser: Boolean,
    onSeatClick: () -> Unit
) {
    val isOccupied = seat.userId != null
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (seat.isTalking) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable { onSeatClick() }
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            // Sound wave indicator if talking
            if (seat.isTalking && !seat.isMuted) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(waveScale)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676).copy(alpha = 0.35f))
                )
            }

            if (isOccupied) {
                AsyncImage(
                    model = seat.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${seat.userId}" },
                    contentDescription = seat.username,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (seat.isHost) 2.dp else if (seat.isTalking) 2.dp else 1.dp,
                            color = if (seat.isHost) Color(0xFFFFD700) else if (seat.isTalking) Color(0xFF00E676) else Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        ),
                    contentScale = ContentScale.Crop
                )

                // Host Crown Badge or Mic Mute Badge
                if (seat.isHost) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .background(Color(0xFFFFD700), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👑", fontSize = 9.sp)
                    }
                } else if (seat.isMuted) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .background(Color(0xFFE53935), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MicOff, contentDescription = "Muted", tint = Color.White, modifier = Modifier.size(10.dp))
                    }
                }
            } else {
                // Empty Seat Placeholder
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Empty Seat",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (isOccupied) (if (isCurrentLoggedInUser) "You" else seat.username) else "Seat ${seat.seatIndex + 1}",
            fontSize = 11.sp,
            color = if (isOccupied) (if (seat.isHost) Color(0xFFFFD700) else Color.White) else Color.Gray,
            fontWeight = if (isOccupied) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
