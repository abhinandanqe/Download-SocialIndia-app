package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.MentionDoc
import com.example.model.MentionTagLocation
import com.example.model.RepositoryManager
import com.example.model.User
import com.example.repository.MentionRepository
import com.example.utils.SoundManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Production-ready Mention Picker Overlay / Bottom Sheet.
 * Allows creators to search allowed users, view selected people, add/remove tags, and save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentionPickerOverlay(
    selectedUsers: List<User>,
    onAddUser: (User) -> Unit,
    onRemoveUser: (User) -> Unit,
    onClose: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<User>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        isSearching = true
        RepositoryManager.socialRepository.searchUsersForMention(searchQuery).collectLatest { users ->
            searchResults = users
            isSearching = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Title & Done Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AlternateEmail,
                        contentDescription = null,
                        tint = Color(0xFFEE1D52),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mention People",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by username or name...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFEE1D52),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Selected Users Horizontal Chips
            if (selectedUsers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(selectedUsers, key = { it.id }) { user ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFFEE1D52).copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEE1D52).copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = user.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${user.id}" },
                                    contentDescription = user.username,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "@${user.username}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEE1D52)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                IconButton(
                                    onClick = { onRemoveUser(user) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color(0xFFEE1D52),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Results List
            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFEE1D52), modifier = Modifier.size(28.dp))
                }
            } else if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "Type to find friends to mention" else "No users found matching \"$searchQuery\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults, key = { it.id }) { user ->
                        val isSelected = selectedUsers.any { it.id == user.id }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    SoundManager.playClickSound()
                                    if (isSelected) onRemoveUser(user) else onAddUser(user)
                                },
                            color = if (isSelected) Color(0xFFEE1D52).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = user.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${user.id}" },
                                    contentDescription = user.username,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "@${user.username}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (user.isVerified) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.Verified,
                                                contentDescription = "Verified",
                                                tint = Color(0xFF1DA1F2),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    if (user.bio.isNotBlank()) {
                                        Text(
                                            text = user.bio,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        SoundManager.playClickSound()
                                        if (isSelected) onRemoveUser(user) else onAddUser(user)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFFEE1D52)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Production-ready Mention Details Bottom Sheet showing all tagged users,
 * original creator, content status, and instant mention management ("Mentions Utha Sake").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentionDetailsBottomSheet(
    contentId: String,
    contentType: String,
    onDismiss: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var mentions by remember { mutableStateOf<List<MentionDoc>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(contentId) {
        isLoading = true
        mentions = MentionRepository.getMentionsForContent(contentId)
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AlternateEmail,
                        contentDescription = null,
                        tint = Color(0xFFEE1D52),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Mentioned People",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFEE1D52), modifier = Modifier.size(32.dp))
                }
            } else if (mentions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PeopleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No active mentions on this ${contentType.lowercase()}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(mentions, key = { it.mentionId.ifEmpty { it.mentionedUserId } }) { mention ->
                        val isMe = mention.mentionedUserId == currentUserId
                        val isAuthor = mention.mentionedByUserId == currentUserId
                        val dateFormatted = remember(mention.createdAt) {
                            SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(mention.createdAt))
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        SoundManager.playClickSound()
                                        onNavigateToProfile(mention.mentionedUserId)
                                        onDismiss()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mentioned User Avatar
                                AsyncImage(
                                    model = mention.mentionedByAvatar.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${mention.mentionedUserId}" },
                                    contentDescription = mention.mentionedUsername,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, Color(0xFFEE1D52).copy(alpha = 0.4f), CircleShape),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "@${mention.mentionedUsername}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isMe) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFFEE1D52).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "YOU",
                                                    color = Color(0xFFEE1D52),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Mentioned by @${mention.mentionedByUsername.ifEmpty { "author" }} • $dateFormatted",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Mention Removal Button ("Mentions Utha Sake" Flow)
                                if (isMe || isAuthor) {
                                    IconButton(
                                        onClick = {
                                            SoundManager.playClickSound()
                                            scope.launch {
                                                val success = MentionRepository.removeMention(mention.mentionId, currentUserId)
                                                if (success) {
                                                    mentions = mentions.filter { it.mentionId != mention.mentionId }
                                                    Toast.makeText(context, "Mention removed successfully! ✅", Toast.LENGTH_SHORT).show()
                                                    if (mentions.isEmpty()) {
                                                        onDismiss()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Failed to remove mention", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove Mention",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(22.dp)
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

/**
 * Interactive Photo/Video visual mention placement canvas.
 * Allows creator to tap anywhere on visual media to place normalized tag coordinates (xRatio, yRatio).
 */
@Composable
fun VisualMediaMentionOverlay(
    tags: List<MentionTagLocation>,
    isEditing: Boolean,
    onAddTagAtCoordinate: (Float, Float) -> Unit,
    onRemoveTag: (MentionTagLocation) -> Unit,
    onTagClicked: (MentionTagLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isEditing) {
                if (isEditing) {
                    detectTapGestures { offset ->
                        val xRatio = (offset.x / size.width).coerceIn(0.05f, 0.95f)
                        val yRatio = (offset.y / size.height).coerceIn(0.05f, 0.95f)
                        onAddTagAtCoordinate(xRatio, yRatio)
                    }
                }
            }
    ) {
        tags.forEach { tag ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = (tag.xRatio * 300).dp,
                            y = (tag.yRatio * 300).dp
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            SoundManager.playClickSound()
                            if (isEditing) onRemoveTag(tag) else onTagClicked(tag)
                        },
                    color = Color.Black.copy(alpha = 0.75f),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFFEE1D52),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "@${tag.username}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isEditing) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove tag",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Instagram-style Story Mention Preview & Repost Dialog.
 * Allows the mentioned user to preview the exact story (video/photo/text) and immediately
 * repost/add it to their own story in 1 tap ("Accept Mention & Add to Story").
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun StoryMentionPreviewDialog(
    mediaUrl: String,
    textOverlay: String = "",
    authorUsername: String,
    authorAvatar: String,
    authorUid: String = "",
    storyId: String = "",
    onDismiss: () -> Unit,
    onAddToStory: (mediaUrl: String, authorUsername: String, authorUid: String, textOverlay: String) -> Unit
) {
    val context = LocalContext.current
    val isImage = com.example.utils.MediaResolver.isImage(mediaUrl) || (!mediaUrl.contains(".mp4") && !mediaUrl.contains("video/"))
    var exoPlayer by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }

    DisposableEffect(mediaUrl) {
        if (!isImage && mediaUrl.isNotBlank()) {
            val player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                val resolved = com.example.utils.MediaResolver.resolveVideoUrl(mediaUrl).ifEmpty { mediaUrl }
                val mediaItem = androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(resolved))
                setMediaItem(mediaItem)
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                playWhenReady = true
                prepare()
            }
            exoPlayer = player
        }
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF181818))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Author Info + Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = authorAvatar.ifEmpty { "https://ui-avatars.com/api/?name=${authorUsername}&background=FE2C55&color=fff" },
                        contentDescription = "Author Avatar",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "@$authorUsername",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "mentioned you in their story",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                // Media Preview Frame (9:16 vertical story preview)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (isImage) {
                        val resolvedImage = com.example.utils.MediaResolver.resolveImageUrl(mediaUrl).ifEmpty { mediaUrl }
                        AsyncImage(
                            model = resolvedImage,
                            contentDescription = "Story Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        if (exoPlayer != null) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    androidx.media3.ui.PlayerView(ctx).apply {
                                        player = exoPlayer
                                        useController = false
                                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFFFE2C55))
                            }
                        }
                    }

                    // Text Overlay on Story if provided
                    if (textOverlay.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 20.dp)
                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = textOverlay,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    // Tag Badge on Media
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = Color(0xFFFE2C55), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Story Mention", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Actions: "Add to Your Story" CTA
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            SoundManager.playClickSound()
                            onAddToStory(mediaUrl, authorUsername, authorUid, textOverlay)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCAF45))
                                    ),
                                    RoundedCornerShape(24.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add This to Your Story", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(onClick = onDismiss) {
                        Text("Not Now", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
