package com.example.ui
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.scale

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
import androidx.compose.material.icons.filled.*

import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*

import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Tab

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Modifier
import com.example.ui.UserLiveAvatar
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import coil.compose.AsyncImage
import com.example.model.RepositoryManager
import com.example.model.MessageEntity
import com.example.model.Video
import com.example.model.LivePresenceManager
import com.example.viewmodel.NotificationViewModel
import kotlinx.coroutines.launch

data class Notification(
    val id: String,
    val type: NotificationType,
    val username: String,
    val userId: String = "",
    val avatarUrl: String,
    val content: String,
    val time: String,
    val isRead: Boolean = false,
    val postId: String? = null,
    val storyId: String? = null,
    val channelId: String? = null,
    val commentId: String? = null,
    val contentType: String? = null,
    val postThumbnail: String? = null,
    val timestamp: String = "",
    val timestampMillis: Long = 0L
)

enum class NotificationType {
    LIKE, COMMENT, FOLLOW, MENTION, REPLY, SHARE, STORY, STORY_MENTION, VERIFICATION, COLLABORATION_REQUEST, COLLABORATION_ACCEPTED, COLLABORATION_DECLINED, COLLABORATION_REMOVED, NEW_POST, CHANNEL_UPDATE, MESSAGE, FOLLOW_REQUEST, FOLLOW_ACCEPTED, LIVE, NOTE_REPLY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    initialTab: String = "messages",
    initialTargetUserId: String? = null,
    viewModel: NotificationViewModel = viewModel(),
    videoViewModel: com.example.viewmodel.VideoViewModel = viewModel(),
    onNavigateToHome: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val videos by videoViewModel.videos.collectAsStateWithLifecycle()
    val allUsers = remember { mutableStateListOf<com.example.model.User>() }
    
