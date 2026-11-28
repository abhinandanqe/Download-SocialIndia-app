package com.example.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Comment
import com.example.model.RepositoryManager
import kotlinx.coroutines.launch
import com.example.model.User
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumCommentBottomSheet(
    videoId: String,
    currentUserId: String,
    onDismiss: () -> Unit,
    onProfileClick: (User) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
        modifier = Modifier.fillMaxHeight(0.95f) // Expandable full screen
    ) {
        val commentsList by RepositoryManager.socialRepository.getComments(videoId).collectAsStateWithLifecycle(initialValue = emptyList())
        var commentText by remember { mutableStateOf("") }
        var replyingToComment by remember { mutableStateOf<Comment?>(null) }
        var sortMode by remember { mutableStateOf("Newest") } // "Newest", "Top Liked", "Oldest"
        val scope = rememberCoroutineScope()
        
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${commentsList.size} Comments",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.clickable {
                            sortMode = when (sortMode) {
                                "Newest" -> "Top Liked"
                                "Top Liked" -> "Oldest"
                                else -> "Newest"
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Tune, "Sort Comments", tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(sortMode, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            val topLevelComments = remember(commentsList, sortMode) {
                val base = commentsList.filter { it.parentCommentId.isNullOrEmpty() }
                when (sortMode) {
                    "Top Liked" -> base.sortedWith(compareByDescending<Comment> { it.isPinned }.thenByDescending { it.likedBy.size }.thenByDescending { it.timestamp })
                    "Oldest" -> base.sortedWith(compareByDescending<Comment> { it.isPinned }.thenBy { it.timestamp })
                    else -> base.sortedWith(compareByDescending<Comment> { it.isPinned }.thenByDescending { it.timestamp })
                }
            }
            val repliesMap = remember(commentsList) {
                commentsList.filter { !it.parentCommentId.isNullOrEmpty() }.groupBy { it.parentCommentId }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(topLevelComments, key = { index, comment -> "${comment.id}_$index" }) { _, comment ->
                    PremiumCommentItemRow(
                        comment = comment,
                        currentUserId = currentUserId,
                        videoId = videoId,
                        onReplyClick = { replyingToComment = comment },
                        onProfileClick = onProfileClick,
                        replies = repliesMap[comment.id] ?: emptyList()
                    )
                }
            }
        }
        
        // Bottom Input Area
        PremiumCommentInputBox(
            commentText = commentText,
            onCommentChange = { commentText = it },
            replyingTo = replyingToComment,
            onCancelReply = { replyingToComment = null },
            onSend = {
                if (commentText.isNotBlank()) {
                    scope.launch {
                        RepositoryManager.socialRepository.addComment(
                            videoId = videoId,
                            userId = currentUserId,
                            content = commentText.trim(),
                            gifUrl = null,
                            parentCommentId = replyingToComment?.id,
                            parentUsername = replyingToComment?.username
                        )
                    }
                    commentText = ""
                    replyingToComment = null
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumCommentItemRow(
    comment: Comment,
    currentUserId: String,
    videoId: String,
    onReplyClick: () -> Unit,
    onProfileClick: (User) -> Unit,
    replies: List<Comment>
) {
    val scope = rememberCoroutineScope()
    val hasLiked = comment.likedBy.contains(currentUserId)
    var showReplies by remember { mutableStateOf(false) }

    // Swipe to action state
    var offsetX by remember { mutableFloatStateOf(0f) }
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = { offsetX = 0f }
        ) { change, dragAmount ->
            change.consume()
            offsetX = (offsetX + dragAmount).coerceIn(-150f, 0f)
        }
    }) {
        // Swipe Action Background (Delete / Report)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier.width(75.dp).height(50.dp).background(Color(0xFFFF3B30), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = offsetX.dp)
                .background(Color(0xFF1C1C1E))
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showMenu = true }
                ),
            verticalAlignment = Alignment.Top
        ) {
            UserLiveAvatar(
                userId = comment.userId,
                avatarUrl = comment.avatarUrl,
                size = 40.dp,
                onClick = { onProfileClick(User(id = comment.userId, username = comment.username, avatarUrl = comment.avatarUrl)) }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "@${comment.username}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "2h",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = comment.text,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Reply",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onReplyClick() }
                    )
                }

                if (replies.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showReplies = !showReplies }
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.width(24.dp).padding(end = 8.dp),
                            color = Color.Gray
                        )
                        Text(
                            text = if (showReplies) "Hide replies" else "View ${replies.size} replies",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    AnimatedVisibility(visible = showReplies) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            replies.forEach { reply ->
                                PremiumReplyItem(reply, currentUserId, videoId, onProfileClick)
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
            
            // Like Column
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        scope.launch {
                            RepositoryManager.socialRepository.toggleCommentLikeFirebase(videoId, comment.id, currentUserId)
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (hasLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (hasLiked) Color(0xFFFF1744) else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (comment.likedBy.isNotEmpty()) {
                    Text(
                        text = comment.likedBy.size.toString(),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            containerColor = Color(0xFF2C2C2E),
            title = { Text("Comment Actions", color = Color.White) },
            text = {
                Column {
                    Text("Copy Text", color = Color.White, modifier = Modifier.fillMaxWidth().clickable { showMenu = false }.padding(12.dp))
                    Text("Pin Comment", color = Color.White, modifier = Modifier.fillMaxWidth().clickable { showMenu = false }.padding(12.dp))
                    if (comment.userId == currentUserId) {
                        Text("Delete", color = Color.Red, modifier = Modifier.fillMaxWidth().clickable { showMenu = false }.padding(12.dp))
                    } else {
                        Text("Report", color = Color.Red, modifier = Modifier.fillMaxWidth().clickable { showMenu = false }.padding(12.dp))
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumReplyItem(
    reply: Comment,
    currentUserId: String,
    videoId: String,
    onProfileClick: (User) -> Unit
) {
    val scope = rememberCoroutineScope()
    val hasLiked = reply.likedBy.contains(currentUserId)
    var showMenu by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { showMenu = true }), verticalAlignment = Alignment.Top) {
        UserLiveAvatar(
            userId = reply.userId,
            avatarUrl = reply.avatarUrl,
            size = 28.dp,
            onClick = {
                onProfileClick(User(id = reply.userId, username = reply.username, avatarUrl = reply.avatarUrl))
            }
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@${reply.username}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (reply.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerificationBadge(
                        isVerified = true,
                        badgeUrl = reply.verificationBadgeUrl.ifBlank { null },
                        modifier = Modifier.size(12.dp),
                        showDialogOnClick = true
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = reply.text,
                color = Color.White,
                fontSize = 14.sp
            )
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = {
                    scope.launch {
                        RepositoryManager.socialRepository.toggleCommentLikeFirebase(videoId, reply.id, currentUserId)
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (hasLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (hasLiked) Color(0xFFFF1744) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
            if (reply.likedBy.isNotEmpty()) {
                Text(
                    text = reply.likedBy.size.toString(),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun PremiumCommentInputBox(
    commentText: String,
    onCommentChange: (String) -> Unit,
    replyingTo: Comment?,
    onCancelReply: () -> Unit,
    onSend: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF121212))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        AnimatedVisibility(visible = replyingTo != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Replying to @${replyingTo?.username}",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp).clickable { onCancelReply() }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserLiveAvatar(userId = "CurrentUser", avatarUrl = "", size = 36.dp)
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedTextField(
                value = commentText,
                onValueChange = onCommentChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add a comment...", color = Color.Gray, fontSize = 14.sp) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.White.copy(alpha = 0.1f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                    cursorColor = Color.White
                ),
                maxLines = 4,
                trailingIcon = {
                    Row(modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Outlined.Gif, contentDescription = "GIF", tint = Color.Gray, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Outlined.Mic, contentDescription = "Voice", tint = Color.Gray, modifier = Modifier.size(24.dp))
                    }
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = onSend,
                enabled = commentText.isNotBlank(),
                modifier = Modifier.size(36.dp).clip(CircleShape).background(if (commentText.isNotBlank()) Color(0xFFFE2C55) else Color.Gray.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}
