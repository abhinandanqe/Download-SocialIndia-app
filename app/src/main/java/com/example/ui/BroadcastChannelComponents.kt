package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.api.ITunesTrack
import com.example.api.MusicClient
import com.example.model.BroadcastChannel
import com.example.model.ChannelAnalyticsData
import com.example.model.RepositoryManager
import com.example.repository.FlagshipFeaturesRepository
import com.example.utils.ExoPlayerUtils
import com.example.utils.GlobalAudioPreviewManager
import com.example.utils.VoicePlayerHelper
import com.example.utils.VoiceRecorderHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

// Categories for Broadcast Channels
val BROADCAST_CHANNEL_CATEGORIES = listOf(
    "Creator", "Entertainment", "Music", "Technology", "Gaming", "News", "Lifestyle", "Art", "Education", "Comedy"
)

/**
 * 1. Comprehensive Create Channel Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBroadcastChannelDialog(
    currentUserId: String,
    currentUsername: String,
    currentUserAvatar: String,
    onDismiss: () -> Unit,
    onChannelCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Creator") }
    var visibilityType by remember { mutableStateOf("PUBLIC") } // "PUBLIC", "FOLLOWERS_ONLY"
    var welcomeMessage by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var uploadedImageUrl by remember { mutableStateOf("") }

    var isSubmitting by remember { mutableStateOf(false) }
    var isUploadingImage by remember { mutableStateOf(false) }
    var isCheckingHandle by remember { mutableStateOf(false) }
    var isHandleAvailable by remember { mutableStateOf<Boolean?>(null) }

    // Image Picker
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            isUploadingImage = true
            scope.launch {
                val uploaded = RepositoryManager.mediaRepository.uploadMedia(uri)
                isUploadingImage = false
                if (!uploaded.isNullOrEmpty()) {
                    uploadedImageUrl = uploaded
                } else {
                    Toast.makeText(context, "Image upload failed, using default", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Debounced check for unique username
    LaunchedEffect(handle) {
        val clean = handle.trim().lowercase().removePrefix("@")
        if (clean.length >= 3) {
            isCheckingHandle = true
            delay(500)
            val available = FlagshipFeaturesRepository.isChannelUsernameAvailable(clean)
            isHandleAvailable = available
            isCheckingHandle = false
        } else {
            isHandleAvailable = null
            isCheckingHandle = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Podcasts,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Broadcast Channel", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Channel Cover/Profile Image
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = !isUploadingImage && !isSubmitting) {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUri != null) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Channel Cover",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (uploadedImageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = uploadedImageUrl,
                            contentDescription = "Channel Cover",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                contentDescription = "Add Cover",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Text("Cover", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (isUploadingImage) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        }
                    }
                }

                // Channel Name
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 40) title = it },
                    label = { Text("Channel Name *") },
                    placeholder = { Text("e.g. Daily Tech Highlights") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("channel_name_input")
                )

                // Channel Handle / Username
                OutlinedTextField(
                    value = handle,
                    onValueChange = { input ->
                        val sanitized = input.filter { it.isLetterOrDigit() || it == '_' }
                        if (sanitized.length <= 25) handle = sanitized
                    },
                    label = { Text("Channel Handle (@handle)") },
                    placeholder = { Text("tech_highlights") },
                    prefix = { Text("@") },
                    singleLine = true,
                    trailingIcon = {
                        if (isCheckingHandle) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else if (isHandleAvailable == true) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Available", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                        } else if (isHandleAvailable == false) {
                            Icon(Icons.Default.Cancel, contentDescription = "Unavailable", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    },
                    supportingText = {
                        if (isHandleAvailable == false) {
                            Text("This handle is already taken", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        } else if (isHandleAvailable == true) {
                            Text("Handle is available", color = Color(0xFF4CAF50), fontSize = 11.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 160) description = it },
                    label = { Text("Description") },
                    placeholder = { Text("What will you broadcast in this channel?") },
                    maxLines = 3,
                    supportingText = { Text("${description.length}/160", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Selection
                Text("Category", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(BROADCAST_CHANNEL_CATEGORIES) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 12.sp) }
                        )
                    }
                }

                // Visibility Selector (Public vs Followers Only)
                ChannelVisibilitySelector(
                    selectedType = visibilityType,
                    onSelect = { visibilityType = it }
                )

                // Optional Welcome Message
                OutlinedTextField(
                    value = welcomeMessage,
                    onValueChange = { if (it.length <= 120) welcomeMessage = it },
                    label = { Text("Welcome Message (Optional)") },
                    placeholder = { Text("Welcome to my broadcast channel!") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        Toast.makeText(context, "Please enter a channel name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (isHandleAvailable == false) {
                        Toast.makeText(context, "Please choose a different handle", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSubmitting = true
                    scope.launch {
                        val isFollowersOnly = visibilityType == "FOLLOWERS_ONLY"
                        val newChannel = BroadcastChannel(
                            creatorId = currentUserId,
                            creatorUsername = currentUsername,
                            creatorAvatarUrl = currentUserAvatar,
                            title = title.trim(),
                            username = handle.trim().lowercase().removePrefix("@"),
                            description = description.trim(),
                            category = category,
                            imageUrl = uploadedImageUrl,
                            visibilityType = visibilityType,
                            visibility = if (isFollowersOnly) "followers" else "public",
                            whoCanJoin = if (isFollowersOnly) "followers" else "everyone",
                            welcomeMessage = welcomeMessage.trim(),
                            membersCount = 1
                        )
                        val id = FlagshipFeaturesRepository.createBroadcastChannel(newChannel)
                        isSubmitting = false
                        if (id.isNotEmpty()) {
                            Toast.makeText(context, "Broadcast Channel created!", Toast.LENGTH_SHORT).show()
                            onChannelCreated(id)
                        } else {
                            Toast.makeText(context, "Failed to create channel", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isSubmitting && !isUploadingImage && title.isNotBlank() && isHandleAvailable != false,
                modifier = Modifier.testTag("submit_create_channel_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Create Channel")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        }
    )
}

/**
 * 2. Channel Settings Dialog (Edit Information & Category)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastChannelSettingsDialog(
    channel: BroadcastChannel,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(channel.title) }
    var description by remember { mutableStateOf(channel.description) }
    var category by remember { mutableStateOf(channel.category.ifEmpty { "Creator" }) }
    var visibilityType by remember { mutableStateOf(if (channel.isFollowersOnly) "FOLLOWERS_ONLY" else "PUBLIC") }
    var imageUrl by remember { mutableStateOf(channel.imageUrl) }
    var isSaving by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val uploaded = RepositoryManager.mediaRepository.uploadMedia(uri)
                if (!uploaded.isNullOrEmpty()) {
                    imageUrl = uploaded
                    Toast.makeText(context, "Cover updated!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Channel Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Change Cover
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Channel Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(BROADCAST_CHANNEL_CATEGORIES) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 12.sp) }
                        )
                    }
                }

                ChannelVisibilitySelector(
                    selectedType = visibilityType,
                    onSelect = { visibilityType = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    isSaving = true
                    scope.launch {
                        val isFollowersOnly = visibilityType == "FOLLOWERS_ONLY"
                        val success = FlagshipFeaturesRepository.updateChannelDetails(
                            channelId = channel.id,
                            title = title,
                            description = description,
                            category = category,
                            imageUrl = imageUrl,
                            visibility = if (isFollowersOnly) "followers" else "public",
                            visibilityType = visibilityType
                        )
                        isSaving = false
                        if (success) {
                            Toast.makeText(context, "Channel settings saved", Toast.LENGTH_SHORT).show()
                            onUpdated()
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Failed to save settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isSaving && title.isNotBlank()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save Changes")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * 3. Channel Analytics Sheet (Real Firestore Metrics)
 */
