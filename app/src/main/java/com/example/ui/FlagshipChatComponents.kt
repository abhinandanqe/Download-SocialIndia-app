package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.model.MessageEntity
import com.example.model.RepositoryManager
import com.example.model.User
import com.example.repository.MediaRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class PendingUpload(
    val id: String,
    val uri: Uri,
    val isVideo: Boolean,
    val caption: String,
    val replyToId: String?,
    val replyToText: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlagshipChatWindow(
    currentUserId: String,
    otherUser: User,
    messages: List<MessageEntity>,
    onSendMessage: (text: String, mediaType: String?, mediaUrl: String?, videoDuration: Int, replyToId: String?, replyToText: String?, thumbnailUrl: String?) -> Unit,
    onDismiss: () -> Unit,
    onCallClick: (Boolean) -> Unit,
    onOpenReel: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaRepo = remember { MediaRepository(context) }

    BackHandler(onBack = onDismiss)

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Attach menu sheet state
    var showAttachSheet by remember { mutableStateOf(false) }
    var showSocialMediaPicker by remember { mutableStateOf(false) }

    // Media selected for preview dialog
    var previewMediaUri by remember { mutableStateOf<Uri?>(null) }
    var previewIsVideo by remember { mutableStateOf(false) }
    var showGiftBoxWrapDialog by remember { mutableStateOf(false) }

    // Full-screen viewer states
    var fullScreenPhotoUrl by remember { mutableStateOf<String?>(null) }
    var fullScreenPhotoSubtitle by remember { mutableStateOf<String?>(null) }
    var fullScreenVideoUrl by remember { mutableStateOf<String?>(null) }
    var fullScreenVideoSubtitle by remember { mutableStateOf<String?>(null) }

    // Uploading states
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var uploadStatusText by remember { mutableStateOf("") }

    // Replying state
    var replyingToMessage by remember { mutableStateOf<MessageEntity?>(null) }

    // Direct Chat Typing state
    val isOtherUserTyping by remember(currentUserId, otherUser.id) {
        RepositoryManager.socialRepository.getDirectChatTypingStatus(currentUserId, otherUser.id)
    }.collectAsState(initial = false)

    var typingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(messageText) {
        if (currentUserId.isNotEmpty() && otherUser.id.isNotEmpty()) {
            typingJob?.cancel()
            if (messageText.isNotBlank()) {
                RepositoryManager.socialRepository.setDirectChatTypingStatus(currentUserId, otherUser.id, true)
                typingJob = scope.launch {
                    kotlinx.coroutines.delay(4000)
                    RepositoryManager.socialRepository.setDirectChatTypingStatus(currentUserId, otherUser.id, false)
                }
            } else {
                RepositoryManager.socialRepository.setDirectChatTypingStatus(currentUserId, otherUser.id, false)
            }
        }
    }

    DisposableEffect(currentUserId, otherUser.id) {
        onDispose {
            typingJob?.cancel()
            scope.launch {
                RepositoryManager.socialRepository.setDirectChatTypingStatus(currentUserId, otherUser.id, false)
            }
        }
    }

    // Local pending / failed uploads list
    val localPendingUploads = remember { mutableStateListOf<PendingUpload>() }
    val failedUploads = remember { mutableStateListOf<PendingUpload>() }

    // Photo & Video pickers
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
                val file = java.io.File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
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

    // Voice Recorder state and permission
    val voiceRecorder = remember { com.example.utils.VoiceRecorderHelper(context) }
    val isRecordingVoice by voiceRecorder.isRecording.collectAsState()
    val recordingDurationSeconds by voiceRecorder.recordingDurationSeconds.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.release()
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceRecorder.startRecording()
        } else {
            Toast.makeText(context, "Microphone permission required for voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to trigger upload and send
    fun startUpload(pending: PendingUpload) {
        scope.launch {
            isUploading = true
            uploadProgress = 10
            uploadStatusText = if (pending.isVideo) "Uploading video..." else "Uploading photo..."
            failedUploads.remove(pending)
            if (!localPendingUploads.contains(pending)) {
                localPendingUploads.add(pending)
            }

            val uploadedUrl = try {
                mediaRepo.uploadMedia(pending.uri) { progress ->
                    uploadProgress = progress
                }
            } catch (e: Exception) {
                null
            }

            isUploading = false
            localPendingUploads.remove(pending)

            if (!uploadedUrl.isNullOrEmpty()) {
                val duration = if (pending.isVideo) {
                    getVideoDurationSeconds(context, pending.uri)
                } else 0

                val mediaType = if (pending.isVideo) "video" else "image"
                val defaultText = if (pending.isVideo) "🎥 Video" else "📷 Photo"
                val textToSend = pending.caption.ifEmpty { defaultText }

                onSendMessage(
                    textToSend,
                    mediaType,
                    uploadedUrl,
                    duration,
                    pending.replyToId,
                    pending.replyToText,
                    null
                )
            } else {
                failedUploads.add(pending)
                Toast.makeText(context, "Failed to upload media. Tap Retry to try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Synced Chat Upgrades: Theme, Vanish Mode & Voice Changer Filter
    val chatUpgradePrefs by remember(currentUserId, otherUser.id) {
        com.example.repository.ProCreatorSuiteRepository.observeChatUpgradePrefs(currentUserId, otherUser.id)
    }.collectAsState(initial = com.example.model.ChatUpgradePrefs())
    var isVanishModeActive by remember { mutableStateOf(false) }
    LaunchedEffect(chatUpgradePrefs.vanishModeEnabled) {
        isVanishModeActive = chatUpgradePrefs.vanishModeEnabled
    }
    var showChatStudioSheet by remember { mutableStateOf(false) }
    var showFullWatchPartyDialog by remember { mutableStateOf(false) }
    val activeChatTheme = remember(chatUpgradePrefs.themeId) {
        ChatUpgradeCatalog.resolveTheme(chatUpgradePrefs.themeId)
    }
    val activeVoiceFilter = remember(chatUpgradePrefs.voiceFilterId) {
        ChatUpgradeCatalog.resolveVoiceFilter(chatUpgradePrefs.voiceFilterId)
    }

    // Auto-purge Vanish Mode messages when leaving chat while Vanish Mode was active
    DisposableEffect(currentUserId, otherUser.id, isVanishModeActive) {
        onDispose {
            if (isVanishModeActive) {
                scope.launch {
                    com.example.repository.ProCreatorSuiteRepository.purgeVanishMessagesForChat(currentUserId, otherUser.id)
                }
            }
        }
    }

    var disappearingMediaMode by remember { mutableStateOf("Keep in Chat") } // "View Once", "Allow Replay", "Keep in Chat"

    val totalCount = messages.size + localPendingUploads.size + failedUploads.size
    LaunchedEffect(totalCount) {
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isVanishModeActive) {
                    Modifier.background(Color(0xFF0A0A12))
                } else if (activeChatTheme.id != "default") {
                    Modifier.background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(activeChatTheme.bgTop, activeChatTheme.bgBottom)
                        )
                    )
                } else {
                    Modifier.background(MaterialTheme.colorScheme.background)
                }
            )
    ) {
        // Top App Bar
        var showChatUserInfoDialog by remember { mutableStateOf(false) }
        if (showChatUserInfoDialog) {
            AlertDialog(
                onDismissRequest = { showChatUserInfoDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = otherUser.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${otherUser.id}" },
                            contentDescription = "Avatar",
                            modifier = Modifier.size(46.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(otherUser.username, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                if (otherUser.isVerified || otherUser.verificationStatus == "approved" || otherUser.verificationStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    VerificationBadge(user = otherUser, modifier = Modifier.size(15.dp), showDialogOnClick = false)
                                }
                            }
                            Text(
                                text = if (otherUser.status == "online") "Active now" else "Active recently",
                                fontSize = 12.sp,
                                color = if (otherUser.status == "online") Color(0xFF4CAF50) else Color.Gray
                            )
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (otherUser.bio.isNotBlank()) {
                            Text(otherUser.bio, fontSize = 13.5.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    showChatUserInfoDialog = false
                                    onCallClick(false)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Audio", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    showChatUserInfoDialog = false
                                    onCallClick(true)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Video", fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showChatUserInfoDialog = false }) { Text("Close") }
                }
            )
        }
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showChatUserInfoDialog = true }
                ) {
                    Box {
                        AsyncImage(
                            model = otherUser.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${otherUser.id}" },
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        if (otherUser.status == "online") {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = otherUser.username,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isVanishModeActive) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            if (otherUser.isVerified || otherUser.verificationStatus == "approved" || otherUser.verificationStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE) {
                                Spacer(modifier = Modifier.width(4.dp))
                                VerificationBadge(
                                    user = otherUser,
                                    modifier = Modifier.size(15.dp),
                                    showDialogOnClick = true
                                )
                            }
                        }
                        Text(
                            text = if (isVanishModeActive) "Vanish Mode • Disappearing messages" else if (isOtherUserTyping) "typing..." else if (otherUser.status == "online") "Active now" else "Active recently",
                            color = if (isVanishModeActive) Color(0xFFB388FF) else if (isOtherUserTyping) MaterialTheme.colorScheme.primary else if (otherUser.status == "online") Color(0xFF4CAF50) else Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = if (isOtherUserTyping || isVanishModeActive) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = if (isVanishModeActive) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            },
            actions = {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF00E676).copy(alpha = 0.16f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676)),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clickable {
                            triggerCrispMicroHaptic(context, strong = true)
                            showFullWatchPartyDialog = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = "Watch Party",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Watch Party",
                            color = Color(0xFF00E676),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                IconButton(onClick = { showChatStudioSheet = true }) {
                    Icon(
                        imageVector = if (isVanishModeActive) Icons.Default.VisibilityOff else Icons.Default.AutoFixHigh,
                        contentDescription = "Chat Themes, Vanish Mode & Voice Filters",
                        tint = if (isVanishModeActive) Color(0xFFB388FF) else activeChatTheme.accent
                    )
                }
                IconButton(onClick = { onCallClick(false) }) {
                    Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = if (isVanishModeActive || activeChatTheme.id != "default") Color.White else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { onCallClick(true) }) {
                    Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = if (isVanishModeActive || activeChatTheme.id != "default") Color.White else MaterialTheme.colorScheme.onSurface)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (isVanishModeActive) Color(0xFF12121A)
                else if (activeChatTheme.id != "default") activeChatTheme.bgTop
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            )
        )

        if (showChatStudioSheet) {
            ChatThemeAndVoiceUpgradesSheet(
                currentThemeId = chatUpgradePrefs.themeId,
                vanishModeEnabled = isVanishModeActive,
                currentVoiceFilterId = chatUpgradePrefs.voiceFilterId,
                onSelectTheme = { newTheme ->
                    scope.launch {
                        com.example.repository.ProCreatorSuiteRepository.updateChatUpgradePrefs(
                            uid1 = currentUserId,
                            uid2 = otherUser.id,
                            themeId = newTheme
                        )
                    }
                },
                onToggleVanishMode = { enabled ->
                    isVanishModeActive = enabled
                    scope.launch {
                        com.example.repository.ProCreatorSuiteRepository.updateChatUpgradePrefs(
                            uid1 = currentUserId,
                            uid2 = otherUser.id,
                            vanishModeEnabled = enabled
                        )
                        if (!enabled) {
                            com.example.repository.ProCreatorSuiteRepository.purgeVanishMessagesForChat(currentUserId, otherUser.id)
                        }
                    }
                },
                onSelectVoiceFilter = { filterId ->
                    scope.launch {
                        com.example.repository.ProCreatorSuiteRepository.updateChatUpgradePrefs(
                            uid1 = currentUserId,
                            uid2 = otherUser.id,
                            voiceFilterId = filterId
                        )
                    }
                    Toast.makeText(context, "Voice filter set: ${ChatUpgradeCatalog.resolveVoiceFilter(filterId).name}", Toast.LENGTH_SHORT).show()
                },
                onStartWatchParty = { showFullWatchPartyDialog = true },
                onDismiss = { showChatStudioSheet = false }
            )
        }

        if (showFullWatchPartyDialog) {
            val partyRoomId = remember(currentUserId, otherUser.id) {
                "wp_${com.example.repository.ProCreatorSuiteRepository.chatPairKey(currentUserId, otherUser.id)}"
            }
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showFullWatchPartyDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                WatchPartyScreen(
                    initialRoomId = partyRoomId,
                    partnerId = otherUser.id,
                    onBack = { showFullWatchPartyDialog = false }
                )
            }
        }

        AnimatedVisibility(visible = isVanishModeActive) {
            Surface(
                color = Color(0xFF1F1B2E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color(0xFFB388FF), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vanish Mode ON • Seen messages disappear when you close chat",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.5.sp
                        )
                    }
                    Text(
                        text = "Turn Off",
                        color = Color(0xFFB388FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { isVanishModeActive = false }
                    )
                }
            }
        }

        // Upload Progress Banner
        AnimatedVisibility(visible = isUploading) {
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

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(messages, key = { index, m -> "msg_${m.id}_$index" }) { _, message ->
                val isMine = message.senderId == currentUserId
                FlagshipMessageBubble(
                    message = message,
                    isMine = isMine,
                    currentUserId = currentUserId,
                    otherUser = otherUser,
                    onPhotoClick = { url ->
                        fullScreenPhotoUrl = url
                        fullScreenPhotoSubtitle = formatTime(message.timestamp)
                    },
                    onVideoClick = { url ->
                        fullScreenVideoUrl = url
                        fullScreenVideoSubtitle = formatTime(message.timestamp)
                    },
                    onReelClick = { reelId ->
                        onOpenReel(reelId)
                    },
                    onReply = {
                        replyingToMessage = message
                    },
                    onReaction = { emoji ->
                        scope.launch {
                            val newReaction = if (message.reaction == emoji) null else emoji
                            RepositoryManager.socialRepository.reactToChatMessage(message.id, newReaction)
                        }
                    },
                    onEdit = { newText ->
                        scope.launch {
                            RepositoryManager.socialRepository.editChatMessage(message.id, newText)
                        }
                    },
                    onDelete = { forEveryone ->
                        scope.launch {
                            RepositoryManager.socialRepository.deleteChatMessage(message.id, forEveryone, currentUserId)
                        }
                    }
                )
            }

            // Local Pending Uploads Bubbles
            items(localPendingUploads, key = { it.id }) { pending ->
                PendingUploadBubble(
                    pending = pending,
                    progress = uploadProgress,
                    isFailed = false,
                    onRetry = {}
                )
            }

            // Failed Uploads Bubbles
            items(failedUploads, key = { it.id }) { failed ->
                PendingUploadBubble(
                    pending = failed,
                    progress = 0,
                    isFailed = true,
                    onRetry = {
                        startUpload(failed)
                    }
                )
            }
        }

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
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (targetMsg.senderId == currentUserId) "Replying to yourself" else "Replying to ${otherUser.username}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val snippet = when {
                                targetMsg.mediaType == "image" -> "📷 Photo"
                                targetMsg.mediaType == "video" -> "🎥 Video"
                                else -> targetMsg.message
                            }
                            Text(
                                text = snippet,
                                fontSize = 13.sp,
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

        // Input Area
        if (isRecordingVoice) {
            val waveTransition = rememberInfiniteTransition(label = "voiceWave")
            val wavePhase by waveTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(420, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wavePhase"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDurationSeconds(recordingDurationSeconds),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(10.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val baseHeights = listOf(8, 16, 22, 12, 20, 24, 14, 18, 24, 10, 18, 22, 15, 20, 12, 16, 10)
                    baseHeights.forEachIndexed { idx, baseH ->
                        val factor = if (idx % 2 == 0) wavePhase else (1.35f - wavePhase)
                        val barH = (baseH * factor).coerceIn(5f, 24f).dp
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(barH)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF0095F6))
                        )
                    }
                }

                // Cancel / Discard
                IconButton(
                    onClick = { voiceRecorder.cancelRecording() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Discard Recording",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Send Voice
                IconButton(
                    onClick = {
                        val file = voiceRecorder.stopRecording()
                        val duration = recordingDurationSeconds
                        if (file != null && file.exists() && duration > 0) {
                            scope.launch {
                                try {
                                    val mediaRepo = MediaRepository(context)
                                    val audioUrl = mediaRepo.uploadMedia(Uri.fromFile(file))
                                    val rep = replyingToMessage
                                    replyingToMessage = null
                                    val filterTag = if (activeVoiceFilter.id != "original") " [VOICE:${activeVoiceFilter.id}]" else ""
                                    val vanishPrefix = if (isVanishModeActive) "[VANISH] " else ""
                                    onSendMessage(
                                        "${vanishPrefix}🎙️ Voice message${filterTag}",
                                        "voice",
                                        audioUrl,
                                        duration,
                                        rep?.id,
                                        rep?.message?.ifEmpty { rep.mediaType },
                                        null
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to send voice message", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Voice Message",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            AnimatedVisibility(
                visible = isOtherUserTyping,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${otherUser.username} is typing...",
                                fontSize = 12.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attachment Button
                IconButton(
                    onClick = { showAttachSheet = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Attach Media",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Camera Quick Button
                IconButton(
                    onClick = {
                        try {
                            cameraPhotoLauncher.launch(null)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Camera unavailable", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Camera",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Sticker & GIF Picker Button
                IconButton(
                    onClick = { showSocialMediaPicker = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SentimentSatisfiedAlt,
                        contentDescription = "Stickers & GIFs",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Message Input Field
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    placeholder = { Text("Message...", color = Color.Gray, fontSize = 15.sp) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    maxLines = 4
                )

                // Send or Mic Button with Gift Box Wrap option
                if (messageText.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showGiftBoxWrapDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("🎁", fontSize = 20.sp)
                        }
                        IconButton(
                            onClick = {
                                val text = com.example.security.InputGuard.sanitizeChatMessage(messageText)
                                if (text.isNotBlank() && com.example.security.InputGuard.allowAction("dm_send:${otherUser.id}", 300L)) {
                                    val rep = replyingToMessage
                                    replyingToMessage = null
                                    messageText = ""
                                    val outbound = if (isVanishModeActive) "[VANISH] $text" else text
                                    onSendMessage(
                                        outbound,
                                        null,
                                        null,
                                        0,
                                        rep?.id,
                                        rep?.message?.ifEmpty { rep.mediaType },
                                        null
                                    )
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = {
                            val hasAudioPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.RECORD_AUDIO
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (hasAudioPerm) {
                                voiceRecorder.startRecording()
                            } else {
                                recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Record Voice Note",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }

    // Attachment Options Bottom Sheet
    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Share Content",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Disappearing Media Mode Selector (View Once / Allow Replay / Keep in Chat)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("View Once", "Allow Replay", "Keep in Chat").forEach { mode ->
                        val isSelected = disappearingMediaMode == mode
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFF0095F6) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { disappearingMediaMode = mode }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (mode == "View Once") Icons.Default.Timer else if (mode == "Allow Replay") Icons.Default.Replay else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = mode,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // Photo Option
                    AttachmentOptionItem(
                        icon = Icons.Default.Image,
                        label = "Photo",
                        containerColor = Color(0xFFE8F5E9),
                        tintColor = Color(0xFF2E7D32),
                        onClick = {
                            showAttachSheet = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    // Video Option
                    AttachmentOptionItem(
                        icon = Icons.Default.Videocam,
                        label = "Video",
                        containerColor = Color(0xFFEDE7F6),
                        tintColor = Color(0xFF673AB7),
                        onClick = {
                            showAttachSheet = false
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    )

                    // Camera Option
                    AttachmentOptionItem(
                        icon = Icons.Default.CameraAlt,
                        label = "Camera",
                        containerColor = Color(0xFFE3F2FD),
                        tintColor = Color(0xFF1976D2),
                        onClick = {
                            showAttachSheet = false
                            try {
                                cameraPhotoLauncher.launch(null)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Camera unavailable", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    // Stickers & GIFs Option
                    AttachmentOptionItem(
                        icon = Icons.Default.EmojiEmotions,
                        label = "Stickers",
                        containerColor = Color(0xFFFFF3E0),
                        tintColor = Color(0xFFE65100),
                        onClick = {
                            showAttachSheet = false
                            showSocialMediaPicker = true
                        }
                    )

                    // Instagram-style Gift Box Option
                    AttachmentOptionItem(
                        icon = Icons.Default.CardGiftcard,
                        label = "Gift Box",
                        containerColor = Color(0xFFFFEBEE),
                        tintColor = Color(0xFFD81B60),
                        onClick = {
                            showAttachSheet = false
                            showGiftBoxWrapDialog = true
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Social Media Picker Sheet (Stickers, GIFs, Emojis)
    if (showSocialMediaPicker) {
        SocialMediaPickerBottomSheet(
            onDismissRequest = { showSocialMediaPicker = false },
            initialTab = MediaPickerTab.STICKER,
            onStickerSelected = { sticker ->
                showSocialMediaPicker = false
                val rep = replyingToMessage
                replyingToMessage = null
                onSendMessage(
                    sticker.name.ifEmpty { "Sticker" },
                    "sticker",
                    sticker.mediaUrl.ifEmpty { sticker.thumbnailUrl },
                    0,
                    rep?.id,
                    rep?.message?.ifEmpty { rep.mediaType },
                    null
                )
                com.example.model.RepositoryManager.stickerRepository.recordStickerUsage(currentUserId, sticker)
            },
            onGifSelected = { gif ->
                showSocialMediaPicker = false
                val rep = replyingToMessage
                replyingToMessage = null
                onSendMessage(
                    "Shared a GIF",
                    "gif",
                    gif.fullUrl,
                    0,
                    rep?.id,
                    rep?.message?.ifEmpty { rep.mediaType },
                    null
                )
            },
            onEmojiSelected = { emoji ->
                messageText += emoji
                showSocialMediaPicker = false
            }
        )
    }

    // Instagram-style Gift Box Message Wrap Dialog
    if (showGiftBoxWrapDialog) {
        com.example.ui.components.GiftBoxWrapDialog(
            initialText = messageText,
            currentUserId = currentUserId,
            onDismiss = { showGiftBoxWrapDialog = false },
            onSendGiftBox = { wrappedText, styleKey, coins ->
                val rep = replyingToMessage
                replyingToMessage = null
                messageText = ""
                onSendMessage(
                    wrappedText,
                    "gift_box",
                    styleKey,
                    0,
                    rep?.id,
                    rep?.message?.ifEmpty { rep.mediaType },
                    if (coins > 0) coins.toString() else null
                )
            }
        )
    }

    // Media Preview Dialog (Send with optional caption)
    previewMediaUri?.let { uri ->
        MediaSendPreviewDialog(
            mediaUri = uri,
            isVideo = previewIsVideo,
            onSend = { caption ->
                val rep = replyingToMessage
                replyingToMessage = null
                val finalCaption = if (disappearingMediaMode != "Keep in Chat") "[$disappearingMediaMode] $caption".trim() else caption
                val pending = PendingUpload(
                    id = "pending_${UUID.randomUUID().toString().take(8)}",
                    uri = uri,
                    isVideo = previewIsVideo,
                    caption = finalCaption,
                    replyToId = rep?.id,
                    replyToText = rep?.message?.ifEmpty { rep.mediaType }
                )
                previewMediaUri = null
                startUpload(pending)
            },
            onDismiss = { previewMediaUri = null }
        )
    }

    // Full-Screen Photo Viewer
    fullScreenPhotoUrl?.let { url ->
        FullScreenPhotoViewer(
            imageUrl = url,
            title = otherUser.username,
            subtitle = fullScreenPhotoSubtitle,
            onDismiss = { fullScreenPhotoUrl = null }
        )
    }

    // Full-Screen Video Viewer
    fullScreenVideoUrl?.let { url ->
        FullScreenVideoViewer(
            videoUrl = url,
            title = otherUser.username,
            subtitle = fullScreenVideoSubtitle,
            onDismiss = { fullScreenVideoUrl = null }
        )
    }
}

@Composable
fun FlagshipMessageBubble(
    message: MessageEntity,
    isMine: Boolean,
    otherUser: User,
    onPhotoClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onReelClick: (String) -> Unit,
    onReply: () -> Unit,
    onReaction: (String) -> Unit,
    onEdit: (String) -> Unit = {},
    onDelete: (Boolean) -> Unit = {},
    currentUserId: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showReactionMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editedText by remember(message.message) { mutableStateOf(message.message) }
    var swipeOffsetX by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = (swipeOffsetX / 3f).dp)
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(swipeOffsetX) > 90f) {
                            onReply()
                        }
                        swipeOffsetX = 0f
                    },
                    onDragCancel = { swipeOffsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        swipeOffsetX = (swipeOffsetX + dragAmount).coerceIn(-160f, 160f)
                    }
                )
            },
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMine) {
            AsyncImage(
                model = otherUser.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${message.senderId}" },
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            // Reaction Bar Popup
            AnimatedVisibility(visible = showReactionMenu) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val emojis = listOf("❤️", "😂", "🔥", "👍", "😮", "😢")
                        emojis.forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 20.sp,
                                modifier = Modifier
                                    .clickable {
                                        onReaction(emoji)
                                        showReactionMenu = false
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                onReply()
                                showReactionMenu = false
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Reply,
                                contentDescription = "Reply",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (isMine && !message.isDeleted && message.mediaType == null) {
                            IconButton(
                                onClick = {
                                    showReactionMenu = false
                                    showEditDialog = true
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        if (!message.isDeleted) {
                            IconButton(
                                onClick = {
                                    showReactionMenu = false
                                    showDeleteDialog = true
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quoted Reply Preview Card
            if (!message.replyToText.isNullOrEmpty()) {
                Surface(
                    shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                    color = if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .background(if (isMine) Color.White else MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = message.replyToText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = if (isMine) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Deleted Message check or Main Message Content Box
            if (message.isDeleted) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Deleted",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "This message was deleted",
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (message.mediaType == "gift_box") {
                Box(
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { showReactionMenu = !showReactionMenu }
                        )
                    }
                ) {
                    com.example.ui.components.InstagramGiftBoxBubble(
                        message = message,
                        isMine = isMine,
                        onClaimCoins = { bonusCoins ->
                            if (!isMine && bonusCoins > 0 && currentUserId.isNotEmpty()) {
                                scope.launch {
                                    try {
                                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                            .collection("users").document(currentUserId)
                                            .update("coins", com.google.firebase.firestore.FieldValue.increment(bonusCoins.toLong()))
                                        android.widget.Toast.makeText(
                                            context,
                                            "🎉 Claimed +$bonusCoins Bonus Coins from Gift Box!",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (e: Exception) {}
                                }
                            }
                        }
                    )
                }
            } else {
                // Check if this is a shared post/reel (Instagram style)
                val isSharedMedia = !message.sharedVideoId.isNullOrEmpty() ||
                        message.mediaType == "shared_reel" ||
                        message.mediaType == "shared_post" ||
                        message.message.startsWith("Shared a reel", ignoreCase = true) ||
                        message.message.startsWith("Shared a post", ignoreCase = true) ||
                        message.message.contains("reels://") ||
                        message.message.contains("post://")

                if (isSharedMedia) {
                    val extractedPostId = message.sharedVideoId?.ifEmpty { null } ?: run {
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
                                    onLongPress = { showReactionMenu = !showReactionMenu }
                                )
                            }
                    ) {
                        InstagramSharedMediaBubble(
                            postId = extractedPostId,
                            mediaUrl = message.mediaUrl,
                            thumbnailUrl = message.thumbnailUrl,
                            mediaType = message.mediaType ?: "video",
                            messageCaption = message.message,
                            isMine = isMine,
                            onMediaClick = { pId, mUrl, isVid ->
                                if (pId.isNotEmpty()) {
                                    onReelClick(pId)
                                } else if (mUrl.isNotEmpty()) {
                                    if (isVid) onVideoClick(mUrl) else onPhotoClick(mUrl)
                                }
                            }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (isMine) 18.dp else 4.dp,
                                    bottomEnd = if (isMine) 4.dp else 18.dp
                                )
                            )
                            .background(
                                if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { showReactionMenu = !showReactionMenu }
                                )
                            }
                    ) {
                        Column {
                            // CASE 0: NOTE REPLY BADGE
                            if (message.mediaType == "NOTE_REPLY") {
                                Surface(
                                    color = if (isMine) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "💭 ${message.replyToText ?: "Replying to note"}",
                                            fontSize = 12.sp,
                                            color = if (isMine) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // CASE 1: PHOTO MESSAGE (Regular uploaded photo)
                            if (message.mediaType == "image" && !message.mediaUrl.isNullOrEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 160.dp, max = 280.dp)
                                        .clickable { onPhotoClick(message.mediaUrl) }
                                ) {
                                    SubcomposeAsyncImage(
                                        model = message.mediaUrl,
                                        contentDescription = "Shared Photo",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 160.dp, max = 280.dp),
                                        contentScale = ContentScale.Crop,
                                        loading = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(200.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(32.dp),
                                                    color = if (isMine) Color.White else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        error = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(180.dp)
                                                    .background(Color.DarkGray.copy(alpha = 0.5f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Default.BrokenImage, contentDescription = "Error", tint = Color.LightGray)
                                                    Text("Failed to load photo", color = Color.LightGray, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    )
                                }
                            }

                            // CASE 2: VIDEO MESSAGE (Regular uploaded video)
                            if (message.mediaType == "video" && !message.mediaUrl.isNullOrEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 160.dp, max = 260.dp)
                                        .clickable { onVideoClick(message.mediaUrl) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = message.thumbnailUrl ?: message.mediaUrl,
                                        contentDescription = "Video Thumbnail",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 160.dp, max = 260.dp),
                                        contentScale = ContentScale.Crop
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.65f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }

                                    if (message.videoDurationSeconds > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black.copy(alpha = 0.7f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = formatDurationSeconds(message.videoDurationSeconds),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // CASE 4: VOICE MESSAGE (with optional Voice Changer Filter pitch/speed)
                            if ((message.mediaType == "voice" || message.mediaType == "audio") && !message.mediaUrl.isNullOrEmpty()) {
                                val parsedVoiceFilter = remember(message.message) {
                                    ChatUpgradeCatalog.parseVoiceFilterFromCaption(message.message)
                                }
                                VoiceMessageBubbleItem(
                                    audioUrl = message.mediaUrl,
                                    durationSeconds = message.videoDurationSeconds,
                                    isMine = isMine,
                                    voiceFilter = parsedVoiceFilter
                                )
                            }

                            // CASE 5: STICKER OR GIF MESSAGE
                            if ((message.mediaType == "sticker" || message.mediaType == "gif") && !message.mediaUrl.isNullOrEmpty()) {
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
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .sizeIn(maxWidth = 180.dp, maxHeight = 180.dp)
                                ) {
                                    AsyncImage(
                                        model = coil.request.ImageRequest.Builder(context)
                                            .data(message.mediaUrl)
                                            .crossfade(true)
                                            .build(),
                                        imageLoader = imageLoader,
                                        contentDescription = if (message.mediaType == "sticker") "Sticker" else "GIF",
                                        modifier = Modifier
                                            .sizeIn(maxWidth = 180.dp, maxHeight = 180.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }

                            // Caption or Text Message (strip internal [VANISH] and [VOICE:*] tags cleanly)
                            val isVanishTagged = message.message.contains("[VANISH]")
                            val cleanDisplayText = remember(message.message) {
                                ChatUpgradeCatalog.stripSystemTags(message.message)
                            }
                            val isVoiceMedia = message.mediaType == "voice" || message.mediaType == "audio"
                            val showText = cleanDisplayText.isNotBlank() &&
                                    !isVoiceMedia &&
                                    !(message.mediaType != null && (cleanDisplayText == "📷 Photo" || cleanDisplayText == "🎥 Video" || cleanDisplayText == "🎙️ Voice message" || message.mediaType == "sticker" || message.mediaType == "gif"))

                            if (isVanishTagged) {
                                Text(
                                    text = "👻 Vanish",
                                    color = Color(0xFFD8B4FE),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 14.dp, top = 6.dp)
                                )
                            }

                            if (showText) {
                                Text(
                                    text = cleanDisplayText,
                                    color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }

                            val isStoryMentionMsg = !isMine && (
                                message.message.contains("mentioned you in", ignoreCase = true) ||
                                    message.mediaType.equals("STORY_MENTION", ignoreCase = true) ||
                                    (message.message.contains("story", ignoreCase = true) && !message.mediaUrl.isNullOrEmpty())
                                )
                            var showStoryRepostCreator by remember { mutableStateOf(false) }
                            if (isStoryMentionMsg) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF0095F6),
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .clickable {
                                            triggerCrispMicroHaptic(context, strong = true)
                                            showStoryRepostCreator = true
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddCircleOutline,
                                            contentDescription = "Add to your story",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Add this to your story",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                            if (showStoryRepostCreator) {
                                val targetMedia = message.mediaUrl?.ifBlank { message.thumbnailUrl ?: "" } ?: ""
                                StoryCreatorDialog(
                                    onDismiss = { showStoryRepostCreator = false },
                                    initialMediaUri = if (targetMedia.isNotBlank()) Uri.parse(targetMedia) else null,
                                    repostVideoUrl = targetMedia.ifBlank { null },
                                    mentionedUserId = otherUser.id,
                                    mentionedUsername = otherUser.username,
                                    initialText = "@${otherUser.username}",
                                    isReback = true
                                )
                            }
                        }
                    }
                }
            }

            // Attached Reaction Badge
            if (!message.reaction.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .offset(y = (-8).dp)
                        .padding(horizontal = 6.dp)
                        .shadow(2.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onReaction(message.reaction) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = message.reaction, fontSize = 13.sp)
                }
            }

            // Timestamp and Status Checkmarks
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, end = 2.dp)
            ) {
                Text(
                    text = formatTime(message.timestamp) + if (message.isEdited) " • Edited" else "",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                if (isMine) {
                    Spacer(modifier = Modifier.width(4.dp))
                    when (message.status) {
                        "sending" -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = Color.Gray
                            )
                        }
                        "read" -> {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Read",
                                tint = Color(0xFF34B7F1),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        "delivered" -> {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Delivered",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        "failed" -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Failed",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        else -> { // "sent"
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Sent",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Message", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Message") },
                    maxLines = 4
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = editedText.trim()
                        if (trimmed.isNotBlank()) {
                            onEdit(trimmed)
                            showEditDialog = false
                        }
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Message?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this message?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (isMine) {
                        TextButton(
                            onClick = {
                                onDelete(true)
                                showDeleteDialog = false
                            }
                        ) {
                            Text("Delete for everyone", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(
                        onClick = {
                            onDelete(false)
                            showDeleteDialog = false
                        }
                    ) {
                        Text("Delete for me", color = Color(0xFFE53935))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Bubble displaying playable voice message with waveform simulation
 */
@Composable
fun VoiceMessageBubbleItem(
    audioUrl: String,
    durationSeconds: Int,
    isMine: Boolean,
    voiceFilter: VoiceFilterSpec = ChatUpgradeCatalog.VOICE_FILTERS.first()
) {
    val voicePlayer = remember { com.example.utils.VoicePlayerHelper() }
    val isPlaying by voicePlayer.isPlaying.collectAsState()
    val currentPositionMs by voicePlayer.currentPositionMs.collectAsState()
    val totalDurationMs by voicePlayer.totalDurationMs.collectAsState()

    DisposableEffect(audioUrl) {
        onDispose {
            voicePlayer.release()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (voiceFilter.id != "original") {
            Text(
                text = "${voiceFilter.emoji} ${voiceFilter.name} Filter",
                color = if (isMine) Color(0xFFFFF59D) else Color(0xFF00F0FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, top = 6.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    voicePlayer.playOrPause(
                        url = audioUrl,
                        pitch = voiceFilter.pitch,
                        speed = voiceFilter.speed
                    )
                },
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (isMine) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause Voice Note" else "Play Voice Note",
                tint = if (isMine) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val heights = listOf(8, 14, 20, 12, 18, 22, 16, 10, 14, 24, 18, 12, 20, 16, 12, 8, 18, 22, 14, 10)
                val progress = if (totalDurationMs > 0) (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
                val activeCount = (heights.size * progress).toInt()

                heights.forEachIndexed { index, h ->
                    val isActive = index <= activeCount
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(h.dp)
                            .background(
                                color = if (isActive) {
                                    if (isMine) Color.White else MaterialTheme.colorScheme.primary
                                } else {
                                    if (isMine) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                },
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val displayDuration = if (isPlaying && currentPositionMs > 0) {
                formatDurationSeconds(currentPositionMs / 1000)
            } else {
                formatDurationSeconds(durationSeconds)
            }

            Text(
                text = displayDuration,
                fontSize = 11.sp,
                color = if (isMine) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        }
    }
}

/**
 * Bubble shown when media is being uploaded locally or failed to upload.
 */
@Composable
fun PendingUploadBubble(
    pending: PendingUpload,
    progress: Int,
    isFailed: Boolean,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SubcomposeAsyncImage(
                            model = pending.uri,
                            contentDescription = "Pending Media",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentScale = ContentScale.Crop
                        )

                        // Dark overlay with indicator or retry
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isFailed) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { onRetry() }
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Upload failed. Tap to retry", color = Color.White, fontSize = 12.sp)
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        progress = { progress / 100f },
                                        color = Color.White,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("$progress%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (pending.caption.isNotBlank()) {
                        Text(
                            text = pending.caption,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isFailed) "Failed" else "Sending...",
                fontSize = 11.sp,
                color = if (isFailed) MaterialTheme.colorScheme.error else Color.Gray
            )
        }
    }
}

@Composable
fun AttachmentOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: Color,
    tintColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tintColor,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