    DisposableEffect(Unit) {
        val registration = RepositoryManager.firestore.collection("users")
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        com.example.model.User(
                            id = doc.id,
                            username = doc.getString("username") ?: "User",
                            avatarUrl = doc.getString("profilePicUrl")
                                ?: doc.getString("avatarUrl")
                                ?: "https://api.dicebear.com/7.x/avataaars/png?seed=${doc.id}",
                            isAdmin = doc.getBoolean("isAdmin") ?: false,
                            isVerified = doc.getBoolean("isVerified") ?: false,
                            status = doc.getString("status") ?: "offline",
                            verificationBadgeUrl = doc.getString("verificationBadgeUrl") ?: ""
                        )
                    }
                    allUsers.clear()
                    allUsers.addAll(list)
                }
            }
        onDispose {
            registration.remove()
        }
    }

    var activeTab by remember {
        mutableStateOf(
            when (initialTab) {
                "notifications", "activity", "alerts" -> 2
                "groups" -> 1
                "public" -> 3
                else -> 0 // Default to DMs
            }
        )
    }
    var showStoryCreator by remember { mutableStateOf(false) }
    var storyMentionForPreview by remember { mutableStateOf<Notification?>(null) }
    var storyMentionMediaUrl by remember { mutableStateOf<String?>(null) }
    var storyMentionAuthorUid by remember { mutableStateOf<String?>(null) }
    var storyMentionAuthorName by remember { mutableStateOf<String?>(null) }
    var storyMentionText by remember { mutableStateOf("") }
    var selectedChatUser by remember { mutableStateOf<com.example.model.User?>(null) }
    var selectedGroupChat by remember { mutableStateOf<com.example.model.GroupChat?>(null) }
    var showGroupCreationDialog by remember { mutableStateOf(false) }
    var showCreateNoteDialog by remember { mutableStateOf(false) }
    var inputUserIdChat by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    var currentUserId by remember { mutableStateOf("") }

    LaunchedEffect(initialTargetUserId, allUsers.size) {
        if (!initialTargetUserId.isNullOrEmpty()) {
            val user = allUsers.find { it.id == initialTargetUserId }
            if (user != null) {
                selectedChatUser = user
            } else {
                RepositoryManager.firestore.collection("users").document(initialTargetUserId)
                    .get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            selectedChatUser = com.example.model.User(
                                id = doc.id,
                                username = doc.getString("username") ?: "User",
                                avatarUrl = doc.getString("profilePicUrl") ?: "https://api.dicebear.com/7.x/avataaars/png?seed=${doc.id}",
                                isVerified = doc.getBoolean("isVerified") ?: false
                            )
                        }
                    }
            }
        }
    }

    val activeAcc = com.example.auth.MultiAccountManager.activeAccount.value
    val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    val currentUserName = activeAcc?.username?.ifEmpty { null }
        ?: activeAcc?.displayName?.ifEmpty { null }
        ?: fbUser?.displayName?.ifEmpty { null }
        ?: "You"
    val currentUserAvatar = activeAcc?.avatarUrl?.ifEmpty { null }
        ?: fbUser?.photoUrl?.toString()?.ifEmpty { null }
        ?: ""
    
    val activeChatUserIds by remember(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            RepositoryManager.socialRepository.getActiveChatUserIds(currentUserId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptySet())
        }
    }.collectAsStateWithLifecycle(initialValue = emptySet())

    val userGroups by remember(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            com.example.repository.GroupChatRepository.listenUserGroups(currentUserId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        currentUserId = RepositoryManager.getCurrentUserId()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        var selectedFilterPill by remember { mutableStateOf("All") }
        val unreadCount = remember(notifications) { notifications.count { !it.isRead } }

        // Instagram Direct Top Bar: Back arrow, username dropdown, Call History, New Message icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onNavigateToHome() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            com.example.utils.SoundManager.playClickSound()
                            activeTab = 0
                        }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = currentUserName,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Switch Account",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onNavigate("call_history") },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.PhoneCallback,
                        contentDescription = "Call History",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = {
                        com.example.utils.SoundManager.playClickSound()
                        val firstSuggested = allUsers.firstOrNull { it.id != currentUserId }
                        if (firstSuggested != null) {
                            selectedChatUser = firstSuggested
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.EditNote,
                        contentDescription = "New Message",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
        
        if (activeTab == 2) {
            // Category pills row so user can switch back from Alerts to Messages/Groups/Public anytime
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val topTabs = listOf("All" to 0, "Groups" to 1, "Alerts" to 2, "Public" to 3)
                items(topTabs) { (label, tabIdx) ->
                    val isSel = activeTab == tabIdx
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSel) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.clickable {
                            com.example.utils.SoundManager.playClickSound()
                            activeTab = tabIdx
                        }
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }
            }
            // --- NOTIFICATIONS TAB ---
            FlagshipNotificationCenter(
                notifications = notifications,
                onNotificationClick = { notification ->
                    viewModel.markAsRead(notification.id)
                    when (notification.type) {
                        NotificationType.LIKE -> {
                            if (!notification.postId.isNullOrEmpty()) {
                                com.example.ui.GlobalState.targetVideoId.value = notification.postId
                                onNavigate("reels")
                            }
                        }
                        NotificationType.COMMENT, NotificationType.REPLY -> {
                            if (!notification.postId.isNullOrEmpty()) {
                                com.example.ui.GlobalState.targetVideoId.value = notification.postId
                                com.example.ui.GlobalState.openCommentsForVideoId.value = notification.postId
                                onNavigate("reels")
                            }
                        }
                        NotificationType.MENTION -> {
                            if (notification.contentType == "story" || !notification.storyId.isNullOrEmpty() || (notification.postThumbnail != null && notification.content.contains("story", ignoreCase = true))) {
                                storyMentionForPreview = notification
                            } else if (!notification.postId.isNullOrEmpty()) {
                                com.example.ui.GlobalState.targetVideoId.value = notification.postId
                                if (notification.contentType == "comment" || !notification.commentId.isNullOrEmpty()) {
                                    com.example.ui.GlobalState.openCommentsForVideoId.value = notification.postId
                                }
                                onNavigate("reels")
                            } else if (!notification.channelId.isNullOrEmpty()) {
                                onNavigate("broadcast_chat/${notification.channelId}")
                            } else if (notification.userId.isNotEmpty()) {
                                onNavigate("profile/${notification.userId}")
                            }
                        }
                        NotificationType.STORY_MENTION -> {
                            if (!notification.postThumbnail.isNullOrEmpty() || !notification.storyId.isNullOrEmpty()) {
                                storyMentionForPreview = notification
                            } else if (!notification.storyId.isNullOrEmpty()) {
                                com.example.ui.GlobalState.targetStoryId.value = notification.storyId
                                onNavigate("home")
                            } else if (notification.userId.isNotEmpty()) {
                                onNavigate("profile/${notification.userId}")
                            }
                        }
                        NotificationType.STORY -> {
                            if (!notification.storyId.isNullOrEmpty()) {
                                com.example.ui.GlobalState.targetStoryId.value = notification.storyId
                                onNavigate("home")
                            } else if (notification.userId.isNotEmpty()) {
                                onNavigate("profile/${notification.userId}")
                            }
                        }
                        NotificationType.FOLLOW -> {
                            if (notification.userId.isNotEmpty()) {
                                onNavigate("profile/${notification.userId}")
                            }
                        }
                        NotificationType.CHANNEL_UPDATE -> {
                            if (!notification.channelId.isNullOrEmpty()) {
                                onNavigate("broadcast_chat/${notification.channelId}")
                            }
                        }
                        NotificationType.MESSAGE -> {
                            if (notification.userId.isNotEmpty()) {
                                val match = allUsers.find { it.id == notification.userId } ?: com.example.model.User(
                                    id = notification.userId,
                                    username = notification.username,
                                    avatarUrl = notification.avatarUrl
                                )
                                selectedChatUser = match
                            }
                        }
                        NotificationType.LIVE -> {
                            if (notification.userId.isNotEmpty()) {
                                val activeLive = com.example.model.LivePresenceManager.activeLives.value[notification.userId]
                                if (activeLive != null) {
                                    val viewerUid = com.example.model.RepositoryManager.getCurrentUserId()
                                    try {
                                        val intent = android.content.Intent(context, com.example.ui.LiveActivity::class.java).apply {
                                            putExtra("liveID", activeLive.storyId)
                                            putExtra("userID", viewerUid.ifEmpty { "guest_${System.currentTimeMillis() % 10000}" })
                                            putExtra("userName", com.example.model.RepositoryManager.currentUserState.value?.username?.ifBlank { "Viewer" } ?: "Viewer")
                                            putExtra("isHost", activeLive.userId == viewerUid)
                                            putExtra("hostUid", activeLive.userId)
                                            putExtra("hostUsername", activeLive.username.ifEmpty { notification.username.ifEmpty { "Host" } })
                                            putExtra("hostAvatar", activeLive.avatarUrl.ifEmpty { notification.avatarUrl })
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        onNavigate("profile/${notification.userId}")
                                    }
                                } else {
                                    onNavigate("profile/${notification.userId}")
                                }
                            }
                        }
                        NotificationType.NOTE_REPLY -> {
                            if (notification.userId.isNotEmpty()) {
                                val match = allUsers.find { it.id == notification.userId } ?: com.example.model.User(
                                    id = notification.userId,
                                    username = notification.username,
                                    avatarUrl = notification.avatarUrl
                                )
                                selectedChatUser = match
                            }
                        }
                        NotificationType.FOLLOW, NotificationType.FOLLOW_REQUEST, NotificationType.FOLLOW_ACCEPTED -> {
                            if (notification.userId.isNotEmpty()) {
                                onNavigate("profile/${notification.userId}")
                            }
                        }
                        NotificationType.COLLABORATION_REQUEST, NotificationType.COLLABORATION_ACCEPTED -> {
                            if (!notification.postId.isNullOrEmpty()) {
                                com.example.ui.GlobalState.targetVideoId.value = notification.postId
                                onNavigate("reels")
                            }
                        }
                        else -> {
                            if (!notification.postId.isNullOrEmpty()) {
                                if (notification.contentType.equals("POST", ignoreCase = true)) {
                                    onNavigate("post_detail/${notification.postId}")
                                } else {
                                    com.example.ui.GlobalState.targetVideoId.value = notification.postId
                                    onNavigate("reels")
                                }
                            } else if (notification.userId.isNotEmpty()) {
                                onNavigate("profile/${notification.userId}")
                            }
                        }
                    }
                },
                onDeleteNotification = { notification ->
                    viewModel.deleteNotification(notification.id)
                },
                onMarkRead = { notification ->
                    viewModel.markAsRead(notification.id)
                }
            )
        } else if (activeTab == 0) {
            // --- DIRECT MESSAGES TAB ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
            ) {
                // Rounded Pill Search Bar ("Search or ask Meta AI") + "Filter" button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = inputUserIdChat,
                        onValueChange = { inputUserIdChat = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(24.dp),
                        placeholder = {
                            Text(
                                "Search or ask Meta AI",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (inputUserIdChat.isNotEmpty()) {
                                IconButton(onClick = {
                                    com.example.utils.SoundManager.playClickSound()
                                    val match = allUsers.firstOrNull {
                                        it.id.equals(inputUserIdChat, ignoreCase = true) ||
                                        it.username.equals(inputUserIdChat, ignoreCase = true)
                                    }
                                    if (match != null) {
                                        selectedChatUser = match
                                        inputUserIdChat = ""
                                    } else {
                                        selectedChatUser = com.example.model.User(
                                            id = inputUserIdChat,
                                            username = inputUserIdChat,
                                            avatarUrl = "https://api.dicebear.com/7.x/avataaars/png?seed=$inputUserIdChat"
                                        )
                                        inputUserIdChat = ""
                                    }
                                }) {
                                    Icon(Icons.Default.Send, contentDescription = "Start Chat", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    )

                    Text(
                        text = "Filter",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                com.example.utils.SoundManager.playClickSound()
                                selectedFilterPill = if (selectedFilterPill == "All") "Primary" else "All"
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Notes Tray
                NotesTrayRow(
                    currentUserId = currentUserId,
                    currentUsername = currentUserName,
                    currentUserAvatar = currentUserAvatar,
                    onReplyToNote = { recipientId, recipientUsername, replyText ->
                        val match = allUsers.find { it.id == recipientId } ?: com.example.model.User(
                            id = recipientId,
                            username = recipientUsername,
                            avatarUrl = "https://api.dicebear.com/7.x/avataaars/png?seed=$recipientId"
                        )
                        scope.launch {
                            try {
                                com.example.model.RepositoryManager.socialRepository.sendMessage(
                                    senderId = currentUserId,
                                    receiverId = recipientId,
                                    message = replyText,
                                    mediaType = "NOTE_REPLY",
                                    replyToText = "Replying to note"
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("InboxScreen", "Error sending note reply", e)
                            }
                        }
                        selectedChatUser = match
                    },
                    onViewProfile = { targetUid ->
                        onNavigate("profile/$targetUid")
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Category Filter Pills ("All", "Primary" with blue dot, "General", "Requests", plus "Channels", "Groups", "Alerts", "Public")
                val filterPills = listOf("All", "Primary", "General", "Requests", "Channels", "Groups", "Alerts", "Public")
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filterPills) { pill ->
                        val isSelected = selectedFilterPill == pill
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.clickable {
                                com.example.utils.SoundManager.playClickSound()
                                when (pill) {
                                    "Channels" -> onNavigate("broadcast_channels")
                                    "Groups" -> activeTab = 1
                                    "Alerts" -> activeTab = 2
                                    "Public" -> activeTab = 3
                                    else -> selectedFilterPill = pill
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (pill == "Primary") {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0095F6))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = pill,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
                                )
                                if (pill == "Alerts" && unreadCount > 0) {
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFFF3040),
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (unreadCount > 99) "99+" else "$unreadCount",
                                                fontSize = 9.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val chatUsers = remember(allUsers, activeChatUserIds, currentUserId) {
                    allUsers.filter { activeChatUserIds.contains(it.id) && it.id != currentUserId }
                }
                val suggestedUsers = remember(allUsers, activeChatUserIds, currentUserId) {
                    allUsers.filter { !activeChatUserIds.contains(it.id) && it.id != currentUserId }
                }
                val filteredChatUsers = remember(selectedFilterPill, chatUsers, suggestedUsers) {
                    when (selectedFilterPill) {
                        "Primary" -> chatUsers
                        "General" -> suggestedUsers.take(8)
                        "Requests" -> suggestedUsers.drop(8)
                        else -> chatUsers
                    }
                }

                var isInitialInboxLoading by remember { mutableStateOf(allUsers.isEmpty()) }
                LaunchedEffect(allUsers) {
                    if (allUsers.isNotEmpty()) {
                        isInitialInboxLoading = false
                    } else {
                        kotlinx.coroutines.delay(400)
                        isInitialInboxLoading = false
                    }
                }

                val savedInboxScroll = remember(selectedFilterPill) { GlobalState.getScroll("inbox_$selectedFilterPill") }
                val inboxListState = androidx.compose.foundation.lazy.rememberLazyListState(
                    initialFirstVisibleItemIndex = savedInboxScroll.first,
                    initialFirstVisibleItemScrollOffset = savedInboxScroll.second
                )
                DisposableEffect(inboxListState, selectedFilterPill) {
                    onDispose {
                        GlobalState.saveScroll(
                            "inbox_$selectedFilterPill",
                            inboxListState.firstVisibleItemIndex,
                            inboxListState.firstVisibleItemScrollOffset
                        )
                    }
                }

                var visibleChatCount by remember(selectedFilterPill) { mutableIntStateOf(10) }
                var visibleSuggestedCount by remember(selectedFilterPill) { mutableIntStateOf(10) }
                val pagedChatUsers = remember(filteredChatUsers, visibleChatCount) {
                    filteredChatUsers.take(visibleChatCount)
                }
                val pagedSuggestedUsers = remember(suggestedUsers, visibleSuggestedCount) {
                    suggestedUsers.take(visibleSuggestedCount)
                }
                val shouldLoadMoreInbox by remember {
                    derivedStateOf {
                        val total = inboxListState.layoutInfo.totalItemsCount
                        val lastVisible = inboxListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        total > 0 && lastVisible >= total - 3
                    }
                }
                LaunchedEffect(shouldLoadMoreInbox, filteredChatUsers.size, suggestedUsers.size) {
                    if (shouldLoadMoreInbox) {
                        if (visibleChatCount < filteredChatUsers.size) {
                            visibleChatCount = (visibleChatCount + 10).coerceAtMost(filteredChatUsers.size)
                        } else if (visibleSuggestedCount < suggestedUsers.size) {
                            visibleSuggestedCount = (visibleSuggestedCount + 10).coerceAtMost(suggestedUsers.size)
                        }
                    }
                }

                val showInboxSkeleton = isInitialInboxLoading && filteredChatUsers.isEmpty() && suggestedUsers.isEmpty()
                val inboxShimmerBrush = if (showInboxSkeleton) {
                    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "inboxShimmer")
                    val shimmerX by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1000f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                        ),
                        label = "inboxShimmerX"
                    )
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        start = androidx.compose.ui.geometry.Offset(shimmerX - 300f, 0f),
                        end = androidx.compose.ui.geometry.Offset(shimmerX, 300f)
                    )
                } else null

                LazyColumn(
                    state = inboxListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    if (showInboxSkeleton && inboxShimmerBrush != null) {
                        items(7, key = { "inbox_skel_$it" }, contentType = { "inbox_skeleton" }) {
                            val shimmerBrush = inboxShimmerBrush
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(shimmerBrush)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(shimmerBrush)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(180.dp)
                                            .height(12.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(shimmerBrush)
                                    )
                                }
                            }
                        }
                    } else if (pagedChatUsers.isNotEmpty()) {
                        items(pagedChatUsers, key = { it.id }, contentType = { "chat_user_row" }) { u ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { com.example.utils.SoundManager.playClickSound(); selectedChatUser = u }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    UserLiveAvatar(userId = u.id, avatarUrl = u.avatarUrl, size = 56.dp, onClick = {
                                        com.example.utils.SoundManager.playClickSound()
                                        selectedChatUser = u
                                    })
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(14.dp)
                                            .background(
                                                if (u.status == "online") Color(0xFF43D854) else Color(0xFF43D854).copy(alpha = 0.85f),
                                                CircleShape
                                            )
                                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = u.username,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        if (u.isVerified) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            VerificationBadge(isVerified = true, badgeUrl = null, modifier = Modifier.size(14.dp))
                                        }
                                        // DM Flame Streak Counter Badge (🔥)
                                        val streakCount = remember(u.id, u.dmStreakCount) {
                                            if (u.dmStreakCount > 0) u.dmStreakCount else ((kotlin.math.abs(u.id.hashCode()) % 18) + 3)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFFF6D00).copy(alpha = 0.16f),
                                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFFF9100).copy(alpha = 0.45f))
                                        ) {
                                            Text(
                                                text = "🔥 $streakCount",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFFF6D00),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    val liveStatusPill = remember(u.id, u.liveActivityStatus, u.status) {
                                        if (u.liveActivityStatus.isNotBlank()) {
                                            u.liveActivityStatus
                                        } else if (u.status == "online") {
                                            if (kotlin.math.abs(u.id.hashCode()) % 2 == 0) "🎬 Watching Reels" else "🎵 Listening to Kesariya"
                                        } else ""
                                    }
                                    if (liveStatusPill.isNotEmpty()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF00E676).copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = liveStatusPill,
                                                    color = Color(0xFF00C853),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = if (u.status == "online") "Active now" else "Sent a message · 2m",
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { com.example.utils.SoundManager.playClickSound(); selectedChatUser = u },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Camera",
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!isInitialInboxLoading && pagedSuggestedUsers.isNotEmpty() && selectedFilterPill == "All") {
                        item(key = "suggestions_header", contentType = "inbox_header") {
                            Spacer(modifier = Modifier.height(if (pagedChatUsers.isNotEmpty()) 12.dp else 4.dp))
                            Text(
                                if (pagedChatUsers.isEmpty()) "Messages" else "Suggestions",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        items(pagedSuggestedUsers, key = { "sug_${it.id}" }, contentType = { "suggested_user_row" }) { u ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { com.example.utils.SoundManager.playClickSound(); selectedChatUser = u }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    UserLiveAvatar(userId = u.id, avatarUrl = u.avatarUrl, size = 56.dp, onClick = {
                                        com.example.utils.SoundManager.playClickSound()
                                        selectedChatUser = u
                                    })
                                    if (u.status == "online") {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(14.dp)
                                                .background(Color(0xFF43D854), CircleShape)
                                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = u.username,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        if (u.isVerified) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            VerificationBadge(isVerified = true, badgeUrl = null, modifier = Modifier.size(13.dp))
                                        }
                                        val sugStreak = remember(u.id, u.dmStreakCount) {
                                            if (u.dmStreakCount > 0) u.dmStreakCount else ((kotlin.math.abs(u.id.hashCode()) % 9) + 1)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFFF6D00).copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = "🔥 $sugStreak",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFF6D00),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (u.status == "online") "🎬 Watching Reels • Active now" else "🎵 Listening to Trending Audio",
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(
                                    onClick = { com.example.utils.SoundManager.playClickSound(); selectedChatUser = u },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Camera",
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    } else if (!isInitialInboxLoading && filteredChatUsers.isEmpty() && suggestedUsers.isEmpty()) {
                        item(key = "inbox_empty", contentType = "inbox_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("No Conversations Yet", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Search a friend above to start chatting", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }
        } else if (activeTab == 1) {
            // --- GROUPS TAB ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Header with Create Group button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Group Chats",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${userGroups.size} active groups",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Button(
                        onClick = {
                            com.example.utils.SoundManager.playClickSound()
                            showGroupCreationDialog = true
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Group", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (userGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Groups Yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Create a squad or community to chat, share media, run polls, and start video/audio calls with friends!",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    com.example.utils.SoundManager.playClickSound()
                                    showGroupCreationDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Your First Group", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(userGroups, key = { index, grp -> "${grp.id}_$index" }) { _, grp ->
                            val isUserAdmin = grp.admins.contains(currentUserId)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        com.example.utils.SoundManager.playClickSound()
                                        selectedGroupChat = grp
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box {
                                        AsyncImage(
                                            model = grp.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/identicon/png?seed=${grp.name}" },
                                            contentDescription = "Group Icon",
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (isUserAdmin) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .align(Alignment.BottomEnd)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFF9800)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Shield,
                                                    contentDescription = "Admin",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = grp.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = formatTime(grp.lastMessageTime),
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val snippet = if (grp.lastMessageSender.isNotEmpty() && grp.lastMessageSender != "System") {
                                                "${grp.lastMessageSender}: ${grp.lastMessage}"
                                            } else {
                                                grp.lastMessage.ifEmpty { "No messages yet" }
                                            }

                                            Text(
                                                text = snippet,
                                                fontSize = 13.sp,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Text(
                                                text = "${grp.members.size} members",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (activeTab == 3) {
            PublicChatScreen()
        }
        }
        
        storyMentionForPreview?.let { notif ->
            StoryMentionPreviewDialog(
                mediaUrl = notif.postThumbnail ?: "",
                textOverlay = notif.content.removePrefix("mentioned you in a story: ").trim('"', ' '),
                authorUsername = notif.username,
                authorAvatar = notif.avatarUrl,
                authorUid = notif.userId,
                storyId = notif.storyId ?: "",
                onDismiss = { storyMentionForPreview = null },
                onAddToStory = { mediaUrl, authorUsername, authorUid, textOverlay ->
                    storyMentionForPreview = null
                    storyMentionMediaUrl = mediaUrl
                    storyMentionAuthorUid = authorUid
                    storyMentionAuthorName = authorUsername
                    storyMentionText = "@$authorUsername"
                    showStoryCreator = true
                }
            )
        }

        if (showStoryCreator) {
            StoryCreatorDialog(
                onDismiss = {
                    showStoryCreator = false
                    storyMentionMediaUrl = null
                    storyMentionAuthorUid = null
                    storyMentionAuthorName = null
                    storyMentionText = ""
                },
                initialMediaUri = storyMentionMediaUrl?.let { android.net.Uri.parse(it) },
                repostVideoUrl = storyMentionMediaUrl,
                mentionedUserId = storyMentionAuthorUid,
                mentionedUsername = storyMentionAuthorName,
                initialText = storyMentionText,
                isReback = storyMentionMediaUrl != null
            )
        }
        
        // Fullscreen Direct Chat Sheet / Window
        selectedChatUser?.let { chatUser ->
            ChatWindow(
                currentUserId = currentUserId,
                otherUser = chatUser,
                videos = videos,
                onDismiss = { selectedChatUser = null },
                onNavigateToHome = onNavigateToHome,
                onNavigate = onNavigate
            )
        }

        // Fullscreen Group Chat Screen
        selectedGroupChat?.let { grp ->
            GroupChatScreen(
                initialGroup = grp,
                currentUserId = currentUserId,
                currentUserName = currentUserName,
                currentUserAvatar = currentUserAvatar,
                allUsers = allUsers,
                onDismiss = { selectedGroupChat = null },
                onStartDirectChat = { target ->
                    selectedGroupChat = null
                    selectedChatUser = target
                },
                onViewProfile = { uid ->
                    onNavigate("profile/$uid")
                }
            )
        }

        // Group Creation Wizard Dialog
        if (showGroupCreationDialog) {
            GroupCreationDialog(
                currentUserId = currentUserId,
                currentUserName = currentUserName,
                availableUsers = allUsers,
                onDismiss = { showGroupCreationDialog = false },
                onGroupCreated = { newGroupId ->
                    showGroupCreationDialog = false
                    selectedGroupChat = com.example.model.GroupChat(
                        id = newGroupId,
                        name = "New Group",
                        members = listOf(currentUserId)
                    )
                }
            )
        }
    }
}

@Composable
fun ChatWindow(
    currentUserId: String,
    otherUser: com.example.model.User,
    videos: List<com.example.model.Video>,
    onDismiss: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var messages by remember { mutableStateOf(listOf<com.example.model.MessageEntity>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentUserId, otherUser.id) {
        var previousCount = 0
        com.example.model.RepositoryManager.socialRepository.getChatMessages(currentUserId, otherUser.id).collect { newMessages ->
            if (previousCount > 0 && newMessages.size > previousCount) {
                val latest = newMessages.lastOrNull()
                if (latest != null && latest.senderId == otherUser.id) {
                    com.example.utils.MessengerSoundManager.onIncomingMessageReceived(
                        context = context,
                        messageId = latest.id,
                        senderId = latest.senderId,
                        isCurrentlyInChat = true
                    )
                }
            }
            previousCount = newMessages.size
            messages = newMessages
        }
    }

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            com.example.model.RepositoryManager.socialRepository.markMessagesAsRead(currentUserId, otherUser.id, messages)
        }
    }

    FlagshipChatWindow(
        currentUserId = currentUserId,
        otherUser = otherUser,
        messages = messages,
        onSendMessage = { text, mediaType, mediaUrl, videoDuration, replyToId, replyToText, thumbnailUrl ->
            scope.launch {
                try {
                    com.example.model.RepositoryManager.socialRepository.sendMessage(
                        senderId = currentUserId,
                        receiverId = otherUser.id,
                        message = text,
                        mediaType = mediaType,
                        mediaUrl = mediaUrl,
                        thumbnailUrl = thumbnailUrl,
                        videoDurationSeconds = videoDuration,
                        replyToId = replyToId,
                        replyToText = replyToText
                    )
                    // Play subtle outgoing sent sound immediately upon verified server save
                    com.example.utils.MessengerSoundManager.playMessageSentSound(context)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Failed to send message", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        },
        onDismiss = onDismiss,
        onCallClick = { isVideo ->
            GlobalCallManager.initiateCall(
                context = context,
                peerId = otherUser.id,
                peerUsername = otherUser.username.ifEmpty { "User" },
                peerDisplayName = otherUser.displayName.ifEmpty { otherUser.username.ifEmpty { "User" } },
                peerAvatarUrl = otherUser.avatarUrl,
                isVideo = isVideo
            )
        },
        onOpenReel = { reelId ->
            GlobalState.targetVideoId.value = reelId
            onNavigate("reels")
        }
    )
}
