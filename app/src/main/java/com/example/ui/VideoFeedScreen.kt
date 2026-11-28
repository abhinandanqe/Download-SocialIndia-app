@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class, com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
package com.example.ui
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import com.example.model.RepositoryManager
import com.example.ui.GlobalState

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.roundToInt
import androidx.compose.ui.text.withStyle

import androidx.compose.ui.draw.rotate
import android.widget.FrameLayout
import android.widget.Toast
import com.example.utils.bounceClick


import android.net.Uri
import android.view.ViewGroup


import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*

import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.LinearEasing

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.utils.bounceClick
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.roundToInt
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.model.CommentEntity
import com.example.model.User
import com.example.model.Video
import com.example.model.StoryUiModel
import com.example.viewmodel.VideoViewModel
import kotlinx.coroutines.launch

data class FloatingEmoji(val id: Long, val emoji: String, val xOffset: Float)

// Meme images for comments
val commentMemes = emptyList<String>()

val defaultCommunityStories = emptyList<StoryUiModel>()

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
fun VideoFeedScreen(
    viewModel: VideoViewModel = viewModel(),
    onNavigateToSearch: () -> Unit = {},
    onCameraClick: () -> Unit = {},
    onNavigateToWebView: (String) -> Unit = {},
    onNavigateToInbox: (String) -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToMusic: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onNavigateToMonetization: () -> Unit = {},
    onNavigateToTemplates: () -> Unit = {}
) {
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    
    val feedType by viewModel.feedType.collectAsStateWithLifecycle()
    val savedReelPage = remember { GlobalState.getPagerPage("reels_feed") }
    val pagerState = rememberPagerState(
        initialPage = savedReelPage.coerceAtMost((videos.size - 1).coerceAtLeast(0)),
        pageCount = { videos.size }
    )
    DisposableEffect(pagerState) {
        onDispose {
            GlobalState.savePagerPage("reels_feed", pagerState.currentPage)
        }
    }
    val globalUploadState by com.example.viewmodel.UploadViewModel.globalUploadState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(videos, GlobalState.targetVideoId.value) {
        val targetId = GlobalState.targetVideoId.value
        if (targetId != null && videos.isNotEmpty()) {
            val idx = videos.indexOfFirst { it.id == targetId }
            if (idx in videos.indices) {
                try {
                    pagerState.scrollToPage(idx)
                } catch (_: Exception) {}
            }
            GlobalState.targetVideoId.value = null
        }
    }

    LaunchedEffect(Unit) {
        GlobalState.homeFeedRefreshTrigger.collect { triggerTime ->
            if (triggerTime > 0L) {
                if (videos.isNotEmpty()) {
                    try {
                        pagerState.animateScrollToPage(0)
                    } catch (_: Exception) {}
                }
                viewModel.refreshVideos()
            }
        }
    }

    
    // Sub-Screen/Dialog states matching screenshots
    var selectedStoryGroup by remember { mutableStateOf<List<StoryUiModel>?>(null) }
    var selectedLiveStory by remember { mutableStateOf<StoryUiModel?>(null) }
    var selectedCreatorProfile by remember { mutableStateOf<User?>(null) }
    var activeChatUser by remember { mutableStateOf<com.example.model.User?>(null) }
    var selectedVideoForShare by remember { mutableStateOf<Video?>(null) }
    var selectedVideoForComments by remember { mutableStateOf<Video?>(null) }
    var selectedVideoForLikes by remember { mutableStateOf<Video?>(null) }
    var selectedVideoForRepost by remember { mutableStateOf<Video?>(null) }
    var selectedMusicForVideos by remember { mutableStateOf<Video?>(null) }
    var selectedVideoForStoryEditor by remember { mutableStateOf<Video?>(null) }
    var activePageVideo by remember { mutableStateOf<Video?>(null) }
    var showSuggestedProfiles by remember { mutableStateOf(false) }
    var hasShownSuggestedProfiles by remember { mutableStateOf(false) }
    var isRealUser by remember { mutableStateOf(false) }
    var currentUserId by remember { mutableStateOf("") }
    
    var showUndoNotInterestedBar by remember { mutableStateOf(false) }
    var recentlyHiddenVideoId by remember { mutableStateOf<String?>(null) }
    var showUndoHideCreatorBar by remember { mutableStateOf(false) }
    var recentlyHiddenCreatorId by remember { mutableStateOf<String?>(null) }
    val isFullScreen by com.example.utils.ReelsPreferencesManager.isFullScreen.collectAsStateWithLifecycle()
    val autoScrollEnabled by com.example.utils.ReelsPreferencesManager.autoScrollEnabled.collectAsStateWithLifecycle()
    val userWantsEarnButton by com.example.utils.ReelsPreferencesManager.showEarnButton.collectAsStateWithLifecycle()
    // Auto-hide Top Header ("Reels / Friends" + top controls) when user scrolls past reel 0 or starts scrolling down (1:1 Instagram Reels behavior)
    val isTopHeaderVisible by remember {
        derivedStateOf {
            !isFullScreen && pagerState.currentPage == 0 && pagerState.currentPageOffsetFraction <= 0.05f
        }
    }
    val isEarnHiddenByScroll by remember {
        derivedStateOf {
            pagerState.currentPage > 0 || Math.abs(pagerState.currentPageOffsetFraction) > 0.05f
        }
    }
    val isEarnVisible = userWantsEarnButton && !isEarnHiddenByScroll

    // Request Reels AudioFocus and ensure sound is enabled on entry; release on exit
    DisposableEffect(Unit) {
        com.example.utils.GlobalMediaSessionManager.requestFocus(
            com.example.utils.MediaSource.REELS,
            context
        )
        com.example.utils.GlobalFeedAudioState.setSoundEnabled(true)
        onDispose {
            com.example.utils.GlobalMediaSessionManager.releaseFocus(
                com.example.utils.MediaSource.REELS
            )
        }
    }
    
    var currentUsername by remember { mutableStateOf("") }
    var userAvatar by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        isRealUser = RepositoryManager.isRealUserLoggedIn()
        currentUserId = RepositoryManager.getCurrentUserId()
        try {
            val userDoc = RepositoryManager.firestore.collection("users").document(currentUserId).get().await()
            currentUsername = userDoc.getString("username") ?: ""
            userAvatar = userDoc.getString("avatar_url") ?: userDoc.getString("profilePicUrl") ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Scroll to shared / targeted video immediately when requested
    LaunchedEffect(com.example.viewmodel.VideoViewModel.selectedVideoIdToPlay, videos) {
        val focusId = com.example.viewmodel.VideoViewModel.selectedVideoIdToPlay
        if (focusId != null) {
            val index = videos.indexOfFirst { it.id == focusId }
            if (index in videos.indices) {
                viewModel.setFeedType(com.example.viewmodel.VideoViewModel.FeedType.FOR_YOU)
                try {
                    pagerState.scrollToPage(index)
                } catch (_: Exception) {}
                com.example.viewmodel.VideoViewModel.selectedVideoIdToPlay = null
            } else {
                viewModel.ensureVideoInFeed(focusId) { foundIdx ->
                    scope.launch {
                        if (foundIdx in videos.indices) {
                            try {
                                pagerState.scrollToPage(foundIdx)
                            } catch (_: Exception) {}
                        }
                        com.example.viewmodel.VideoViewModel.selectedVideoIdToPlay = null
                    }
                }
            }
        }
    }



    // Sync active page video
    LaunchedEffect(pagerState.currentPage) {
        GlobalState.savePagerPage("reels_feed", pagerState.currentPage)
        if (pagerState.currentPage == 3 && !hasShownSuggestedProfiles) {
            hasShownSuggestedProfiles = true
            showSuggestedProfiles = true
        }
    }
    LaunchedEffect(pagerState.currentPage, videos) {
        if (videos.isNotEmpty() && pagerState.currentPage < videos.size) {
            val nextVideo = videos[pagerState.currentPage]
            if (activePageVideo?.id != nextVideo.id) {
                activePageVideo = nextVideo
                if (nextVideo.id.isNotEmpty() && !nextVideo.id.startsWith("fallback")) {
                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.model.RepositoryManager.postRepository.incrementViews(nextVideo.id)
                    }
                }
                val nextUrls = videos
                    .drop(pagerState.currentPage + 1)
                    .filter { !it.isMediaAnImage && it.videoUrl.isNotBlank() && (!it.isExclusiveContent && !it.isPaidPost) }
                    .take(2)
                    .map { it.videoUrl }
                if (nextUrls.isNotEmpty()) {
                    com.example.utils.VideoCacheManager.prefetchNextVideos(context, nextUrls, maxCount = 2)
                }
                
                if (pagerState.currentPage >= videos.size - 3) {
                    viewModel.loadMore()
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (videos.isEmpty()) {
                    viewModel.refreshVideos()
                }
            } else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                com.example.utils.VideoCacheManager.cancelPrefetches()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            com.example.utils.VideoCacheManager.cancelPrefetches()
        }
    }
    
    LaunchedEffect(globalUploadState) {
        if (globalUploadState is com.example.viewmodel.UploadState.Success || globalUploadState is com.example.viewmodel.UploadState.SuccessWithMessage) {
            viewModel.refreshVideos()
        }
    }

    val stories by viewModel.stories.collectAsStateWithLifecycle()
    var showCreateStoryDialog by remember { mutableStateOf(false) }
    var storyTextOverlay by remember { mutableStateOf("") }
    var selectedMentionUserId by remember { mutableStateOf<String?>(null) }
    var selectedMentionUsername by remember { mutableStateOf<String?>(null) }
    var isStoryReback by remember { mutableStateOf(false) }

    val followingListState = remember(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            RepositoryManager.socialRepository.getFollowing(currentUserId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val followingIds = followingListState.value.map { it.id }.toSet()
    
    val combinedStories = remember(stories, followingIds, currentUserId) {
        val myReal = (stories as? List<StoryUiModel>)?.filter { it.userId == currentUserId } ?: emptyList()
        val followedReal = (stories as? List<StoryUiModel>)?.filter { it.userId != currentUserId && it.userId in followingIds } ?: emptyList()
        val othersReal = (stories as? List<StoryUiModel>)?.filter { it.userId != currentUserId && it.userId !in followingIds } ?: emptyList()
        
        myReal + followedReal + othersReal
    }

    LaunchedEffect(combinedStories, GlobalState.targetStoryId.value) {
        val targetId = GlobalState.targetStoryId.value
        if (targetId != null && combinedStories.isNotEmpty()) {
            val story = combinedStories.find { it.id == targetId }
            if (story != null) {
                if (story.isLive) {
                    selectedLiveStory = story
                } else {
                    val groupedStories = combinedStories.groupBy { it.userId ?: it.username }.values.map { it.toList() }
                    val storyGroup = groupedStories.find { group -> group.any { it.id == targetId } }
                    if (storyGroup != null) {
                        selectedStoryGroup = storyGroup
                    }
                }
            }
            GlobalState.targetStoryId.value = null
        }
    }

    if (isLoading && videos.isEmpty()) {
        InstagramShimmerLoading()
        return
    }

    if (!isLoading && videos.isEmpty()) {
        if (feedType == com.example.viewmodel.VideoViewModel.FeedType.COMMUNITY) {
            Box(modifier = Modifier.fillMaxSize()) {
                FriendsFeedEmptyState(
                    viewModel = viewModel,
                    onNavigateToSearch = onNavigateToSearch,
                    onCameraClick = onCameraClick
                )
                // Ensure top navigation is still visible
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    TopNavigation(
                        viewModel = viewModel,
                        onSearchClick = onNavigateToSearch,
                        onCameraClick = onCameraClick,
                        onNavigate = onNavigateToInbox,
                        onMonetizationClick = onNavigateToMonetization
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEE1D52).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = "No posts yet",
                            tint = Color(0xFFEE1D52),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "No posts yet • Upload your first Reel",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Share your real videos and moments with the Social India community.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                com.example.utils.SoundManager.playClickSound()
                                onCameraClick()
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Reel", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                com.example.utils.SoundManager.playClickSound()
                                viewModel.refreshVideos()
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Refresh", color = Color.White)
                        }
                    }
                }
            }
            // Ensure top navigation is still visible
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                TopNavigation(
                    viewModel = viewModel,
                    onSearchClick = onNavigateToSearch,
                    onCameraClick = onCameraClick,
                    onNavigate = onNavigateToInbox,
                    onMonetizationClick = onNavigateToMonetization
                )
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (globalUploadState is com.example.viewmodel.UploadState.Uploading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(4.dp),
                color = Color(0xFFEE1D52),
                trackColor = Color.Transparent
            )
        }
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshVideos() },
            modifier = Modifier.fillMaxSize()
        ) {
            val showCommentsForPager = selectedVideoForComments != null
            val pagerScaleState = androidx.compose.animation.core.animateFloatAsState(targetValue = if (showCommentsForPager) 0.85f else 1f, label = "pagerScale")
            val pagerCornerState = androidx.compose.animation.core.animateDpAsState(targetValue = if (showCommentsForPager) 24.dp else 0.dp, label = "pagerCorner")
            val pagerOffsetYState = androidx.compose.animation.core.animateFloatAsState(targetValue = if (showCommentsForPager) -150f else 0f, label = "pagerOffsetY")

            // Vertical Pager
            androidx.compose.foundation.pager.VerticalPager(
                state = pagerState,
                key = { pageIndex ->
                    if (pageIndex >= 0 && pageIndex < videos.size) {
                        "${videos[pageIndex].id}_$pageIndex"
                    } else {
                        "page_$pageIndex"
                    }
                },
                beyondViewportPageCount = 1,
                flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = androidx.compose.foundation.pager.PagerSnapDistance.atMost(1),
                    snapAnimationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    )
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                    .graphicsLayer {
                        val s = pagerScaleState.value
                        scaleX = s
                        scaleY = s
                        translationY = pagerOffsetYState.value
                        alpha = 1f
                        val c = pagerCornerState.value
                        if (c > 0.dp) {
                            clip = true
                            shape = RoundedCornerShape(c)
                        }
                    }
            ) { page ->
                if (page < videos.size) {
                    val video = videos[page]
                    val isVisible = pagerState.currentPage == page && selectedStoryGroup == null && selectedCreatorProfile == null && activeChatUser == null && !showCreateStoryDialog
                    val isPrewarm = kotlin.math.abs(pagerState.currentPage - page) <= 1

                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        VideoPlayerItem(
                            video = video,
                            isVisible = isVisible,
                            isPrewarm = isPrewarm,
                            showTopControls = isTopHeaderVisible,
                            onLike = { 
                                scope.launch {
                                    com.example.model.RepositoryManager.socialRepository.toggleVideoLike(video.id, currentUserId)
                                }
                            },
                            onAvatarClick = { selectedCreatorProfile = video.user },
                            onUserMentionClick = { user -> selectedCreatorProfile = user },
                            onShareClick = { selectedVideoForShare = video },
                            onShowLikesList = { selectedVideoForLikes = video },
                            onRepostClick = {
                                val isCurrentlyReposted = video.isReposted
                                video.isReposted = !isCurrentlyReposted
                                
                                scope.launch {
                                    try {
                                        val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
                                        if (currentUserId.isNotEmpty()) {
                                            val postRef = com.example.model.RepositoryManager.firestore.collection("posts").document(video.id)
                                            com.example.model.RepositoryManager.firestore.runTransaction { transaction ->
                                                val snapshot = transaction.get(postRef)
                                                val currentRepostedUsers = snapshot.get("repostedByUserIds") as? List<*> ?: emptyList<Any>()
                                                val newRepostedUsers = currentRepostedUsers.filterIsInstance<String>().toMutableList()
                                                if (isCurrentlyReposted) {
                                                    newRepostedUsers.remove(currentUserId)
                                                } else {
                                                    if (!newRepostedUsers.contains(currentUserId)) newRepostedUsers.add(currentUserId)
                                                }
                                                transaction.update(postRef, "repostedByUserIds", newRepostedUsers)
                                                transaction.update(postRef, "repostsCount", newRepostedUsers.size)
                                            }.await()
                                            android.widget.Toast.makeText(context, if (isCurrentlyReposted) "Repost removed" else "Reposted successfully", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        video.isReposted = isCurrentlyReposted
                                    }
                                }
                            },
                            
                            onCommentClick = { selectedVideoForComments = video },
                            onNavigateToWebView = onNavigateToWebView,
                            onNotInterested = {
                                val cId = video.userId
                                val vId = video.id
                                viewModel.markNotInterested(vId, cId)
                                recentlyHiddenVideoId = vId
                                showUndoNotInterestedBar = true
                            },
                            onHideAccount = {
                                val cId = video.userId
                                val uName = video.user.username
                                viewModel.hideCreator(cId, uName)
                                recentlyHiddenCreatorId = cId
                                showUndoHideCreatorBar = true
                            },
                            onDeleteVideo = {
                                viewModel.deleteVideoLocally(video.id)
                            },
                            onRemixClick = onCameraClick,
                            onVideoEnded = {
                                val isModalOpen = selectedStoryGroup != null || selectedCreatorProfile != null || activeChatUser != null || selectedVideoForShare != null || selectedVideoForComments != null
                                if (autoScrollEnabled && !isModalOpen && pagerState.currentPage < videos.size - 1 && !pagerState.isScrollInProgress) {
                                    scope.launch {
                                        kotlinx.coroutines.delay(200)
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        
        // 1:1 Instagram Top Navigation Tabs (Visible on initial Reel, auto-hides smoothly on downward swipe/scroll)
        androidx.compose.animation.AnimatedVisibility(
            visible = isTopHeaderVisible,
            enter = androidx.compose.animation.fadeIn(tween(220)) + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
            exit = androidx.compose.animation.fadeOut(tween(180)) + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it })
        ) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 0.dp)
            ) {
                TopNavigation(
                    viewModel = viewModel,
                    onSearchClick = onNavigateToSearch,
                    onCameraClick = onCameraClick,
                    onNavigate = onNavigateToInbox,
                    onMonetizationClick = onNavigateToMonetization,
                    isEarnVisible = isEarnVisible
                )
                // Hot Video Templates discovery promo strip on Reels page for new & existing users
                Surface(
                    onClick = {
                        com.example.utils.SoundManager.playClickSound()
                        onNavigateToTemplates()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.52f),
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(listOf(Color(0xFFEE1D52), Color(0xFFFF9933)))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .testTag("reels_hot_templates_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(Color(0xFFEE1D52), Color(0xFFFF9933)))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesomeMosaic,
                                    contentDescription = "Hot Video Templates",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "🔥 Hot Video Templates",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Beat-sync reels • Create viral videos in 1 tap",
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFEE1D52)
                        ) {
                            Text(
                                text = "Try Now",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Floating Exit Full Screen Button
        if (isFullScreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { com.example.utils.ReelsPreferencesManager.setFullScreen(false) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Exit Full Screen",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exit Full Screen", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Auto Scroll active badge
        if (autoScrollEnabled && !isFullScreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 16.dp, start = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { com.example.utils.ReelsPreferencesManager.toggleAutoScroll() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto Scroll", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Undo Not Interested Snackbar
        androidx.compose.animation.AnimatedVisibility(
            visible = showUndoNotInterestedBar,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 70.dp, start = 16.dp, end = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reel hidden from your feed", color = Color.White, fontSize = 14.sp)
                    TextButton(onClick = {
                        recentlyHiddenVideoId?.let { vId ->
                            viewModel.undoNotInterested(vId)
                        }
                        showUndoNotInterestedBar = false
                    }) {
                        Text("Undo", color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Undo Hide Creator Snackbar
        androidx.compose.animation.AnimatedVisibility(
            visible = showUndoHideCreatorBar,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 70.dp, start = 16.dp, end = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Account hidden", color = Color.White, fontSize = 14.sp)
                    TextButton(onClick = {
                        recentlyHiddenCreatorId?.let { cId ->
                            viewModel.undoHideCreator(cId)
                        }
                        showUndoHideCreatorBar = false
                    }) {
                        Text("Undo", color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Story Player Overlay (Image 6)
    selectedStoryGroup?.let { storyGroup ->
        androidx.compose.runtime.key(storyGroup) {
            StoryPlayerOverlay(
                stories = storyGroup,
                onDismiss = { 
                    com.example.utils.GlobalMediaSessionManager.releaseFocus(com.example.utils.MediaSource.STORY, context)
                    selectedStoryGroup = null 
                },
                onRebackClick = { uId, uName ->
                    com.example.utils.GlobalMediaSessionManager.releaseFocus(com.example.utils.MediaSource.STORY, context)
                    selectedStoryGroup = null
                    if (!RepositoryManager.isRealUserLoggedIn()) {
                        Toast.makeText(context, "Please log in to reply with a story", Toast.LENGTH_SHORT).show()
                        onCameraClick()
                    } else {
                        selectedMentionUserId = uId
                        selectedMentionUsername = uName
                        isStoryReback = true
                        storyTextOverlay = "Replying @$uName: "
                        showCreateStoryDialog = true
                    }
                },
                onProfileClick = { clickedUser ->
                    com.example.utils.GlobalMediaSessionManager.releaseFocus(com.example.utils.MediaSource.STORY, context)
                    selectedStoryGroup = null
                    scope.launch {
                        selectedCreatorProfile = RepositoryManager.socialRepository.getUser(clickedUser)
                    }
                }
            )
        }
    }

    selectedLiveStory?.let { story ->
        val context = LocalContext.current
        val currentUser = RepositoryManager.getCurrentUserId()
        LaunchedEffect(story) {
            var realUserName = "User_${currentUser.take(5)}"
            try {
                val userDoc = com.example.model.RepositoryManager.firestore.collection("users").document(currentUser).get().await()
                realUserName = userDoc.getString("username") ?: realUserName
            } catch (e: Exception) { }
            try {
                val intent = android.content.Intent(context, com.example.ui.LiveActivity::class.java).apply {
                    putExtra("userID", currentUser)
                    putExtra("userName", realUserName)
                    putExtra("liveID", story.id)
                    putExtra("isHost", story.userId == currentUser)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Unable to open live stream", Toast.LENGTH_SHORT).show()
            }
            selectedLiveStory = null
        }
    }

    // Custom Dynamic Story Creation Dialog
    if (showCreateStoryDialog) {
        StoryCreatorDialog(
            onDismiss = { showCreateStoryDialog = false },
            initialText = storyTextOverlay,
            mentionedUserId = selectedMentionUserId,
            mentionedUsername = selectedMentionUsername,
            isReback = isStoryReback
        )
    }

    // Creator Profile Screen Overlay (Image 9)
    selectedCreatorProfile?.let { creator ->
        CreatorProfileOverlay(
            user = creator,
            videos = videos,
            onDismiss = { selectedCreatorProfile = null },
            onPlayVideo = { video ->
                selectedCreatorProfile = null
                val index = videos.indexOfFirst { it.id == video.id }
                if (index in videos.indices) {
                    scope.launch {
                        try {
                            pagerState.scrollToPage(index)
                        } catch (_: Exception) {}
                    }
                }
            },
            onMessageClick = { chatUser ->
                selectedCreatorProfile = null
                activeChatUser = chatUser
            }
        )
    }

    // Direct Safe Message Box with Call triggers
    activeChatUser?.let { chatUser ->
        val currentUserId = RepositoryManager.getCurrentUserId()
        ChatWindow(
            currentUserId = currentUserId,
            otherUser = chatUser,
            videos = videos,
            onDismiss = { activeChatUser = null },
            onNavigateToHome = { activeChatUser = null }
        )
    }

    // Share Sheet Bottom Sheet (Image 7)
    selectedVideoForShare?.let { video ->
        ShareBottomSheet(
            video = video,
            onDismiss = { selectedVideoForShare = null },
            onAddToStory = { sharedVideo ->
                selectedVideoForShare = null
                val viewerUid = RepositoryManager.getCurrentUserId()
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
                    Toast.makeText(context, "Please log in to share to your story", Toast.LENGTH_SHORT).show()
                    onCameraClick()
                } else if (isExLocked) {
                    Toast.makeText(context, "Unlock this exclusive reel before adding to your story", Toast.LENGTH_SHORT).show()
                } else {
                    selectedVideoForStoryEditor = sharedVideo
                }
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
                        contentType = "reel",
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

    selectedVideoForComments?.let { video ->
        ModernCommentBottomSheet(
            video = video,
            onDismiss = { selectedVideoForComments = null },
            currentUserId = currentUserId,
            onProfileClick = { userId ->
                selectedVideoForComments = null
                // Since we need a User object for the overlay, we fetch or mock it
                scope.launch {
                   try {
                       val doc = RepositoryManager.firestore.collection("users").document(userId).get().await()
                       selectedCreatorProfile = User(
                           id = doc.id,
                           username = doc.getString("username") ?: "User",
                           avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: "",
                           isVerified = doc.getBoolean("isVerified") ?: false
                       )
                   } catch(e: Exception) {}
                }
            }
        )
    }

    selectedVideoForLikes?.let { video ->
        PostLikersBottomSheet(
            postId = video.id,
            onDismiss = { selectedVideoForLikes = null },
            onNavigateToProfile = { userId ->
                selectedVideoForLikes = null
                scope.launch {
                    try {
                        val doc = RepositoryManager.firestore.collection("users").document(userId).get().await()
                        selectedCreatorProfile = User(
                            id = doc.id,
                            username = doc.getString("username") ?: "User",
                            avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: "",
                            isVerified = doc.getBoolean("isVerified") ?: false
                        )
                    } catch(e: Exception) {}
                }
            }
        )
    }

    selectedMusicForVideos?.let { video ->
        MusicExploreBottomSheet(
            video = video,
            onDismiss = { selectedMusicForVideos = null },
            onUseAudio = {
                selectedMusicForVideos = null
                onCameraClick()
            }
        )
    }

    selectedVideoForStoryEditor?.let { video ->
        val cachedVault = com.example.repository.ExclusiveContentRepository.getCachedVaultMedia(video.id)
        val resolvedStoryUriStr = cachedVault?.mediaUrl?.ifBlank { video.videoUrl } ?: video.videoUrl
        StoryCreatorDialog(
            initialMediaUri = resolvedStoryUriStr.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) },
            isReback = true,
            mentionedUserId = video.user.id,
            mentionedUsername = video.user.username,
            repostedPostId = video.id,
            onDismiss = { selectedVideoForStoryEditor = null }
        )
    }

}

}


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerItem(
    video: Video,
    isVisible: Boolean,
    isPrewarm: Boolean = true,
    showTopControls: Boolean = true,
    onLike: () -> Unit,
    onAvatarClick: () -> Unit,
    onShareClick: () -> Unit,
    onRepostClick: () -> Unit,
    onNavigateToMusic: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onCommentClick: () -> Unit = {},
    onShowLikesList: () -> Unit = {},
    onNavigateToWebView: (String) -> Unit = {},
    onNotInterested: () -> Unit = {},
    onHideAccount: () -> Unit = {},
    onDeleteVideo: () -> Unit = {},
    onRemixClick: () -> Unit = {},
    onVideoEnded: () -> Unit = {},
    onUserMentionClick: (com.example.model.User) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentSpeed by com.example.utils.ReelsPreferencesManager.playbackSpeed.collectAsStateWithLifecycle()
    val autoScrollEnabled by com.example.utils.ReelsPreferencesManager.autoScrollEnabled.collectAsStateWithLifecycle()
    val loopVideo by com.example.utils.ReelsPreferencesManager.loopVideo.collectAsStateWithLifecycle()
    val captionsEnabled by com.example.utils.ReelsPreferencesManager.captionsEnabled.collectAsStateWithLifecycle()
    val isFullScreen by com.example.utils.ReelsPreferencesManager.isFullScreen.collectAsStateWithLifecycle()
    var isLongPress2x by remember { mutableStateOf(false) }

    val isPremiumBlur = video.isPremium && video.user.id != RepositoryManager.getCurrentUserId()
    var isUnlocked by remember(video.id) { mutableStateOf(false) }
    val isImage = video.isMediaAnImage
    var isPlayerErrorFallback by remember(video.id, video.videoUrl) { mutableStateOf(false) }
    var hasRenderedFirstFrame by remember(video.id, video.videoUrl) { mutableStateOf(false) }

    val isSoundEnabled by com.example.utils.GlobalFeedAudioState.isSoundEnabled.collectAsStateWithLifecycle()
    val isMuted = !isSoundEnabled
    var showMuteIcon by remember { mutableStateOf(false) }
    var isManuallyPaused by remember { mutableStateOf(false) }
    var showPlayPauseIcon by remember { mutableStateOf(false) }
    var exoPlayer by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
    var audioPlayer by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }

    val toggleSound: () -> Unit = {
        val newSoundState = com.example.utils.GlobalFeedAudioState.toggleSound()
        exoPlayer?.volume = if (newSoundState) 1f else 0f
        audioPlayer?.volume = if (newSoundState) 1f else 0f
        showMuteIcon = true
        com.example.utils.SoundManager.playClickSound()
    }
    var showHeart by remember { mutableStateOf(false) }
    var heartOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var doubleTapTrigger by remember { mutableStateOf(0) }
    var showBombExplosion by remember { mutableStateOf(false) }
    var bombOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var exoPlayerState by remember { mutableIntStateOf(androidx.media3.common.Player.STATE_IDLE) }
    var exoPlayerError by remember { mutableStateOf<String?>(null) }

    var detectedAspectRatio by remember(video.id) {
        mutableFloatStateOf(
            if (video.aspectRatio > 0f) video.aspectRatio
            else if (video.videoHeight > 0) video.videoWidth.toFloat() / video.videoHeight.toFloat()
            else 0f
        )
    }
    var userFitMode by remember(video.id) {
        mutableStateOf(
            if (video.aspectRatio >= 0.70f) "fit" else if (isFullScreen) "zoom" else "fit"
        )
    }
    
    val reelUnlockedKeys by com.example.repository.EntitlementRepository.unlockedKeysFlow.collectAsStateWithLifecycle()
    val reelPurchaseStatusMap by com.example.repository.EntitlementRepository.purchaseStatusMapFlow.collectAsStateWithLifecycle()
    val reelPurchaseRec = reelPurchaseStatusMap[video.id]
    val curReelViewerId = remember { RepositoryManager.getCurrentUserId() }
    val isReelOwner = curReelViewerId.isNotEmpty() && (video.userId == curReelViewerId)
    val isExclusivePaidReel = (video.isExclusiveContent || video.isPaidPost) && video.postUnlockPrice >= 10.0
    val isExclusiveReelUnlocked = remember(video.id, curReelViewerId, reelUnlockedKeys, isReelOwner, isExclusivePaidReel) {
        if (!isExclusivePaidReel || isReelOwner) true
        else reelUnlockedKeys.contains("${curReelViewerId}_${video.id}") ||
            com.example.repository.EntitlementRepository.hasAccessFast(
                userId = curReelViewerId,
                creatorId = video.userId,
                contentId = video.id,
                isExclusive = true,
                isPaidPost = true
            )
    }
    val isExclusiveReelLocked = isExclusivePaidReel && !isExclusiveReelUnlocked
    var reelVaultPayload by remember(video.id) {
        mutableStateOf(com.example.repository.ExclusiveContentRepository.getCachedVaultMedia(video.id))
    }
    var showReelUnlockSheet by remember(video.id) { mutableStateOf(false) }
    var showReelReportDialog by remember(video.id) { mutableStateOf(false) }

    LaunchedEffect(video.id, isExclusiveReelUnlocked, video.hasVaultMedia) {
        if (isExclusivePaidReel && isExclusiveReelUnlocked && video.hasVaultMedia && reelVaultPayload == null) {
            reelVaultPayload = com.example.repository.ExclusiveContentRepository.resolveUnlockedMedia(video.id)
        }
    }

    if (isVisible && isExclusivePaidReel) {
        com.example.utils.ExclusiveScreenSecurityEffect(enabled = true)
    }

    val effectiveReelMediaUrl = if (isExclusiveReelLocked) {
        ""
    } else {
        reelVaultPayload?.mediaUrl?.ifBlank { video.videoUrl } ?: video.videoUrl
    }

    var reelRetryAttempt by remember(video.id, effectiveReelMediaUrl) { mutableIntStateOf(0) }
    var reelAutoRetryCount by remember(video.id, effectiveReelMediaUrl) { mutableIntStateOf(0) }
    var isReelAutoRetrying by remember(video.id, effectiveReelMediaUrl) { mutableStateOf(false) }

    LaunchedEffect(reelAutoRetryCount, isReelAutoRetrying, effectiveReelMediaUrl, isVisible, isExclusiveReelLocked) {
        if (isReelAutoRetrying && reelAutoRetryCount in 1..3 && isVisible && !isExclusiveReelLocked && !isImage) {
            val delayMs = com.example.utils.VideoTranscoder.computeRetryBackoffMs(reelAutoRetryCount)
            kotlinx.coroutines.delay(delayMs)
            com.example.utils.ExoPlayerUtils.releaseToPool(exoPlayer)
            exoPlayer = null
            isReelAutoRetrying = false
            isPlayerErrorFallback = false
            reelRetryAttempt++
        }
    }

    LaunchedEffect(effectiveReelMediaUrl, video.musicTrackUrl, isVisible, isPrewarm, reelRetryAttempt, isExclusiveReelUnlocked) {
        if (isVisible && !isExclusiveReelLocked) {
            if (reelRetryAttempt == 0) {
                kotlinx.coroutines.delay(45)
            }
            if (!isImage && exoPlayer == null && effectiveReelMediaUrl.isNotBlank()) {
                try {
                    val candidateUrls = com.example.utils.MediaResolver.getFallbackVideoUrls(effectiveReelMediaUrl).ifEmpty {
                        listOfNotNull(com.example.utils.MediaResolver.resolveVideoUrl(effectiveReelMediaUrl).takeIf { it.isNotBlank() })
                    }
                    val playUrl = candidateUrls.firstOrNull().orEmpty()
                    var fallbackUrlIndex = 0
                    val player = com.example.utils.ExoPlayerUtils.acquireRecycledPlayer(context).apply {
                        val audioAttrs = androidx.media3.common.AudioAttributes.Builder()
                            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build()
                        setAudioAttributes(audioAttrs, false)
                        com.example.utils.ExoPlayerUtils.applyLiteModeConstraints(this)
                        repeatMode = if (autoScrollEnabled) androidx.media3.common.Player.REPEAT_MODE_OFF else if (loopVideo) androidx.media3.common.Player.REPEAT_MODE_ALL else androidx.media3.common.Player.REPEAT_MODE_OFF
                        val speedToSet = if (isLongPress2x) 2.0f else currentSpeed
                        setPlaybackSpeed(speedToSet)
                        com.example.utils.ExoPlayerUtils.bindItemListener(this, object : androidx.media3.common.Player.Listener {
                            override fun onRenderedFirstFrame() {
                                hasRenderedFirstFrame = true
                                isPlayerErrorFallback = false
                                isReelAutoRetrying = false
                                reelAutoRetryCount = 0
                            }
                            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                                if (videoSize.width > 0 && videoSize.height > 0) {
                                    val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
                                    detectedAspectRatio = ratio
                                    if (ratio >= 0.70f) {
                                        userFitMode = "fit"
                                    }
                                    if (playbackState == androidx.media3.common.Player.STATE_READY) {
                                        hasRenderedFirstFrame = true
                                        isPlayerErrorFallback = false
                                        isReelAutoRetrying = false
                                        reelAutoRetryCount = 0
                                    }
                                }
                            }
                            override fun onPlaybackStateChanged(state: Int) {
                                exoPlayerState = state
                                if (state == androidx.media3.common.Player.STATE_READY && videoSize.width > 0) {
                                    hasRenderedFirstFrame = true
                                    isPlayerErrorFallback = false
                                    isReelAutoRetrying = false
                                    reelAutoRetryCount = 0
                                }
                                if (state == androidx.media3.common.Player.STATE_ENDED) {
                                    if (autoScrollEnabled) {
                                        onVideoEnded()
                                    }
                                }
                            }
                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                android.util.Log.w("ExoPlayer", "Silent handle player error: ${error.errorCodeName} - ${error.message}")
                                if (fallbackUrlIndex + 1 < candidateUrls.size) {
                                    fallbackUrlIndex++
                                    val nextUrl = candidateUrls[fallbackUrlIndex]
                                    try {
                                        com.example.utils.MediaResolver.extractGoogleDriveFileId(video.videoUrl)?.let { fid ->
                                            com.example.utils.GoogleDrivePermissionRepairManager.repairPermissionAsync(fid)
                                        }
                                        val directItem = androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(nextUrl))
                                        setMediaItem(directItem)
                                        prepare()
                                        playWhenReady = isVisible && !isManuallyPaused
                                        return
                                    } catch (_: Exception) {}
                                }
                                if (reelAutoRetryCount < 3) {
                                    reelAutoRetryCount++
                                    isReelAutoRetrying = true
                                    isPlayerErrorFallback = false
                                } else {
                                    isReelAutoRetrying = false
                                    isPlayerErrorFallback = true
                                }
                            }
                        })
                        
                        if (playUrl.isBlank()) {
                            if (reelAutoRetryCount < 3) {
                                reelAutoRetryCount++
                                isReelAutoRetrying = true
                                isPlayerErrorFallback = false
                            } else {
                                isReelAutoRetrying = false
                                isPlayerErrorFallback = true
                            }
                        } else {
                            val videoSource = com.example.utils.VideoCacheManager.buildCachedMediaSource(context, playUrl)
                            setMediaSource(videoSource)
                        }

                        volume = if (isVisible && !isMuted) 1.0f else 0f
                        prepare()
                        playWhenReady = isVisible && !isManuallyPaused
                    }
                    exoPlayer = player
                } catch (e: Throwable) {
                    android.util.Log.d("VideoFeedScreen", "Handled create exoPlayer: ${e.message}")
                }
            } else if (isImage && !video.musicTrackUrl.isNullOrEmpty() && audioPlayer == null) {
                try {
                    val player = com.example.utils.ExoPlayerUtils.createExoPlayer(context, isMusic = true).apply {
                        repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                        val speedToSet = if (isLongPress2x) 2.0f else currentSpeed
                        setPlaybackSpeed(speedToSet)
                        var audioRetryCount = 0
                        com.example.utils.ExoPlayerUtils.bindItemListener(this, object : androidx.media3.common.Player.Listener {
                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                android.util.Log.w("VideoFeedScreen", "Audio player error: ${error.message}")
                                if (audioRetryCount < 1) {
                                    audioRetryCount++
                                    try {
                                        prepare()
                                        playWhenReady = isVisible && !isManuallyPaused
                                    } catch (_: Exception) {}
                                }
                            }
                        })
                        val rawMusicUrl = video.musicTrackUrl ?: ""
                        val audioUrl = com.example.utils.MediaResolver.resolveVideoUrl(rawMusicUrl)
                        val mediaItem = androidx.media3.common.MediaItem.Builder().setUri(android.net.Uri.parse(audioUrl)).build()
                        setMediaItem(mediaItem)
                        volume = if (isVisible && !isMuted) 1f else 0f
                        prepare()
                        playWhenReady = isVisible && !isManuallyPaused
                    }
                    audioPlayer = player
                } catch (e: Throwable) {
                    android.util.Log.d("VideoFeedScreen", "Handled photo audioPlayer create: ${e.message}")
                }
            }
        } else {
            // Release active decoder immediately when off-screen so budget devices only run 1 hardware decoder
            if (exoPlayer != null) {
                com.example.utils.ExoPlayerUtils.releaseToPool(exoPlayer)
                exoPlayer = null
                hasRenderedFirstFrame = false
            }
            if (audioPlayer != null) {
                com.example.utils.ExoPlayerUtils.safeRelease(audioPlayer)
                audioPlayer = null
            }
            // If adjacent reel (isPrewarm), warm the disk cache only for unlocked content
            if (isPrewarm && !isImage && effectiveReelMediaUrl.isNotBlank()) {
                com.example.utils.VideoCacheManager.prefetchVideo(context, effectiveReelMediaUrl)
            }
        }
    }

    LaunchedEffect(isVisible, isManuallyPaused, isMuted, exoPlayer, audioPlayer) {
        exoPlayer?.let { player ->
            if (isVisible) {
                player.volume = if (isMuted) 0f else 1f
                player.playWhenReady = !isManuallyPaused
            } else {
                player.volume = 0f
                player.playWhenReady = false
            }
        }
        audioPlayer?.let { player ->
            if (isVisible) {
                player.volume = if (isMuted) 0f else 1f
                player.playWhenReady = !isManuallyPaused
            } else {
                player.volume = 0f
                player.playWhenReady = false
            }
        }
    }
    
    DisposableEffect(video.videoUrl, video.musicTrackUrl) {
        onDispose {
            com.example.utils.ExoPlayerUtils.releaseToPool(exoPlayer)
            exoPlayer = null
            com.example.utils.ExoPlayerUtils.safeRelease(audioPlayer)
            audioPlayer = null
        }
    }


    val isReelsAllowed by com.example.utils.GlobalMediaSessionManager.isReelsPlaybackAllowed.collectAsStateWithLifecycle()

    LaunchedEffect(currentSpeed, isLongPress2x, exoPlayer) {
        val speed = if (isLongPress2x) 2.0f else currentSpeed
        exoPlayer?.setPlaybackSpeed(speed)
    }

    LaunchedEffect(autoScrollEnabled, loopVideo, exoPlayer) {
        exoPlayer?.repeatMode = if (autoScrollEnabled) {
            androidx.media3.common.Player.REPEAT_MODE_OFF
        } else if (loopVideo) {
            androidx.media3.common.Player.REPEAT_MODE_ALL
        } else {
            androidx.media3.common.Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer, audioPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer?.pause()
                    audioPlayer?.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isVisible && !isManuallyPaused && com.example.utils.GlobalMediaSessionManager.isReelsPlaybackAllowed.value) {
                        exoPlayer?.play()
                        audioPlayer?.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val emergencyPause: () -> Unit = {
            exoPlayer?.pause()
            audioPlayer?.pause()
        }
        com.example.utils.GlobalMediaSessionManager.registerPauseCallback(emergencyPause)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            com.example.utils.GlobalMediaSessionManager.unregisterPauseCallback(emergencyPause)
        }
    }

    LaunchedEffect(isVisible, isManuallyPaused, exoPlayer, audioPlayer, isMuted, isReelsAllowed) {
        if (isVisible && !isManuallyPaused && isReelsAllowed) {
            exoPlayer?.volume = if (isMuted) 0f else 1.0f
            audioPlayer?.volume = if (isMuted) 0f else 1.0f
            exoPlayer?.play()
            audioPlayer?.play()
        } else {
            exoPlayer?.volume = 0f
            audioPlayer?.volume = 0f
            exoPlayer?.pause()
            audioPlayer?.pause()
            if (!isVisible) {
                isManuallyPaused = false // Reset when scrolled away
            }
        }
    }

    
    val heartScale by animateFloatAsState(
        targetValue = if (showHeart) 1.5f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        finishedListener = { if (it == 1.5f) showHeart = false }
    )
    val heartAlpha by animateFloatAsState(
        targetValue = if (showHeart) 1f else 0f,
        animationSpec = tween(500)
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (video.videoUrls.size > 1) {
            val feedPagerState = rememberPagerState(pageCount = { video.videoUrls.size })
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                heartOffset = offset
                                showHeart = true
                                doubleTapTrigger++
                                onLike()
                            },
                            onTap = {
                                isManuallyPaused = !isManuallyPaused
                                showPlayPauseIcon = true
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = feedPagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val slideUrl = remember(video.videoUrls, page) {
                        com.example.utils.MediaResolver.resolveImageUrl(video.videoUrls[page]).ifBlank { video.videoUrls[page] }
                    }
                    val backdropReq = remember(slideUrl) {
                        com.example.utils.ExoPlayerUtils.buildFastPosterRequest(context, slideUrl, 120, 160)
                    }
                    val foregroundReq = remember(slideUrl) {
                        com.example.utils.ExoPlayerUtils.buildViewportImageRequest(context, slideUrl, 1080, 1440)
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // Ultra-lightweight downsampled ambient backdrop (zero GPU blur stall)
                        AsyncImage(
                            model = backdropReq,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().alpha(0.32f)
                        )
                        // Original media with exact proportions
                        AsyncImage(
                            model = foregroundReq,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                // Carousel dots
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 140.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(video.videoUrls.size) { iteration ->
                        val isSelected = feedPagerState.currentPage == iteration
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        } else if (isImage) {
            DoubleTapLikeBox(
                onDoubleTapLike = { offset ->
                    heartOffset = offset
                    showHeart = true
                    doubleTapTrigger++
                    onLike()
                },
                onSingleTap = {
                    isManuallyPaused = !isManuallyPaused
                    showPlayPauseIcon = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                val resolvedImg = remember(video.videoUrl) {
                    com.example.utils.MediaResolver.resolveImageUrl(video.videoUrl).ifBlank { video.videoUrl }
                }
                val backdropReq = remember(resolvedImg) {
                    com.example.utils.ExoPlayerUtils.buildFastPosterRequest(context, resolvedImg, 120, 160)
                }
                val foregroundReq = remember(resolvedImg) {
                    com.example.utils.ExoPlayerUtils.buildViewportImageRequest(context, resolvedImg, 1080, 1440)
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Ultra-lightweight downsampled ambient backdrop (zero GPU blur stall)
                    AsyncImage(
                        model = backdropReq,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(0.32f)
                    )
                    // Original media with exact proportions without cropping
                    AsyncImage(
                        model = foregroundReq,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else if (exoPlayer != null || !isImage) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                val isPlayingOrReady = (exoPlayerState == androidx.media3.common.Player.STATE_READY) && hasRenderedFirstFrame

                // LAYER 1 (BOTTOM): Player View inside DoubleTapLikeBox
                if (exoPlayer != null && !isPlayerErrorFallback) {
                    DoubleTapLikeBox(
                        onDoubleTapLike = { offset ->
                            heartOffset = offset
                            showHeart = true
                            doubleTapTrigger++
                            onLike()
                        },
                        onSingleTap = {
                            toggleSound()
                        },
                        onLongPress = {
                            isLongPress2x = !isLongPress2x
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isLongPress2x) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.Black.copy(alpha = 0.72f),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 56.dp)
                                    .zIndex(25f)
                            ) {
                                Text(
                                    text = "⏩ 2x Speed Active (Tap or Hold to Reset)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                        // Ambient background for non-vertical videos without GPU blur stalls
                        if (userFitMode == "fit" && detectedAspectRatio >= 0.70f) {
                            val ambientImg = video.musicTrackImageUrl?.takeIf { it.isNotBlank() }
                                ?: video.user.avatarUrl.takeIf { it.isNotBlank() }
                            if (ambientImg != null) {
                                val ambientReq = remember(ambientImg) {
                                    com.example.utils.ExoPlayerUtils.buildFastPosterRequest(context, ambientImg, 120, 160)
                                }
                                AsyncImage(
                                    model = ambientReq,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().alpha(0.30f)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color(0xFF181A26),
                                                    Color(0xFF0F1018),
                                                    Color(0xFF141622)
                                                )
                                            )
                                        )
                                )
                            }
                        }

                        AndroidView(
                            factory = { ctx ->
                                val view = android.view.LayoutInflater.from(ctx).inflate(com.example.R.layout.texture_player_view, null, false) as androidx.media3.ui.PlayerView
                                view.apply {
                                    player = exoPlayer
                                    resizeMode = if (userFitMode == "zoom") {
                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    } else {
                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    isClickable = false
                                    isFocusable = false
                                    setOnTouchListener { _, _ -> false }
                                }
                            },
                            update = { playerView ->
                                if (playerView.player != exoPlayer) {
                                    playerView.player = exoPlayer
                                }
                                val targetResizeMode = if (userFitMode == "zoom") {
                                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                } else {
                                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                if (playerView.resizeMode != targetResizeMode) {
                                    playerView.resizeMode = targetResizeMode
                                }
                                exoPlayer?.playWhenReady = isVisible && !isManuallyPaused
                                audioPlayer?.playWhenReady = isVisible && !isManuallyPaused
                            },
                            onRelease = { playerView ->
                                playerView.player = null
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // LAYER 2 (TOP): Thumbnail poster overlay while buffering
                if (!isPlayingOrReady || isPlayerErrorFallback) {
                    val primaryPoster = remember(video.videoUrl, video.thumbnailUrl) {
                        com.example.utils.MediaResolver.resolveVideoPosterUrl(video.videoUrl, video.thumbnailUrl)
                    }
                    val resolvedPoster = remember(primaryPoster, video.musicTrackImageUrl, video.user.avatarUrl, video.id) {
                        primaryPoster.ifBlank {
                            video.musicTrackImageUrl?.takeIf { it.isNotBlank() }
                                ?: video.user.avatarUrl.ifBlank { "https://api.dicebear.com/7.x/identicon/png?seed=${video.id}" }
                        }
                    }
                    val posterRequest = remember(resolvedPoster) {
                        com.example.utils.ExoPlayerUtils.buildFastPosterRequest(
                            context = context,
                            url = resolvedPoster,
                            widthPx = 720,
                            heightPx = 1280
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF12141E)),
                        contentAlignment = Alignment.Center
                    ) {
                        coil.compose.AsyncImage(
                            model = posterRequest,
                            contentDescription = "Video Thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isPlayerErrorFallback && !isReelAutoRetrying) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .clickable {
                                        com.example.utils.MediaResolver.extractGoogleDriveFileId(video.videoUrl)?.let { fid ->
                                            com.example.utils.GoogleDrivePermissionRepairManager.repairPermissionAsync(fid)
                                        }
                                        com.example.utils.ExoPlayerUtils.releaseToPool(exoPlayer)
                                        exoPlayer = null
                                        reelAutoRetryCount = 0
                                        isReelAutoRetrying = false
                                        isPlayerErrorFallback = false
                                        reelRetryAttempt++
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Retry Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Tap to retry video",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else if (isReelAutoRetrying || (isVisible && !hasRenderedFirstFrame)) {
                            ExclusiveSkeletonBox(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(0.35f),
                                cornerRadius = 0
                            )
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.align(Alignment.Center).size(36.dp)
                            )
                        }
                    }
                }
            }
        }
        


        if (showBombExplosion) {
            BombExplosionAnimation(
                offset = bombOffset,
                onFinished = { showBombExplosion = false }
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f)
                        )
                    )
                )
        )




        // Mute / Unmute Overlay
        if (showMuteIcon) {
            LaunchedEffect(showMuteIcon) {
                kotlinx.coroutines.delay(800)
                showMuteIcon = false
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Mute Toggle",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
        
        // Play / Pause Overlay
        if (showPlayPauseIcon) {
            LaunchedEffect(showPlayPauseIcon) {
                kotlinx.coroutines.delay(800)
                showPlayPauseIcon = false
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isManuallyPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        
        // 5-second Floating Hearts (Radially) Overlay
        if (showHeart) {
            FloatingHeartsOverlay(
                origin = heartOffset,
                onComplete = { showHeart = false }
            )
        }

        // Long Press 2x Speed Active Indicator
        if (isLongPress2x) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = Color(0xFFFE2C55), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("2x Speed Active", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- CREATOR GOLD BADGE FOR EXCLUSIVE REELS ---
        if (isExclusivePaidReel && isExclusiveReelUnlocked) {
            ExclusiveGoldBadge(
                label = if (isReelOwner) "Exclusive" else "Unlocked",
                priceInr = if (isReelOwner) video.postUnlockPrice else null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 14.dp)
            )
        }

        // --- EXCLUSIVE PAID REEL BLURRED OVERLAY (SMOOTH UNBLUR ANIMATION) ---
        androidx.compose.animation.AnimatedVisibility(
            visible = isExclusiveReelLocked,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(480)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(25f)
        ) {
            ExclusiveLockedOverlay(
                blurredPreviewUrl = video.blurredThumbnailUrl.ifBlank { video.thumbnailUrl },
                creatorUsername = video.user.username,
                creatorAvatarUrl = video.user.avatarUrl,
                teaserCaption = video.teaserCaption.ifBlank { video.description },
                priceInr = video.postUnlockPrice,
                contentTypeLabel = "Exclusive Reel",
                purchaseStatus = reelPurchaseRec?.status,
                expiresAt = reelPurchaseRec?.expiresAt ?: 0L,
                onUnlockClick = { showReelUnlockSheet = true },
                onReportClick = { showReelReportDialog = true },
                modifier = Modifier.fillMaxSize()
            )
        }

        // --- INSTAGRAM SUBSCRIBER REEL PAYWALL OVERLAY (PRESERVED FOR SUBSCRIBER REELS) ---
        val curUserId = curReelViewerId
        val isAuthorReel = isReelOwner
        var hasSubscriberAccessReel by remember(video.id) {
            mutableStateOf(RepositoryManager.socialRepository.isSubscribedCached(curUserId, video.userId))
        }

        LaunchedEffect(video.id, curUserId) {
            if (isPremiumBlur && !isExclusivePaidReel && !isAuthorReel && curUserId.isNotEmpty()) {
                RepositoryManager.socialRepository.isSubscribedToCreator(curUserId, video.userId).collect { isSub ->
                    hasSubscriberAccessReel = isSub
                }
            }
        }

        val isUnlockedViaEntitlement = isExclusiveReelUnlocked

        if (isPremiumBlur && !isExclusivePaidReel && !isAuthorReel && !hasSubscriberAccessReel && !isUnlockedViaEntitlement) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF13111C).copy(alpha = 0.92f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF9C27B0).copy(alpha = 0.5f)),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF9C27B0).copy(alpha = 0.25f),
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFE040FB)),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🔒", fontSize = 34.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Subscriber Exclusive Reel",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Reserved exclusively for VIP subscribers of @${video.user.username.ifEmpty { "Creator" }}.",
                            color = Color(0xFFC0C2D6),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                com.example.utils.SoundManager.playClickSound()
                                val effectivePrice = video.premiumPrice.takeIf { it > 0 } ?: 19.0
                                val intent = android.content.Intent(context, com.example.ui.SubscriptionActivity::class.java).apply {
                                    putExtra("creatorId", video.userId)
                                    putExtra("creatorName", video.user.username.ifEmpty { "Creator" })
                                    putExtra("contentId", video.id)
                                    putExtra("amount", String.format(java.util.Locale.US, "%.2f", effectivePrice))
                                    putExtra("purpose", "CREATOR_EXCLUSIVE")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Unable to open subscription checkout", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE040FB)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Subscribe for ₹${video.premiumPrice.toInt().takeIf { it > 0 } ?: 19}/mo", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        if (showReelUnlockSheet) {
            ExclusiveUnlockBottomSheet(
                contentId = video.id,
                contentType = "REEL",
                creatorId = video.userId,
                creatorUsername = video.user.username,
                creatorAvatarUrl = video.user.avatarUrl,
                title = video.description.ifBlank { "Exclusive Reel" },
                teaserCaption = video.teaserCaption,
                blurredThumbnailUrl = video.blurredThumbnailUrl.ifBlank { video.thumbnailUrl },
                priceInr = video.postUnlockPrice,
                onDismiss = { showReelUnlockSheet = false },
                onUnlocked = { payload ->
                    if (payload != null) reelVaultPayload = payload
                    showReelUnlockSheet = false
                    isManuallyPaused = false
                    reelRetryAttempt++
                }
            )
        }

        if (showReelReportDialog) {
            ExclusiveReportDialog(
                contentId = video.id,
                contentType = "REEL",
                creatorId = video.userId,
                creatorUsername = video.user.username,
                onDismiss = { showReelReportDialog = false }
            )
        }

        // Remix / Duet Side-by-Side Preview Mode State
        var isRemixDuetSplitActive by remember(video.id) { mutableStateOf(false) }
        var showReelSpeedMenu by remember(video.id) { mutableStateOf(false) }

        if (isRemixDuetSplitActive && !isFullScreen && !isExclusiveReelLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 104.dp, end = 16.dp)
                    .size(width = 112.dp, height = 168.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF14161E))
                    .border(1.5.dp, Color(0xFF0095F6), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF0095F6), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Remix / Duet", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Side-by-Side", color = Color.White.copy(alpha = 0.65f), fontSize = 9.5.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0095F6),
                        modifier = Modifier.clickable {
                            isRemixDuetSplitActive = false
                            onRemixClick()
                        }
                    ) {
                        Text("Record", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                IconButton(
                    onClick = { isRemixDuetSplitActive = false },
                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Duet", tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }

        // Floating Reel Playback Speed (0.3x - 3x), Auto-Captions (CC), & Remix/Duet Pill (Auto-hides after first swipe)
        androidx.compose.animation.AnimatedVisibility(
            visible = showTopControls && !isFullScreen && !isExclusiveReelLocked,
            enter = androidx.compose.animation.fadeIn(tween(200)) + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
            exit = androidx.compose.animation.fadeOut(tween(180)) + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 98.dp, start = 14.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speed pill (0.3x to 3x)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.38f),
                        modifier = Modifier
                            .bounceClick()
                            .clickable { showReelSpeedMenu = !showReelSpeedMenu }
                    ) {
                        Text(
                            text = "${if (currentSpeed % 1f == 0f) currentSpeed.toInt().toString() else currentSpeed.toString()}x",
                            color = if (currentSpeed != 1f) Color(0xFF00E5FF) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Auto-Captions (CC) toggle pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (captionsEnabled) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.38f),
                        modifier = Modifier
                            .bounceClick()
                            .clickable { com.example.utils.ReelsPreferencesManager.toggleCaptions() }
                    ) {
                        Text(
                            text = "CC",
                            color = if (captionsEnabled) Color(0xFF00E5FF) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Remix / Duet Layout toggle pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isRemixDuetSplitActive) Color(0xFF0095F6).copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.38f),
                        modifier = Modifier
                            .bounceClick()
                            .clickable { isRemixDuetSplitActive = !isRemixDuetSplitActive }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.ViewSidebar, contentDescription = "Duet", tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Duet", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // 1-Tap Floating Picture-in-Picture (PiP) Mini-Player pill
                    if (!isImage && video.videoUrl.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.38f),
                            modifier = Modifier
                                .bounceClick()
                                .clickable {
                                    exoPlayer?.pause()
                                    GlobalPipController.startVideoPip(
                                        id = video.id,
                                        title = video.description.ifBlank { "Reel Video" },
                                        authorName = video.user.username.ifBlank { "Creator" },
                                        videoUrl = com.example.utils.MediaResolver.resolveVideoUrl(video.videoUrl),
                                        thumbnailUrl = video.thumbnailUrl
                                    )
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP Mini-Player", tint = Color(0xFF00E5FF), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("PiP", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(visible = showReelSpeedMenu) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.3f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f).forEach { spd ->
                            val isSelected = currentSpeed == spd
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF0095F6) else Color.White.copy(alpha = 0.12f),
                                modifier = Modifier.clickable {
                                    com.example.utils.ReelsPreferencesManager.setPlaybackSpeed(spd)
                                    exoPlayer?.setPlaybackSpeed(spd)
                                    showReelSpeedMenu = false
                                }
                            ) {
                                Text(
                                    text = "${if (spd % 1f == 0f) spd.toInt().toString() else spd.toString()}x",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Captions Overlay Box
        if (captionsEnabled && video.description.isNotBlank() && !isFullScreen && !isExclusiveReelLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 190.dp, start = 16.dp, end = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("CC", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = video.description,
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Overlay UI with clicks
        VideoOverlay(
            video = video,
            doubleTapTrigger = doubleTapTrigger,
            isAudioPlaying = isVisible && !isManuallyPaused,
            isExclusiveLocked = isExclusivePaidReel && !isExclusiveReelUnlocked,
            onUnlockExclusiveClick = { showReelUnlockSheet = true },
            onLike = {
                if (!video.isLiked) {
                    bombOffset = androidx.compose.ui.geometry.Offset.Zero
                    showBombExplosion = true
                }
                onLike()
            },
            onAvatarClick = onAvatarClick,
            onShareClick = onShareClick,
            onRepostClick = onRepostClick,
            onCommentClick = onCommentClick,
            onNavigateToWebView = onNavigateToWebView,
            onNavigateToMusic = onNavigateToMusic,
            onNotInterested = onNotInterested,
            onHideAccount = onHideAccount,
            onDeleteVideo = onDeleteVideo,
            onRemixClick = onRemixClick,
            onUserMentionClick = onUserMentionClick
        )
    }
}

@Composable
fun TopNavigation(
    viewModel: VideoViewModel,
    onSearchClick: () -> Unit,
    onCameraClick: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onMonetizationClick: () -> Unit = {},
    isEarnVisible: Boolean = true
) {
    val context = LocalContext.current
    val feedType by viewModel.feedType.collectAsStateWithLifecycle()
    var showReelsDropdown by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(top = 12.dp)
    ) {
        // Left: + Add icon
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add",
            tint = Color.White,
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.CenterStart)
                .bounceClick()
                .clickable {
                    com.example.utils.SoundManager.playClickSound()
                    onCameraClick()
                }
        )

        // Center: Reels and Friends Tab Switcher
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center)
        ) {
            // Reels Tab with Dropdown
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .bounceClick()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            com.example.utils.SoundManager.playClickSound()
                            if (feedType != VideoViewModel.FeedType.FOR_YOU && feedType != VideoViewModel.FeedType.FOLLOWING) {
                                viewModel.setFeedType(VideoViewModel.FeedType.FOR_YOU)
                            } else {
                                showReelsDropdown = true
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    val isReelsActive = feedType == VideoViewModel.FeedType.FOR_YOU || feedType == VideoViewModel.FeedType.FOLLOWING
                    Text(
                        text = if (feedType == VideoViewModel.FeedType.FOLLOWING) "Following" else "Reels",
                        color = if (isReelsActive) Color.White else Color.White.copy(alpha = 0.65f),
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isReelsActive) 23.sp else 19.sp,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.6f),
                                offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                blurRadius = 4f
                            )
                        )
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Dropdown",
                        tint = if (isReelsActive) Color.White else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier
                            .size(24.dp)
                            .padding(start = 2.dp)
                            .clickable {
                                com.example.utils.SoundManager.playClickSound()
                                showReelsDropdown = true
                            }
                    )
                }

                DropdownMenu(
                    expanded = showReelsDropdown,
                    onDismissRequest = { showReelsDropdown = false },
                    modifier = Modifier
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Explore,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("For You", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                                if (feedType == VideoViewModel.FeedType.FOR_YOU) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            com.example.utils.SoundManager.playClickSound()
                            showReelsDropdown = false
                            viewModel.setFeedType(VideoViewModel.FeedType.FOR_YOU)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.PersonPin,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Following", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                                if (feedType == VideoViewModel.FeedType.FOLLOWING) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            com.example.utils.SoundManager.playClickSound()
                            showReelsDropdown = false
                            viewModel.setFeedType(VideoViewModel.FeedType.FOLLOWING)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Near You / Apna District 📍", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        },
                        onClick = {
                            com.example.utils.SoundManager.playClickSound()
                            showReelsDropdown = false
                            viewModel.setFeedType(VideoViewModel.FeedType.FOR_YOU)
                            android.widget.Toast.makeText(context, "📍 Apna District / Local Trends Activated!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Friends Tab with Overlapping Mini Friend Avatars
            val allVideos by viewModel.videos.collectAsStateWithLifecycle()
            val miniFriendAvatars = remember(allVideos) {
                allVideos.mapNotNull { it.user.avatarUrl.takeIf { url -> url.isNotBlank() } }.distinct().take(3)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .bounceClick()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        com.example.utils.SoundManager.playClickSound()
                        viewModel.setFeedType(VideoViewModel.FeedType.COMMUNITY)
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                val isFriendsActive = feedType == VideoViewModel.FeedType.COMMUNITY
                Text(
                    text = "Friends",
                    color = if (isFriendsActive) Color.White else Color.White.copy(alpha = 0.65f),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isFriendsActive) 22.sp else 18.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.6f),
                            offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                            blurRadius = 4f
                        )
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(contentAlignment = Alignment.CenterStart) {
                    val avatarsToShow = if (miniFriendAvatars.isNotEmpty()) miniFriendAvatars else listOf(
                        "https://api.dicebear.com/7.x/avataaars/png?seed=friend1",
                        "https://api.dicebear.com/7.x/avataaars/png?seed=friend2"
                    )
                    avatarsToShow.take(3).forEachIndexed { idx, avatarUrl ->
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Friend Avatar",
                            modifier = Modifier
                                .padding(start = (idx * 11).dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .border(1.2.dp, Color.White, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        // Right: Creator Monetization Studio Button (Facebook style)
        androidx.compose.animation.AnimatedVisibility(
            visible = isEarnVisible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f)
        ) {
            Surface(
                modifier = Modifier
                    .bounceClick()
                    .clickable {
                        com.example.utils.SoundManager.playClickSound()
                        onMonetizationClick()
                    },
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1877F2).copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = "Creator Monetization",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Earn",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FriendsFeedEmptyState(
    viewModel: VideoViewModel,
    onNavigateToSearch: () -> Unit,
    onCameraClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var suggestedUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoadingSuggestions by remember { mutableStateOf(true) }
    val followedUserIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        isLoadingSuggestions = true
        try {
            val users = RepositoryManager.socialRepository.getSuggestedUsers(10)
            suggestedUsers = users
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoadingSuggestions = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(top = 64.dp, bottom = 80.dp)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Friends Badge Icon
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF00E676).copy(alpha = 0.35f), Color(0xFF00C853).copy(alpha = 0.05f), Color.Transparent)
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(Color(0xFF1E1E1E), CircleShape)
                    .border(1.5.dp, Color(0xFF00E676).copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Watch Reels from Friends",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Follow friends and creators to watch their reels, creative stories, and latest posts right here.",
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Quick action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    com.example.utils.SoundManager.playClickSound()
                    onNavigateToSearch()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Find Friends", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = {
                    com.example.utils.SoundManager.playClickSound()
                    onCameraClick()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEE1D52),
                    contentColor = Color.White
                ),
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Icon(Icons.Default.VideoCall, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Post Reel", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Suggested Friends header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Suggested For You",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Explore",
                color = Color(0xFF00E676),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    com.example.utils.SoundManager.playClickSound()
                    onNavigateToSearch()
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoadingSuggestions) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00E676), modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else if (suggestedUsers.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                suggestedUsers.forEach { user ->
                    val isFollowed = followedUserIds.contains(user.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        coil.compose.AsyncImage(
                            model = user.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${user.id}" },
                            contentDescription = user.username,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = user.username,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                if (user.isVerified) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Verified",
                                        tint = Color(0xFF1DA1F2),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (user.bio.isNotBlank()) user.bio else "Suggested creator",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                com.example.utils.SoundManager.playClickSound()
                                scope.launch {
                                    RepositoryManager.socialRepository.dismissSuggestion(user.id)
                                    suggestedUsers = suggestedUsers.filter { it.id != user.id }
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = {
                                com.example.utils.SoundManager.playClickSound()
                                val myId = RepositoryManager.getCurrentUserId()
                                if (myId.isNotEmpty()) {
                                    scope.launch {
                                        if (isFollowed) {
                                            followedUserIds.remove(user.id)
                                            RepositoryManager.socialRepository.unfollowUser(myId, user.id)
                                        } else {
                                            followedUserIds.add(user.id)
                                            RepositoryManager.socialRepository.followUser(myId, user.id)
                                            viewModel.refreshVideos()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowed) Color.DarkGray else Color(0xFF00E676),
                                contentColor = if (isFollowed) Color.White else Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = if (isFollowed) "Following" else "Follow",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        TextButton(
            onClick = {
                com.example.utils.SoundManager.playClickSound()
                viewModel.setFeedType(VideoViewModel.FeedType.FOR_YOU)
            }
        ) {
            Text("Switch to For You Reels", color = Color(0xFFEE1D52), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun VideoOverlay(
    video: Video,
    doubleTapTrigger: Int = 0,
    isAudioPlaying: Boolean = true,
    isExclusiveLocked: Boolean = false,
    onUnlockExclusiveClick: () -> Unit = {},
    onLike: () -> Unit,
    onAvatarClick: () -> Unit,
    onShareClick: () -> Unit,
    onRepostClick: () -> Unit,
    onShowHeart: () -> Unit = {},
    onNavigateToMusic: (String, String, String, String) -> Unit = { _, _, _, _ -> },

    onCommentClick: () -> Unit = {},
    onShowLikesList: () -> Unit = {},
    onNavigateToWebView: (String) -> Unit = {},
    onNotInterested: () -> Unit = {},
    onHideAccount: () -> Unit = {},
    onDeleteVideo: () -> Unit = {},
    onRemixClick: () -> Unit = {},
    onUserMentionClick: (com.example.model.User) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isFullScreen by com.example.utils.ReelsPreferencesManager.isFullScreen.collectAsStateWithLifecycle()
    val isSoundEnabled by com.example.utils.GlobalFeedAudioState.isSoundEnabled.collectAsStateWithLifecycle()
    val isMuted = !isSoundEnabled
    val toggleSound: () -> Unit = {
        com.example.utils.GlobalFeedAudioState.toggleSound()
        com.example.utils.SoundManager.playClickSound()
    }
    var currentUserId by remember { mutableStateOf("") }
    var isRealUser by remember { mutableStateOf(false) }
    var isSaved by remember(video.id) { mutableStateOf(false) }
    var showCollaboratorsDialog by remember { mutableStateOf(false) }
    var showMoreOptionsDialog by remember { mutableStateOf(false) }
    var showTrialReelSheet by remember { mutableStateOf(false) }
    var showAddYoursSheet by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    var localCollabInviteStatus by remember(video.id) {
        mutableStateOf(if (video.pendingCollaboratorIds.contains(currentUserId)) "PENDING" else "NONE")
    }
    var isCollabAcceptedLocally by remember(video.id) {
        mutableStateOf(video.collaboratorIds.contains(currentUserId))
    }

    LaunchedEffect(Unit) {
        currentUserId = RepositoryManager.getCurrentUserId()
        isRealUser = RepositoryManager.isRealUserLoggedIn()
        if (currentUserId.isNotEmpty() && video.pendingCollaboratorIds.contains(currentUserId)) {
            localCollabInviteStatus = "PENDING"
        }
        if (currentUserId.isNotEmpty() && video.collaboratorIds.contains(currentUserId)) {
            isCollabAcceptedLocally = true
        }
    }

    val isFollowingFlow = remember(currentUserId, video.userId) {
        if (currentUserId.isNotEmpty() && video.userId.isNotEmpty()) {
            RepositoryManager.socialRepository.isFollowing(currentUserId, video.userId)
        } else {
            kotlinx.coroutines.flow.flowOf(false)
        }
    }
    val isFollowingStream by isFollowingFlow.collectAsStateWithLifecycle(initialValue = false)
    
    var isFollowing by remember(isFollowingStream) { mutableStateOf(isFollowingStream) }

    var realTimeLikesCount by remember { mutableIntStateOf(video.likesCount) }
    var realTimeCommentsCount by remember { mutableIntStateOf(video.commentsCount) }
    var realTimeRepostsCount by remember { mutableIntStateOf(0) }
    var realTimeSharesCount by remember { mutableIntStateOf(0) }
    var realTimeSavesCount by remember { mutableIntStateOf(0) }
    var recentReposterAvatars by remember { mutableStateOf<List<String>>(emptyList()) }
    var isRealTimeLiked by remember { mutableStateOf(video.isLiked) }
    
    LaunchedEffect(video.isLiked) {
        isRealTimeLiked = video.isLiked
    }
    
    LaunchedEffect(doubleTapTrigger) {
        if (doubleTapTrigger > 0 && !isRealTimeLiked) {
            isRealTimeLiked = true
            realTimeLikesCount++
        }
    }
    
    LaunchedEffect(video.id) {
        kotlinx.coroutines.delay(350)
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val doc = db.collection("posts").document(video.id).get().await()
            if (doc != null && doc.exists()) {
                val fbPost = doc.toObject(com.example.model.FirebasePost::class.java)
                if (fbPost != null) {
                    realTimeLikesCount = fbPost.likesCount
                    realTimeCommentsCount = fbPost.commentsCount
                    realTimeRepostsCount = fbPost.repostsCount
                    realTimeSharesCount = fbPost.sharesCount
                    realTimeSavesCount = fbPost.savesCount
                }
            }
        } catch (_: Exception) {}
    }

    // Vinyl rotation isolated inside SpinningReelAudioArt so VideoOverlay stays recompose-free



    val isDarkTheme by com.example.utils.ThemePreference.isDarkTheme.collectAsStateWithLifecycle()
    val contentColor = Color.White
    val subTextColor = Color.White.copy(alpha = 0.85f)
    val iconTintColor = Color.White
    val overlayBg = Color.Transparent
    val overlayBorder = Color.Transparent

    val shadowStyle = androidx.compose.ui.text.TextStyle(
        shadow = androidx.compose.ui.graphics.Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = androidx.compose.ui.geometry.Offset(1f, 1f),
            blurRadius = 8f
        )
    )

    var showChallengeSheet by remember(video.id) { mutableStateOf(false) }
    var showSuperTipSheet by remember(video.id) { mutableStateOf(false) }
    var showReelGiftSheet by remember(video.id) { mutableStateOf(false) }
    var reelGiftBurst by remember(video.id) { mutableStateOf<ContentGiftBurstEvent?>(null) }
    var tipSuccessAmount by remember(video.id) { mutableStateOf<Int?>(null) }
    var isReelRepostedLocal by remember(video.id, video.isReposted) { mutableStateOf(video.isReposted) }
    var showReelRepostPill by remember(video.id) { mutableStateOf(false) }

    LaunchedEffect(showReelRepostPill) {
        if (showReelRepostPill) {
            kotlinx.coroutines.delay(com.example.ui.motion.SocialMotion.DurationRepostToastHoldMs)
            showReelRepostPill = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        if (!isFullScreen) {
            // Smooth edge-to-edge gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .align(Alignment.BottomCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )
        
        // Bottom Left: User Info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = if (isFullScreen) 24.dp else 72.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!video.reposterUsername.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(bottom = 0.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Repeat, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${video.reposterUsername ?: "Someone"} reposted", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (!video.repostThought.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(video.repostThought ?: "", fontSize = 12.sp)
                    }
                }
            }
            if (localCollabInviteStatus == "PENDING" && currentUserId.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color(0xFF69C9D0).copy(alpha = 0.8f)),
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.GroupAdd,
                            contentDescription = null,
                            tint = Color(0xFF69C9D0),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "@${video.user.username} invited you to collaborate",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                localCollabInviteStatus = "DECLINED"
                                scope.launch {
                                    com.example.model.RepositoryManager.postRepository.declineCollaboration(video.id)
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Decline", fontSize = 10.sp, color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                localCollabInviteStatus = "ACCEPTED"
                                isCollabAcceptedLocally = true
                                scope.launch {
                                    com.example.model.RepositoryManager.postRepository.acceptCollaboration(video.id)
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Accept", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // User Row (Avatar, Name, Follow)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.bounceClick().clickable { 
                    if (video.collaborators.isNotEmpty() || isCollabAcceptedLocally) {
                        showCollaboratorsDialog = true
                    } else {
                        onAvatarClick() 
                    }
                }
            ) {
                AsyncImage(
                    model = video.user.avatarUrl,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
                
                val displayUsername = remember(video.collaborators, isCollabAcceptedLocally) {
                    val names = video.collaborators.map { it.username }.toMutableList()
                    if (isCollabAcceptedLocally && video.collaborators.none { it.id == currentUserId }) {
                        names.add(0, "You")
                    }
                    if (names.isNotEmpty()) {
                        "${video.user.username} × ${names.joinToString(" × ")}"
                    } else {
                        video.user.username
                    }
                }
                
                Text(
                    text = displayUsername,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black.copy(alpha = 0.5f), offset = androidx.compose.ui.geometry.Offset(1f, 1f), blurRadius = 4f))
                )
                
                if (video.user.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerificationBadge(user = video.user, modifier = Modifier.size(14.dp))
                }
                
                if (currentUserId.isNotEmpty() && video.userId.isNotEmpty() && currentUserId != video.userId) {
                    Spacer(modifier = Modifier.width(8.dp))
                    if (!isFollowing) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .bounceClick()
                                .clickable {
                                    if (!isRealUser) {
                                        Toast.makeText(context, "Please login first to follow users!", Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }
                                    scope.launch {
                                        isFollowing = true
                                        try {
                                            RepositoryManager.socialRepository.followUser(currentUserId, video.userId)
                                        } catch (e: Exception) {
                                            isFollowing = false
                                        }
                                    }
                                }
                        ) {
                            Text("Follow", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = shadowStyle)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.People, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Friend", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, style = shadowStyle)
                        }
                    }
                }
            }
            
            // Description with Interactive Mentions, Hashtags & 1-Tap Regional Translation
            var isTranslatingReelCaption by remember(video.id) { mutableStateOf(false) }
            var translatedReelCaption by remember(video.id) { mutableStateOf<String?>(null) }
            var showTranslatedReelCaption by remember(video.id) { mutableStateOf(false) }

            val cachedTranslation = translatedReelCaption
            val activeReelDescription = if (showTranslatedReelCaption && !cachedTranslation.isNullOrBlank()) {
                cachedTranslation
            } else {
                video.description
            }

            val words = activeReelDescription.split(" ")
            val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
                words.forEach { word ->
                    if (word.startsWith("@") && word.length > 1) {
                        pushStringAnnotation(tag = "MENTION", annotation = word)
                        withStyle(style = androidx.compose.ui.text.SpanStyle(color = Color(0xFF69C9D0), fontWeight = FontWeight.Bold)) {
                            append(word)
                        }
                        pop()
                    } else if (word.startsWith("#") && word.length > 1) {
                        pushStringAnnotation(tag = "HASHTAG", annotation = word)
                        withStyle(style = androidx.compose.ui.text.SpanStyle(color = Color(0xFF1DA1F2), fontWeight = FontWeight.Bold)) {
                            append(word)
                        }
                        pop()
                    } else if (word.startsWith("http://") || word.startsWith("https://")) {
                        pushStringAnnotation(tag = "URL", annotation = word)
                        withStyle(style = androidx.compose.ui.text.SpanStyle(color = Color(0xFF1DA1F2), fontWeight = FontWeight.Bold)) {
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
                text = annotatedString,
                style = shadowStyle.merge(androidx.compose.ui.text.TextStyle(color = contentColor, fontSize = 14.sp)),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                onClick = { offset ->
                    annotatedString.getStringAnnotations("MENTION", start = offset, end = offset).firstOrNull()?.let { annotation ->
                        val username = annotation.item.removePrefix("@").trim().lowercase()
                        com.example.utils.SoundManager.playClickSound()
                        scope.launch {
                            try {
                                val userDoc = com.example.model.RepositoryManager.firestore.collection("users")
                                    .whereEqualTo("username", username)
                                    .limit(1).get().await().documents.firstOrNull()
                                if (userDoc != null) {
                                    val user = userDoc.toObject(com.example.model.User::class.java)
                                    if (user != null) {
                                        onUserMentionClick(user.copy(id = userDoc.id))
                                        return@launch
                                    }
                                }
                                onAvatarClick()
                            } catch (_: Exception) {
                                onAvatarClick()
                            }
                        }
                        return@ClickableText
                    }
                    annotatedString.getStringAnnotations("HASHTAG", start = offset, end = offset).firstOrNull()?.let { annotation ->
                        onNavigateToWebView("https://socialindia.app/tag/${annotation.item.removePrefix("#")}")
                        return@ClickableText
                    }
                    annotatedString.getStringAnnotations("URL", start = offset, end = offset).firstOrNull()?.let { annotation ->
                        onNavigateToWebView(annotation.item)
                    }
                }
            )

            // 1-Tap Regional Translate + 1-Tap Join Challenge + Super Tip Pills (hidden while locked)
            if (!isExclusiveLocked) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (video.description.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.42f),
                            modifier = Modifier.clickable {
                                if (showTranslatedReelCaption) {
                                    showTranslatedReelCaption = false
                                } else if (translatedReelCaption != null) {
                                    showTranslatedReelCaption = true
                                } else if (!isTranslatingReelCaption) {
                                    isTranslatingReelCaption = true
                                    scope.launch {
                                        translatedReelCaption = com.example.model.GeminiService.translateText(video.description, "Hindi")
                                        isTranslatingReelCaption = false
                                        showTranslatedReelCaption = true
                                    }
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Translate, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when {
                                        isTranslatingReelCaption -> "Translating..."
                                        showTranslatedReelCaption -> "Original"
                                        else -> "अ/A Hindi"
                                    },
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 1-Tap "Join Challenge" Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE1306C).copy(alpha = 0.78f),
                        modifier = Modifier.clickable {
                            triggerCrispMicroHaptic(context)
                            showChallengeSheet = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Join Challenge",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Instant Creator Super-Tip Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.42f),
                        modifier = Modifier.clickable {
                            triggerCrispMicroHaptic(context, strong = true)
                            showSuperTipSheet = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💎 Super Tip", color = Color(0xFFFFD700), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                if (video.isTrialReel && (video.userId == currentUserId || video.user.id == currentUserId)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TrialReelBadge(
                        onClick = { showTrialReelSheet = true }
                    )
                }

                if (!video.addYoursPromptTitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AddYoursStickerView(
                        promptText = video.addYoursPromptTitle ?: "",
                        participantsCount = 0,
                        onClick = { showAddYoursSheet = true }
                    )
                }

                // Music / Audio row (Instagram music note + animated equalizer + seamless looping marquee)
                com.example.ui.motion.InstagramAudioRow(
                    audioTitle = video.musicTrack?.takeIf { it.isNotBlank() } ?: "Original Audio",
                    isPlaying = isAudioPlaying,
                    iconTint = iconTintColor,
                    textColor = contentColor,
                    iconSize = 14.dp,
                    textStyle = shadowStyle.copy(color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    onClick = {
                        onNavigateToMusic(
                            video.musicTrackId ?: video.id,
                            video.musicTrack ?: "Original Audio",
                            video.musicTrackArtist ?: video.user.username,
                            video.musicTrackImageUrl ?: video.user.avatarUrl
                        )
                    }
                )
            }

            if (showChallengeSheet) {
                val extractedTag = remember(video.description) {
                    Regex("#\\w+").findAll(video.description).map { it.value }.firstOrNull() ?: "#DesiSwagChallenge"
                }
                ChallengeHubBottomSheet(
                    challengeTag = extractedTag,
                    soundName = video.musicTrack ?: "Original Audio",
                    post = com.example.model.VideoPost(
                        id = video.id,
                        userId = video.userId,
                        username = video.user.username,
                        userAvatar = video.user.avatarUrl,
                        videoUrl = video.videoUrl,
                        thumbnailUrl = video.thumbnailUrl,
                        caption = video.description,
                        musicTrack = video.musicTrack ?: "Original Audio",
                        likes = realTimeLikesCount,
                        views = video.viewsCount
                    ),
                    allPosts = com.example.model.RepositoryManager.postRepository.cachedPosts,
                    onDismiss = { showChallengeSheet = false },
                    onJoinChallengeNow = { tag, audioName ->
                        showChallengeSheet = false
                        Toast.makeText(context, "🎬 Camera loaded with $tag & $audioName (15s Timer)", Toast.LENGTH_SHORT).show()
                        onRemixClick()
                    }
                )
            }

            if (showSuperTipSheet) {
                CreatorSuperTipSheet(
                    creatorId = video.userId.ifBlank { video.user.id },
                    creatorName = video.user.username.ifBlank { "Creator" },
                    creatorAvatar = video.user.avatarUrl,
                    postId = video.id,
                    onDismiss = { showSuperTipSheet = false },
                    onTipSuccess = { amt ->
                        showSuperTipSheet = false
                        tipSuccessAmount = amt
                    }
                )
            }
        }

        // Golden Confetti Celebration Overlay when Super-Tip succeeds
        tipSuccessAmount?.let { amt ->
            SuperTipConfettiOverlay(
                visible = true,
                amountInr = amt,
                creatorName = video.user.username.ifBlank { "Creator" },
                onFinished = { tipSuccessAmount = null }
            )
        }
        
        // Right Floating Action Column: Heart, Comment, Repost, Share, Save, Gift, Remix, More
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = if (isFullScreen) 18.dp else 66.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Heart (with animated rolling count below)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedLikeButton(
                    isLiked = isRealTimeLiked,
                    animationTrigger = doubleTapTrigger,
                    iconSize = 32.dp,
                    unlikedColor = Color.White,
                    onClick = {
                        com.example.utils.SoundManager.playClickSound()
                        if (!isRealUser) {
                            Toast.makeText(context, "Please login first to like videos!", Toast.LENGTH_SHORT).show()
                        } else {
                            if (!isRealTimeLiked) {
                                onShowHeart()
                            }
                            isRealTimeLiked = !isRealTimeLiked
                            if (isRealTimeLiked) {
                                realTimeLikesCount++
                            } else {
                                realTimeLikesCount--
                            }
                            onLike()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .bounceClick()
                        .clickable { onShowLikesList() }
                        .padding(2.dp)
                ) {
                    AnimatedCounterText(
                        count = realTimeLikesCount,
                        formatCount = { com.example.utils.NumberFormatUtils.formatCount(it) },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = shadowStyle
                    )
                }
            }

            // 2. Comment bubble (with count)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally, 
                modifier = Modifier.bounceClick().clickable {
                    triggerCrispMicroHaptic(context)
                    onCommentClick()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Comment",
                    tint = Color.White,
                    modifier = Modifier.size(29.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                AnimatedCounterText(
                    count = realTimeCommentsCount,
                    formatCount = { com.example.utils.NumberFormatUtils.formatCount(it) },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 3. 1:1 Instagram Repost loop (180-deg spring rotation + scale bounce + vibrant green #00E676)
            InstagramRepostButton(
                isReposted = isReelRepostedLocal,
                repostCount = realTimeRepostsCount.coerceAtLeast(video.repostsCount),
                iconSize = 29.dp,
                inactiveTint = Color.White,
                activeTint = Color(0xFF00E676),
                isVerticalLayout = true,
                formatCount = { com.example.utils.NumberFormatUtils.formatCount(it) },
                onClick = {
                    com.example.utils.SoundManager.playClickSound()
                    if (isExclusiveLocked) {
                        Toast.makeText(context, "Unlock this exclusive reel first to repost", Toast.LENGTH_SHORT).show()
                        onUnlockExclusiveClick()
                        return@InstagramRepostButton
                    }
                    if (!isReelRepostedLocal) {
                        isReelRepostedLocal = true
                        realTimeRepostsCount += 1
                        video.isReposted = true
                        showReelRepostPill = true
                        scope.launch {
                            RepositoryManager.socialRepository.repostContent(
                                contentId = video.id,
                                ownerUid = video.userId,
                                contentType = "reel",
                                note = "",
                                currentUserId = currentUserId,
                                currentUsername = RepositoryManager.currentUsername,
                                currentUserAvatar = RepositoryManager.currentUserAvatar
                            )
                        }
                    } else {
                        onRepostClick()
                    }
                }
            )

            // 4. 1:1 Instagram Paper-Plane Share (upward-right glide impulse + spring bounce)
            InstagramShareButton(
                shareCount = realTimeSharesCount.coerceAtLeast(video.sharesCount),
                iconSize = 27.dp,
                tint = Color.White,
                isVerticalLayout = true,
                formatCount = { com.example.utils.NumberFormatUtils.formatCount(it) },
                onClick = {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            com.example.model.RepositoryManager.firestore.collection("posts").document(video.id).update("sharesCount", com.google.firebase.firestore.FieldValue.increment(1))
                        } catch(e: Exception) {}
                    }
                    onShareClick()
                }
            )

            // 5. Bookmark (with count)
            LaunchedEffect(currentUserId, video.id) {
                if (currentUserId.isNotEmpty()) {
                    try {
                        val doc = com.example.model.RepositoryManager.firestore.collection("users")
                            .document(currentUserId)
                            .collection("savedPosts")
                            .document(video.id)
                            .get()
                            .await()
                        isSaved = doc.exists()
                    } catch(e: Exception) {}
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally, 
                modifier = Modifier.bounceClick().clickable { 
                    triggerCrispMicroHaptic(context)
                    if (!isRealUser) {
                        Toast.makeText(context, "Please login first to save videos!", Toast.LENGTH_SHORT).show()
                    } else {
                        val initialSaved = isSaved
                        isSaved = !initialSaved
                        realTimeSavesCount = if (initialSaved) (realTimeSavesCount - 1).coerceAtLeast(0) else (realTimeSavesCount + 1)
                        if (!initialSaved) {
                            Toast.makeText(context, "Saved to your profile", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Removed from saved", Toast.LENGTH_SHORT).show()
                        }
                        scope.launch {
                            val result = com.example.model.RepositoryManager.socialRepository.toggleSave(currentUserId, video.id)
                            isSaved = result
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Save",
                    tint = if (isSaved) Color(0xFFFFD700) else Color.White,
                    modifier = Modifier.size(29.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                AnimatedCounterText(
                    count = realTimeSavesCount,
                    formatCount = { com.example.utils.NumberFormatUtils.formatCount(it) },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // 5.5 Super Gift / Tip Creator Button (Direct Creator Earning on Reels)
            if (video.userId != currentUserId && video.userId.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .bounceClick()
                        .clickable {
                            triggerCrispMicroHaptic(context, strong = true)
                            if (!isRealUser) {
                                Toast.makeText(context, "Please login first to gift creators!", Toast.LENGTH_SHORT).show()
                            } else {
                                showReelGiftSheet = true
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFF2A6D), Color(0xFFFFB300))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = "Send Gift",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Gift",
                        color = Color(0xFFFFD700),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // 5b. Reels Duet / Remix Studio Button
            var showReelRemixSheet by remember { mutableStateOf(false) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .bounceClick()
                    .clickable {
                        triggerCrispMicroHaptic(context, strong = false)
                        if (isExclusiveLocked) {
                            Toast.makeText(context, "Unlock this exclusive reel first to remix", Toast.LENGTH_SHORT).show()
                            onUnlockExclusiveClick()
                        } else {
                            showReelRemixSheet = true
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.42f))
                        .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Compare,
                        contentDescription = "Remix Duet",
                        tint = Color(0xFF00F0FF),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Remix",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 6. 3-dots menu
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.bounceClick().clickable { showMoreOptionsDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "More options",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            if (showReelRemixSheet) {
                ReelsRemixAndDuetSheet(
                    originalVideo = video,
                    onDismiss = { showReelRemixSheet = false }
                )
            }
        }
        }

        // Fixed overlay Repost Toast positioned above the bottom nav (never affects layout flow)
        val currentUserRealtime by com.example.model.RepositoryManager.currentUserState.collectAsStateWithLifecycle()
        RepostFloatingPill(
            visible = showReelRepostPill,
            userAvatarUrl = currentUserRealtime?.avatarUrl ?: com.example.model.RepositoryManager.currentUserAvatar,
            onAddThoughtClick = {
                showReelRepostPill = false
                onRepostClick()
            },
            onUndoClick = {
                showReelRepostPill = false
                isReelRepostedLocal = false
                realTimeRepostsCount = (realTimeRepostsCount - 1).coerceAtLeast(0)
                video.isReposted = false
                video.repostsCount = realTimeRepostsCount
                if (currentUserId.isNotEmpty()) {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val postRef = RepositoryManager.firestore.collection("posts").document(video.id)
                            RepositoryManager.firestore.runTransaction { transaction ->
                                val snap = transaction.get(postRef)
                                if (snap.exists()) {
                                    val users = (snap.get("repostedByUserIds") as? List<*>)
                                        ?.filterIsInstance<String>()
                                        ?.toMutableList() ?: mutableListOf()
                                    users.remove(currentUserId)
                                    transaction.update(postRef, "repostedByUserIds", users)
                                    transaction.update(postRef, "repostsCount", users.size)
                                }
                            }.await()
                        } catch (_: Exception) {}
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (isFullScreen) 18.dp else 72.dp)
        )

        // Full-Screen Animated Super-Gift Burst Overlay on Reel
        AnimatedGiftBurstOverlay(
            event = reelGiftBurst,
            onFinished = { reelGiftBurst = null },
            modifier = Modifier.fillMaxSize()
        )

        if (showReelGiftSheet) {
            CreatorGiftBottomSheet(
                creatorId = video.userId,
                creatorUsername = video.user.username.ifBlank { "Creator" },
                contentId = video.id,
                contentType = "REEL",
                onDismiss = { showReelGiftSheet = false },
                onGiftSent = { ev -> reelGiftBurst = ev }
            )
        }
    }

    if (showMoreOptionsDialog) {
        ReelsOptionsBottomSheet(
            video = video,
            currentUserId = currentUserId,
            isRealUser = isRealUser,
            isMuted = isMuted,
            onToggleMute = { toggleSound() },
            onDismiss = { showMoreOptionsDialog = false },
            onShareClick = onShareClick,
            onSaveToggle = {
                val initialSaved = isSaved
                isSaved = !initialSaved
                realTimeSavesCount = if (initialSaved) (realTimeSavesCount - 1).coerceAtLeast(0) else (realTimeSavesCount + 1)
                scope.launch {
                    val result = com.example.model.RepositoryManager.socialRepository.toggleSave(currentUserId, video.id)
                    isSaved = result
                }
            },
            onNotInterested = {
                onNotInterested()
                showMoreOptionsDialog = false
            },
            onHideAccount = {
                onHideAccount()
                showMoreOptionsDialog = false
            },
            onDeleteVideo = {
                onDeleteVideo()
                showMoreOptionsDialog = false
            },
            onNavigateToProfile = { onAvatarClick() },
            onRemixClick = {
                onRemixClick()
                showMoreOptionsDialog = false
            }
        )
    }

    if (showCollaboratorsDialog) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showCollaboratorsDialog = false },
            containerColor = Color(0xFF1E1E1E),
            scrimColor = Color.Transparent,
            dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Collaborators",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
                )
                
                val allAuthors = listOf(video.user) + video.collaborators
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(allAuthors) { author ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick().clickable {
                                    showCollaboratorsDialog = false
                                    if (author.id == video.user.id) onAvatarClick()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = author.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = author.username,
                                        color = androidx.compose.ui.graphics.Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    if (author.isVerified) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        VerificationBadge(user = author, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                            
                            if (currentUserId.isNotEmpty() && author.id != currentUserId) {
                                var isFollowingAuthor by remember(author.id) { mutableStateOf(false) }
                                LaunchedEffect(author.id, currentUserId) {
                                    com.example.model.RepositoryManager.socialRepository.isFollowing(currentUserId, author.id).collect { isFollowingAuthor = it }
                                }
                                Button(
                                    onClick = { 
                                        if (isFollowingAuthor) {
                                            com.example.utils.SoundManager.playClickSound()
                                            scope.launch {
                                                try {
                                                    com.example.model.RepositoryManager.socialRepository.unfollowUser(currentUserId, author.id)
                                                    isFollowingAuthor = false
                                                } catch(e: Exception) {}
                                            }
                                        } else {
                                            com.example.utils.SoundManager.playClickSound()
                                            scope.launch {
                                                try {
                                                    com.example.model.RepositoryManager.socialRepository.followUser(currentUserId, author.id)
                                                    isFollowingAuthor = true
                                                } catch(e: Exception) {}
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isFollowingAuthor) Color(0xFF2B2B2B) else Color(0xFF375FFF)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(if (isFollowingAuthor) "Following" else "Follow", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }

                                if (currentUserId == video.userId && author.id != video.userId) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                com.example.model.RepositoryManager.postRepository.removeCollaborator(video.id, author.id)
                                                showCollaboratorsDialog = false
                                                Toast.makeText(context, "Collaborator removed", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Remove", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showTrialReelSheet) {
            TrialReelPerformanceSheet(
                postId = video.id,
                onDismiss = { showTrialReelSheet = false },
                onPublishedToEveryone = {
                    video.isTrialReel = false
                    showTrialReelSheet = false
                    Toast.makeText(context, "Published to everyone!", Toast.LENGTH_SHORT).show()
                },
                onDeleteTrial = {
                    showTrialReelSheet = false
                    scope.launch {
                        com.example.repository.FlagshipFeaturesRepository.deleteTrialReel(video.id)
                        Toast.makeText(context, "Trial Reel deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        if (showAddYoursSheet) {
            AddYoursPromptSheet(
                promptId = video.addYoursPromptId ?: video.id,
                promptText = video.addYoursPromptTitle ?: "",
                creatorUsername = video.user.username,
                onDismiss = { showAddYoursSheet = false },
                onAddYoursClick = {
                    showAddYoursSheet = false
                    Toast.makeText(context, "Tap + icon on main screen to create with this prompt!", Toast.LENGTH_LONG).show()
                },
                onOpenContent = { _ ->
                    showAddYoursSheet = false
                }
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionsBottomSheet(
    video: com.example.model.Video,
    currentUserId: String,
    isRealUser: Boolean,
    onDismiss: () -> Unit,
    onShareClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isOwner = currentUserId.isNotEmpty() && currentUserId == video.userId
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editCaptionText by remember(video.description) { mutableStateOf(video.description) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showInsightsDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = if (isOwner) "Manage Video" else "Options",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp).align(Alignment.CenterHorizontally)
            )

            // Horizontal scrolling quick actions
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isOwner) {
                    item { QuickActionItem(Icons.Default.Analytics, "Insights") { showInsightsDialog = true } }
                    item { QuickActionItem(Icons.Default.TrendingUp, "Boost") { Toast.makeText(context, "Opening Boost options...", Toast.LENGTH_SHORT).show(); onDismiss() } }
                    item { QuickActionItem(Icons.Default.Download, "Save") { 
        Toast.makeText(context, "Downloading video...", Toast.LENGTH_SHORT).show()
        try {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(video.videoUrl))
            request.setTitle("SocialIndia Video")
            request.setDescription("Downloading video...")
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MOVIES, "SocialIndia_${System.currentTimeMillis()}.mp4")
            val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            downloadManager.enqueue(request)
        } catch(e: Exception) {
            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
        }
        onDismiss()
 } }
                    item { QuickActionItem(Icons.Default.Settings, "Settings") { Toast.makeText(context, "Video settings...", Toast.LENGTH_SHORT).show(); onDismiss() } }
                } else {
                    item { QuickActionItem(Icons.Default.Reply, "Share") { onShareClick(); onDismiss() } }
                    item { QuickActionItem(Icons.Default.Link, "Copy Link") { 
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Reel Link", "https://shorts.app/reel/${video.id}")
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
        onDismiss()
 } }
                    item { QuickActionItem(Icons.Default.Download, "Save") { 
        Toast.makeText(context, "Downloading video...", Toast.LENGTH_SHORT).show()
        try {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(video.videoUrl))
            request.setTitle("SocialIndia Video")
            request.setDescription("Downloading video...")
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MOVIES, "SocialIndia_${System.currentTimeMillis()}.mp4")
            val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            downloadManager.enqueue(request)
        } catch(e: Exception) {
            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
        }
        onDismiss()
 } }
                    item { QuickActionItem(Icons.Default.Repeat, "Remix") { Toast.makeText(context, "Opening Remix editor...", Toast.LENGTH_SHORT).show(); onDismiss() } }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.HorizontalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))

            // Vertical list options
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                if (isOwner) {
                    item { ListOptionItem(Icons.Default.Edit, "Edit", "Change caption, tags, or location") { showEditDialog = true } }
                    item { ListOptionItem(Icons.Default.ChatBubbleOutline, "Turn Off Comments", "Disable comments for this video") { Toast.makeText(context, "Comment settings updated", Toast.LENGTH_SHORT).show(); onDismiss() } }
                    item { ListOptionItem(Icons.Default.Repeat, "Turn Off Remixing", "Stop others from remixing this video") { Toast.makeText(context, "Remixing settings updated", Toast.LENGTH_SHORT).show(); onDismiss() } }
                    item { ListOptionItem(Icons.Default.Archive, "Archive", "Hide video from your profile") { Toast.makeText(context, "Video archived", Toast.LENGTH_SHORT).show(); onDismiss() } }
                    item { 
                        ListOptionItem(Icons.Default.Delete, "Delete", "Permanently remove this video", iconColor = Color(0xFFFE2C55), textColor = Color(0xFFFE2C55)) { 
                            showDeleteDialog = true
                        }
                    }
                } else {
                    item { 
                        ListOptionItem(Icons.Default.BookmarkBorder, "Save", "Add to your saved collection") { 
                            if (!isRealUser) {
                                Toast.makeText(context, "Please login first to save videos!", Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {
                                    try {
                                        com.example.model.RepositoryManager.firestore.collection("users")
                                            .document(currentUserId)
                                            .collection("savedPosts")
                                            .document(video.id)
                                            .set(mapOf("timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                                        com.example.model.RepositoryManager.firestore.collection("posts").document(video.id).update("savesCount", com.google.firebase.firestore.FieldValue.increment(1))
                                        Toast.makeText(context, "Saved to your profile", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {}
                                }
                            }
                            onDismiss()
                        }
                    }
                }
                item { ListOptionItem(Icons.Default.VisibilityOff, "Not Interested", "Show fewer videos like this") { Toast.makeText(context, "We'll show fewer videos like this", Toast.LENGTH_SHORT).show(); onDismiss() } }
                item { ListOptionItem(Icons.Default.ClosedCaption, "Captions", "Turn captions on or off") { Toast.makeText(context, "Captions enabled/disabled", Toast.LENGTH_SHORT).show(); onDismiss() } }
                item { ListOptionItem(Icons.Default.Info, "About this Account", "See info about ${video.user.username}") { Toast.makeText(context, "Showing account info...", Toast.LENGTH_SHORT).show(); onDismiss() } }
                item { ListOptionItem(Icons.Default.Block, "Hide / Block Account", "Block ${video.user.username}") { Toast.makeText(context, "Account blocked", Toast.LENGTH_SHORT).show(); onDismiss() } }
                item { ListOptionItem(Icons.Default.Report, "Report", "Report this video for violation", iconColor = Color(0xFFFE2C55), textColor = Color(0xFFFE2C55)) { showReportDialog = true } }
            }
        }
    }

    if (showEditDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Video Caption", color = androidx.compose.ui.graphics.Color.White) },
            text = {
                OutlinedTextField(
                    value = editCaptionText,
                    onValueChange = { editCaptionText = it },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            RepositoryManager.firestore.collection("videos").document(video.id).update("description", editCaptionText).await()
                            Toast.makeText(context, "Caption updated successfully", Toast.LENGTH_SHORT).show()
                            showEditDialog = false
                            onDismiss()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to update caption", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("Save", color = Color(0xFF1DB954))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF2C2C2C)
        )
    }
    
    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Video", color = androidx.compose.ui.graphics.Color.White) },
            text = { Text("Are you sure you want to permanently delete this video? This action cannot be undone.", color = Color.Gray) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    scope.launch {
                        try {
                            com.example.model.RepositoryManager.firestore.collection("posts").document(video.id).delete()
                            Toast.makeText(context, "Video deleted successfully", Toast.LENGTH_SHORT).show()
                        } catch(e: Exception) {
                            Toast.makeText(context, "Failed to delete video", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showDeleteDialog = false
                    onDismiss()
                }) {
                    Text("Delete", color = Color(0xFFFE2C55), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        )
    }

    if (showReportDialog) {
        val reportReasons = listOf("Spam or misleading", "Inappropriate content", "Hate speech", "Copyright violation", "Other")
        ModalBottomSheet(
            onDismissRequest = { showReportDialog = false },
            containerColor = Color(0xFF1E1E1E),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp)) {
                Text("Why are you reporting this video?", color = androidx.compose.ui.graphics.Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                reportReasons.forEach { reason ->
                    Text(
                        text = reason,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    try {
                                        com.example.model.RepositoryManager.firestore.collection("reports").add(mapOf(
                                            "postId" to video.id,
                                            "reporterId" to currentUserId,
                                            "reason" to reason,
                                            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                        ))
                                        com.example.model.RepositoryManager.firestore.collection("posts").document(video.id).update("reportsCount", com.google.firebase.firestore.FieldValue.increment(1))
                                        Toast.makeText(context, "Report submitted successfully", Toast.LENGTH_SHORT).show()
                                    } catch(e: Exception) {}
                                }
                                showReportDialog = false
                                onDismiss()
                            }
                            .padding(vertical = 14.dp)
                    )
                }
            }
        }
    }

    if (showInsightsDialog) {
        ModalBottomSheet(
            onDismissRequest = { showInsightsDialog = false },
            containerColor = Color(0xFF1E1E1E),
        ) {
Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 32.dp), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Video Insights", color = androidx.compose.ui.graphics.Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    InsightItem(Icons.Default.PlayArrow, com.example.utils.NumberFormatUtils.formatCount(video.viewsCount), "Views")
                    InsightItem(Icons.Default.Visibility, com.example.utils.NumberFormatUtils.formatCount(video.viewsCount + video.sharesCount * 2), "Reach")
                    InsightItem(Icons.Default.Favorite, com.example.utils.NumberFormatUtils.formatCount(video.likesCount), "Likes")
                    InsightItem(Icons.Default.ChatBubbleOutline, com.example.utils.NumberFormatUtils.formatCount(video.commentsCount), "Comments")
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    InsightItem(Icons.Default.BookmarkBorder, com.example.utils.NumberFormatUtils.formatCount(video.savesCount), "Saves")
                    InsightItem(Icons.Default.Send, com.example.utils.NumberFormatUtils.formatCount(video.sharesCount), "Shares")
                    InsightItem(Icons.Default.Repeat, com.example.utils.NumberFormatUtils.formatCount(video.repostsCount), "Reposts")
                    InsightItem(Icons.Default.Timer, "0:12", "Avg Watch")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun InsightItem(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = label, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = value, color = androidx.compose.ui.graphics.Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
    }
}

@Composable
fun QuickActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.bounceClick().clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(Color(0xFF2C2C2C), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ListOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    title: String, 
    subtitle: String? = null, 
    iconColor: Color = Color.White,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick().clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentBottomSheet(
    video: com.example.model.Video,
    onDismiss: () -> Unit,
    currentUserId: String,
    onProfileClick: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        scrimColor = Color.Transparent,
        dragHandle = { BottomSheetDefaults.DragHandle(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f)) }
    ) {
        val context = LocalContext.current
        val videoId = video.id
        val commentsList by RepositoryManager.socialRepository.getComments(videoId).collectAsStateWithLifecycle(initialValue = emptyList())
        var commentText by remember { mutableStateOf("") }
        var selectedMemeUrl by remember { mutableStateOf<String?>(null) }
        var showMemeSelector by remember { mutableStateOf(false) }
        var showGifPicker by remember { mutableStateOf(false) }
        var selectedGifUrl by remember { mutableStateOf<String?>(null) }
        var replyingToComment by remember { mutableStateOf<com.example.model.Comment?>(null) }
        val scope = rememberCoroutineScope()
        val floatingEmojis = remember { mutableStateListOf<FloatingEmoji>() }

        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .padding(horizontal = 16.dp)
            ) {
                // Header (Instagram style search bar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF6A92D4),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Search · " + (video.description.takeIf { it.isNotBlank() } ?: "comments"),
                        color = Color(0xFF6A92D4),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f))

                // Separate top-level comments and sub-comment replies
                val topLevelComments = commentsList.filter { it.parentCommentId.isNullOrEmpty() }
                val repliesMap = commentsList.filter { !it.parentCommentId.isNullOrEmpty() }.groupBy { it.parentCommentId }

                // Comments List
                if (topLevelComments.isEmpty()) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "No comments yet", 
                            color = androidx.compose.ui.graphics.Color.White, 
                            fontSize = 24.sp, 
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Start the conversation.", 
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f), 
                            fontSize = 14.sp, 
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(topLevelComments) { comment ->
                            CommentItemRow(
                                comment = comment,
                                currentUserId = currentUserId,
                                videoId = video.id,
                                onReplyClick = { replyingToComment = comment },
                                onProfileClick = onProfileClick,
                                replies = repliesMap[comment.id] ?: emptyList()
                            )
                        }
                    }
                }

                // Optional Attached sticker review preview before posting
                selectedMemeUrl?.let { meme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(6.dp))) {
                            AsyncImage(model = meme, contentDescription = null, contentScale = ContentScale.Crop)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Meme Reaction Attached", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { com.example.utils.SoundManager.playClickSound();  selectedMemeUrl = null }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red)
                        }
                    }
                }

                // Meme Quick Selection Tray (collapsible)
                if (showMemeSelector) {
                    val reactionMemes = listOf(
                        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
                        "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=150",
                        "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=150",
                        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150"
                    )
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text("Attach Reaction Meme:", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(reactionMemes) { meme ->
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .bounceClick().clickable { com.example.utils.SoundManager.playClickSound(); 
                                            selectedMemeUrl = meme
                                            showMemeSelector = false
                                        }
                                ) {
                                    AsyncImage(model = meme, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }

                // Replying To Status Banner
                replyingToComment?.let { replyComment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Replying to @${replyComment.username}",
                            color = Color(0xFF375FFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { replyingToComment = null },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Quick Reactions
                val quickReactions = listOf("❤️", "🙌", "🔥", "👏", "😢", "😍", "😮", "😂")
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    items(quickReactions) { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 28.sp,
                            modifier = Modifier
                                .bounceClick().clickable {
                                    commentText += emoji
                                }
                                .padding(4.dp)
                        )
                    }
                }

                // User Avatar fetch
                var currentUserAvatar by remember { mutableStateOf("") }
                LaunchedEffect(currentUserId) {
                    try {
                        if (currentUserId.isNotEmpty()) {
                            val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(currentUserId).get().await()
                            currentUserAvatar = doc.getString("avatar_url") ?: doc.getString("profilePicUrl") ?: ""
                        }
                    } catch (e: Exception) {}
                }

                // Selected GIF / Meme preview chip
                if (selectedGifUrl != null || selectedMemeUrl != null) {
                    val previewUrl = selectedGifUrl ?: selectedMemeUrl ?: ""
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF22222A))
                        ) {
                            AsyncImage(
                                model = previewUrl,
                                contentDescription = "Selected GIF",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GIF attached", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { selectedGifUrl = null; selectedMemeUrl = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove GIF", tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Input Bar Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp, top = 8.dp)
                ) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Gray)) {
                        AsyncImage(
                            model = currentUserAvatar.takeIf { it.isNotEmpty() } ?: "https://api.dicebear.com/7.x/avataaars/png?seed=$currentUserId",
                            contentDescription = "My Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (replyingToComment != null) "Reply to @${replyingToComment?.username}..." else "What do you think of this?", color = Color.Gray, fontSize = 14.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                                Box(modifier = Modifier
                                    .bounceClick().clickable { showGifPicker = true }
                                    .border(1.dp, Color(0xFFEE1D52), RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEE1D52).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("GIF", color = Color(0xFFEE1D52), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                var showCommentGiftSheet by remember(video.id) { mutableStateOf(false) }
                                Icon(
                                    imageVector = Icons.Default.CardGiftcard,
                                    contentDescription = "Gift Creator",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(20.dp).bounceClick().clickable {
                                        showCommentGiftSheet = true
                                    }
                                )
                                if (showCommentGiftSheet && video.userId.isNotEmpty()) {
                                    CreatorGiftBottomSheet(
                                        creatorId = video.userId,
                                        creatorUsername = video.user.username.ifBlank { "Creator" },
                                        contentId = video.id,
                                        contentType = "REEL",
                                        onDismiss = { showCommentGiftSheet = false },
                                        onGiftSent = { ev ->
                                            Toast.makeText(context, "Sent ${ev.emoji} ${ev.giftName} to @${ev.creatorName}!", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                if (commentText.isNotBlank() || selectedGifUrl != null || selectedMemeUrl != null) {
                                    IconButton(
                                        modifier = Modifier.size(28.dp),
                                        onClick = { 
                                            com.example.utils.SoundManager.playClickSound();  
                                            if (currentUserId.isEmpty()) {
                                                return@IconButton
                                            }
                                            if (commentText.isNotBlank() || selectedGifUrl != null || selectedMemeUrl != null) {
                                                scope.launch {
                                                    try {
                                                        val finalGifUrl = selectedGifUrl ?: selectedMemeUrl
                                                        RepositoryManager.socialRepository.addComment(
                                                            videoId = video.id,
                                                            userId = currentUserId,
                                                            content = commentText,
                                                            gifUrl = finalGifUrl,
                                                            parentCommentId = replyingToComment?.id,
                                                            parentUsername = replyingToComment?.username
                                                        )
                                                        commentText = ""
                                                        selectedGifUrl = null
                                                        selectedMemeUrl = null
                                                        replyingToComment = null
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("Comment", "Failed to add comment", e)
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFFEE1D52), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    )
                }
            }
            
            // GIF & Sticker Picker Modal Sheet
            if (showGifPicker) {
                com.example.ui.GifPickerBottomSheet(
                    onDismissRequest = { showGifPicker = false },
                    onGifSelected = { gif ->
                        selectedGifUrl = gif.fullUrl
                        showGifPicker = false
                    },
                    onStickerSelected = { sticker ->
                        selectedGifUrl = sticker.mediaUrl.ifEmpty { sticker.thumbnailUrl }
                        showGifPicker = false
                        com.example.model.RepositoryManager.stickerRepository.recordStickerUsage(currentUserId, sticker)
                    }
                )
            }
            
            // Draw Floating Emojis
            floatingEmojis.forEach { floatingEmoji ->
                AnimatedFloatingEmoji(
                    emoji = floatingEmoji.emoji,
                    xOffset = floatingEmoji.xOffset,
                    onAnimationFinished = {
                        floatingEmojis.remove(floatingEmoji)
                    }
                )
            }
        }
    }
}

fun extractMentions(text: String): List<String> {
    return Regex("@(\\w+)").findAll(text).map { it.groupValues[1] }.toList()
}

@Composable
fun CommentItemRow(
    comment: com.example.model.Comment,
    currentUserId: String,
    videoId: String,
    onReplyClick: () -> Unit,
    onProfileClick: (String) -> Unit,
    replies: List<com.example.model.Comment>
) {
    val scope = rememberCoroutineScope()
    val hasLiked = comment.likedBy.contains(currentUserId)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Profile image
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
                    .clickable {
                        onProfileClick(comment.userId)
                    }
            ) {
                AsyncImage(
                    model = comment.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.username,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            onProfileClick(comment.userId)
                        }
                    )
                    if (comment.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = Color(0xFF1DA1F2),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = android.text.format.DateUtils.getRelativeTimeSpanString(comment.timestamp?.time ?: System.currentTimeMillis()).toString().replace(" weeks ago", "w").replace(" days ago", "d").replace(" hours ago", "h").replace(" minutes ago", "m").replace(" seconds ago", "s"),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                if (comment.text.isNotEmpty()) {
                    Text(
                        text = comment.text,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 14.sp
                    )
                }
                    if (!comment.gifUrl.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF22222A))
                        ) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val imageLoader = remember {
                                coil.ImageLoader.Builder(context)
                                    .components {
                                        if (android.os.Build.VERSION.SDK_INT >= 28) {
                                            add(coil.decode.ImageDecoderDecoder.Factory())
                                        } else {
                                            add(coil.decode.GifDecoder.Factory())
                                        }
                                    }
                                    
                            }
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(context)
                                    .data(comment.gifUrl)
                                    
.crossfade(true).build(),
                                imageLoader = imageLoader.build(),
                                contentDescription = "Comment GIF",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                            )
                        }
                    }
                // Parse mentions and show ID logo
                    val mentionedUsernames = extractMentions(comment.text)
                    var mentionedUsers by remember(comment.text) { mutableStateOf<List<com.example.model.User>>(emptyList()) }
                    LaunchedEffect(mentionedUsernames) {
                        try {
                            val users = mutableListOf<com.example.model.User>()
                            for (username in mentionedUsernames) {
                                val query = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users")
                                    .whereEqualTo("username", username).limit(1).get().await()
                                val doc = query.documents.firstOrNull()
                                if (doc != null) {
                                    users.add(com.example.model.User(id = doc.id, username = username, avatarUrl = doc.getString("avatar_url") ?: doc.getString("profilePicUrl") ?: ""))
                                }
                            }
                            mentionedUsers = users
                        } catch (e: Exception) {}
                    }
                    
                    if (mentionedUsers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (mu in mentionedUsers) {
                                Row(
                                    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).clickable { onProfileClick(mu.id) }.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = mu.avatarUrl.takeIf { it.isNotEmpty() } ?: "https://api.dicebear.com/7.x/avataaars/png?seed=${mu.id}",
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("@${mu.username}", color = Color(0xFF375FFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                Spacer(modifier = Modifier.height(6.dp))
                var showCommentVideoReplySheet by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Reply",
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onReplyClick() }
                    )
                    Text(
                        text = "🎬 Video Reply",
                        color = Color(0xFF00F0FF),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showCommentVideoReplySheet = true }
                    )
                }
                if (showCommentVideoReplySheet) {
                    CommentVideoReplySheet(
                        targetCommentUsername = comment.username.ifBlank { "user" },
                        targetCommentText = comment.text,
                        sourceVideoId = videoId,
                        onDismiss = { showCommentVideoReplySheet = false }
                    )
                }
            }
            
            // Like column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                AnimatedLikeButton(
                    isLiked = hasLiked,
                    iconSize = 16.dp,
                    unlikedColor = Color.White.copy(alpha = 0.6f),
                    likedColor = Color.Red,
                    modifier = Modifier.size(24.dp),
                    onClick = {
                        scope.launch {
                            RepositoryManager.socialRepository.toggleCommentLikeFirebase(videoId, comment.id, currentUserId)
                        }
                    }
                )
                if (comment.likedBy.isNotEmpty()) {
                    Text(
                        text = comment.likedBy.size.toString(),
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
            }
        }
        
        // Nested replies mapping
        if (replies.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            replies.forEach { reply ->
                val replyHasLiked = reply.likedBy.contains(currentUserId)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, top = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Gray.copy(alpha = 0.3f))
                            .bounceClick().clickable {
                                onProfileClick(reply.userId)
                            }
                    ) {
                        AsyncImage(
                            model = reply.avatarUrl.takeIf { it.isNotEmpty() } ?: "https://api.dicebear.com/7.x/avataaars/png?seed=${reply.userId}",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = reply.username,
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    onProfileClick(reply.userId)
                                }
                            )
                            if (reply.isVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF1DA1F2),
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        if (reply.text.isNotEmpty()) {
                            Text(
                                text = reply.text,
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 13.sp
                            )
                        }
                        if (!reply.gifUrl.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF22222A))
                            ) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val imageLoader = remember {
                                    coil.ImageLoader.Builder(context)
                                        .components {
                                            if (android.os.Build.VERSION.SDK_INT >= 28) {
                                                add(coil.decode.ImageDecoderDecoder.Factory())
                                            } else {
                                                add(coil.decode.GifDecoder.Factory())
                                            }
                                        }
                                        
                                }
                                AsyncImage(
                                    model = coil.request.ImageRequest.Builder(context)
                                        .data(reply.gifUrl)
                                        
.crossfade(true).build(),
                                    imageLoader = imageLoader.build(),
                                    contentDescription = "GIF",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp)
                                )
                            }
                        }
                    }
                    
                    // Reply Like
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    RepositoryManager.socialRepository.toggleCommentLikeFirebase(videoId, reply.id, currentUserId)
                                }
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = if (replyHasLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (replyHasLiked) Color.Red else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (reply.likedBy.isNotEmpty()) {
                            Text(
                                text = reply.likedBy.size.toString(),
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedFloatingEmoji(emoji: String, xOffset: Float, onAnimationFinished: () -> Unit) {
    val transition = rememberInfiniteTransition()
    var isRunning by remember { mutableStateOf(true) }
    
    val yAnim = remember { Animatable(1000f) }
    val alphaAnim = remember { Animatable(1f) }
    
    LaunchedEffect(Unit) {
        launch {
            yAnim.animateTo(
                targetValue = -200f,
                animationSpec = tween(durationMillis = 2000, easing = LinearOutSlowInEasing)
            )
        }
        launch {
            alphaAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
            )
        }
        onAnimationFinished()
    }
    
    if (isRunning) {
        Text(
            text = emoji,
            fontSize = 32.sp,
            modifier = Modifier
                .offset(x = xOffset.dp, y = yAnim.value.dp)
                .alpha(alphaAnim.value)
        )
    }
}



@Composable
fun ShareActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: Color,
    bgColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )) {
        Box(
            modifier = Modifier.size(60.dp).clip(CircleShape).background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = Color.Black, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}



@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun CreatorProfileOverlay(
    user: User,
    videos: List<Video>,
    onDismiss: () -> Unit,
    onPlayVideo: (Video) -> Unit,
    onMessageClick: (User) -> Unit
) {
    val creatorVideos = remember(user, videos) {
        videos.filter { it.userId == user.id || it.user.username == user.username }
    }
    
    var currentUserId by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
    }
    
    var showFollowList by remember { mutableStateOf(false) }
    var selectedFollowTab by remember { mutableStateOf(1) } // 0 -> Following, 1 -> Followers
    var previewVideo by remember { mutableStateOf<Video?>(null) }

    val followersListState = remember(user.id) {
        if (user.id.isNotEmpty()) {
            com.example.model.RepositoryManager.socialRepository.getFollowers(user.id)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val followersList = followersListState.value

    val followingListState = remember(user.id) {
        if (user.id.isNotEmpty()) {
            com.example.model.RepositoryManager.socialRepository.getFollowing(user.id)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val followingList = followingListState.value

    val userOnlineStatusState = remember(user.id) {
        if (user.id.isNotEmpty()) {
            com.example.model.RepositoryManager.socialRepository.observeUserStatus(user.id)
        } else {
            kotlinx.coroutines.flow.flowOf("offline")
        }
    }.collectAsStateWithLifecycle(initialValue = "offline")
    val userOnlineStatus = userOnlineStatusState.value == "online"
    
    val isFollowedRealtimeState = remember(currentUserId, user.id) {
        if (currentUserId.isNotEmpty() && user.id.isNotEmpty()) {
            com.example.model.RepositoryManager.socialRepository.isFollowing(currentUserId, user.id)
        } else {
            kotlinx.coroutines.flow.flowOf(false)
        }
    }.collectAsStateWithLifecycle(initialValue = false)
    val isFollowedRealtime = isFollowedRealtimeState.value

    val userStatsState = remember(user.id) {
        if (user.id.isNotEmpty()) {
            com.example.model.RepositoryManager.socialRepository.observeUserStats(user.id)
        } else {
            kotlinx.coroutines.flow.flowOf(Pair(0, 0))
        }
    }.collectAsStateWithLifecycle(initialValue = Pair(0, 0))
    val userStats = userStatsState.value

    val totalLikesCountState = remember(user.id) {
        if (user.id.isNotEmpty()) {
            com.example.model.RepositoryManager.socialRepository.getTotalLikesCount(user.id)
        } else {
            kotlinx.coroutines.flow.flowOf(0)
        }
    }.collectAsStateWithLifecycle(initialValue = 0)
    val totalLikesCount = totalLikesCountState.value

    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = "Profile",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            HorizontalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f))

            // Main body
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        // Avatar in center
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                                    .background(Color.Gray),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(if (userOnlineStatus) Color(0xFF4CAF50) else Color.Gray, CircleShape)
                                    .border(2.dp, Color.Black, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(user.username, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (user.isVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                VerificationBadge(user = user, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (userOnlineStatus) "• Online" else "• Offline",
                                color = if (userOnlineStatus) Color(0xFF4CAF50) else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(user.username.replace("@", "@"), color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        // Stats Row (Real-time counts)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .bounceClick()
                                    .clickable {
                                        com.example.utils.SoundManager.playClickSound()
                                        selectedFollowTab = 0 // Following
                                        showFollowList = true
                                    }
                            ) {
                                ProfileStat(com.example.utils.NumberFormatUtils.formatCount(userStats.second), "Following")
                            }
                            Box(modifier = Modifier.width(1.dp).height(12.dp).background(Color.White.copy(alpha = 0.1f)))
                            Box(
                                modifier = Modifier
                                    .bounceClick()
                                    .clickable {
                                        com.example.utils.SoundManager.playClickSound()
                                        selectedFollowTab = 1 // Followers
                                        showFollowList = true
                                    }
                            ) {
                                ProfileStat(com.example.utils.NumberFormatUtils.formatCount(userStats.first), "Followers")
                            }
                            Box(modifier = Modifier.width(1.dp).height(12.dp).background(Color.White.copy(alpha = 0.1f)))
                            ProfileStat(com.example.utils.NumberFormatUtils.formatCount(totalLikesCount), "Likes")
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Action buttons row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (user.id == currentUserId) {
                                Button(
                                    onClick = { com.example.utils.SoundManager.playClickSound() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("This is you! ✨", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { 
                                        com.example.utils.SoundManager.playClickSound()
                                        if (currentUserId.isNotEmpty() && user.id.isNotEmpty()) {
                                            scope.launch {
                                                if (isFollowedRealtime) {
                                                    com.example.model.RepositoryManager.socialRepository.unfollowUser(currentUserId, user.id)
                                                } else {
                                                    com.example.model.RepositoryManager.socialRepository.followUser(currentUserId, user.id)
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isFollowedRealtime) Color(0xFF333333) else Color(0xFFEE1D52)
                                    ),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(if (isFollowedRealtime) "Following" else "Follow", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { com.example.utils.SoundManager.playClickSound(); onMessageClick(user) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("Say hi 👋", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Bio
                        Text(
                            text = "Always ready for any promotion business! For collaborations contact us at mail@tiktok.com. TikTok Creator Partnership Partner.",
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                // Grid of Videos
                item {
                    Text(
                        "Videos",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                item {
                    if (creatorVideos.isEmpty()) {
                        Text("No videos yet.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(500.dp)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(creatorVideos) { video ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(3f / 4f)
                                        .background(Color(0xFF222222))
                                        .bounceClick().clickable { 
                                            com.example.utils.SoundManager.playClickSound()
                                            previewVideo = video 
                                        }
                                ) {
                                    val isImage = com.example.utils.MediaResolver.isImage(video.videoUrl)
                                    val thumbUrl = if (!isImage && video.videoUrl.contains("/raw/")) video.videoUrl.replace("/raw/", "/image/") else video.videoUrl
                                    
                                    val requestBuilder = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                        .data(thumbUrl)
                                        
.crossfade(true).build()
                                    if (!isImage) {
                                        /* requestBuilder.decoderFactory(coil.decode.VideoFrameDecoder.Factory()) */
                                    }
                                    
                                    AsyncImage(
                                        model = requestBuilder,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(com.example.utils.NumberFormatUtils.formatCount(video.viewsCount), color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showFollowList) {
            FollowsScreen(
                targetUserId = user.id,
                initialTab = if (selectedFollowTab == 0) "following" else "followers",
                onBack = { showFollowList = false },
                onNavigateToProfile = { uid: String ->
                    showFollowList = false
                }
            )
        }

        previewVideo?.let { videoToPlay ->
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { previewVideo = null }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val videoContext = androidx.compose.ui.platform.LocalContext.current
                        var pState by remember { mutableIntStateOf(androidx.media3.common.Player.STATE_IDLE) }
                        val videoPlayer = remember(videoToPlay.videoUrl) {
                            com.example.utils.ExoPlayerUtils.createExoPlayer(videoContext).apply {
                                addListener(object : androidx.media3.common.Player.Listener {
                                    override fun onPlaybackStateChanged(state: Int) {
                                        pState = state
                                    }
                                })
                                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                            }
                        }
                        
                        androidx.compose.runtime.LaunchedEffect(videoToPlay.videoUrl) {
                            val playUrl = com.example.utils.MediaResolver.resolveVideoUrl(videoToPlay.videoUrl)
                            videoPlayer.setMediaItem(androidx.media3.common.MediaItem.Builder().setUri(android.net.Uri.parse(playUrl)).build())
                            videoPlayer.prepare()
                            videoPlayer.playWhenReady = true
                        }
                        androidx.compose.runtime.DisposableEffect(videoPlayer) {
                            onDispose {
                                com.example.utils.ExoPlayerUtils.safeRelease(videoPlayer)
                            }
                        }
                        val isPlayingOrReady = pState == androidx.media3.common.Player.STATE_READY || pState == androidx.media3.common.Player.STATE_BUFFERING
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isPlayingOrReady,
                            enter = androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.fadeOut()
                        ) {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(videoContext)
                                    .data(videoToPlay.videoUrl)
                                    
.crossfade(true).build(),
                                contentDescription = "Video Thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                val view = android.view.LayoutInflater.from(ctx).inflate(com.example.R.layout.texture_player_view, null, false) as androidx.media3.ui.PlayerView
                                view.apply {
                                    useController = true
                                    player = videoPlayer
                                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                }
                            },
                            onRelease = { playerView ->
                                playerView.player = null
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Close button on top-right
                        IconButton(
                            onClick = { previewVideo = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Preview", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
    BackHandler {
        if (showFollowList) {
            showFollowList = false
        } else if (previewVideo != null) {
            previewVideo = null
        } else {
            onDismiss()
        }
    }
}

@Composable
fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}

@Composable
fun InstagramShimmerLoading() {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "instagram_loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    val spinnerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "instagram_spinner_rotation"
    )

    val instagramGradient = Brush.sweepGradient(
        listOf(
            Color(0xFFFEDA75), // Yellow
            Color(0xFFFA7E1E), // Orange
            Color(0xFFD62976), // Pink
            Color(0xFF962FBF), // Purple
            Color(0xFF4F5BD5), // Blue
            Color(0xFFFEDA75)  // Cycle back
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12))
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Instagram Top Bar Skeleton
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Logo Placeholder
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(110.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = alpha * 0.4f))
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = alpha * 0.3f)))
                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = alpha * 0.3f)))
                }
            }

            // Instagram Stories Tray Skeleton
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(5) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(2.dp, instagramGradient, CircleShape)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(Color.Gray.copy(alpha = alpha * 0.5f))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .height(10.dp)
                                .width(46.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.Gray.copy(alpha = alpha * 0.4f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Instagram Feed Card Skeleton
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // Post header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Gray.copy(alpha = alpha * 0.6f))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Box(modifier = Modifier.height(12.dp).width(90.dp).clip(RoundedCornerShape(3.dp)).background(Color.Gray.copy(alpha = alpha * 0.6f)))
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.height(9.dp).width(50.dp).clip(RoundedCornerShape(2.dp)).background(Color.Gray.copy(alpha = alpha * 0.35f)))
                    }
                }

                // Post media box with shimmer and signature Instagram rotating spinner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF18181E).copy(alpha = alpha * 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Iconic Instagram Rotating Gradient Spinner Ring
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .size(46.dp)
                            .graphicsLayer {
                                this.rotationZ = spinnerRotation
                            }
                    ) {
                        drawArc(
                            brush = instagramGradient,
                            startAngle = 0f,
                            sweepAngle = 285f,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 3.5.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                    }
                }

                // Post action bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.Gray.copy(alpha = alpha * 0.5f)))
                        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.Gray.copy(alpha = alpha * 0.5f)))
                        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.Gray.copy(alpha = alpha * 0.5f)))
                    }
                    Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(3.dp)).background(Color.Gray.copy(alpha = alpha * 0.5f)))
                }

                // Likes count & caption line
                Box(modifier = Modifier.padding(start = 14.dp).height(12.dp).width(120.dp).clip(RoundedCornerShape(3.dp)).background(Color.Gray.copy(alpha = alpha * 0.5f)))
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.padding(start = 14.dp).height(11.dp).width(200.dp).clip(RoundedCornerShape(3.dp)).background(Color.Gray.copy(alpha = alpha * 0.35f)))
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

data class BombParticle(
    val id: Int,
    val emoji: String,
    val initialSpeedX: Float,
    val initialSpeedY: Float,
    val rotationSpeed: Float,
    val scaleFactor: Float,
    val maxProgress: Float = kotlin.random.Random.nextFloat() * 0.3f + 0.7f
)

@Composable
fun BombExplosionAnimation(
    offset: androidx.compose.ui.geometry.Offset,
    onFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val density = LocalDensity.current
    val isCentered = offset == androidx.compose.ui.geometry.Offset.Zero

    val particles = remember {
        val emojis = listOf("❤️", "🔥", "✨", "⭐", "💥", "❤️", "💖", "⚡")
        List(35) { i ->
            val angle = kotlin.random.Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = kotlin.random.Random.nextFloat() * 300f + 150f
            val speedX = kotlin.math.cos(angle) * speed
            val speedY = kotlin.math.sin(angle) * speed - 150f
            BombParticle(
                id = i,
                emoji = emojis[kotlin.random.Random.nextInt(emojis.size)],
                initialSpeedX = speedX,
                initialSpeedY = speedY,
                rotationSpeed = kotlin.random.Random.nextFloat() * 720f - 360f,
                scaleFactor = kotlin.random.Random.nextFloat() * 0.6f + 0.6f
            )
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing)
        )
        onFinished()
    }

    val t = progress.value

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val offsetX = with(density) { offset.x.toDp() }
        val offsetY = with(density) { offset.y.toDp() }

        if (t < 0.35f) {
            val bombProgress = t / 0.35f
            val bombScale = if (bombProgress < 0.7f) {
                (bombProgress / 0.7f) * 1.8f
            } else {
                1.8f - ((bombProgress - 0.7f) / 0.3f) * 0.4f
            }
            val shakeX = if (bombProgress > 0.6f) {
                kotlin.math.sin(bombProgress * 50f) * 15f
            } else 0f

            Box(
                modifier = if (isCentered) {
                    Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            scaleX = bombScale
                            scaleY = bombScale
                            translationX = shakeX
                        }
                } else {
                    Modifier
                        .offset(
                            x = offsetX - 35.dp + with(density) { shakeX.toDp() },
                            y = offsetY - 35.dp
                        )
                        .graphicsLayer {
                            scaleX = bombScale
                            scaleY = bombScale
                        }
                },
                contentAlignment = Alignment.Center
            ) {
                Text("💣", fontSize = 40.sp)
            }
        }

        if (t >= 0.3f && t < 0.6f) {
            val flashProgress = (t - 0.3f) / 0.3f
            val flashScale = 1f + flashProgress * 2.5f
            val flashAlpha = 1f - flashProgress

            Box(
                modifier = if (isCentered) {
                    Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            scaleX = flashScale
                            scaleY = flashScale
                            alpha = flashAlpha
                        }
                } else {
                    Modifier
                        .offset(x = offsetX - 50.dp, y = offsetY - 50.dp)
                        .graphicsLayer {
                            scaleX = flashScale
                            scaleY = flashScale
                            alpha = flashAlpha
                        }
                },
                contentAlignment = Alignment.Center
            ) {
                Text("💥", fontSize = 60.sp)
            }
        }

        if (t >= 0.3f) {
            val pt = (t - 0.3f) / 0.7f

            particles.forEach { particle ->
                val px = particle.initialSpeedX * pt
                val py = particle.initialSpeedY * pt + 400f * pt * pt
                
                val pScale = (1f - pt) * particle.scaleFactor
                val pAlpha = (1f - pt).coerceIn(0f, 1f)
                val pRotation = particle.rotationSpeed * pt

                Box(
                    modifier = if (isCentered) {
                        Modifier
                            .align(Alignment.Center)
                            .offset(
                                x = with(density) { px.toDp() },
                                y = with(density) { py.toDp() }
                            )
                            .graphicsLayer {
                                scaleX = pScale
                                scaleY = pScale
                                alpha = pAlpha
                                rotationZ = pRotation
                            }
                    } else {
                        Modifier
                            .offset(
                                x = offsetX - 15.dp + with(density) { px.toDp() },
                                y = offsetY - 15.dp + with(density) { py.toDp() }
                            )
                            .graphicsLayer {
                                scaleX = pScale
                                scaleY = pScale
                                alpha = pAlpha
                                rotationZ = pRotation
                            }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text(particle.emoji, fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
fun FloatingHeartsOverlay(origin: androidx.compose.ui.geometry.Offset, onComplete: () -> Unit) {
    // Snappier 1.5 seconds total animation
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        onComplete()
    }
    
    // Create multiple hearts with faster movement
    val hearts = remember { List(12) { 
        FloatingHeartData(
            delay = (0..500).random().toLong(),
            xDrift = (-120..120).random().toFloat(),
            yDrift = (-600..-300).random().toFloat(),
            scale = (6..14).random() / 10f,
            duration = (800..1200).random()
        )
    } }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // The main big heart (snappier)
        var mainHeartVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            mainHeartVisible = true
            kotlinx.coroutines.delay(600)
            mainHeartVisible = false
        }
        val mainScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (mainHeartVisible) 1.3f else 0f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
            )
        )
        val mainAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (mainHeartVisible) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(200)
        )
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = Color(0xFFFE2C55).copy(alpha = mainAlpha),
            modifier = Modifier
                .offset(
                    x = with(LocalDensity.current) { origin.x.toDp() - 50.dp },
                    y = with(LocalDensity.current) { origin.y.toDp() - 50.dp }
                )
                .size(100.dp)
                .scale(mainScale)
        )

        // Radial floating hearts (faster)
        hearts.forEach { heart ->
            var isAnimating by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(heart.delay)
                isAnimating = true
            }
            if (isAnimating) {
                val progress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.tween(heart.duration, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
                
                val currentX = origin.x + (heart.xDrift * progress) + (kotlin.math.sin(progress * 12f) * 40f)
                val currentY = origin.y + (heart.yDrift * progress)
                val alpha = 1f - progress
                
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFE2C55).copy(alpha = alpha),
                    modifier = Modifier
                        .offset(
                            x = with(LocalDensity.current) { currentX.toDp() - 15.dp },
                            y = with(LocalDensity.current) { currentY.toDp() - 15.dp }
                        )
                        .size((30 * heart.scale).dp)
                        .scale(0.5f + (0.5f * (1f - progress)))
                )
            }
        }
    }
}

data class FloatingHeartData(
    val delay: Long,
    val xDrift: Float,
    val yDrift: Float,
    val scale: Float,
    val duration: Int
)

@Composable
fun ScrollingText(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle) {
    val scrollState = androidx.compose.foundation.rememberScrollState()
    var shouldAnimate by remember { mutableStateOf(true) }
    
    LaunchedEffect(shouldAnimate) {
        while (shouldAnimate) {
            if (scrollState.maxValue <= 0) {
                kotlinx.coroutines.delay(1500)
                continue
            }
            scrollState.animateScrollTo(
                scrollState.maxValue,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 3000,
                    delayMillis = 1000,
                    easing = androidx.compose.animation.core.LinearEasing
                )
            )
            scrollState.scrollTo(0)
            kotlinx.coroutines.delay(1000)
        }
    }

    Box(modifier = modifier.fillMaxWidth(0.5f)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.horizontalScroll(scrollState, false)
        ) {
            Text(
                text = "$text • $text • ",
                style = style,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SpinningReelAudioArt(
    imageUrl: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val angleState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_rotation"
    )
    val artRequest = remember(imageUrl) {
        com.example.utils.ExoPlayerUtils.buildFastPosterRequest(context, imageUrl, 120, 120)
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .graphicsLayer { rotationZ = angleState.value }
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
            .bounceClick()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = artRequest,
            contentDescription = "Audio Art",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

