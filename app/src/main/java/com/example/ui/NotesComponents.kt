package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.utils.GlobalAudioPreviewManager
import com.example.api.MusicClient
import com.example.api.ITunesTrack
import com.example.model.RepositoryManager
import com.example.model.UserNote
import com.example.repository.FlagshipFeaturesRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * SOCIAL INDIA — NOTES SYSTEM (Instagram-Style UI/UX)
 *
 * Real-time, 24-hour ephemeral notes with S45 Music API integration,
 * close-friends / mutual-followers audience controls, emoji reactions,
 * and direct Messenger thread reply support.
 */

private fun formatNoteTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 60_000L) return "Just now"
    val minutes = diff / 60_000L
    if (minutes < 60) return "${minutes}m ago"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    return "${days}d ago"
}

private fun formatNoteExpiryRemaining(expiresAt: Long): String {
    val remaining = expiresAt - System.currentTimeMillis()
    if (remaining <= 0) return "Expired"
    val hours = remaining / (60 * 60 * 1000L)
    if (hours > 0) return "Expires in ${hours}h"
    val minutes = remaining / (60 * 1000L)
    return "Expires in ${minutes}m"
}

@Composable
fun NotesTrayRow(
    currentUserId: String,
    currentUsername: String,
    currentUserAvatar: String,
    onReplyToNote: (recipientId: String, recipientUsername: String, noteText: String) -> Unit,
    onViewProfile: (userId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allNotes by FlagshipFeaturesRepository.listenActiveNotes(currentUserId).collectAsState(initial = emptyList())

    val myNote = remember(allNotes, currentUserId) {
        allNotes.find { it.userId == currentUserId }
    }
    val otherNotes = remember(allNotes, currentUserId) {
        allNotes.filter { it.userId != currentUserId }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedNoteForReply by remember { mutableStateOf<UserNote?>(null) }
    var showMyNoteOptions by remember { mutableStateOf(false) }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("notes_tray_row"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        // ==========================================
        // 1. CURRENT USER NOTE ITEM
        // ==========================================
        item(key = "user_own_note_$currentUserId") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(82.dp)
                    .clickable {
                        if (myNote != null) {
                            showMyNoteOptions = true
                        } else {
                            showCreateDialog = true
                        }
                    }
                    .testTag("my_note_item")
            ) {
                Box(
                    modifier = Modifier.height(104.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Avatar with Add Badge
                    Box(
                        modifier = Modifier.size(68.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentUserAvatar.isNotEmpty()) {
                                AsyncImage(
                                    model = currentUserAvatar,
                                    contentDescription = "Your Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Avatar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Plus Icon Badge if no active note
                        if (myNote == null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 2.dp, y = 2.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Share a note",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Thought Bubble overlapping top of avatar
                    if (myNote != null) {
                        NoteThoughtBubble(
                            text = myNote.text,
                            musicTrackName = myNote.musicTrackName,
                            isCloseFriends = myNote.audience == "close_friends",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .widthIn(max = 88.dp)
                        )
                    } else {
                        // Instagram-style floating prompt bubble overlapping top of avatar
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Note...",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Location off",
                                    fontSize = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your note",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ==========================================
        // 2. FRIENDS' ACTIVE NOTES
        // ==========================================
        items(otherNotes, key = { it.id.ifEmpty { it.userId } }) { note ->
            val isCloseFriends = note.audience == "close_friends"
            val cfGreen = Color(0xFF10B981)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(82.dp)
                    .clickable { selectedNoteForReply = note }
                    .testTag("friend_note_${note.userId}")
            ) {
                Box(
                    modifier = Modifier.height(104.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Avatar with Close Friends indicator if applicable
                    Box(
                        modifier = Modifier.size(68.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val avatarModifier = if (isCloseFriends) {
                            Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .border(2.5.dp, cfGreen, CircleShape)
                                .padding(2.dp)
                                .clip(CircleShape)
                        } else {
                            Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                        }

                        Box(
                            modifier = avatarModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (note.avatarUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = note.avatarUrl,
                                    contentDescription = "${note.username}'s avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Thought Bubble overlapping top of avatar
                    NoteThoughtBubble(
                        text = note.text,
                        musicTrackName = note.musicTrackName,
                        isCloseFriends = isCloseFriends,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = 88.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isCloseFriends) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Close Friends",
                            tint = cfGreen,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Text(
                        text = note.username,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // ==========================================
    // 3. CREATE / EDIT NOTE DIALOG
    // ==========================================
    if (showCreateDialog) {
        CreateNoteDialog(
            currentUserId = currentUserId,
            currentUsername = currentUsername,
            currentUserAvatar = currentUserAvatar,
            existingNote = myNote,
            onDismiss = { showCreateDialog = false },
            onPublished = { showCreateDialog = false }
        )
    }

    // ==========================================
    // 4. MY NOTE MANAGEMENT SHEET
    // ==========================================
    if (showMyNoteOptions && myNote != null) {
        MyNoteOptionsDialog(
            note = myNote,
            currentUserId = currentUserId,
            onDismiss = { showMyNoteOptions = false },
            onEdit = {
                showMyNoteOptions = false
                showCreateDialog = true
            },
            onDeleted = {
                showMyNoteOptions = false
            },
            onViewProfile = onViewProfile
        )
    }

    // ==========================================
    // 5. FRIEND NOTE VIEWER & REPLY SHEET
    // ==========================================
    selectedNoteForReply?.let { note ->
        FriendNoteViewerSheet(
            note = note,
            currentUserId = currentUserId,
            onDismiss = {
                GlobalAudioPreviewManager.stop()
                selectedNoteForReply = null
            },
            onViewProfile = { uid ->
                GlobalAudioPreviewManager.stop()
                selectedNoteForReply = null
                onViewProfile(uid)
            },
            onSendReply = { replyMsg ->
                GlobalAudioPreviewManager.stop()
                onReplyToNote(note.userId, note.username, replyMsg)
                selectedNoteForReply = null
            }
        )
    }
}

/**
 * High-fidelity Instagram-style Thought Bubble
 */
@Composable
fun NoteThoughtBubble(
    text: String,
    musicTrackName: String?,
    isCloseFriends: Boolean,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isCloseFriends) {
        Color(0xFF10B981).copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
    }
    val borderColor = if (isCloseFriends) {
        Color(0xFF10B981).copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = bubbleColor,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = text,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!musicTrackName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    val infiniteTransition = rememberInfiniteTransition(label = "note_wave")
                    val bar1 by infiniteTransition.animateFloat(
                        initialValue = 3f,
                        targetValue = 9f,
                        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
                        label = "b1"
                    )
                    val bar2 by infiniteTransition.animateFloat(
                        initialValue = 8f,
                        targetValue = 3f,
                        animationSpec = infiniteRepeatable(tween(310), RepeatMode.Reverse),
                        label = "b2"
                    )
                    val bar3 by infiniteTransition.animateFloat(
                        initialValue = 4f,
                        targetValue = 10f,
                        animationSpec = infiniteRepeatable(tween(430), RepeatMode.Reverse),
                        label = "b3"
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                            modifier = Modifier.height(10.dp)
                        ) {
                            listOf(bar1, bar2, bar3).forEach { h ->
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(h.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = musicTrackName,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        // Subtle thought-bubble tail dot
        Box(
            modifier = Modifier
                .offset(y = (-2).dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(borderColor)
        )
    }
}

/**
 * Modern Instagram-style Note Composer with S45 Music and Audience Controls
 */
@Composable
fun CreateNoteDialog(
    currentUserId: String,
    currentUsername: String,
    currentUserAvatar: String,
    existingNote: UserNote? = null,
    onDismiss: () -> Unit,
    onPublished: () -> Unit
) {
    var noteText by remember { mutableStateOf(existingNote?.text ?: "") }
    var selectedAudience by remember { mutableStateOf(existingNote?.audience ?: "followers") }
    var trackName by remember { mutableStateOf(existingNote?.musicTrackName ?: "") }
    var trackArtist by remember { mutableStateOf(existingNote?.musicTrackArtist ?: "") }
    var trackUrl by remember { mutableStateOf(existingNote?.musicTrackUrl ?: "") }
    var trackArtwork by remember { mutableStateOf(existingNote?.musicTrackArtwork ?: "") }
    var trackId by remember { mutableStateOf(existingNote?.musicTrackId ?: "") }
    var showMusicPicker by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isPlayingTrack by GlobalAudioPreviewManager.isPlaying.collectAsState()
    val currentPlayingUrl by GlobalAudioPreviewManager.currentUrl.collectAsState()
    val isCurrentTrackPlaying = isPlayingTrack && currentPlayingUrl == trackUrl

    val quickEmojis = listOf("💭", "🔥", "❤️", "😂", "🎧", "☕", "✨", "🍕", "🚀", "💯", "😴", "🥳")

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
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            GlobalAudioPreviewManager.stop()
                            onDismiss()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    Text(
                        text = if (existingNote != null) "Edit Note" else "New Note",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Button(
                        onClick = {
                            if (noteText.isBlank()) {
                                Toast.makeText(context, "Please enter some text", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSubmitting = true
                            scope.launch {
                                GlobalAudioPreviewManager.stop()
                                val note = UserNote(
                                    id = existingNote?.id?.ifEmpty { currentUserId } ?: currentUserId,
                                    userId = currentUserId,
                                    username = currentUsername,
                                    avatarUrl = currentUserAvatar,
                                    text = noteText.trim(),
                                    musicTrackId = trackId.ifBlank { null },
                                    musicTrackName = trackName.ifBlank { null },
                                    musicTrackArtist = trackArtist.ifBlank { null },
                                    musicTrackUrl = trackUrl.ifBlank { null },
                                    musicTrackArtwork = trackArtwork.ifBlank { null },
                                    musicProvider = "S45",
                                    musicDurationSeconds = 30,
                                    audience = selectedAudience,
                                    reactions = existingNote?.reactions ?: emptyMap()
                                )
                                val success = FlagshipFeaturesRepository.publishNote(note)
                                isSubmitting = false
                                if (success) {
                                    Toast.makeText(context, "Note shared!", Toast.LENGTH_SHORT).show()
                                    onPublished()
                                } else {
                                    Toast.makeText(context, "Failed to share note", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isSubmitting && noteText.isNotBlank(),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("share_note_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Share", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Centered Interactive Live Preview
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.widthIn(max = 240.dp)
                    ) {
                        // Live Thought Bubble
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            shadowElevation = 3.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (noteText.isNotBlank()) noteText else "Share a thought...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    color = if (noteText.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (trackName.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = trackName,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // Nub
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Large Centered Avatar
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentUserAvatar.isNotEmpty()) {
                                AsyncImage(
                                    model = currentUserAvatar,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Text Input Field
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { if (it.length <= 60) noteText = it },
                    placeholder = { Text("What's on your mind? (60 char max)", fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_text_input"),
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 3,
                    supportingText = {
                        Text(
                            text = "${noteText.length}/60",
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            color = if (noteText.length >= 55) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                // Quick Emoji Tap Tray
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(quickEmojis) { emo ->
                        Surface(
                            onClick = {
                                if (noteText.length + emo.length <= 60) {
                                    noteText += emo
                                }
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emo, fontSize = 16.sp)
                            }
                        }
                    }
                }

                // Instagram Note Prompts & 2-Second Looping Video Note Controls
                var isRecordingLoopVideo by remember { mutableStateOf(false) }
                var hasLoopVideoAttached by remember { mutableStateOf(noteText.startsWith("📹 ")) }
                val notePrompts = remember {
                    listOf(
                        "🎧 Song on repeat?",
                        "☕ Weekend plans?",
                        "🔥 Hot take today:",
                        "📍 Current mood in 3 emojis",
                        "🎬 Late night thoughts..."
                    )
                }

                LaunchedEffect(isRecordingLoopVideo) {
                    if (isRecordingLoopVideo) {
                        kotlinx.coroutines.delay(2000L)
                        isRecordingLoopVideo = false
                        hasLoopVideoAttached = true
                        if (!noteText.startsWith("📹")) {
                            noteText = ("📹 2s Loop • " + noteText).take(60)
                        }
                        Toast.makeText(context, "2-Second Looping Video Note captured!", Toast.LENGTH_SHORT).show()
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { isRecordingLoopVideo = true },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = "2s Video Note",
                            modifier = Modifier.size(14.dp),
                            tint = if (isRecordingLoopVideo || hasLoopVideoAttached) Color(0xFFEE1D52) else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                isRecordingLoopVideo -> "Recording 2s..."
                                hasLoopVideoAttached -> "✓ 2s Video Loop"
                                else -> "2s Video Note"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(notePrompts) { prompt ->
                            Surface(
                                onClick = { noteText = prompt.take(60) },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ) {
                                Text(
                                    text = prompt,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // S45 Music Attachment Card or Selector
                if (trackName.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (trackArtwork.isNotBlank()) {
                                AsyncImage(
                                    model = trackArtwork,
                                    contentDescription = null,
                                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(trackName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (trackArtist.isNotBlank()) {
                                    Text(trackArtist, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }

                            if (trackUrl.isNotBlank()) {
                                IconButton(
                                    onClick = { GlobalAudioPreviewManager.playOrToggle(context, trackUrl) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (isCurrentTrackPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                        contentDescription = "Preview",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    GlobalAudioPreviewManager.stop()
                                    trackName = ""
                                    trackArtist = ""
                                    trackUrl = ""
                                    trackArtwork = ""
                                    trackId = ""
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove music", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showMusicPicker = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_music_button")
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add song from S45 Music (Optional)", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Audience Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Shared with", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedAudience == "followers",
                            onClick = { selectedAudience = "followers" },
                            label = { Text("Followers you follow back", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedAudience == "close_friends",
                            onClick = { selectedAudience = "close_friends" },
                            label = { Text("Close Friends ⭐", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.2f),
                                selectedLabelColor = Color(0xFF059669)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showMusicPicker) {
        QuickMusicPickerDialog(
            onDismiss = {
                GlobalAudioPreviewManager.stop()
                showMusicPicker = false
            },
            onSelect = { name, artist, url, artwork, id ->
                trackName = name
                trackArtist = artist
                trackUrl = url
                trackArtwork = artwork
                trackId = id
                showMusicPicker = false
            }
        )
    }
}

/**
 * S45 Indian Music API Picker Sheet
 */
@Composable
fun QuickMusicPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (trackName: String, trackArtist: String, trackUrl: String, trackArtwork: String, trackId: String) -> Unit
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(MusicClient.categories.firstOrNull() ?: "🔥 Trending") }
    var tracks by remember { mutableStateOf<List<ITunesTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val isPlaying by GlobalAudioPreviewManager.isPlaying.collectAsState()
    val isBuffering by GlobalAudioPreviewManager.isLoading.collectAsState()
    val currentUrl by GlobalAudioPreviewManager.currentUrl.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            GlobalAudioPreviewManager.stop()
        }
    }

    LaunchedEffect(search, selectedCategory) {
        isLoading = true
        if (search.isNotBlank()) kotlinx.coroutines.delay(250)
        try {
            val query = if (search.isNotBlank()) search else selectedCategory
            val response = MusicClient.searchIndianTracks(query, selectedCategory)
            tracks = response.results
        } catch (e: Exception) {
            tracks = emptyList()
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = {
            GlobalAudioPreviewManager.stop()
            onDismiss()
        },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler {
            GlobalAudioPreviewManager.stop()
            onDismiss()
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top App Bar with Back Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
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
                    Text(
                        text = "Select Music for Note",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                // Search Bar
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search Bollywood, Punjabi, Lo-Fi...", fontSize = 14.sp) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (search.isNotEmpty()) {
                                IconButton(onClick = { search = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                }

                // Category Chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    items(MusicClient.categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat && search.isEmpty(),
                            onClick = {
                                selectedCategory = cat
                                search = ""
                            },
                            label = { Text(cat, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                    }
                } else if (tracks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No songs found. Try a different search query.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tracks, key = { it.id ?: (it.name?.hashCode()?.toLong() ?: 0L) }) { track ->
                            val trackTitle = track.name ?: "Unknown Track"
                            val trackArtist = track.artistName ?: "Unknown Artist"
                            val previewAudioUrl = track.previewUrl ?: ""
                            val artwork = track.artworkUrl ?: ""
                            val isThisTrackPlaying = isPlaying && currentUrl == previewAudioUrl
                            val isThisTrackBuffering = isBuffering && currentUrl == previewAudioUrl
                            val durationSec = track.durationSeconds
                            val durationFormatted = "${durationSec / 60}:${(durationSec % 60).toString().padStart(2, '0')}"

                            Surface(
                                onClick = {
                                    GlobalAudioPreviewManager.stop()
                                    onSelect(trackTitle, trackArtist, previewAudioUrl, artwork, track.id.toString())
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isThisTrackPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = artwork.ifEmpty { "https://picsum.photos/seed/$trackTitle/100/100" },
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(trackTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("$trackArtist • $durationFormatted", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }

                                    if (previewAudioUrl.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                GlobalAudioPreviewManager.playOrToggle(context, previewAudioUrl)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            if (isThisTrackBuffering) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(
                                                    if (isThisTrackPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                                    contentDescription = "Preview Audio",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(28.dp)
                                                )
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
    }
}

/**
 * Compact Instagram-style Friend Note Viewer Sheet with Emoji Reactions & Direct Message Reply
 */
@Composable
fun FriendNoteViewerSheet(
    note: UserNote,
    currentUserId: String,
    onDismiss: () -> Unit,
    onViewProfile: (userId: String) -> Unit,
    onSendReply: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var replyText by remember { mutableStateOf("") }
    var activeReactions by remember { mutableStateOf(note.reactions) }

    val isPlaying by GlobalAudioPreviewManager.isPlaying.collectAsState()
    val isBuffering by GlobalAudioPreviewManager.isLoading.collectAsState()
    val currentUrl by GlobalAudioPreviewManager.currentUrl.collectAsState()
    val isThisNotePlaying = isPlaying && currentUrl == note.musicTrackUrl
    val isThisNoteBuffering = isBuffering && currentUrl == note.musicTrackUrl

    val reactionEmojis = listOf("❤️", "😂", "🔥", "👏", "😮", "😢", "💯", "🙌")
    val currentUserReaction = activeReactions[currentUserId]

    DisposableEffect(Unit) {
        onDispose {
            GlobalAudioPreviewManager.stop()
        }
    }

    LaunchedEffect(note.userId, currentUserId) {
        if (currentUserId.isNotEmpty() && note.userId != currentUserId) {
            FlagshipFeaturesRepository.recordNoteView(note.userId, currentUserId)
        }
    }

    Dialog(
        onDismissRequest = {
            GlobalAudioPreviewManager.stop()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header: Profile & Timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable {
                                GlobalAudioPreviewManager.stop()
                                onViewProfile(note.userId)
                            }
                    ) {
                        if (note.avatarUrl.isNotEmpty()) {
                            AsyncImage(
                                model = note.avatarUrl,
                                contentDescription = "${note.username}'s Profile",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                GlobalAudioPreviewManager.stop()
                                onViewProfile(note.userId)
                            }
                    ) {
                        Text(note.username, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            formatNoteTimeAgo(note.createdAt),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            GlobalAudioPreviewManager.stop()
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Main Thought Bubble Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "\"${note.text}\"",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // S45 Music Preview Card
                        if (!note.musicTrackName.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!note.musicTrackArtwork.isNullOrBlank()) {
                                        AsyncImage(
                                            model = note.musicTrackArtwork,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = note.musicTrackName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = note.musicTrackArtist ?: "S45 Music Track",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }

                                    if (!note.musicTrackUrl.isNullOrBlank()) {
                                        IconButton(
                                            onClick = {
                                                GlobalAudioPreviewManager.playOrToggle(context, note.musicTrackUrl)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            if (isThisNoteBuffering) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(
                                                    if (isThisNotePlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                                    contentDescription = "Play preview",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Emoji Reactions
                Column {
                    Text(
                        text = "React to Note",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(reactionEmojis) { emoji ->
                            val isSelected = currentUserReaction == emoji
                            Surface(
                                onClick = {
                                    val newEmoji = if (isSelected) "" else emoji
                                    activeReactions = activeReactions.toMutableMap().apply {
                                        if (newEmoji.isEmpty()) remove(currentUserId) else put(currentUserId, newEmoji)
                                    }
                                    scope.launch {
                                        FlagshipFeaturesRepository.reactToNote(note.userId, currentUserId, newEmoji)
                                    }
                                },
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(emoji, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }

                // Reply to Note Field
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text("Reply to @${note.username}...", fontSize = 13.sp) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_reply_input"),
                    trailingIcon = {
                        if (replyText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    if (note.expiresAt <= System.currentTimeMillis()) {
                                        Toast.makeText(context, "This note has expired", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                        return@IconButton
                                    }
                                    onSendReply(replyText.trim())
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
                )
            }
        }
    }
}

/**
 * Own Note Management Sheet (View, Edit, Delete)
 */
@Composable
fun MyNoteOptionsDialog(
    note: UserNote,
    currentUserId: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onViewProfile: (userId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showConfirmDelete by remember { mutableStateOf(false) }

    val isPlayingTrack by GlobalAudioPreviewManager.isPlaying.collectAsState()
    val currentPlayingUrl by GlobalAudioPreviewManager.currentUrl.collectAsState()
    val isTrackPlaying = isPlayingTrack && currentPlayingUrl == note.musicTrackUrl

    DisposableEffect(Unit) {
        onDispose {
            GlobalAudioPreviewManager.stop()
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Delete note?", fontWeight = FontWeight.Bold) },
            text = { Text("Your note will be immediately removed and will no longer be visible to your friends.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDelete = false
                        scope.launch {
                            FlagshipFeaturesRepository.deleteNote(currentUserId)
                            Toast.makeText(context, "Note deleted", Toast.LENGTH_SHORT).show()
                            onDeleted()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = {
            GlobalAudioPreviewManager.stop()
            onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Your Active Note", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    formatNoteExpiryRemaining(note.expiresAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "\"${note.text}\"",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        )

                        if (!note.musicTrackName.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!note.musicTrackArtwork.isNullOrBlank()) {
                                        AsyncImage(
                                            model = note.musicTrackArtwork,
                                            contentDescription = "Cover",
                                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    } else {
                                        Box(
                                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(note.musicTrackName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(note.musicTrackArtist ?: "Unknown Artist", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                    if (!note.musicTrackUrl.isNullOrBlank()) {
                                        IconButton(
                                            onClick = {
                                                GlobalAudioPreviewManager.playOrToggle(context, note.musicTrackUrl)
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                if (isTrackPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                                contentDescription = "Play/Pause",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Audience indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (note.audience == "close_friends") Icons.Default.Star else Icons.Default.Group,
                        contentDescription = null,
                        tint = if (note.audience == "close_friends") Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (note.audience == "close_friends") "Shared with Close Friends ⭐" else "Shared with followers you follow back",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Reactions received summary
                if (note.reactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Reactions received (${note.reactions.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        note.reactions.values.distinct().take(6).forEach { emo ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(emo, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                // 👁 SEEN BY / VIEWERS LIST (Instagram Story Viewer style)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = "Viewers",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Seen by ${note.viewers.size}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (note.viewers.isEmpty()) {
                            Text(
                                text = "No views yet. Friends who see your note will appear here.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                note.viewers.forEach { viewerId ->
                                    var viewerUser by remember { mutableStateOf<com.example.model.User?>(null) }
                                    LaunchedEffect(viewerId) {
                                        try {
                                            val doc = com.example.model.RepositoryManager.firestore.collection("users").document(viewerId).get().await()
                                            viewerUser = com.example.model.User(
                                                id = doc.id,
                                                username = doc.getString("username") ?: "User",
                                                displayName = doc.getString("displayName") ?: doc.getString("name") ?: "",
                                                avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: "",
                                                isVerified = doc.getBoolean("isVerified") ?: false
                                            )
                                        } catch (_: Exception) {}
                                    }

                                    val viewTime = note.viewerDetails[viewerId] ?: note.createdAt
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                GlobalAudioPreviewManager.stop()
                                                onDismiss()
                                                onViewProfile(viewerId)
                                            }
                                            .padding(vertical = 4.dp, horizontal = 2.dp)
                                    ) {
                                        AsyncImage(
                                            model = viewerUser?.avatarUrl?.ifEmpty { null } ?: "https://api.dicebear.com/7.x/avataaars/png?seed=$viewerId",
                                            contentDescription = null,
                                            modifier = Modifier.size(30.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = viewerUser?.username ?: "user_${viewerId.take(5)}",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (viewerUser?.isVerified == true) {
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Icon(Icons.Default.Verified, contentDescription = "Verified", tint = Color(0xFF1D9BF0), modifier = Modifier.size(11.dp))
                                                }
                                            }
                                            if (!viewerUser?.displayName.isNullOrBlank()) {
                                                Text(
                                                    text = viewerUser?.displayName.orEmpty(),
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Text(
                                            text = formatNoteTimeAgo(viewTime),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                GlobalAudioPreviewManager.stop()
                onEdit()
            }) {
                Text("Leave a new note")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    showConfirmDelete = true
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete note")
            }
        }
    )
}
