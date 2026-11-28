package com.example.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex


import com.example.viewmodel.StoryViewModel

import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.UserLiveAvatar
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.model.Video
import com.example.model.User
import com.example.model.StoryUiModel
import com.example.model.RepositoryManager
import com.example.viewmodel.VideoViewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.compose.animation.core.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AccountCircle
import com.example.utils.bounceClick

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun HomeFeedScreen(
    viewModel: VideoViewModel,
    storyViewModel: StoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigate: (String) -> Unit
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val allStories by storyViewModel.stories.collectAsStateWithLifecycle()
    
    val currentUserRealtime by RepositoryManager.currentUserState.collectAsStateWithLifecycle()
    val activeSavedAcc by com.example.auth.MultiAccountManager.activeAccount.collectAsStateWithLifecycle()

    var currentUserId by remember { mutableStateOf(RepositoryManager.getCurrentUserId()) }
    var currentUsername by remember { mutableStateOf(currentUserRealtime?.username ?: activeSavedAcc?.username ?: "") }
    var userAvatar by remember { mutableStateOf(currentUserRealtime?.avatarUrl ?: activeSavedAcc?.avatarUrl ?: "") }

    LaunchedEffect(currentUserRealtime, activeSavedAcc) {
        currentUserId = RepositoryManager.getCurrentUserId()
        currentUserRealtime?.let { u ->
            if (u.username.isNotEmpty()) currentUsername = u.username
            if (u.avatarUrl.isNotEmpty()) userAvatar = u.avatarUrl
        } ?: run {
            activeSavedAcc?.let { acc ->
                if (acc.username.isNotEmpty()) currentUsername = acc.username
                if (acc.avatarUrl.isNotEmpty()) userAvatar = acc.avatarUrl
            }
        }
    }

    var selectedVideoForComments by remember { mutableStateOf<com.example.model.Video?>(null) }
    var selectedVideoForLikes by remember { mutableStateOf<com.example.model.Video?>(null) }
    var selectedVideoForShare by remember { mutableStateOf<com.example.model.Video?>(null) }
    var selectedVideoForOptions by remember { mutableStateOf<com.example.model.Video?>(null) }
    var selectedVideoForRepost by remember { mutableStateOf<com.example.model.Video?>(null) }
    var showCreateStoryDialog by remember { mutableStateOf(false) }
    var storyVideoToShare by remember { mutableStateOf<com.example.model.Video?>(null) }
    var selectedStoryGroup by remember { mutableStateOf<Pair<List<StoryUiModel>, Int>?>(null) }
    var selectedLiveStory by remember { mutableStateOf<StoryUiModel?>(null) }
    var feedSuggestedUsers by remember { mutableStateOf(com.example.ui.GlobalState.cachedHomeSuggestions) }
    var activeHomeAds by remember { mutableStateOf(com.example.ui.GlobalState.cachedHomeAds) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val uid = RepositoryManager.getCurrentUserId()
        currentUserId = uid
        val now = System.currentTimeMillis()
        val extrasFresh = (now - com.example.ui.GlobalState.lastHomeExtrasFetchMs) < 60_000L &&
            com.example.ui.GlobalState.cachedHomeSuggestions.isNotEmpty()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.coroutineScope {
                val userJob = if (uid.isNotBlank() && (userAvatar.isEmpty() || currentUsername.isEmpty())) {
                    launch {
                        try {
                            val userDoc = RepositoryManager.firestore.collection("users").document(uid).get().await()
                            val avatar = userDoc.getString("avatar_url") ?: userDoc.getString("profilePicUrl") ?: ""
                            val uname = userDoc.getString("username") ?: ""
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (avatar.isNotEmpty()) userAvatar = avatar
                                if (uname.isNotEmpty()) currentUsername = uname
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("HomeFeedScreen", "User doc fetch skipped: ${e.message}")
                        }
                    }
                } else null

                val suggestionsJob = if (!extrasFresh) launch {
                    try {
                        val suggestions = RepositoryManager.socialRepository.getModularRecommendations(limit = 15)
                        com.example.ui.GlobalState.cachedHomeSuggestions = suggestions
                        com.example.ui.GlobalState.lastHomeExtrasFetchMs = System.currentTimeMillis()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            feedSuggestedUsers = suggestions
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("HomeFeedScreen", "Suggestions fetch error: ${e.message}")
                    }
                } else null

                val adsJob = if (!extrasFresh) launch {
                    try {
                        val ads = com.example.repository.AdvertisingRepository.getEligibleAdsForPlacement(
                            com.example.model.AdPlacement.HOME_FEED,
                            uid
                        )
                        com.example.ui.GlobalState.cachedHomeAds = ads
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            activeHomeAds = ads
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("HomeFeedScreen", "Ads fetch error: ${e.message}")
                    }
                } else null
                userJob?.join()
                suggestionsJob?.join()
                adsJob?.join()
            }
        }
    }
    
    val initialHomeScroll = remember { com.example.ui.GlobalState.getScroll("home_feed") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = initialHomeScroll.first,
        initialFirstVisibleItemScrollOffset = initialHomeScroll.second
    )
    DisposableEffect(listState) {
        onDispose {
            com.example.ui.GlobalState.saveScroll(
                "home_feed",
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }
    LaunchedEffect(videos, com.example.ui.GlobalState.targetVideoId.value) {
        val targetId = com.example.ui.GlobalState.targetVideoId.value
        if (targetId != null && videos.isNotEmpty()) {
            val index = videos.indexOfFirst { it.id == targetId }
            if (index in videos.indices) {
                try {
                    val targetItem = (index + 3).coerceAtMost((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(index + 3))
                    listState.scrollToItem(targetItem)
                } catch (_: Exception) {}
                com.example.ui.GlobalState.targetVideoId.value = null
            }
        }
    }

    LaunchedEffect(showCreateStoryDialog, storyVideoToShare) {
        if (showCreateStoryDialog || storyVideoToShare != null) {
            com.example.utils.GlobalMediaSessionManager.requestFocus(com.example.utils.MediaSource.STORY, context)
        }
    }

    if (showCreateStoryDialog || storyVideoToShare != null) {
        val targetVideo = storyVideoToShare
        val targetVaultMedia = targetVideo?.let {
            com.example.repository.ExclusiveContentRepository.getCachedVaultMedia(it.id)
        }
        val targetMediaUri = (targetVaultMedia?.mediaUrl?.takeIf { it.isNotEmpty() }
            ?: targetVideo?.videoUrl?.takeIf { it.isNotEmpty() }
            ?: targetVaultMedia?.postUrls?.firstOrNull()?.takeIf { it.isNotEmpty() }
            ?: targetVideo?.videoUrls?.firstOrNull()?.takeIf { it.isNotEmpty() }
            ?: targetVaultMedia?.thumbnailUrl?.takeIf { it.isNotEmpty() }
            ?: targetVideo?.thumbnailUrl?.takeIf { it.isNotEmpty() })
            ?.let { android.net.Uri.parse(it) }

        StoryCreatorDialog(
            onDismiss = { 
                showCreateStoryDialog = false
                storyVideoToShare = null
                com.example.utils.GlobalMediaSessionManager.requestFocus(com.example.utils.MediaSource.HOME_FEED, context)
            },
            repostedPostId = targetVideo?.id,
            isReback = targetVideo != null,
            mentionedUserId = targetVideo?.user?.id,
            mentionedUsername = targetVideo?.user?.username,
            initialMediaUri = targetMediaUri
        )
    }

    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var unreadNotificationsCount by remember { mutableStateOf(0) }
    var unreadMessagesCount by remember { mutableStateOf(0) }
    DisposableEffect(currentUserId) {
        var notifReg: com.google.firebase.firestore.ListenerRegistration? = null
        var chatsReg: com.google.firebase.firestore.ListenerRegistration? = null
        if (currentUserId.isNotEmpty()) {
            try {
                notifReg = com.example.model.RepositoryManager.firestore.collection("users").document(currentUserId).collection("notifications")
                    .whereEqualTo("isRead", false)
                    .addSnapshotListener { snap, _ ->
                        if (snap != null) {
                            unreadNotificationsCount = snap.documents.size
                        }
                    }
            } catch (_: Exception) {}

            // Real unread messages listener
            try {
                chatsReg = com.example.model.RepositoryManager.firestore.collection("users").document(currentUserId).collection("chats")
                    .addSnapshotListener { snap, _ ->
                        if (snap != null) {
                            try {
                                var sumUnread = 0
                                for (doc in snap.documents) {
                                    val unread = (doc.get("unreadCount") as? Number)?.toLong() ?: 0L
                                    if (unread > 0) sumUnread += unread.toInt()
                                }
                                unreadMessagesCount = sumUnread
                            } catch (_: Exception) {}
                        }
                    }
            } catch (_: Exception) {}
        }
        onDispose {
            notifReg?.remove()
            chatsReg?.remove()
        }
    }

    val isAppDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val topGlassBg = if (isAppDark) Color(0xD0121216) else Color(0xD8FFFFFF)
    val bottomLineBrush = if (isAppDark) {
        Brush.horizontalGradient(
            listOf(
                Color.White.copy(alpha = 0.04f),
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.04f)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color.Black.copy(alpha = 0.04f),
                Color.Black.copy(alpha = 0.10f),
                Color.Black.copy(alpha = 0.04f)
            )
        )
    }
    val contentTextColor = if (isAppDark) Color.White else Color(0xFF141416)

    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    com.example.ui.components.LiquidMeshBackground(
        modifier = Modifier.fillMaxSize(),
        isDark = isAppDark
    ) {

            // Main Content Area
            DisposableEffect(Unit) {
                com.example.utils.GlobalMediaSessionManager.requestFocus(com.example.utils.MediaSource.HOME_FEED, context)
                com.example.utils.GlobalFeedAudioState.setSoundEnabled(true)
                onDispose {
                    com.example.utils.GlobalMediaSessionManager.releaseFocus(com.example.utils.MediaSource.HOME_FEED)
                }
            }

            var selectedFeedMode by remember { mutableStateOf("FOR_YOU") } // FOR_YOU, FOLLOWING, FAVORITES
            val favoriteCreatorIds = remember { mutableStateListOf<String>() }
            var followingCreatorIds by remember { mutableStateOf<Set<String>>(emptySet()) }
            var homeRepostToastTarget by remember { mutableStateOf<Pair<com.example.model.Video, () -> Unit>?>(null) }
            LaunchedEffect(homeRepostToastTarget) {
                if (homeRepostToastTarget != null) {
                    kotlinx.coroutines.delay(com.example.ui.motion.SocialMotion.DurationRepostToastHoldMs)
                    homeRepostToastTarget = null
                }
            }

            LaunchedEffect(currentUserId) {
                if (currentUserId.isNotEmpty()) {
                    try {
                        val followingSnap = RepositoryManager.firestore.collection("users").document(currentUserId).collection("following").get().await()
                        followingCreatorIds = followingSnap.documents.map { it.id }.toSet()
                    } catch (_: Exception) {}
                }
            }

            val displayedVideos = remember(videos, selectedFeedMode, followingCreatorIds, favoriteCreatorIds.size) {
                when (selectedFeedMode) {
                    "FOLLOWING" -> {
                        val filtered = videos.filter { it.userId in followingCreatorIds || it.userId == currentUserId }
                        (if (filtered.isNotEmpty()) filtered else videos).sortedByDescending { it.timestamp }
                    }
                    "FAVORITES" -> {
                        val filtered = videos.filter { it.userId in favoriteCreatorIds || it.isSaved || it.isLiked }
                        if (filtered.isNotEmpty()) filtered else videos
                    }
                    else -> videos // Algorithmic For You
                }
            }

            val playableVideos = remember(displayedVideos) {
                displayedVideos.filter { v ->
                    (!v.isMediaAnImage && v.videoUrl.isNotBlank()) ||
                    (v.isMediaAnImage && !com.example.api.MusicClient.resolveAudioUrlForTrack(v.musicTrackUrl, v.musicTrack).isNullOrBlank())
                }
            }
            val playableVideoIds = remember(playableVideos) { playableVideos.map { it.id }.toSet() }
            val isHomeFeedAllowed by com.example.utils.GlobalMediaSessionManager.isHomeFeedPlaybackAllowed.collectAsStateWithLifecycle()
            val isStoryActive by com.example.utils.GlobalMediaSessionManager.isStoryActive.collectAsStateWithLifecycle()

            val playingVideoId by remember(playableVideos, isHomeFeedAllowed, isStoryActive, selectedStoryGroup, showCreateStoryDialog, storyVideoToShare) {
                derivedStateOf {
                    if (!isHomeFeedAllowed || isStoryActive || selectedStoryGroup != null || showCreateStoryDialog || storyVideoToShare != null) {
                        return@derivedStateOf null
                    }
                    val layoutInfo = listState.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    if (visibleItems.isEmpty()) return@derivedStateOf null

                    val viewportStart = layoutInfo.viewportStartOffset
                    val viewportEnd = layoutInfo.viewportEndOffset
                    val viewportHeight = viewportEnd - viewportStart
                    if (viewportHeight <= 0) return@derivedStateOf null
                    val viewportCenter = viewportStart + viewportHeight / 2

                    // Extract matching video items from layoutInfo (must be at least 45% visible to autoplay)
                    val candidateItems = visibleItems.filter { itemInfo ->
                        val rawKey = itemInfo.key as? String ?: return@filter false
                        val realId = rawKey.substringBeforeLast("_", rawKey)
                        if (realId !in playableVideoIds) return@filter false
                        val itemTop = itemInfo.offset
                        val itemBottom = itemInfo.offset + itemInfo.size
                        val visibleTop = maxOf(itemTop, viewportStart)
                        val visibleBottom = minOf(itemBottom, viewportEnd)
                        val visibleHeight = maxOf(0, visibleBottom - visibleTop)
                        val fraction = visibleHeight.toFloat() / itemInfo.size.toFloat().coerceAtLeast(1f)
                        fraction >= 0.38f
                    }

                    if (candidateItems.isEmpty()) {
                        return@derivedStateOf null
                    }

                    val centerItem = candidateItems.minByOrNull { item ->
                        val itemCenter = item.offset + item.size / 2
                        Math.abs(itemCenter - viewportCenter)
                    }

                    val centerKey = centerItem?.key as? String
                    centerKey?.substringBeforeLast("_", centerKey)
                }
            }

            LaunchedEffect(playingVideoId, displayedVideos) {
                if (playingVideoId != null && displayedVideos.isNotEmpty()) {
                    val currentIdx = displayedVideos.indexOfFirst { it.id == playingVideoId }
                    if (currentIdx >= 0) {
                        val nextUrls = displayedVideos
                            .drop(currentIdx + 1)
                            .filter { !it.isMediaAnImage && it.videoUrl.isNotBlank() && (!it.isExclusiveContent && !it.isPaidPost) }
                            .take(2)
                            .map { it.videoUrl }
                        if (nextUrls.isNotEmpty()) {
                            com.example.utils.VideoCacheManager.prefetchNextVideos(context, nextUrls, maxCount = 2)
                        }
                    }
                }
            }

            val homeLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(homeLifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                        com.example.utils.VideoCacheManager.cancelPrefetches()
                    }
                }
                homeLifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    homeLifecycleOwner.lifecycle.removeObserver(observer)
                    com.example.utils.VideoCacheManager.cancelPrefetches()
                }
            }
        
            val shouldLoadMore by remember {
                derivedStateOf {
                    val total = listState.layoutInfo.totalItemsCount
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    total > 0 && lastVisible >= total - 3
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore && !isLoading) {
                    viewModel.loadMore()
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    viewModel.refreshVideos()
                    storyViewModel.refreshStories()
                    com.example.utils.GlobalMediaSessionManager.requestFocus(
                        com.example.utils.MediaSource.HOME_FEED,
                        context
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = statusBarTopPadding + 48.dp,
                        bottom = navBarBottomPadding + 70.dp
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // removed banner

                    // Original Story Module Tray
                    item(key = "story_tray_header", contentType = "story_tray") {
                        OriginalStoryTray(
                            stories = allStories,
                            currentUserId = currentUserId,
                            userAvatar = userAvatar,
                            onStoryClick = { group, index -> 
                                // Immediate global media pause before opening story viewer
                                com.example.utils.GlobalMediaSessionManager.requestFocus(com.example.utils.MediaSource.STORY, context)
                                selectedStoryGroup = Pair(group, index) 
                            },
                            onLiveClick = { liveStory ->
                                try {
                                    val intent = android.content.Intent(context, com.example.ui.LiveActivity::class.java).apply {
                                        putExtra("liveID", liveStory.id)
                                        putExtra("userID", currentUserId)
                                        putExtra("userName", currentUsername.ifEmpty { "User" })
                                        putExtra("isHost", liveStory.userId == currentUserId)
                                        putExtra("hostUid", liveStory.userId)
                                        putExtra("hostUsername", liveStory.username)
                                        putExtra("hostAvatar", liveStory.avatarUrl)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(context, "Unable to open live stream", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onCreateStoryClick = {
                                if (!RepositoryManager.isRealUserLoggedIn()) {
                                    android.widget.Toast.makeText(context, "Please log in to share a story", android.widget.Toast.LENGTH_SHORT).show()
                                    onNavigate("auth")
                                } else {
                                    showCreateStoryDialog = true
                                }
                            }
                        )
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                            thickness = 0.5.dp
                        )
                        // Creator Pro Suite Quick-Launch Strip
                        val proSuiteShortcuts = remember {
                            listOf(
                                Triple("🏆 Leaderboard", "leaderboard", Color(0xFFFFB300)),
                                Triple("🎡 Daily Spin & Box", "daily_rewards", Color(0xFFFF2A6D)),
                                Triple("✨ Frames & Themes", "cosmetics_store", Color(0xFF9C27B0)),
                                Triple("🤝 Brand Collabs", "brand_collabs", Color(0xFF10B981)),
                                Triple("🎬 Watch Party", "watch_party/new", Color(0xFF00BCD4)),
                                Triple("🔥 Hot Templates", "templates", Color(0xFFEE1D52))
                            )
                        }
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(proSuiteShortcuts.size, key = { proSuiteShortcuts[it].second }) { i ->
                                val (label, route, accent) = proSuiteShortcuts[i]
                                Surface(
                                    onClick = { onNavigate(route) },
                                    shape = RoundedCornerShape(50),
                                    color = accent.copy(alpha = if (isAppDark) 0.16f else 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                                    modifier = Modifier.testTag("home_pro_shortcut_$i")
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isAppDark) Color.White else Color(0xFF141418),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Instagram-Style Hot Video Templates Showcase right on Home Screen
                    item(key = "home_hot_video_templates_strip", contentType = "templates_strip") {
                        var homeVideoTemplates by remember {
                            mutableStateOf(
                                com.example.ui.templates.SAMPLE_SOCIAL_INDIA_TEMPLATES
                                    .filter { it.type == "reel" || it.type == "quick_story" || it.type == "story" }
                                    .sortedByDescending { it.usageCount }
                            )
                        }
                        LaunchedEffect(Unit) {
                            try {
                                val (fetched, _) = com.example.ui.templates.TemplateRepository.fetchAllTemplates(context)
                                val videoTpls = fetched
                                    .filter { it.type == "reel" || it.type == "quick_story" || it.type == "story" }
                                    .sortedByDescending { it.usageCount }
                                if (videoTpls.isNotEmpty()) {
                                    homeVideoTemplates = videoTpls
                                }
                            } catch (_: Exception) {}
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "🔥 Hot Video Templates",
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFFEE1D52).copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "REELS & BEAT SYNC",
                                            color = Color(0xFFEE1D52),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "See all →",
                                    color = Color(0xFF0095F6),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable { onNavigate("templates") }
                                )
                            }
                            androidx.compose.foundation.lazy.LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(homeVideoTemplates.size, key = { homeVideoTemplates[it].id }) { idx ->
                                    val tpl = homeVideoTemplates[idx]
                                    val c1 = Color(tpl.previewGradientStart)
                                    val c2 = Color(tpl.previewGradientEnd)
                                    Surface(
                                        onClick = { onNavigate("templates/${tpl.id}") },
                                        shape = RoundedCornerShape(18.dp),
                                        color = Color.Transparent,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, c1.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .width(166.dp)
                                            .height(114.dp)
                                            .testTag("home_template_card_${tpl.id}")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(c1.copy(alpha = 0.88f), c2.copy(alpha = 0.94f))
                                                    )
                                                )
                                                .padding(11.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(50),
                                                        color = Color.Black.copy(alpha = 0.48f)
                                                    ) {
                                                        Text(
                                                            text = "${tpl.typeLabel} • ${(tpl.duration / 1000L).coerceAtLeast(1L)}s",
                                                            color = Color.White,
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Icon(
                                                        Icons.Default.PlayCircleFilled,
                                                        contentDescription = "Preview video template",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = tpl.title,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    val usageTxt = if (tpl.usageCount > 0L) {
                                                        "${com.example.ui.templates.formatUsageCount(tpl.usageCount)} uses • Use Template"
                                                    } else {
                                                        "Use Video Template"
                                                    }
                                                    Text(
                                                        text = usageTxt,
                                                        color = Color.White.copy(alpha = 0.92f),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                            thickness = 0.5.dp
                        )
                    }

                    // Interactive Live Community Polls & Quizzes Deck
                    item(key = "home_feed_polls_strip", contentType = "polls_strip") {
                        HomeFeedPollsStrip(
                            currentUserId = currentUserId,
                            currentUsername = currentUsername.ifBlank { "Creator" },
                            currentUserAvatar = userAvatar,
                            onNavigateToProfile = { uid ->
                                if (uid.isNotBlank()) onNavigate("profile/${android.net.Uri.encode(uid.trim())}")
                            }
                        )
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                            thickness = 0.5.dp
                        )
                    }

                    // Skeleton Loading when videos are empty
                    if (isLoading && displayedVideos.isEmpty()) {
                        items(count = 3, key = { "home_skeleton_$it" }, contentType = { "post_skeleton" }) {
                            HomeFeedPostSkeleton()
                        }
                    } else if (!isLoading && displayedVideos.isEmpty()) {
                        // Polished Empty State
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CameraAlt,
                                                contentDescription = "Welcome to Social India",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "No posts yet • Upload your first Reel",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Follow creators, see what's trending, or be the first to share your Reel with friends!",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            lineHeight = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Button(
                                                onClick = { onNavigate("upload") },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Upload Reel", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                            OutlinedButton(
                                                onClick = { onNavigate("discover") },
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Explore", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            }
                                         }
                                    }
                                }
                            }
                        }
                        if (feedSuggestedUsers.isNotEmpty()) {
                            item {
                                QuickFollowHorizontalSection(
                                    suggestedUsers = feedSuggestedUsers,
                                    onNavigateToProfile = { uid ->
                                        if (uid.isNotBlank()) onNavigate("profile/${android.net.Uri.encode(uid.trim())}")
                                    },
                                    onSeeAllClick = { onNavigate("discover_people") },
                                    onDismissUser = { dismissedUid ->
                                        scope.launch {
                                            RepositoryManager.socialRepository.dismissSuggestion(dismissedUid)
                                            feedSuggestedUsers = feedSuggestedUsers.filter { it.user.id != dismissedUid }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    items(
                        count = displayedVideos.size,
                        key = { index -> "${displayedVideos[index].id}_$index" },
                        contentType = { "video_post_item" }
                    ) { index ->
                        val video = displayedVideos[index]
                        com.example.ui.motion.FeedItemReveal(key = video.id) {
                            HomeVideoPostItem(
                                video = video,
                                isPlaying = video.id == playingVideoId,
                                onLike = { viewModel.toggleLike(video.id) },
                                onLikesClick = { selectedVideoForLikes = video },
                                onCommentClick = { selectedVideoForComments = video },
                                onShareClick = { selectedVideoForShare = video },
                                onSaveClick = { viewModel.toggleSave(video.id) },
                                onRepostClick = {
                                    val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
                                    val isExLocked = (video.isExclusiveContent || video.isPaidPost) &&
                                        video.postUnlockPrice >= 10.0 &&
                                        video.userId != currentUserId &&
                                        !com.example.repository.EntitlementRepository.hasAccessFast(
                                            userId = currentUserId,
                                            creatorId = video.userId,
                                            contentId = video.id,
                                            isExclusive = true,
                                            isPaidPost = true
                                        )
                                    if (isExLocked) {
                                        android.widget.Toast.makeText(context, "Unlock this exclusive content first to repost", android.widget.Toast.LENGTH_SHORT).show()
                                    } else if (currentUserId.isNotEmpty()) {
                                        val postRef = com.example.model.RepositoryManager.firestore.collection("posts").document(video.id)
                                        postRef.get().addOnSuccessListener { snapshot ->
                                            if (snapshot.exists()) {
                                                val currentRepostedUsers = snapshot.get("repostedByUserIds") as? List<*> ?: emptyList<Any>()
                                                val newRepostedUsers = currentRepostedUsers.filterIsInstance<String>().toMutableList()
                                                val isCurrentlyReposted = newRepostedUsers.contains(currentUserId)
                                                
                                                if (isCurrentlyReposted) {
                                                    newRepostedUsers.remove(currentUserId)
                                                } else {
                                                    newRepostedUsers.add(currentUserId)
                                                }
                                                
                                                com.example.model.RepositoryManager.firestore.runTransaction { transaction ->
                                                    transaction.update(postRef, "repostedByUserIds", newRepostedUsers)
                                                    transaction.update(postRef, "repostsCount", newRepostedUsers.size)
                                                }.addOnSuccessListener {
                                                    android.widget.Toast.makeText(context, if (isCurrentlyReposted) "Repost removed" else "Reposted successfully", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                onShowRepostToast = { undoAction ->
                                    homeRepostToastTarget = video to undoAction
                                },
                                onOptionsClick = { selectedVideoForOptions = video },
                                onProfileClick = {
                                    val uid = video.userId.ifBlank { video.user.id }.trim()
                                    if (uid.isNotBlank()) {
                                        onNavigate("profile/${android.net.Uri.encode(uid)}")
                                    }
                                },
                                onMentionClick = { targetUid ->
                                    val uid = targetUid.trim()
                                    if (uid.isNotBlank()) {
                                        onNavigate("profile/${android.net.Uri.encode(uid)}")
                                    }
                                },
                                onReelsClick = {
                                    com.example.viewmodel.VideoViewModel.selectedVideoIdToPlay = video.id
                                    onNavigate("reels")
                                },
                                onAudioClick = { trackId, trackTitle, trackArtist, trackCover ->
                                    val eTrackId = java.net.URLEncoder.encode(if (trackId.isEmpty()) "unknown" else trackId, "UTF-8")
                                    val eTrackName = java.net.URLEncoder.encode(if (trackTitle.isEmpty()) "Original Audio" else trackTitle, "UTF-8")
                                    val eArtistName = java.net.URLEncoder.encode(if (trackArtist.isEmpty()) "Original Creator" else trackArtist, "UTF-8")
                                    val eTrackImage = java.net.URLEncoder.encode(if (trackCover.isEmpty()) "empty" else trackCover, "UTF-8")
                                    onNavigate("audio_details/$eTrackId/$eTrackName/$eArtistName/$eTrackImage")
                                }
                            )
                        }

                            if (activeHomeAds.isNotEmpty() && (index + 1) % 4 == 0) {
                                val adIdx = ((index + 1) / 4 - 1) % activeHomeAds.size
                                val ad = activeHomeAds[adIdx]
                                if (!com.example.repository.AdvertisingRepository.isAdHidden(ad.adId)) {
                                    SponsoredPostCard(
                                        ad = ad,
                                        currentUserId = currentUserId,
                                        onNavigateToProfile = { uid ->
                                            if (uid.isNotBlank()) onNavigate("profile/${android.net.Uri.encode(uid.trim())}")
                                        },
                                        onAdHidden = {
                                            activeHomeAds = activeHomeAds.filter { it.adId != ad.adId }
                                        }
                                    )
                                }
                            }
                        }

                        if (displayedVideos.isNotEmpty() && isLoading) {
                            item(key = "home_pagination_skeleton", contentType = "post_skeleton") {
                                HomeFeedPostSkeleton()
                            }
                        }
                    }
                }

            // Smooth Hide-on-scroll-down / Show-on-scroll-up Top App Bar (Zero-recomposition scroll tracking)
            var isTopBarVisible by remember { mutableStateOf(true) }

            LaunchedEffect(listState, displayedVideos) {
                var prevIndex = listState.firstVisibleItemIndex
                var prevOffset = listState.firstVisibleItemScrollOffset
                var lastPrefetchedIdx = -1
                val imageLoader = coil.Coil.imageLoader(context)
                androidx.compose.runtime.snapshotFlow {
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                }.collect { (currentIndex, currentOffset) ->
                    if (currentIndex == 0 && currentOffset < 80) {
                        if (!isTopBarVisible) isTopBarVisible = true
                    } else if (currentIndex > prevIndex || (currentIndex == prevIndex && currentOffset > prevOffset + 22)) {
                        if (isTopBarVisible) isTopBarVisible = false
                    } else if (currentIndex < prevIndex || (currentIndex == prevIndex && currentOffset < prevOffset - 16)) {
                        if (!isTopBarVisible) isTopBarVisible = true
                    }
                    if (currentIndex != prevIndex) {
                        com.example.ui.GlobalState.saveScroll("home_feed", currentIndex, currentOffset)
                        // Prefetch next 3 post images/posters & avatars into Coil cache ahead of scroll
                        val videoStart = (currentIndex - 1).coerceAtLeast(0)
                        if (videoStart != lastPrefetchedIdx && displayedVideos.isNotEmpty()) {
                            lastPrefetchedIdx = videoStart
                            val endIdx = (videoStart + 3).coerceAtMost(displayedVideos.lastIndex)
                            for (i in videoStart..endIdx) {
                                val nextPost = displayedVideos[i]
                                val rawImg = if (nextPost.isMediaAnImage) {
                                    nextPost.thumbnailUrl.takeIf { it.isNotBlank() } ?: nextPost.videoUrls.firstOrNull() ?: nextPost.videoUrl
                                } else {
                                    com.example.utils.MediaResolver.resolveVideoPosterUrl(nextPost.videoUrl, nextPost.thumbnailUrl)
                                }
                                if (rawImg.isNotBlank()) {
                                    imageLoader.enqueue(
                                        com.example.utils.ExoPlayerUtils.buildFastPosterRequest(context, rawImg, 900, 1125)
                                    )
                                }
                            }
                        }
                    }
                    prevIndex = currentIndex
                    prevOffset = currentOffset
                }
            }

            // Use State<Dp> (not delegated `by`) so animation ticks ONLY inside graphicsLayer (zero HomeFeed recomposition)
            val topBarOffsetYState = animateDpAsState(
                targetValue = if (isTopBarVisible) 0.dp else (-100).dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "topBarScrollOffset"
            )

            // Sleek Instagram-Style Sticky Top App Bar (60fps GPU transform layer)
            com.example.ui.components.LiquidGlassTopHeader(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = topBarOffsetYState.value.toPx()
                    },
                isDark = isAppDark
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // App Logo / Title with Quick Switcher & Refresh
                    var showFeedSwitcherMenu by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .bounceClick()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    com.example.utils.SoundManager.playClickSound()
                                    showFeedSwitcherMenu = true
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Social India",
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Cursive,
                                color = contentTextColor
                            )
                            if (selectedFeedMode != "FOR_YOU") {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0095F6).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (selectedFeedMode == "FOLLOWING") "Following" else "Favorites",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0095F6),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Feed Options",
                                tint = contentTextColor,
                                modifier = Modifier.padding(start = 4.dp).size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showFeedSwitcherMenu,
                            onDismissRequest = { showFeedSwitcherMenu = false },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .widthIn(min = 230.dp)
                        ) {
                            // Feed Mode Switcher: For You (Algorithmic), Following, Favorites
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = if (selectedFeedMode == "FOR_YOU") Color(0xFF0095F6) else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("For You", fontWeight = if (selectedFeedMode == "FOR_YOU") FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                                        }
                                        if (selectedFeedMode == "FOR_YOU") {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF0095F6), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    selectedFeedMode = "FOR_YOU"
                                    showFeedSwitcherMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PeopleOutline, contentDescription = null, tint = if (selectedFeedMode == "FOLLOWING") Color(0xFF0095F6) else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("Following", fontWeight = if (selectedFeedMode == "FOLLOWING") FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                                        }
                                        if (selectedFeedMode == "FOLLOWING") {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF0095F6), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    selectedFeedMode = "FOLLOWING"
                                    showFeedSwitcherMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.StarBorder, contentDescription = null, tint = if (selectedFeedMode == "FAVORITES") Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("Favorites", fontWeight = if (selectedFeedMode == "FAVORITES") FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                                        }
                                        if (selectedFeedMode == "FAVORITES") {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    selectedFeedMode = "FAVORITES"
                                    showFeedSwitcherMenu = false
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = Color(0xFFFE2C55), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                "Social India Store 🛍️",
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                "Buy & explore products & merch",
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    showFeedSwitcherMenu = false
                                    onNavigate("store")
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            "Refresh Realtime Feed",
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                },
                                onClick = {
                                    showFeedSwitcherMenu = false
                                    viewModel.refreshVideos()
                                }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            "Switch Account",
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                    }
                                },
                                onClick = {
                                    showFeedSwitcherMenu = false
                                    GlobalState.showAccountSwitcher.value = true
                                }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Explore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            "Explore Creators",
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                    }
                                },
                                onClick = {
                                    showFeedSwitcherMenu = false
                                    onNavigate("discover_people")
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            val isLiteActive = com.example.utils.NetworkMonitor.isLiteModeActive()
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Bolt,
                                                contentDescription = null,
                                                tint = if (isLiteActive) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(19.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = if (isLiteActive) "Data Saver / Lite Mode: ON" else "Data Saver / Lite Mode",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = "Fast compressed streams for slow networks",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                                )
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    val nextState = !com.example.utils.NetworkMonitor.isDataSaverEnabled.value
                                    com.example.utils.NetworkMonitor.setDataSaverEnabled(context, nextState)
                                    showFeedSwitcherMenu = false
                                    android.widget.Toast.makeText(
                                        context,
                                        if (nextState) "Smart Data Saver / Lite Mode Enabled ⚡" else "Data Saver Disabled (Full HD Mode)",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }

                    // Uniform 22.dp Thin-Stroke Action Icons (Leaderboard, AI Sparkle, Plus/Create, Heart, Messenger)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { onNavigate("leaderboard") },
                            modifier = Modifier.size(36.dp).bounceClick()
                        ) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = "Leaderboard",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = { onNavigate("ai_hub") },
                            modifier = Modifier.size(36.dp).bounceClick()
                        ) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = "AI Hub",
                                tint = contentTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (!RepositoryManager.isRealUserLoggedIn()) {
                                    android.widget.Toast.makeText(context, "Please log in to create a post or reel", android.widget.Toast.LENGTH_SHORT).show()
                                    onNavigate("auth")
                                } else {
                                    onNavigate("upload")
                                }
                            },
                            modifier = Modifier.size(36.dp).bounceClick()
                        ) {
                            Icon(
                                Icons.Outlined.AddBox,
                                contentDescription = "Create",
                                tint = contentTextColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = { onNavigate("inbox/notifications") },
                            modifier = Modifier.size(36.dp).bounceClick()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Activity",
                                    tint = contentTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                com.example.ui.motion.AnimatedBadgePop(
                                    count = unreadNotificationsCount,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 6.dp, y = (-5).dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { onNavigate("inbox/messages") },
                            modifier = Modifier.size(36.dp).bounceClick()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    com.example.ui.MessengerIcon,
                                    contentDescription = "Messages",
                                    tint = contentTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                com.example.ui.motion.AnimatedBadgePop(
                                    count = unreadMessagesCount,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 6.dp, y = (-5).dp)
                                )
                            }
                        }
                    }
                }
            }

            // Fixed overlay Repost Toast positioned above the bottom nav (never shifts feed posts)
            val currentUserRealtimeHome by com.example.model.RepositoryManager.currentUserState.collectAsStateWithLifecycle()
            RepostFloatingPill(
                visible = homeRepostToastTarget != null,
                userAvatarUrl = currentUserRealtimeHome?.avatarUrl ?: RepositoryManager.currentUserAvatar,
                onAddThoughtClick = {
                    val target = homeRepostToastTarget?.first
                    homeRepostToastTarget = null
                    if (target != null) {
                        selectedVideoForRepost = target
                    }
                },
                onUndoClick = {
                    val undo = homeRepostToastTarget?.second
                    homeRepostToastTarget = null
                    undo?.invoke()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 72.dp)
            )
        }

    selectedVideoForOptions?.let { video ->
        HomeMoreOptionsBottomSheet(
            video = video,
            onDismiss = { selectedVideoForOptions = null },
            onShareClick = {
                val target = video
                selectedVideoForOptions = null
                selectedVideoForShare = target
            },
            onSaveClick = {
                viewModel.toggleSave(video.id)
            },
            onRepostClick = {
                val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
                val isExLocked = (video.isExclusiveContent || video.isPaidPost) &&
                    video.postUnlockPrice >= 10.0 &&
                    video.userId != currentUserId &&
                    !com.example.repository.EntitlementRepository.hasAccessFast(
                        userId = currentUserId,
                        creatorId = video.userId,
                        contentId = video.id,
                        isExclusive = true,
                        isPaidPost = true
                    )
                if (isExLocked) {
                    android.widget.Toast.makeText(context, "Unlock this exclusive content first to repost", android.widget.Toast.LENGTH_SHORT).show()
                } else if (currentUserId.isNotEmpty()) {
                    val postRef = com.example.model.RepositoryManager.firestore.collection("posts").document(video.id)
                    postRef.get().addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            val currentRepostedUsers = snapshot.get("repostedByUserIds") as? List<*> ?: emptyList<Any>()
                            val newRepostedUsers = currentRepostedUsers.filterIsInstance<String>().toMutableList()
                            val isCurrentlyReposted = newRepostedUsers.contains(currentUserId)
                            if (isCurrentlyReposted) newRepostedUsers.remove(currentUserId) else newRepostedUsers.add(currentUserId)
                            com.example.model.RepositoryManager.firestore.runTransaction { transaction ->
                                transaction.update(postRef, "repostedByUserIds", newRepostedUsers)
                                transaction.update(postRef, "repostsCount", newRepostedUsers.size)
                            }.addOnSuccessListener {
                                android.widget.Toast.makeText(context, if (isCurrentlyReposted) "Repost removed" else "Reposted successfully", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                selectedVideoForOptions = null
            },
            onDeleteSuccess = {
                viewModel.deleteVideoLocally(video.id)
                viewModel.refreshVideos()
                selectedVideoForOptions = null
            },
            onEditCaptionSuccess = { newCaption ->
                viewModel.updateVideoCaptionLocally(video.id, newCaption)
            }
        )
    }

    selectedVideoForShare?.let { video ->
        ShareBottomSheet(
            video = video,
            onDismiss = { selectedVideoForShare = null },
            onAddToStory = { sharedVideo ->
                selectedVideoForShare = null
                val viewerUid = com.example.model.RepositoryManager.getCurrentUserId()
                val isExLocked = (sharedVideo.isExclusiveContent || sharedVideo.isPaidPost) &&
                    sharedVideo.postUnlockPrice >= 10.0 &&
                    sharedVideo.userId != viewerUid &&
                    !com.example.repository.EntitlementRepository.hasAccessFast(
                        userId = viewerUid,
                        creatorId = sharedVideo.userId,
                        contentId = sharedVideo.id,
                        isExclusive = true,
                        isPaidPost = true
                    )
                if (!RepositoryManager.isRealUserLoggedIn()) {
                    android.widget.Toast.makeText(context, "Please log in to share to your story", android.widget.Toast.LENGTH_SHORT).show()
                    onNavigate("auth")
                } else if (isExLocked) {
                    android.widget.Toast.makeText(context, "Unlock this exclusive content before adding to your story", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    storyVideoToShare = sharedVideo
                }
            }
        )
    }

    selectedVideoForComments?.let { video ->
        ModernCommentBottomSheet(
            video = video,
            onDismiss = { selectedVideoForComments = null },
            currentUserId = currentUserId,
            onProfileClick = { userId ->
                selectedVideoForComments = null
                if (userId.isNotBlank()) onNavigate("profile/${android.net.Uri.encode(userId.trim())}")
            }
        )
    }

    selectedVideoForLikes?.let { video ->
        PostLikersBottomSheet(
            postId = video.id,
            onDismiss = { selectedVideoForLikes = null },
            onNavigateToProfile = { userId ->
                selectedVideoForLikes = null
                if (userId.isNotBlank()) onNavigate("profile/${android.net.Uri.encode(userId.trim())}")
            }
        )
    }

    selectedVideoForRepost?.let { video ->
        RepostBottomSheet(
            video = video,
            onDismiss = { selectedVideoForRepost = null },
            onConfirmRepost = { note ->
                scope.launch {
                    val success = RepositoryManager.socialRepository.repostContent(
                        contentId = video.id,
                        ownerUid = video.userId,
                        contentType = "post",
                        note = note,
                        currentUserId = currentUserId,
                        currentUsername = currentUsername,
                        currentUserAvatar = userAvatar
                    )
                    if (success) {
                        video.isReposted = true
                        video.repostsCount++
                        android.widget.Toast.makeText(context, "Reposted successfully", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Couldn't repost. Try again.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    selectedVideoForRepost = null
                }
            }
        )
    }

    selectedStoryGroup?.let { groupWithIndex ->
        StoryPlayerOverlay(
            stories = groupWithIndex.first,
            initialIndex = groupWithIndex.second,
            onDismiss = { 
                com.example.utils.GlobalMediaSessionManager.releaseFocus(com.example.utils.MediaSource.STORY, context)
                selectedStoryGroup = null 
            },
            onAddStoryClick = {
                if (!RepositoryManager.isRealUserLoggedIn()) {
                    android.widget.Toast.makeText(context, "Please log in to share a story", android.widget.Toast.LENGTH_SHORT).show()
                    selectedStoryGroup = null
                    onNavigate("auth")
                } else {
                    showCreateStoryDialog = true
                }
            },
            onProfileClick = { userId ->
                com.example.utils.GlobalMediaSessionManager.releaseFocus(com.example.utils.MediaSource.STORY, context)
                selectedStoryGroup = null
                if (userId.isNotBlank()) onNavigate("profile/${android.net.Uri.encode(userId.trim())}")
            },
            onNavigateToReel = { videoId ->
                com.example.utils.GlobalMediaSessionManager.releaseFocus(com.example.utils.MediaSource.STORY, context)
                selectedStoryGroup = null
                onNavigate("post_detail/$videoId")
            }
        )
    }
}

private val HomePostRingGradient = Brush.linearGradient(
    listOf(
        Color(0xFFF09433),
        Color(0xFFE6683C),
        Color(0xFFDC2743),
        Color(0xFFCC2366),
        Color(0xFFBC1888)
    )
)

@Composable
fun HomeVideoPostItem(
    video: com.example.model.Video,
    isPlaying: Boolean,
    onLike: () -> Unit,
    onLikesClick: () -> Unit = {},
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit = {},
    onRepostClick: () -> Unit,
    onShowRepostToast: ((() -> Unit) -> Unit)? = null,
    onOptionsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onMentionClick: (String) -> Unit = {},
    onReelsClick: () -> Unit,
    onAudioClick: (String, String, String, String) -> Unit = { _, _, _, _ -> }
) {
    var isRealTimeLiked by remember { mutableStateOf(video.isLiked) }
    var realTimeLikesCount by remember { mutableStateOf(video.likesCount) }
    var isRealTimeSaved by remember { mutableStateOf(video.isSaved) }
    
    val currentUid = remember { com.example.model.RepositoryManager.getCurrentUserId() }
    val coroutineScope = rememberCoroutineScope()
    var localInvitationStatus by remember(video.id) {
        mutableStateOf(if (video.pendingCollaboratorIds.contains(currentUid)) "PENDING" else "NONE")
    }
    var isAcceptedLocally by remember(video.id) {
        mutableStateOf(video.collaboratorIds.contains(currentUid))
    }
    
    LaunchedEffect(video.isLiked) {
        isRealTimeLiked = video.isLiked
    }
    LaunchedEffect(video.isSaved) {
        isRealTimeSaved = video.isSaved
    }
    
    val isAppDark = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f
    val postRingGradient = HomePostRingGradient
    val soundTitle = if (!video.musicTrack.isNullOrEmpty()) video.musicTrack else "Original Audio - ${video.user.username.ifEmpty { "Creator" }}"
    val soundArtist = if (!video.musicTrackArtist.isNullOrEmpty()) video.musicTrackArtist else video.user.username.ifEmpty { "Creator" }
    val soundCover = if (!video.musicTrackImageUrl.isNullOrEmpty()) video.musicTrackImageUrl else video.user.avatarUrl
    val soundId = if (!video.musicTrackId.isNullOrEmpty()) video.musicTrackId else "orig_${video.id}"

    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .wrapContentWidth(androidx.compose.ui.Alignment.CenterHorizontally)
            .widthIn(max = 600.dp)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .padding(bottom = 14.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            val isOwnPost = video.userId == currentUid && currentUid.isNotEmpty()
            val currentUserRealtime = if (isOwnPost) {
                com.example.model.RepositoryManager.currentUserState.collectAsStateWithLifecycle().value
            } else null
            val effectiveUsername = if (isOwnPost && currentUserRealtime?.username?.isNotEmpty() == true) {
                currentUserRealtime.username
            } else if (video.user.username.isNotEmpty() && video.user.username != "User") {
                video.user.username
            } else if (video.userId.isNotEmpty()) {
                "user_" + video.userId.take(5)
            } else {
                "Creator"
            }

            val effectiveAvatar = if (isOwnPost && currentUserRealtime?.avatarUrl?.isNotEmpty() == true) {
                currentUserRealtime.avatarUrl
            } else {
                video.user.avatarUrl
            }
            val headerContext = androidx.compose.ui.platform.LocalContext.current
            val avatarModel = effectiveAvatar
            val avatarRequest = remember(avatarModel) {
                if (avatarModel.isNotBlank()) {
                    com.example.utils.ExoPlayerUtils.buildFastPosterRequest(headerContext, avatarModel, 128, 128)
                } else null
            }

            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = androidx.compose.ui.Modifier
                    .weight(1f)
            ) {
                // 34.dp circular avatar with Instagram gradient story ring
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .size(34.dp)
                        .bounceClick()
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable { onProfileClick() }
                        .border(1.8.dp, postRingGradient, androidx.compose.foundation.shape.CircleShape)
                        .padding(2.8.dp)
                ) {
                    coil.compose.AsyncImage(
                        model = avatarRequest,
                        contentDescription = "Avatar",
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .clip(androidx.compose.foundation.shape.CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(10.dp))
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.weight(1f)
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = androidx.compose.ui.Modifier.clickable { onProfileClick() }
                    ) {
                        androidx.compose.material3.Text(
                            text = effectiveUsername,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val isAuthorVerified = remember(video.user, video.userId) {
                            val u = video.user
                            !u.verificationSuspended && (
                                u.isVerified ||
                                u.verificationStatus == "approved" ||
                                u.verificationStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE
                            )
                        }
                        if (isAuthorVerified) {
                            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(4.dp))
                            VerificationBadge(
                                user = video.user,
                                modifier = androidx.compose.ui.Modifier.size(14.dp)
                            )
                        }
                        val collabDisplayList = remember(video.collaborators, isAcceptedLocally) {
                            val names = video.collaborators.map { it.username }.toMutableList()
                            if (isAcceptedLocally && video.collaborators.none { it.id == currentUid }) {
                                names.add(0, "You")
                            }
                            names
                        }
                        if (collabDisplayList.isNotEmpty()) {
                            androidx.compose.material3.Text(
                                text = " and ${collabDisplayList.joinToString()}",
                                fontSize = 13.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Instagram-style Audio row: music note + animated equalizer + seamless looping marquee while playing
                    val audioTint = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
                    com.example.ui.motion.InstagramAudioRow(
                        audioTitle = soundTitle,
                        isPlaying = isPlaying,
                        iconTint = audioTint,
                        textColor = audioTint,
                        iconSize = 12.dp,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.5.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        ),
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        onClick = { onAudioClick(soundId, soundTitle, soundArtist, soundCover) }
                    )
                }
            }
            // Clean rounded Follow / Following pill button (border-radius: 8.dp)
            if (currentUid.isNotEmpty() && currentUid != video.user.id) {
                SocialFollowButton(
                    targetUser = video.user,
                    isCompact = true,
                    modifier = androidx.compose.ui.Modifier.padding(end = 4.dp)
                )
            }
            androidx.compose.material3.IconButton(
                onClick = onOptionsClick,
                modifier = androidx.compose.ui.Modifier.size(32.dp).bounceClick()
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    modifier = androidx.compose.ui.Modifier.size(20.dp)
                )
            }
        }

        if (localInvitationStatus == "PENDING" && currentUid.isNotEmpty()) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = androidx.compose.ui.Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.GroupAdd,
                        contentDescription = null,
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = androidx.compose.ui.Modifier.size(20.dp)
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
                    androidx.compose.material3.Text(
                        text = "@${video.user.username} invited you to collaborate",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = androidx.compose.ui.Modifier.weight(1f)
                    )
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            localInvitationStatus = "DECLINED"
                            coroutineScope.launch {
                                com.example.model.RepositoryManager.postRepository.declineCollaboration(video.id)
                            }
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = androidx.compose.ui.Modifier.height(30.dp)
                    ) {
                        androidx.compose.material3.Text("Decline", fontSize = 11.sp)
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(6.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            localInvitationStatus = "ACCEPTED"
                            isAcceptedLocally = true
                            coroutineScope.launch {
                                com.example.model.RepositoryManager.postRepository.acceptCollaboration(video.id)
                            }
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = androidx.compose.ui.Modifier.height(30.dp)
                    ) {
                        androidx.compose.material3.Text("Accept", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }
        
        val isImage = video.isMediaAnImage

        val context = androidx.compose.ui.platform.LocalContext.current
        var playerState by remember { mutableIntStateOf(androidx.media3.common.Player.STATE_IDLE) }
        var exoPlayer by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
        var isPlayerError by remember(video.videoUrl, isPlaying) { mutableStateOf(false) }
        var hasRenderedFirstFrame by remember(video.videoUrl, isPlaying) { mutableStateOf(false) }
        val isSoundEnabled by com.example.utils.GlobalFeedAudioState.isSoundEnabled.collectAsStateWithLifecycle()
        val isMuted = !isSoundEnabled
        var showSoundToast by remember { mutableStateOf(false) }
        var speakerBounceTrigger by remember { mutableStateOf(false) }
        val speakerScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (speakerBounceTrigger) 1.25f else 1.0f,
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 400f),
            label = "speakerScale"
        )
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

        val resolvedMusicUrl = remember(video.musicTrackUrl, video.musicTrack) {
            com.example.api.MusicClient.resolveAudioUrlForTrack(video.musicTrackUrl, video.musicTrack)
        }
        val hasMusic = !resolvedMusicUrl.isNullOrBlank()
        var manualPlayRequested by remember(video.id) { mutableStateOf(false) }
        val effectiveIsPlaying = isPlaying || (isImage && hasMusic && manualPlayRequested)

        LaunchedEffect(isPlaying) {
            if (!isPlaying) {
                manualPlayRequested = false
            }
        }

        val toggleSound: () -> Unit = {
            if (isImage && hasMusic && !effectiveIsPlaying) {
                manualPlayRequested = true
            }
            val newSoundState = com.example.utils.GlobalFeedAudioState.toggleSound()
            exoPlayer?.volume = if (newSoundState) 1f else 0f
            if (newSoundState && exoPlayer?.isPlaying == false) {
                exoPlayer?.play()
            }
            speakerBounceTrigger = !speakerBounceTrigger
            showSoundToast = true
            com.example.utils.SoundManager.playClickSound()
        }

        androidx.compose.runtime.LaunchedEffect(isSoundEnabled) {
            if (exoPlayer != null) {
                exoPlayer?.volume = if (isSoundEnabled) 1f else 0f
            }
        }

        LaunchedEffect(showSoundToast) {
            if (showSoundToast) {
                kotlinx.coroutines.delay(1000)
                showSoundToast = false
            }
        }

        var retryAttempt by remember(video.id) { mutableIntStateOf(0) }
        var autoRetryCount by remember(video.id, video.videoUrl) { mutableIntStateOf(0) }
        var isAutoRetrying by remember(video.id, video.videoUrl) { mutableStateOf(false) }
        var isManuallyPaused by remember(video.id) { mutableStateOf(false) }
        var detectedVideoRatio by remember(video.id) {
            mutableFloatStateOf(
                when {
                    video.aspectRatio > 0f -> video.aspectRatio
                    video.videoWidth > 0 && video.videoHeight > 0 -> video.videoWidth.toFloat() / video.videoHeight.toFloat()
                    else -> 0f
                }
            )
        }

        val unlockedKeys by com.example.repository.EntitlementRepository.unlockedKeysFlow.collectAsStateWithLifecycle()
        val purchaseStatusMap by com.example.repository.EntitlementRepository.purchaseStatusMapFlow.collectAsStateWithLifecycle()
        val purchaseRec = purchaseStatusMap[video.id]
        val isAuthorExclusive = currentUid.isNotEmpty() && (video.userId == currentUid)
        val isExclusivePaidPost = (video.isExclusiveContent || video.isPaidPost) && video.postUnlockPrice >= 10.0
        val isUnlockedExclusive = remember(video.id, currentUid, unlockedKeys, isAuthorExclusive, isExclusivePaidPost) {
            if (!isExclusivePaidPost || isAuthorExclusive) true
            else unlockedKeys.contains("${currentUid}_${video.id}") ||
                com.example.repository.EntitlementRepository.hasAccessFast(
                    userId = currentUid,
                    creatorId = video.userId,
                    contentId = video.id,
                    isExclusive = true,
                    isPaidPost = true
                )
        }
        val isExclusiveLocked = isExclusivePaidPost && !isUnlockedExclusive
        var vaultPayload by remember(video.id) {
            mutableStateOf(com.example.repository.ExclusiveContentRepository.getCachedVaultMedia(video.id))
        }
        var isResolvingVault by remember(video.id) { mutableStateOf(false) }
        var showExclusiveUnlockSheet by remember(video.id) { mutableStateOf(false) }
        var showExclusiveReportDialog by remember(video.id) { mutableStateOf(false) }

        LaunchedEffect(video.id, isUnlockedExclusive, video.hasVaultMedia) {
            if (isExclusivePaidPost && isUnlockedExclusive && video.hasVaultMedia && vaultPayload == null) {
                isResolvingVault = true
                vaultPayload = com.example.repository.ExclusiveContentRepository.resolveUnlockedMedia(video.id)
                isResolvingVault = false
            }
        }

        if (isExclusivePaidPost && effectiveIsPlaying) {
            com.example.utils.ExclusiveScreenSecurityEffect(enabled = true)
        }

        val activeMediaUrl = if (isExclusivePaidPost && !isUnlockedExclusive) {
            ""
        } else {
            vaultPayload?.mediaUrl?.ifBlank { video.videoUrl } ?: video.videoUrl
        }
        val activeThumbUrl = if (isExclusivePaidPost && !isUnlockedExclusive) {
            video.blurredThumbnailUrl.ifBlank { video.thumbnailUrl }
        } else {
            vaultPayload?.thumbnailUrl?.ifBlank { video.thumbnailUrl } ?: video.thumbnailUrl
        }

        LaunchedEffect(autoRetryCount, isAutoRetrying, activeMediaUrl, effectiveIsPlaying, isExclusiveLocked) {
            if (isAutoRetrying && autoRetryCount in 1..3 && effectiveIsPlaying && !isExclusiveLocked && !isImage) {
                val delayMs = com.example.utils.VideoTranscoder.computeRetryBackoffMs(autoRetryCount)
                kotlinx.coroutines.delay(delayMs)
                isAutoRetrying = false
                isPlayerError = false
                retryAttempt++
            }
        }

        LaunchedEffect(effectiveIsPlaying, activeMediaUrl, resolvedMusicUrl, isImage, retryAttempt, isUnlockedExclusive) {
            if (!effectiveIsPlaying || isExclusiveLocked) {
                showSoundToast = false
                hasRenderedFirstFrame = false
                isManuallyPaused = false
            }
            if (effectiveIsPlaying && !isExclusiveLocked) {
                // Wait 90ms for fast scroll flings to pass before touching ExoPlayer on the main thread
                if (!manualPlayRequested && retryAttempt == 0) {
                    kotlinx.coroutines.delay(90)
                }
                val mediaToPlayUrl = if (!isImage && activeMediaUrl.isNotEmpty()) {
                    activeMediaUrl
                } else if (isImage && !resolvedMusicUrl.isNullOrEmpty()) {
                    resolvedMusicUrl
                } else null

                if (mediaToPlayUrl != null) {
                    try {
                        com.example.utils.ExoPlayerUtils.releaseToPool(exoPlayer)
                        val candidateUrls = if (isImage) {
                            listOf(mediaToPlayUrl)
                        } else {
                            com.example.utils.MediaResolver.getFallbackVideoUrls(mediaToPlayUrl).ifEmpty {
                                listOfNotNull(com.example.utils.MediaResolver.resolveVideoUrl(mediaToPlayUrl).takeIf { it.isNotBlank() })
                            }
                        }
                        val playUrl = candidateUrls.firstOrNull().orEmpty()
                        var fallbackUrlIndex = 0
                        val player = (if (isImage) com.example.utils.ExoPlayerUtils.createExoPlayer(context, isMusic = true) else com.example.utils.ExoPlayerUtils.acquireRecycledPlayer(context)).apply {
                            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                            volume = if (com.example.utils.GlobalFeedAudioState.isSoundOn()) 1f else 0f
                            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                                .setContentType(if (isImage) androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC else androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                                .build()
                            setAudioAttributes(audioAttributes, false)
                            if (!isImage) {
                                com.example.utils.ExoPlayerUtils.applyLiteModeConstraints(this)
                            }
                            com.example.utils.ExoPlayerUtils.bindItemListener(this, object : androidx.media3.common.Player.Listener {
                                override fun onRenderedFirstFrame() {
                                    hasRenderedFirstFrame = true
                                    isPlayerError = false
                                    isAutoRetrying = false
                                    autoRetryCount = 0
                                }
                                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                                    if (videoSize.width > 0 && videoSize.height > 0) {
                                        detectedVideoRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                                        if (playbackState == androidx.media3.common.Player.STATE_READY) {
                                            hasRenderedFirstFrame = true
                                            isPlayerError = false
                                            isAutoRetrying = false
                                            autoRetryCount = 0
                                        }
                                    }
                                }
                                override fun onPlaybackStateChanged(state: Int) {
                                    playerState = state
                                    if (state == androidx.media3.common.Player.STATE_READY) {
                                        isPlayerError = false
                                        isAutoRetrying = false
                                        if (isImage || videoSize.width > 0) {
                                            hasRenderedFirstFrame = true
                                            autoRetryCount = 0
                                        }
                                    }
                                }
                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                    android.util.Log.w("HomeFeedScreen", "ExoPlayer error: ${error.errorCodeName} - ${error.message}")
                                    if (!isImage && fallbackUrlIndex + 1 < candidateUrls.size) {
                                        fallbackUrlIndex++
                                        val nextUrl = candidateUrls[fallbackUrlIndex]
                                        try {
                                            com.example.utils.MediaResolver.extractGoogleDriveFileId(mediaToPlayUrl)?.let { fid ->
                                                com.example.utils.GoogleDrivePermissionRepairManager.repairPermissionAsync(fid)
                                            }
                                            val directItem = androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(nextUrl))
                                            setMediaItem(directItem)
                                            prepare()
                                            playWhenReady = !isManuallyPaused
                                            return
                                        } catch (_: Exception) {}
                                    }
                                    if (!isImage && autoRetryCount < 3) {
                                        autoRetryCount++
                                        isAutoRetrying = true
                                        isPlayerError = false
                                    } else {
                                        isAutoRetrying = false
                                        isPlayerError = true
                                    }
                                }
                            })
                            if (playUrl.isNotBlank()) {
                                if (!isImage) {
                                    val cachedSource = com.example.utils.VideoCacheManager.buildCachedMediaSource(context, playUrl)
                                    setMediaSource(cachedSource)
                                } else {
                                    val mediaItem = androidx.media3.common.MediaItem.Builder().setUri(android.net.Uri.parse(playUrl)).build()
                                    setMediaItem(mediaItem)
                                }
                                prepare()
                                playWhenReady = !isManuallyPaused
                            } else {
                                if (!isImage && autoRetryCount < 3) {
                                    autoRetryCount++
                                    isAutoRetrying = true
                                    isPlayerError = false
                                } else {
                                    isAutoRetrying = false
                                    isPlayerError = true
                                }
                            }
                        }
                        exoPlayer = player
                    } catch (e: Throwable) {
                        android.util.Log.d("HomeFeedScreen", "Handled player create: ${e.message}")
                    }
                } else {
                    exoPlayer?.pause()
                    com.example.utils.ExoPlayerUtils.releaseToPool(exoPlayer)
                    exoPlayer = null
                }
            } else {
                exoPlayer?.pause()
                com.example.utils.ExoPlayerUtils.releaseToPool(exoPlayer)
                exoPlayer = null
            }
        }
        
        DisposableEffect(lifecycleOwner, exoPlayer) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                    exoPlayer?.pause()
                } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && effectiveIsPlaying && !isManuallyPaused && com.example.utils.GlobalMediaSessionManager.isHomeFeedPlaybackAllowed.value) {
                    exoPlayer?.play()
                    exoPlayer?.volume = if (com.example.utils.GlobalFeedAudioState.isSoundOn()) 1f else 0f
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            val emergencyPause = {
                exoPlayer?.pause()
                exoPlayer?.volume = 0f
            }
            com.example.utils.GlobalMediaSessionManager.registerPauseCallback(emergencyPause)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                com.example.utils.GlobalMediaSessionManager.unregisterPauseCallback(emergencyPause)
            }
        }
        
        DisposableEffect(video.videoUrl, resolvedMusicUrl, effectiveIsPlaying) {
            onDispose {
                if (isImage) {
                    com.example.utils.ExoPlayerUtils.safeRelease(exoPlayer)
                } else {
                    com.example.utils.ExoPlayerUtils.releaseToPool(exoPlayer)
                }
                exoPlayer = null
            }
        }

        var selectedAspectMode by remember(video.id) {
            mutableStateOf(if (detectedVideoRatio > 0f) "Original" else "4:5")
        } // "Original", "4:5", "1:1", "16:9"
        val resolvedAspectRatio = when (selectedAspectMode) {
            "1:1" -> 1f
            "16:9" -> 16f / 9f
            "Original" -> if (detectedVideoRatio > 0f) detectedVideoRatio.coerceIn(0.75f, 1.91f) else (4f / 5f)
            else -> 4f / 5f
        }
        var showTaggedUsersOverlay by remember(video.id) { mutableStateOf(false) }
        var showSaveCollectionBanner by remember { mutableStateOf(false) }
        var savedCollectionName by remember { mutableStateOf("All Posts") }
        var showCollectionPickerDialog by remember { mutableStateOf(false) }

        LaunchedEffect(showSaveCollectionBanner) {
            if (showSaveCollectionBanner) {
                kotlinx.coroutines.delay(3500)
                showSaveCollectionBanner = false
            }
        }

        DoubleTapLikeBox(
            onDoubleTapLike = {
                if (isExclusivePaidPost && !isUnlockedExclusive) {
                    showExclusiveUnlockSheet = true
                    return@DoubleTapLikeBox
                }
                if (!isRealTimeLiked) {
                    isRealTimeLiked = true
                    realTimeLikesCount++
                    onLike()
                }
                if (!isImage && activeMediaUrl.isNotEmpty()) {
                    manualPlayRequested = true
                    isManuallyPaused = false
                    exoPlayer?.play()
                }
            },
            onSingleTap = {
                if (isExclusivePaidPost && !isUnlockedExclusive) {
                    showExclusiveUnlockSheet = true
                    return@DoubleTapLikeBox
                }
                if (!isImage || hasMusic) {
                    manualPlayRequested = true
                    toggleSound()
                }
                if (isImage) {
                    showTaggedUsersOverlay = !showTaggedUsersOverlay
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(resolvedAspectRatio)
                .clip(androidx.compose.ui.graphics.RectangleShape)
                .clipToBounds()
                .background(androidx.compose.ui.graphics.Color.Black)
        ) {
            val isPlayingOrReady = (playerState == androidx.media3.common.Player.STATE_READY || (exoPlayer?.isPlaying == true)) && (isImage || hasRenderedFirstFrame)
            val hasMedia = video.videoUrl.isNotEmpty() || video.videoUrls.isNotEmpty() || video.thumbnailUrl.isNotEmpty()

            // LAYER 1 (BOTTOM): Video Player Surface (attached immediately so frame decodes underneath poster)
            if (video.videoUrl.isNotEmpty() && !isImage && exoPlayer != null && !isPlayerError) {
                val targetResizeMode = if (selectedAspectMode == "Original" || selectedAspectMode == "16:9" || detectedVideoRatio >= 1.1f) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                } else {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        com.example.utils.ExoPlayerUtils.createTexturePlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = targetResizeMode
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            clipChildren = true
                            clipToPadding = true
                            clipToOutline = true
                            outlineProvider = android.view.ViewOutlineProvider.BOUNDS
                        }
                    },
                    update = { playerView ->
                        playerView.clipChildren = true
                        playerView.clipToPadding = true
                        playerView.clipToOutline = true
                        playerView.outlineProvider = android.view.ViewOutlineProvider.BOUNDS
                        if (playerView.resizeMode != targetResizeMode) {
                            playerView.resizeMode = targetResizeMode
                        }
                        if (playerView.player != exoPlayer) {
                            playerView.player = exoPlayer
                        }
                    },
                    onRelease = { playerView ->
                        playerView.player = null
                    },
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .clip(androidx.compose.ui.graphics.RectangleShape)
                        .clipToBounds()
                )
            }

            // LAYER 2 (MIDDLE): Poster / Carousel Overlay (covers player until STATE_READY, or on error)
            if (hasMedia && (!isPlayingOrReady || isImage || isPlayerError)) {
                if (isImage) {
                    val mediaList = if (video.videoUrls.isNotEmpty()) video.videoUrls.take(20) else listOf(video.thumbnailUrl.takeIf { it.isNotBlank() } ?: video.videoUrl)
                    if (mediaList.size > 1) {
                        val carouselPagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { mediaList.size })
                        Box(modifier = Modifier.fillMaxSize()) {
                            androidx.compose.foundation.pager.HorizontalPager(
                                state = carouselPagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { pageIndex ->
                                val rawPageUrl = mediaList[pageIndex]
                                val fallbackUrls = remember(rawPageUrl) {
                                    com.example.utils.MediaResolver.getFallbackImageUrls(rawPageUrl).ifEmpty { listOf(rawPageUrl) }
                                }
                                var urlIndex by remember(rawPageUrl) { mutableIntStateOf(0) }
                                var isImgFailed by remember(rawPageUrl) { mutableStateOf(false) }
                                val currentImgUrl = fallbackUrls.getOrElse(urlIndex) { rawPageUrl }
                                val imageReq = remember(currentImgUrl) {
                                    com.example.utils.ExoPlayerUtils.buildFastPosterRequest(
                                        context = context,
                                        url = currentImgUrl,
                                        widthPx = 900,
                                        heightPx = 1125,
                                        onError = {
                                            if (urlIndex + 1 < fallbackUrls.size) {
                                                urlIndex++
                                            } else {
                                                isImgFailed = true
                                            }
                                        }
                                    )
                                }
                                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF14151C))) {
                                    coil.compose.AsyncImage(
                                        model = imageReq,
                                        contentDescription = "Post Media",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = if (selectedAspectMode == "Original") ContentScale.Fit else ContentScale.Crop
                                    )
                                    if (isImgFailed) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF1A1A22))
                                                .clickable {
                                                    com.example.utils.MediaResolver.extractGoogleDriveFileId(rawPageUrl)?.let { fid ->
                                                        com.example.utils.GoogleDrivePermissionRepairManager.repairPermissionAsync(fid)
                                                    }
                                                    isImgFailed = false
                                                    urlIndex = 0
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Color.White, modifier = Modifier.size(32.dp))
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text("Tap to reload photo", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            // Instagram-style 1/N Carousel Counter Pill at Top-Right
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "${carouselPagerState.currentPage + 1}/${mediaList.size}",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                            // Carousel Dots Indicator
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                repeat(mediaList.size) { iteration ->
                                    val isSelected = carouselPagerState.currentPage == iteration
                                    Box(
                                        modifier = Modifier
                                            .size(if (isSelected) 6.dp else 5.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0xFF0095F6) else Color.White.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    } else {
                        val unlockedVaultUrl = vaultPayload?.mediaUrl
                        val rawSingleImg = if (isExclusivePaidPost && isUnlockedExclusive && !unlockedVaultUrl.isNullOrBlank()) {
                            unlockedVaultUrl
                        } else {
                            activeThumbUrl.takeIf { it.isNotBlank() } ?: activeMediaUrl.ifBlank { video.blurredThumbnailUrl }
                        }
                        val fallbackUrls = remember(rawSingleImg) {
                            com.example.utils.MediaResolver.getFallbackImageUrls(rawSingleImg).ifEmpty { listOf(rawSingleImg) }
                        }
                        var urlIndex by remember(rawSingleImg) { mutableIntStateOf(0) }
                        var isSingleImgFailed by remember(rawSingleImg) { mutableStateOf(false) }
                        val currentImgUrl = fallbackUrls.getOrElse(urlIndex) { rawSingleImg }
                        val singleImgReq = remember(currentImgUrl) {
                            com.example.utils.ExoPlayerUtils.buildFastPosterRequest(
                                context = context,
                                url = currentImgUrl,
                                widthPx = 900,
                                heightPx = 1125,
                                onError = {
                                    if (urlIndex + 1 < fallbackUrls.size) {
                                        urlIndex++
                                    } else {
                                        isSingleImgFailed = true
                                    }
                                }
                            )
                        }
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF14151C))) {
                            coil.compose.AsyncImage(
                                model = singleImgReq,
                                contentDescription = "Post Media",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = if (selectedAspectMode == "Original") ContentScale.Fit else ContentScale.Crop
                            )
                            if (isSingleImgFailed) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF1A1A22))
                                        .clickable {
                                            com.example.utils.MediaResolver.extractGoogleDriveFileId(rawSingleImg)?.let { fid ->
                                                com.example.utils.GoogleDrivePermissionRepairManager.repairPermissionAsync(fid)
                                            }
                                            isSingleImgFailed = false
                                            urlIndex = 0
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Color.White, modifier = Modifier.size(32.dp))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Tap to reload photo", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val primaryPoster = remember(activeMediaUrl, activeThumbUrl) {
                        com.example.utils.MediaResolver.resolveVideoPosterUrl(activeMediaUrl, activeThumbUrl)
                    }
                    val posterUrl = remember(primaryPoster, video.musicTrackImageUrl, video.user.avatarUrl, video.id) {
                        primaryPoster.ifBlank {
                            video.musicTrackImageUrl?.takeIf { it.isNotBlank() }
                                ?: video.user.avatarUrl.ifBlank { "https://api.dicebear.com/7.x/identicon/png?seed=${video.id}" }
                        }
                    }
                    val posterReq = remember(posterUrl) {
                        com.example.utils.ExoPlayerUtils.buildFastPosterRequest(
                            context = context,
                            url = posterUrl,
                            widthPx = 720,
                            heightPx = 960
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF14151C))
                    ) {
                        coil.compose.AsyncImage(
                            model = posterReq,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        if (isPlayerError && !isAutoRetrying) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .clickable {
                                        com.example.utils.MediaResolver.extractGoogleDriveFileId(video.videoUrl)?.let { fid ->
                                            com.example.utils.GoogleDrivePermissionRepairManager.repairPermissionAsync(fid)
                                        }
                                        autoRetryCount = 0
                                        isAutoRetrying = false
                                        isPlayerError = false
                                        manualPlayRequested = true
                                        retryAttempt++
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Retry Video", tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tap to retry video", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else if (isAutoRetrying || (effectiveIsPlaying && !hasRenderedFirstFrame)) {
                            ExclusiveSkeletonBox(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(0.35f),
                                cornerRadius = 0
                            )
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.align(Alignment.Center).size(34.dp)
                            )
                        }
                    }
                }
            }

            // LAYER 3 (TOP): Instagram-style Reel Badge on Video Posts (hidden when exclusive-locked)
            if (!isImage && !isExclusiveLocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { onReelsClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Watch Reel",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reel",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Interactive User Tagging (@) & Aspect Ratio Switcher (1:1 / 4:5 / 16:9) at Bottom-Left
            val taggedUsernames = remember(video.collaborators, video.description) {
                val mentionRegex = Regex("@([A-Za-z0-9_.]+)")
                val extracted = mentionRegex.findAll(video.description).map { it.groupValues[1] }.toList()
                val collabs = video.collaborators.map { it.username }.filter { it.isNotBlank() }
                (collabs + extracted).distinct().ifEmpty { listOf(video.user.username.ifEmpty { "creator" }) }
            }

            var showProductTagsPopover by remember(video.id) { mutableStateOf(false) }

            if (showProductTagsPopover && !isExclusiveLocked) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 52.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                showProductTagsPopover = false
                                GlobalState.pendingDeepLinkRoute.value = "store"
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = Color(0xFF0095F6), modifier = Modifier.size(16.dp))
                        Column {
                            Text("Creator Featured Merch • ₹999", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Tap to view in Shop & Checkout →", color = Color(0xFF0095F6), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (!isExclusiveLocked) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // User Tagging Person Icon
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .bounceClick()
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { showTaggedUsersOverlay = !showTaggedUsersOverlay },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Tagged Users",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Product Tagging ("View Products") Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .bounceClick()
                        .clickable { showProductTagsPopover = !showProductTagsPopover }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = "View Products",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "View products",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Aspect Ratio Cycle Pill (Original -> 4:5 -> 1:1 -> 16:9)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .bounceClick()
                        .clickable {
                            selectedAspectMode = when (selectedAspectMode) {
                                "Original" -> "4:5"
                                "4:5" -> "1:1"
                                "1:1" -> "16:9"
                                else -> "Original"
                            }
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Aspect Ratio",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedAspectMode,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Floating PiP Mini-Player Pill for Videos (only when unlocked)
                if (!isImage && activeMediaUrl.isNotBlank() && (!isExclusivePaidPost || isUnlockedExclusive)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .bounceClick()
                            .clickable {
                                exoPlayer?.pause()
                                GlobalPipController.startVideoPip(
                                    id = video.id,
                                    title = video.description.ifBlank { "Social India Video" },
                                    authorName = video.user.username.ifBlank { "Creator" },
                                    videoUrl = com.example.utils.MediaResolver.resolveVideoUrl(activeMediaUrl),
                                    thumbnailUrl = activeThumbUrl
                                )
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Mini Player PiP",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PiP",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            }

            // Tagged Users Popover Chips when toggled (hidden while locked)
            androidx.compose.animation.AnimatedVisibility(
                visible = showTaggedUsersOverlay && !isExclusiveLocked,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.85f),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 46.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    taggedUsernames.take(3).forEach { taggedName ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.8f),
                            modifier = Modifier.clickable { onProfileClick() }
                        ) {
                            Text(
                                text = "@$taggedName",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Save to Collection Floating Action Strip at Bottom of Media
            androidx.compose.animation.AnimatedVisibility(
                visible = showSaveCollectionBanner,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = Color(0xFF1E1E24).copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BookmarkAdded, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Saved to $savedCollectionName",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "Save to collection",
                            color = Color(0xFF0095F6),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showCollectionPickerDialog = true }
                        )
                    }
                }
            }

            // Buffering Spinner
            if (playerState == androidx.media3.common.Player.STATE_BUFFERING && !isImage && isPlaying) {
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Central Animated Speaker Indicator when toggled
            androidx.compose.animation.AnimatedVisibility(
                visible = showSoundToast,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.7f),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.7f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Dedicated Bottom-Right Floating Dark Translucent Play/Pause & Mute/Unmute Circular Buttons
            if ((!isExclusivePaidPost || isUnlockedExclusive) && ((!isImage && activeMediaUrl.isNotEmpty()) || (isImage && hasMusic))) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isImage && activeMediaUrl.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .bounceClick()
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .clickable {
                                    manualPlayRequested = true
                                    isManuallyPaused = !isManuallyPaused
                                    if (isManuallyPaused) {
                                        exoPlayer?.pause()
                                    } else {
                                        exoPlayer?.play()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isManuallyPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isManuallyPaused) "Play Video" else "Pause Video",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .bounceClick()
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    manualPlayRequested = true
                                    toggleSound()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute Audio" else "Mute Audio",
                            tint = Color.White,
                            modifier = Modifier
                                .size(15.dp)
                                .scale(speakerScale)
                        )
                    }
                }
            }

            // --- CREATOR GOLD BADGE & UNLOCKED EXCLUSIVE INDICATOR ---
            if (isExclusivePaidPost && isUnlockedExclusive) {
                ExclusiveGoldBadge(
                    label = if (isAuthorExclusive) "Exclusive" else "Unlocked",
                    priceInr = if (isAuthorExclusive) video.postUnlockPrice else null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )
            }

            // Skeleton shimmer briefly while fetching unlocked vault media
            if (isExclusivePaidPost && isUnlockedExclusive && isResolvingVault) {
                ExclusiveSkeletonBox(modifier = Modifier.fillMaxSize(), cornerRadius = 0)
            }

            // --- PAY-TO-UNLOCK EXCLUSIVE BLURRED PREVIEW OVERLAY (SMOOTH UNBLUR ANIMATION) ---
            androidx.compose.animation.AnimatedVisibility(
                visible = isExclusivePaidPost && !isUnlockedExclusive,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(480)) +
                    androidx.compose.animation.scaleOut(targetScale = 1.04f, animationSpec = androidx.compose.animation.core.tween(480)),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(20f)
            ) {
                ExclusiveLockedOverlay(
                    blurredPreviewUrl = video.blurredThumbnailUrl.ifBlank { video.thumbnailUrl },
                    creatorUsername = video.user.username,
                    creatorAvatarUrl = video.user.avatarUrl,
                    teaserCaption = video.teaserCaption.ifBlank { video.description },
                    priceInr = video.postUnlockPrice,
                    contentTypeLabel = if (isImage) "Exclusive Post" else "Exclusive Reel",
                    purchaseStatus = purchaseRec?.status,
                    expiresAt = purchaseRec?.expiresAt ?: 0L,
                    onUnlockClick = { showExclusiveUnlockSheet = true },
                    onReportClick = { showExclusiveReportDialog = true },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // --- INSTAGRAM SUBSCRIBER-ONLY PAYWALL OVERLAY (PRESERVED FOR SUBSCRIBER POSTS) ---
            val isSubscriberContent = video.isPremium && !isExclusivePaidPost
            val isAuthor = currentUid.isNotEmpty() && (video.userId == currentUid)
            var hasSubscriberAccess by remember(video.id) {
                mutableStateOf(com.example.model.RepositoryManager.socialRepository.isSubscribedCached(currentUid, video.userId))
            }

            LaunchedEffect(video.id, currentUid) {
                if (isSubscriberContent && !isAuthor && currentUid.isNotEmpty()) {
                    com.example.model.RepositoryManager.socialRepository.isSubscribedToCreator(currentUid, video.userId).collect { isSub ->
                        hasSubscriberAccess = isSub
                    }
                }
            }

            if (isSubscriberContent && !isAuthor && !hasSubscriberAccess) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF9C27B0).copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFE040FB)),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Subscriber Locked",
                                    tint = Color(0xFFE040FB),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Subscriber Exclusive",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This media is exclusive to subscribers of @${video.user.username.ifEmpty { "Creator" }}. Subscribe to unblur and unlock.",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                com.example.utils.SoundManager.playClickSound()
                                val effectivePrice = video.premiumPrice.takeIf { it > 0 } ?: 19.0
                                val intent = android.content.Intent(context, com.example.ui.SubscriptionActivity::class.java).apply {
                                    putExtra("creatorId", video.userId)
                                    putExtra("creatorName", video.user.username.ifEmpty { "Creator" })
                                    putExtra("amount", String.format(java.util.Locale.US, "%.2f", effectivePrice))
                                    putExtra("purpose", "CREATOR_EXCLUSIVE")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(context, "Unable to open subscription checkout", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Subscribe (₹${video.premiumPrice.toInt().takeIf { it > 0 } ?: 19}/mo)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (showExclusiveUnlockSheet) {
            ExclusiveUnlockBottomSheet(
                contentId = video.id,
                contentType = if (isImage) "POST" else "REEL",
                creatorId = video.userId,
                creatorUsername = video.user.username,
                creatorAvatarUrl = video.user.avatarUrl,
                title = video.description.ifBlank { "Exclusive Post" },
                teaserCaption = video.teaserCaption,
                blurredThumbnailUrl = video.blurredThumbnailUrl.ifBlank { video.thumbnailUrl },
                priceInr = video.postUnlockPrice,
                onDismiss = { showExclusiveUnlockSheet = false },
                onUnlocked = { payload ->
                    if (payload != null) vaultPayload = payload
                    showExclusiveUnlockSheet = false
                    manualPlayRequested = true
                    isManuallyPaused = false
                }
            )
        }

        if (showExclusiveReportDialog) {
            ExclusiveReportDialog(
                contentId = video.id,
                contentType = if (isImage) "POST" else "REEL",
                creatorId = video.userId,
                creatorUsername = video.user.username,
                onDismiss = { showExclusiveReportDialog = false }
            )
        }

        if (showCollectionPickerDialog) {
            val collections = listOf("All Posts", "Inspiration ✨", "Reels & Audio 🎵", "Shopping & Style 🛍️", "Travel & Places 📍", "Favorites ⭐")
            AlertDialog(
                onDismissRequest = { showCollectionPickerDialog = false },
                title = { Text("Save to Collection", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        collections.forEach { colName ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (savedCollectionName == colName) Color(0xFF0095F6).copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        savedCollectionName = colName
                                        showCollectionPickerDialog = false
                                        showSaveCollectionBanner = true
                                        if (currentUid.isNotEmpty()) {
                                            com.example.model.RepositoryManager.firestore.collection("users")
                                                .document(currentUid)
                                                .collection("saved_collections")
                                                .document(video.id)
                                                .set(mapOf("postId" to video.id, "collection" to colName, "timestamp" to System.currentTimeMillis()))
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(colName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    if (savedCollectionName == colName) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF0095F6), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCollectionPickerDialog = false }) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
        
        var isRealTimeReposted by remember(video.id, video.isReposted) { mutableStateOf(video.isReposted) }
        var realTimeRepostsCount by remember(video.id, video.repostsCount) { mutableIntStateOf(video.repostsCount) }
        val homeReducedMotion = com.example.ui.motion.rememberReducedMotionEnabled()

        // Post Action Bar: Heart, Comment, Repost, Paper-plane Share with 16.dp gap & inline 14.sp bold counts, Bookmark on far right
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Heart + Inline Count
                FlagshipLikeButton(
                    isLiked = isRealTimeLiked,
                    onClick = {
                        isRealTimeLiked = !isRealTimeLiked
                        if (isRealTimeLiked) realTimeLikesCount++ else realTimeLikesCount--
                        onLike()
                    },
                    likesCount = realTimeLikesCount,
                    iconSize = 24.dp,
                    unlikedColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    countStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                    ),
                    onCountClick = onLikesClick
                )

                // 2. Speech-bubble Comment + Inline Count with Press Scale (0.92 -> 1)
                val commentScale = remember { androidx.compose.animation.core.Animatable(1f) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .bounceClick(enableHaptics = false)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                triggerCrispMicroHaptic(context)
                                com.example.utils.SoundManager.playClickSound()
                                if (!homeReducedMotion) {
                                    coroutineScope.launch {
                                        commentScale.snapTo(com.example.ui.motion.SocialMotion.PressScale)
                                        commentScale.animateTo(1.16f, com.example.ui.motion.SocialMotion.floatSpec(com.example.ui.motion.SocialMotion.DurationQuick))
                                        commentScale.animateTo(1f, com.example.ui.motion.SocialMotion.springPop())
                                    }
                                }
                                onCommentClick()
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ModeComment,
                        contentDescription = "Comment",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .size(23.dp)
                            .graphicsLayer {
                                scaleX = commentScale.value
                                scaleY = commentScale.value
                            }
                    )
                    if (video.commentsCount >= 0) {
                        Spacer(modifier = Modifier.width(5.dp))
                        AnimatedCounterText(
                            count = video.commentsCount,
                            formatCount = { com.example.utils.NumberFormatUtils.formatCount(it) },
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 3. 1:1 Instagram Repost (360-deg rotation + 1 -> 1.3 -> 1 bounce + vibrant green #00E676 + slide-fade count)
                InstagramRepostButton(
                    isReposted = isRealTimeReposted,
                    repostCount = realTimeRepostsCount,
                    inactiveTint = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    activeTint = Color(0xFF00E676),
                    iconSize = 24.dp,
                    formatCount = { com.example.utils.NumberFormatUtils.formatCount(it) },
                    onClick = {
                        com.example.utils.SoundManager.playClickSound()
                        if (isExclusivePaidPost && !isUnlockedExclusive) {
                            android.widget.Toast.makeText(context, "Unlock this exclusive content first to repost", android.widget.Toast.LENGTH_SHORT).show()
                            showExclusiveUnlockSheet = true
                            return@InstagramRepostButton
                        }
                        if (!isRealTimeReposted) {
                            isRealTimeReposted = true
                            realTimeRepostsCount += 1
                            video.isReposted = true
                            video.repostsCount = realTimeRepostsCount
                            onShowRepostToast?.invoke {
                                isRealTimeReposted = false
                                realTimeRepostsCount = (realTimeRepostsCount - 1).coerceAtLeast(0)
                                video.isReposted = false
                                video.repostsCount = realTimeRepostsCount
                                val uidToRemove = RepositoryManager.getCurrentUserId()
                                if (uidToRemove.isNotEmpty()) {
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val postRef = RepositoryManager.firestore.collection("posts").document(video.id)
                                            RepositoryManager.firestore.runTransaction { transaction ->
                                                val snap = transaction.get(postRef)
                                                if (snap.exists()) {
                                                    val users = (snap.get("repostedByUserIds") as? List<*>)
                                                        ?.filterIsInstance<String>()
                                                        ?.toMutableList() ?: mutableListOf()
                                                    users.remove(uidToRemove)
                                                    transaction.update(postRef, "repostedByUserIds", users)
                                                    transaction.update(postRef, "repostsCount", users.size)
                                                }
                                            }.await()
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                            coroutineScope.launch {
                                RepositoryManager.socialRepository.repostContent(
                                    contentId = video.id,
                                    ownerUid = video.userId,
                                    contentType = "post",
                                    note = "",
                                    currentUserId = RepositoryManager.currentUserId,
                                    currentUsername = RepositoryManager.currentUsername,
                                    currentUserAvatar = RepositoryManager.currentUserAvatar
                                )
                            }
                        } else {
                            // Open thought sheet or undo
                            onRepostClick()
                        }
                    }
                )

                // 4. 1:1 Instagram Paper-Plane Share + Inline Count
                InstagramShareButton(
                    shareCount = video.sharesCount,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    iconSize = 22.dp,
                    formatCount = { com.example.utils.NumberFormatUtils.formatCount(it) },
                    onClick = {
                        com.example.utils.SoundManager.playClickSound()
                        onShareClick()
                    }
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.weight(1f))

            // 4.5 Animated Super Tip / Gift Creator Pill (Direct Creator Earning on Posts)
            var showPostGiftSheet by remember(video.id) { mutableStateOf(false) }
            var postGiftBurst by remember(video.id) { mutableStateOf<ContentGiftBurstEvent?>(null) }
            if (video.userId.isNotEmpty() && video.userId != currentUid) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFB300).copy(alpha = 0.16f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.6f)),
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .bounceClick()
                        .clickable {
                            triggerCrispMicroHaptic(context, strong = true)
                            showPostGiftSheet = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = "Tip Creator",
                            tint = Color(0xFFFF8F00),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Tip",
                            color = Color(0xFFFF8F00),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            if (showPostGiftSheet) {
                CreatorGiftBottomSheet(
                    creatorId = video.userId,
                    creatorUsername = video.user.username.ifBlank { "Creator" },
                    contentId = video.id,
                    contentType = "POST",
                    onDismiss = { showPostGiftSheet = false },
                    onGiftSent = { ev -> postGiftBurst = ev }
                )
            }

            postGiftBurst?.let { ev ->
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { postGiftBurst = null },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    AnimatedGiftBurstOverlay(
                        event = ev,
                        onFinished = { postGiftBurst = null },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 5. Bookmark on the far right with Spring Bounce (supports saving to collections)
            InstagramBookmarkButton(
                isBookmarked = isRealTimeSaved,
                inactiveTint = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                activeTint = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                iconSize = 24.dp,
                onClick = {
                    com.example.utils.SoundManager.playClickSound()
                    isRealTimeSaved = !isRealTimeSaved
                    onSaveClick()
                    showSaveCollectionBanner = isRealTimeSaved
                }
            )
        }
        
        if (realTimeLikesCount > 0) {
            androidx.compose.material3.Text(
                text = "Liked by $realTimeLikesCount ${if (realTimeLikesCount == 1) "person" else "people"}",
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                fontSize = 13.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                modifier = androidx.compose.ui.Modifier
                    .clickable { onLikesClick() }
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        if (video.description.isNotEmpty()) {
            var isTranslatingCaption by remember(video.id) { mutableStateOf(false) }
            var translatedCaption by remember(video.id) { mutableStateOf<String?>(null) }
            var showTranslatedCaption by remember(video.id) { mutableStateOf(false) }
            val cachedTranslated = translatedCaption
            val activeCaptionText = if (showTranslatedCaption && !cachedTranslated.isNullOrBlank()) cachedTranslated else video.description

            val words = activeCaptionText.split(" ")
            val captionAnnotated = androidx.compose.ui.text.buildAnnotatedString {
                withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(video.user.username.ifEmpty { "creator" })
                    append("  ")
                }
                words.forEach { word ->
                    if (word.startsWith("@") && word.length > 1) {
                        pushStringAnnotation(tag = "MENTION", annotation = word)
                        withStyle(style = androidx.compose.ui.text.SpanStyle(color = Color(0xFF0095F6), fontWeight = FontWeight.SemiBold)) {
                            append(word)
                        }
                        pop()
                    } else if (word.startsWith("#") && word.length > 1) {
                        pushStringAnnotation(tag = "HASHTAG", annotation = word)
                        withStyle(style = androidx.compose.ui.text.SpanStyle(color = Color(0xFF0095F6), fontWeight = FontWeight.Normal)) {
                            append(word)
                        }
                        pop()
                    } else {
                        append(word)
                    }
                    append(" ")
                }
            }

            androidx.compose.foundation.text.ClickableText(
                text = captionAnnotated,
                style = androidx.compose.ui.text.TextStyle(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp
                ),
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                onClick = { offset ->
                    captionAnnotated.getStringAnnotations("MENTION", start = offset, end = offset).firstOrNull()?.let { annotation ->
                        val uname = annotation.item.removePrefix("@").trim().lowercase()
                        com.example.utils.SoundManager.playClickSound()
                        coroutineScope.launch {
                            try {
                                val userDoc = com.example.model.RepositoryManager.firestore.collection("users")
                                    .whereEqualTo("username", uname)
                                    .limit(1).get().await().documents.firstOrNull()
                                if (userDoc != null) {
                                    onMentionClick(userDoc.id)
                                } else {
                                    onProfileClick()
                                }
                            } catch (_: Exception) {}
                        }
                        return@ClickableText
                    }
                }
            )

            // 1-Tap Hindi / Regional Language Translation Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clickable {
                        if (showTranslatedCaption) {
                            showTranslatedCaption = false
                        } else if (translatedCaption != null) {
                            showTranslatedCaption = true
                        } else if (!isTranslatingCaption) {
                            isTranslatingCaption = true
                            coroutineScope.launch {
                                translatedCaption = com.example.model.GeminiService.translateText(video.description, "Hindi")
                                isTranslatingCaption = false
                                showTranslatedCaption = true
                            }
                        }
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = "Translate",
                    tint = Color(0xFF0095F6),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when {
                        isTranslatingCaption -> "Translating to Hindi..."
                        showTranslatedCaption -> "See Original (मूल देखें)"
                        else -> "See Hindi Translation (अ/A)"
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0095F6)
                )
            }
        }

        if (video.commentsCount > 0) {
            androidx.compose.material3.Text(
                text = "View all ${video.commentsCount} comments",
                fontSize = 13.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = androidx.compose.ui.Modifier
                    .clickable { onCommentClick() }
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeMoreOptionsBottomSheet(
    video: com.example.model.Video,
    onDismiss: () -> Unit,
    onShareClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    onRepostClick: () -> Unit = {},
    onDeleteSuccess: () -> Unit = {},
    onEditCaptionSuccess: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
    val isOwner = currentUserId.isNotEmpty() && currentUserId == video.userId

    var isSaved by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(video.isSaved) }
    var showReportDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showDeleteDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showBlockDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showEditCaptionDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var editedCaption by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(video.description) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Quick Action Row (Instagram-style icons: Bookmark, Share, Repost, Copy Link)
            androidx.compose.foundation.layout.Row(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
            ) {
                // Save Quick Action
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    modifier = androidx.compose.ui.Modifier
                        .clickable {
                            isSaved = !isSaved
                            onSaveClick()
                            android.widget.Toast.makeText(
                                context,
                                if (isSaved) "Saved to collection" else "Removed from saved",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        .padding(8.dp)
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .size(50.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = if (isSaved) androidx.compose.material.icons.Icons.Filled.Bookmark else androidx.compose.material.icons.Icons.Outlined.BookmarkBorder,
                            contentDescription = "Save",
                            tint = if (isSaved) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(6.dp))
                    androidx.compose.material3.Text(
                        text = if (isSaved) "Saved" else "Save",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                    )
                }

                // Share Quick Action
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    modifier = androidx.compose.ui.Modifier
                        .clickable { onShareClick() }
                        .padding(8.dp)
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .size(50.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Send,
                            contentDescription = "Share",
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(6.dp))
                    androidx.compose.material3.Text("Share", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                }

                // Repost Quick Action
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    modifier = androidx.compose.ui.Modifier
                        .clickable { onRepostClick() }
                        .padding(8.dp)
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .size(50.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Repeat,
                            contentDescription = "Repost",
                            tint = if (video.isReposted) androidx.compose.ui.graphics.Color(0xFFFFB300) else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(6.dp))
                    androidx.compose.material3.Text("Repost", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                }

                // Copy Link Quick Action
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    modifier = androidx.compose.ui.Modifier
                        .clickable {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Post Link", "https://socialindia.app/p/${video.id}")
                            clipboard?.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "Link copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                        .padding(8.dp)
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .size(50.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Outlined.Link,
                            contentDescription = "Copy Link",
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(6.dp))
                    androidx.compose.material3.Text("Copy link", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                }
            }

            androidx.compose.material3.HorizontalDivider(modifier = androidx.compose.ui.Modifier.padding(vertical = 8.dp))

            // Action list items
            if (isOwner) {
                // Owner actions: Edit caption, Delete
                androidx.compose.foundation.layout.Row(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .clickable { showEditCaptionDialog = true }
                        .padding(14.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Outlined.Edit,
                        contentDescription = "Edit Caption",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(16.dp))
                    androidx.compose.material3.Text("Edit caption", fontSize = 15.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                }

                androidx.compose.foundation.layout.Row(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .clickable { showDeleteDialog = true }
                        .padding(14.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Outlined.Delete,
                        contentDescription = "Delete Post",
                        tint = androidx.compose.ui.graphics.Color(0xFFE53935)
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(16.dp))
                    androidx.compose.material3.Text("Delete post", fontSize = 15.sp, color = androidx.compose.ui.graphics.Color(0xFFE53935), fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                }
            } else {
                // Non-owner actions: Not Interested, Block, Report
                androidx.compose.foundation.layout.Row(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .clickable {
                            android.widget.Toast.makeText(context, "We'll show fewer posts like this", android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                        .padding(14.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Outlined.VisibilityOff,
                        contentDescription = "Not Interested",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(16.dp))
                    androidx.compose.material3.Text("Not interested", fontSize = 15.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                }

                androidx.compose.foundation.layout.Row(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .clickable { showBlockDialog = true }
                        .padding(14.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Outlined.Block,
                        contentDescription = "Block User",
                        tint = androidx.compose.ui.graphics.Color(0xFFE53935)
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(16.dp))
                    androidx.compose.material3.Text("Block @${video.user.username}", fontSize = 15.sp, color = androidx.compose.ui.graphics.Color(0xFFE53935))
                }

                androidx.compose.foundation.layout.Row(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .clickable { showReportDialog = true }
                        .padding(14.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.Report,
                        contentDescription = "Report",
                        tint = androidx.compose.ui.graphics.Color(0xFFE53935)
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(16.dp))
                    androidx.compose.material3.Text("Report post", fontSize = 15.sp, color = androidx.compose.ui.graphics.Color(0xFFE53935), fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                }
            }
        }
    }

    // Edit Caption Dialog
    if (showEditCaptionDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEditCaptionDialog = false },
            title = { androidx.compose.material3.Text("Edit Caption") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = editedCaption,
                    onValueChange = { editedCaption = it },
                    label = { androidx.compose.material3.Text("Caption") },
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    onEditCaptionSuccess(editedCaption)
                    com.example.model.RepositoryManager.firestore.collection("posts")
                        .document(video.id)
                        .update(mapOf("caption" to editedCaption, "description" to editedCaption))
                        .addOnSuccessListener {
                            android.widget.Toast.makeText(context, "Caption updated", android.widget.Toast.LENGTH_SHORT).show()
                            showEditCaptionDialog = false
                            onDismiss()
                        }
                        .addOnFailureListener {
                            android.widget.Toast.makeText(context, "Caption updated locally", android.widget.Toast.LENGTH_SHORT).show()
                            showEditCaptionDialog = false
                            onDismiss()
                        }
                }) {
                    androidx.compose.material3.Text("Save")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showEditCaptionDialog = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { androidx.compose.material3.Text("Delete Post?") },
            text = { androidx.compose.material3.Text("Are you sure you want to delete this post? This action cannot be undone.") },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        com.example.model.RepositoryManager.firestore.collection("posts")
                            .document(video.id)
                            .delete()
                            .addOnSuccessListener {
                                android.widget.Toast.makeText(context, "Post deleted", android.widget.Toast.LENGTH_SHORT).show()
                                showDeleteDialog = false
                                onDeleteSuccess()
                            }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFE53935))
                ) {
                    androidx.compose.material3.Text("Delete", color = androidx.compose.ui.graphics.Color.White)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }

    // Block User Confirmation Dialog
    if (showBlockDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { androidx.compose.material3.Text("Block @${video.user.username}?") },
            text = { androidx.compose.material3.Text("They won't be able to find your profile, posts or story on Social India.") },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        if (currentUserId.isNotEmpty() && video.userId.isNotEmpty()) {
                            com.example.model.RepositoryManager.firestore.collection("users")
                                .document(currentUserId)
                                .collection("blockedUsers")
                                .document(video.userId)
                                .set(mapOf("blockedAt" to com.google.firebase.Timestamp.now()))
                        }
                        android.widget.Toast.makeText(context, "@${video.user.username} has been blocked", android.widget.Toast.LENGTH_SHORT).show()
                        showBlockDialog = false
                        onDismiss()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFE53935))
                ) {
                    androidx.compose.material3.Text("Block", color = androidx.compose.ui.graphics.Color.White)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showBlockDialog = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }

    // Report Post Dialog
    if (showReportDialog) {
        var selectedReason by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("Inappropriate Content") }
        val reportReasons = listOf("Spam", "Inappropriate Content", "Harassment or Bullying", "False Information", "Hate Speech")

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { androidx.compose.material3.Text("Report Post") },
            text = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text(
                        "Why are you reporting this post?",
                        fontSize = 14.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                    reportReasons.forEach { reason ->
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .clickable { selectedReason = reason }
                                .padding(vertical = 4.dp)
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason }
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
                            androidx.compose.material3.Text(reason, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = { 
                        if (currentUserId.isNotEmpty()) {
                            val nowMillis = System.currentTimeMillis()
                            val report = hashMapOf(
                                "postId" to video.id,
                                "targetId" to video.id,
                                "targetType" to "POST",
                                "ownerId" to video.userId,
                                "ownerUsername" to video.user.username,
                                "reporterId" to currentUserId,
                                "reason" to selectedReason,
                                "caption" to video.description,
                                "contentPreview" to video.description,
                                "mediaUrl" to video.videoUrl,
                                "timestamp" to nowMillis,
                                "status" to "PENDING"
                            )
                            com.example.model.RepositoryManager.firestore.collection("reports").add(report)
                        }
                        android.widget.Toast.makeText(context, "Report submitted. Thank you for keeping Social India safe.", android.widget.Toast.LENGTH_LONG).show()
                        showReportDialog = false
                        onDismiss()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFE53935))
                ) {
                    androidx.compose.material3.Text("Submit Report", color = androidx.compose.ui.graphics.Color.White)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showReportDialog = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }
}
