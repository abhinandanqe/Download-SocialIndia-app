package com.example.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.model.RepositoryManager
import com.example.model.StoryUiModel
import com.example.utils.ExoPlayerUtils
import com.example.utils.VideoCacheManager
import com.example.utils.bounceClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.absoluteValue

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun StoryPlayerOverlay(
    stories: List<StoryUiModel>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit,
    onRebackClick: (String, String) -> Unit = { _, _ -> },
    onProfileClick: (String) -> Unit = {},
    onAddStoryClick: () -> Unit = {},
    onNavigateToReel: (String) -> Unit = {}
) {
    if (stories.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            com.example.utils.GlobalMediaSessionManager.requestFocus(com.example.utils.MediaSource.STORY, context)
        }
        DisposableEffect(Unit) {
            onDispose {
                com.example.utils.GlobalMediaSessionManager.releaseFocus(com.example.utils.MediaSource.STORY, context)
            }
        }

        var textMessage by remember { mutableStateOf("") }
        var showSocialMediaPicker by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        var activeStories by remember(stories) { mutableStateOf(stories) }
        var currentStoryIndex by remember { mutableIntStateOf(initialIndex) }
        var showViewersSheet by remember { mutableStateOf(false) }
        var showShareSheet by remember { mutableStateOf(false) }
        var showOwnerMoreMenu by remember { mutableStateOf(false) }
        var showDeleteConfirmDialog by remember { mutableStateOf(false) }
        var showEditStoryDialog by remember { mutableStateOf(false) }
        var showPrivacySettingsDialog by remember { mutableStateOf(false) }
        var showAddToHighlightDialog by remember { mutableStateOf(false) }
        var showAddMentionToStoryDialog by remember { mutableStateOf(false) }
        var liveViewCount by remember { mutableIntStateOf(0) }
        var liveLikesCount by remember { mutableIntStateOf(0) }
        var previewUserId by remember { mutableStateOf<String?>(null) }
        var showReactionDock by remember { mutableStateOf(false) }
        var showStoryGiftSheet by remember { mutableStateOf(false) }
        var storyGiftBurst by remember { mutableStateOf<ContentGiftBurstEvent?>(null) }
        var storyReactionParticles by remember { mutableStateOf<List<FloatingParticle>>(emptyList()) }

        val triggerReactionBurst: (String) -> Unit = { emoji ->
            val newParticles = (0..6).map { idx ->
                FloatingParticle(
                    id = System.nanoTime() + idx,
                    emoji = emoji,
                    startXOffset = kotlin.random.Random.nextInt(-50, 50).toFloat(),
                    swayAmount = kotlin.random.Random.nextInt(15, 45).toFloat(),
                    targetScale = kotlin.random.Random.nextFloat() * 0.5f + 1.1f,
                    durationMs = kotlin.random.Random.nextInt(1800, 2400)
                )
            }
            storyReactionParticles = (storyReactionParticles + newParticles).takeLast(24)
            scope.launch {
                delay(2600)
                storyReactionParticles = storyReactionParticles.filter { System.nanoTime() - it.id < 2_500_000_000L }
            }
        }

        androidx.activity.compose.BackHandler {
            onDismiss()
        }

        val currentStoryId = activeStories.getOrNull(currentStoryIndex)?.id ?: ""
        DisposableEffect(currentStoryId) {
            val viewersReg = if (currentStoryId.isNotEmpty()) {
                RepositoryManager.postRepository.getStoryViewersCountListener(currentStoryId) { count ->
                    liveViewCount = count
                }
            } else null
            val likesReg = if (currentStoryId.isNotEmpty()) {
                RepositoryManager.postRepository.getStoryLikesCountListener(currentStoryId) { count ->
                    liveLikesCount = count
                }
            } else null
            onDispose {
                viewersReg?.remove()
                likesReg?.remove()
            }
        }
        
        // Safety check for index
        LaunchedEffect(activeStories.size) {
            if (activeStories.isEmpty()) {
                onDismiss()
            } else if (currentStoryIndex >= activeStories.size) {
                currentStoryIndex = (activeStories.size - 1).coerceAtLeast(0)
            }
        }
        
        var storyProgress by remember { mutableStateOf(0f) }
        val story = activeStories.getOrNull(currentStoryIndex)
        
        if (story == null) {
            LaunchedEffect(Unit) { onDismiss() }
        } else {
            var currentUserId by remember { mutableStateOf("") }
            var currentUsername by remember { mutableStateOf("") }
        var currentUserAvatar by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            val uid = RepositoryManager.getCurrentUserId()
            currentUserId = uid
            if (uid.isNotEmpty()) {
                try {
                    val uDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                    currentUsername = uDoc.getString("username") ?: ""
                    currentUserAvatar = uDoc.getString("profilePicUrl") ?: uDoc.getString("avatarUrl") ?: ""
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

    var isPaused by remember { mutableStateOf(false) }
    var hideUiOnHold by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    var upwardDrag by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentStoryIndex, activeStories.size, currentUserId) {
        val currentItem = activeStories.getOrNull(currentStoryIndex) ?: return@LaunchedEffect
        android.util.Log.d("StoryPlayer", "Playing story index: $currentStoryIndex, id: ${currentItem.id}")
        if (currentUserId.isNotEmpty() && currentStoryIndex in activeStories.indices) {
            try {
                RepositoryManager.socialRepository.markStoryAsViewed(currentItem.id, currentUserId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("StoryPlayer", "Error marking story viewed", e)
            }
        }
        storyProgress = 0f
        try {
            val isVideo = currentItem.imageUrl.contains(".mp4", ignoreCase = true) || currentItem.imageUrl.contains("video", ignoreCase = true)
            val durationMs = if (isVideo) 15000L else 5000L
            val stepMs = 40L
            val increment = stepMs.toFloat() / durationMs.toFloat()
            while (storyProgress < 1f) {
                delay(stepMs)
                val sheetOpen = showViewersSheet || showShareSheet || showOwnerMoreMenu || showReactionDock || showSocialMediaPicker
                if (!isPaused && !isDragging && !hideUiOnHold && !sheetOpen) {
                    storyProgress += increment
                }
            }
            if (currentStoryIndex < activeStories.size - 1) {
                currentStoryIndex++
            } else {
                onDismiss()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // normal when skipped/cancelled
        } catch (e: Throwable) {
            android.util.Log.e("StoryPlayer", "Error in story progress loop", e)
        }
    }

    var showStoryHeart by remember { mutableStateOf(false) }
    var storyHeartOffset by remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val storyHeartScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showStoryHeart) 1.5f else 0f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy),
        finishedListener = { if (it == 1.5f) showStoryHeart = false },
        label = "storyHeartScale"
    )
    val storyHeartAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showStoryHeart) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(500),
        label = "storyHeartAlpha"
    )

    val reducedMotion = com.example.ui.motion.rememberReducedMotionEnabled()
    val storyOpenScale = remember { androidx.compose.animation.core.Animatable(if (reducedMotion) 1f else 0.86f) }
    val storyOpenAlpha = remember { androidx.compose.animation.core.Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            launch {
                storyOpenScale.animateTo(1f, com.example.ui.motion.SocialMotion.springSheet())
            }
            launch {
                storyOpenAlpha.animateTo(1f, com.example.ui.motion.SocialMotion.floatSpec(com.example.ui.motion.SocialMotion.DurationFast))
            }
        }
    }

    val animatedDragOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = com.example.ui.motion.SocialMotion.springSheet(reducedMotion),
        label = "dragDismissSpring"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = ((1f - (animatedDragOffset / 1000f).coerceIn(0f, 1f)) * storyOpenAlpha.value).coerceIn(0f, 1f)))
            .graphicsLayer {
                scaleX = storyOpenScale.value
                scaleY = storyOpenScale.value
                alpha = storyOpenAlpha.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.14f)
            }
            .pointerInput(currentStoryIndex, currentUserId) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isDragging = true
                        isPaused = true
                        upwardDrag = 0f
                    },
                    onDragEnd = {
                        isDragging = false
                        if (dragOffset > 260f) {
                            onDismiss()
                        } else if (upwardDrag < -100f) {
                            val activeItem = activeStories.getOrNull(currentStoryIndex)
                            if (activeItem?.userId == currentUserId) {
                                isPaused = true
                                showViewersSheet = true
                            } else {
                                isPaused = true
                                showReactionDock = true
                            }
                            dragOffset = 0f
                        } else {
                            isPaused = false
                            dragOffset = 0f
                        }
                        upwardDrag = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        isPaused = false
                        dragOffset = 0f
                        upwardDrag = 0f
                    }
                ) { _, dragAmount ->
                    if (dragAmount > 0 || dragOffset > 0) {
                        dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                    } else {
                        upwardDrag += dragAmount
                    }
                }
            }
    ) {
        val scale = 1f - (animatedDragOffset / 3000f).coerceIn(0f, 0.5f)
        Box(modifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationY = animatedDragOffset
            shape = RoundedCornerShape(if (animatedDragOffset > 0) 20.dp else 0.dp)
            clip = animatedDragOffset > 0
        }) {
        val audioPlayer = remember {
            try {
                ExoPlayerUtils.createExoPlayer(context, isMusic = true).apply {
                    repeatMode = Player.REPEAT_MODE_ALL
                }
            } catch (e: Throwable) {
                null
            }
        }
        
        DisposableEffect(audioPlayer) {
            onDispose { 
                ExoPlayerUtils.safeRelease(audioPlayer)
            }
        }
        
        val safeInitialPage = if (activeStories.isEmpty()) 0 else currentStoryIndex.coerceIn(0, activeStories.lastIndex)
        val pagerState = rememberPagerState(initialPage = safeInitialPage, pageCount = { activeStories.size })
        val currentStoryOnPage = activeStories.getOrNull(pagerState.currentPage)
        val musicUrlOnPage = currentStoryOnPage?.musicTrackUrl

        LaunchedEffect(musicUrlOnPage, isPaused, isDragging, hideUiOnHold, pagerState.currentPage) {
            audioPlayer?.let { player ->
                if (!musicUrlOnPage.isNullOrEmpty() && !isPaused && !isDragging && !hideUiOnHold) {
                    try {
                        player.setMediaItem(MediaItem.Builder().setUri(Uri.parse(musicUrlOnPage)).setMimeType(androidx.media3.common.MimeTypes.AUDIO_MP4).build())
                        player.prepare()
                        player.play()
                    } catch (e: Throwable) {
                        android.util.Log.e("StoryPlayer", "Error playing audio", e)
                    }
                } else {
                    player.pause()
                }
            }
        }
        
        var lastPagerUserId by remember { mutableStateOf(activeStories.getOrNull(safeInitialPage)?.userId) }
        LaunchedEffect(pagerState.currentPage) {
            currentStoryIndex = pagerState.currentPage
            lastPagerUserId = activeStories.getOrNull(pagerState.currentPage)?.userId
        }
        LaunchedEffect(currentStoryIndex, activeStories.size) {
            if (pagerState.currentPage != currentStoryIndex && currentStoryIndex in activeStories.indices) {
                val targetUserId = activeStories.getOrNull(currentStoryIndex)?.userId
                try {
                    if (targetUserId != null && targetUserId == lastPagerUserId) {
                        // Instant snap when stepping between segments of the SAME user
                        pagerState.scrollToPage(currentStoryIndex)
                    } else {
                        // 3D Cube transition when switching to the NEXT or PREVIOUS user's stories
                        lastPagerUserId = targetUserId
                        pagerState.animateScrollToPage(
                            page = currentStoryIndex,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageStory = activeStories.getOrNull(page) ?: return@HorizontalPager
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val offScreenRight = pageOffset < 0f
                        val deg = 90f
                        val interpolated = androidx.compose.animation.core.FastOutLinearInEasing.transform(pageOffset.absoluteValue.coerceIn(0f, 1f))
                        rotationY = interpolated * if (offScreenRight) deg else -deg
                        cameraDistance = 12f * density
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                            pivotFractionX = if (offScreenRight) 0f else 1f,
                            pivotFractionY = 0.5f
                        )
                    }
                    .pointerInput(page, activeStories.size) {
                        detectTapGestures(
                            onPress = {
                                var heldLongEnough = false
                                val holdJob = scope.launch {
                                    delay(180)
                                    heldLongEnough = true
                                    isPaused = true
                                    hideUiOnHold = true
                                }
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    holdJob.cancel()
                                    if (heldLongEnough) {
                                        isPaused = false
                                        hideUiOnHold = false
                                    }
                                }
                            },
                            onDoubleTap = { offset ->
                                triggerCrispMicroHaptic(context, strong = true)
                                storyHeartOffset = offset
                                showStoryHeart = true
                                triggerReactionBurst("❤️")
                                scope.launch {
                                    try {
                                        RepositoryManager.postRepository.reactToStory(pageStory.id, currentUserId, "❤️")
                                    } catch (e: Throwable) {
                                        android.util.Log.e("StoryPlayer", "Error liking story", e)
                                    }
                                }
                            },
                            onTap = { offset ->
                                try {
                                    if (showReactionDock) {
                                        showReactionDock = false
                                        isPaused = false
                                        return@detectTapGestures
                                    }
                                    val screenWidth = size.width
                                    if (offset.x < screenWidth * 0.30f) {
                                        // Left 30% -> Previous story segment (or previous user)
                                        if (currentStoryIndex > 0) currentStoryIndex-- else onDismiss()
                                    } else {
                                        // Right 70% -> Next story segment (or next user)
                                        if (pageStory.isReback && !pageStory.repostedPostId.isNullOrEmpty() &&
                                            offset.x in (screenWidth * 0.32f)..(screenWidth * 0.68f)
                                        ) {
                                            onDismiss()
                                            onNavigateToReel(pageStory.repostedPostId)
                                        } else if (currentStoryIndex < activeStories.size - 1) {
                                            currentStoryIndex++
                                        } else {
                                            onDismiss()
                                        }
                                    }
                                } catch (e: Throwable) {
                                    android.util.Log.e("StoryPlayer", "Error on tap", e)
                                }
                            }
                        )
                    }
            ) {
            val storyUnlockedKeys by com.example.repository.EntitlementRepository.unlockedKeysFlow.collectAsStateWithLifecycle()
            val storyPurchaseStatusMap by com.example.repository.EntitlementRepository.purchaseStatusMapFlow.collectAsStateWithLifecycle()
            val storyPurchaseRec = storyPurchaseStatusMap[pageStory.id]
            val isCurrentPage = pagerState.currentPage == page
            val isStoryOwner = currentUserId.isNotEmpty() && pageStory.userId == currentUserId
            val isExclusiveStory = pageStory.isExclusiveContent && pageStory.exclusivePrice >= 10.0
            val isStoryUnlocked = remember(pageStory.id, currentUserId, storyUnlockedKeys, isStoryOwner, isExclusiveStory) {
                if (!isExclusiveStory || isStoryOwner) true
                else storyUnlockedKeys.contains("${currentUserId}_${pageStory.id}") ||
                    com.example.repository.EntitlementRepository.hasAccessFast(
                        userId = currentUserId,
                        creatorId = pageStory.userId ?: "",
                        contentId = pageStory.id,
                        isExclusive = true,
                        isPaidPost = true
                    )
            }
            var storyVaultPayload by remember(pageStory.id) {
                mutableStateOf(com.example.repository.ExclusiveContentRepository.getCachedVaultMedia(pageStory.id))
            }
            var showStoryUnlockSheet by remember(pageStory.id) { mutableStateOf(false) }
            var showStoryReportDialog by remember(pageStory.id) { mutableStateOf(false) }

            LaunchedEffect(pageStory.id, isStoryUnlocked, pageStory.hasVaultMedia) {
                if (isExclusiveStory && isStoryUnlocked && pageStory.hasVaultMedia && storyVaultPayload == null) {
                    storyVaultPayload = com.example.repository.ExclusiveContentRepository.resolveUnlockedMedia(pageStory.id)
                }
            }

            // Pause auto-advance while viewing a locked exclusive story
            LaunchedEffect(isCurrentPage, isExclusiveStory, isStoryUnlocked, showStoryUnlockSheet) {
                if (isCurrentPage && isExclusiveStory && (!isStoryUnlocked || showStoryUnlockSheet)) {
                    isPaused = true
                }
            }

            if (isCurrentPage && isExclusiveStory) {
                com.example.utils.ExclusiveScreenSecurityEffect(enabled = true)
            }

            val resolvedMediaUrl = if (isExclusiveStory && !isStoryUnlocked) {
                ""
            } else {
                storyVaultPayload?.mediaUrl?.ifBlank { pageStory.imageUrl } ?: pageStory.imageUrl
            }
            val isImage = com.example.utils.MediaResolver.isImage(resolvedMediaUrl)
            val isStoryVideo = !resolvedMediaUrl.isNullOrEmpty() && !isImage

            if (isStoryVideo && (!isExclusiveStory || isStoryUnlocked)) {
                var exoPlayer by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }

                DisposableEffect(resolvedMediaUrl, isCurrentPage, isStoryUnlocked) {
                    var player: androidx.media3.exoplayer.ExoPlayer? = null
                    if (isCurrentPage && resolvedMediaUrl.isNotBlank()) {
                        try {
                            player = ExoPlayerUtils.createExoPlayer(context).apply {
                                repeatMode = Player.REPEAT_MODE_ALL
                                val dataSourceFactory = VideoCacheManager.createCacheDataSourceFactory(context)
                                val playUrl = com.example.utils.MediaResolver.resolveVideoUrl(resolvedMediaUrl)
                                if (playUrl.isNotBlank()) {
                                    val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                                        .createMediaSource(
                                            MediaItem.Builder()
                                                .setUri(Uri.parse(playUrl))
                                                .build()
                                        )
                                    setMediaSource(mediaSource)
                                    prepare()
                                    playWhenReady = true
                                }
                            }
                            exoPlayer = player
                        } catch (e: Throwable) {
                            exoPlayer = null
                        }
                    }
                    onDispose {
                        ExoPlayerUtils.safeRelease(player)
                        exoPlayer = null
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    MediaShimmerPlaceholder(modifier = Modifier.fillMaxSize())
                    if (exoPlayer != null) {
                        LaunchedEffect(isPaused, isCurrentPage, isDragging, hideUiOnHold) {
                            exoPlayer?.playWhenReady = isCurrentPage && !isPaused && !isDragging && !hideUiOnHold
                        }

                        AndroidView(
                            factory = { ctx ->
                                com.example.utils.ExoPlayerUtils.createTexturePlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    isClickable = false
                                    isFocusable = false
                                    setOnTouchListener { _, _ -> false }
                                }
                            },
                            update = { playerView ->
                                if (playerView.player != exoPlayer) {
                                    playerView.player = exoPlayer
                                }
                            },
                            onRelease = { playerView ->
                                playerView.player = null
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else if (!isExclusiveStory || isStoryUnlocked) {
                if (resolvedMediaUrl.isNotEmpty()) {
                    val resolvedStoryImg = com.example.utils.MediaResolver.resolveImageUrl(resolvedMediaUrl).ifBlank { resolvedMediaUrl }
                    coil.compose.SubcomposeAsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(resolvedStoryImg)
                            .crossfade(true)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        loading = {
                            MediaShimmerPlaceholder(modifier = Modifier.fillMaxSize())
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF833AB4),
                                                Color(0xFFFD1D1D),
                                                Color(0xFFFCB045)
                                            )
                                        )
                                    )
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF833AB4),
                                        Color(0xFFFD1D1D),
                                        Color(0xFFFCB045)
                                    )
                                )
                            )
                    )
                }
            }

            // Exclusive Story Creator Badge
            if (isExclusiveStory && isStoryUnlocked) {
                ExclusiveGoldBadge(
                    label = if (isStoryOwner) "Exclusive Story" else "Unlocked",
                    priceInr = if (isStoryOwner) pageStory.exclusivePrice else null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 62.dp, end = 14.dp)
                )
            }

            // Locked Exclusive Story Blurred Overlay with smooth fade-out reveal
            androidx.compose.animation.AnimatedVisibility(
                visible = isExclusiveStory && !isStoryUnlocked,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(480)),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(20f)
            ) {
                ExclusiveLockedOverlay(
                    blurredPreviewUrl = pageStory.blurredThumbnailUrl.ifBlank { pageStory.imageUrl },
                    creatorUsername = pageStory.username,
                    creatorAvatarUrl = pageStory.avatarUrl,
                    teaserCaption = pageStory.teaserCaption.ifBlank { pageStory.textOverlay },
                    priceInr = pageStory.exclusivePrice,
                    contentTypeLabel = "Exclusive Story",
                    purchaseStatus = storyPurchaseRec?.status,
                    expiresAt = storyPurchaseRec?.expiresAt ?: 0L,
                    onUnlockClick = {
                        isPaused = true
                        showStoryUnlockSheet = true
                    },
                    onReportClick = {
                        isPaused = true
                        showStoryReportDialog = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (showStoryUnlockSheet) {
                ExclusiveUnlockBottomSheet(
                    contentId = pageStory.id,
                    contentType = "STORY",
                    creatorId = pageStory.userId ?: "",
                    creatorUsername = pageStory.username,
                    creatorAvatarUrl = pageStory.avatarUrl,
                    title = pageStory.teaserCaption.ifBlank { "Exclusive Story" },
                    teaserCaption = pageStory.teaserCaption,
                    blurredThumbnailUrl = pageStory.blurredThumbnailUrl.ifBlank { pageStory.imageUrl },
                    priceInr = pageStory.exclusivePrice,
                    onDismiss = { showStoryUnlockSheet = false },
                    onUnlocked = { payload ->
                        if (payload != null) storyVaultPayload = payload
                        showStoryUnlockSheet = false
                        isPaused = false
                    }
                )
            }

            if (showStoryReportDialog) {
                ExclusiveReportDialog(
                    contentId = pageStory.id,
                    contentType = "STORY",
                    creatorId = pageStory.userId ?: "",
                    creatorUsername = pageStory.username,
                    onDismiss = { showStoryReportDialog = false }
                )
            }

            if (pageStory.isReback && pageStory.repostedPostId != null && pageStory.repostedPostId.isNotEmpty() && (!isExclusiveStory || isStoryUnlocked)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .clickable {
                            onDismiss()
                            onNavigateToReel(pageStory.repostedPostId)
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = resolvedMediaUrl,
                            contentDescription = "Post Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("View Post", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
            if (showStoryHeart || storyHeartScale > 0f) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFE2C55).copy(alpha = storyHeartAlpha),
                    modifier = Modifier
                        .offset(
                            x = with(LocalDensity.current) { storyHeartOffset.x.toDp() - 50.dp },
                            y = with(LocalDensity.current) { storyHeartOffset.y.toDp() - 50.dp }
                        )
                        .size(100.dp)
                        .scale(storyHeartScale)
                )
            }

            // Clean Text Overlay (Hidden while locked so only teaser caption is visible)
            if (pageStory.textOverlay.isNotBlank() && (!isExclusiveStory || isStoryUnlocked)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = pageStory.textOverlay,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (pageStory.stickers.isNotEmpty() && (!isExclusiveStory || isStoryUnlocked)) {
                StoryStickersOverlay(
                    stickers = pageStory.stickers,
                    storyId = pageStory.id,
                    currentUserId = currentUserId,
                    currentUsername = currentUsername,
                    currentUserAvatar = currentUserAvatar,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Floating Reaction Particles Overlay (Facebook-style reactions)
            if (storyReactionParticles.isNotEmpty()) {
                LiveFloatingReactionsEngine(
                    particles = storyReactionParticles,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top Progress Lines Overlay (Per-User Segmented Progress Bars, Fades out on Hold/Drag)
            val currentUserStories = remember(activeStories, pageStory.userId) {
                activeStories.filter { it.userId == pageStory.userId }.ifEmpty { listOf(pageStory) }
            }
            val currentSegmentInUserGroup = remember(currentUserStories, pageStory.id) {
                currentUserStories.indexOfFirst { it.id == pageStory.id }.coerceAtLeast(0)
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !hideUiOnHold && !isDragging,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(start=16.dp, end=16.dp, top=16.dp, bottom=32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        currentUserStories.forEachIndexed { segIdx, _ ->
                            val progress = when {
                                segIdx < currentSegmentInUserGroup -> 1f
                                segIdx == currentSegmentInUserGroup -> storyProgress
                                else -> 0f
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                                        .background(Color.White, RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = pageStory.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                                .bounceClick().clickable {
                                    if (!pageStory.userId.isNullOrEmpty()) {
                                        isPaused = true
                                        previewUserId = pageStory.userId
                                    }
                                },
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier
                                .bounceClick().clickable {
                                    if (!pageStory.userId.isNullOrEmpty()) {
                                        isPaused = true
                                        previewUserId = pageStory.userId
                                    }
                                }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(pageStory.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (pageStory.isVerified) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    VerificationBadge(
                                        isVerified = true,
                                        badgeUrl = pageStory.verificationBadgeUrl.ifBlank { null },
                                        modifier = Modifier.size(15.dp),
                                        showDialogOnClick = true
                                    )
                                }
                                if (pageStory.isCloseFriendsOnly) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF10B981))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Close Friends", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(pageStory.timeAgo, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                            }
                            if (!pageStory.musicTrackName.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 1.dp)
                                ) {
                                    AnimatedMusicEqualizer(
                                        isPlaying = !isPaused,
                                        barColor = Color(0xFFFFD700),
                                        barWidth = 2.dp,
                                        maxHeight = 10.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = "Audio",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${pageStory.musicTrackName}${if (!pageStory.musicTrackArtist.isNullOrBlank()) " • ${pageStory.musicTrackArtist}" else ""}",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        if (pageStory.userId == currentUserId) {
                            IconButton(onClick = {
                                onDismiss()
                                onAddStoryClick()
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add to Story", tint = Color.White)
                            }
                            IconButton(onClick = {
                                isPaused = true
                                showOwnerMoreMenu = true
                            }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Story Options", tint = Color.White)
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }

            // Bottom message / owner controls (Fades out cleanly when user holds screen)
            androidx.compose.animation.AnimatedVisibility(
                visible = !hideUiOnHold && !isDragging,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.68f))))
                        .navigationBarsPadding()
                        .padding(start=16.dp, end=16.dp, top=16.dp, bottom=32.dp)
                ) {
                    val pageStory = activeStories.getOrNull(currentStoryIndex)
                    if (pageStory != null && pageStory.userId == currentUserId) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            // 1:1 Instagram "Seen by" Viewers & Reactions Pill
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color.White.copy(alpha = 0.22f))
                                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                                    .clickable {
                                        isPaused = true
                                        showViewersSheet = true
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Activity • ${maxOf(liveViewCount, pageStory.viewers.size)}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (liveLikesCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "❤️🔥 $liveLikesCount",
                                        color = Color(0xFFFFD54F),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Add to Highlight button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .clickable {
                                        isPaused = true
                                        showAddToHighlightDialog = true
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Highlight", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Share button (opens ModernShareBottomSheet)
                            IconButton(
                                onClick = {
                                    isPaused = true
                                    showShareSheet = true
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Share", tint = Color.White)
                            }

                            // More options button (Edit, Delete, Settings)
                            IconButton(
                                onClick = {
                                    isPaused = true
                                    showOwnerMoreMenu = true
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                            }
                        }
                    } else if (pageStory != null) {
                        val isViewerMentioned = remember(pageStory, currentUserId, currentUsername) {
                            (currentUserId.isNotEmpty() && pageStory.mentionedUserId == currentUserId) ||
                            (currentUsername.isNotEmpty() && pageStory.mentionedUsername.equals(currentUsername, ignoreCase = true)) ||
                            (currentUserId.isNotEmpty() && pageStory.stickers.any { it.type == "mention" && (it.mentionUserId == currentUserId || (currentUsername.isNotEmpty() && it.mentionUsername.equals(currentUsername, ignoreCase = true))) }) ||
                            (currentUsername.isNotEmpty() && pageStory.textOverlay.contains("@$currentUsername", ignoreCase = true))
                        }

                        if (isViewerMentioned && (!isExclusiveStory || isStoryUnlocked)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCAF45))
                                        )
                                    )
                                    .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                                    .clickable {
                                        triggerCrispMicroHaptic(context, strong = true)
                                        isPaused = true
                                        showAddMentionToStoryDialog = true
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add this to your story", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    Text("Repost ✨", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        val userReaction by remember(pageStory.id, currentUserId) {
                            RepositoryManager.postRepository.getStoryUserReaction(pageStory.id, currentUserId)
                        }.collectAsStateWithLifecycle(initialValue = null)

                        AnimatedVisibility(
                            visible = showReactionDock,
                            enter = fadeIn() + androidx.compose.animation.scaleIn(
                                initialScale = 0.8f,
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                            ),
                            exit = fadeOut() + androidx.compose.animation.scaleOut(
                                targetScale = 0.8f,
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                            ),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 10.dp)
                        ) {
                            FacebookStoryReactionsDock(
                                selectedReaction = userReaction,
                                onSelectReaction = { emoji ->
                                    showReactionDock = false
                                    isPaused = false
                                    triggerCrispMicroHaptic(context, strong = true)
                                    triggerReactionBurst(emoji)
                                    if (currentUserId.isNotEmpty()) {
                                        scope.launch {
                                            try {
                                                RepositoryManager.postRepository.reactToStory(pageStory.id, currentUserId, emoji)
                                                val ownerId = pageStory.userId ?: ""
                                                if (ownerId.isNotBlank() && ownerId != currentUserId) {
                                                    RepositoryManager.socialRepository.sendMessage(
                                                        senderId = currentUserId,
                                                        receiverId = ownerId,
                                                        message = "Reacted $emoji to your story",
                                                        mediaUrl = pageStory.imageUrl,
                                                        mediaType = "STORY_REACTION"
                                                    )
                                                }
                                                Toast.makeText(context, "Sent $emoji to @${pageStory.username}", Toast.LENGTH_SHORT).show()
                                            } catch (e: Throwable) {
                                                android.util.Log.e("StoryPlayer", "Error reacting to story", e)
                                            }
                                        }
                                    }
                                },
                                onDismiss = {
                                    showReactionDock = false
                                    isPaused = false
                                }
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = textMessage,
                                onValueChange = {
                                    textMessage = it
                                    if (!showReactionDock && it.isEmpty()) {
                                        // Ready for quick reaction or text
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                placeholder = { Text("Send message...", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = Color.White.copy(alpha = 0.65f),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.45f)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Send
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onSend = {
                                        if (textMessage.isNotBlank()) {
                                            val replyText = textMessage
                                            textMessage = ""
                                            scope.launch {
                                                try {
                                                    RepositoryManager.postRepository.addStoryComment(pageStory.id, com.example.model.Comment(
                                                        userId = currentUserId,
                                                        username = currentUsername.ifBlank { "Me" },
                                                        avatarUrl = currentUserAvatar,
                                                        text = replyText
                                                    ))
                                                    val ownerId = pageStory.userId ?: ""
                                                    if (ownerId.isNotBlank() && ownerId != currentUserId) {
                                                        RepositoryManager.socialRepository.sendMessage(
                                                            senderId = currentUserId,
                                                            receiverId = ownerId,
                                                            message = "Replied to your story: $replyText",
                                                            mediaUrl = pageStory.imageUrl,
                                                            mediaType = "STORY_REPLY"
                                                        )
                                                    }
                                                    Toast.makeText(context, "Sent to @${pageStory.username}!", Toast.LENGTH_SHORT).show()
                                                } catch (e: Throwable) {}
                                            }
                                        }
                                    }
                                )
                            )

                            // Quick Emoji Reaction Bar Trigger (8 Instagram Quick Reactions)
                            IconButton(
                                onClick = {
                                    triggerCrispMicroHaptic(context)
                                    showReactionDock = !showReactionDock
                                    isPaused = showReactionDock
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AddReaction,
                                    contentDescription = "Quick Emoji Reactions",
                                    tint = if (showReactionDock) Color(0xFFFFD700) else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // 1:1 Instagram Story Heart Like Button with Red Spring-Bounce & Floating Mini-Hearts
                            val heartBounceScale = remember { androidx.compose.animation.core.Animatable(1f) }
                            val isHeartLiked = userReaction != null
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .graphicsLayer {
                                        scaleX = heartBounceScale.value
                                        scaleY = heartBounceScale.value
                                    }
                                    .clip(CircleShape)
                                    .pointerInput(pageStory.id, userReaction) {
                                        detectTapGestures(
                                            onLongPress = {
                                                triggerCrispMicroHaptic(context)
                                                showReactionDock = true
                                                isPaused = true
                                            },
                                            onTap = {
                                                triggerCrispMicroHaptic(context, strong = !isHeartLiked)
                                                scope.launch {
                                                    heartBounceScale.snapTo(0.7f)
                                                    heartBounceScale.animateTo(
                                                        1.35f,
                                                        androidx.compose.animation.core.spring(
                                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                                        )
                                                    )
                                                    heartBounceScale.animateTo(
                                                        1f,
                                                        androidx.compose.animation.core.spring(
                                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                                            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                                        )
                                                    )
                                                }
                                                if (isHeartLiked) {
                                                    scope.launch {
                                                        try {
                                                            RepositoryManager.postRepository.unlikeStory(pageStory.id, currentUserId)
                                                        } catch (e: Throwable) {
                                                            android.util.Log.e("StoryPlayer", "Error unliking story", e)
                                                        }
                                                    }
                                                } else {
                                                    triggerReactionBurst("❤️")
                                                    if (currentUserId.isNotEmpty()) {
                                                        scope.launch {
                                                            try {
                                                                RepositoryManager.postRepository.reactToStory(pageStory.id, currentUserId, "❤️")
                                                            } catch (e: Throwable) {
                                                                android.util.Log.e("StoryPlayer", "Error reacting to story", e)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (userReaction != null && userReaction != "❤️") {
                                    Text(
                                        text = userReaction ?: "❤️",
                                        fontSize = 22.sp,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isHeartLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Like Story",
                                        tint = if (isHeartLiked) Color(0xFFFF1744) else Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            // Super Gift Creator Button (🎁) for Story Earning
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .bounceClick()
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFFFF2A6D), Color(0xFFFFB300))
                                        )
                                    )
                                    .clickable {
                                        triggerCrispMicroHaptic(context, strong = true)
                                        isPaused = true
                                        showStoryGiftSheet = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CardGiftcard,
                                    contentDescription = "Send Gift to Creator",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Direct Story Share Button (✈️) opening ModernShareBottomSheet
                            IconButton(
                                onClick = {
                                    triggerCrispMicroHaptic(context)
                                    isPaused = true
                                    showShareSheet = true
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Share Story",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(23.dp)
                                        .graphicsLayer { rotationZ = -22f }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Full-Screen Animated Super-Gift Burst Overlay on Story
        AnimatedGiftBurstOverlay(
            event = storyGiftBurst,
            onFinished = { storyGiftBurst = null },
            modifier = Modifier.fillMaxSize()
        )

        if (showStoryGiftSheet) {
            val activeStory = activeStories.getOrNull(currentStoryIndex)
            if (activeStory != null && !activeStory.userId.isNullOrBlank()) {
                CreatorGiftBottomSheet(
                    creatorId = activeStory.userId,
                    creatorUsername = activeStory.username,
                    contentId = activeStory.id,
                    contentType = "STORY",
                    onDismiss = {
                        showStoryGiftSheet = false
                        isPaused = false
                    },
                    onGiftSent = { ev ->
                        storyGiftBurst = ev
                        triggerReactionBurst(ev.emoji)
                        isPaused = false
                    }
                )
            }
        }

        if (showOwnerMoreMenu) {
            val currentStory = activeStories.getOrNull(currentStoryIndex)
            if (currentStory != null) {
                StoryOwnerMoreBottomSheet(
                    story = currentStory,
                    onDismiss = {
                        showOwnerMoreMenu = false
                        isPaused = false
                    },
                    onEditStory = {
                        showEditStoryDialog = true
                    },
                    onDeleteStory = {
                        showDeleteConfirmDialog = true
                    },
                    onOpenViewers = {
                        showViewersSheet = true
                    },
                    onOpenSettings = {
                        showPrivacySettingsDialog = true
                    },
                    onAddToHighlight = {
                        showAddToHighlightDialog = true
                    }
                )
            }
        }

        if (showSocialMediaPicker) {
            val pageStory = activeStories.getOrNull(currentStoryIndex)
            SocialMediaPickerBottomSheet(
                onDismissRequest = {
                    showSocialMediaPicker = false
                    isPaused = false
                },
                initialTab = MediaPickerTab.STICKER,
                onStickerSelected = { sticker ->
                    showSocialMediaPicker = false
                    isPaused = false
                    if (pageStory != null && currentUserId.isNotEmpty()) {
                        scope.launch {
                            try {
                                RepositoryManager.postRepository.addStoryComment(pageStory.id, com.example.model.Comment(
                                    userId = currentUserId,
                                    username = "Me",
                                    avatarUrl = "",
                                    text = sticker.name.ifEmpty { "Sticker" },
                                    gifUrl = sticker.mediaUrl.ifEmpty { sticker.thumbnailUrl },
                                    type = "STICKER",
                                    stickerId = sticker.stickerId,
                                    packId = sticker.packId,
                                    driveFileId = sticker.driveFileId,
                                    thumbnailDriveFileId = sticker.thumbnailDriveFileId,
                                    isAnimated = sticker.isAnimated
                                ))
                                RepositoryManager.stickerRepository.recordStickerUsage(currentUserId, sticker)
                                Toast.makeText(context, "Sticker sent!", Toast.LENGTH_SHORT).show()
                            } catch (e: Throwable) {}
                        }
                    }
                },
                onGifSelected = { gif ->
                    showSocialMediaPicker = false
                    isPaused = false
                    if (pageStory != null && currentUserId.isNotEmpty()) {
                        scope.launch {
                            try {
                                RepositoryManager.postRepository.addStoryComment(pageStory.id, com.example.model.Comment(
                                    userId = currentUserId,
                                    username = "Me",
                                    avatarUrl = "",
                                    text = "Shared a GIF",
                                    gifUrl = gif.fullUrl,
                                    type = "GIF"
                                ))
                                Toast.makeText(context, "GIF sent!", Toast.LENGTH_SHORT).show()
                            } catch (e: Throwable) {}
                        }
                    }
                },
                onEmojiSelected = { emoji ->
                    textMessage += emoji
                    showSocialMediaPicker = false
                    isPaused = false
                }
            )
        }

        if (showDeleteConfirmDialog) {
            DeleteStoryConfirmationDialog(
                onConfirmDelete = {
                    showDeleteConfirmDialog = false
                    val targetStory = activeStories.getOrNull(currentStoryIndex)
                    if (targetStory != null) {
                        scope.launch {
                            try {
                                RepositoryManager.postRepository.deleteStory(targetStory.id)
                                Toast.makeText(context, "Story deleted", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error deleting story", Toast.LENGTH_SHORT).show()
                            }
                        }
                        val updated = activeStories.toMutableList().apply { removeAt(currentStoryIndex) }
                        if (updated.isEmpty()) {
                            onDismiss()
                        } else {
                            activeStories = updated
                            currentStoryIndex = currentStoryIndex.coerceAtMost(updated.size - 1)
                            isPaused = false
                        }
                    }
                },
                onDismiss = {
                    showDeleteConfirmDialog = false
                    isPaused = false
                }
            )
        }

        if (showEditStoryDialog) {
            val currentStory = activeStories.getOrNull(currentStoryIndex)
            if (currentStory != null) {
                EditStoryDialog(
                    story = currentStory,
                    onDismiss = {
                        showEditStoryDialog = false
                        isPaused = false
                    },
                    onStoryUpdated = { updatedStory ->
                        val updated = activeStories.toMutableList()
                        updated[currentStoryIndex] = updatedStory
                        activeStories = updated
                        showEditStoryDialog = false
                        isPaused = false
                    }
                )
            }
        }

        if (showPrivacySettingsDialog) {
            val currentStory = activeStories.getOrNull(currentStoryIndex)
            if (currentStory != null) {
                StoryPrivacySettingsDialog(
                    story = currentStory,
                    onDismiss = {
                        showPrivacySettingsDialog = false
                        isPaused = false
                    },
                    onPrivacyUpdated = { isCloseFriends ->
                        val updated = activeStories.toMutableList()
                        updated[currentStoryIndex] = currentStory.copy(isCloseFriendsOnly = isCloseFriends)
                        activeStories = updated
                        showPrivacySettingsDialog = false
                        isPaused = false
                    }
                )
            }
        }

        if (showAddToHighlightDialog) {
            val currentStory = activeStories.getOrNull(currentStoryIndex)
            if (currentStory != null) {
                AddStoryToHighlightDialog(
                    storyId = currentStory.id,
                    userId = currentUserId,
                    coverUrl = currentStory.imageUrl,
                    onDismiss = {
                        showAddToHighlightDialog = false
                        isPaused = false
                    }
                )
            }
        }


        if (showShareSheet) {
            val currentStory = activeStories.getOrNull(currentStoryIndex)
            val isStoryEx = currentStory != null && currentStory.isExclusiveContent && currentStory.exclusivePrice >= 10.0
            val isCurOwner = currentStory != null && currentUserId.isNotEmpty() && currentStory.userId == currentUserId
            val isStoryUnlockedForShare = if (!isStoryEx || isCurOwner) {
                true
            } else {
                com.example.repository.EntitlementRepository.hasAccessFast(
                    userId = currentUserId,
                    creatorId = currentStory?.userId ?: "",
                    contentId = currentStory?.id ?: "",
                    isExclusive = true,
                    isPaidPost = true
                )
            }
            val sharePayload = com.example.model.SharePayload.fromStory(
                storyId = currentStory?.id ?: "",
                creatorName = currentStory?.username ?: "Creator",
                creatorAvatar = currentStory?.avatarUrl ?: "",
                mediaUrl = currentStory?.imageUrl ?: "",
                ownerUid = currentStory?.userId ?: "",
                isExclusiveContent = isStoryEx,
                isUnlocked = isStoryUnlockedForShare
            )
            ModernShareBottomSheet(
                payload = sharePayload,
                onDismiss = {
                    showShareSheet = false
                    isPaused = false
                },
                onAddToStory = {
                    if (isStoryEx && !isStoryUnlockedForShare) {
                        Toast.makeText(context, "Unlock this exclusive story before adding to your story", Toast.LENGTH_SHORT).show()
                        showShareSheet = false
                        isPaused = false
                    } else {
                        showShareSheet = false
                        onDismiss()
                        onAddStoryClick()
                    }
                }
            )
        }

        if (showViewersSheet) {
            val activeStoryForViewers = activeStories.getOrNull(currentStoryIndex) ?: story
            StoryViewersBottomSheet(
                storyId = activeStoryForViewers.id,
                viewers = activeStoryForViewers.viewers,
                onDismiss = {
                    showViewersSheet = false
                    isPaused = false
                },
                onProfileClick = { uId ->
                    showViewersSheet = false
                    onDismiss()
                    onProfileClick(uId)
                }
            )
        }

        previewUserId?.let { uid ->
            SocialUserProfilePreviewDialog(
                userId = uid,
                currentUserId = currentUserId,
                onDismiss = {
                    previewUserId = null
                    isPaused = false
                },
                onViewFullProfile = { fullUid ->
                    previewUserId = null
                    onDismiss()
                    onProfileClick(fullUid)
                },
                onDirectMessage = { msgUser ->
                    previewUserId = null
                    onDismiss()
                    onProfileClick(msgUser.id)
                }
            )
        }

        if (showAddMentionToStoryDialog) {
            val currentStory = activeStories.getOrNull(currentStoryIndex)
            if (currentStory != null) {
                StoryCreatorDialog(
                    onDismiss = {
                        showAddMentionToStoryDialog = false
                        isPaused = false
                    },
                    initialMediaUri = if (currentStory.imageUrl.isNotEmpty()) android.net.Uri.parse(currentStory.imageUrl) else null,
                    repostVideoUrl = currentStory.imageUrl.takeIf { it.isNotEmpty() },
                    mentionedUserId = currentStory.userId,
                    mentionedUsername = currentStory.username,
                    initialText = "@${currentStory.username}",
                    isReback = true
                )
            }
        }
    }
}
}
}
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryViewersBottomSheet(
    viewers: List<String>,
    onDismiss: () -> Unit,
    onProfileClick: (String) -> Unit = {},
    storyId: String = ""
) {
    var selectedViewerId by remember { mutableStateOf<String?>(null) }
    var filterReactionsOnly by remember { mutableStateOf(false) }

    val reactionsMap by remember(storyId) {
        if (storyId.isNotBlank()) {
            RepositoryManager.postRepository.getStoryReactions(storyId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyMap())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyMap())

    var liveViewersList by remember(viewers) { mutableStateOf(viewers) }
    DisposableEffect(storyId) {
        if (storyId.isBlank()) return@DisposableEffect onDispose {}
        val reg = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("stories")
            .document(storyId)
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    val vList = (snap.get("viewers") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    if (vList.isNotEmpty()) {
                        liveViewersList = vList
                    }
                }
            }
        onDispose { reg.remove() }
    }

    // Merge all viewers + anyone who reacted/liked, putting reactors first
    val mergedViewers = remember(liveViewersList, reactionsMap, filterReactionsOnly) {
        val combined = (reactionsMap.keys + liveViewersList).distinct()
        val sorted = combined.sortedByDescending { reactionsMap.containsKey(it) }
        if (filterReactionsOnly) sorted.filter { reactionsMap.containsKey(it) } else sorted
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18181B),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(reactionsMap.keys + liveViewersList).distinct().size} Viewers",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (reactionsMap.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !filterReactionsOnly,
                            onClick = { filterReactionsOnly = false },
                            label = { Text("All", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.White.copy(alpha = 0.2f),
                                selectedLabelColor = Color.White,
                                labelColor = Color.Gray
                            )
                        )
                        FilterChip(
                            selected = filterReactionsOnly,
                            onClick = { filterReactionsOnly = true },
                            label = { Text("❤️🔥 Reactions (${reactionsMap.size})", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF1744).copy(alpha = 0.25f),
                                selectedLabelColor = Color.White,
                                labelColor = Color.Gray
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (mergedViewers.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (filterReactionsOnly) "No emoji reactions or likes yet" else "No viewers yet",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    items(mergedViewers, key = { it }) { viewerId ->
                        var user by remember { mutableStateOf<com.example.model.User?>(null) }
                        val viewerReaction = reactionsMap[viewerId]

                        LaunchedEffect(viewerId) {
                            try {
                                val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    .collection("users")
                                    .document(viewerId)
                                    .get()
                                    .await()
                                val vStatus = doc.getString("verificationStatus") ?: ""
                                val vSuspended = doc.getBoolean("verificationSuspended") ?: false
                                val isViewerVerified = !vSuspended && (
                                    (doc.getBoolean("isVerified") ?: false) ||
                                    vStatus == "approved" ||
                                    vStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE
                                )
                                user = com.example.model.User(
                                    id = doc.id,
                                    username = doc.getString("username") ?: "User",
                                    displayName = doc.getString("displayName") ?: doc.getString("name") ?: "",
                                    avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: "",
                                    isVerified = isViewerVerified,
                                    verificationStatus = vStatus,
                                    verificationBadgeUrl = doc.getString("verificationBadgeUrl") ?: ""
                                )
                            } catch (_: Exception) {}
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedViewerId = viewerId }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Viewer Avatar with Exact Emoji / Heart Reaction Badge Overlay
                            Box(contentAlignment = Alignment.BottomEnd) {
                                coil.compose.AsyncImage(
                                    model = user?.avatarUrl?.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${viewerId}" },
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (viewerReaction != null) 1.5.dp else 0.5.dp,
                                            color = if (viewerReaction == "❤️") Color(0xFFFF1744)
                                            else if (viewerReaction != null) Color(0xFFFFD54F)
                                            else Color.White.copy(alpha = 0.2f),
                                            shape = CircleShape
                                        ),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                if (!viewerReaction.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = 3.dp, y = 3.dp)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (viewerReaction == "❤️") Color(0xFFFF1744) else Color(0xFF27272A)
                                            )
                                            .border(1.5.dp, Color(0xFF18181B), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (viewerReaction == "❤️") {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = "Liked Story",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        } else {
                                            Text(
                                                text = viewerReaction,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val titleText = user?.displayName?.takeIf { it.isNotBlank() } ?: user?.username ?: "Loading..."
                                    Text(
                                        text = titleText,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    user?.let { viewerUser ->
                                        if (viewerUser.isVerified) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            VerificationBadge(
                                                user = viewerUser,
                                                modifier = Modifier.size(14.dp),
                                                showDialogOnClick = false
                                            )
                                        }
                                    }
                                }
                                val usernameText = user?.username
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!usernameText.isNullOrBlank()) {
                                        Text("@$usernameText", color = Color.Gray, fontSize = 12.sp)
                                    }
                                    if (!viewerReaction.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (viewerReaction == "❤️") "• Liked your story ❤️" else "• Reacted $viewerReaction",
                                            color = if (viewerReaction == "❤️") Color(0xFFFF5252) else Color(0xFFFFD54F),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            user?.let { targetUser ->
                                SocialFollowButton(
                                    targetUser = targetUser,
                                    modifier = Modifier.height(32.dp).width(100.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        selectedViewerId?.let { vId ->
            SocialUserProfilePreviewDialog(
                userId = vId,
                onDismiss = { selectedViewerId = null },
                onViewFullProfile = { fullUid ->
                    selectedViewerId = null
                    onProfileClick(fullUid)
                }
            )
        }
    }
}

@Composable
fun FacebookStoryReactionsDock(
    selectedReaction: String?,
    onSelectReaction: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Exact 8 Instagram Quick-Reaction Emojis
    val reactions = listOf(
        "😂" to "Laugh",
        "😮" to "Surprised",
        "😍" to "Heart Eyes",
        "😢" to "Sad",
        "👏" to "Clap",
        "🔥" to "Fire",
        "🎉" to "Party",
        "💯" to "100"
    )
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xF01C1E21),
        shadowElevation = 14.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            reactions.forEach { (emoji, _) ->
                var isHovered by remember { mutableStateOf(false) }
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isHovered || selectedReaction == emoji) 1.35f else 1.0f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                    ),
                    label = "ReactionScale_$emoji"
                )

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationY = if (isHovered || selectedReaction == emoji) -8f else 0f
                        }
                        .clip(CircleShape)
                        .background(
                            if (selectedReaction == emoji) Color.White.copy(alpha = 0.25f) else Color.Transparent
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelectReaction(emoji)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
