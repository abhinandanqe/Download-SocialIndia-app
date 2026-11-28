package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.model.RepositoryManager
import com.example.notification.model.NotificationFilter
import com.example.notification.model.SocialIndiaNotification
import com.example.notification.viewmodel.SocialIndiaNotificationViewModel
import kotlinx.coroutines.launch

/**
 * Premium Instagram-standard Notification Center for Social India.
 * Features Sections (TODAY, THIS WEEK, EARLIER), Filter Chips, Smart Multi-Avatar Grouping,
 * Inline Actions (Follow Back, Collab, Live), Search, and Deep Linking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    viewModel: SocialIndiaNotificationViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val todayItems by viewModel.todayNotifications.collectAsStateWithLifecycle()
    val thisWeekItems by viewModel.thisWeekNotifications.collectAsStateWithLifecycle()
    val earlierItems by viewModel.earlierNotifications.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var isSearchExpanded by remember { mutableStateOf(false) }
    var showMarkAllConfirmDialog by remember { mutableStateOf(false) }

    val hasAnyItems = todayItems.isNotEmpty() || thisWeekItems.isNotEmpty() || earlierItems.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Notifications",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (unreadCount > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.height(20.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (unreadCount > 99) "99+" else "$unreadCount",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = "Activity & updates",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Search toggle
                    IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Mark All as Read button
                    if (unreadCount > 0) {
                        IconButton(onClick = { showMarkAllConfirmDialog = true }) {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = "Mark all as read",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Settings shortcut
                    IconButton(onClick = { onNavigate("notification_settings") }) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Notification Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Expandable Search Bar
            AnimatedVisibility(
                visible = isSearchExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("Search by name, message, or keyword...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )
            }

            // Category Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(NotificationFilter.values()) { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectedFilter.value = filter },
                        label = {
                            Text(
                                text = filter.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                thickness = 0.5.dp
            )

            // Content List or Empty State
            if (!hasAnyItems) {
                NotificationCenterEmptyState(
                    selectedFilter = selectedFilter,
                    onResetFilter = {
                        viewModel.selectedFilter.value = NotificationFilter.ALL
                        viewModel.searchQuery.value = ""
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. TODAY
                    if (todayItems.isNotEmpty()) {
                        item(key = "header_today") {
                            NotificationSectionTitle("TODAY")
                        }
                        itemsIndexed(
                            items = todayItems,
                            key = { index, n -> "nc_today_${n.id}_$index" }
                        ) { _, notification ->
                            NotificationItemRow(
                                notification = notification,
                                onClick = {
                                    viewModel.markAsRead(notification.id)
                                    handleNotificationNavigation(notification, onNavigate)
                                },
                                onFollowToggle = {
                                    viewModel.markAsRead(notification.id)
                                },
                                onCollabResponse = { accepted ->
                                    viewModel.markAsRead(notification.id)
                                    val postId = notification.targetId
                                    if (!postId.isNullOrEmpty()) {
                                        scope.launch {
                                            if (accepted) {
                                                RepositoryManager.postRepository.acceptCollaboration(postId)
                                            } else {
                                                RepositoryManager.postRepository.declineCollaboration(postId)
                                            }
                                        }
                                    }
                                },
                                onDelete = { viewModel.deleteNotification(notification.id) }
                            )
                        }
                    }

                    // 2. THIS WEEK
                    if (thisWeekItems.isNotEmpty()) {
                        item(key = "header_this_week") {
                            NotificationSectionTitle("THIS WEEK")
                        }
                        itemsIndexed(
                            items = thisWeekItems,
                            key = { index, n -> "nc_week_${n.id}_$index" }
                        ) { _, notification ->
                            NotificationItemRow(
                                notification = notification,
                                onClick = {
                                    viewModel.markAsRead(notification.id)
                                    handleNotificationNavigation(notification, onNavigate)
                                },
                                onFollowToggle = {
                                    viewModel.markAsRead(notification.id)
                                },
                                onCollabResponse = { accepted ->
                                    viewModel.markAsRead(notification.id)
                                    val postId = notification.targetId
                                    if (!postId.isNullOrEmpty()) {
                                        scope.launch {
                                            if (accepted) {
                                                RepositoryManager.postRepository.acceptCollaboration(postId)
                                            } else {
                                                RepositoryManager.postRepository.declineCollaboration(postId)
                                            }
                                        }
                                    }
                                },
                                onDelete = { viewModel.deleteNotification(notification.id) }
                            )
                        }
                    }

                    // 3. EARLIER
                    if (earlierItems.isNotEmpty()) {
                        item(key = "header_earlier") {
                            NotificationSectionTitle("EARLIER")
                        }
                        itemsIndexed(
                            items = earlierItems,
                            key = { index, n -> "nc_earlier_${n.id}_$index" }
                        ) { _, notification ->
                            NotificationItemRow(
                                notification = notification,
                                onClick = {
                                    viewModel.markAsRead(notification.id)
                                    handleNotificationNavigation(notification, onNavigate)
                                },
                                onFollowToggle = {
                                    viewModel.markAsRead(notification.id)
                                },
                                onCollabResponse = { accepted ->
                                    viewModel.markAsRead(notification.id)
                                    val postId = notification.targetId
                                    if (!postId.isNullOrEmpty()) {
                                        scope.launch {
                                            if (accepted) {
                                                RepositoryManager.postRepository.acceptCollaboration(postId)
                                            } else {
                                                RepositoryManager.postRepository.declineCollaboration(postId)
                                            }
                                        }
                                    }
                                },
                                onDelete = { viewModel.deleteNotification(notification.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog for Mark All as Read
    if (showMarkAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showMarkAllConfirmDialog = false },
            title = { Text("Mark All as Read", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to mark all $unreadCount unread notifications as read?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.markAllAsRead()
                        showMarkAllConfirmDialog = false
                    }
                ) {
                    Text("Mark Read")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarkAllConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun NotificationSectionTitle(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationItemRow(
    notification: SocialIndiaNotification,
    onClick: () -> Unit,
    onFollowToggle: () -> Unit,
    onCollabResponse: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }
    val scope = rememberCoroutineScope()

    var isCollabHandled by remember { mutableStateOf(false) }
    var collabAccepted by remember { mutableStateOf(false) }

    val isFollowType = notification.type in listOf("follow", "follow_request", "follow_accepted")
    val isFollowingFlow = remember(currentUserId, notification.actorUid) {
        if (isFollowType && notification.actorUid.isNotEmpty()) {
            RepositoryManager.socialRepository.isFollowing(currentUserId, notification.actorUid)
        } else {
            kotlinx.coroutines.flow.flowOf(false)
        }
    }
    val isFollowing by isFollowingFlow.collectAsStateWithLifecycle(initialValue = false)

    val rowBg = if (!notification.read) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Multi-avatar stacking for grouped notifications or single avatar
        NotificationAvatarComponent(notification = notification)

        Spacer(modifier = Modifier.width(12.dp))

        // Content Column
        Column(modifier = Modifier.weight(1f)) {
            // Main Notification Text with Username in Bold
            val actorName = notification.actorDisplayName.ifEmpty { notification.actorUsername }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = actorName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (notification.actorIsVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = Color(0xFF1DA1F2),
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatRelativeTimestamp(notification.createdAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                if (!notification.read) {
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
                text = notification.text,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Action or Thumbnail on the right
        when {
            // 1. Follow / Follow Back Button
            isFollowType && notification.actorUid.isNotEmpty() && notification.actorUid != currentUserId -> {
                Button(
                    onClick = {
                        onFollowToggle()
                        scope.launch {
                            if (isFollowing) {
                                RepositoryManager.socialRepository.unfollowUser(currentUserId, notification.actorUid)
                            } else {
                                RepositoryManager.socialRepository.followUser(currentUserId, notification.actorUid)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowing) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (isFollowing) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            Color.White
                        }
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (isFollowing) "Following" else "Follow Back",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 2. Collaboration Accept / Decline Buttons
            notification.type == "collaboration_request" -> {
                if (!isCollabHandled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                isCollabHandled = true
                                collabAccepted = true
                                onCollabResponse(true)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                isCollabHandled = true
                                collabAccepted = false
                                onCollabResponse(false)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Decline", fontSize = 11.sp)
                        }
                    }
                } else {
                    Text(
                        text = if (collabAccepted) "Accepted" else "Declined",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (collabAccepted) Color(0xFF17BF63) else Color.Gray
                    )
                }
            }

            // 3. Live Join Button
            notification.type in listOf("live_start", "live_invite") -> {
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Watch", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            // 4. Media Thumbnail
            !notification.mediaUrl.isNullOrEmpty() -> {
                AsyncImage(
                    model = notification.mediaUrl,
                    contentDescription = "Content preview",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun NotificationAvatarComponent(notification: SocialIndiaNotification) {
    Box(modifier = Modifier.size(50.dp)) {
        if (notification.groupedActors.size > 1) {
            // Grouped multi-avatar representation
            val firstActor = notification.groupedActors[0]
            val secondActor = notification.groupedActors[1]

            // Background actor
            AsyncImage(
                model = secondActor.photoUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${secondActor.uid}" },
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .align(Alignment.TopEnd)
                    .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentScale = ContentScale.Crop
            )

            // Foreground actor
            AsyncImage(
                model = firstActor.photoUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${firstActor.uid}" },
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .align(Alignment.BottomStart)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Single Actor Avatar
            AsyncImage(
                model = notification.actorPhoto.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${notification.actorUid}" },
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .align(Alignment.Center),
                contentScale = ContentScale.Crop
            )

            // Category badge overlay at bottom end
            val badge = getNotificationBadgeIcon(notification.type)
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(badge.color)
                    .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badge.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

private data class BadgeInfo(val icon: ImageVector, val color: Color)

private fun getNotificationBadgeIcon(type: String): BadgeInfo {
    return when (type) {
        "like", "story_like", "reel_like" -> BadgeInfo(Icons.Default.Favorite, Color(0xFFE0245E))
        "comment", "reply", "story_reply" -> BadgeInfo(Icons.Default.ChatBubble, Color(0xFF1DA1F2))
        "follow", "follow_request", "follow_accepted" -> BadgeInfo(Icons.Default.Person, Color(0xFF17BF63))
        "mention", "story_mention" -> BadgeInfo(Icons.Default.AlternateEmail, Color(0xFFF45D22))
        "message", "group_message" -> BadgeInfo(Icons.Default.Mail, Color(0xFF0095F6))
        "live_start", "live_invite", "live_gift" -> BadgeInfo(Icons.Default.LiveTv, Color(0xFFFF1744))
        "creator_milestone", "monetization" -> BadgeInfo(Icons.Default.Star, Color(0xFFFF9800))
        "exclusive_request" -> BadgeInfo(Icons.Default.WorkspacePremium, Color(0xFFFFB300))
        "exclusive_pending" -> BadgeInfo(Icons.Default.HourglassTop, Color(0xFFF59E0B))
        "exclusive_approved", "exclusive_unlock" -> BadgeInfo(Icons.Default.LockOpen, Color(0xFF10B981))
        "exclusive_rejected", "exclusive_refunded" -> BadgeInfo(Icons.Default.CurrencyRupee, Color(0xFF38BDF8))
        "security_alert", "new_login" -> BadgeInfo(Icons.Default.Shield, Color(0xFFD32F2F))
        else -> BadgeInfo(Icons.Default.Notifications, Color(0xFF673AB7))
    }
}

private fun formatRelativeTimestamp(millis: Long): String {
    val diff = (System.currentTimeMillis() - millis).coerceAtLeast(0)
    val seconds = diff / 1000
    if (seconds < 60) return "Just now"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h"
    val days = hours / 24
    if (days < 7) return "${days}d"
    val weeks = days / 7
    return "${weeks}w"
}

private fun handleNotificationNavigation(
    notification: SocialIndiaNotification,
    onNavigate: (String) -> Unit
) {
    if (notification.deepLink.isNotEmpty()) {
        onNavigate(notification.deepLink)
    } else {
        when (notification.type) {
            "mention", "story_mention" -> {
                val cType = notification.targetType ?: (notification.metadata["contentType"] as? String) ?: ""
                if (notification.deepLink.isNotEmpty()) {
                    onNavigate(notification.deepLink)
                } else if (cType == "reel" && !notification.targetId.isNullOrEmpty()) {
                    onNavigate("reel/${notification.targetId}")
                } else if (cType == "story" && !notification.targetId.isNullOrEmpty()) {
                    onNavigate("story/${notification.targetId}")
                } else if (!notification.targetId.isNullOrEmpty()) {
                    onNavigate("post_detail/${notification.targetId}")
                } else if (notification.actorUid.isNotEmpty()) {
                    onNavigate("profile/${notification.actorUid}")
                } else {
                    onNavigate("home")
                }
            }
            "like", "comment", "reply", "repost", "share" -> {
                if (!notification.targetId.isNullOrEmpty()) {
                    onNavigate("post_detail/${notification.targetId}")
                } else {
                    onNavigate("home")
                }
            }
            "follow", "follow_request", "follow_accepted" -> {
                if (notification.actorUid.isNotEmpty()) {
                    onNavigate("profile/${notification.actorUid}")
                }
            }
            "message" -> {
                if (notification.actorUid.isNotEmpty()) {
                    onNavigate("inbox_chat/${notification.actorUid}")
                }
            }
            "live_start", "live_invite" -> {
                if (!notification.targetId.isNullOrEmpty()) {
                    onNavigate("live_viewer/${notification.targetId}")
                }
            }
            "creator_milestone", "monetization" -> {
                onNavigate("professional_dashboard")
            }
            "exclusive_request" -> {
                onNavigate("exclusive_requests")
            }
            "exclusive_pending", "exclusive_rejected", "exclusive_refunded", "payment_success" -> {
                onNavigate("wallet")
            }
            "exclusive_approved", "exclusive_unlock" -> {
                val cType = notification.targetType ?: (notification.metadata["contentType"] as? String) ?: ""
                if (cType.equals("reel", ignoreCase = true) && !notification.targetId.isNullOrEmpty()) {
                    onNavigate("reel/${notification.targetId}")
                } else if (!notification.targetId.isNullOrEmpty()) {
                    onNavigate("post_detail/${notification.targetId}")
                } else {
                    onNavigate("home")
                }
            }
            "security_alert" -> {
                onNavigate("privacy_settings")
            }
            else -> {
                if (!notification.targetId.isNullOrEmpty()) {
                    onNavigate("post_detail/${notification.targetId}")
                } else if (notification.actorUid.isNotEmpty()) {
                    onNavigate("profile/${notification.actorUid}")
                } else {
                    onNavigate("home")
                }
            }
        }
    }
}

@Composable
private fun NotificationCenterEmptyState(
    selectedFilter: NotificationFilter,
    onResetFilter: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsNone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (selectedFilter == NotificationFilter.ALL) "No notifications yet" else "No ${selectedFilter.label} notifications",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (selectedFilter == NotificationFilter.ALL) {
                    "When someone likes, comments, or follows you, you'll see it here."
                } else {
                    "You don't have any notifications under the ${selectedFilter.label} filter right now."
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (selectedFilter != NotificationFilter.ALL) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onResetFilter) {
                    Text("Show All Notifications")
                }
            }
        }
    }
}
