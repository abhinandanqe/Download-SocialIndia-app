package com.example.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.model.Comment
import com.example.model.GifItem
import com.example.model.RepositoryManager
import com.example.model.User
import com.example.model.Video
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Modern Instagram-Style Comment Bottom Sheet UI
 * Features:
 * - Real profile photo, username, verified badge, creator badge, compact relative timestamp
 * - Rich formatted text with clickable @mentions and #hashtags
 * - Pinned comment indicator and (edited) badge
 * - Real GIF rendering and GIF picker integration
 * - Like with animated heart and real-time like count
 * - Nested replies with collapse/expand thread view
 * - Instagram-style live @mention autocomplete suggestions while typing
 * - Mention notifications dispatched automatically
 * - Interactive Emoji Picker tray and quick-reaction bar
 * - Real-time typing indicators with animated bouncing dots
 * - Real-time comment count updates and Firestore synchronization
 * - Full options suite: Copy, Reply, Pin/Unpin, Edit, Delete, Share, Report
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernCommentBottomSheet(
    video: Video,
    onDismiss: () -> Unit,
    currentUserId: String,
    onProfileClick: (String) -> Unit,
    onHashtagClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val videoId = video.id

    var isLoadingInitialComments by remember(videoId) { mutableStateOf(true) }
    val commentsList by RepositoryManager.socialRepository.getComments(videoId).collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(commentsList, videoId) {
        if (commentsList.isNotEmpty()) {
            isLoadingInitialComments = false
        } else {
            delay(350)
            isLoadingInitialComments = false
        }
    }
    val typingUsers by RepositoryManager.socialRepository.getCommentTypingUsers(videoId, currentUserId).collectAsStateWithLifecycle(initialValue = emptyList())

    var commentText by remember { mutableStateOf("") }
    var replyingToComment by remember { mutableStateOf<Comment?>(null) }
    var selectedGifUrl by remember { mutableStateOf<String?>(null) }
    var selectedSticker by remember { mutableStateOf<com.example.model.StickerItem?>(null) }
    var showGifPicker by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    val commentVoiceRecorder = remember { com.example.utils.VoiceRecorderHelper(context) }
    val isRecordingAudioComment by commentVoiceRecorder.isRecording.collectAsStateWithLifecycle()
    val audioRecSeconds by commentVoiceRecorder.recordingDurationSeconds.collectAsStateWithLifecycle()
    val audioAmplitude by commentVoiceRecorder.amplitude.collectAsStateWithLifecycle()
    var isUploadingAudioComment by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { commentVoiceRecorder.release() }
    }
    val audioCommentPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            commentVoiceRecorder.startRecording()
        } else {
            Toast.makeText(context, "Microphone permission needed for Audio Comments", Toast.LENGTH_SHORT).show()
        }
    }

    // User profile state for current user
    var currentUsername by remember { mutableStateOf("User") }
    var currentUserAvatar by remember { mutableStateOf("") }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(currentUserId).get().await()
                currentUsername = doc.getString("username") ?: "User"
                currentUserAvatar = doc.getString("avatar_url") ?: doc.getString("profilePicUrl") ?: ""
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Sort order: "Top" or "Newest"
    var sortOrder by remember { mutableStateOf("Top") }

    var selectedMentions = remember { mutableStateListOf<com.example.model.Mention>() }

    // Live @mention query extraction
    val currentMentionQuery = remember(commentText) {
        val lastWord = commentText.substringAfterLast(" ", commentText)
        if (lastWord.startsWith("@") && !lastWord.contains("\n")) {
            lastWord.removePrefix("@")
        } else {
            null
        }
    }

    var mentionSuggestions by remember { mutableStateOf<List<User>>(emptyList()) }
    LaunchedEffect(currentMentionQuery) {
        if (currentMentionQuery != null) {
            RepositoryManager.socialRepository.searchUsersForMention(currentMentionQuery).collect { users ->
                mentionSuggestions = users
            }
        } else {
            mentionSuggestions = emptyList()
        }
    }

    // Typing debounce job
    var typingJob by remember { mutableStateOf<Job?>(null) }

    fun onTextChange(newText: String) {
        commentText = newText
        typingJob?.cancel()
        typingJob = scope.launch {
            if (newText.isNotBlank()) {
                RepositoryManager.socialRepository.setCommentTypingStatus(videoId, currentUserId, currentUsername, true)
                delay(3000)
                RepositoryManager.socialRepository.setCommentTypingStatus(videoId, currentUserId, currentUsername, false)
            } else {
                RepositoryManager.socialRepository.setCommentTypingStatus(videoId, currentUserId, currentUsername, false)
            }
        }
    }

    // Action dialogs
    var selectedCommentForOptions by remember { mutableStateOf<Comment?>(null) }
    var commentToEdit by remember { mutableStateOf<Comment?>(null) }
    var commentToDelete by remember { mutableStateOf<Comment?>(null) }
    var commentToReport by remember { mutableStateOf<Comment?>(null) }

    // Top-level comments and grouped nested replies
    val topLevelComments = remember(commentsList, sortOrder) {
        val filtered = commentsList.filter { it.parentCommentId.isNullOrEmpty() }
        if (sortOrder == "Top") {
            filtered.sortedWith(
                compareByDescending<Comment> { it.isPinned }
                    .thenByDescending { it.likedBy.size }
                    .thenByDescending { it.timestamp?.time ?: 0L }
            )
        } else {
            filtered.sortedWith(
                compareByDescending<Comment> { it.isPinned }
                    .thenByDescending { it.timestamp?.time ?: 0L }
            )
        }
    }

    val repliesMap = remember(commentsList) {
        commentsList.filter { !it.parentCommentId.isNullOrEmpty() }
            .groupBy { it.parentCommentId.orEmpty() }
    }

    val totalCommentCount = remember(commentsList) { commentsList.size }

    // Quick emoji bar list
    val quickEmojis = listOf("❤️", "🙌", "🔥", "👏", "😢", "😍", "😮", "😂", "💯", "✨")

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141416),
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // --- HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Comments",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "$totalCommentCount",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Sort Chip (Top / Newest)
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable {
                            sortOrder = if (sortOrder == "Top") "Newest" else "Top"
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = if (sortOrder == "Top") Icons.Default.TrendingUp else Icons.Default.Schedule,
                                contentDescription = "Sort",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = sortOrder,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

            // --- REAL-TIME TYPING INDICATOR BANNER ---
            AnimatedVisibility(
                visible = typingUsers.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E22))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BouncingDotsIndicator()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${typingUsers.first()} is typing...",
                        color = Color(0xFF3897F0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // --- COMMENTS LIST OR EMPTY STATE ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoadingInitialComments && topLevelComments.isEmpty()) {
                    val infiniteTransition = rememberInfiniteTransition(label = "commentShimmer")
                    val shimmerOffset by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1000f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "commentShimmerOffset"
                    )
                    val shimmerBrush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.White.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.06f)
                        ),
                        start = androidx.compose.ui.geometry.Offset(shimmerOffset - 300f, 0f),
                        end = androidx.compose.ui.geometry.Offset(shimmerOffset, 300f)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(6, key = { "comment_skel_$it" }, contentType = { "comment_skeleton" }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(shimmerBrush)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .width(110.dp)
                                            .height(12.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(shimmerBrush)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.82f)
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(shimmerBrush)
                                    )
                                }
                            }
                        }
                    }
                } else if (topLevelComments.isEmpty()) {
                    // Instagram-style clean empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No comments yet",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Start the conversation or tag friends with @",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val savedCommentScroll = remember(videoId) { GlobalState.getScroll("comments_$videoId") }
                    val listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = savedCommentScroll.first,
                        initialFirstVisibleItemScrollOffset = savedCommentScroll.second
                    )
                    DisposableEffect(listState, videoId) {
                        onDispose {
                            GlobalState.saveScroll(
                                "comments_$videoId",
                                listState.firstVisibleItemIndex,
                                listState.firstVisibleItemScrollOffset
                            )
                        }
                    }
                    var visibleCommentsCount by remember(videoId, sortOrder) { mutableIntStateOf(10) }
                    val pagedTopLevelComments = remember(topLevelComments, visibleCommentsCount) {
                        topLevelComments.take(visibleCommentsCount)
                    }
                    val shouldLoadMoreComments by remember {
                        derivedStateOf {
                            val total = listState.layoutInfo.totalItemsCount
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            total > 0 && lastVisible >= total - 3
                        }
                    }
                    LaunchedEffect(shouldLoadMoreComments, topLevelComments.size) {
                        if (shouldLoadMoreComments && visibleCommentsCount < topLevelComments.size) {
                            visibleCommentsCount = (visibleCommentsCount + 10).coerceAtMost(topLevelComments.size)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(
                            items = pagedTopLevelComments,
                            key = { index, comment -> "${comment.id}_$index" },
                            contentType = { _, _ -> "comment_row" }
                        ) { _, comment ->
                            ModernCommentItem(
                                comment = comment,
                                currentUserId = currentUserId,
                                videoCreatorId = video.userId,
                                videoId = videoId,
                                replies = repliesMap[comment.id] ?: emptyList(),
                                onReplyClick = { targetComment ->
                                    replyingToComment = targetComment
                                    focusRequester.requestFocus()
                                },
                                onProfileClick = onProfileClick,
                                onHashtagClick = { hashtag ->
                                    if (onHashtagClick != null) {
                                        onHashtagClick(hashtag)
                                    } else {
                                        Toast.makeText(context, "Exploring #$hashtag", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onOptionsClick = { targetComment ->
                                    selectedCommentForOptions = targetComment
                                }
                            )
                        }
                    }
                }
            }

            // --- LIVE @MENTION SUGGESTIONS OVERLAY ---
            AnimatedVisibility(
                visible = mentionSuggestions.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Surface(
                    color = Color(0xFF1E1E24),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "Mentions",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(mentionSuggestions, key = { it.id }) { user ->
                                Surface(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.clickable {
                                        val (newText, _) = com.example.utils.MentionHelper.replaceMention(commentText, commentText.length, user.username)
                                        commentText = newText
                                        if (!selectedMentions.any { it.uid == user.id }) {
                                            selectedMentions.add(com.example.model.Mention(uid = user.id, username = user.username))
                                        }
                                        mentionSuggestions = emptyList()
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        AsyncImage(
                                            model = user.avatarUrl.takeIf { it.isNotBlank() },
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.12f))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "@${user.username}",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (user.isVerified) {
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Verified",
                                                tint = Color(0xFF0095F6),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- REPLYING TO BANNER ---
            AnimatedVisibility(
                visible = replyingToComment != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E22))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Reply,
                            contentDescription = null,
                            tint = Color(0xFF0095F6),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Replying to @${replyingToComment?.username}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { replyingToComment = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel reply",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // --- SELECTED GIF PREVIEW BANNER ---
            AnimatedVisibility(
                visible = selectedGifUrl != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C1C20))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = selectedGifUrl,
                                contentDescription = "Attached GIF",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "GIF Attached",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(
                        onClick = { selectedGifUrl = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove GIF",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // --- QUICK EMOJI BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141416))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (emoji in quickEmojis) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .clickable {
                                commentText += emoji
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 18.sp)
                    }
                }
            }

            // --- INPUT BOX ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141416))
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Current User Avatar
                AsyncImage(
                    model = currentUserAvatar.takeIf { it.isNotBlank() },
                    contentDescription = "Current User Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Text field with trailing icon buttons (Emoji & GIF)
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { onTextChange(it) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            text = if (replyingToComment != null) "Reply to @${replyingToComment?.username}..." else "Add a comment...",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 14.sp
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3897F0).copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color(0xFF1E1E24),
                        unfocusedContainerColor = Color(0xFF1E1E24),
                        cursorColor = Color(0xFF3897F0),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (commentText.isNotBlank() || selectedGifUrl != null) {
                                val content = commentText.trim()
                                val gif = selectedGifUrl
                                val sticker = selectedSticker
                                val replyParent = replyingToComment
                                val mentionsToSend = selectedMentions.toList()
                                commentText = ""
                                selectedGifUrl = null
                                selectedSticker = null
                                replyingToComment = null
                                showEmojiPicker = false
                                selectedMentions.clear()
                                focusManager.clearFocus()

                                scope.launch {
                                    RepositoryManager.socialRepository.setCommentTypingStatus(videoId, currentUserId, currentUsername, false)
                                    RepositoryManager.socialRepository.addComment(
                                        videoId = videoId,
                                        userId = currentUserId,
                                        content = content,
                                        gifUrl = gif,
                                        parentCommentId = replyParent?.let { if (!it.parentCommentId.isNullOrEmpty()) it.parentCommentId else it.id },
                                        parentUsername = replyParent?.username,
                                        mentions = mentionsToSend,
                                        stickerId = sticker?.stickerId,
                                        packId = sticker?.packId,
                                        driveFileId = sticker?.driveFileId,
                                        thumbnailDriveFileId = sticker?.thumbnailDriveFileId,
                                        isAnimated = sticker?.isAnimated ?: false
                                    )
                                }
                            }
                        }
                    ),
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            // Emoji picker toggle
                            IconButton(
                                onClick = { showEmojiPicker = !showEmojiPicker },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (showEmojiPicker) Icons.Filled.SentimentSatisfied else Icons.Outlined.SentimentSatisfied,
                                    contentDescription = "Emoji Picker",
                                    tint = if (showEmojiPicker) Color(0xFF3897F0) else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // GIF picker button
                            IconButton(
                                onClick = { showGifPicker = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Surface(
                                    color = if (selectedGifUrl != null) Color(0xFF3897F0) else Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "GIF",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Voice Note / Audio Comment Mic Button (Cloud Upload + Live Waveform, 30s Max)
                            IconButton(
                                enabled = !isUploadingAudioComment,
                                onClick = {
                                    if (isRecordingAudioComment) {
                                        val dur = audioRecSeconds.coerceAtLeast(1)
                                        val file = commentVoiceRecorder.stopRecording()
                                        if (file != null) {
                                            val replyParent = replyingToComment
                                            val draftText = commentText.trim()
                                            replyingToComment = null
                                            isUploadingAudioComment = true
                                            scope.launch {
                                                try {
                                                    val cloudAudioUrl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                        com.example.repository.MediaRepository(context)
                                                            .uploadMedia(android.net.Uri.fromFile(file))
                                                    }
                                                    val finalAudioUrl = cloudAudioUrl?.takeIf { it.isNotBlank() } ?: file.absolutePath
                                                    RepositoryManager.socialRepository.addComment(
                                                        videoId = videoId,
                                                        userId = currentUserId,
                                                        content = draftText.ifBlank { "Voice Note (${dur}s)" },
                                                        parentCommentId = replyParent?.let { if (!it.parentCommentId.isNullOrEmpty()) it.parentCommentId else it.id },
                                                        parentUsername = replyParent?.username,
                                                        audioUrl = finalAudioUrl,
                                                        audioDurationSec = dur
                                                    )
                                                    commentText = ""
                                                    Toast.makeText(context, "Voice Comment posted!", Toast.LENGTH_SHORT).show()
                                                } catch (_: Exception) {
                                                    Toast.makeText(context, "Voice upload failed — try again", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isUploadingAudioComment = false
                                                }
                                            }
                                        }
                                    } else {
                                        val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.RECORD_AUDIO
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            commentVoiceRecorder.startRecording()
                                        } else {
                                            audioCommentPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                if (isUploadingAudioComment) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00E5FF), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = if (isRecordingAudioComment) Icons.Filled.Send else Icons.Outlined.Mic,
                                        contentDescription = if (isRecordingAudioComment) "Send Voice Comment" else "Record Voice Comment",
                                        tint = if (isRecordingAudioComment) Color(0xFF00E676) else Color(0xFF00E5FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                )

                if (isRecordingAudioComment) {
                    LaunchedEffect(audioRecSeconds) {
                        if (audioRecSeconds >= 30) {
                            val file = commentVoiceRecorder.stopRecording()
                            if (file != null) {
                                val replyParent = replyingToComment
                                val draftText = commentText.trim()
                                replyingToComment = null
                                isUploadingAudioComment = true
                                scope.launch {
                                    try {
                                        val cloudAudioUrl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            com.example.repository.MediaRepository(context)
                                                .uploadMedia(android.net.Uri.fromFile(file))
                                        }
                                        val finalAudioUrl = cloudAudioUrl?.takeIf { it.isNotBlank() } ?: file.absolutePath
                                        RepositoryManager.socialRepository.addComment(
                                            videoId = videoId,
                                            userId = currentUserId,
                                            content = draftText.ifBlank { "Voice Note (30s)" },
                                            parentCommentId = replyParent?.let { if (!it.parentCommentId.isNullOrEmpty()) it.parentCommentId else it.id },
                                            parentUsername = replyParent?.username,
                                            audioUrl = finalAudioUrl,
                                            audioDurationSec = 30
                                        )
                                        commentText = ""
                                        Toast.makeText(context, "30s Voice Comment posted!", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isUploadingAudioComment = false
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFFFF1744).copy(alpha = 0.22f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val ampLevel = (audioAmplitude.toFloat() / 16000f).coerceIn(0.25f, 1f)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(14.dp)
                            ) {
                                listOf(0.5f, 1.0f, 0.7f, 0.9f, 0.6f).forEach { factor ->
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height((14f * ampLevel * factor).coerceIn(3f, 14f).dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(Color(0xFFFF5252))
                                    )
                                }
                            }
                            Text(
                                text = "0:${String.format("%02d", audioRecSeconds)}/0:30",
                                color = Color(0xFFFF5252),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel voice note",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        commentVoiceRecorder.cancelRecording()
                                        Toast.makeText(context, "Voice note cancelled", Toast.LENGTH_SHORT).show()
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Post / Send Button
                val canSend = commentText.isNotBlank() || selectedGifUrl != null
                IconButton(
                    onClick = {
                        if (canSend) {
                            val content = commentText.trim()
                            val gif = selectedGifUrl
                            val sticker = selectedSticker
                            val replyParent = replyingToComment
                            val mentionsToSend = selectedMentions.toList()
                            commentText = ""
                            selectedGifUrl = null
                            selectedSticker = null
                            replyingToComment = null
                            showEmojiPicker = false
                            selectedMentions.clear()
                            focusManager.clearFocus()

                            scope.launch {
                                RepositoryManager.socialRepository.setCommentTypingStatus(videoId, currentUserId, currentUsername, false)
                                RepositoryManager.socialRepository.addComment(
                                    videoId = videoId,
                                    userId = currentUserId,
                                    content = content,
                                    gifUrl = gif,
                                    parentCommentId = replyParent?.let { if (!it.parentCommentId.isNullOrEmpty()) it.parentCommentId else it.id },
                                    parentUsername = replyParent?.username,
                                    mentions = mentionsToSend,
                                    stickerId = sticker?.stickerId,
                                    packId = sticker?.packId,
                                    driveFileId = sticker?.driveFileId,
                                    thumbnailDriveFileId = sticker?.thumbnailDriveFileId,
                                    isAnimated = sticker?.isAnimated ?: false
                                )
                            }
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (canSend) Color(0xFF0095F6) else Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (canSend) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // --- FULL EMOJI PICKER TRAY ---
            AnimatedVisibility(
                visible = showEmojiPicker,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                InstagramEmojiPicker(
                    onEmojiSelected = { emoji ->
                        commentText += emoji
                    },
                    onClose = { showEmojiPicker = false }
                )
            }
        }
    }

    // --- GIF & STICKER PICKER BOTTOM SHEET ---
    if (showGifPicker) {
        GifPickerBottomSheet(
            onDismissRequest = { showGifPicker = false },
            onGifSelected = { gifItem: GifItem ->
                selectedGifUrl = gifItem.fullUrl
                selectedSticker = null
                showGifPicker = false
            },
            onStickerSelected = { sticker: com.example.model.StickerItem ->
                selectedGifUrl = sticker.mediaUrl.ifEmpty { sticker.thumbnailUrl }
                selectedSticker = sticker
                showGifPicker = false
                RepositoryManager.stickerRepository.recordStickerUsage(currentUserId, sticker)
            },
            onEmojiSelected = { emoji ->
                commentText += emoji
                showGifPicker = false
            }
        )
    }

    // --- COMMENT OPTIONS DIALOG ---
    selectedCommentForOptions?.let { targetComment ->
        CommentOptionsBottomSheet(
            comment = targetComment,
            currentUserId = currentUserId,
            videoCreatorId = video.userId,
            onDismiss = { selectedCommentForOptions = null },
            onReply = {
                replyingToComment = targetComment
                selectedCommentForOptions = null
                focusRequester.requestFocus()
            },
            onCopy = {
                clipboardManager.setText(AnnotatedString(targetComment.text))
                Toast.makeText(context, "Comment copied to clipboard", Toast.LENGTH_SHORT).show()
                selectedCommentForOptions = null
            },
            onPinToggle = {
                scope.launch {
                    RepositoryManager.socialRepository.togglePinComment(videoId, targetComment.id, targetComment.isPinned)
                }
                selectedCommentForOptions = null
            },
            onEdit = {
                commentToEdit = targetComment
                selectedCommentForOptions = null
            },
            onDelete = {
                commentToDelete = targetComment
                selectedCommentForOptions = null
            },
            onShare = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "\"${targetComment.text}\" - @${targetComment.username}")
                }
                try {
                    context.startActivity(Intent.createChooser(shareIntent, "Share Comment"))
                } catch (_: Exception) {
                    Toast.makeText(context, "Unable to share comment", Toast.LENGTH_SHORT).show()
                }
                selectedCommentForOptions = null
            },
            onReport = {
                commentToReport = targetComment
                selectedCommentForOptions = null
            }
        )
    }

    // --- EDIT COMMENT DIALOG ---
    commentToEdit?.let { targetComment ->
        var editText by remember { mutableStateOf(targetComment.text) }
        AlertDialog(
            onDismissRequest = { commentToEdit = null },
            containerColor = Color(0xFF24242A),
            title = { Text("Edit Comment", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF0095F6),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        cursorColor = Color(0xFF0095F6)
                    ),
                    maxLines = 4
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newContent = editText.trim()
                        if (newContent.isNotBlank()) {
                            scope.launch {
                                RepositoryManager.socialRepository.editComment(videoId, targetComment.id, newContent)
                            }
                        }
                        commentToEdit = null
                    }
                ) {
                    Text("Save", color = Color(0xFF0095F6), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { commentToEdit = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }

    // --- DELETE COMMENT CONFIRMATION DIALOG ---
    commentToDelete?.let { targetComment ->
        AlertDialog(
            onDismissRequest = { commentToDelete = null },
            containerColor = Color(0xFF24242A),
            title = { Text("Delete Comment?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to delete this comment? All replies will also be removed.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            RepositoryManager.socialRepository.deleteComment(videoId, targetComment.id)
                        }
                        commentToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { commentToDelete = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }

    // --- REPORT COMMENT DIALOG ---
    commentToReport?.let { targetComment ->
        var selectedReason by remember { mutableStateOf("Spam") }
        val reasons = listOf("Spam", "Hate speech or symbols", "Bullying or harassment", "False information", "Scam or fraud")

        AlertDialog(
            onDismissRequest = { commentToReport = null },
            containerColor = Color(0xFF24242A),
            title = { Text("Report Comment", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Why are you reporting this comment?", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    for (reason in reasons) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedReason = reason }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF0095F6),
                                    unselectedColor = Color.White.copy(alpha = 0.5f)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(reason, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val reportId = java.util.UUID.randomUUID().toString()
                                FirebaseFirestore.getInstance().collection("reports").document(reportId).set(
                                    mapOf(
                                        "id" to reportId,
                                        "type" to "comment",
                                        "targetCommentId" to targetComment.id,
                                        "targetCommentText" to targetComment.text,
                                        "reportedUserId" to targetComment.userId,
                                        "reporterUserId" to currentUserId,
                                        "reason" to selectedReason,
                                        "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                    )
                                )
                            } catch (e: Exception) {}
                        }
                        Toast.makeText(context, "Thank you. Your report has been submitted.", Toast.LENGTH_SHORT).show()
                        commentToReport = null
                    }
                ) {
                    Text("Submit", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { commentToReport = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }
}

/**
 * Top-level comment row with support for nested replies, collapse/expand,
 * verified badge, creator badge, @mentions, #hashtags, and like animations.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernCommentItem(
    comment: Comment,
    currentUserId: String,
    videoCreatorId: String,
    videoId: String,
    replies: List<Comment>,
    onReplyClick: (Comment) -> Unit,
    onProfileClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit,
    onOptionsClick: (Comment) -> Unit
) {
    val scope = rememberCoroutineScope()
    val hasLiked = comment.likedBy.contains(currentUserId)
    var isRepliesExpanded by remember { mutableStateOf(false) }

    // Heart scale animation
    val heartScale = remember { Animatable(1f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onOptionsClick(comment) }
                ),
            verticalAlignment = Alignment.Top
        ) {
            // User Avatar
            AsyncImage(
                model = comment.avatarUrl.takeIf { it.isNotBlank() },
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable { onProfileClick(comment.userId) }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Username row + Badges + Timestamp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = comment.username,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onProfileClick(comment.userId) }
                    )

                    if (comment.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        VerificationBadge(
                            isVerified = true,
                            badgeUrl = comment.verificationBadgeUrl.ifBlank { null },
                            modifier = Modifier.size(14.dp),
                            showDialogOnClick = true
                        )
                    }

                    if (comment.userId == videoCreatorId) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFFE2C55).copy(alpha = 0.18f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Creator",
                                color = Color(0xFFFF5252),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (comment.isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFFFD700).copy(alpha = 0.18f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Pinned",
                                    color = Color(0xFFFFD700),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = formatInstagramTime(comment.timestamp),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp
                    )

                    if (comment.isEdited) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "(edited)",
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Comment Content + 1-Tap Regional Translator
                var translatedCommentText by remember(comment.id) { mutableStateOf<String?>(null) }
                var showTranslatedComment by remember(comment.id) { mutableStateOf(false) }
                var isTranslatingComment by remember(comment.id) { mutableStateOf(false) }

                if (comment.text.isNotEmpty()) {
                    val cachedTranslated = translatedCommentText
                    val displayedCommentText = if (showTranslatedComment && !cachedTranslated.isNullOrBlank()) {
                        cachedTranslated
                    } else {
                        comment.text
                    }
                    FormattedCommentText(
                        text = displayedCommentText,
                        onMentionClick = { mentionUsername ->
                            scope.launch {
                                try {
                                    val query = FirebaseFirestore.getInstance().collection("users")
                                        .whereEqualTo("username", mentionUsername)
                                        .limit(1)
                                        .get()
                                        .await()
                                    val doc = query.documents.firstOrNull()
                                    if (doc != null) {
                                        onProfileClick(doc.id)
                                    }
                                } catch (e: Exception) {}
                            }
                        },
                        onHashtagClick = onHashtagClick
                    )
                }

                // GIF Rendering
                if (!comment.gifUrl.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    CommentGifPlayer(gifUrl = comment.gifUrl)
                }

                // Voice Note / Audio Comment Bubble with Live Progress Waveform & Speed Control
                if (!comment.audioUrl.isNullOrEmpty()) {
                    val audioPlayerHelper = remember { com.example.utils.VoicePlayerHelper() }
                    val isPlayingAudio by audioPlayerHelper.isPlaying.collectAsStateWithLifecycle()
                    val currentPosMs by audioPlayerHelper.currentPositionMs.collectAsStateWithLifecycle()
                    val totalDurMs by audioPlayerHelper.totalDurationMs.collectAsStateWithLifecycle()
                    val playSpeed by audioPlayerHelper.playbackSpeed.collectAsStateWithLifecycle()
                    val wavePhase = if (isPlayingAudio) {
                        val waveAnim = rememberInfiniteTransition(label = "commentVoiceWave")
                        val animVal by waveAnim.animateFloat(
                            initialValue = 0.45f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(tween(360, easing = LinearEasing), RepeatMode.Reverse),
                            label = "phase"
                        )
                        animVal
                    } else 0.7f
                    DisposableEffect(comment.audioUrl) {
                        onDispose { audioPlayerHelper.release() }
                    }
                    val totalSecs = (totalDurMs / 1000).takeIf { it > 0 } ?: comment.audioDurationSec.coerceIn(1, 60)
                    val elapsedSecs = (currentPosMs / 1000).coerceIn(0, totalSecs)
                    val progressFraction = if (totalDurMs > 0) (currentPosMs.toFloat() / totalDurMs.toFloat()).coerceIn(0f, 1f) else 0f

                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF1D2333),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isPlayingAudio) Color(0xFF00E5FF).copy(alpha = 0.7f) else Color(0xFF0095F6).copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.clickable {
                            audioPlayerHelper.playOrPause(comment.audioUrl)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlayingAudio) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                contentDescription = "Play Voice Comment",
                                tint = if (isPlayingAudio) Color(0xFF00E5FF) else Color(0xFF0095F6),
                                modifier = Modifier.size(26.dp)
                            )
                            val bars = listOf(6, 12, 16, 9, 15, 18, 10, 14, 17, 8, 13, 16, 11, 15, 9, 14)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(20.dp)
                            ) {
                                bars.forEachIndexed { idx, baseH ->
                                    val barPos = idx.toFloat() / bars.size.toFloat()
                                    val isPlayedBar = isPlayingAudio && barPos <= progressFraction
                                    val mult = if (isPlayingAudio) (if (idx % 2 == 0) wavePhase else 1.4f - wavePhase) else 0.7f
                                    Box(
                                        modifier = Modifier
                                            .width(2.5.dp)
                                            .height((baseH * mult).coerceIn(4f, 20f).dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                when {
                                                    isPlayedBar -> Color(0xFF00E5FF)
                                                    isPlayingAudio -> Color(0xFF0095F6).copy(alpha = 0.75f)
                                                    else -> Color.White.copy(alpha = 0.55f)
                                                }
                                            )
                                    )
                                }
                            }
                            Text(
                                text = if (isPlayingAudio) "0:${String.format("%02d", elapsedSecs)}" else "0:${String.format("%02d", totalSecs)}",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier.clickable {
                                    audioPlayerHelper.cyclePlaybackSpeed()
                                }
                            ) {
                                Text(
                                    text = if (playSpeed == 1.0f) "1x" else if (playSpeed == 1.5f) "1.5x" else "2x",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Actions row: Reply, Likes count, Translate (अ/A), More options
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Reply",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onReplyClick(comment) }
                    )

                    if (comment.likedBy.isNotEmpty()) {
                        Text(
                            text = "${comment.likedBy.size} ${if (comment.likedBy.size == 1) "like" else "likes"}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (comment.text.isNotBlank()) {
                        Text(
                            text = when {
                                isTranslatingComment -> "Translating..."
                                showTranslatedComment -> "See original"
                                else -> "Translate (अ/A)"
                            },
                            color = Color(0xFF38BDF8),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                if (showTranslatedComment) {
                                    showTranslatedComment = false
                                } else if (translatedCommentText != null) {
                                    showTranslatedComment = true
                                } else if (!isTranslatingComment) {
                                    isTranslatingComment = true
                                    scope.launch {
                                        translatedCommentText = com.example.model.GeminiService.translateText(comment.text, "Hindi")
                                        isTranslatingComment = false
                                        showTranslatedComment = true
                                    }
                                }
                            }
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Options",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onOptionsClick(comment) }
                    )
                }
            }

            // Like Heart Button on the right
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                IconButton(
                    onClick = {
                        scope.launch {
                            heartScale.animateTo(1.35f, tween(100))
                            heartScale.animateTo(1.0f, tween(150))
                            RepositoryManager.socialRepository.toggleCommentLikeFirebase(videoId, comment.id, currentUserId)
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (hasLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (hasLiked) Color(0xFFFF2D55) else Color.White.copy(alpha = 0.45f),
                        modifier = Modifier
                            .size(17.dp)
                            .scale(heartScale.value)
                    )
                }
                if (comment.likedBy.isNotEmpty()) {
                    Text(
                        text = "${comment.likedBy.size}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // --- NESTED REPLIES (INSTAGRAM COLLAPSE/EXPAND) ---
        if (replies.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 48.dp)
                    .clickable { isRepliesExpanded = !isRepliesExpanded }
            ) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRepliesExpanded) "Hide replies" else "View ${replies.size} ${if (replies.size == 1) "reply" else "replies"}",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedVisibility(
                visible = isRepliesExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = 48.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (reply in replies) {
                        ModernReplyItem(
                            reply = reply,
                            currentUserId = currentUserId,
                            videoCreatorId = videoCreatorId,
                            videoId = videoId,
                            onReplyClick = {
                                onReplyClick(reply)
                            },
                            onProfileClick = onProfileClick,
                            onHashtagClick = onHashtagClick,
                            onOptionsClick = { onOptionsClick(reply) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern Reply Item inside a thread
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernReplyItem(
    reply: Comment,
    currentUserId: String,
    videoCreatorId: String,
    videoId: String,
    onReplyClick: () -> Unit,
    onProfileClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit,
    onOptionsClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val hasLiked = reply.likedBy.contains(currentUserId)
    val heartScale = remember { Animatable(1f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onOptionsClick
            ),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = reply.avatarUrl.takeIf { it.isNotBlank() },
            contentDescription = "Reply Avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .clickable { onProfileClick(reply.userId) }
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = reply.username,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onProfileClick(reply.userId) }
                )

                if (reply.isVerified) {
                    Spacer(modifier = Modifier.width(3.dp))
                    VerificationBadge(
                        isVerified = true,
                        badgeUrl = reply.verificationBadgeUrl.ifBlank { null },
                        modifier = Modifier.size(12.dp),
                        showDialogOnClick = true
                    )
                }

                if (reply.userId == videoCreatorId) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = Color(0xFFFE2C55).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Creator",
                            color = Color(0xFFFF5252),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = formatInstagramTime(reply.timestamp),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp
                )

                if (reply.isEdited) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(edited)",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 10.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (reply.text.isNotEmpty()) {
                FormattedCommentText(
                    text = reply.text,
                    fontSize = 13.sp,
                    onMentionClick = { mentionUsername ->
                        scope.launch {
                            try {
                                val query = FirebaseFirestore.getInstance().collection("users")
                                    .whereEqualTo("username", mentionUsername)
                                    .limit(1)
                                    .get()
                                    .await()
                                val doc = query.documents.firstOrNull()
                                if (doc != null) {
                                    onProfileClick(doc.id)
                                }
                            } catch (e: Exception) {}
                        }
                    },
                    onHashtagClick = onHashtagClick
                )
            }

            if (!reply.gifUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                CommentGifPlayer(gifUrl = reply.gifUrl, maxHeight = 130.dp)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Reply",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onReplyClick() }
                )

                if (reply.likedBy.isNotEmpty()) {
                    Text(
                        text = "${reply.likedBy.size} ${if (reply.likedBy.size == 1) "like" else "likes"}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Options",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onOptionsClick() }
                )
            }
        }

        // Reply Like Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 6.dp)
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        heartScale.animateTo(1.35f, tween(100))
                        heartScale.animateTo(1.0f, tween(150))
                        RepositoryManager.socialRepository.toggleCommentLikeFirebase(videoId, reply.id, currentUserId)
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (hasLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (hasLiked) Color(0xFFFF2D55) else Color.White.copy(alpha = 0.45f),
                    modifier = Modifier
                        .size(15.dp)
                        .scale(heartScale.value)
                )
            }
            if (reply.likedBy.isNotEmpty()) {
                Text(
                    text = "${reply.likedBy.size}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Text formatter that renders clickable @mentions and #hashtags
 */
@Composable
fun FormattedCommentText(
    text: String,
    onMentionClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFEEEEEE),
    fontSize: TextUnit = 14.sp
) {
    val annotatedString = remember(text) {
        buildAnnotatedString {
            val tokenRegex = Regex("(@[A-Za-z0-9_.]+|#[A-Za-z0-9_]+)")
            var lastIndex = 0
            val matches = tokenRegex.findAll(text)
            for (match in matches) {
                if (match.range.first > lastIndex) {
                    append(text.substring(lastIndex, match.range.first))
                }
                val token = match.value
                if (token.startsWith("@")) {
                    pushStringAnnotation(tag = "MENTION", annotation = token.removePrefix("@"))
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF0095F6),
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(token)
                    }
                    pop()
                } else if (token.startsWith("#")) {
                    pushStringAnnotation(tag = "HASHTAG", annotation = token.removePrefix("#"))
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF0095F6),
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(token)
                    }
                    pop()
                }
                lastIndex = match.range.last + 1
            }
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }

    ClickableText(
        text = annotatedString,
        style = androidx.compose.ui.text.TextStyle(
            color = color,
            fontSize = fontSize,
            lineHeight = 19.sp
        ),
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "MENTION", start = offset, end = offset).firstOrNull()?.let {
                onMentionClick(it.item)
                return@ClickableText
            }
            annotatedString.getStringAnnotations(tag = "HASHTAG", start = offset, end = offset).firstOrNull()?.let {
                onHashtagClick(it.item)
                return@ClickableText
            }
        }
    )
}

/**
 * GIF Player for comments with Coil GifDecoder
 */
@Composable
fun CommentGifPlayer(
    gifUrl: String,
    maxHeight: Dp = 180.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF222228))
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(gifUrl)
                .crossfade(true)
                .build(),
            imageLoader = imageLoader,
            contentDescription = "Comment GIF",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
        )
    }
}

/**
 * Comment Actions Options Bottom Sheet (Copy, Reply, Pin, Edit, Delete, Share, Report)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentOptionsBottomSheet(
    comment: Comment,
    currentUserId: String,
    videoCreatorId: String,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onPinToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit
) {
    val isCommentAuthor = comment.userId == currentUserId
    val isVideoCreator = currentUserId == videoCreatorId

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E24),
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Comment by @${comment.username}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Reply
            OptionRow(
                icon = Icons.Default.Reply,
                title = "Reply",
                onClick = onReply
            )

            // Copy
            OptionRow(
                icon = Icons.Default.ContentCopy,
                title = "Copy Text",
                onClick = onCopy
            )

            // Pin / Unpin (Only video creator)
            if (isVideoCreator) {
                OptionRow(
                    icon = Icons.Default.PushPin,
                    title = if (comment.isPinned) "Unpin from top" else "Pin to top",
                    tint = Color(0xFFFFD700),
                    onClick = onPinToggle
                )
            }

            // Share
            OptionRow(
                icon = Icons.Default.Share,
                title = "Share Comment",
                onClick = onShare
            )

            // Edit (Only comment author)
            if (isCommentAuthor) {
                OptionRow(
                    icon = Icons.Default.Edit,
                    title = "Edit Comment",
                    onClick = onEdit
                )
            }

            // Delete (Comment author or Video creator)
            if (isCommentAuthor || isVideoCreator) {
                OptionRow(
                    icon = Icons.Default.Delete,
                    title = "Delete Comment",
                    tint = Color(0xFFFF3B30),
                    onClick = onDelete
                )
            }

            // Report (If not own comment)
            if (!isCommentAuthor) {
                OptionRow(
                    icon = Icons.Default.Flag,
                    title = "Report Comment",
                    tint = Color(0xFFFF3B30),
                    onClick = onReport
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            color = tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Rich Instagram-style Categorized Emoji Picker
 */
@Composable
fun InstagramEmojiPicker(
    onEmojiSelected: (String) -> Unit,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Smileys", "Gestures", "Hearts", "Fire")

    val smileys = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
        "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
        "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
        "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
        "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
        "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗"
    )

    val gestures = listOf(
        "👍", "👎", "👊", "✊", "🤛", "🤜", "🤞", "✌️", "🤟", "🤘",
        "👌", "🤏", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐",
        "🖖", "👋", "🤙", "💪", "🖕", "✍️", "🙏", "🦶", "🦵", "👏",
        "🤝", "🙌", "👐", "🤲", "🫂", "👀", "👁️", "🧠", "❤️‍🔥", "💯"
    )

    val hearts = listOf(
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
        "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "💌",
        "♥️", "💋", "😻", "😽", "🥰", "😍", "😘", "💐", "🌹", "✨"
    )

    val celebration = listOf(
        "🔥", "✨", "🌟", "💫", "💥", "💯", "🎉", "🎊", "🎈", "🎂",
        "🎁", "🏆", "🥇", "🥈", "🥉", "🎯", "🚀", "👑", "💎", "⭐",
        "🎵", "🎶", "🎤", "🎧", "🎬", "🍿", "🍻", "🥂", "🍾", "🥂"
    )

    val currentList = when (selectedTab) {
        0 -> smileys
        1 -> gestures
        2 -> hearts
        else -> celebration
    }

    Surface(
        color = Color(0xFF18181C),
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Category Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF141416),
                contentColor = Color(0xFF0095F6),
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) Color(0xFF0095F6) else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    )
                }
            }

            // Grid of Emojis
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(currentList) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onEmojiSelected(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

/**
 * Instagram-style Bouncing 3-Dots Typing Indicator
 */
@Composable
fun BouncingDotsIndicator() {
    val infiniteTransition = rememberInfiniteTransition()

    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 130, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 260, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(y = dot1.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(Color(0xFF3897F0))
        )
        Box(
            modifier = Modifier
                .offset(y = dot2.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(Color(0xFF3897F0))
        )
        Box(
            modifier = Modifier
                .offset(y = dot3.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(Color(0xFF3897F0))
        )
    }
}

/**
 * Instagram-style relative time formatting:
 * < 1m -> "now"
 * < 1h -> "12m"
 * < 24h -> "3h"
 * < 7d -> "2d"
 * else -> "5w"
 */
fun formatInstagramTime(timestamp: Date?): String {
    if (timestamp == null) return "now"
    val diff = System.currentTimeMillis() - timestamp.time
    if (diff < 0) return "now"
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val weeks = days / 7

    return when {
        seconds < 60 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> "${weeks}w"
    }
}
