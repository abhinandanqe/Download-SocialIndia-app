package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.model.*
import com.example.repository.GroupChatRepository
import com.example.repository.MediaRepository
import com.example.utils.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    initialGroup: GroupChat,
    currentUserId: String,
    currentUserName: String,
    currentUserAvatar: String,
    allUsers: List<User>,
    onDismiss: () -> Unit,
    onStartDirectChat: (User) -> Unit,
    onViewProfile: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaRepo = remember { MediaRepository(context) }
    val listState = rememberLazyListState()

    BackHandler(onBack = onDismiss)

    // Real-time group details
    var group by remember { mutableStateOf(initialGroup) }
    LaunchedEffect(initialGroup.id) {
        GroupChatRepository.listenGroupDetails(initialGroup.id).collect { updated ->
            if (updated != null) {
                group = updated
            }
        }
    }

    // Real-time messages
    var messages by remember { mutableStateOf(listOf<GroupMessage>()) }
    LaunchedEffect(group.id) {
        GroupChatRepository.listenGroupMessages(group.id).collect { msgs ->
            messages = msgs
            // Mark as read
            GroupChatRepository.markMessagesAsRead(group.id, currentUserId, msgs)
        }
    }

    // Scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Real-time typing indicators
    val typingUsers by remember(group.id, currentUserId) {
        GroupChatRepository.listenTypingStatus(group.id, currentUserId)
    }.collectAsState(initial = emptyList())

    // UI state
    var inputText by remember { mutableStateOf("") }
    var isUploadingMedia by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var uploadStatusText by remember { mutableStateOf("") }
    var showGifPicker by remember { mutableStateOf(false) }
    var showPollCreator by remember { mutableStateOf(false) }
    var showGroupInfoSheet by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var previewMember by remember { mutableStateOf<User?>(null) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceTimerSeconds by remember { mutableIntStateOf(0) }

    // Media preview dialog before sending
    var previewMediaUri by remember { mutableStateOf<Uri?>(null) }
    var previewIsVideo by remember { mutableStateOf(false) }

    // Full screen viewers
    var fullScreenPhotoUrl by remember { mutableStateOf<String?>(null) }
    var fullScreenPhotoSubtitle by remember { mutableStateOf<String?>(null) }
    var fullScreenVideoUrl by remember { mutableStateOf<String?>(null) }
    var fullScreenVideoSubtitle by remember { mutableStateOf<String?>(null) }

    // Replying state
    var replyingToMessage by remember { mutableStateOf<GroupMessage?>(null) }

    // Local pending / failed uploads
    val localPendingUploads = remember { mutableStateListOf<PendingUpload>() }
    val failedUploads = remember { mutableStateListOf<PendingUpload>() }

    // Admin status check
    val isAdmin = group.admins.contains(currentUserId)
    val canPost = !group.onlyAdminsCanPost || isAdmin

    // Media pickers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            previewMediaUri = uri
            previewIsVideo = false
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            previewMediaUri = uri
            previewIsVideo = true
        }
    }

    val cameraPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val file = java.io.File(context.cacheDir, "grp_cam_${System.currentTimeMillis()}.jpg")
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                previewMediaUri = Uri.fromFile(file)
                previewIsVideo = false
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to capture photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Function to execute upload and broadcast message
    fun startGroupUpload(pending: PendingUpload) {
        scope.launch {
            isUploadingMedia = true
            uploadProgress = 10
            uploadStatusText = if (pending.isVideo) "Uploading video..." else "Uploading photo..."
            failedUploads.remove(pending)
            if (!localPendingUploads.contains(pending)) {
                localPendingUploads.add(pending)
            }

            val url = try {
                mediaRepo.uploadMedia(pending.uri) { progress ->
                    uploadProgress = progress
                }
            } catch (e: Exception) {
                null
            }

            isUploadingMedia = false
            localPendingUploads.remove(pending)

            if (url != null) {
                val duration = if (pending.isVideo) getVideoDurationSeconds(context, pending.uri) else 0
                val defaultMsg = if (pending.isVideo) "🎥 Video" else "📷 Photo"
                val newMsg = GroupMessage(
                    groupId = group.id,
                    senderId = currentUserId,
                    senderName = currentUserName,
                    senderAvatar = currentUserAvatar,
                    message = pending.caption.ifEmpty { defaultMsg },
                    type = if (pending.isVideo) "video" else "image",
                    mediaUrl = url,
                    videoDurationSeconds = duration,
                    replyToId = pending.replyToId,
                    replyToText = pending.replyToText,
                    timestamp = System.currentTimeMillis()
                )
                GroupChatRepository.sendGroupMessage(group.id, newMsg)
            } else {
                failedUploads.add(pending)
                Toast.makeText(context, "Upload failed. Tap to retry.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Typing debounce
    LaunchedEffect(inputText) {
        if (inputText.isNotBlank()) {
            GroupChatRepository.setTypingStatus(group.id, currentUserId, currentUserName, true)
            delay(2500)
            GroupChatRepository.setTypingStatus(group.id, currentUserId, currentUserName, false)
        } else {
            GroupChatRepository.setTypingStatus(group.id, currentUserId, currentUserName, false)
        }
    }

    // Voice record timer effect
    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            voiceTimerSeconds = 0
            while (isRecordingVoice) {
                delay(1000)
                voiceTimerSeconds++
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                SoundManager.playClickSound()
                                showGroupInfoSheet = true
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        AsyncImage(
                            model = group.avatarUrl,
                            contentDescription = "Group Avatar",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = group.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (typingUsers.isNotEmpty()) {
                                Text(
                                    text = "${typingUsers.first()} is typing...",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Text(
                                    text = "${group.members.size} members",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        SoundManager.playClickSound()
                        onDismiss()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        SoundManager.playClickSound()
                        GlobalCallManager.initiateCall(
                            context = context,
                            peerId = "group_${group.id}",
                            peerUsername = "${group.name} (Group Audio)",
                            isVideo = false
                        )
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "Group Voice Call")
                    }
                    IconButton(onClick = {
                        SoundManager.playClickSound()
                        GlobalCallManager.initiateCall(
                            context = context,
                            peerId = "group_${group.id}",
                            peerUsername = "${group.name} (Group Video)",
                            isVideo = true
                        )
                    }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Group Video Call")
                    }
                    IconButton(onClick = {
                        SoundManager.playClickSound()
                        showGroupInfoSheet = true
                    }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Group Info")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            // Uploading progress banner
            AnimatedVisibility(visible = isUploadingMedia) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            progress = { uploadProgress / 100f },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "$uploadStatusText $uploadProgress%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Messages feed
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.type == "system") {
                        // System Pill
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = msg.message,
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (msg.type == "announcement") {
                        // Special Announcement Banner
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Campaign,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "ANNOUNCEMENT",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = formatTime(msg.timestamp),
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = msg.message,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        val author = allUsers.firstOrNull { it.id == msg.senderId } ?: User(
                                            id = msg.senderId,
                                            username = msg.senderName,
                                            avatarUrl = msg.senderAvatar
                                        )
                                        previewMember = author
                                    }
                                ) {
                                    AsyncImage(
                                        model = msg.senderAvatar.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${msg.senderId}" },
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp).clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Posted by ${msg.senderName}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    } else {
                        // Standard user message bubble
                        val isMine = msg.senderId == currentUserId
                        GroupMessageBubble(
                            message = msg,
                            isMine = isMine,
                            currentUserId = currentUserId,
                            onMemberClick = {
                                val user = allUsers.firstOrNull { it.id == msg.senderId } ?: User(
                                    id = msg.senderId,
                                    username = msg.senderName,
                                    avatarUrl = msg.senderAvatar
                                )
                                previewMember = user
                            },
                            onVotePoll = { optionId ->
                                scope.launch {
                                    GroupChatRepository.votePoll(
                                        groupId = group.id,
                                        messageId = msg.id,
                                        optionId = optionId,
                                        userId = currentUserId
                                    )
                                }
                            },
                            onPhotoClick = { url ->
                                fullScreenPhotoUrl = url
                                fullScreenPhotoSubtitle = "${msg.senderName} • ${formatTime(msg.timestamp)}"
                            },
                            onVideoClick = { url ->
                                fullScreenVideoUrl = url
                                fullScreenVideoSubtitle = "${msg.senderName} • ${formatTime(msg.timestamp)}"
                            },
                            onReply = {
                                replyingToMessage = msg
                            },
                            onReaction = { emoji ->
                                scope.launch {
                                    GroupChatRepository.reactToGroupMessage(group.id, msg.id, currentUserId, emoji)
                                }
                            }
                        )
                    }
                }

                // Local Pending Uploads
                items(localPendingUploads, key = { it.id }) { pending ->
                    PendingUploadBubble(
                        pending = pending,
                        progress = uploadProgress,
                        isFailed = false,
                        onRetry = {}
                    )
                }

                // Failed Uploads
                items(failedUploads, key = { it.id }) { failed ->
                    PendingUploadBubble(
                        pending = failed,
                        progress = 0,
                        isFailed = true,
                        onRetry = {
                            startGroupUpload(failed)
                        }
                    )
                }
            }

            // Input Bar
            if (!canPost) {
                // Restriction notice
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Only group admins can send messages in this group",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // Audio recording bar or input field
                if (isRecordingVoice) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Recording",
                            tint = Color.Red,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recording voice note... ${voiceTimerSeconds}s",
                            fontWeight = FontWeight.Bold,
                            color = Color.Red,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            SoundManager.playClickSound()
                            isRecordingVoice = false
                        }) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                SoundManager.playClickSound()
                                val duration = if (voiceTimerSeconds == 0) 4 else voiceTimerSeconds
                                isRecordingVoice = false
                                scope.launch {
                                    val voiceMsg = GroupMessage(
                                        groupId = group.id,
                                        senderId = currentUserId,
                                        senderName = currentUserName,
                                        senderAvatar = currentUserAvatar,
                                        message = "Voice message (${duration}s)",
                                        type = "voice",
                                        voiceDurationSeconds = duration,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    GroupChatRepository.sendGroupMessage(group.id, voiceMsg)
                                }
                            },
                            shape = CircleShape
                        ) {
                            Text("Send")
                        }
                    }
                } else {
                    Column {
                        // Replying Preview Banner
                        AnimatedVisibility(visible = replyingToMessage != null) {
                            replyingToMessage?.let { targetMsg ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(30.dp)
                                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Replying to ${targetMsg.senderName}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            val snippet = when (targetMsg.type) {
                                                "image" -> "📷 Photo"
                                                "video" -> "🎥 Video"
                                                else -> targetMsg.message
                                            }
                                            Text(
                                                text = snippet,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(onClick = { replyingToMessage = null }) {
                                            Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .navigationBarsPadding()
                                .imePadding(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Attach (+) Menu
                            Box {
                                IconButton(onClick = {
                                    SoundManager.playClickSound()
                                    showAttachMenu = !showAttachMenu
                                }) {
                                    Icon(
                                        Icons.Default.AddCircle,
                                        contentDescription = "Attach",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showAttachMenu,
                                    onDismissRequest = { showAttachMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Photo") },
                                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF2E7D32)) },
                                        onClick = {
                                            SoundManager.playClickSound()
                                            showAttachMenu = false
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Video") },
                                        leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF673AB7)) },
                                        onClick = {
                                            SoundManager.playClickSound()
                                            showAttachMenu = false
                                            videoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Camera") },
                                        leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color(0xFF1976D2)) },
                                        onClick = {
                                            SoundManager.playClickSound()
                                            showAttachMenu = false
                                            try {
                                                cameraPhotoLauncher.launch(null)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Camera unavailable", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Create Poll") },
                                        leadingIcon = { Icon(Icons.Default.Poll, contentDescription = null) },
                                        onClick = {
                                            SoundManager.playClickSound()
                                            showAttachMenu = false
                                            showPollCreator = true
                                        }
                                    )
                                    if (isAdmin) {
                                        DropdownMenuItem(
                                            text = { Text("Announcement") },
                                            leadingIcon = { Icon(Icons.Default.Campaign, contentDescription = null) },
                                            onClick = {
                                                SoundManager.playClickSound()
                                                showAttachMenu = false
                                                if (inputText.isNotBlank()) {
                                                    val text = inputText.trim()
                                                    inputText = ""
                                                    scope.launch {
                                                        val announcementMsg = GroupMessage(
                                                            groupId = group.id,
                                                            senderId = currentUserId,
                                                            senderName = currentUserName,
                                                            senderAvatar = currentUserAvatar,
                                                            message = text,
                                                            type = "announcement",
                                                            timestamp = System.currentTimeMillis()
                                                        )
                                                        GroupChatRepository.sendGroupMessage(group.id, announcementMsg)
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Type text first then tap Announcement", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // GIF Button
                            IconButton(onClick = {
                                SoundManager.playClickSound()
                                showGifPicker = true
                            }) {
                                Text("GIF", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }

                            // Text Field
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                placeholder = { Text("Message...", color = Color.Gray, fontSize = 14.sp) },
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                maxLines = 4
                            )

                            // Voice or Send Button
                            if (inputText.isBlank()) {
                                IconButton(onClick = {
                                    SoundManager.playClickSound()
                                    isRecordingVoice = true
                                }) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice Message", tint = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        SoundManager.playClickSound()
                                        val textToSend = inputText.trim()
                                        val rep = replyingToMessage
                                        replyingToMessage = null
                                        inputText = ""
                                        scope.launch {
                                            val newMsg = GroupMessage(
                                                groupId = group.id,
                                                senderId = currentUserId,
                                                senderName = currentUserName,
                                                senderAvatar = currentUserAvatar,
                                                message = textToSend,
                                                type = "text",
                                                replyToId = rep?.id,
                                                replyToText = rep?.message?.ifEmpty { rep.type },
                                                timestamp = System.currentTimeMillis()
                                            )
                                            GroupChatRepository.sendGroupMessage(group.id, newMsg)
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // GIF / Sticker Picker Sheet
    if (showGifPicker) {
        GifPickerBottomSheet(
            onDismissRequest = { showGifPicker = false },
            onGifSelected = { gifItem ->
                showGifPicker = false
                scope.launch {
                    val gifMsg = GroupMessage(
                        groupId = group.id,
                        senderId = currentUserId,
                        senderName = currentUserName,
                        senderAvatar = currentUserAvatar,
                        message = "Shared a GIF",
                        type = "gif",
                        mediaUrl = gifItem.fullUrl,
                        timestamp = System.currentTimeMillis()
                    )
                    GroupChatRepository.sendGroupMessage(group.id, gifMsg)
                }
            },
            onStickerSelected = { stickerItem ->
                showGifPicker = false
                scope.launch {
                    val stickerMsg = GroupMessage(
                        groupId = group.id,
                        senderId = currentUserId,
                        senderName = currentUserName,
                        senderAvatar = currentUserAvatar,
                        message = stickerItem.name.ifEmpty { "Sticker" },
                        type = "sticker",
                        mediaUrl = stickerItem.mediaUrl.ifEmpty { stickerItem.thumbnailUrl },
                        thumbnailUrl = stickerItem.thumbnailUrl,
                        driveFileId = stickerItem.driveFileId,
                        thumbnailDriveFileId = stickerItem.thumbnailDriveFileId,
                        stickerId = stickerItem.stickerId,
                        packId = stickerItem.packId,
                        isAnimated = stickerItem.isAnimated,
                        timestamp = System.currentTimeMillis()
                    )
                    GroupChatRepository.sendGroupMessage(group.id, stickerMsg)
                    RepositoryManager.stickerRepository.recordStickerUsage(currentUserId, stickerItem)
                }
            }
        )
    }

    // Poll Creator Sheet
    if (showPollCreator) {
        PollCreatorDialog(
            creatorId = currentUserId,
            onDismiss = { showPollCreator = false },
            onPollCreated = { poll ->
                scope.launch {
                    val pollMsg = GroupMessage(
                        groupId = group.id,
                        senderId = currentUserId,
                        senderName = currentUserName,
                        senderAvatar = currentUserAvatar,
                        message = poll.question,
                        type = "poll",
                        poll = poll,
                        timestamp = System.currentTimeMillis()
                    )
                    GroupChatRepository.sendGroupMessage(group.id, pollMsg)
                }
            }
        )
    }

    // Group Info Sheet
    if (showGroupInfoSheet) {
        GroupInfoBottomSheet(
            group = group,
            currentUserId = currentUserId,
            currentUserName = currentUserName,
            allUsers = allUsers,
            onDismiss = { showGroupInfoSheet = false },
            onGroupLeftOrDeleted = {
                showGroupInfoSheet = false
                onDismiss()
            },
            onMemberClick = { member ->
                previewMember = member
            }
        )
    }

    // Member Preview Dialog
    previewMember?.let { member ->
        GroupMemberPreviewDialog(
            user = member,
            group = group,
            currentUserId = currentUserId,
            onDismiss = { previewMember = null },
            onStartDirectChat = { target ->
                previewMember = null
                onDismiss()
                onStartDirectChat(target)
            },
            onViewProfile = { uid ->
                previewMember = null
                onDismiss()
                onViewProfile(uid)
            },
            onPromoteToAdmin = {
                scope.launch {
                    GroupChatRepository.promoteToAdmin(
                        groupId = group.id,
                        memberId = member.id,
                        actorName = currentUserName,
                        memberName = member.username
                    )
                    Toast.makeText(context, "${member.username} is now Admin", Toast.LENGTH_SHORT).show()
                }
            },
            onDemoteAdmin = {
                scope.launch {
                    GroupChatRepository.demoteAdmin(
                        groupId = group.id,
                        memberId = member.id,
                        actorName = currentUserName,
                        memberName = member.username
                    )
                    Toast.makeText(context, "Admin privileges removed", Toast.LENGTH_SHORT).show()
                }
            },
            onRemoveFromGroup = {
                scope.launch {
                    GroupChatRepository.removeMember(
                        groupId = group.id,
                        memberId = member.id,
                        actorName = currentUserName,
                        memberName = member.username
                    )
                    Toast.makeText(context, "Removed from group", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    // Media Send Preview Dialog (with Caption)
    previewMediaUri?.let { uri ->
        MediaSendPreviewDialog(
            mediaUri = uri,
            isVideo = previewIsVideo,
            onSend = { caption ->
                val rep = replyingToMessage
                replyingToMessage = null
                val pending = PendingUpload(
                    id = "grp_up_${UUID.randomUUID().toString().take(8)}",
                    uri = uri,
                    isVideo = previewIsVideo,
                    caption = caption,
                    replyToId = rep?.id,
                    replyToText = rep?.message?.ifEmpty { rep.type }
                )
                previewMediaUri = null
                startGroupUpload(pending)
            },
            onDismiss = { previewMediaUri = null }
        )
    }

    // Full-Screen Photo Viewer
    fullScreenPhotoUrl?.let { url ->
        FullScreenPhotoViewer(
            imageUrl = url,
            title = group.name,
            subtitle = fullScreenPhotoSubtitle,
            onDismiss = { fullScreenPhotoUrl = null }
        )
    }

    // Full-Screen Video Viewer
    fullScreenVideoUrl?.let { url ->
        FullScreenVideoViewer(
            videoUrl = url,
            title = group.name,
            subtitle = fullScreenVideoSubtitle,
            onDismiss = { fullScreenVideoUrl = null }
        )
    }
}

@Composable
fun GroupMessageBubble(
    message: GroupMessage,
    isMine: Boolean,
    currentUserId: String,
    onMemberClick: () -> Unit,
    onVotePoll: (Int) -> Unit,
    onPhotoClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onReply: () -> Unit,
    onReaction: (String) -> Unit
) {
    var isPlayingVoice by remember { mutableStateOf(false) }
    var showReactionMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMine) {
            AsyncImage(
                model = message.senderAvatar.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${message.senderId}" },
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable {
                        SoundManager.playClickSound()
                        onMemberClick()
                    },
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            // Sender name (if not mine)
            if (!isMine) {
                Text(
                    text = message.senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onMemberClick() }
                        .padding(start = 4.dp, bottom = 2.dp)
                )
            }

            // Quoted Reply Card
            if (!message.replyToText.isNullOrEmpty()) {
                Surface(
                    color = if (isMine) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(22.dp)
                                .background(
                                    if (isMine) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = message.replyToText ?: "",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isMine) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            val isSharedMedia = message.type == "shared_reel" ||
                    message.type == "shared_post" ||
                    message.message.startsWith("Shared a reel", ignoreCase = true) ||
                    message.message.startsWith("Shared a post", ignoreCase = true) ||
                    message.message.contains("reels://") ||
                    message.message.contains("post://")

            if (isSharedMedia) {
                val extractedPostId = run {
                    val msg = message.message
                    when {
                        msg.contains("reels://") -> msg.substringAfter("reels://").substringBefore(" ").trim()
                        msg.contains("post://") -> msg.substringAfter("post://").substringBefore(" ").trim()
                        msg.contains("story://") -> msg.substringAfter("story://").substringBefore(" ").trim()
                        else -> null
                    }
                }

                Box(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    SoundManager.playClickSound()
                                    showReactionMenu = true
                                }
                            )
                        }
                ) {
                    InstagramSharedMediaBubble(
                        postId = extractedPostId,
                        mediaUrl = message.mediaUrl,
                        thumbnailUrl = message.thumbnailUrl,
                        mediaType = if (message.type == "shared_post") "image" else "video",
                        messageCaption = message.message,
                        isMine = isMine,
                        onMediaClick = { pId, mUrl, isVid ->
                            if (mUrl.isNotEmpty()) {
                                if (isVid) onVideoClick(mUrl) else onPhotoClick(mUrl)
                            } else if (pId.isNotEmpty()) {
                                scope.launch {
                                    try {
                                        val postDoc = com.example.model.RepositoryManager.firestore.collection("posts").document(pId).get().await()
                                        val realUrl = postDoc.getString("postUrl") ?: postDoc.getString("fileUrl") ?: ""
                                        if (realUrl.isNotEmpty()) {
                                            if (com.example.utils.MediaResolver.isImage(realUrl)) {
                                                onPhotoClick(realUrl)
                                            } else {
                                                onVideoClick(realUrl)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("GroupChatScreen", "Error resolving post $pId", e)
                                    }
                                }
                            }
                        }
                    )
                }
            } else {
                // Message Bubble content
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMine) 16.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 16.dp
                            )
                        )
                        .background(
                            if (isMine) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    SoundManager.playClickSound()
                                    showReactionMenu = true
                                }
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    when (message.type) {
                    "image" -> {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        message.mediaUrl?.let { onPhotoClick(it) }
                                    }
                            ) {
                                SubcomposeAsyncImage(
                                    model = message.mediaUrl,
                                    contentDescription = "Photo",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 150.dp, max = 240.dp),
                                    contentScale = ContentScale.Crop,
                                    loading = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .background(Color.DarkGray.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                        }
                                    },
                                    error = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp)
                                                .background(MaterialTheme.colorScheme.errorContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                )
                            }
                            if (message.message.isNotBlank() && message.message != "Shared a photo") {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = message.message,
                                    fontSize = 14.sp,
                                    color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    "video" -> {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        message.mediaUrl?.let { onVideoClick(it) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                SubcomposeAsyncImage(
                                    model = message.thumbnailUrl ?: message.mediaUrl,
                                    contentDescription = "Video",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 150.dp, max = 240.dp),
                                    contentScale = ContentScale.Crop,
                                    loading = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .background(Color.Black.copy(alpha = 0.3f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                        }
                                    },
                                    error = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(150.dp)
                                                .background(Color.Black),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White)
                                        }
                                    }
                                )
                                // Centered Play Button
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Play Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                // Duration badge
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.75f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = "0:${message.videoDurationSeconds.toString().padStart(2, '0')}",
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (message.message.isNotBlank() && message.message != "Shared a video") {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = message.message,
                                    fontSize = 14.sp,
                                    color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    "gif", "sticker" -> {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val imageLoader = remember {
                            coil.ImageLoader.Builder(context)
                                .components {
                                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                                        add(coil.decode.ImageDecoderDecoder.Factory())
                                    } else {
                                        add(coil.decode.GifDecoder.Factory())
                                    }
                                }
                                .build()
                        }
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(message.mediaUrl)
                                .crossfade(true)
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = if (message.type == "sticker") "Sticker" else "GIF",
                            modifier = Modifier
                                .sizeIn(maxWidth = 180.dp, maxHeight = 180.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    "voice" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    SoundManager.playClickSound()
                                    isPlayingVoice = !isPlayingVoice
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (isPlayingVoice) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play voice",
                                    tint = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                LinearProgressIndicator(
                                    progress = { if (isPlayingVoice) 0.65f else 0.0f },
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    trackColor = Color.Gray.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "0:${message.voiceDurationSeconds.toString().padStart(2, '0')}",
                                    fontSize = 11.sp,
                                    color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else Color.Gray
                                )
                            }
                        }
                    }
                    "poll" -> {
                        message.poll?.let { poll ->
                            val totalVotes = poll.options.sumOf { opt: GroupPollOption -> opt.votes.size }
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = poll.question,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (poll.multipleChoice) "Multiple choice • $totalVotes votes" else "Single choice • $totalVotes votes",
                                    fontSize = 11.sp,
                                    color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else Color.Gray
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                poll.options.forEach { opt ->
                                    val percent = if (totalVotes > 0) (opt.votes.size.toFloat() / totalVotes) else 0f
                                    val isSelected = opt.votes.contains(message.senderId)

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surface
                                            )
                                            .clickable {
                                                SoundManager.playClickSound()
                                                onVotePoll(opt.id)
                                            }
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = opt.text,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${(percent * 100).toInt()}% (${opt.votes.size})",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { percent },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = Color.Gray.copy(alpha = 0.2f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                    else -> {
                        // Regular text message
                        Text(
                            text = message.message,
                            fontSize = 14.sp,
                            color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

            // Aggregated Reactions Row
            if (message.reactions.isNotEmpty()) {
                val emojiCounts = message.reactions.values.groupingBy { it }.eachCount()
                val myReaction = message.reactions[currentUserId]
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    emojiCounts.forEach { (emoji, count) ->
                        val hasMyReaction = myReaction == emoji
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (hasMyReaction) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (hasMyReaction) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable { onReaction(emoji) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = emoji, fontSize = 12.sp)
                                if (count > 1) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "$count",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasMyReaction) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Timestamp and read receipt
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTime(message.timestamp),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                if (isMine) {
                    Spacer(modifier = Modifier.width(4.dp))
                    val readCount = message.readBy.filter { it != message.senderId }.size
                    if (readCount > 0) {
                        Icon(
                            Icons.Default.DoneAll,
                            contentDescription = "Read by members",
                            tint = Color(0xFF34B7F1),
                            modifier = Modifier.size(13.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Done,
                            contentDescription = "Sent",
                            tint = Color.Gray,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            // Reaction and Context Dropdown
            DropdownMenu(
                expanded = showReactionMenu,
                onDismissRequest = { showReactionMenu = false }
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    listOf("❤️", "😂", "🔥", "👍", "😮", "😢").forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 22.sp,
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .clickable {
                                    SoundManager.playClickSound()
                                    showReactionMenu = false
                                    onReaction(emoji)
                                }
                        )
                    }
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Reply") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                    onClick = {
                        SoundManager.playClickSound()
                        showReactionMenu = false
                        onReply()
                    }
                )
            }
        }
    }
}