@Composable
fun BroadcastChannelAnalyticsSheet(
    channelId: String,
    onDismiss: () -> Unit
) {
    var analyticsData by remember { mutableStateOf<ChannelAnalyticsData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(channelId) {
        isLoading = true
        analyticsData = FlagshipFeaturesRepository.fetchChannelAnalytics(channelId, daysFilter = 30)
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Channel Analytics", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    val data = analyticsData ?: ChannelAnalyticsData()

                    // Metrics Grid
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AnalyticsMetricCard(
                                title = "Total Members",
                                value = data.totalMembers.toString(),
                                icon = Icons.Default.Group,
                                modifier = Modifier.weight(1f)
                            )
                            AnalyticsMetricCard(
                                title = "Update Views",
                                value = data.postViews.toString(),
                                icon = Icons.Default.Visibility,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AnalyticsMetricCard(
                                title = "Total Reactions",
                                value = data.reactions.toString(),
                                icon = Icons.Default.Favorite,
                                modifier = Modifier.weight(1f)
                            )
                            AnalyticsMetricCard(
                                title = "Poll Votes",
                                value = data.pollVotes.toString(),
                                icon = Icons.Default.Poll,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Engagement Rate Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Engagement Rate", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("${data.engagementRate}%", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun AnalyticsMetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 4. Voice Recorder Dialog for Channel Updates
 */
@Composable
fun BroadcastChannelVoiceDialog(
    onDismiss: () -> Unit,
    onSendVoice: (voiceFileUrl: String, durationSeconds: Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorderHelper = remember { VoiceRecorderHelper(context) }
    val playerHelper = remember { VoicePlayerHelper() }

    val isRecording by recorderHelper.isRecording.collectAsStateWithLifecycle()
    val durationSeconds by recorderHelper.recordingDurationSeconds.collectAsStateWithLifecycle()
    val isPlayingAudio by playerHelper.isPlaying.collectAsStateWithLifecycle()
    val playbackPosition by playerHelper.currentPositionMs.collectAsStateWithLifecycle()
    val playbackDuration by playerHelper.totalDurationMs.collectAsStateWithLifecycle()

    var recordedFile by remember { mutableStateOf<File?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    // Pulsing animation for recording red dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    DisposableEffect(Unit) {
        onDispose {
            if (recorderHelper.isRecording.value) {
                recorderHelper.cancelRecording()
            }
            recorderHelper.release()
            playerHelper.release()
        }
    }

    Dialog(onDismissRequest = { if (!isUploading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Voice Broadcast", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss, enabled = !isUploading) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Voice Recording Area
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = alpha))
                        )
                    } else {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                // Timer
                val minutes = durationSeconds / 60
                val seconds = durationSeconds % 60
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )

                // Status text / Preview Player
                if (recordedFile != null && !isRecording) {
                    // Playback Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val file = recordedFile
                                if (file != null) {
                                    playerHelper.playOrPause(file.absolutePath)
                                }
                            }
                        ) {
                            Icon(
                                if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Progress slider
                        val progress = if (playbackDuration > 0) (playbackPosition.toFloat() / playbackDuration.toFloat()).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${durationSeconds}s",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isRecording) {
                        Button(
                            onClick = {
                                val file = recorderHelper.stopRecording()
                                recordedFile = file
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop")
                        }
                    } else if (recordedFile == null) {
                        Button(
                            onClick = {
                                val started = recorderHelper.startRecording()
                                if (!started) {
                                    Toast.makeText(context, "Microphone access required", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Record")
                        }
                    } else {
                        // Re-record Button
                        OutlinedButton(
                            onClick = {
                                playerHelper.stop()
                                recorderHelper.cancelRecording()
                                recordedFile = null
                                recorderHelper.startRecording()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isUploading
                        ) {
                            Text("Re-record")
                        }

                        // Send Voice Note
                        Button(
                            onClick = {
                                val file = recordedFile ?: return@Button
                                playerHelper.stop()
                                isUploading = true
                                scope.launch {
                                    val url = RepositoryManager.mediaRepository.uploadMedia(Uri.fromFile(file))
                                    isUploading = false
                                    if (!url.isNullOrEmpty()) {
                                        onSendVoice(url, durationSeconds)
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "Failed to upload audio", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isUploading
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Broadcast")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5. Music Picker Dialog for Channel Updates (S45 Music API)
 */
@Composable
fun BroadcastChannelMusicPickerDialog(
    onDismiss: () -> Unit,
    onSelectTrack: (ITunesTrack) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<ITunesTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var playingTrackId by remember { mutableStateOf<Long?>(null) }

    // Initial search
    LaunchedEffect(Unit) {
        isLoading = true
        val res = MusicClient.searchIndianTracks("Bollywood trending")
        tracks = res.results
        isLoading = false
    }

    // Debounced search
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            isLoading = true
            delay(500)
            val res = MusicClient.searchIndianTracks(searchQuery)
            tracks = res.results
            isLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            GlobalAudioPreviewManager.stop()
        }
    }

    Dialog(
        onDismissRequest = {
            GlobalAudioPreviewManager.stop()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler {
            GlobalAudioPreviewManager.stop()
            onDismiss()
        }

        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        GlobalAudioPreviewManager.stop()
                        onDismiss()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Attach Music", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                }

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search songs, artists...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Track List
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (tracks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        Text("No tracks found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tracks, key = { it.id ?: (it.name.hashCode().toLong()) }) { track ->
                            val isThisPlaying = playingTrackId == track.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isThisPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Artwork
                                    AsyncImage(
                                        model = track.artworkUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(track.name ?: "Track", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artistName ?: "Artist", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    // Preview Play Button
                                    val preview = track.previewUrl
                                    if (!preview.isNullOrBlank()) {
                                        IconButton(
                                            onClick = {
                                                if (isThisPlaying) {
                                                    GlobalAudioPreviewManager.pause()
                                                    playingTrackId = null
                                                } else {
                                                    GlobalAudioPreviewManager.playOrToggle(context, preview)
                                                    playingTrackId = track.id
                                                }
                                            }
                                        ) {
                                            Icon(
                                                if (isThisPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                                contentDescription = "Preview",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }

                                    // Select Button
                                    Button(
                                        onClick = {
                                            GlobalAudioPreviewManager.stop()
                                            onSelectTrack(track)
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Select", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 6. Create Poll or Quiz Dialog
 */
@Composable
fun BroadcastChannelPollOrQuizDialog(
    onDismiss: () -> Unit,
    onCreate: (question: String, options: List<String>, isQuiz: Boolean, correctOptionIndex: Int) -> Unit
) {
    val context = LocalContext.current
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var isQuiz by remember { mutableStateOf(false) }
    var correctOptionIndex by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isQuiz) "Create Channel Quiz" else "Create Channel Poll",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Quiz Mode Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Quiz Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Has one correct answer", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isQuiz, onCheckedChange = { isQuiz = it })
                }

                // Question
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Question *") },
                    placeholder = { Text(if (isQuiz) "Which year was Social India founded?" else "What should we build next?") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                // Options List
                Text("Options (2-6)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                options.forEachIndexed { index, optionText ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isQuiz) {
                            RadioButton(
                                selected = correctOptionIndex == index,
                                onClick = { correctOptionIndex = index }
                            )
                        }

                        OutlinedTextField(
                            value = optionText,
                            onValueChange = { newTxt ->
                                options = options.toMutableList().also { it[index] = newTxt }
                            },
                            placeholder = { Text("Option ${index + 1}${if (isQuiz && correctOptionIndex == index) " (Correct Answer)" else ""}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        if (options.size > 2) {
                            IconButton(
                                onClick = {
                                    options = options.toMutableList().also { it.removeAt(index) }
                                    if (correctOptionIndex >= options.size) {
                                        correctOptionIndex = options.size - 1
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove Option", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // Add option button
                if (options.size < 6) {
                    TextButton(
                        onClick = { options = options + "" },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Option")
                    }
                }

                // Confirm Button
                Button(
                    onClick = {
                        val validOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
                        if (question.isBlank() || validOptions.size < 2) {
                            Toast.makeText(context, "Please enter question and at least 2 options", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onCreate(question.trim(), validOptions, isQuiz, if (isQuiz) correctOptionIndex.coerceIn(0, validOptions.size - 1) else -1)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isQuiz) "Post Quiz" else "Post Poll", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 7. Channel Share Sheet Dialog (Story, Chat, Deep link, System Share)
 */
@Composable
fun BroadcastChannelShareDialog(
    channel: BroadcastChannel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val channelLink = "https://socialindia.app/channel/${channel.id}"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Share Channel", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Preview Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (channel.imageUrl.isNotEmpty()) {
                                AsyncImage(model = channel.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Outlined.Podcasts, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(channel.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${channel.membersCount} members • ${channel.category}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Share Options
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Copy Link
                    Surface(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Channel Link", channelLink)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Channel link copied!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Copy Channel Link", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }

                    // System Share
                    Surface(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Join my broadcast channel \"${channel.title}\" on Social India:\n$channelLink")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Channel via"))
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Share via Other Apps...", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 8. Fullscreen Media Viewer Dialog (Zoomable Image or Video Player)
 */
@Composable
fun FullscreenMediaViewerDialog(
    mediaUrl: String,
    isVideo: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isMuted by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (scale > 1f) {
            offset += offsetChange
        } else {
            offset = Offset.Zero
        }
    }

    DisposableEffect(mediaUrl, isVideo) {
        if (isVideo) {
            val player = ExoPlayerUtils.createExoPlayer(context).apply {
                setMediaItem(MediaItem.fromUri(mediaUrl))
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ALL
            }
            exoPlayer = player
        }
        onDispose {
            exoPlayer?.let { ExoPlayerUtils.safeRelease(it) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (isVideo && exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Zoomable Image
                AsyncImage(
                    model = mediaUrl,
                    contentDescription = "Full image",
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state = transformState)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )
            }

            // Close Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

/**
 * Modern Selection UI for Broadcast Channel Visibility
 * Options: PUBLIC vs FOLLOWERS_ONLY
 */
@Composable
fun ChannelVisibilitySelector(
    selectedType: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Visibility", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

        // Public Option Card
        val isPublic = selectedType.equals("PUBLIC", ignoreCase = true)
        Surface(
            onClick = { onSelect("PUBLIC") },
            shape = RoundedCornerShape(12.dp),
            color = if (isPublic) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = if (isPublic) 2.dp else 1.dp,
                color = if (isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("visibility_public_option")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Public,
                        contentDescription = null,
                        tint = if (isPublic) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Public",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Anyone can discover and join",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isPublic) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Followers Only Option Card
        val isFollowers = selectedType.equals("FOLLOWERS_ONLY", ignoreCase = true)
        Surface(
            onClick = { onSelect("FOLLOWERS_ONLY") },
            shape = RoundedCornerShape(12.dp),
            color = if (isFollowers) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = if (isFollowers) 2.dp else 1.dp,
                color = if (isFollowers) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("visibility_followers_only_option")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isFollowers) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Group,
                        contentDescription = null,
                        tint = if (isFollowers) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Followers Only",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Only followers of the creator can join",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isFollowers) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Follower-Required Channel Preview State
 * Rendered when a channel is FOLLOWERS_ONLY and the user is not following the creator.
 * Protects channel updates with zero content leaks.
 */
@Composable
fun FollowersOnlyRequiredCard(
    channel: BroadcastChannel,
    isFollowing: Boolean,
    isFollowLoading: Boolean,
    onFollowCreator: () -> Unit,
    onJoinChannel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Channel Cover / Profile
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (channel.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = channel.imageUrl,
                    contentDescription = "Channel Cover",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Outlined.Podcasts,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Channel Title & Verified
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = channel.title,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            if (channel.isVerified) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.Verified,
                    contentDescription = "Verified Channel",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (channel.username.isNotEmpty()) {
            Text(
                text = "@${channel.username}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Badge: FOLLOWERS ONLY
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "FOLLOWERS ONLY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (channel.description.isNotEmpty()) {
            Text(
                text = channel.description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = "${channel.membersCount} members • ${channel.category.ifEmpty { "Creator" }}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Protected Gate Box
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isFollowing) Icons.Default.Check else Icons.Outlined.PersonAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isFollowing) "You are following this creator!" else "Follow this creator to join their broadcast channel.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isFollowing) "You are now eligible to join this channel and view all protected broadcast updates." else "Updates, media notes, polls and exclusive broadcasts in this channel are restricted to followers of @${channel.creatorUsername}.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!isFollowing) {
                    Button(
                        onClick = onFollowCreator,
                        enabled = !isFollowLoading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("follow_creator_to_join_button")
                    ) {
                        if (isFollowLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FOLLOW @${channel.creatorUsername.uppercase().ifEmpty { "CREATOR" }}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onJoinChannel,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("join_followers_only_channel_button")
                    ) {
                        Icon(Icons.Outlined.Podcasts, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "JOIN CHANNEL",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Blocked / Unavailable Channel Screen
 */
@Composable
fun BroadcastChannelBlockedView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Block,
                contentDescription = "Blocked",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Channel Unavailable",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This broadcast channel is not available due to privacy or account settings.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onBack,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Go Back")
        }
    }
}

/**
 * Creator Profile Broadcast Channel Card Entry (Requirement 11)
 */
@Composable
fun BroadcastChannelProfileEntry(
    channel: BroadcastChannel,
    currentUserId: String,
    isFollowing: Boolean,
    onFollow: () -> Unit,
    onJoin: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOwner = channel.creatorId == currentUserId
    val isMember by FlagshipFeaturesRepository.isChannelMember(channel.id, currentUserId)
        .collectAsStateWithLifecycle(initialValue = isOwner)
    val isFollowersOnly = channel.isFollowersOnly

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = modifier.fillMaxWidth().testTag("profile_broadcast_channel_card")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (channel.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = channel.imageUrl,
                        contentDescription = "Channel Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Outlined.Podcasts,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Broadcast Channel",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (channel.isVerified) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isFollowersOnly) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = if (isFollowersOnly) "Followers only" else "Public",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            color = if (isFollowersOnly) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = "${channel.membersCount} members",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Button
            if (isOwner || isMember) {
                OutlinedButton(
                    onClick = onOpen,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Open", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else if (isFollowersOnly && !isFollowing) {
                Button(
                    onClick = onFollow,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Follow", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onJoin,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Join", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
