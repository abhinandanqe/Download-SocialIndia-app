package com.example.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlagshipNotificationCenter(
    notifications: List<Notification>,
    onNotificationClick: (Notification) -> Unit,
    onDeleteNotification: ((Notification) -> Unit)? = null,
    onMarkRead: ((Notification) -> Unit)? = null
) {
    var selectedTab by remember { mutableStateOf("All") }
    val tabs = listOf("All", "Likes", "Comments", "Follows", "Mentions", "Collabs")

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOf(selectedTab),
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
            indicator = { tabPositions ->
                val tabIdx = tabs.indexOf(selectedTab).coerceIn(0, tabPositions.size - 1)
                if (tabIdx in tabPositions.indices) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[tabIdx]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 2.5.dp
                    )
                }
            },
            divider = {}
        ) {
            tabs.forEach { title ->
                Tab(
                    selected = selectedTab == title,
                    onClick = { selectedTab = title },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTab == title) MaterialTheme.colorScheme.onBackground else Color.Gray,
                            fontWeight = if (selectedTab == title) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        }

        val filteredNotifications = remember(notifications, selectedTab) {
            notifications.filter {
                when (selectedTab) {
                    "All" -> true
                    "Likes" -> it.type == NotificationType.LIKE
                    "Comments" -> it.type == NotificationType.COMMENT || it.type == NotificationType.REPLY
                    "Follows" -> it.type == NotificationType.FOLLOW
                    "Mentions" -> it.type == NotificationType.MENTION || it.type == NotificationType.STORY_MENTION
                    "Collabs" -> it.type == NotificationType.COLLABORATION_REQUEST || it.type == NotificationType.COLLABORATION_ACCEPTED || it.type == NotificationType.COLLABORATION_DECLINED || it.type == NotificationType.COLLABORATION_REMOVED
                    else -> true
                }
            }
        }

        var isInitialNotifLoading by remember { mutableStateOf(notifications.isEmpty()) }
        LaunchedEffect(notifications) {
            if (notifications.isNotEmpty()) {
                isInitialNotifLoading = false
            } else {
                kotlinx.coroutines.delay(350)
                isInitialNotifLoading = false
            }
        }

        if (isInitialNotifLoading && filteredNotifications.isEmpty()) {
            val infiniteTransition = rememberInfiniteTransition(label = "notifShimmer")
            val shimmerOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "notifShimmerOffset"
            )
            val shimmerBrush = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                start = androidx.compose.ui.geometry.Offset(shimmerOffset - 300f, 0f),
                end = androidx.compose.ui.geometry.Offset(shimmerOffset, 300f)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(8, key = { "notif_skel_$it" }, contentType = { "notif_skeleton" }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(shimmerBrush)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(13.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmerBrush)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmerBrush)
                            )
                        }
                    }
                }
            }
        } else if (filteredNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No notifications yet",
                        color = Color.Gray,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            val savedNotifScroll = remember(selectedTab) { GlobalState.getScroll("notifications_$selectedTab") }
            val notifListState = rememberLazyListState(
                initialFirstVisibleItemIndex = savedNotifScroll.first,
                initialFirstVisibleItemScrollOffset = savedNotifScroll.second
            )
            DisposableEffect(notifListState, selectedTab) {
                onDispose {
                    GlobalState.saveScroll(
                        "notifications_$selectedTab",
                        notifListState.firstVisibleItemIndex,
                        notifListState.firstVisibleItemScrollOffset
                    )
                }
            }

            var visibleNotifCount by remember(selectedTab) { mutableIntStateOf(10) }
            val pagedNotifications = remember(filteredNotifications, visibleNotifCount) {
                filteredNotifications.take(visibleNotifCount)
            }
            val shouldLoadMoreNotifs by remember {
                derivedStateOf {
                    val total = notifListState.layoutInfo.totalItemsCount
                    val lastVisible = notifListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    total > 0 && lastVisible >= total - 3
                }
            }
            LaunchedEffect(shouldLoadMoreNotifs, filteredNotifications.size) {
                if (shouldLoadMoreNotifs && visibleNotifCount < filteredNotifications.size) {
                    visibleNotifCount = (visibleNotifCount + 10).coerceAtMost(filteredNotifications.size)
                }
            }

            val todayMillis = remember {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            val yesterdayMillis = todayMillis - 86_400_000L

            val todayItems = remember(pagedNotifications, todayMillis) {
                pagedNotifications.filter { it.timestampMillis >= todayMillis }
            }
            val yesterdayItems = remember(pagedNotifications, todayMillis, yesterdayMillis) {
                pagedNotifications.filter { it.timestampMillis in yesterdayMillis until todayMillis }
            }
            val earlierItems = remember(pagedNotifications, yesterdayMillis) {
                pagedNotifications.filter { it.timestampMillis < yesterdayMillis }
            }

            LazyColumn(
                state = notifListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                if (todayItems.isNotEmpty()) {
                    item(key = "section_today", contentType = "notif_header") {
                        NotificationSectionHeader("Today")
                    }
                    itemsIndexed(
                        items = todayItems,
                        key = { index, n -> "today_${n.id}_$index" },
                        contentType = { _, _ -> "notif_row" }
                    ) { _, notification ->
                        FlagshipNotificationItem(
                            notification = notification,
                            onClick = { onNotificationClick(notification) },
                            onDelete = { onDeleteNotification?.invoke(notification) },
                            onMarkRead = { onMarkRead?.invoke(notification) }
                        )
                    }
                }

                if (yesterdayItems.isNotEmpty()) {
                    item(key = "section_yesterday", contentType = "notif_header") {
                        NotificationSectionHeader("Yesterday")
                    }
                    itemsIndexed(
                        items = yesterdayItems,
                        key = { index, n -> "yesterday_${n.id}_$index" },
                        contentType = { _, _ -> "notif_row" }
                    ) { _, notification ->
                        FlagshipNotificationItem(
                            notification = notification,
                            onClick = { onNotificationClick(notification) },
                            onDelete = { onDeleteNotification?.invoke(notification) },
                            onMarkRead = { onMarkRead?.invoke(notification) }
                        )
                    }
                }

                if (earlierItems.isNotEmpty()) {
                    item(key = "section_earlier", contentType = "notif_header") {
                        NotificationSectionHeader("Earlier")
                    }
                    itemsIndexed(
                        items = earlierItems,
                        key = { index, n -> "earlier_${n.id}_$index" },
                        contentType = { _, _ -> "notif_row" }
                    ) { _, notification ->
                        FlagshipNotificationItem(
                            notification = notification,
                            onClick = { onNotificationClick(notification) },
                            onDelete = { onDeleteNotification?.invoke(notification) },
                            onMarkRead = { onMarkRead?.invoke(notification) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationSectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlagshipNotificationItem(
    notification: Notification,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onMarkRead: (() -> Unit)? = null
) {
    var isDismissed by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                isDismissed = true
                onDelete?.invoke()
                true
            } else if (it == SwipeToDismissBoxValue.StartToEnd) {
                onMarkRead?.invoke()
                false
            } else {
                false
            }
        }
    )

    AnimatedVisibility(
        visible = !isDismissed,
        exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val color = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFE53935)
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                    else -> Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color)
                        .padding(horizontal = 20.dp),
                    contentAlignment = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) Icons.Default.MarkEmailRead else Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .background(if (!notification.isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile Avatar with Icon Badge
                Box(modifier = Modifier.size(52.dp)) {
                    AsyncImage(
                        model = notification.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${notification.userId}" },
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .align(Alignment.TopStart),
                        contentScale = ContentScale.Crop
                    )
                    // Type Icon Badge
                    val iconData = getNotificationIcon(notification.type)
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(iconData.color)
                            .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconData.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = notification.username,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = notification.time.ifEmpty { "Just now" },
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        if (!notification.isRead) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = getNotificationText(notification),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                when (notification.type) {
                    NotificationType.FOLLOW -> {
                        val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
                        val isFollowingFlow = remember(currentUserId, notification.userId) { com.example.model.RepositoryManager.socialRepository.isFollowing(currentUserId, notification.userId) }
                        val isFollowing by isFollowingFlow.collectAsStateWithLifecycle(initialValue = false)
                        val scope = rememberCoroutineScope()
                        Button(
                            onClick = { 
                                scope.launch {
                                    if (isFollowing) {
                                        com.example.model.RepositoryManager.socialRepository.unfollowUser(currentUserId, notification.userId)
                                    } else {
                                        com.example.model.RepositoryManager.socialRepository.followUser(currentUserId, notification.userId)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowing) Color.LightGray.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary,
                                contentColor = if (isFollowing) MaterialTheme.colorScheme.onBackground else Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = if (isFollowing) "Following" else "Follow Back",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    NotificationType.COLLABORATION_REQUEST -> {
                        var responded by remember { mutableStateOf(false) }
                        var accepted by remember { mutableStateOf(false) }
                        val scope = rememberCoroutineScope()

                        if (!responded) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { 
                                        responded = true
                                        accepted = true
                                        if (notification.postId != null) {
                                            scope.launch {
                                                com.example.model.RepositoryManager.postRepository.acceptCollaboration(notification.postId)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) { Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                
                                Button(
                                    onClick = { 
                                        responded = true 
                                        accepted = false
                                        if (notification.postId != null) {
                                            scope.launch {
                                                com.example.model.RepositoryManager.postRepository.declineCollaboration(notification.postId)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray.copy(alpha = 0.3f), contentColor = MaterialTheme.colorScheme.onSurface),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) { Text("Decline", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            }
                        } else {
                            Text(
                                text = if (accepted) "Accepted" else "Declined",
                                fontSize = 12.sp,
                                color = if (accepted) Color(0xFF17BF63) else Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    else -> {
                        if (notification.type == NotificationType.STORY_MENTION || (notification.type == NotificationType.MENTION && (!notification.storyId.isNullOrEmpty() || notification.contentType == "story"))) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { onClick() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFE2C55),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Add to Story", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                if (!notification.postThumbnail.isNullOrEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onClick() }
                                    ) {
                                        AsyncImage(
                                            model = notification.postThumbnail,
                                            contentDescription = "Thumbnail",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp).align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        } else if (!notification.postThumbnail.isNullOrEmpty()) {
                            AsyncImage(
                                model = notification.postThumbnail,
                                contentDescription = "Thumbnail",
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}

data class NotificationIconData(val icon: ImageVector, val color: Color)

fun getNotificationIcon(type: NotificationType): NotificationIconData {
    return when (type) {
        NotificationType.LIKE -> NotificationIconData(Icons.Default.Favorite, Color(0xFFE0245E))
        NotificationType.COMMENT -> NotificationIconData(Icons.Default.ChatBubble, Color(0xFF1DA1F2))
        NotificationType.FOLLOW -> NotificationIconData(Icons.Default.Person, Color(0xFF17BF63))
        NotificationType.MENTION -> NotificationIconData(Icons.Default.AlternateEmail, Color(0xFFF45D22))
        NotificationType.STORY_MENTION -> NotificationIconData(Icons.Default.AlternateEmail, Color(0xFFEE1D52))
        NotificationType.REPLY -> NotificationIconData(Icons.Default.Reply, Color(0xFF794BC4))
        NotificationType.SHARE -> NotificationIconData(Icons.Default.Share, Color(0xFF1DA1F2))
        NotificationType.STORY -> NotificationIconData(Icons.Default.History, Color(0xFFEE1D52))
        NotificationType.VERIFICATION -> NotificationIconData(Icons.Default.Verified, Color(0xFF1DA1F2))
        NotificationType.COLLABORATION_REQUEST -> NotificationIconData(Icons.Default.People, Color(0xFFF58025))
        NotificationType.COLLABORATION_ACCEPTED -> NotificationIconData(Icons.Default.HowToReg, Color(0xFF17BF63))
        NotificationType.COLLABORATION_DECLINED -> NotificationIconData(Icons.Default.PersonOff, Color(0xFFE53935))
        NotificationType.COLLABORATION_REMOVED -> NotificationIconData(Icons.Default.PersonRemove, Color(0xFF757575))
        NotificationType.NEW_POST -> NotificationIconData(Icons.Default.VideoLibrary, Color(0xFFEE1D52))
        NotificationType.CHANNEL_UPDATE -> NotificationIconData(Icons.Default.Campaign, Color(0xFF673AB7))
        NotificationType.MESSAGE -> NotificationIconData(Icons.Default.Mail, Color(0xFF0095F6))
        NotificationType.FOLLOW_REQUEST -> NotificationIconData(Icons.Default.PersonAdd, Color(0xFF3F51B5))
        NotificationType.FOLLOW_ACCEPTED -> NotificationIconData(Icons.Default.CheckCircle, Color(0xFF4CAF50))
        NotificationType.LIVE -> NotificationIconData(Icons.Default.LiveTv, Color(0xFFFF1744))
        NotificationType.NOTE_REPLY -> NotificationIconData(Icons.Default.ChatBubbleOutline, Color(0xFF00ACC1))
    }
}

fun getNotificationText(notification: Notification): String {
    return if (notification.content.isNotBlank() && notification.type != NotificationType.COMMENT) {
        notification.content
    } else {
        when (notification.type) {
            NotificationType.LIKE -> "liked your post."
            NotificationType.COMMENT -> if (notification.content.isNotBlank()) notification.content else "commented on your post."
            NotificationType.FOLLOW -> "started following you."
            NotificationType.MENTION -> if (notification.content.isNotBlank()) notification.content else "mentioned you."
            NotificationType.STORY_MENTION -> "mentioned you in a story."
            NotificationType.REPLY -> if (notification.content.isNotBlank()) notification.content else "replied to your comment."
            NotificationType.SHARE -> "shared your post."
            NotificationType.STORY -> "viewed your story."
            NotificationType.VERIFICATION -> "Your account has been verified."
            NotificationType.COLLABORATION_REQUEST -> "invited you to collaborate on a post."
            NotificationType.COLLABORATION_ACCEPTED -> "accepted your collaboration request."
            NotificationType.COLLABORATION_DECLINED -> "declined your collaboration request."
            NotificationType.COLLABORATION_REMOVED -> "removed you as a collaborator."
            NotificationType.NEW_POST -> "uploaded a new video."
            NotificationType.CHANNEL_UPDATE -> if (notification.content.isNotBlank()) notification.content else "posted a channel update."
            NotificationType.MESSAGE -> "sent you a message."
            NotificationType.FOLLOW_REQUEST -> "requested to follow you."
            NotificationType.FOLLOW_ACCEPTED -> "accepted your follow request."
            NotificationType.LIVE -> "started a live broadcast."
            NotificationType.NOTE_REPLY -> "replied to your note."
        }
    }
}
