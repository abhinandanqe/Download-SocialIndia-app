package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.outlined.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.model.FirebasePost
import com.example.model.RepositoryManager
import com.example.repository.FlagshipFeaturesRepository
import com.example.repository.MentionRepository
import com.example.repository.MonetizationRepository
import com.example.utils.GlobalAudioPreviewManager
import com.example.utils.ProfileScrollStateStore
import com.example.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.testTag
import com.example.repository.EntitlementRepository
import com.example.repository.ExclusiveContentRepository
import com.example.repository.ExclusivePurchaseRecord
import kotlin.math.roundToInt

private data class ProfileTabItem(
    val id: Int,
    val icon: ImageVector,
    val label: String
)

private val ProfileTilePlaceholderBrush = androidx.compose.ui.graphics.Brush.linearGradient(
    listOf(Color(0xFF231F3A), Color(0xFF1B182E))
)

private val ProfileReelScrimBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    profileUserId: String?,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToReel: (String) -> Unit,
    onNavigateToPostDetail: (postId: String, userId: String?) -> Unit = { _, _ -> },
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToFollows: (String, String) -> Unit = { _, _ -> },
    onNavigateToDiscoverPeople: () -> Unit = {},
    onNavigateToSaved: () -> Unit = {},
    onBack: () -> Unit,
    onOpenAccountSwitcher: () -> Unit = {},
    onNavigateToChannels: () -> Unit = {},
    onNavigateToBusinessDashboard: () -> Unit = {},
    onNavigateToCreateAd: (String?) -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val profileViewModel: ProfileViewModel = viewModel()
    val profileUser by profileViewModel.profileUser.collectAsStateWithLifecycle()
    val userPosts by profileViewModel.userPosts.collectAsStateWithLifecycle()
    val likedPosts by profileViewModel.likedPosts.collectAsStateWithLifecycle()
    val collabPosts by profileViewModel.collabPosts.collectAsStateWithLifecycle()
    val repostedPosts by profileViewModel.repostedPosts.collectAsStateWithLifecycle()
    val reportedPosts by profileViewModel.reportedPosts.collectAsStateWithLifecycle()
    val isLoading by profileViewModel.isLoadingProfile.collectAsStateWithLifecycle()
    val isRefreshing by profileViewModel.isRefreshing.collectAsStateWithLifecycle()
    val isLoadingMore by profileViewModel.isLoadingMore.collectAsStateWithLifecycle()
    val hasMorePosts by profileViewModel.hasMorePosts.collectAsStateWithLifecycle()
    val profileError by profileViewModel.profileError.collectAsStateWithLifecycle()
    val userNotFound by profileViewModel.userNotFound.collectAsStateWithLifecycle()

    val currentUserId = RepositoryManager.getCurrentUserId()
    val normalizedProfileUserId = profileUserId?.trim()?.takeIf { it.isNotEmpty() }
    val isCurrentUser = normalizedProfileUserId == null || normalizedProfileUserId == currentUserId
    val targetProfileUid = normalizedProfileUserId ?: currentUserId

    val profileCosmetics by remember(targetProfileUid) {
        try {
            com.example.repository.ProCreatorSuiteRepository.observeUserCosmetics(targetProfileUid)
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(com.example.model.UserCosmeticsState())
        }
    }.collectAsStateWithLifecycle(initialValue = com.example.model.UserCosmeticsState())
    val equippedProfileThemeBrush = remember(profileCosmetics.equippedThemeId) {
        resolveProfileThemeBrush(profileCosmetics.equippedThemeId)
    }
    val hasDarkProfileTheme = equippedProfileThemeBrush != null
    val profileBgColor = if (hasDarkProfileTheme) Color(0xFF0A0C14) else MaterialTheme.colorScheme.background
    val profileTopBarBg = if (hasDarkProfileTheme) {
        resolveProfileThemeTopColor(profileCosmetics.equippedThemeId) ?: Color(0xFF0A0C14)
    } else {
        MaterialTheme.colorScheme.background
    }
    val profilePrimaryText = if (hasDarkProfileTheme) Color.White else MaterialTheme.colorScheme.onBackground
    val profileSecondaryText = if (hasDarkProfileTheme) Color(0xFFB4BCD0) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    val profileCardBg = if (hasDarkProfileTheme) Color.White.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    val profileCardBorder = if (hasDarkProfileTheme) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
    val monetizationSettingsFlow = remember {
        try {
            MonetizationRepository.observeMonetizationSettings()
        } catch (_: Throwable) {
            kotlinx.coroutines.flow.flowOf(com.example.model.MonetizationSettings())
        }
    }
    val profileMonetizationSettings by monetizationSettingsFlow
        .collectAsStateWithLifecycle(initialValue = com.example.model.MonetizationSettings())

    val activeAccount by com.example.auth.MultiAccountManager.activeAccount.collectAsStateWithLifecycle(initialValue = null)
    val activeAccountUid = activeAccount?.uid ?: currentUserId

    val creatorChannelsFlow = remember(targetProfileUid) {
        if (targetProfileUid.isNotEmpty()) {
            FlagshipFeaturesRepository.listenCreatorBroadcastChannels(targetProfileUid)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }
    val creatorChannels by creatorChannelsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val isFollowingCreatorFlow = remember(currentUserId, targetProfileUid, isCurrentUser) {
        if (!isCurrentUser && currentUserId.isNotEmpty() && targetProfileUid.isNotEmpty()) {
            RepositoryManager.socialRepository.isFollowing(currentUserId, targetProfileUid)
        } else {
            kotlinx.coroutines.flow.flowOf(false)
        }
    }
    val isFollowingCreator by isFollowingCreatorFlow.collectAsStateWithLifecycle(initialValue = false)

    // Creator subscription status
    val isSubscribedFlow = remember(currentUserId, targetProfileUid, isCurrentUser) {
        if (!isCurrentUser && currentUserId.isNotEmpty() && targetProfileUid.isNotEmpty()) {
            RepositoryManager.socialRepository.isSubscribedToCreator(currentUserId, targetProfileUid)
        } else {
            kotlinx.coroutines.flow.flowOf(false)
        }
    }
    val isSubscribedToCreator by isSubscribedFlow.collectAsStateWithLifecycle(initialValue = false)
    var showSubscribeDialog by remember { mutableStateOf(false) }
    var creatorPlan by remember { mutableStateOf<com.example.model.CreatorSubscriptionPlan?>(null) }

    LaunchedEffect(targetProfileUid) {
        if (!isCurrentUser && targetProfileUid.isNotEmpty()) {
            try {
                creatorPlan = MonetizationRepository.getSubscriptionPlan(targetProfileUid)
            } catch (_: Exception) {}
        }
    }

    val taggedPostsFlow = remember(targetProfileUid) {
        if (targetProfileUid.isNotEmpty()) {
            MentionRepository.getUserTaggedPostsFlow(targetProfileUid)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }
    val taggedPosts by taggedPostsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var postForMentionDetails by remember { mutableStateOf<FirebasePost?>(null) }
    var selectedTaggedPostForOptions by remember { mutableStateOf<FirebasePost?>(null) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showSuggestedPeople by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showStoryCreator by remember { mutableStateOf(false) }
    var showProfileStoryViewer by remember { mutableStateOf(false) }
    var profileUserStories by remember(targetProfileUid) { mutableStateOf<List<com.example.model.StoryUiModel>>(emptyList()) }
    val activeLivesMap by com.example.model.LivePresenceManager.activeLives.collectAsStateWithLifecycle()
    val profileLiveSession = activeLivesMap[targetProfileUid]
    var sharedPayloadForStory by remember { mutableStateOf<com.example.model.SharePayload?>(null) }
    var suggestedUsersList by remember { mutableStateOf<List<com.example.model.SuggestedUser>>(emptyList()) }
    var selectedPostForOptions by remember { mutableStateOf<FirebasePost?>(null) }
    var postForInsights by remember { mutableStateOf<FirebasePost?>(null) }
    var selectedHighlight by remember { mutableStateOf<com.example.model.StoryHighlight?>(null) }
    var showHighlightViewer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Real-time listener for this profile's active non-live stories (24h)
    DisposableEffect(targetProfileUid, profileUser?.username, profileUser?.avatarUrl, profileUser?.isVerified) {
        if (targetProfileUid.isBlank()) {
            profileUserStories = emptyList()
            onDispose {}
        } else {
            val twentyFourHoursMs = 24L * 60L * 60L * 1000L
            val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val reg = try {
                RepositoryManager.firestore.collection("stories")
                    .whereEqualTo("userId", targetProfileUid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null) return@addSnapshotListener
                        try {
                            val nowMs = System.currentTimeMillis()
                            val userSnap = profileUser
                            val parsed = snapshot.documents.mapNotNull { doc ->
                                try {
                                    val story = doc.toObject(com.example.model.Story::class.java)?.copy(id = doc.id)
                                        ?: return@mapNotNull null
                                    if (story.isLive || story.isDeleted || story.isRemovedByAdmin) return@mapNotNull null
                                    val ts = story.timestamp?.time ?: nowMs
                                    if ((nowMs - ts) > twentyFourHoursMs) return@mapNotNull null
                                    val hasContent = story.mediaUrl.isNotBlank() || story.blurredThumbnailUrl.isNotBlank() || story.textOverlay.isNotBlank()
                                    if (!hasContent || com.example.utils.MediaResolver.isDummyOrSampleUrl(story.mediaUrl)) {
                                        return@mapNotNull null
                                    }
                                    val stickersList = if (!story.stickersJson.isNullOrEmpty()) {
                                        try {
                                            jsonParser.decodeFromString<List<com.example.model.StorySticker>>(story.stickersJson)
                                        } catch (_: Exception) {
                                            emptyList()
                                        }
                                    } else emptyList()
                                    val diffMin = ((nowMs - ts) / 60_000L).coerceAtLeast(0L)
                                    val timeAgoLabel = when {
                                        diffMin < 1L -> "Just now"
                                        diffMin < 60L -> "${diffMin}m ago"
                                        else -> "${diffMin / 60L}h ago"
                                    }
                                    ts to com.example.model.StoryUiModel(
                                        id = story.id,
                                        username = userSnap?.username?.ifBlank { "User" } ?: "User",
                                        avatarUrl = userSnap?.avatarUrl ?: "",
                                        imageUrl = story.mediaUrl,
                                        textOverlay = story.textOverlay,
                                        timeAgo = timeAgoLabel,
                                        mentionedUserId = story.mentionedUserId,
                                        mentionedUsername = story.mentionedUsername,
                                        userId = story.userId,
                                        viewers = story.viewers,
                                        likesCount = story.likesCount,
                                        commentsCount = story.commentsCount,
                                        isVerified = userSnap?.isVerified ?: false,
                                        verificationBadgeUrl = userSnap?.verificationBadgeUrl ?: "",
                                        timestamp = ts,
                                        isLive = false,
                                        isReback = story.isReback,
                                        repostedPostId = story.repostedPostId,
                                        musicTrackName = story.musicTrackName,
                                        musicTrackArtist = story.musicTrackArtist,
                                        musicTrackUrl = story.musicTrackUrl,
                                        isCloseFriendsOnly = story.isCloseFriendsOnly,
                                        stickers = stickersList,
                                        isExclusiveContent = story.isExclusiveContent,
                                        exclusivePrice = story.exclusivePrice,
                                        teaserCaption = story.teaserCaption,
                                        blurredThumbnailUrl = story.blurredThumbnailUrl,
                                        hasVaultMedia = story.hasVaultMedia,
                                        unlockCount = story.unlockCount
                                    )
                                } catch (_: Exception) {
                                    null
                                }
                            }.sortedByDescending { it.first }.map { it.second }
                            profileUserStories = parsed
                        } catch (_: Exception) {}
                    }
            } catch (_: Exception) {
                null
            }
            onDispose { reg?.remove() }
        }
    }

    // Independent Scroll Positions per Tab (Posts, Reels, Mentions, Collabs, Liked, Reposts, Reported, Trials)
    val postsGridState = remember(activeAccountUid, targetProfileUid) {
        val pos = ProfileScrollStateStore.getPosition(activeAccountUid, targetProfileUid, 0)
        LazyGridState(pos.first, pos.second)
    }
    val reelsGridState = remember(activeAccountUid, targetProfileUid) {
        val pos = ProfileScrollStateStore.getPosition(activeAccountUid, targetProfileUid, 1)
        LazyGridState(pos.first, pos.second)
    }
    val taggedGridState = remember(activeAccountUid, targetProfileUid) {
        val pos = ProfileScrollStateStore.getPosition(activeAccountUid, targetProfileUid, 2)
        LazyGridState(pos.first, pos.second)
    }
    val collabsGridState = remember(activeAccountUid, targetProfileUid) {
        val pos = ProfileScrollStateStore.getPosition(activeAccountUid, targetProfileUid, 3)
        LazyGridState(pos.first, pos.second)
    }
    val likedGridState = remember(activeAccountUid, targetProfileUid) {
        val pos = ProfileScrollStateStore.getPosition(activeAccountUid, targetProfileUid, 4)
        LazyGridState(pos.first, pos.second)
    }
    val repostsGridState = remember(activeAccountUid, targetProfileUid) {
        val pos = ProfileScrollStateStore.getPosition(activeAccountUid, targetProfileUid, 5)
        LazyGridState(pos.first, pos.second)
    }
    val reportedGridState = remember(activeAccountUid, targetProfileUid) {
        val pos = ProfileScrollStateStore.getPosition(activeAccountUid, targetProfileUid, 6)
        LazyGridState(pos.first, pos.second)
    }
    val trialsGridState = remember(activeAccountUid, targetProfileUid) {
        val pos = ProfileScrollStateStore.getPosition(activeAccountUid, targetProfileUid, 7)
        LazyGridState(pos.first, pos.second)
    }
    val unlockedGridState = remember(activeAccountUid, targetProfileUid) {
        val pos = ProfileScrollStateStore.getPosition(activeAccountUid, targetProfileUid, 8)
        LazyGridState(pos.first, pos.second)
    }

    // Exclusive content unlock modal & unlocked story quick viewer
    var selectedExclusivePostToUnlock by remember { mutableStateOf<FirebasePost?>(null) }
    var unlockedStoryToView by remember { mutableStateOf<com.example.model.StoryUiModel?>(null) }

    // Coordinated Collapsible Header state
    var headerHeightPx by remember { mutableFloatStateOf(0f) }
    var headerOffset by remember(activeAccountUid, targetProfileUid) {
        mutableFloatStateOf(ProfileScrollStateStore.getHeaderOffset(activeAccountUid, targetProfileUid))
    }

    // Determine current active grid state
    val activeGridState = when (selectedTabIndex) {
        0 -> postsGridState
        1 -> reelsGridState
        2 -> if (isCurrentUser) unlockedGridState else taggedGridState
        3 -> if (isCurrentUser) taggedGridState else collabsGridState
        4 -> if (isCurrentUser) collabsGridState else repostsGridState
        5 -> likedGridState
        6 -> repostsGridState
        7 -> reportedGridState
        else -> trialsGridState
    }

    // Auto-save active grid position via snapshotFlow (zero per-pixel recomposition of ProfileScreen)
    LaunchedEffect(activeGridState, selectedTabIndex, activeAccountUid, targetProfileUid) {
        var lastSavedIdx = -1
        snapshotFlow { activeGridState.firstVisibleItemIndex to activeGridState.firstVisibleItemScrollOffset }
            .collect { (idx, offset) ->
                if (idx != lastSavedIdx) {
                    lastSavedIdx = idx
                    ProfileScrollStateStore.savePosition(
                        activeAccountUid,
                        targetProfileUid,
                        selectedTabIndex,
                        idx,
                        offset
                    )
                }
            }
    }

    // Save header offset and all scroll states on exit / dispose
    DisposableEffect(activeAccountUid, targetProfileUid) {
        onDispose {
            ProfileScrollStateStore.savePosition(activeAccountUid, targetProfileUid, 0, postsGridState.firstVisibleItemIndex, postsGridState.firstVisibleItemScrollOffset)
            ProfileScrollStateStore.savePosition(activeAccountUid, targetProfileUid, 1, reelsGridState.firstVisibleItemIndex, reelsGridState.firstVisibleItemScrollOffset)
            ProfileScrollStateStore.savePosition(activeAccountUid, targetProfileUid, 2, collabsGridState.firstVisibleItemIndex, collabsGridState.firstVisibleItemScrollOffset)
            ProfileScrollStateStore.savePosition(activeAccountUid, targetProfileUid, 3, likedGridState.firstVisibleItemIndex, likedGridState.firstVisibleItemScrollOffset)
            ProfileScrollStateStore.savePosition(activeAccountUid, targetProfileUid, 4, repostsGridState.firstVisibleItemIndex, repostsGridState.firstVisibleItemScrollOffset)
            ProfileScrollStateStore.savePosition(activeAccountUid, targetProfileUid, 5, reportedGridState.firstVisibleItemIndex, reportedGridState.firstVisibleItemScrollOffset)
            ProfileScrollStateStore.savePosition(activeAccountUid, targetProfileUid, 6, trialsGridState.firstVisibleItemIndex, trialsGridState.firstVisibleItemScrollOffset)
            ProfileScrollStateStore.saveHeaderOffset(activeAccountUid, targetProfileUid, headerOffset)
        }
    }

    // Instagram-style NestedScrollConnection for coordinated header collapsing & sticky tabs
    val nestedScrollConnection = remember(headerHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                // Up-swipe (delta < 0): collapse header first until fully collapsed (-headerHeightPx)
                if (delta < 0f && headerHeightPx > 0f && headerOffset > -headerHeightPx) {
                    val oldOffset = headerOffset
                    val newOffset = (oldOffset + delta).coerceIn(-headerHeightPx, 0f)
                    headerOffset = newOffset
                    val consumedY = newOffset - oldOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                // Down-swipe (delta > 0): after child grid reaches top, expand header back to 0f
                if (delta > 0f && headerHeightPx > 0f && headerOffset < 0f) {
                    val oldOffset = headerOffset
                    val newOffset = (oldOffset + delta).coerceIn(-headerHeightPx, 0f)
                    headerOffset = newOffset
                    val consumedY = newOffset - oldOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }
        }
    }

    // Smooth scroll to top function (tapped TopAppBar or active tab)
    val onScrollToTop: () -> Unit = {
        scope.launch {
            launch {
                try {
                    activeGridState.animateScrollToItem(0)
                } catch (_: Exception) {}
            }
            launch {
                if (headerOffset < 0f) {
                    animate(
                        initialValue = headerOffset,
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { value, _ ->
                        headerOffset = value
                    }
                }
            }
        }
    }

    // Infinite scroll trigger for posts pagination
    val shouldLoadMore by remember(activeGridState, selectedTabIndex) {
        derivedStateOf {
            if (selectedTabIndex != 0) false
            else {
                val lastVisible = activeGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val total = activeGridState.layoutInfo.totalItemsCount
                total > 0 && lastVisible >= total - 6
            }
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMorePosts && !isLoadingMore) {
            profileViewModel.loadMorePosts(targetProfileUid)
        }
    }

    LaunchedEffect(profileUserId) {
        profileViewModel.loadProfile(profileUserId)
        try {
            suggestedUsersList = RepositoryManager.socialRepository.getModularRecommendations(limit = 15)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Scaffold(
        containerColor = profileBgColor,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isCurrentUser) onOpenAccountSwitcher() else onScrollToTop()
                            }
                    ) {
                        Text(
                            text = profileUser?.username?.ifBlank { "Profile" } ?: "Profile",
                            fontWeight = FontWeight.ExtraBold,
                            color = profilePrimaryText,
                            fontSize = 20.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val isUserVerified = (profileUser?.isVerified == true || profileUser?.verificationStatus.equals("VERIFIED", ignoreCase = true) || profileUser?.verificationStatus.equals("approved", ignoreCase = true)) &&
                            profileUser?.accountStatus != "SUSPENDED" &&
                            profileUser?.isBanned != true
                        if (isUserVerified) {
                            Spacer(modifier = Modifier.width(5.dp))
                            VerificationBadge(
                                isVerified = true,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (isCurrentUser) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Switch Account",
                                tint = profilePrimaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (!isCurrentUser) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = profilePrimaryText
                            )
                        }
                    }
                },
                actions = {
                    if (isCurrentUser) {
                        val headerCoins = profileUser?.coins ?: 0L
                        if (headerCoins > 0L) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (hasDarkProfileTheme) Color(0xFFFFD700).copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .padding(end = 2.dp)
                                    .testTag("profile_topbar_coin_pill")
                                    .clickable { onNavigate("wallet") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    com.example.ui.components.CoinIcon(size = 13.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = com.example.utils.NumberFormatUtils.formatCount(headerCoins.toInt()),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (hasDarkProfileTheme) Color(0xFFFFD700) else Color(0xFFB45309),
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { showStoryCreator = true }) {
                            Icon(Icons.Default.AddBox, contentDescription = "Create", tint = profilePrimaryText)
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = profilePrimaryText)
                        }
                    } else {
                        IconButton(onClick = { showShareSheet = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Profile", tint = profilePrimaryText)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = profileTopBarBg,
                    titleContentColor = profilePrimaryText
                )
            )
        }
    ) { padding ->
        if (isLoading && profileUser == null) {
            val infiniteTransition = rememberInfiniteTransition(label = "profileShimmer")
            val shimmerTranslate by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "profileShimmerOffset"
            )
            val shimmerBrush = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                start = Offset(shimmerTranslate - 300f, 0f),
                end = Offset(shimmerTranslate, 300f)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(shimmerBrush)
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        repeat(3) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(shimmerBrush)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(shimmerBrush)
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .width(140.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .width(220.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    items(9, key = { "profile_skel_$it" }, contentType = { "profile_skel" }) {
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(shimmerBrush)
                        )
                    }
                }
            }
        } else if (userNotFound && profileUser == null && userPosts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.PersonOff,
                        contentDescription = "User not found",
                        modifier = Modifier.size(52.dp),
                        tint = profileSecondaryText
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "User not found",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = profilePrimaryText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "This profile may have been deleted or doesn't exist.",
                        fontSize = 13.sp,
                        color = profileSecondaryText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!isCurrentUser) {
                            OutlinedButton(onClick = onBack) {
                                Text("Go Back")
                            }
                        }
                        Button(
                            onClick = { profileViewModel.loadProfile(normalizedProfileUserId, isRefresh = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        } else if (profileError != null && profileUser == null && userPosts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = "Error",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Couldn't load profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = profileError ?: "Please check connection and retry",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { profileViewModel.loadProfile(normalizedProfileUserId) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Retry")
                    }
                }
            }
        } else {
            // Instagram-style coordinated container with Pull-to-refresh
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    profileViewModel.refreshProfile(profileUserId)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .nestedScroll(nestedScrollConnection)
                ) {
                    // Profile content categories (memoized so header/tab changes don't re-sort)
                    val allDisplayPosts = remember(userPosts, collabPosts) {
                        (userPosts + collabPosts)
                            .distinctBy { it.id }
                            .filter { !it.isArchived && !it.isDeleted && !it.isRemovedByAdmin && !it.isTrialReel }
                            .sortedByDescending { it.isPinned }
                    }

                    val reelsPosts = remember(allDisplayPosts) {
                        allDisplayPosts.filter { post ->
                            val isVideoType = post.mediaType.equals("video", ignoreCase = true) ||
                                post.mediaType.equals("reel", ignoreCase = true)
                            val mUrl = post.postUrl.ifEmpty { post.postUrls.firstOrNull() ?: "" }
                            isVideoType || (mUrl.isNotEmpty() && !com.example.utils.MediaResolver.isImage(mUrl))
                        }
                    }

                    val tabs = remember(isCurrentUser) {
                        if (isCurrentUser) {
                            listOf(
                                ProfileTabItem(0, Icons.Default.GridOn, "Posts"),
                                ProfileTabItem(1, Icons.Default.Movie, "Reels"),
                                ProfileTabItem(8, Icons.Default.LockOpen, "Unlocked"),
                                ProfileTabItem(2, Icons.Default.AlternateEmail, "Mentions"),
                                ProfileTabItem(3, Icons.Default.Group, "Collabs"),
                                ProfileTabItem(4, Icons.Default.FavoriteBorder, "Liked"),
                                ProfileTabItem(5, Icons.Default.Repeat, "Reposts"),
                                ProfileTabItem(6, Icons.Default.ReportProblem, "Reported"),
                                ProfileTabItem(7, Icons.Default.Science, "Trials")
                            )
                        } else {
                            listOf(
                                ProfileTabItem(0, Icons.Default.GridOn, "Posts"),
                                ProfileTabItem(1, Icons.Default.Movie, "Reels"),
                                ProfileTabItem(2, Icons.Default.AlternateEmail, "Mentions"),
                                ProfileTabItem(3, Icons.Default.Group, "Collabs"),
                                ProfileTabItem(5, Icons.Default.Repeat, "Reposts")
                            )
                        }
                    }

                    val activeTabId = tabs.getOrNull(selectedTabIndex.coerceIn(0, tabs.lastIndex))?.id ?: 0

                    // 1. Grid Container (Translates smoothly in lockstep with collapsing header)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 48.dp) // Room for sticky TabRow (48dp)
                            .offset {
                                IntOffset(0, (headerHeightPx + headerOffset).roundToInt())
                            }
                    ) {
                        when (activeTabId) {
                            0 -> ProfilePostsGrid(
                                posts = allDisplayPosts,
                                gridState = postsGridState,
                                isCurrentUser = isCurrentUser,
                                viewerId = currentUserId,
                                onNavigateToPost = { postId, authorId ->
                                    onNavigateToPostDetail(postId, authorId ?: targetProfileUid.ifEmpty { currentUserId })
                                },
                                onLockedExclusiveClick = { lockedPost ->
                                    selectedExclusivePostToUnlock = lockedPost
                                },
                                onLongClickPost = { selectedPostForOptions = it },
                                isLoadingMore = isLoadingMore,
                                emptyIcon = Icons.Default.CameraAlt,
                                emptyTitle = "No Posts Yet",
                                emptySubtitle = if (isCurrentUser) "Share your first photo or reel to get started" else "This creator hasn't posted yet"
                            )
                            1 -> ProfileReelsGrid(
                                reels = reelsPosts,
                                gridState = reelsGridState,
                                isCurrentUser = isCurrentUser,
                                viewerId = currentUserId,
                                onNavigateToReel = onNavigateToReel,
                                onLockedExclusiveClick = { lockedReel ->
                                    selectedExclusivePostToUnlock = lockedReel
                                },
                                onLongClickPost = { selectedPostForOptions = it }
                            )
                            8 -> ProfileUnlockedExclusiveTab(
                                buyerId = currentUserId,
                                gridState = unlockedGridState,
                                onOpenUnlockedPost = { postId, creatorId ->
                                    onNavigateToPostDetail(postId, creatorId)
                                },
                                onOpenUnlockedReel = { reelId ->
                                    onNavigateToReel(reelId)
                                },
                                onOpenUnlockedStory = { purchase, resolvedMediaUrl ->
                                    unlockedStoryToView = com.example.model.StoryUiModel(
                                        id = purchase.contentId,
                                        username = purchase.creatorUsername.ifBlank { "Creator" },
                                        avatarUrl = purchase.creatorAvatar,
                                        imageUrl = resolvedMediaUrl.ifBlank { purchase.blurredThumbnailUrl },
                                        textOverlay = purchase.teaserCaption,
                                        timeAgo = "Unlocked",
                                        userId = purchase.creatorId,
                                        timestamp = purchase.timestamp,
                                        isExclusiveContent = true,
                                        exclusivePrice = purchase.amount,
                                        teaserCaption = purchase.teaserCaption,
                                        blurredThumbnailUrl = purchase.blurredThumbnailUrl,
                                        hasVaultMedia = true
                                    )
                                }
                            )
                            2 -> ProfilePostsGrid(
                                posts = taggedPosts,
                                gridState = taggedGridState,
                                isCurrentUser = isCurrentUser,
                                viewerId = currentUserId,
                                onNavigateToPost = { postId, authorId ->
                                    onNavigateToPostDetail(postId, authorId ?: targetProfileUid.ifEmpty { currentUserId })
                                },
                                onLockedExclusiveClick = { lockedPost ->
                                    selectedExclusivePostToUnlock = lockedPost
                                },
                                onLongClickPost = {
                                    selectedTaggedPostForOptions = it
                                },
                                isLoadingMore = false,
                                emptyIcon = Icons.Default.AlternateEmail,
                                emptyTitle = "No Mentions Yet",
                                emptySubtitle = if (isCurrentUser) "Photos and reels where you are mentioned will appear here" else "Photos and reels where this creator is mentioned appear here"
                            )
                            3 -> ProfilePostsGrid(
                                posts = collabPosts,
                                gridState = collabsGridState,
                                isCurrentUser = isCurrentUser,
                                viewerId = currentUserId,
                                onNavigateToPost = { postId, authorId ->
                                    onNavigateToPostDetail(postId, authorId ?: targetProfileUid.ifEmpty { currentUserId })
                                },
                                onLockedExclusiveClick = { lockedPost ->
                                    selectedExclusivePostToUnlock = lockedPost
                                },
                                onLongClickPost = { selectedPostForOptions = it },
                                isLoadingMore = false,
                                emptyIcon = Icons.Default.Group,
                                emptyTitle = "No Collaborations",
                                emptySubtitle = "Posts shared with collaborators appear here"
                            )
                            4 -> ProfilePostsGrid(
                                posts = likedPosts,
                                gridState = likedGridState,
                                isCurrentUser = isCurrentUser,
                                viewerId = currentUserId,
                                onNavigateToPost = { postId, authorId ->
                                    onNavigateToPostDetail(postId, authorId ?: targetProfileUid.ifEmpty { currentUserId })
                                },
                                onLockedExclusiveClick = { lockedPost ->
                                    selectedExclusivePostToUnlock = lockedPost
                                },
                                onLongClickPost = { selectedPostForOptions = it },
                                isLoadingMore = false,
                                emptyIcon = Icons.Default.FavoriteBorder,
                                emptyTitle = "No Liked Posts",
                                emptySubtitle = "Posts you like will show up here"
                            )
                            5 -> ProfilePostsGrid(
                                posts = repostedPosts,
                                gridState = repostsGridState,
                                isCurrentUser = isCurrentUser,
                                viewerId = currentUserId,
                                onNavigateToPost = { postId, authorId ->
                                    onNavigateToPostDetail(postId, authorId ?: targetProfileUid.ifEmpty { currentUserId })
                                },
                                onLockedExclusiveClick = { lockedPost ->
                                    selectedExclusivePostToUnlock = lockedPost
                                },
                                onLongClickPost = { selectedPostForOptions = it },
                                isLoadingMore = false,
                                emptyIcon = Icons.Default.Repeat,
                                emptyTitle = "No Reposts",
                                emptySubtitle = "Reposted content will appear here"
                            )
                            6 -> ProfilePostsGrid(
                                posts = reportedPosts,
                                gridState = reportedGridState,
                                isCurrentUser = isCurrentUser,
                                viewerId = currentUserId,
                                onNavigateToPost = { postId, authorId ->
                                    onNavigateToPostDetail(postId, authorId ?: targetProfileUid.ifEmpty { currentUserId })
                                },
                                onLockedExclusiveClick = { lockedPost ->
                                    selectedExclusivePostToUnlock = lockedPost
                                },
                                onLongClickPost = { selectedPostForOptions = it },
                                isLoadingMore = false,
                                emptyIcon = Icons.Default.ReportProblem,
                                emptyTitle = "No Reported Posts",
                                emptySubtitle = "Posts reported by community appear here"
                            )
                            else -> ProfilePostsGrid(
                                posts = userPosts.filter { it.isTrialReel },
                                gridState = trialsGridState,
                                isCurrentUser = isCurrentUser,
                                viewerId = currentUserId,
                                onNavigateToPost = { postId, authorId ->
                                    onNavigateToPostDetail(postId, authorId ?: targetProfileUid.ifEmpty { currentUserId })
                                },
                                onLockedExclusiveClick = { lockedPost ->
                                    selectedExclusivePostToUnlock = lockedPost
                                },
                                onLongClickPost = { selectedPostForOptions = it },
                                isLoadingMore = false,
                                emptyIcon = Icons.Default.Science,
                                emptyTitle = "No Trial Reels",
                                emptySubtitle = "Trial reels in testing appear here"
                            )
                        }
                    }

                    // 2. Collapsible Header + Sticky TabRow (zIndex 10 to stay strictly above grid items)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, headerOffset.roundToInt()) }
                            .zIndex(10f)
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    if (headerHeightPx > 0f) {
                                        val oldOffset = headerOffset
                                        val newOffset = (oldOffset + delta).coerceIn(-headerHeightPx, 0f)
                                        headerOffset = newOffset
                                    }
                                }
                            )
                    ) {
                        // Profile Header Content Area (Measured dynamically for smooth collapse)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (equippedProfileThemeBrush != null) {
                                        Modifier.background(equippedProfileThemeBrush)
                                    } else {
                                        Modifier.background(MaterialTheme.colorScheme.background)
                                    }
                                )
                                .onGloballyPositioned { coordinates ->
                                    headerHeightPx = coordinates.size.height.toFloat()
                                }
                        ) {
                            // Avatar + Stats
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 86.dp Circular Avatar with Live / Story Ring, optional Animated Cosmetics Frame & "+" badge
                                val hasActiveProfileStories = profileUserStories.isNotEmpty()
                                val isProfileLiveNow = profileLiveSession != null
                                Box(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .height(106.dp)
                                        .clickable {
                                            when {
                                                isProfileLiveNow && profileLiveSession != null -> {
                                                    try {
                                                        val intent = android.content.Intent(context, com.example.ui.LiveActivity::class.java).apply {
                                                            putExtra("liveID", profileLiveSession.storyId)
                                                            putExtra("userID", activeAccountUid)
                                                            putExtra("userName", RepositoryManager.currentUserState.value?.username?.ifBlank { "Viewer" } ?: "Viewer")
                                                            putExtra("isHost", targetProfileUid == activeAccountUid)
                                                            putExtra("hostUid", targetProfileUid)
                                                            putExtra("hostUsername", profileUser?.username ?: profileLiveSession.username)
                                                            putExtra("hostAvatar", profileUser?.avatarUrl ?: profileLiveSession.avatarUrl)
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "Unable to open live stream", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                hasActiveProfileStories -> {
                                                    com.example.utils.GlobalMediaSessionManager.requestFocus(
                                                        com.example.utils.MediaSource.STORY,
                                                        context
                                                    )
                                                    showProfileStoryViewer = true
                                                }
                                                isCurrentUser -> {
                                                    showStoryCreator = true
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier.size(92.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val ringBrush: androidx.compose.ui.graphics.Brush? = when {
                                            isProfileLiveNow -> androidx.compose.ui.graphics.Brush.sweepGradient(
                                                listOf(Color(0xFFFF1744), Color(0xFFFF2A6D), Color(0xFFFF9100), Color(0xFFFF1744))
                                            )
                                            hasActiveProfileStories -> androidx.compose.ui.graphics.Brush.linearGradient(
                                                listOf(Color(0xFFFCAF45), Color(0xFFE1306C), Color(0xFF833AB4))
                                            )
                                            else -> null
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(90.dp)
                                                .then(
                                                    if (ringBrush != null) {
                                                        Modifier
                                                            .clip(CircleShape)
                                                            .border(3.dp, ringBrush, CircleShape)
                                                            .padding(4.dp)
                                                    } else Modifier
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (profileCosmetics.equippedFrameId != "frame_none" && ringBrush == null) {
                                                CosmeticAvatarFrameRing(
                                                    frameId = profileCosmetics.equippedFrameId,
                                                    avatarUrl = profileUser?.avatarUrl ?: "",
                                                    size = 78.dp,
                                                    contentDescription = "Profile Picture"
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(82.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val avatarUrlVal = profileUser?.avatarUrl
                                                    if (!avatarUrlVal.isNullOrEmpty()) {
                                                        AsyncImage(
                                                            model = ImageRequest.Builder(context)
                                                                .data(avatarUrlVal)
                                                                .crossfade(true)
                                                                .diskCachePolicy(CachePolicy.ENABLED)
                                                                .memoryCachePolicy(CachePolicy.ENABLED)
                                                                .build(),
                                                            contentDescription = "Profile Picture",
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .clip(CircleShape),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    } else {
                                                        Icon(
                                                            Icons.Default.Person,
                                                            contentDescription = "Profile",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(40.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (isProfileLiveNow) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFFFF1744))
                                                    .border(1.5.dp, MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "LIVE",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    letterSpacing = 0.8.sp
                                                )
                                            }
                                        } else if (isCurrentUser) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF0095F6))
                                                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                                    .clickable { showStoryCreator = true },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Add Story",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (isCurrentUser) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shadowElevation = 3.dp,
                                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                                            modifier = Modifier.align(Alignment.TopCenter)
                                        ) {
                                            Text(
                                                text = "Note...",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(24.dp))

                                // Stats
                                val targetUid = profileUserId ?: currentUserId
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    ProfileScreenStat(
                                        count = com.example.utils.NumberFormatUtils.formatCount(userPosts.size),
                                        label = "Posts",
                                        textColor = profilePrimaryText,
                                        labelColor = profileSecondaryText,
                                        onClick = onScrollToTop
                                    )
                                    ProfileScreenStat(
                                        count = com.example.utils.NumberFormatUtils.formatCount(profileUser?.followerCount ?: 0),
                                        label = "Followers",
                                        textColor = profilePrimaryText,
                                        labelColor = profileSecondaryText,
                                        onClick = { if (targetUid.isNotEmpty()) onNavigateToFollows(targetUid, "followers") }
                                    )
                                    ProfileScreenStat(
                                        count = com.example.utils.NumberFormatUtils.formatCount(profileUser?.followingCount ?: 0),
                                        label = "Following",
                                        textColor = profilePrimaryText,
                                        labelColor = profileSecondaryText,
                                        onClick = { if (targetUid.isNotEmpty()) onNavigateToFollows(targetUid, "following") }
                                    )
                                }
                            }

                            // Bio Section
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                val user = profileUser
                                val isUserVerified = (user?.isVerified == true || user?.verificationStatus.equals("VERIFIED", ignoreCase = true) || user?.verificationStatus.equals("approved", ignoreCase = true)) &&
                                    user?.accountStatus != "SUSPENDED" &&
                                    user?.isBanned != true

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val uName = user?.username ?: ""
                                    val dispName = user?.displayName?.ifEmpty { uName } ?: uName
                                    Text(
                                        text = dispName,
                                        fontWeight = FontWeight.Bold,
                                        color = profilePrimaryText,
                                        fontSize = 15.sp
                                    )
                                    if (isUserVerified) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        VerificationBadge(
                                            isVerified = true,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    val pronounsText = user?.pronouns
                                    if (!pronounsText.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = pronounsText,
                                            color = profileSecondaryText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                if (!user?.displayName.isNullOrEmpty() && user?.displayName != user?.username) {
                                    Text(
                                        text = "@${user?.username ?: ""}",
                                        color = profileSecondaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                val profileCategory = user?.category
                                if (!profileCategory.isNullOrEmpty()) {
                                    Text(
                                        text = profileCategory,
                                        color = profileSecondaryText,
                                        fontSize = 13.sp
                                    )
                                }

                                if (user?.accountType == "BUSINESS" || user?.businessName?.isNotEmpty() == true) {
                                    val bizCategory = user.businessCategory
                                    if (bizCategory.isNotEmpty() && bizCategory != user.category) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(bizCategory, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                            leadingIcon = { Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    if (user.businessAddress.isNotEmpty() || user.businessCity.isNotEmpty()) {
                                        val loc = listOf(user.businessAddress, user.businessCity, user.businessState).filter { it.isNotEmpty() }.joinToString(", ")
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(loc, fontSize = 12.sp, color = profileSecondaryText)
                                        }
                                    }
                                    val bizHours = user.businessHours
                                    if (bizHours.isNotEmpty()) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                            Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(bizHours, fontSize = 12.sp, color = profileSecondaryText)
                                        }
                                    }
                                }

                                val bioText = user?.bio
                                if (!bioText.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = bioText,
                                        color = profilePrimaryText,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }

                                // External Links Row (Supports Website + Custom Links)
                                val primaryWebsite = profileUser?.websiteUrl?.takeIf { it.isNotBlank() } ?: profileUser?.businessWebsite?.takeIf { it.isNotBlank() }
                                val extraLinksCount = profileUser?.customLinks?.size ?: 0
                                if (primaryWebsite != null || extraLinksCount > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            val urlToOpen = primaryWebsite ?: profileUser?.customLinks?.values?.firstOrNull() ?: ""
                                            if (urlToOpen.isNotBlank()) {
                                                try {
                                                    val finalUrl = if (urlToOpen.startsWith("http")) urlToOpen else "https://$urlToOpen"
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)))
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Link,
                                            contentDescription = "External Link",
                                            tint = Color(0xFF0095F6),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = (primaryWebsite ?: "links.socialindia.app") + if (extraLinksCount > 0) " and $extraLinksCount more" else "",
                                            color = Color(0xFF0095F6),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Pinned Profile Music Player Bar (Real Music Picker like Notes + Animated Equalizer)
                                var isPlayingProfileSong by remember { mutableStateOf(false) }
                                var showProfileMusicPicker by remember { mutableStateOf(false) }
                                var localSongTitle by remember(profileUser?.profileMusicTitle) {
                                    mutableStateOf(profileUser?.profileMusicTitle ?: "")
                                }
                                var localSongArtist by remember(profileUser?.profileMusicArtist) {
                                    mutableStateOf(profileUser?.profileMusicArtist ?: "")
                                }
                                var localSongUrl by remember(profileUser?.profileMusicUrl) {
                                    mutableStateOf(profileUser?.profileMusicUrl ?: "")
                                }
                                val hasProfileSong = localSongTitle.isNotBlank() && localSongUrl.isNotBlank()

                                DisposableEffect(Unit) {
                                    onDispose {
                                        if (isPlayingProfileSong) {
                                            GlobalAudioPreviewManager.stop()
                                        }
                                    }
                                }

                                if (hasProfileSong || isCurrentUser) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isPlayingProfileSong) {
                                            Color(0xFF0095F6).copy(alpha = 0.2f)
                                        } else {
                                            profileCardBg
                                        },
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 1.dp,
                                            color = if (isPlayingProfileSong) {
                                                Color(0xFF0095F6).copy(alpha = 0.7f)
                                            } else {
                                                profileCardBorder
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("profile_music_bar")
                                            .clickable {
                                                if (hasProfileSong) {
                                                    isPlayingProfileSong = !isPlayingProfileSong
                                                    if (isPlayingProfileSong) {
                                                        GlobalAudioPreviewManager.playOrToggle(context, localSongUrl)
                                                    } else {
                                                        GlobalAudioPreviewManager.stop()
                                                    }
                                                } else if (isCurrentUser) {
                                                    showProfileMusicPicker = true
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                                            listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCAF45))
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = when {
                                                        !hasProfileSong -> Icons.Default.MusicNote
                                                        isPlayingProfileSong -> Icons.Default.Pause
                                                        else -> Icons.Default.PlayArrow
                                                    },
                                                    contentDescription = "Profile Music",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (hasProfileSong) localSongTitle else "Add Profile Music",
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = profilePrimaryText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (hasProfileSong) {
                                                        localSongArtist.ifBlank { "Selected Song" }
                                                    } else {
                                                        "Pick any real song like Notes 🎵"
                                                    },
                                                    fontSize = 11.sp,
                                                    color = profileSecondaryText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (hasProfileSong) {
                                                AnimatedMusicEqualizer(
                                                    isPlaying = isPlayingProfileSong,
                                                    barColor = Color(0xFF0095F6),
                                                    modifier = Modifier.padding(horizontal = 6.dp)
                                                )
                                            }
                                            if (isCurrentUser) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFF0095F6).copy(alpha = 0.18f),
                                                    modifier = Modifier
                                                        .testTag("change_profile_music_button")
                                                        .clickable {
                                                            if (isPlayingProfileSong) {
                                                                GlobalAudioPreviewManager.stop()
                                                                isPlayingProfileSong = false
                                                            }
                                                            showProfileMusicPicker = true
                                                        }
                                                ) {
                                                    Text(
                                                        text = if (hasProfileSong) "Change" else "Select",
                                                        color = Color(0xFF38BDF8),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (showProfileMusicPicker) {
                                    MusicPickerSheet(
                                        onDismiss = { showProfileMusicPicker = false },
                                        onTrackSelected = { track ->
                                            val chosenTitle = track.name.orEmpty()
                                            val chosenArtist = track.artistName.orEmpty()
                                            val chosenUrl = track.previewUrl.orEmpty()
                                            localSongTitle = chosenTitle
                                            localSongArtist = chosenArtist
                                            localSongUrl = chosenUrl
                                            showProfileMusicPicker = false
                                            if (currentUserId.isNotEmpty()) {
                                                scope.launch {
                                                    try {
                                                        RepositoryManager.firestore.collection("users")
                                                            .document(currentUserId)
                                                            .update(
                                                                mapOf(
                                                                    "profileMusicTitle" to chosenTitle,
                                                                    "profileMusicArtist" to chosenArtist,
                                                                    "profileMusicUrl" to chosenUrl
                                                                )
                                                            )
                                                        Toast.makeText(context, "Profile song set: $chosenTitle 🎵", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Failed to save music", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Equipped VIP Entrance Badge + Pro Creator Suite Hub Strip
                            if (profileCosmetics.equippedEntranceId != "entrance_none") {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    VipEntranceBadgePill(entranceId = profileCosmetics.equippedEntranceId)
                                }
                            }
                            androidx.compose.foundation.lazy.LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val profileTools = if (isCurrentUser) {
                                    listOf(
                                        Triple("✨ Frames & Themes", "cosmetics_store", Color(0xFF9C27B0)),
                                        Triple("🤝 Brand Collabs", "brand_collabs", Color(0xFF10B981)),
                                        Triple("🏆 Leaderboard", "leaderboard", Color(0xFFFFB300)),
                                        Triple("🎁 Spin & Lucky Box", "daily_rewards", Color(0xFFFF2A6D)),
                                        Triple("🔥 Hot Templates", "templates", Color(0xFFEE1D52))
                                    )
                                } else {
                                    listOf(
                                        Triple("🤝 Book Shoutout", "brand_collabs", Color(0xFF10B981)),
                                        Triple("🎁 Lucky Boxes", "daily_rewards", Color(0xFFFF2A6D)),
                                        Triple("🏆 Leaderboard", "leaderboard", Color(0xFFFFB300))
                                    )
                                }
                                items(profileTools.size) { idx ->
                                    val (lbl, route, clr) = profileTools[idx]
                                    Surface(
                                        onClick = { onNavigate(route) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (hasDarkProfileTheme) clr.copy(alpha = 0.2f) else clr.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, clr.copy(alpha = 0.45f))
                                    ) {
                                        Text(
                                            text = lbl,
                                            color = profilePrimaryText,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            var showQrProfileDialog by remember { mutableStateOf(false) }
                            var showCreatorInsightsExpanded by remember { mutableStateOf(false) }

                            // Professional Creator Dashboard Card (Reach, Impressions, Engagement, Real Monetization Status)
                            if (isCurrentUser) {
                                val isProfileMonetized = profileUser?.isMonetized == true ||
                                    profileUser?.monetizationStatus.equals("ACTIVE", ignoreCase = true) ||
                                    profileUser?.monetizationStatus.equals("APPROVED", ignoreCase = true)
                                val isProfileMonPending = profileUser?.monetizationRequested == true ||
                                    profileUser?.monetizationStatus.equals("PENDING", ignoreCase = true) ||
                                    profileUser?.monetizationStatus.equals("Under Review", ignoreCase = true)
                                val reqMonFollowers = profileMonetizationSettings.minFollowersForMonetization.coerceAtLeast(1)
                                val currentFollowersVal = profileUser?.followerCount ?: 0

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = profileCardBg,
                                    border = BorderStroke(1.dp, profileCardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .clickable { showCreatorInsightsExpanded = !showCreatorInsightsExpanded }
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Professional dashboard",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.5.sp,
                                                    color = profilePrimaryText
                                                )
                                                val totalReach = userPosts.sumOf { it.viewsCount }
                                                Text(
                                                    text = "${com.example.utils.NumberFormatUtils.formatCount(totalReach)} accounts reached in the last 30 days",
                                                    fontSize = 11.5.sp,
                                                    color = profileSecondaryText
                                                )
                                            }
                                            Icon(
                                                imageVector = if (showCreatorInsightsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.ChevronRight,
                                                contentDescription = "Insights",
                                                tint = profileSecondaryText,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        androidx.compose.animation.AnimatedVisibility(visible = showCreatorInsightsExpanded) {
                                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                                HorizontalDivider(color = profilePrimaryText.copy(alpha = 0.1f))
                                                Spacer(modifier = Modifier.height(8.dp))
                                                val reachVal = userPosts.sumOf { it.viewsCount }
                                                val engagementVal = userPosts.sumOf { it.likesCount + it.commentsCount + it.sharesCount + it.savesCount }
                                                val impressionsVal = reachVal + engagementVal
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(com.example.utils.NumberFormatUtils.formatCount(reachVal), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = profilePrimaryText)
                                                        Text("Reach", fontSize = 11.sp, color = profileSecondaryText)
                                                    }
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(com.example.utils.NumberFormatUtils.formatCount(impressionsVal), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = profilePrimaryText)
                                                        Text("Impressions", fontSize = 11.sp, color = profileSecondaryText)
                                                    }
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(com.example.utils.NumberFormatUtils.formatCount(engagementVal), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = profilePrimaryText)
                                                        Text("Engagement", fontSize = 11.sp, color = profileSecondaryText)
                                                    }
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        val (mLabel, mColor) = when {
                                                            isProfileMonetized -> "Active ✓" to Color(0xFF10B981)
                                                            isProfileMonPending -> "Review" to Color(0xFFFF9800)
                                                            currentFollowersVal >= reqMonFollowers -> "Apply" to Color(0xFF38BDF8)
                                                            else -> "Locked" to Color(0xFFFF5252)
                                                        }
                                                        Text(mLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = mColor)
                                                        Text("Monetization", fontSize = 11.sp, color = profileSecondaryText)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedButton(
                                                        onClick = { onNavigate("monetization") },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(36.dp)
                                                            .testTag("profile_monetization_button"),
                                                        shape = RoundedCornerShape(8.dp),
                                                        border = BorderStroke(1.dp, profileCardBorder),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) {
                                                        Text(
                                                            if (isProfileMonetized) "Monetization ₹" else "Monetization",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = profilePrimaryText,
                                                            maxLines = 1
                                                        )
                                                    }
                                                    OutlinedButton(
                                                        onClick = { onNavigate("wallet") },
                                                        modifier = Modifier
                                                            .weight(0.85f)
                                                            .height(36.dp)
                                                            .testTag("profile_wallet_button"),
                                                        shape = RoundedCornerShape(8.dp),
                                                        border = BorderStroke(1.dp, profileCardBorder),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) {
                                                        Text("Wallet", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = profilePrimaryText, maxLines = 1)
                                                    }
                                                    OutlinedButton(
                                                        onClick = { onNavigate("professional_dashboard") },
                                                        modifier = Modifier
                                                            .weight(0.85f)
                                                            .height(36.dp)
                                                            .testTag("profile_insights_button"),
                                                        shape = RoundedCornerShape(8.dp),
                                                        border = BorderStroke(1.dp, profileCardBorder),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) {
                                                        Text("Insights", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = profilePrimaryText, maxLines = 1)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Monetization-Gated Creator Studio / Earnings Strip
                                val liveWallet = (profileUser?.walletBalance ?: 0.0).toInt()
                                val liveCoins = profileUser?.coins ?: 0L
                                if (isProfileMonetized) {
                                    val balanceLabel = buildString {
                                        append("Creator Earnings • ₹${com.example.utils.NumberFormatUtils.formatCount(liveWallet)}")
                                        if (liveCoins > 0L) {
                                            append("  •  🪙 ${com.example.utils.NumberFormatUtils.formatCount(liveCoins.toInt())}")
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.Transparent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    listOf(Color(0xFF0F766E), Color(0xFF1D4ED8), Color(0xFF7C3AED))
                                                )
                                            )
                                            .testTag("profile_earn_money_banner")
                                            .clickable { onNavigate("wallet") }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(
                                                    imageVector = Icons.Default.AccountBalanceWallet,
                                                    contentDescription = "Creator Earnings",
                                                    tint = Color(0xFFFFD700),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = balanceLabel,
                                                        color = Color.White,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "Monetized Creator • Video RPM, Gifts & UPI Payouts",
                                                        color = Color.White.copy(alpha = 0.85f),
                                                        fontSize = 10.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.White.copy(alpha = 0.22f)
                                            ) {
                                                Text(
                                                    text = "Wallet →",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = profileCardBg,
                                        border = BorderStroke(
                                            1.dp,
                                            if (currentFollowersVal >= reqMonFollowers) Color(0xFF10B981).copy(alpha = 0.55f)
                                            else profileCardBorder
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .testTag("profile_monetization_gate_banner")
                                            .clickable { onNavigate("monetization") }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(
                                                    imageVector = if (isProfileMonPending) Icons.Default.HourglassTop else Icons.Default.Lock,
                                                    contentDescription = "Monetization Status",
                                                    tint = if (currentFollowersVal >= reqMonFollowers) Color(0xFF10B981) else Color(0xFFFFB300),
                                                    modifier = Modifier.size(19.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = when {
                                                            isProfileMonPending -> "Monetization Under Review"
                                                            currentFollowersVal >= reqMonFollowers -> "Eligible for Creator Monetization"
                                                            else -> "Monetization Locked • $currentFollowersVal/$reqMonFollowers Followers"
                                                        },
                                                        color = profilePrimaryText,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = if (currentFollowersVal >= reqMonFollowers)
                                                            "Apply now to unlock video earnings & creator payouts"
                                                        else
                                                            "Reach $reqMonFollowers followers to unlock Video Earnings Wallet",
                                                        color = profileSecondaryText,
                                                        fontSize = 10.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (currentFollowersVal >= reqMonFollowers) Color(0xFF10B981) else Color(0xFF0095F6).copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = if (isProfileMonPending) "Status" else if (currentFollowersVal >= reqMonFollowers) "Apply" else "View",
                                                    color = if (currentFollowersVal >= reqMonFollowers) Color.White else Color(0xFF38BDF8),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            } else if (targetProfileUid.isNotEmpty()) {
                                var showProfileGiftSheet by remember(targetProfileUid) { mutableStateOf(false) }
                                var profileGiftBurst by remember(targetProfileUid) { mutableStateOf<ContentGiftBurstEvent?>(null) }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFFB300).copy(alpha = 0.14f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .clickable { showProfileGiftSheet = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CardGiftcard,
                                            contentDescription = "Gift Creator",
                                            tint = Color(0xFFFF8F00),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Send Super Gift & Support @${profileUser?.username?.ifBlank { "Creator" } ?: "Creator"} 🎁",
                                            color = Color(0xFFFF8F00),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                                if (showProfileGiftSheet) {
                                    CreatorGiftBottomSheet(
                                        creatorId = targetProfileUid,
                                        creatorUsername = profileUser?.username?.ifBlank { "Creator" } ?: "Creator",
                                        contentId = targetProfileUid,
                                        contentType = "POST",
                                        onDismiss = { showProfileGiftSheet = false },
                                        onGiftSent = { ev -> profileGiftBurst = ev }
                                    )
                                }
                                profileGiftBurst?.let { ev ->
                                    androidx.compose.ui.window.Dialog(
                                        onDismissRequest = { profileGiftBurst = null },
                                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                                    ) {
                                        AnimatedGiftBurstOverlay(
                                            event = ev,
                                            onFinished = { profileGiftBurst = null },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // Action Buttons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isCurrentUser) {
                                    Button(
                                        onClick = onNavigateToEditProfile,
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = profileCardBg),
                                        border = BorderStroke(1.dp, profileCardBorder),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            "Edit profile",
                                            color = profilePrimaryText,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Button(
                                        onClick = { showQrProfileDialog = true },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = profileCardBg),
                                        border = BorderStroke(1.dp, profileCardBorder),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            "Share profile",
                                            color = profilePrimaryText,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    FilledTonalIconButton(
                                        onClick = { showSuggestedPeople = !showSuggestedPeople },
                                        modifier = Modifier.size(36.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = if (showSuggestedPeople) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else profileCardBg
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.PersonAdd,
                                            contentDescription = "Discover People",
                                            tint = if (showSuggestedPeople) MaterialTheme.colorScheme.primary else profilePrimaryText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    val targetUserObj = profileUser ?: com.example.model.User(id = profileUserId ?: "")
                                    val hasActiveSubscriptionPlan = creatorPlan != null && creatorPlan?.status.equals("ACTIVE", ignoreCase = true) && (creatorPlan?.price ?: 0.0) > 0.0

                                    if (hasActiveSubscriptionPlan && !isSubscribedToCreator) {
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = android.content.Intent(context, com.example.ui.SubscriptionActivity::class.java).apply {
                                                        putExtra("creatorId", targetUserObj.id)
                                                        putExtra("creatorName", targetUserObj.username.ifEmpty { "Creator" })
                                                        putExtra("amount", String.format(java.util.Locale.US, "%.2f", creatorPlan?.price ?: 49.0))
                                                        putExtra("purpose", "CREATOR_EXCLUSIVE")
                                                    }
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {
                                                    Toast.makeText(context, "Unable to open subscription checkout", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.weight(0.9f).height(38.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Loyalty, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "Subscribe",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    } else if (hasActiveSubscriptionPlan && isSubscribedToCreator) {
                                        Surface(
                                            modifier = Modifier.weight(0.9f).height(38.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFE8F5E9),
                                            border = BorderStroke(1.dp, Color(0xFF4CAF50))
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Subscribed", color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    SocialFollowButton(
                                        targetUser = targetUserObj,
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    )
                                    Button(
                                        onClick = {
                                            profileUser?.id?.let { uid -> onNavigateToChat(uid) }
                                        },
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            "Message",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    var showProfileSuperTipSheet by remember { mutableStateOf(false) }
                                    var profileTipConfettiAmt by remember { mutableStateOf<Int?>(null) }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFFFD700).copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, Color(0xFFFFD700)),
                                        modifier = Modifier
                                            .height(38.dp)
                                            .clickable {
                                                triggerCrispMicroHaptic(context, strong = true)
                                                showProfileSuperTipSheet = true
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text("💎 Tip", color = Color(0xFFFFD700), fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                                        }
                                    }

                                    if (showProfileSuperTipSheet) {
                                        CreatorSuperTipSheet(
                                            creatorId = profileUser?.id ?: profileUserId ?: "",
                                            creatorName = profileUser?.username?.ifBlank { "Creator" } ?: "Creator",
                                            creatorAvatar = profileUser?.avatarUrl ?: "",
                                            postId = "",
                                            onDismiss = { showProfileSuperTipSheet = false },
                                            onTipSuccess = { amt ->
                                                showProfileSuperTipSheet = false
                                                profileTipConfettiAmt = amt
                                            }
                                        )
                                    }

                                    profileTipConfettiAmt?.let { tipAmt ->
                                        androidx.compose.ui.window.Dialog(
                                            onDismissRequest = { profileTipConfettiAmt = null },
                                            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                                        ) {
                                            SuperTipConfettiOverlay(
                                                visible = true,
                                                amountInr = tipAmt,
                                                creatorName = profileUser?.username?.ifBlank { "Creator" } ?: "Creator",
                                                onFinished = { profileTipConfettiAmt = null }
                                            )
                                        }
                                    }
                                    FilledTonalIconButton(
                                        onClick = {
                                            val targetUid = profileUser?.id ?: profileUserId ?: ""
                                            val targetName = profileUser?.username ?: "User"
                                            val targetPhoto = profileUser?.avatarUrl ?: ""
                                            if (targetUid.isNotEmpty()) {
                                                com.example.zego.SocialIndiaZegoManager.startVoiceCall(context, targetUid, targetName, targetPhoto)
                                            }
                                        },
                                        modifier = Modifier.size(38.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Call,
                                            contentDescription = "Voice Call",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                    FilledTonalIconButton(
                                        onClick = {
                                            val targetUid = profileUser?.id ?: profileUserId ?: ""
                                            val targetName = profileUser?.username ?: "User"
                                            val targetPhoto = profileUser?.avatarUrl ?: ""
                                            if (targetUid.isNotEmpty()) {
                                                com.example.zego.SocialIndiaZegoManager.startVideoCall(context, targetUid, targetName, targetPhoto)
                                            }
                                        },
                                        modifier = Modifier.size(38.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Videocam,
                                            contentDescription = "Video Call",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                    FilledTonalIconButton(
                                        onClick = { showSuggestedPeople = !showSuggestedPeople },
                                        modifier = Modifier.size(38.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = if (showSuggestedPeople) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.PersonAdd,
                                            contentDescription = "Suggested People",
                                            tint = if (showSuggestedPeople) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Instagram-style QR Share Profile Dialog
                            if (showQrProfileDialog) {
                                val qrUsername = profileUser?.username?.ifEmpty { "social_india" } ?: "social_india"
                                val profileDeepLink = "https://socialindia.app/u/$qrUsername"
                                androidx.compose.ui.window.Dialog(onDismissRequest = { showQrProfileDialog = false }) {
                                    Card(
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(200.dp)
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(
                                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                                            listOf(Color(0xFFF09433), Color(0xFFDC2743), Color(0xFFBC1888))
                                                        )
                                                    )
                                                    .padding(14.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = Color.White,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            Icons.Default.QrCode2,
                                                            contentDescription = "Profile QR Code",
                                                            tint = Color(0xFFDC2743),
                                                            modifier = Modifier.size(140.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Text(
                                                text = "@${qrUsername.uppercase()}",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 18.sp,
                                                color = Color(0xFFDC2743)
                                            )
                                            Spacer(modifier = Modifier.height(18.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Profile Link", profileDeepLink))
                                                        Toast.makeText(context, "Profile link copied!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Copy link", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Button(
                                                    onClick = {
                                                        showQrProfileDialog = false
                                                        showShareSheet = true
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0095F6)),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Share", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (isCurrentUser && (profileUser?.accountType == "BUSINESS" || profileUser?.isProfessionalAccount == true)) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onNavigateToBusinessDashboard,
                                        modifier = Modifier.weight(1.2f).height(38.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Outlined.Dashboard, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Business Dashboard", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { onNavigateToCreateAd(null) },
                                        modifier = Modifier.weight(0.8f).height(38.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Outlined.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Create Ad", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            } else if (!isCurrentUser && profileUser?.accountType == "BUSINESS" && profileUser?.businessActionButton?.isNotEmpty() == true) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        val url = profileUser?.businessActionUrl.orEmpty()
                                        if (url.isNotEmpty()) {
                                            try {
                                                val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("tel:") && !url.startsWith("mailto:")) "https://$url" else url
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not open action", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(profileUser?.businessActionButton ?: "Contact Us", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            // Inline Discover People Carousel
                            if (showSuggestedPeople || (isCurrentUser && (profileUser?.followingCount ?: 0) < 3 && suggestedUsersList.isNotEmpty())) {
                                Spacer(modifier = Modifier.height(10.dp))
                                QuickFollowHorizontalSection(
                                    suggestedUsers = suggestedUsersList,
                                    onNavigateToProfile = { uid ->
                                        if (uid.isNotBlank()) onNavigate("profile/${android.net.Uri.encode(uid.trim())}")
                                    },
                                    onSeeAllClick = onNavigateToDiscoverPeople,
                                    onDismissUser = { dismissedUid ->
                                        scope.launch {
                                            RepositoryManager.socialRepository.dismissSuggestion(dismissedUid)
                                            suggestedUsersList = suggestedUsersList.filter { it.user.id != dismissedUid }
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Creator Broadcast Channels Section
                            if (creatorChannels.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Broadcast Channels",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = "View all",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clickable { onNavigateToChannels() }
                                        )
                                    }

                                    creatorChannels.forEach { channel ->
                                        BroadcastChannelProfileEntry(
                                            channel = channel,
                                            currentUserId = currentUserId,
                                            isFollowing = isFollowingCreator,
                                            onFollow = {
                                                scope.launch {
                                                    RepositoryManager.socialRepository.followUser(currentUserId, targetProfileUid)
                                                    Toast.makeText(context, "Now following @${channel.creatorUsername}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onJoin = {
                                                scope.launch {
                                                    val success = FlagshipFeaturesRepository.joinChannel(channel.id, currentUserId)
                                                    if (success) {
                                                        Toast.makeText(context, "Joined ${channel.title}!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Follow creator to join", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            onOpen = onNavigateToChannels
                                        )
                                    }
                                }
                            } else if (isCurrentUser) {
                                // Creator Broadcast Channels Shortcut for owner
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .clickable { onNavigateToChannels() },
                                    shape = RoundedCornerShape(12.dp),
                                    color = profileCardBg,
                                    border = BorderStroke(1.dp, profileCardBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFE2C55).copy(alpha = 0.18f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Campaign,
                                                contentDescription = "Broadcast Channels",
                                                tint = Color(0xFFFE2C55),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Create Broadcast Channel",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = profilePrimaryText
                                            )
                                            Text(
                                                text = "Send updates, run polls & connect with audience",
                                                fontSize = 11.sp,
                                                color = profileSecondaryText
                                            )
                                        }
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = profileSecondaryText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Story Highlights Tray
                            StoryHighlightsTray(
                                userId = targetProfileUid,
                                isCurrentUser = isCurrentUser,
                                onHighlightClick = { highlight ->
                                    selectedHighlight = highlight
                                    showHighlightViewer = true
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Sticky TabRow (Always sticks right beneath TopAppBar when header is collapsed)
                        if (tabs.size > 6) {
                            ScrollableTabRow(
                                selectedTabIndex = selectedTabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
                                edgePadding = 4.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                containerColor = profileBgColor,
                                contentColor = profilePrimaryText,
                                indicator = { tabPositions ->
                                    if (tabPositions.isNotEmpty()) {
                                        val safeIndex = selectedTabIndex.coerceIn(0, tabPositions.lastIndex)
                                        val selTabId = tabs.getOrNull(safeIndex)?.id
                                        TabRowDefaults.SecondaryIndicator(
                                            Modifier.tabIndicatorOffset(tabPositions[safeIndex]),
                                            color = if (selTabId == 8) ExclusiveGoldMid else if (hasDarkProfileTheme) Color.White else MaterialTheme.colorScheme.primary,
                                            height = 2.dp
                                        )
                                    }
                                },
                                divider = {
                                    HorizontalDivider(
                                        color = profilePrimaryText.copy(alpha = 0.12f),
                                        thickness = 0.5.dp
                                    )
                                }
                            ) {
                                tabs.forEachIndexed { index, tab ->
                                    val isSelected = selectedTabIndex == index
                                    Tab(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) {
                                                onScrollToTop()
                                            } else {
                                                selectedTabIndex = index
                                            }
                                        },
                                        modifier = Modifier.testTag("profile_tab_${tab.label.lowercase()}"),
                                        icon = {
                                            Icon(
                                                tab.icon,
                                                contentDescription = tab.label,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        },
                                        selectedContentColor = when (tab.id) {
                                            8 -> ExclusiveGoldMid
                                            6 -> Color(0xFFFF9800)
                                            else -> if (hasDarkProfileTheme) Color.White else MaterialTheme.colorScheme.primary
                                        },
                                        unselectedContentColor = if (tab.id == 8) ExclusiveGoldMid.copy(alpha = 0.55f) else profilePrimaryText.copy(alpha = 0.45f)
                                    )
                                }
                            }
                        } else if (tabs.isNotEmpty()) {
                            TabRow(
                                selectedTabIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                containerColor = profileBgColor,
                                contentColor = profilePrimaryText,
                                indicator = { tabPositions ->
                                    if (tabPositions.isNotEmpty()) {
                                        val safeIndex = selectedTabIndex.coerceIn(0, tabPositions.lastIndex)
                                        TabRowDefaults.SecondaryIndicator(
                                            Modifier.tabIndicatorOffset(tabPositions[safeIndex]),
                                            color = if (hasDarkProfileTheme) Color.White else MaterialTheme.colorScheme.primary,
                                            height = 2.dp
                                        )
                                    }
                                },
                                divider = {
                                    HorizontalDivider(
                                        color = profilePrimaryText.copy(alpha = 0.12f),
                                        thickness = 0.5.dp
                                    )
                                }
                            ) {
                                tabs.forEachIndexed { index, tab ->
                                    val isSelected = selectedTabIndex == index
                                    Tab(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) {
                                                onScrollToTop()
                                            } else {
                                                selectedTabIndex = index
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                tab.icon,
                                                contentDescription = tab.label,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        },
                                        selectedContentColor = if (tab.id == 6) Color(0xFFFF9800) else if (hasDarkProfileTheme) Color.White else MaterialTheme.colorScheme.primary,
                                        unselectedContentColor = profilePrimaryText.copy(alpha = 0.45f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tagged/Mentioned Post Options Dialog ("Mentions Utha Sake")
        selectedTaggedPostForOptions?.let { post ->
            val isMyPost = post.userId == currentUserId

            AlertDialog(
                onDismissRequest = { selectedTaggedPostForOptions = null },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mention Options", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ListItem(
                            headlineContent = { Text("View Post") },
                            leadingContent = { Icon(Icons.Default.Visibility, contentDescription = null) },
                            modifier = Modifier.clickable {
                                onNavigateToPostDetail(post.id, post.userId)
                                selectedTaggedPostForOptions = null
                            }
                        )
                        ListItem(
                            headlineContent = { Text("View Mentioned People") },
                            leadingContent = { Icon(Icons.Default.People, contentDescription = null) },
                            modifier = Modifier.clickable {
                                postForMentionDetails = post
                                selectedTaggedPostForOptions = null
                            }
                        )
                        if (isCurrentUser || !isMyPost) {
                            ListItem(
                                headlineContent = { Text("Remove My Mention", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                                supportingContent = { Text("Remove your tag from this content without deleting the post", fontSize = 11.sp) },
                                leadingContent = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        val deterministicId = "mention_post_${post.id}_${currentUserId}"
                                        val success = MentionRepository.removeMention(deterministicId, currentUserId)
                                        if (success) {
                                            Toast.makeText(context, "Mention removed successfully! ✅", Toast.LENGTH_SHORT).show()
                                        } else {
                                            // Fallback for reel contentType
                                            val reelId = "mention_reel_${post.id}_${currentUserId}"
                                            val successReel = MentionRepository.removeMention(reelId, currentUserId)
                                            if (successReel) {
                                                Toast.makeText(context, "Mention removed successfully! ✅", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed to remove mention", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    selectedTaggedPostForOptions = null
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedTaggedPostForOptions = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        postForMentionDetails?.let { mentionPost ->
            MentionDetailsBottomSheet(
                contentId = mentionPost.id,
                contentType = if (mentionPost.postUrl.endsWith(".mp4") || !com.example.utils.MediaResolver.isImage(mentionPost.postUrl)) "reel" else "post",
                onDismiss = { postForMentionDetails = null },
                onNavigateToProfile = { uid ->
                    if (uid.isNotBlank()) onNavigate("profile/${android.net.Uri.encode(uid.trim())}")
                }
            )
        }

        // Post Options Dialog (Pin, Insights, Archive, Delete)
        selectedPostForOptions?.let { post ->
            AlertDialog(
                onDismissRequest = { selectedPostForOptions = null },
                title = { Text("Post Options", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ListItem(
                            headlineContent = { Text(if (post.isPinned) "Unpin from profile" else "Pin to your profile") },
                            leadingContent = { Icon(Icons.Default.PushPin, contentDescription = null) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val (_, msg) = FlagshipFeaturesRepository.togglePinPost(currentUserId, post.id)
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    profileViewModel.loadProfile(profileUserId)
                                }
                                selectedPostForOptions = null
                            }
                        )
                        ListItem(
                            headlineContent = { Text("View Insights") },
                            leadingContent = { Icon(Icons.Default.BarChart, contentDescription = null) },
                            modifier = Modifier.clickable {
                                postForInsights = post
                                selectedPostForOptions = null
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Archive") },
                            leadingContent = { Icon(Icons.Default.Archive, contentDescription = null) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    FlagshipFeaturesRepository.setPostArchive(post.id, true)
                                    profileViewModel.loadProfile(profileUserId)
                                }
                                selectedPostForOptions = null
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Move to Recently Deleted", color = MaterialTheme.colorScheme.error) },
                            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    FlagshipFeaturesRepository.softDeletePost(post.id)
                                    profileViewModel.loadProfile(profileUserId)
                                }
                                selectedPostForOptions = null
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedPostForOptions = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (postForInsights != null) {
            ContentInsightsBottomSheet(
                post = postForInsights,
                onDismiss = { postForInsights = null }
            )
        }

        if (showShareSheet) {
            profileUser?.let { pUser ->
                val sharePayload = com.example.model.SharePayload.fromUser(pUser)
                ModernShareBottomSheet(
                    payload = sharePayload,
                    onDismiss = { showShareSheet = false },
                    onAddToStory = { payload ->
                        sharedPayloadForStory = payload
                        showShareSheet = false
                    }
                )
            }
        }

        if (showProfileStoryViewer && profileUserStories.isNotEmpty()) {
            StoryPlayerOverlay(
                stories = profileUserStories,
                initialIndex = 0,
                onDismiss = {
                    showProfileStoryViewer = false
                    com.example.utils.GlobalMediaSessionManager.releaseFocus(com.example.utils.MediaSource.STORY)
                },
                onAddStoryClick = {
                    showProfileStoryViewer = false
                    com.example.utils.GlobalMediaSessionManager.releaseFocus(com.example.utils.MediaSource.STORY)
                    showStoryCreator = true
                },
                onProfileClick = { uid ->
                    showProfileStoryViewer = false
                    com.example.utils.GlobalMediaSessionManager.releaseFocus(com.example.utils.MediaSource.STORY)
                    if (uid != targetProfileUid) {
                        onNavigate("profile/$uid")
                    }
                },
                onNavigateToReel = { reelId ->
                    showProfileStoryViewer = false
                    com.example.utils.GlobalMediaSessionManager.releaseFocus(com.example.utils.MediaSource.STORY)
                    onNavigateToReel(reelId)
                }
            )
        }

        unlockedStoryToView?.let { unlockedStory ->
            StoryPlayerOverlay(
                stories = listOf(unlockedStory),
                initialIndex = 0,
                onDismiss = {
                    unlockedStoryToView = null
                    com.example.utils.GlobalMediaSessionManager.releaseFocus(com.example.utils.MediaSource.STORY)
                },
                onAddStoryClick = {
                    unlockedStoryToView = null
                    showStoryCreator = true
                },
                onProfileClick = { uid ->
                    unlockedStoryToView = null
                    if (uid.isNotBlank() && uid != targetProfileUid) {
                        onNavigate("profile/$uid")
                    }
                },
                onNavigateToReel = { reelId ->
                    unlockedStoryToView = null
                    onNavigateToReel(reelId)
                }
            )
        }

        val lockedPostForSheet = selectedExclusivePostToUnlock
        if (lockedPostForSheet != null) {
            val isReelType = lockedPostForSheet.mediaType.equals("video", ignoreCase = true) ||
                lockedPostForSheet.mediaType.equals("reel", ignoreCase = true)
            ExclusiveUnlockBottomSheet(
                contentId = lockedPostForSheet.id,
                contentType = if (isReelType) "REEL" else "POST",
                creatorId = lockedPostForSheet.userId,
                creatorUsername = profileUser?.username?.takeIf { it.isNotBlank() } ?: lockedPostForSheet.username.orEmpty(),
                creatorAvatarUrl = profileUser?.avatarUrl?.takeIf { it.isNotBlank() } ?: lockedPostForSheet.userAvatarUrl.orEmpty(),
                title = lockedPostForSheet.caption.take(60),
                teaserCaption = lockedPostForSheet.teaserCaption.ifBlank { lockedPostForSheet.caption },
                blurredThumbnailUrl = lockedPostForSheet.blurredThumbnailUrl.ifBlank { lockedPostForSheet.thumbnailUrl ?: lockedPostForSheet.postUrl },
                priceInr = lockedPostForSheet.postUnlockPrice.coerceAtLeast(10.0),
                onDismiss = { selectedExclusivePostToUnlock = null },
                onUnlocked = {
                    selectedExclusivePostToUnlock = null
                    if (isReelType) {
                        onNavigateToReel(lockedPostForSheet.id)
                    } else {
                        onNavigateToPostDetail(lockedPostForSheet.id, lockedPostForSheet.userId)
                    }
                }
            )
        }

        if (showStoryCreator || sharedPayloadForStory != null) {
            val shared = sharedPayloadForStory
            StoryCreatorDialog(
                onDismiss = { 
                    showStoryCreator = false
                    sharedPayloadForStory = null
                },
                initialText = shared?.let { "Check out @${it.ownerUsername}'s profile!" } ?: "",
                mentionedUserId = shared?.ownerUid,
                mentionedUsername = shared?.ownerUsername,
                initialMediaUri = shared?.thumbnailUrl?.takeIf { it.isNotEmpty() }?.let { android.net.Uri.parse(it) }
            )
        }

        val activeHighlight = selectedHighlight
        if (showHighlightViewer && activeHighlight != null) {
            HighlightViewerDialog(
                highlight = activeHighlight,
                onDismiss = {
                    showHighlightViewer = false
                    selectedHighlight = null
                }
            )
        }

        val subscribeCreator = profileUser
        if (showSubscribeDialog && subscribeCreator != null) {
            val creator = subscribeCreator
            val plan = creatorPlan ?: com.example.model.CreatorSubscriptionPlan(creatorUid = creator.id)
            var isProcessingSub by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isProcessingSub) showSubscribeDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Loyalty, contentDescription = null, tint = Color(0xFF7B1FA2), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Subscribe to @${creator.username}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = plan.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF7B1FA2)
                        )
                        Text(
                            text = "₹${plan.price.toInt()}/month",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = plan.description,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        HorizontalDivider()
                        Text("Subscriber Exclusive Benefits:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        plan.benefits.forEach { benefit ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(benefit, fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                isProcessingSub = true
                                try {
                                    val userDoc = RepositoryManager.firestore.collection("users").document(currentUserId).get().await()
                                    val balance = userDoc.getDouble("walletBalance") ?: 0.0

                                    if (balance < plan.price) {
                                        Toast.makeText(context, "Opening UPI Payment Gateway...", Toast.LENGTH_SHORT).show()
                                        try {
                                            val intent = android.content.Intent(context, com.example.ui.SubscriptionActivity::class.java).apply {
                                                putExtra("creatorId", creator.id)
                                                putExtra("creatorName", creator.username.ifEmpty { "Creator" })
                                                putExtra("amount", String.format(java.util.Locale.US, "%.2f", plan.price))
                                                putExtra("purpose", "CREATOR_EXCLUSIVE")
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Unable to open UPI checkout", Toast.LENGTH_SHORT).show()
                                        }
                                        showSubscribeDialog = false
                                        return@launch
                                    }

                                    // 1. Deduct from subscriber wallet
                                    RepositoryManager.firestore.collection("users").document(currentUserId)
                                        .update("walletBalance", com.google.firebase.firestore.FieldValue.increment(-plan.price))
                                        .await()

                                    // 2. Add verified subscription record
                                    val subRecord = com.example.model.AuthoritativeSubscription(
                                        subscriberId = currentUserId,
                                        creatorId = creator.id,
                                        planId = plan.planId,
                                        pricePaid = plan.price
                                    )
                                    RepositoryManager.firestore.collection("subscriptions").document(subRecord.id).set(subRecord).await()

                                    // 3. Record verified immutable transaction on creator's earnings ledger
                                    val platformFee = plan.price * 0.15 // 15% platform fee
                                    val creatorNet = plan.price - platformFee
                                    val earningTxn = com.example.model.EarningTransaction(
                                        creatorUid = creator.id,
                                        senderUid = currentUserId,
                                        sourceType = "SUBSCRIPTION",
                                        sourceId = subRecord.id,
                                        grossAmount = plan.price,
                                        platformFee = platformFee,
                                        creatorAmount = creatorNet,
                                        status = "AVAILABLE",
                                        contentTitle = "Monthly Subscription Pass"
                                    )
                                    MonetizationRepository.recordEarningTransaction(earningTxn)

                                    Toast.makeText(context, "Successfully subscribed to @${creator.username}! 🎉", Toast.LENGTH_SHORT).show()
                                    showSubscribeDialog = false
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Subscription failed: ${e.localizedMessage ?: "Please try again"}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isProcessingSub = false
                                }
                            }
                        },
                        enabled = !isProcessingSub,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2))
                    ) {
                        Text(if (isProcessingSub) "Processing..." else "Subscribe Now (₹${plan.price.toInt()})")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSubscribeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfilePostsGrid(
    posts: List<FirebasePost>,
    gridState: LazyGridState,
    isCurrentUser: Boolean,
    viewerId: String = RepositoryManager.getCurrentUserId(),
    onNavigateToPost: (postId: String, userId: String?) -> Unit,
    onLockedExclusiveClick: (FirebasePost) -> Unit = { post -> onNavigateToPost(post.id, post.userId) },
    onLongClickPost: (FirebasePost) -> Unit,
    isLoadingMore: Boolean = false,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String
) {
    val unlockedKeys by EntitlementRepository.unlockedKeysFlow.collectAsStateWithLifecycle()

    if (posts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 48.dp, horizontal = 24.dp),
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
                        imageVector = emptyIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = emptyTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = emptySubtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        val distinctPosts = remember(posts) { posts.distinctBy { it.id } }
        var visibleCount by remember(distinctPosts.firstOrNull()?.id) { mutableIntStateOf(18) }
        val paginatedPosts = remember(distinctPosts, visibleCount) {
            distinctPosts.take(visibleCount)
        }
        val shouldLoadMoreGrid by remember {
            derivedStateOf {
                val total = gridState.layoutInfo.totalItemsCount
                val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                total > 0 && lastVisible >= total - 6
            }
        }
        LaunchedEffect(shouldLoadMoreGrid, distinctPosts.size) {
            if (shouldLoadMoreGrid && visibleCount < distinctPosts.size) {
                visibleCount = (visibleCount + 15).coerceAtMost(distinctPosts.size)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            verticalArrangement = Arrangement.spacedBy(1.5.dp),
            contentPadding = PaddingValues(bottom = 64.dp)
        ) {
            itemsIndexed(
                items = paginatedPosts,
                key = { index, post -> "${post.id}_$index" },
                contentType = { _, post -> if (post.mediaType.equals("video", ignoreCase = true)) "profile_video_tile" else "profile_image_tile" }
            ) { _, post ->
                val isExclusiveItem = (post.isExclusive || post.isPaidPost) && post.postUnlockPrice >= 10.0
                val isOwner = viewerId.isNotBlank() && post.userId == viewerId
                var isUnlockedRemote by remember(post.id, viewerId, unlockedKeys) {
                    mutableStateOf(
                        !isExclusiveItem || isOwner || unlockedKeys.contains("${viewerId}_${post.id}")
                    )
                }
                var vaultThumbUrl by remember(post.id) {
                    mutableStateOf(ExclusiveContentRepository.getCachedVaultMedia(post.id)?.let {
                        it.thumbnailUrl.ifBlank { it.mediaUrl }
                    } ?: "")
                }

                LaunchedEffect(post.id, viewerId, isExclusiveItem, isOwner, unlockedKeys) {
                    if (isExclusiveItem) {
                        try {
                            val unlocked = isOwner ||
                                unlockedKeys.contains("${viewerId}_${post.id}") ||
                                EntitlementRepository.verifyAccessAuthoritative(
                                    userId = viewerId,
                                    creatorId = post.userId,
                                    contentId = post.id,
                                    isExclusive = true,
                                    isPaidPost = true
                                )
                            isUnlockedRemote = unlocked
                            if (unlocked && post.hasVaultMedia && vaultThumbUrl.isBlank()) {
                                val vault = ExclusiveContentRepository.resolveUnlockedMedia(post.id)
                                if (vault != null) {
                                    vaultThumbUrl = vault.thumbnailUrl.ifBlank { vault.mediaUrl }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                val isLockedTile = isExclusiveItem && !isOwner && !isUnlockedRemote
                val isImage = when {
                    post.mediaType.equals("video", ignoreCase = true) || post.mediaType.equals("reel", ignoreCase = true) -> false
                    post.mediaType.equals("image", ignoreCase = true) || post.postUrls.size > 1 -> true
                    else -> com.example.utils.MediaResolver.isImage(post.postUrl ?: "")
                }
                val isVideo = (post.postUrl ?: "").isNotEmpty() && !isImage
                val isCarousel = post.postUrls.size > 1
                val rawMediaUrl = when {
                    isLockedTile -> post.blurredThumbnailUrl.ifBlank { post.thumbnailUrl ?: post.postUrl }
                    isExclusiveItem && vaultThumbUrl.isNotBlank() -> {
                        if (isVideo) {
                            com.example.utils.MediaResolver.resolveVideoPosterUrl(vaultThumbUrl, vaultThumbUrl).ifBlank { vaultThumbUrl }
                        } else {
                            vaultThumbUrl
                        }
                    }
                    isVideo -> {
                        com.example.utils.MediaResolver.resolveVideoPosterUrl(post.postUrl, post.thumbnailUrl)
                            .ifBlank { post.thumbnailUrl?.takeIf { it.isNotEmpty() } ?: post.postUrl }
                    }
                    else -> {
                        post.thumbnailUrl?.takeIf { it.isNotEmpty() }
                            ?: post.postUrl.ifEmpty { post.postUrls.firstOrNull() ?: "" }
                    }
                }
                val candidateUrls = remember(rawMediaUrl, isImage, isLockedTile) {
                    if (isImage || isLockedTile) {
                        com.example.utils.MediaResolver.getFallbackImageUrls(rawMediaUrl).ifEmpty { listOf(rawMediaUrl) }
                    } else {
                        listOf(rawMediaUrl)
                    }
                }
                var fallbackIdx by remember(rawMediaUrl) { mutableIntStateOf(0) }
                val mediaUrl = candidateUrls.getOrElse(fallbackIdx) { rawMediaUrl }

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(ProfileTilePlaceholderBrush)
                        .combinedClickable(
                            onClick = {
                                if (isLockedTile) {
                                    onLockedExclusiveClick(post)
                                } else {
                                    onNavigateToPost(post.id, post.userId)
                                }
                            },
                            onLongClick = {
                                if (isCurrentUser) {
                                    onLongClickPost(post)
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val tileContext = LocalContext.current
                    val tileRequest = remember(mediaUrl) {
                        com.example.utils.ExoPlayerUtils.buildFastPosterRequest(
                            context = tileContext,
                            url = mediaUrl,
                            widthPx = 360,
                            heightPx = 360,
                            onError = {
                                if (fallbackIdx + 1 < candidateUrls.size) {
                                    fallbackIdx++
                                }
                            }
                        )
                    }
                    AsyncImage(
                        model = tileRequest,
                        contentDescription = if (isLockedTile) "Locked Exclusive Post" else "Post Thumbnail",
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isLockedTile) Modifier.blur(14.dp) else Modifier),
                        contentScale = ContentScale.Crop,
                    )

                    if (isLockedTile) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .border(1.dp, ExclusiveGoldStart, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked",
                                        tint = ExclusiveGoldStart,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                ExclusiveGridLockBadge(
                                    priceInr = post.postUnlockPrice,
                                    isOwner = false,
                                    isUnlocked = false
                                )
                            }
                        }
                    } else if (isExclusiveItem) {
                        ExclusiveGridLockBadge(
                            priceInr = post.postUnlockPrice,
                            isOwner = isOwner,
                            isUnlocked = true,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(5.dp)
                        )
                    }

                    if (post.isPinned) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .size(22.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    if (isVideo) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                        )
                    } else if (isCarousel) {
                        Icon(
                            Icons.Default.Collections,
                            contentDescription = "Carousel",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(18.dp)
                        )
                    }
                }
            }

            if (isLoadingMore) {
                items(3, key = { "profile_more_skel_$it" }, contentType = { "profile_skel" }) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileReelsGrid(
    reels: List<FirebasePost>,
    gridState: LazyGridState,
    isCurrentUser: Boolean,
    viewerId: String = RepositoryManager.getCurrentUserId(),
    onNavigateToReel: (String) -> Unit,
    onLockedExclusiveClick: (FirebasePost) -> Unit = { onNavigateToReel(it.id) },
    onLongClickPost: (FirebasePost) -> Unit
) {
    val unlockedKeys by EntitlementRepository.unlockedKeysFlow.collectAsStateWithLifecycle()

    if (reels.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 48.dp, horizontal = 24.dp),
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
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Reels Yet",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isCurrentUser) "Create and share your first reel with the community" else "This creator hasn't posted any reels yet",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        val distinctReels = remember(reels) { reels.distinctBy { it.id } }
        var visibleReelsCount by remember(distinctReels.firstOrNull()?.id) { mutableIntStateOf(18) }
        val paginatedReels = remember(distinctReels, visibleReelsCount) {
            distinctReels.take(visibleReelsCount)
        }
        val shouldLoadMoreReels by remember {
            derivedStateOf {
                val total = gridState.layoutInfo.totalItemsCount
                val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                total > 0 && lastVisible >= total - 6
            }
        }
        LaunchedEffect(shouldLoadMoreReels, distinctReels.size) {
            if (shouldLoadMoreReels && visibleReelsCount < distinctReels.size) {
                visibleReelsCount = (visibleReelsCount + 15).coerceAtMost(distinctReels.size)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            verticalArrangement = Arrangement.spacedBy(1.5.dp),
            contentPadding = PaddingValues(bottom = 64.dp)
        ) {
            itemsIndexed(
                items = paginatedReels,
                key = { index, reel -> "${reel.id}_$index" },
                contentType = { _, _ -> "profile_reel_tile" }
            ) { _, reel ->
                val isExclusiveReel = (reel.isExclusive || reel.isPaidPost) && reel.postUnlockPrice >= 10.0
                val isOwner = viewerId.isNotBlank() && reel.userId == viewerId
                var isUnlockedRemote by remember(reel.id, viewerId, unlockedKeys) {
                    mutableStateOf(
                        !isExclusiveReel || isOwner || unlockedKeys.contains("${viewerId}_${reel.id}")
                    )
                }
                var vaultThumbUrl by remember(reel.id) {
                    mutableStateOf(ExclusiveContentRepository.getCachedVaultMedia(reel.id)?.let {
                        it.thumbnailUrl.ifBlank { it.mediaUrl }
                    } ?: "")
                }

                LaunchedEffect(reel.id, viewerId, isExclusiveReel, isOwner, unlockedKeys) {
                    if (isExclusiveReel) {
                        try {
                            val unlocked = isOwner ||
                                unlockedKeys.contains("${viewerId}_${reel.id}") ||
                                EntitlementRepository.verifyAccessAuthoritative(
                                    userId = viewerId,
                                    creatorId = reel.userId,
                                    contentId = reel.id,
                                    isExclusive = true,
                                    isPaidPost = true
                                )
                            isUnlockedRemote = unlocked
                            if (unlocked && reel.hasVaultMedia && vaultThumbUrl.isBlank()) {
                                val vault = ExclusiveContentRepository.resolveUnlockedMedia(reel.id)
                                if (vault != null) {
                                    vaultThumbUrl = vault.thumbnailUrl.ifBlank { vault.mediaUrl }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                val isLockedTile = isExclusiveReel && !isOwner && !isUnlockedRemote
                val rawMediaUrl = when {
                    isLockedTile -> reel.blurredThumbnailUrl.ifBlank { reel.thumbnailUrl ?: reel.postUrl }
                    isExclusiveReel && vaultThumbUrl.isNotBlank() -> vaultThumbUrl
                    else -> reel.thumbnailUrl?.takeIf { it.isNotEmpty() }
                        ?: reel.postUrl.ifEmpty { reel.postUrls.firstOrNull() ?: "" }
                }
                val mediaUrl = if (isLockedTile) {
                    rawMediaUrl
                } else {
                    com.example.utils.MediaResolver.resolveVideoPosterUrl(rawMediaUrl, reel.thumbnailUrl)
                        .ifBlank { com.example.utils.MediaResolver.resolveImageUrl(rawMediaUrl).ifBlank { rawMediaUrl } }
                }
                val viewOrLikeCount = when {
                    reel.viewsCount > 0 -> formatCount(reel.viewsCount)
                    reel.likesCount > 0 -> formatCount(reel.likesCount)
                    else -> "0"
                }

                Box(
                    modifier = Modifier
                        .aspectRatio(0.72f) // Portrait reels aspect ratio
                        .background(ProfileTilePlaceholderBrush)
                        .clip(RoundedCornerShape(2.dp))
                        .combinedClickable(
                            onClick = {
                                if (isLockedTile) {
                                    onLockedExclusiveClick(reel)
                                } else {
                                    onNavigateToReel(reel.id)
                                }
                            },
                            onLongClick = {
                                if (isCurrentUser) {
                                    onLongClickPost(reel)
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val reelContext = LocalContext.current
                    val reelTileRequest = remember(mediaUrl) {
                        com.example.utils.ExoPlayerUtils.buildFastPosterRequest(
                            context = reelContext,
                            url = mediaUrl,
                            widthPx = 360,
                            heightPx = 500
                        )
                    }
                    AsyncImage(
                        model = reelTileRequest,
                        contentDescription = if (isLockedTile) "Locked Exclusive Reel" else "Reel Thumbnail",
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isLockedTile) Modifier.blur(14.dp) else Modifier),
                        contentScale = ContentScale.Crop
                    )

                    if (isLockedTile) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.48f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .border(1.dp, ExclusiveGoldStart, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked",
                                        tint = ExclusiveGoldStart,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                ExclusiveGridLockBadge(
                                    priceInr = reel.postUnlockPrice,
                                    isOwner = false,
                                    isUnlocked = false
                                )
                            }
                        }
                    } else if (isExclusiveReel) {
                        ExclusiveGridLockBadge(
                            priceInr = reel.postUnlockPrice,
                            isOwner = isOwner,
                            isUnlocked = true,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(5.dp)
                        )
                    }

                    // Gradient overlay at bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.35f)
                            .align(Alignment.BottomCenter)
                            .background(ProfileReelScrimBrush)
                    )

                    // View / play count badge at bottom left
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Plays",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = viewOrLikeCount,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Pinned icon at top start if pinned
                    if (reel.isPinned) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .size(20.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Viewer's personal "Unlocked" tab listing all exclusive Posts, Reels, and Stories they have purchased.
 */
@Composable
private fun ProfileUnlockedExclusiveTab(
    buyerId: String,
    gridState: LazyGridState,
    onOpenUnlockedPost: (postId: String, creatorId: String) -> Unit,
    onOpenUnlockedReel: (reelId: String) -> Unit,
    onOpenUnlockedStory: (ExclusivePurchaseRecord, String) -> Unit
) {
    var isLoading by remember(buyerId) { mutableStateOf(true) }
    var unlockedItems by remember(buyerId) { mutableStateOf<List<ExclusivePurchaseRecord>>(emptyList()) }

    LaunchedEffect(buyerId) {
        isLoading = true
        try {
            ExclusiveContentRepository.observeBuyerUnlockedItems(buyerId).collect { records ->
                unlockedItems = records
                isLoading = false
            }
        } catch (_: Exception) {
            isLoading = false
        }
    }

    if (isLoading) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(4) {
                ExclusiveSkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.85f),
                    cornerRadius = 16
                )
            }
        }
    } else if (unlockedItems.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 48.dp, horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(ExclusiveGoldMid.copy(alpha = 0.14f))
                        .border(1.dp, ExclusiveGoldStart.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = ExclusiveGoldMid,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Unlocked Content Yet",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Exclusive Posts, Reels, and Stories you unlock are saved here permanently.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .testTag("profile_unlocked_grid"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 72.dp)
        ) {
            items(unlockedItems, key = { it.id }) { purchase ->
                var resolvedMediaUrl by remember(purchase.contentId) {
                    mutableStateOf(
                        ExclusiveContentRepository.getCachedVaultMedia(purchase.contentId)?.mediaUrl ?: ""
                    )
                }
                var resolvedThumbUrl by remember(purchase.contentId) {
                    mutableStateOf(
                        ExclusiveContentRepository.getCachedVaultMedia(purchase.contentId)?.let {
                            it.thumbnailUrl.ifBlank { it.mediaUrl }
                        } ?: ""
                    )
                }

                LaunchedEffect(purchase.contentId) {
                    val vault = ExclusiveContentRepository.resolveUnlockedMedia(purchase.contentId)
                    if (vault != null) {
                        resolvedMediaUrl = vault.mediaUrl
                        resolvedThumbUrl = vault.thumbnailUrl.ifBlank { vault.mediaUrl }
                    }
                }

                val displayThumb = resolvedThumbUrl.ifBlank { purchase.blurredThumbnailUrl }
                val typeUpper = purchase.contentType.uppercase()

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF151520),
                    border = BorderStroke(1.dp, ExclusiveGoldMid.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.82f)
                        .clickable {
                            when (typeUpper) {
                                "REEL", "VIDEO" -> onOpenUnlockedReel(purchase.contentId)
                                "STORY" -> onOpenUnlockedStory(purchase, resolvedMediaUrl)
                                else -> onOpenUnlockedPost(purchase.contentId, purchase.creatorId)
                            }
                        }
                        .testTag("unlocked_item_${purchase.contentId}")
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (displayThumb.isNotBlank()) {
                            AsyncImage(
                                model = displayThumb,
                                contentDescription = purchase.title.ifBlank { "Unlocked exclusive item" },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Bottom scrim
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.55f)
                                .align(Alignment.BottomCenter)
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                                    )
                                )
                        )

                        // Top-left type pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.68f),
                            border = BorderStroke(0.8.dp, ExclusiveGoldStart.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = when (typeUpper) {
                                    "REEL", "VIDEO" -> "REEL"
                                    "STORY" -> "STORY"
                                    else -> "POST"
                                },
                                color = ExclusiveGoldStart,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }

                        // Top-right Unlocked check pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF064E3B).copy(alpha = 0.9f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(11.dp))
                                Text("₹${purchase.amount.toInt()}", color = Color(0xFF34D399), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Bottom creator & teaser info
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AsyncImage(
                                    model = purchase.creatorAvatar.ifBlank { "https://api.dicebear.com/7.x/avataaars/png?seed=${purchase.creatorUsername}" },
                                    contentDescription = purchase.creatorUsername,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, ExclusiveGoldStart, CircleShape)
                                )
                                Text(
                                    text = "@${purchase.creatorUsername.ifBlank { "creator" }}",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (purchase.teaserCaption.isNotBlank() || purchase.title.isNotBlank()) {
                                Text(
                                    text = purchase.teaserCaption.ifBlank { purchase.title },
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

@Composable
private fun ProfileScreenStat(
    count: String,
    label: String,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
    labelColor: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = count,
            fontWeight = FontWeight.Bold,
            color = textColor,
            fontSize = 16.sp
        )
        Text(
            text = label,
            color = labelColor,
            fontSize = 13.sp
        )
    }
}
