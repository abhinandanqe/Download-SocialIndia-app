@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class, ExperimentalMaterial3Api::class)
package com.example.ui

import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.model.Comment
import com.example.model.RepositoryManager
import com.example.model.StoryUiModel
import com.example.utils.ExoPlayerUtils
import com.example.utils.NumberFormatUtils
import com.example.utils.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

@Composable
fun LiveViewerScreen(
    story: StoryUiModel,
    isHost: Boolean = false,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val currentUserId = remember { RepositoryManager.getCurrentUserId() }
    val initialAccount = remember { com.example.auth.MultiAccountManager.activeAccount.value }
    val fbUser = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser }
    var currentUsername: String by remember {
        val name = initialAccount?.username?.ifEmpty { null }
            ?: initialAccount?.displayName?.ifEmpty { null }
            ?: fbUser?.displayName?.ifEmpty { null }
            ?: "User"
        mutableStateOf(name)
    }
    var currentUserAvatar: String by remember {
        val pic = initialAccount?.avatarUrl?.ifEmpty { null }
            ?: fbUser?.photoUrl?.toString()?.ifEmpty { null }
            ?: ""
        mutableStateOf(pic)
    }
    var currentUserVerified: Boolean by remember {
        mutableStateOf(initialAccount?.isVerified ?: false)
    }
    val userProfilesCache = remember { mutableStateMapOf<String, com.example.model.User>() }

    // Determine host mode: explicit param or user matches story author
    val effectiveIsHost = remember(isHost, story.userId, currentUserId) {
        isHost || (currentUserId.isNotEmpty() && story.userId == currentUserId)
    }

    var walletBalance by remember { mutableStateOf(0.0) }

    // Stream Analytics & Metrics
    var viewers by remember { mutableStateOf(maxOf(story.viewers.size, 1)) }
    var peakViewers by remember { mutableStateOf(viewers) }
    var totalLikes by remember { mutableStateOf(story.likesCount) }
    var totalGiftsCount by remember { mutableStateOf(0) }

    LaunchedEffect(story.id) {
        if (!effectiveIsHost) return@LaunchedEffect
        // No wait, both host and viewer should see gifts float
    }


    // Floating Reactions
    var floatingParticles by remember { mutableStateOf<List<FloatingParticle>>(emptyList()) }

    // Modals and Sheets
    var showGiftSheet by remember { mutableStateOf(false) }
    var showViewersSheet by remember { mutableStateOf(false) }
    var showJoinLiveSheet by remember { mutableStateOf(false) }
    var showHostControlsSheet by remember { mutableStateOf(false) }
    var showEndLiveConfirmDialog by remember { mutableStateOf(false) }
    var showStreamSummaryDialog by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showInviteFriendsSheet by remember { mutableStateOf(false) }
    var showModeratorSheet by remember { mutableStateOf(false) }
    var showAnalyticsDialog by remember { mutableStateOf(false) }
    var selectedCommentForMod by remember { mutableStateOf<Comment?>(null) }
    var isSlowModeEnabled by remember { mutableStateOf(false) }
    var lastCommentSentTime by remember { mutableStateOf(0L) }
    var isLiveEnded by remember { mutableStateOf(false) }
    var mutedUserIds by remember { mutableStateOf(setOf<String>()) }
    var totalSharesCount by remember { mutableStateOf(0) }
    var streamDurationSeconds by remember { mutableStateOf(0L) }
    var isFollowing by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf(listOf<Comment>()) }
    var newCommentText by remember { mutableStateOf("") }
    var pinnedComment by remember { mutableStateOf<Comment?>(null) }
    var commentsEnabled by remember { mutableStateOf(true) }
    var showLiveMediaPicker by remember { mutableStateOf(false) }

    var followersGainedCount by remember { mutableStateOf(0) }

    var observedGifts by remember { mutableStateOf<List<com.example.model.GiftTransaction>>(emptyList()) }
    LaunchedEffect(story.id) {
        RepositoryManager.socialRepository.observeLiveGifts(story.id).collect { gifts ->
            val prevCount = observedGifts.size
            observedGifts = gifts
            totalGiftsCount = gifts.size
            if (gifts.size > prevCount && prevCount > 0) {
                val newGifts = gifts.take(gifts.size - prevCount)
                newGifts.forEach { gift ->
                    val giftEmoji = LIVE_GIFTS_CATALOG.find { it.id == gift.giftId }?.emoji ?: "🎁"
                    scope.launch {
                        kotlinx.coroutines.delay(kotlin.random.Random.nextLong(0, 300))
                        floatingParticles = (floatingParticles + FloatingParticle(
                            id = kotlin.random.Random.nextLong(),
                            emoji = giftEmoji,
                            startXOffset = kotlin.random.Random.nextInt(-100, 100).toFloat(),
                            swayAmount = kotlin.random.Random.nextInt(20, 50).toFloat()
                        )).takeLast(25)
                    }
                }
            }
        }
    }


    // Co-Host State
    var isJoinRequestPending by remember { mutableStateOf(false) }
    var isCoHostActive by remember { mutableStateOf(false) }
    var coHostUsername by remember { mutableStateOf("Guest") }
    var coHostAvatarUrl by remember { mutableStateOf("") }
    var pendingCoHostRequests by remember { mutableStateOf<List<CoHostRequest>>(emptyList()) }

    // Host Controls State
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var isCameraMuted by remember { mutableStateOf(false) }
    var isMicMuted by remember { mutableStateOf(false) }
    var currentCameraFilter by remember { mutableStateOf(LiveCameraFilter.NORMAL) }

    // Stream Duration Timer
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            streamDurationSeconds++
        }
    }

    // Load current user profile details
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            try {
                val userDoc = RepositoryManager.firestore.collection("users").document(currentUserId).get().await()
                currentUsername = userDoc.getString("username") ?: "User"
                currentUserAvatar = userDoc.getString("profilePicUrl") ?: ""
                currentUserVerified = userDoc.getBoolean("isVerified") ?: false
                walletBalance = userDoc.getDouble("walletBalance") ?: (userDoc.getDouble("earnings") ?: 0.0)
            } catch (e: Exception) {
                android.util.Log.e("LiveViewer", "Error loading user profile", e)
            }
        }
    }

    // Follow status observation
    LaunchedEffect(story.userId, currentUserId) {
        val targetId = story.userId
        if (!targetId.isNullOrEmpty() && currentUserId.isNotEmpty() && targetId != currentUserId) {
            RepositoryManager.socialRepository.isFollowing(currentUserId, targetId).collect { following ->
                isFollowing = following
            }
        }
    }

    // Post "Joined Live" announcement on entrance for viewers
    LaunchedEffect(story.id) {
        if (!effectiveIsHost && currentUserId.isNotEmpty()) {
            val joinComment = Comment(
                id = java.util.UUID.randomUUID().toString(),
                userId = currentUserId,
                username = currentUsername,
                avatarUrl = currentUserAvatar,
                isVerified = currentUserVerified,
                text = "👋 Joined the live video"
            )
            try {
                RepositoryManager.postRepository.addStoryComment(story.id, joinComment)
            } catch (e: Exception) {
                android.util.Log.e("LiveViewer", "Failed to post joined comment", e)
            }
        }
    }

    // Firestore real-time listener for comments and stream status with proper lifecycle disposal
    DisposableEffect(story.id) {
        val commentsListener = RepositoryManager.firestore.collection("stories")
            .document(story.id)
            .collection("comments")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val commentsList = snapshot.documents.mapNotNull { it.toObject(Comment::class.java) }
                    comments = commentsList
                    val liveViewersCount = maxOf(commentsList.map { it.userId }.distinct().size + 1, 1)
                    viewers = liveViewersCount
                    if (liveViewersCount > peakViewers) {
                        peakViewers = liveViewersCount
                    }
                }
            }

        val storyStatusListener = RepositoryManager.firestore.collection("stories")
            .document(story.id)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val isLive = snapshot.getBoolean("isLive") ?: false
                    if (!isLive && !effectiveIsHost) {
                        isLiveEnded = true
                    }
                }
            }
        onDispose {
            commentsListener.remove()
            storyStatusListener.remove()
        }
    }

    // Preload profiles for all comment senders so chat shows names and avatars, not raw IDs
    LaunchedEffect(comments) {
        val uids = comments.map { it.userId }.filter { it.isNotEmpty() && !userProfilesCache.containsKey(it) }.distinct()
        uids.forEach { uid ->
            try {
                val uDoc = RepositoryManager.firestore.collection("users").document(uid).get().await()
                if (uDoc.exists()) {
                    val resolvedName = uDoc.getString("username")
                        ?: uDoc.getString("displayName")
                        ?: uDoc.getString("name")
                        ?: "User"
                    val resolvedPic = uDoc.getString("profilePicUrl")
                        ?: uDoc.getString("avatarUrl")
                        ?: uDoc.getString("avatar_url")
                        ?: ""
                    val resolvedVer = uDoc.getBoolean("isVerified") ?: false
                    userProfilesCache[uid] = com.example.model.User(
                        id = uid,
                        username = resolvedName,
                        avatarUrl = resolvedPic,
                        isVerified = resolvedVer
                    )
                }
            } catch (ignored: Exception) {}
        }
    }

    // Floating reaction spawner helper
    fun spawnReaction(emoji: String) {
        val newParticle = FloatingParticle(
            id = System.currentTimeMillis() + Random.nextLong(10000),
            emoji = emoji,
            startXOffset = Random.nextInt(-35, 35).toFloat(),
            swayAmount = Random.nextInt(18, 38).toFloat(),
            targetScale = Random.nextFloat() * 0.35f + 1.05f,
            durationMs = Random.nextInt(1800, 2400)
        )
        floatingParticles = (floatingParticles + newParticle).takeLast(25)
        totalLikes++
    }

    // Cleanup finished particles
    LaunchedEffect(floatingParticles) {
        if (floatingParticles.isNotEmpty()) {
            delay(2400)
            val now = System.currentTimeMillis()
            floatingParticles = floatingParticles.filter { now - it.id < 2400 }
        }
    }

    // ExoPlayer for viewer video playback if mediaUrl is provided
    var exoPlayer by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
    DisposableEffect(story.imageUrl, effectiveIsHost) {
        var player: androidx.media3.exoplayer.ExoPlayer? = null
        if (!effectiveIsHost && story.imageUrl.isNotEmpty()) {
            try {
                player = ExoPlayerUtils.createExoPlayer(context).apply {
                    repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                    val playUrl = com.example.utils.MediaResolver.resolveVideoUrl(story.imageUrl).ifEmpty { story.imageUrl }
                    setMediaItem(androidx.media3.common.MediaItem.Builder().setUri(android.net.Uri.parse(playUrl)).build())
                    prepare()
                    playWhenReady = true
                }
                exoPlayer = player
            } catch (e: Exception) {
                android.util.Log.e("LiveViewer", "ExoPlayer init error", e)
            }
        }
        onDispose {
            player?.release()
            exoPlayer = null
        }
    }

    // Main Live Root Container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        SoundManager.playClickSound()
                        // Double tap triggers burst of 3 reactions
                        listOf("❤️", "🔥", "❤️").forEach { emoji ->
                            spawnReaction(emoji)
                        }
                    }
                )
            }
    ) {
        // --- 1. LIVE PLAYER SURFACE ---
        if (effectiveIsHost) {
            // HOST MODE: CameraX Preview Surface
            if (!isCameraMuted) {
                val previewView = remember {
                    PreviewView(context).apply {
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                }

                LaunchedEffect(lensFacing) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val targetSelector = try {
                                val desired = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                                if (cameraProvider.hasCamera(desired)) desired
                                else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA
                                else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) CameraSelector.DEFAULT_FRONT_CAMERA
                                else null
                            } catch (e: Exception) {
                                null
                            }

                            if (targetSelector != null) {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, targetSelector, preview)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("LiveHost", "CameraX bind failed", e)
                        }
                    }, androidx.core.content.ContextCompat.getMainExecutor(context))
                }

                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Video filter effect simulation
                if (currentCameraFilter != LiveCameraFilter.NORMAL) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                when (currentCameraFilter) {
                                    LiveCameraFilter.WARM -> Color(0xFFFF9800).copy(alpha = 0.14f)
                                    LiveCameraFilter.VIBRANT -> Color(0xFFE040FB).copy(alpha = 0.10f)
                                    LiveCameraFilter.CINEMA_NOIR -> Color.Black.copy(alpha = 0.35f)
                                    LiveCameraFilter.SUNSET -> Color(0xFFFF5722).copy(alpha = 0.16f)
                                    else -> Color.Transparent
                                }
                            )
                    )
                }
            } else {
                // Privacy Video Off placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF141414)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .background(Color(0xFF242424), CircleShape)
                                .border(2.dp, Color(0xFF00F0FF).copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = "Camera Off",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Camera is Paused",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Audio stream is still live",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            // AUDIENCE MODE: Video stream or High-definition live broadcast canvas
            if (exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        val view = android.view.LayoutInflater.from(ctx)
                            .inflate(com.example.R.layout.texture_player_view, null, false) as androidx.media3.ui.PlayerView
                        view.apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    update = { view ->
                        if (view.player != exoPlayer) {
                            view.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // High Quality Ambient Stream Broadcast Backdrop
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF1A0A2A),
                                    Color(0xFF0A0D1E),
                                    Color(0xFF05050A)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Ambient creator showcase with pulse
                    val infiniteTransition = rememberInfiniteTransition(label = "BroadcastWave")
                    val waveScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.35f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "WaveScale"
                    )

                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .graphicsLayer {
                                scaleX = waveScale
                                scaleY = waveScale
                                alpha = 0.25f
                            }
                            .background(Color(0xFFFE2C55), CircleShape)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .border(3.dp, Color(0xFFFE2C55), CircleShape)
                        ) {
                            AsyncImage(
                                model = story.avatarUrl.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300" },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = story.username.ifEmpty { "Live Host" },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            VerificationBadge(
                                isVerified = story.isVerified,
                                badgeUrl = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "🔴 Live Audio & Video Broadcast",
                            color = Color(0xFF00F0FF),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        var coHostGridCount by remember { mutableIntStateOf(4) } // Supports 2 or 4 grids (Live Rooms up to 4 co-hosts)
        var isPkBattleActive by remember { mutableStateOf(false) }
        var pkHostScore by remember { mutableIntStateOf(1450) }
        var pkOpponentScore by remember { mutableIntStateOf(1180) }
        var pkRemainingSeconds by remember { mutableIntStateOf(300) } // 5:00 PK Battle timer
        LaunchedEffect(isPkBattleActive) {
            while (isPkBattleActive && pkRemainingSeconds > 0) {
                kotlinx.coroutines.delay(1000L)
                pkRemainingSeconds--
            }
        }
        var showLiveQuestionBoxSheet by remember { mutableStateOf(false) }
        var activePinnedQuestion by remember { mutableStateOf<Pair<String, String>?>(Pair("arjun_india", "What camera & lighting setup are you using for this stream? 🔥")) }
        val liveQuestionsQueue = remember {
            mutableStateListOf(
                "arjun_india" to "What camera & lighting setup are you using for this stream? 🔥",
                "priya_creations" to "When is the next creator meetup in Mumbai? 🙌",
                "rohit_vlogs" to "Can you do a live collab with @social_india?"
            )
        }
        var newQuestionInput by remember { mutableStateOf("") }

        // --- 1B. LIVE PK BATTLE ARENA (Split-Screen Creator vs. Creator + Real-Time Score Bar + Timer) ---
        if (isPkBattleActive) {
            val totalScore = (pkHostScore + pkOpponentScore).coerceAtLeast(1)
            val hostRatio = (pkHostScore.toFloat() / totalScore.toFloat()).coerceIn(0.15f, 0.85f)
            val mins = pkRemainingSeconds / 60
            val secs = pkRemainingSeconds % 60
            val timerFormatted = String.format(java.util.Locale.US, "%02d:%02d", mins, secs)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.56f)
                    .align(Alignment.Center)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0E0F17))
                    .border(1.5.dp, Color(0xFFFFD700).copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            ) {
                // Real-Time PK Gift/Score Progress Bar
                Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF141520)).padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔥 @${story.username.ifEmpty { "host" }} • $pkHostScore pts",
                            color = Color(0xFFFF2E63),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Surface(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700))
                        ) {
                            Text(
                                text = "⚔️ PK $timerFormatted",
                                color = Color(0xFFFFD700),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "$pkOpponentScore pts • @${coHostUsername.ifEmpty { "challenger_live" }} ⚡",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(hostRatio)
                                .fillMaxHeight()
                                .background(Brush.horizontalGradient(listOf(Color(0xFFFF2E63), Color(0xFFFF6B6B))))
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f - hostRatio)
                                .fillMaxHeight()
                                .background(Brush.horizontalGradient(listOf(Color(0xFF0095F6), Color(0xFF00E5FF))))
                        )
                    }
                }

                // Split-Screen Creator vs. Creator Video Panels
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Left: Host
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF1A1018))
                            .border(0.5.dp, Color(0xFFFF2E63).copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(
                                model = story.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${story.username}" },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(2.5.dp, Color(0xFFFF2E63), CircleShape)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("@${story.username.ifEmpty { "host" }}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { pkHostScore += 100 },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2E63)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("+100 Gift 🎁", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // Right: Challenger
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF0F1924))
                            .border(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val oppName = coHostUsername.ifEmpty { "challenger_live" }
                            AsyncImage(
                                model = coHostAvatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=$oppName" },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(2.5.dp, Color(0xFF00E5FF), CircleShape)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("@$oppName", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { pkOpponentScore += 100 },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0095F6)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("+100 Boost ⚡", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // --- 2. CO-HOST LIVE ROOM MULTI-GRID OVERLAY (Up to 4 Grids) ---
        if (isCoHostActive) {
            val coHostParticipants = remember(coHostUsername, currentUsername, coHostGridCount) {
                val firstCoHost = if (effectiveIsHost) coHostUsername.ifEmpty { "creator_guest" } else currentUsername.ifEmpty { "you" }
                listOf(
                    Triple(story.username.ifEmpty { "Host" }, story.avatarUrl, "Host"),
                    Triple(firstCoHost, if (effectiveIsHost) coHostAvatarUrl else currentUserAvatar, "Co-Host 1"),
                    Triple("neha_sharma", "https://api.dicebear.com/7.x/avataaars/png?seed=neha", "Co-Host 2"),
                    Triple("vikram_live", "https://api.dicebear.com/7.x/avataaars/png?seed=vikram", "Co-Host 3")
                ).take(coHostGridCount)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.54f)
                    .align(Alignment.Center)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF101015))
                    .border(1.5.dp, Color(0xFF00F0FF).copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val rows = coHostParticipants.chunked(2)
                    rows.forEach { rowItems ->
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            rowItems.forEach { (uname, avatar, roleLabel) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .border(0.5.dp, Color.White.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .border(2.dp, Color(0xFF00F0FF), CircleShape)
                                        ) {
                                            AsyncImage(
                                                model = avatar.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=$uname" },
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "@$uname",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "🎙️ $roleLabel",
                                            color = Color(0xFF00F0FF),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Co-host layout switcher (2-grid vs 4-grid) & close button
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier.clickable {
                            coHostGridCount = if (coHostGridCount == 4) 2 else 4
                        }
                    ) {
                        Text(
                            text = if (coHostGridCount == 4) "4-Grid Room" else "2-Grid Split",
                            color = Color(0xFF00F0FF),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            isCoHostActive = false
                            Toast.makeText(context, "Co-Host room closed", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Leave", tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }

        // --- 3. GRADIENT VIGNETTES (For Crisp Readability) ---
        // Top Vignette
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                    )
                )
        )

        // Bottom Vignette
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                    )
                )
        )

        // --- 4. TOP CREATOR HEADER BAR ---
        LiveTopBar(
            story = story,
            viewersCount = viewers,
            isFollowing = isFollowing,
            isHost = effectiveIsHost,
            onFollowClick = {
                val targetUserId = story.userId ?: ""
                if (currentUserId.isNotEmpty() && targetUserId.isNotEmpty()) {
                    scope.launch {
                        try {
                            if (isFollowing) {
                                RepositoryManager.socialRepository.unfollowUser(currentUserId, targetUserId)
                                isFollowing = false
                            } else {
                                RepositoryManager.socialRepository.followUser(currentUserId, targetUserId)
                                isFollowing = true
                                // Announce follow in chat
                                val comment = Comment(
                                    id = java.util.UUID.randomUUID().toString(),
                                    userId = currentUserId,
                                    username = currentUsername,
                                    avatarUrl = currentUserAvatar,
                                    isVerified = currentUserVerified,
                                    text = "⭐ Followed the host!"
                                )
                                RepositoryManager.postRepository.addStoryComment(story.id, comment)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("LiveViewer", "Error toggling follow", e)
                        }
                    }
                }
            },
            onViewersClick = { showViewersSheet = true },
            onCloseClick = {
                if (effectiveIsHost) {
                    showEndLiveConfirmDialog = true
                } else {
                    onClose()
                }
            },
            onHostControlsClick = { showHostControlsSheet = true },
            onShareClick = {
                totalSharesCount++
                showShareSheet = true
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // --- 5. FLOATING REACTIONS PARTICLES LAYER ---
        LiveFloatingReactionsEngine(
            particles = floatingParticles,
            modifier = Modifier.fillMaxSize()
        )

        // --- 5B. LIVE QUESTION BOX (Q&A), 4-GRID ROOM & PK BATTLE PILLS ---
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 104.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isPkBattleActive) Color(0xFFFFD700) else Color.Black.copy(alpha = 0.65f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.7f)),
                modifier = Modifier.clickable {
                    isPkBattleActive = !isPkBattleActive
                    if (isPkBattleActive) isCoHostActive = false
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚔️ PK Battle",
                        color = if (isPkBattleActive) Color.Black else Color(0xFFFFD700),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.clickable {
                    isCoHostActive = !isCoHostActive
                    if (isCoHostActive) isPkBattleActive = false
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.GridView, contentDescription = "Live Room", tint = Color(0xFF00F0FF), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("4-Grid", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFE91E63).copy(alpha = 0.85f),
                modifier = Modifier.clickable { showLiveQuestionBoxSheet = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.HelpOutline, contentDescription = "Q&A", tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Q&A (${liveQuestionsQueue.size})", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showLiveQuestionBoxSheet) {
            AlertDialog(
                onDismissRequest = { showLiveQuestionBoxSheet = false },
                title = { Text("Live Q&A Question Box", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tap a question to pin it on the Live screen, or ask a new question:", fontSize = 12.sp, color = Color.Gray)
                        liveQuestionsQueue.forEach { (asker, qText) ->
                            val isPinned = activePinnedQuestion?.second == qText
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isPinned) Color(0xFFE91E63).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activePinnedQuestion = asker to qText
                                        showLiveQuestionBoxSheet = false
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("@$asker", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFE91E63))
                                    Text(qText, fontSize = 13.sp)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = newQuestionInput,
                            onValueChange = { newQuestionInput = it },
                            placeholder = { Text("Ask the host a question...", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newQuestionInput.isNotBlank()) {
                                val entry = currentUsername.ifEmpty { "viewer" } to newQuestionInput.trim()
                                liveQuestionsQueue.add(0, entry)
                                activePinnedQuestion = entry
                                newQuestionInput = ""
                            }
                            showLiveQuestionBoxSheet = false
                        }
                    ) {
                        Text("Submit & Pin", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLiveQuestionBoxSheet = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // --- 6. BOTTOM SECTION: LIVE CHAT + INPUT DOCK ---
        val enrichedComments = remember(comments, userProfilesCache.toMap()) {
            comments.map { comment ->
                val cached = userProfilesCache[comment.userId]
                if (cached != null) {
                    val finalName = when {
                        comment.username.isNotBlank() && comment.username != comment.userId && comment.username != "User" -> comment.username
                        cached.username.isNotBlank() && cached.username != comment.userId -> cached.username
                        else -> "User"
                    }
                    val finalAvatar = when {
                        comment.avatarUrl.isNotBlank() && !comment.avatarUrl.contains("dicebear") -> comment.avatarUrl
                        cached.avatarUrl.isNotBlank() -> cached.avatarUrl
                        else -> comment.avatarUrl
                    }
                    comment.copy(
                        username = finalName,
                        avatarUrl = finalAvatar,
                        isVerified = comment.isVerified || cached.isVerified
                    )
                } else if (comment.username == comment.userId || comment.username.isBlank()) {
                    comment.copy(username = "User")
                } else {
                    comment
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            // Pinned Question Box Card Display above Live Chat
            activePinnedQuestion?.let { (qAuthor, qBody) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1F1B2E).copy(alpha = 0.92f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE91E63).copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE91E63)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("?", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Question from @$qAuthor", color = Color(0xFFFF80AB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(qBody, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        }
                        IconButton(
                            onClick = { activePinnedQuestion = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss Question", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Live Comments Stream
            if (commentsEnabled) {
                LiveChatFeed(
                    comments = enrichedComments,
                    pinnedComment = pinnedComment,
                    onCommentClick = { clickedComment ->
                        if (effectiveIsHost) {
                            selectedCommentForMod = clickedComment
                            showModeratorSheet = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .padding(start = 14.dp, bottom = 6.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(start = 14.dp, bottom = 10.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "💬 Live chat is paused by the host",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            // Bottom Action Bar
            LiveBottomActionBar(
                commentText = newCommentText,
                onCommentChange = { newCommentText = it },
                onSendComment = {
                    if (newCommentText.isNotBlank() && currentUserId.isNotEmpty()) {
                        // Check if muted by host
                        if (mutedUserIds.contains(currentUserId)) {
                            Toast.makeText(context, "You are muted by the host in this live", Toast.LENGTH_SHORT).show()
                            return@LiveBottomActionBar
                        }

                        // Check slow mode
                        if (isSlowModeEnabled && !effectiveIsHost) {
                            val now = System.currentTimeMillis()
                            if (now - lastCommentSentTime < 10000) {
                                val remaining = ((10000 - (now - lastCommentSentTime)) / 1000) + 1
                                Toast.makeText(context, "Slow mode active: wait ${remaining}s", Toast.LENGTH_SHORT).show()
                                return@LiveBottomActionBar
                            }
                            lastCommentSentTime = now
                        }

                        val commentToSend = newCommentText.trim()
                        newCommentText = ""
                        scope.launch {
                            try {
                                val comment = Comment(
                                    id = java.util.UUID.randomUUID().toString(),
                                    userId = currentUserId,
                                    username = currentUsername,
                                    avatarUrl = currentUserAvatar,
                                    isVerified = currentUserVerified,
                                    text = commentToSend
                                )
                                RepositoryManager.postRepository.addStoryComment(story.id, comment)
                            } catch (e: Exception) {
                                android.util.Log.e("LiveViewer", "Error sending comment", e)
                            }
                        }
                    }
                },
                onGiftClick = { showGiftSheet = true },
                onReactionClick = { emoji ->
                    spawnReaction(emoji)
                },
                onJoinLiveClick = { showJoinLiveSheet = true },
                onHostToolsClick = { showHostControlsSheet = true },
                onShareClick = {
                    totalSharesCount++
                    showShareSheet = true
                },
                onStickerClick = {
                    showLiveMediaPicker = true
                },
                isHost = effectiveIsHost,
                isCoHostActive = isCoHostActive
            )
        }

        // --- 7. MODALS & BOTTOM SHEETS ---

        // Sticker & Media Picker Sheet
        if (showLiveMediaPicker) {
            SocialMediaPickerBottomSheet(
                onDismissRequest = { showLiveMediaPicker = false },
                initialTab = MediaPickerTab.STICKER,
                onStickerSelected = { sticker ->
                    showLiveMediaPicker = false
                    if (currentUserId.isNotEmpty()) {
                        scope.launch {
                            try {
                                val comment = Comment(
                                    id = java.util.UUID.randomUUID().toString(),
                                    userId = currentUserId,
                                    username = currentUsername,
                                    avatarUrl = currentUserAvatar,
                                    isVerified = currentUserVerified,
                                    text = sticker.name.ifEmpty { "Sent a sticker 🎨" },
                                    gifUrl = sticker.mediaUrl.ifEmpty { sticker.thumbnailUrl },
                                    type = "STICKER",
                                    stickerId = sticker.stickerId,
                                    packId = sticker.packId,
                                    driveFileId = sticker.driveFileId,
                                    thumbnailDriveFileId = sticker.thumbnailDriveFileId,
                                    isAnimated = sticker.isAnimated
                                )
                                RepositoryManager.postRepository.addStoryComment(story.id, comment)
                                RepositoryManager.stickerRepository.recordStickerUsage(currentUserId, sticker)
                            } catch (e: Exception) {
                                android.util.Log.e("LiveViewer", "Error sending sticker in live", e)
                            }
                        }
                    }
                },
                onGifSelected = { gif ->
                    showLiveMediaPicker = false
                    if (currentUserId.isNotEmpty()) {
                        scope.launch {
                            try {
                                val comment = Comment(
                                    id = java.util.UUID.randomUUID().toString(),
                                    userId = currentUserId,
                                    username = currentUsername,
                                    avatarUrl = currentUserAvatar,
                                    isVerified = currentUserVerified,
                                    text = "Shared a GIF 🎭",
                                    gifUrl = gif.fullUrl,
                                    type = "GIF"
                                )
                                RepositoryManager.postRepository.addStoryComment(story.id, comment)
                            } catch (e: Exception) {
                                android.util.Log.e("LiveViewer", "Error sending GIF in live", e)
                            }
                        }
                    }
                },
                onEmojiSelected = { emoji ->
                    newCommentText += emoji
                    showLiveMediaPicker = false
                }
            )
        }

        // Gift Catalog Sheet
        if (showGiftSheet) {
            LiveGiftBottomSheet(
                walletBalance = walletBalance,
                onDismiss = { showGiftSheet = false },
                onGiftSelected = { gift ->
                    showGiftSheet = false
                    totalGiftsCount++
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    SoundManager.playClickSound()

                    // Spawn burst reactions for gift
                    repeat(6) {
                        spawnReaction(gift.emoji)
                    }

                    // Post gift announcement in live chat
                    if (currentUserId.isNotEmpty()) {
                        scope.launch {
                            try {
                                val giftModel = com.example.repository.LiveGiftModel(
                                    id = gift.id,
                                    name = gift.name,
                                    emoji = gift.emoji,
                                    coins = gift.coins,
                                    hexColor = String.format("#%06X", (0xFFFFFF and gift.color.hashCode())),
                                    sticker3dUrl = gift.sticker3dUrl
                                )
                                val sendResult = RepositoryManager.giftRepository.sendGift(
                                    senderId = currentUserId,
                                    senderUsername = currentUsername,
                                    senderAvatar = currentUserAvatar,
                                    isSenderVerified = currentUserVerified,
                                    streamerId = story.userId ?: "",
                                    liveStoryId = story.id,
                                    gift = giftModel
                                )

                                if (sendResult.isSuccess) {
                                    val giftComment = Comment(
                                        id = java.util.UUID.randomUUID().toString(),
                                        userId = currentUserId,
                                        username = currentUsername,
                                        avatarUrl = currentUserAvatar,
                                        isVerified = currentUserVerified,
                                        text = "🎁 Sent a ${gift.name} ${gift.emoji} (${gift.coins} Coins)"
                                    )
                                    RepositoryManager.postRepository.addStoryComment(story.id, giftComment)
                                } else {
                                    val err = sendResult.exceptionOrNull()?.message ?: "Transaction failed"
                                    android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("LiveViewer", "Failed to send gift comment", e)
                            }
                        }
                    }
                }
            )
        }

        // Audience Viewers List Sheet
        if (showViewersSheet) {
            LiveViewersBottomSheet(
                viewersCount = viewers,
                comments = comments,
                onDismiss = { showViewersSheet = false }
            )
        }

        // Join Live (Co-Host) Request Sheet
        if (showJoinLiveSheet) {
            LiveJoinRequestSheet(
                hostUsername = story.username.ifEmpty { "Host" },
                isRequestPending = isJoinRequestPending,
                isCoHostActive = isCoHostActive,
                onRequestJoin = {
                    isJoinRequestPending = true
                    Toast.makeText(context, "Request sent to host!", Toast.LENGTH_SHORT).show()
                    // Send join request announcement in chat
                    if (currentUserId.isNotEmpty()) {
                        scope.launch {
                            try {
                                val reqComment = Comment(
                                    id = java.util.UUID.randomUUID().toString(),
                                    userId = currentUserId,
                                    username = currentUsername,
                                    avatarUrl = currentUserAvatar,
                                    isVerified = currentUserVerified,
                                    text = "🙋 Requested to join Live as Co-Host"
                                )
                                RepositoryManager.postRepository.addStoryComment(story.id, reqComment)
                            } catch (e: Exception) { }
                        }
                    }
                },
                onTestSplitScreen = {
                    showJoinLiveSheet = false
                    isCoHostActive = true
                    coHostUsername = currentUsername
                    coHostAvatarUrl = currentUserAvatar
                    Toast.makeText(context, "Joined Live as Co-Host!", Toast.LENGTH_SHORT).show()
                },
                onLeaveCoHost = {
                    showJoinLiveSheet = false
                    isCoHostActive = false
                    Toast.makeText(context, "Left Co-Host broadcast", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showJoinLiveSheet = false }
            )
        }

        // Host Studio Controls Sheet
        if (showHostControlsSheet) {
            LiveHostControlsSheet(
                isCameraMuted = isCameraMuted,
                isMicMuted = isMicMuted,
                currentFilter = currentCameraFilter,
                commentsEnabled = commentsEnabled,
                pendingRequests = pendingCoHostRequests,
                onFlipCamera = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        CameraSelector.LENS_FACING_BACK
                    } else {
                        CameraSelector.LENS_FACING_FRONT
                    }
                },
                onToggleCameraMute = { isCameraMuted = !isCameraMuted },
                onToggleMicMute = {
                    isMicMuted = !isMicMuted
                    Toast.makeText(context, if (isMicMuted) "Microphone Muted" else "Microphone Unmuted", Toast.LENGTH_SHORT).show()
                },
                onSelectFilter = { currentCameraFilter = it },
                onToggleComments = {
                    commentsEnabled = !commentsEnabled
                    Toast.makeText(context, if (commentsEnabled) "Comments Enabled" else "Comments Disabled", Toast.LENGTH_SHORT).show()
                },
                onAcceptCoHost = { req ->
                    pendingCoHostRequests = pendingCoHostRequests.filter { it.userId != req.userId }
                    coHostUsername = req.username
                    coHostAvatarUrl = req.avatarUrl
                    isCoHostActive = true
                    Toast.makeText(context, "${req.username} joined as Co-Host", Toast.LENGTH_SHORT).show()
                },
                onDeclineCoHost = { req ->
                    pendingCoHostRequests = pendingCoHostRequests.filter { it.userId != req.userId }
                },
                onEndLiveClick = {
                    showHostControlsSheet = false
                    showEndLiveConfirmDialog = true
                },
                onAnalyticsClick = {
                    showHostControlsSheet = false
                    showAnalyticsDialog = true
                },
                onModeratorToolsClick = {
                    showHostControlsSheet = false
                    selectedCommentForMod = null
                    showModeratorSheet = true
                },
                onInviteFriendsClick = {
                    showHostControlsSheet = false
                    showInviteFriendsSheet = true
                },
                onShareLiveClick = {
                    showHostControlsSheet = false
                    totalSharesCount++
                    showShareSheet = true
                },
                onDismiss = { showHostControlsSheet = false }
            )
        }

        // Host End Live Confirmation Dialog
        if (showEndLiveConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showEndLiveConfirmDialog = false },
                containerColor = Color(0xFF1F1F1F),
                title = {
                    Text(
                        text = "End Live Video?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to end your live broadcast? All viewers will see the live ended summary.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showEndLiveConfirmDialog = false
                            scope.launch {
                                try {
                                    RepositoryManager.postRepository.endLiveStory(story.id)
                                } catch (e: Exception) {
                                    android.util.Log.e("LiveHost", "Error ending live story", e)
                                }
                                isLiveEnded = true
                                showAnalyticsDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                    ) {
                        Text("End Now", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEndLiveConfirmDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                }
            )
        }

        // Live Share Bottom Sheet (In-app sharing, copy link, preview)
        if (showShareSheet) {
            LiveShareBottomSheet(
                story = story,
                onDismiss = { showShareSheet = false }
            )
        }

        // Invite Friends Bottom Sheet
        if (showInviteFriendsSheet) {
            InviteFriendsBottomSheet(
                storyId = story.id,
                hostUsername = story.username.ifEmpty { "Host" },
                onDismiss = { showInviteFriendsSheet = false }
            )
        }

        // Live Moderator Bottom Sheet
        if (showModeratorSheet) {
            LiveModeratorBottomSheet(
                selectedComment = selectedCommentForMod,
                isChatEnabled = commentsEnabled,
                isSlowModeEnabled = isSlowModeEnabled,
                onToggleChat = { enabled ->
                    commentsEnabled = enabled
                    Toast.makeText(context, if (enabled) "Live chat enabled" else "Live chat paused", Toast.LENGTH_SHORT).show()
                },
                onToggleSlowMode = { enabled ->
                    isSlowModeEnabled = enabled
                    Toast.makeText(context, if (enabled) "Slow mode enabled (10s)" else "Slow mode disabled", Toast.LENGTH_SHORT).show()
                },
                onPinComment = { comment ->
                    pinnedComment = if (pinnedComment?.id == comment.id) null else comment
                    Toast.makeText(context, "Comment pinned", Toast.LENGTH_SHORT).show()
                },
                onMuteUser = { uid, uname ->
                    mutedUserIds = mutedUserIds + uid
                    Toast.makeText(context, "@$uname muted from chat", Toast.LENGTH_SHORT).show()
                },
                onKickUser = { uid, uname ->
                    Toast.makeText(context, "@$uname removed from live room", Toast.LENGTH_SHORT).show()
                },
                onDismiss = {
                    showModeratorSheet = false
                    selectedCommentForMod = null
                }
            )
        }

        // Creator Live Analytics Dashboard
        if (showAnalyticsDialog) {
            CreatorLiveAnalyticsDialog(
                stats = CreatorLiveStats(
                    title = "Live Broadcast Summary",
                    durationSeconds = streamDurationSeconds,
                    peakViewers = maxOf(peakViewers, viewers),
                    totalViewers = maxOf(peakViewers, viewers),
                    totalWatchTimeMinutes = maxOf(1L, (streamDurationSeconds * peakViewers) / 60),
                    followersGained = followersGainedCount,
                    totalLikes = totalLikes,
                    totalComments = comments.size,
                    totalShares = totalSharesCount,
                    giftsCount = totalGiftsCount,
                    coinsEarned = totalGiftsCount * 25
                ),
                topGifters = emptyList(),
                onDismiss = {
                    showAnalyticsDialog = false
                    if (isLiveEnded) {
                        onClose()
                    }
                }
            )
        }

        // Live Ended Screen for Viewers
        if (isLiveEnded && !effectiveIsHost) {
            val minutes = streamDurationSeconds / 60
            val seconds = streamDurationSeconds % 60
            val formattedDuration = String.format("%02d:%02d", minutes, seconds)
            LiveEndedScreen(
                story = story,
                durationText = formattedDuration,
                totalViewersCount = peakViewers,
                onWatchReplay = {
                    Toast.makeText(context, "Playing stream replay...", Toast.LENGTH_SHORT).show()
                },
                onExploreOthers = {
                    onClose()
                },
                onClose = onClose
            )
        }

        // Host Stream Ended Summary Dialog
        if (showStreamSummaryDialog) {
            LiveStreamSummaryDialog(
                durationSeconds = streamDurationSeconds,
                totalViewers = peakViewers,
                totalLikes = totalLikes,
                giftsCount = totalGiftsCount,
                onDismiss = {
                    showStreamSummaryDialog = false
                    onClose()
                }
            )
        }

        // Real-Time Animated Gift Overlay for all viewers
        com.example.ui.components.RealtimeLiveGiftOverlay(
            liveStoryId = story.id,
            modifier = Modifier.fillMaxSize()
        )
    }
}
