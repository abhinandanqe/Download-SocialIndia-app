package com.example.ui

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.RepositoryManager
import com.example.model.SavedCollection
import com.example.model.Video
import com.example.repository.AccountTransparencyInfo
import com.example.repository.FlagshipFeaturesRepository
import com.example.utils.NumberFormatUtils
import com.example.utils.ReelsPreferencesManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsOptionsBottomSheet(
    video: Video,
    currentUserId: String,
    isRealUser: Boolean,
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    onDismiss: () -> Unit,
    onShareClick: () -> Unit,
    onSaveToggle: () -> Unit,
    onNotInterested: () -> Unit,
    onHideAccount: () -> Unit,
    onDeleteVideo: () -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onRemixClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isOwner = currentUserId.isNotEmpty() && currentUserId == video.userId

    // Preferences
    val currentSpeed by ReelsPreferencesManager.playbackSpeed.collectAsStateWithLifecycle()
    val autoScrollEnabled by ReelsPreferencesManager.autoScrollEnabled.collectAsStateWithLifecycle()
    val loopVideo by ReelsPreferencesManager.loopVideo.collectAsStateWithLifecycle()
    val captionsEnabled by ReelsPreferencesManager.captionsEnabled.collectAsStateWithLifecycle()
    val dataSaverEnabled by ReelsPreferencesManager.dataSaverEnabled.collectAsStateWithLifecycle()
    val isFullScreen by ReelsPreferencesManager.isFullScreen.collectAsStateWithLifecycle()
    val showEarnButton by ReelsPreferencesManager.showEarnButton.collectAsStateWithLifecycle()

    // Sub-dialog states
    var showReportDialog by remember { mutableStateOf(false) }
    var showAboutAccountDialog by remember { mutableStateOf(false) }
    var showInsightsDialog by remember { mutableStateOf(false) }
    var showEditCaptionDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var showCollectionsDialog by remember { mutableStateOf(false) }
    var showRemixSheet by remember { mutableStateOf(false) }
    var showContentPrefDialog by remember { mutableStateOf(false) }
    var showWhyAmISeeingThisDialog by remember { mutableStateOf(false) }
    var whySeeingThisReason by remember { mutableStateOf<com.example.ai.WhyAmISeeingThisReason?>(null) }

    // Owner toggles
    var allowComments by remember { mutableStateOf(true) }
    var allowRemix by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18181A),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.25f),
                width = 36.dp,
                height = 4.dp
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isOwner) "Manage Your Reel" else "Reel Options",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isOwner) "Insights, settings & controls" else "@${video.user.username}",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. TOP QUICK ACTIONS ROW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Share
                        ReelQuickActionButton(
                            icon = Icons.Default.Share,
                            label = "Share",
                            onClick = {
                                onShareClick()
                                onDismiss()
                            }
                        )

                        // Copy Link
                        ReelQuickActionButton(
                            icon = Icons.Default.Link,
                            label = "Copy Link",
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val shareUrl = "https://socialindia.app/reel/${video.id}"
                                val clip = ClipData.newPlainText("Social India Reel", shareUrl)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Reel link copied to clipboard!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        )

                        // Save / Collection
                        ReelQuickActionButton(
                            icon = if (video.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            label = if (video.isSaved) "Saved" else "Save",
                            iconTint = if (video.isSaved) Color(0xFFFE2C55) else Color.White,
                            onClick = {
                                if (!isRealUser) {
                                    Toast.makeText(context, "Please log in to save reels!", Toast.LENGTH_SHORT).show()
                                    return@ReelQuickActionButton
                                }
                                onSaveToggle()
                                if (!video.isSaved) {
                                    // If saved, offer collection dialog
                                    showCollectionsDialog = true
                                }
                            }
                        )

                        // Remix
                        ReelQuickActionButton(
                            icon = Icons.Default.Repeat,
                            label = "Remix",
                            onClick = {
                                showRemixSheet = true
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                }
            }

            // 2. PLAYBACK CONTROLS SECTION
            item {
                SectionHeader("Playback Controls")

                // Playback Speed Selector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Playback Speed",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (currentSpeed != 1.0f) {
                            Text(
                                text = "Reset",
                                color = Color(0xFF2196F3),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable { ReelsPreferencesManager.setPlaybackSpeed(1.0f) }
                                    .padding(4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        speeds.forEach { speed ->
                            val isSelected = currentSpeed == speed
                            val label = if (speed == 1.0f) "1x" else "${speed}x"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFFFE2C55) else Color.White.copy(alpha = 0.08f))
                                    .clickable {
                                        ReelsPreferencesManager.setPlaybackSpeed(speed)
                                        Toast.makeText(context, "Speed set to $label", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Sound / Mute Toggle in 3 Dots Options
                ReelSwitchItem(
                    icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    title = if (isMuted) "Audio Muted" else "Audio Playing",
                    subtitle = if (isMuted) "Audio is muted. Tap to play sound" else "Audio is playing. Tap to mute sound",
                    checked = !isMuted,
                    onCheckedChange = {
                        onToggleMute()
                    }
                )

                // Auto Scroll Toggle
                ReelSwitchItem(
                    icon = Icons.Default.PlayCircleOutline,
                    title = "Auto Scroll",
                    subtitle = "Automatically scrolls to next reel when video ends",
                    checked = autoScrollEnabled,
                    onCheckedChange = {
                        val enabled = ReelsPreferencesManager.toggleAutoScroll()
                        Toast.makeText(context, if (enabled) "Auto Scroll: ON" else "Auto Scroll: OFF", Toast.LENGTH_SHORT).show()
                    }
                )

                // Full Screen Toggle
                ReelSwitchItem(
                    icon = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    title = "Full Screen Mode",
                    subtitle = "Hide controls and borders for an immersive view",
                    checked = isFullScreen,
                    onCheckedChange = {
                        ReelsPreferencesManager.toggleFullScreen()
                        onDismiss()
                    }
                )

                // Earn Button Toggle
                ReelSwitchItem(
                    icon = Icons.Default.MonetizationOn,
                    title = "Show 'Earn' Button",
                    subtitle = "Show or hide the Earn button on top right of reels",
                    checked = showEarnButton,
                    onCheckedChange = {
                        val enabled = ReelsPreferencesManager.toggleShowEarnButton()
                        Toast.makeText(context, if (enabled) "Earn button: Visible" else "Earn button: Hidden", Toast.LENGTH_SHORT).show()
                    }
                )

                // Loop Video Toggle
                ReelSwitchItem(
                    icon = Icons.Default.Loop,
                    title = "Loop Video",
                    subtitle = "Repeat this reel continuously",
                    checked = loopVideo,
                    onCheckedChange = {
                        ReelsPreferencesManager.toggleLoopVideo()
                    }
                )

                // Captions Toggle
                ReelSwitchItem(
                    icon = Icons.Default.ClosedCaption,
                    title = "Captions",
                    subtitle = "Show subtitles & video caption overlay",
                    checked = captionsEnabled,
                    onCheckedChange = {
                        val enabled = ReelsPreferencesManager.toggleCaptions()
                        Toast.makeText(context, if (enabled) "Captions enabled" else "Captions disabled", Toast.LENGTH_SHORT).show()
                    }
                )

                // Data Saver Toggle
                ReelSwitchItem(
                    icon = Icons.Default.DataSaverOn,
                    title = "Data Saver",
                    subtitle = "Reduce video resolution and prefetching to save data",
                    checked = dataSaverEnabled,
                    onCheckedChange = {
                        val enabled = ReelsPreferencesManager.toggleDataSaver()
                        Toast.makeText(context, if (enabled) "Data Saver enabled" else "Data Saver disabled", Toast.LENGTH_SHORT).show()
                    }
                )

                // Download / Offline Save
                ReelActionListItem(
                    icon = Icons.Default.Download,
                    title = "Download Reel",
                    subtitle = "Save video to your device gallery",
                    onClick = {
                        try {
                            val request = DownloadManager.Request(Uri.parse(video.videoUrl))
                            request.setTitle("Social India Reel")
                            request.setDescription("Downloading video...")
                            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            request.setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_MOVIES,
                                "SocialIndia_${System.currentTimeMillis()}.mp4"
                            )
                            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            downloadManager.enqueue(request)
                            Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 20.dp))
            }

            // 3. OWNER ACTIONS OR VIEWER ACTIONS
            if (isOwner) {
                item {
                    SectionHeader("Creator Controls")

                    // Edit Caption
                    ReelActionListItem(
                        icon = Icons.Default.Edit,
                        title = "Edit Caption",
                        subtitle = "Update caption, tags and description",
                        onClick = { showEditCaptionDialog = true }
                    )

                    // Turn off Comments
                    ReelActionListItem(
                        icon = Icons.Default.ChatBubbleOutline,
                        title = if (allowComments) "Turn Off Comments" else "Turn On Comments",
                        subtitle = if (allowComments) "Disable comments on this reel" else "Allow viewers to comment",
                        onClick = {
                            allowComments = !allowComments
                            scope.launch {
                                RepositoryManager.reelsActionsRepository.toggleComments(video.id, allowComments)
                                Toast.makeText(context, if (allowComments) "Comments turned on" else "Comments disabled", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        }
                    )

                    // Turn off Remix
                    ReelActionListItem(
                        icon = Icons.Default.Repeat,
                        title = if (allowRemix) "Turn Off Remixing" else "Turn On Remixing",
                        subtitle = if (allowRemix) "Prevent others from remixing this reel" else "Allow viewers to remix",
                        onClick = {
                            allowRemix = !allowRemix
                            scope.launch {
                                RepositoryManager.reelsActionsRepository.toggleRemix(video.id, allowRemix)
                                Toast.makeText(context, if (allowRemix) "Remixing allowed" else "Remixing disabled", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        }
                    )

                    // Insights
                    ReelActionListItem(
                        icon = Icons.Default.Analytics,
                        title = "View Insights",
                        subtitle = "Analytics on views, reach, likes & engagement",
                        onClick = { showInsightsDialog = true }
                    )

                    // Archive
                    ReelActionListItem(
                        icon = Icons.Default.Archive,
                        title = "Archive Reel",
                        subtitle = "Hide from feed and profile without deleting",
                        onClick = {
                            scope.launch {
                                RepositoryManager.reelsActionsRepository.archiveReel(video.id, true)
                                Toast.makeText(context, "Reel archived", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        }
                    )

                    // Delete
                    ReelActionListItem(
                        icon = Icons.Default.Delete,
                        title = "Delete Reel",
                        subtitle = "Permanently remove this reel",
                        tint = Color(0xFFFE2C55),
                        onClick = { showDeleteConfirmDialog = true }
                    )
                }
            } else {
                item {
                    SectionHeader("Content & Account")

                    // Why am I seeing this?
                    ReelActionListItem(
                        icon = Icons.Default.AutoAwesome,
                        title = "Why am I seeing this Reel?",
                        subtitle = "AI recommendation signals & interest transparency",
                        onClick = {
                            scope.launch {
                                val reason = com.example.ai.SocialIndiaAIManager.recommendationService.explainRecommendation(
                                    video.id,
                                    currentUserId
                                )
                                whySeeingThisReason = reason
                                showWhyAmISeeingThisDialog = true
                            }
                        }
                    )

                    // About This Account
                    ReelActionListItem(
                        icon = Icons.Default.Info,
                        title = "About This Account",
                        subtitle = "Joined date, verification badge & transparency",
                        onClick = { showAboutAccountDialog = true }
                    )

                    // Not Interested
                    ReelActionListItem(
                        icon = Icons.Default.VisibilityOff,
                        title = "Not Interested",
                        subtitle = "Show fewer reels like this in your feed",
                        onClick = {
                            onNotInterested()
                            onDismiss()
                        }
                    )

                    // Hide Account
                    ReelActionListItem(
                        icon = Icons.Default.PersonOff,
                        title = "Hide @${video.user.username}",
                        subtitle = "Don't show reels from this creator",
                        onClick = {
                            onHideAccount()
                            onDismiss()
                        }
                    )

                    // Block Account
                    ReelActionListItem(
                        icon = Icons.Default.Block,
                        title = "Block @${video.user.username}",
                        subtitle = "They won't be able to message or find your profile",
                        tint = Color(0xFFFE2C55),
                        onClick = { showBlockConfirmDialog = true }
                    )

                    // Report
                    ReelActionListItem(
                        icon = Icons.Default.Report,
                        title = "Report",
                        subtitle = "Flag inappropriate or policy-violating content",
                        tint = Color(0xFFFE2C55),
                        onClick = { showReportDialog = true }
                    )

                    // Manage Content Preferences
                    ReelActionListItem(
                        icon = Icons.Default.Tune,
                        title = "Manage Content Preferences",
                        subtitle = "Review hidden creators and interested topics",
                        onClick = { showContentPrefDialog = true }
                    )
                }
            }
        }
    }

    // ==========================================
    // SUB-SHEETS AND DIALOGS
    // ==========================================

    // Why Am I Seeing This Reel Dialog
    if (showWhyAmISeeingThisDialog) {
        val r = whySeeingThisReason
        AlertDialog(
            onDismissRequest = { showWhyAmISeeingThisDialog = false },
            containerColor = Color(0xFF222226),
            icon = {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    "Why am I seeing this Reel?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = r?.primaryReasonHindi ?: "यह Reel आपकी रुचियों और एक्टिविटी के आधार पर सुझाई गई है।",
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = r?.primaryReasonEnglish ?: "Recommended based on your activity and interests on Social India.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    if (r?.signalsDetected?.isNotEmpty() == true) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Detected signals:",
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        r.signalsDetected.forEach { sig ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = sig,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Social India AI recommendations prioritize user privacy. You can adjust your preferences anytime in Settings.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showWhyAmISeeingThisDialog = false }) {
                    Text("Got it", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // About Account Dialog
    if (showAboutAccountDialog) {
        AboutAccountBottomSheet(
            userId = video.user.id,
            fallbackUsername = video.user.username,
            fallbackAvatar = video.user.avatarUrl,
            onDismiss = { showAboutAccountDialog = false }
        )
    }

    // Report Dialog
    if (showReportDialog) {
        ReportReelBottomSheet(
            videoId = video.id,
            creatorId = video.userId,
            currentUserId = currentUserId,
            onDismiss = { showReportDialog = false }
        )
    }

    // Insights Dialog
    if (showInsightsDialog) {
        ReelInsightsBottomSheet(
            video = video,
            onDismiss = { showInsightsDialog = false }
        )
    }

    // Edit Caption Dialog
    if (showEditCaptionDialog) {
        var captionText by remember { mutableStateOf(video.description) }
        var isUpdating by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditCaptionDialog = false },
            containerColor = Color(0xFF222226),
            title = {
                Text("Edit Caption", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = captionText,
                    onValueChange = { captionText = it },
                    placeholder = { Text("Write a caption...", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFE2C55),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    maxLines = 4
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isUpdating = true
                        scope.launch {
                            RepositoryManager.reelsActionsRepository.updateCaption(video.id, captionText)
                            Toast.makeText(context, "Caption updated", Toast.LENGTH_SHORT).show()
                            isUpdating = false
                            showEditCaptionDialog = false
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                    enabled = !isUpdating
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCaptionDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    // Delete Confirmation
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = Color(0xFF222226),
            title = {
                Text("Delete Reel?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to delete this reel? This action is permanent and cannot be undone.",
                    color = Color.White.copy(alpha = 0.75f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            RepositoryManager.reelsActionsRepository.deleteReel(video.id)
                            onDeleteVideo()
                            Toast.makeText(context, "Reel deleted", Toast.LENGTH_SHORT).show()
                        }
                        showDeleteConfirmDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    // Block Confirmation
    if (showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            containerColor = Color(0xFF222226),
            title = {
                Text("Block @${video.user.username}?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "They won't be able to message you, see your profile or view your reels. They will not be notified.",
                    color = Color.White.copy(alpha = 0.75f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            RepositoryManager.reelsActionsRepository.blockCreator(
                                currentUserId,
                                video.userId,
                                video.user.username
                            )
                            onHideAccount()
                            Toast.makeText(context, "Blocked @${video.user.username}", Toast.LENGTH_SHORT).show()
                        }
                        showBlockConfirmDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
                ) {
                    Text("Block")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    // Collections Dialog
    if (showCollectionsDialog) {
        CollectionsPickerBottomSheet(
            userId = currentUserId,
            video = video,
            onDismiss = { showCollectionsDialog = false }
        )
    }

    // Remix Sheet
    if (showRemixSheet) {
        RemixOptionsBottomSheet(
            video = video,
            onDismiss = { showRemixSheet = false },
            onStartRemix = { mode ->
                Toast.makeText(context, "Starting Remix ($mode)...", Toast.LENGTH_SHORT).show()
                onRemixClick()
                showRemixSheet = false
                onDismiss()
            }
        )
    }

    // Content Preferences Dialog
    if (showContentPrefDialog) {
        ContentPreferencesBottomSheet(
            userId = currentUserId,
            onDismiss = { showContentPrefDialog = false }
        )
    }
}

// ==========================================
// REEL OPTIONS SUB-COMPONENTS
// ==========================================

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
fun ReelQuickActionButton(
    icon: ImageVector,
    label: String,
    iconTint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ReelSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFFE2C55),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
fun ReelActionListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = tint,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.25f),
            modifier = Modifier.size(14.dp)
        )
    }
}

// ==========================================
// ABOUT THIS ACCOUNT SHEET
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAccountBottomSheet(
    userId: String,
    fallbackUsername: String,
    fallbackAvatar: String,
    onDismiss: () -> Unit
) {
    var transparencyInfo by remember { mutableStateOf<AccountTransparencyInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        transparencyInfo = RepositoryManager.reelsActionsRepository.getAccountTransparency(userId)
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "About this account",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "To help keep our community authentic, we show information about accounts on Social India.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
            )

            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFFFE2C55), modifier = Modifier.padding(24.dp))
            } else {
                val info = transparencyInfo
                // Profile Avatar & Name
                AsyncImage(
                    model = info?.profilePhotoUrl?.ifEmpty { fallbackAvatar } ?: fallbackAvatar,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = info?.displayName ?: fallbackUsername,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (info?.isVerified == true) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "@${info?.username ?: fallbackUsername}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Detail Cards
                AccountDetailRow(
                    icon = Icons.Default.CalendarToday,
                    title = "Date Joined",
                    value = info?.joinedDateFormatted ?: "Recently joined"
                )

                AccountDetailRow(
                    icon = Icons.Default.Public,
                    title = "Account Based In",
                    value = info?.country ?: "India"
                )

                AccountDetailRow(
                    icon = Icons.Default.Verified,
                    title = "Verification Status",
                    value = if (info?.isVerified == true) "${info.verificationType ?: "Personal"} Verified" else "Not Verified"
                )

                if (info?.isBusinessAccount == true) {
                    AccountDetailRow(
                        icon = Icons.Default.Storefront,
                        title = "Business Account",
                        value = info.businessCategory ?: "Official Business"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close", color = Color.White)
            }
        }
    }
}

@Composable
fun AccountDetailRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = title, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            Text(text = value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ==========================================
// REPORT BOTTOM SHEET
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportReelBottomSheet(
    videoId: String,
    creatorId: String,
    currentUserId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var additionalDetails by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val reportReasons = listOf(
        "Spam or misleading",
        "Nudity or sexual activity",
        "Hate speech or symbols",
        "Violence or dangerous organizations",
        "Bullying or harassment",
        "Intellectual property violation",
        "Scam or fraud",
        "False information"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Report Reel",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Why are you reporting this reel? Your report is anonymous.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            reportReasons.forEach { reason ->
                val isSelected = selectedReason == reason
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0xFFFE2C55).copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { selectedReason = reason }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = reason,
                        color = if (isSelected) Color(0xFFFE2C55) else Color.White,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color(0xFFFE2C55),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (selectedReason != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = additionalDetails,
                    onValueChange = { additionalDetails = it },
                    placeholder = { Text("Additional details (optional)...", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFE2C55),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val reasonToSubmit = selectedReason ?: return@Button
                        isSubmitting = true
                        scope.launch {
                            try {
                                RepositoryManager.reelsActionsRepository.reportReel(
                                    reporterId = currentUserId,
                                    videoId = videoId,
                                    creatorId = creatorId,
                                    reason = reasonToSubmit,
                                    details = additionalDetails
                                )
                                Toast.makeText(context, "Thank you. Your report has been submitted.", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to submit report. Please try again.", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSubmitting = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Text("Submit Report")
                    }
                }
            }
        }
    }
}

// ==========================================
// REEL INSIGHTS BOTTOM SHEET
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelInsightsBottomSheet(
    video: Video,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "Reel Insights",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Primary overview card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    InsightStatColumn(
                        icon = Icons.Default.PlayArrow,
                        count = NumberFormatUtils.formatCount(video.viewsCount),
                        label = "Plays"
                    )
                    InsightStatColumn(
                        icon = Icons.Default.Visibility,
                        count = NumberFormatUtils.formatCount((video.viewsCount * 1.3).toInt()),
                        label = "Reach"
                    )
                    InsightStatColumn(
                        icon = Icons.Default.Timer,
                        count = "0:14",
                        label = "Avg Watch"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Engagement Grid
            Text(
                text = "Interactions",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InsightGridItem(Icons.Default.Favorite, NumberFormatUtils.formatCount(video.likesCount), "Likes", Modifier.weight(1f))
                Spacer(modifier = Modifier.width(10.dp))
                InsightGridItem(Icons.Default.ChatBubbleOutline, NumberFormatUtils.formatCount(video.commentsCount), "Comments", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InsightGridItem(Icons.Default.BookmarkBorder, NumberFormatUtils.formatCount(video.savesCount), "Saves", Modifier.weight(1f))
                Spacer(modifier = Modifier.width(10.dp))
                InsightGridItem(Icons.Default.Share, NumberFormatUtils.formatCount(video.sharesCount), "Shares", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done", color = Color.White)
            }
        }
    }
}

@Composable
fun InsightStatColumn(icon: ImageVector, count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = Color(0xFFFE2C55), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = count, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
    }
}

@Composable
fun InsightGridItem(icon: ImageVector, count: String, label: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = count, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            }
        }
    }
}

// ==========================================
// COLLECTIONS PICKER BOTTOM SHEET
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsPickerBottomSheet(
    userId: String,
    video: Video,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val collections by com.example.repository.FlagshipFeaturesRepository.listenCollections(userId).collectAsState(initial = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Save to Collection",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New Collection", tint = Color(0xFFFE2C55))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (collections.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "No custom collections yet", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { showCreateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
                        ) {
                            Text("Create Collection")
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(collections) { coll ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    scope.launch {
                                        val post = com.example.model.FirebasePost(
                                            id = video.id,
                                            userId = video.userId,
                                            postUrl = video.videoUrl,
                                            caption = video.description,
                                            likesCount = video.likesCount,
                                            commentsCount = video.commentsCount
                                        )
                                        com.example.repository.FlagshipFeaturesRepository.savePost(userId, post, coll.id)
                                        Toast.makeText(context, "Saved to ${coll.name}", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(text = coll.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(text = "${coll.itemCount} items", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Color(0xFF222226),
            title = { Text("New Collection", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    placeholder = { Text("Collection name", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFE2C55),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCollectionName.isNotBlank()) {
                            scope.launch {
                                com.example.repository.FlagshipFeaturesRepository.createCollection(userId, newCollectionName.trim(), video.videoUrl)
                                Toast.makeText(context, "Collection created", Toast.LENGTH_SHORT).show()
                                showCreateDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}

// ==========================================
// REMIX OPTIONS BOTTOM SHEET
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemixOptionsBottomSheet(
    video: Video,
    onDismiss: () -> Unit,
    onStartRemix: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Remix this Reel",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Choose how you want to record alongside @${video.user.username}'s reel",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            RemixOptionCard(
                icon = Icons.Default.ViewAgenda,
                title = "Side by Side",
                description = "Record next to the original video",
                onClick = { onStartRemix("SIDE_BY_SIDE") }
            )

            RemixOptionCard(
                icon = Icons.Default.PictureInPicture,
                title = "Picture in Picture",
                description = "Place your video in a corner overlay",
                onClick = { onStartRemix("PIP") }
            )

            RemixOptionCard(
                icon = Icons.Default.Mic,
                title = "Use Audio",
                description = "Create a reel with ${video.musicTrack ?: "Original Sound"}",
                onClick = { onStartRemix("AUDIO") }
            )
        }
    }
}

@Composable
fun RemixOptionCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFE2C55).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color(0xFFFE2C55), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(text = description, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    }
}

// ==========================================
// CONTENT PREFERENCES BOTTOM SHEET
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentPreferencesBottomSheet(
    userId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hiddenCreatorsFlow = remember(userId) {
        RepositoryManager.reelsActionsRepository.getHiddenCreatorIds(userId)
    }
    val hiddenCreators by hiddenCreatorsFlow.collectAsState(initial = emptySet())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Content Preferences",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Manage your recommendation filters and hidden creators",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Text(
                text = "Hidden Creators (${hiddenCreators.size})",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (hiddenCreators.isEmpty()) {
                Text(
                    text = "You haven't hidden any creators.",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                    items(hiddenCreators.toList()) { creatorId ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Creator ID: ${creatorId.take(10)}...", color = Color.White, fontSize = 14.sp)
                            TextButton(onClick = {
                                scope.launch {
                                    RepositoryManager.reelsActionsRepository.undoHideCreator(userId, creatorId)
                                    Toast.makeText(context, "Unhid creator", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text("Unhide", color = Color(0xFF2196F3))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done", color = Color.White)
            }
        }
    }
}
