@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import com.example.model.RepositoryManager
import com.example.model.Video
import com.example.repository.ProCreatorSuiteRepository
import com.example.utils.ExoPlayerUtils
import com.example.utils.GlobalMediaSessionManager
import com.example.utils.GoogleDrivePermissionRepairManager
import com.example.utils.MediaResolver
import com.example.utils.MediaSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchPartyScreen(
    initialRoomId: String? = null,
    partnerId: String = "",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }
    val currentUser by RepositoryManager.currentUserState.collectAsStateWithLifecycle()

    var availableReels by remember {
        mutableStateOf(
            RepositoryManager.getCachedFeedVideos()
                .filter { !it.isMediaAnImage && it.videoUrl.isNotBlank() && !MediaResolver.isDummyOrSampleUrl(it.videoUrl) }
        )
    }
    var isFetchingReels by remember { mutableStateOf(availableReels.isEmpty()) }

    var activeRoomId by remember { mutableStateOf(initialRoomId?.takeIf { it.isNotBlank() && it != "new" } ?: "") }
    var joinCodeInput by remember { mutableStateOf("") }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showQueuePicker by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }
    var isMuted by remember { mutableStateOf(false) }

    // Fetch real playable reels + create or join synced Watch Party room
    LaunchedEffect(Unit) {
        if (availableReels.isEmpty()) {
            isFetchingReels = true
            val fetched = ProCreatorSuiteRepository.fetchRecentVideoReelsForWatchParty()
            if (fetched.isNotEmpty()) {
                availableReels = fetched
            }
            isFetchingReels = false
        }
        if (activeRoomId.isBlank()) {
            val firstReel = availableReels.firstOrNull()
            val res = ProCreatorSuiteRepository.createOrJoinWatchParty(
                roomId = null,
                hostId = currentUserId,
                hostUsername = currentUser?.username?.ifBlank { "Host" } ?: "Host",
                chatPartnerId = partnerId,
                initialVideoId = firstReel?.id ?: "",
                initialVideoUrl = firstReel?.videoUrl ?: "",
                initialVideoTitle = firstReel?.description?.take(48)?.ifBlank { "@${firstReel.user.username}'s Reel" }
                    ?: "Social India Watch Party",
                initialThumb = firstReel?.thumbnailUrl ?: ""
            )
            res.onSuccess { activeRoomId = it }
        } else {
            ProCreatorSuiteRepository.createOrJoinWatchParty(
                roomId = activeRoomId,
                hostId = currentUserId,
                hostUsername = currentUser?.username?.ifBlank { "Guest" } ?: "Guest"
            )
        }
    }

    val session by remember(activeRoomId) {
        ProCreatorSuiteRepository.observeWatchParty(activeRoomId)
    }.collectAsStateWithLifecycle(initialValue = null)

    val partyMessages by remember(activeRoomId) {
        ProCreatorSuiteRepository.observeWatchPartyMessages(activeRoomId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    fun selectReel(reel: Video) {
        if (activeRoomId.isBlank()) return
        scope.launch {
            ProCreatorSuiteRepository.syncWatchPartyPlayback(
                roomId = activeRoomId,
                isPlaying = true,
                positionMs = 0L,
                videoId = reel.id,
                videoUrl = reel.videoUrl,
                videoTitle = reel.description.take(48).ifBlank { "@${reel.user.username}'s Reel" },
                videoThumb = MediaResolver.resolveVideoPosterUrl(reel.videoUrl, reel.thumbnailUrl)
            )
        }
    }

    fun playNextReel() {
        if (availableReels.isEmpty()) return
        val curIdx = availableReels.indexOfFirst { it.id == session?.videoId || it.videoUrl == session?.videoUrl }
        val next = availableReels[(curIdx + 1) % availableReels.size]
        selectReel(next)
    }

    // Auto-seed the first playable reel if the room has no video selected yet
    LaunchedEffect(session?.id, session?.videoUrl, availableReels.size) {
        val s = session ?: return@LaunchedEffect
        if (s.videoUrl.isBlank() && activeRoomId.isNotBlank() && availableReels.isNotEmpty()) {
            selectReel(availableReels.first())
        }
    }

    // Use ExoPlayerUtils.createExoPlayer (emulator software decoder fallback + OkHttp/Drive/Cache factory)
    val exoPlayer = remember(context) {
        ExoPlayerUtils.createExoPlayer(context, isMusic = false).apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 1f
        }
    }

    var loadedVideoUrl by remember { mutableStateOf("") }
    var hasRenderedFirstFrame by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var isVideoError by remember { mutableStateOf(false) }
    var retryCounter by remember { mutableIntStateOf(0) }

    DisposableEffect(exoPlayer) {
        GlobalMediaSessionManager.requestFocus(MediaSource.REELS, context)
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                isBuffering = false
                isVideoError = false
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    isVideoError = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                isVideoError = true
                val rawUrl = session?.videoUrl.orEmpty()
                MediaResolver.extractGoogleDriveFileId(rawUrl)?.let { fid ->
                    GoogleDrivePermissionRepairManager.repairPermissionAsync(fid)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            ExoPlayerUtils.safeRelease(exoPlayer)
            GlobalMediaSessionManager.releaseFocus(MediaSource.REELS, context)
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(session?.videoUrl, session?.isPlaying, session?.positionMs, session?.updatedAt, retryCounter) {
        val s = session ?: return@LaunchedEffect
        val rawUrl = s.videoUrl
        if (rawUrl.isBlank()) return@LaunchedEffect
        try {
            val resolvedUrl = MediaResolver.resolveVideoUrl(rawUrl)
            if (resolvedUrl.isNotBlank() && (resolvedUrl != loadedVideoUrl || isVideoError)) {
                loadedVideoUrl = resolvedUrl
                hasRenderedFirstFrame = false
                isVideoError = false
                isBuffering = true
                exoPlayer.setMediaItem(MediaItem.fromUri(resolvedUrl))
                exoPlayer.prepare()
            }
            if (loadedVideoUrl.isNotBlank()) {
                if (abs(exoPlayer.currentPosition - s.positionMs) > 2500L && s.positionMs >= 0L) {
                    exoPlayer.seekTo(s.positionMs)
                }
                exoPlayer.playWhenReady = s.isPlaying
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            isVideoError = true
            isBuffering = false
        }
    }

    // Pulsing Live Sync badge animation
    val pulseTransition = rememberInfiniteTransition(label = "wp_live_pulse")
    val livePulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "wp_live_alpha"
    )

    // Floating Emoji Reaction Fountain animation
    var activeFloatingEmoji by remember { mutableStateOf<Pair<String, String>?>(null) }
    val emojiOffsetY = remember { Animatable(0f) }
    val emojiAlpha = remember { Animatable(0f) }
    LaunchedEffect(session?.lastEmojiTs) {
        val s = session ?: return@LaunchedEffect
        if (s.lastEmoji.isNotBlank() && s.lastEmojiTs > 0L) {
            activeFloatingEmoji = s.lastEmoji to s.lastEmojiSender
            emojiOffsetY.snapTo(0f)
            emojiAlpha.snapTo(1f)
            launch { emojiOffsetY.animateTo(-150f, tween(1350)) }
            emojiAlpha.animateTo(0f, tween(1350))
            activeFloatingEmoji = null
        }
    }

    val chatListState = rememberLazyListState()
    LaunchedEffect(partyMessages.size) {
        if (partyMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(partyMessages.lastIndex)
        }
    }

    val activePosterUrl = remember(session?.videoUrl, session?.videoThumb) {
        MediaResolver.resolveVideoPosterUrl(session?.videoUrl.orEmpty(), session?.videoThumb.orEmpty())
    }

    Scaffold(
        containerColor = Color(0xFF07070F),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Watch Party",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFFFF2A6D).copy(alpha = livePulseAlpha)
                            ) {
                                Text(
                                    text = "● LIVE SYNC",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Room ${session?.roomCode ?: "..."} • ${session?.participants?.size ?: 1} watching in HD",
                            color = Color(0xFF00F0FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("watch_party_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Surface(
                        onClick = { showJoinDialog = true },
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFFFD700).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "Join Code",
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (activeRoomId.isNotBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Watch Party Room", activeRoomId))
                                Toast.makeText(context, "Room ID copied! Share with friends", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Room ID", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF07070F))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0C0A1E), Color(0xFF07070F), Color(0xFF0A0D18))
                    )
                )
                .padding(padding)
                .navigationBarsPadding()
        ) {
            // High-Graphics Cinema Synced Video Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(276.dp)
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF1A1432), Color(0xFF0D1426)))
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF2A6D), Color(0xFFFFD700))),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                // TextureView Player (eliminates SurfaceView black-screen composition bug!)
                if (loadedVideoUrl.isNotBlank() && !isVideoError) {
                    AndroidView(
                        factory = { ctx ->
                            ExoPlayerUtils.createTexturePlayerView(ctx, AspectRatioFrameLayout.RESIZE_MODE_ZOOM).apply {
                                player = exoPlayer
                                useController = false
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

                // Poster Thumbnail Underlayer while video decodes first frame (zero black screen!)
                if (!hasRenderedFirstFrame || isVideoError || loadedVideoUrl.isBlank()) {
                    if (activePosterUrl.isNotBlank()) {
                        AsyncImage(
                            model = ExoPlayerUtils.buildFastPosterRequest(context, activePosterUrl, 720, 720),
                            contentDescription = "Reel Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                        )
                    }
                }

                // Center State Overlay: Buffering / Error / Empty
                when {
                    isVideoError -> {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.Black.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, Color(0xFFFF2A6D).copy(alpha = 0.5f)),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TextButton(onClick = { retryCounter++ }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Color(0xFF00F0FF))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                if (availableReels.size > 1) {
                                    Button(
                                        onClick = { playNextReel() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A6D)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Next Reel", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    isBuffering || isFetchingReels -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF00F0FF),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isFetchingReels) "Loading HD Reels..." else "Syncing video...",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    loadedVideoUrl.isBlank() && availableReels.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.QueuePlayNext, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No video Reels uploaded yet", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Upload a Reel to co-watch live with friends!", color = Color(0xFFB4B4CC), fontSize = 11.sp)
                        }
                    }
                }

                // Top-Right Volume Pill
                Surface(
                    onClick = { isMuted = !isMuted },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Bottom Cinema Controls Overlay
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session?.videoTitle ?: "Synced HD Reel",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Host @${session?.hostUsername ?: "Host"} • ${if (session?.isPlaying == true) "Playing" else "Paused"}",
                            color = Color(0xFF00F0FF),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val backPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(backPos)
                                scope.launch {
                                    ProCreatorSuiteRepository.syncWatchPartyPlayback(
                                        roomId = activeRoomId,
                                        isPlaying = session?.isPlaying ?: true,
                                        positionMs = backPos
                                    )
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        FilledIconButton(
                            onClick = {
                                val nextPlay = !(session?.isPlaying ?: true)
                                exoPlayer.playWhenReady = nextPlay
                                scope.launch {
                                    ProCreatorSuiteRepository.syncWatchPartyPlayback(
                                        roomId = activeRoomId,
                                        isPlaying = nextPlay,
                                        positionMs = exoPlayer.currentPosition
                                    )
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFFF2A6D)),
                            modifier = Modifier.size(40.dp).testTag("watch_party_play_pause_btn")
                        ) {
                            Icon(
                                if (session?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play Pause",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { playNextReel() },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next Reel", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        FilledTonalIconButton(
                            onClick = { showQueuePicker = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFF23233A)),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.QueuePlayNext, contentDescription = "Reel Queue", tint = Color(0xFF00F0FF), modifier = Modifier.size(19.dp))
                        }
                    }
                }

                // Floating Emoji Reaction Burst
                activeFloatingEmoji?.let { (emoji, sender) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = 58.dp)
                            .offset(y = emojiOffsetY.value.dp)
                            .alpha(emojiAlpha.value)
                    ) {
                        Text(text = emoji, fontSize = 42.sp)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.72f)
                        ) {
                            Text(
                                text = "@$sender",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Inline Quick Reel Switcher Strip (1-tap reel change without leaving Watch Party!)
            if (availableReels.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableReels.take(12), key = { it.id }) { reel ->
                        val isActive = session?.videoId == reel.id || session?.videoUrl == reel.videoUrl
                        val thumbUrl = MediaResolver.resolveVideoPosterUrl(reel.videoUrl, reel.thumbnailUrl)
                            .ifBlank { reel.user.avatarUrl }
                        Surface(
                            onClick = { selectReel(reel) },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF151526),
                            border = BorderStroke(
                                if (isActive) 2.dp else 1.dp,
                                if (isActive) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier.width(122.dp).height(54.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AsyncImage(
                                    model = ExoPlayerUtils.buildFastPosterRequest(context, thumbUrl, 160, 160),
                                    contentDescription = reel.user.username,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isActive) "▶ PLAYING" else "@${reel.user.username}",
                                        color = if (isActive) Color(0xFF00F0FF) else Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = reel.description.ifBlank { "HD Reel" },
                                        color = Color(0xFFA0A0B8),
                                        fontSize = 9.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Floating Reaction Bar
            val watchPartyEmojis = remember { listOf("🔥", "😂", "❤️", "🤯", "👏", "💎", "🎉") }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(watchPartyEmojis, key = { it }) { emoji ->
                    Surface(
                        onClick = {
                            scope.launch {
                                ProCreatorSuiteRepository.sendWatchPartyEmoji(
                                    roomId = activeRoomId,
                                    emoji = emoji,
                                    senderUsername = currentUser?.username?.ifBlank { "Viewer" } ?: "Viewer"
                                )
                            }
                        },
                        shape = CircleShape,
                        color = Color(0xFF17172A),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = emoji, fontSize = 19.sp)
                        }
                    }
                }
            }

            // Live Watch Party Chat Feed
            LazyColumn(
                state = chatListState,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (partyMessages.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF141426),
                            border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.25f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(20.dp))
                                Text(
                                    text = "Watch Party is LIVE! Tap Copy Room ID to invite friends — play, pause, switch reels & react in real time.",
                                    color = Color(0xFFC4C4DC),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                items(partyMessages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == currentUserId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isMine) Color(0xFF2B1B4D) else Color(0xFF161628),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    text = "@${msg.senderUsername}",
                                    color = if (isMine) Color(0xFF00F0FF) else Color(0xFFFFD700),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                                Text(text = msg.text, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Live Chat Composer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10101D))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    placeholder = { Text("React or chat live...", color = Color(0xFF8A8AA6), fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f)
                )
                FilledIconButton(
                    onClick = {
                        val txt = chatInput.trim()
                        if (txt.isEmpty() || activeRoomId.isBlank()) return@FilledIconButton
                        chatInput = ""
                        scope.launch {
                            ProCreatorSuiteRepository.sendWatchPartyMessage(
                                roomId = activeRoomId,
                                senderId = currentUserId,
                                senderUsername = currentUser?.username?.ifBlank { "Viewer" } ?: "Viewer",
                                senderAvatar = currentUser?.avatarUrl ?: "",
                                text = txt
                            )
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF00F0FF)),
                    modifier = Modifier.size(48.dp).testTag("watch_party_send_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.Black)
                }
            }
        }
    }

    // Reel Queue Picker Sheet
    if (showQueuePicker) {
        ModalBottomSheet(
            onDismissRequest = { showQueuePicker = false },
            containerColor = Color(0xFF141422)
        ) {
            Column(modifier = Modifier.padding(16.dp).navigationBarsPadding()) {
                Text("Switch Synced Reel", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 380.dp)
                ) {
                    items(availableReels, key = { it.id }) { reel ->
                        val poster = MediaResolver.resolveVideoPosterUrl(reel.videoUrl, reel.thumbnailUrl).ifBlank { reel.user.avatarUrl }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1D1D30))
                                .clickable {
                                    showQueuePicker = false
                                    selectReel(reel)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AsyncImage(
                                model = ExoPlayerUtils.buildFastPosterRequest(context, poster, 180, 180),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = reel.description.ifBlank { "Reel by @${reel.user.username}" },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(text = "@${reel.user.username}", color = Color(0xFFA0A0B8), fontSize = 11.sp)
                            }
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color(0xFF00F0FF))
                        }
                    }
                }
            }
        }
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor = Color(0xFF161626),
            titleContentColor = Color.White,
            title = { Text("Join Friend's Watch Party", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = joinCodeInput,
                    onValueChange = { joinCodeInput = it },
                    label = { Text("Paste Room ID (e.g. wp_a1b2c3d4)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = joinCodeInput.trim()
                        if (trimmed.isNotEmpty()) {
                            activeRoomId = trimmed
                            showJoinDialog = false
                            scope.launch {
                                ProCreatorSuiteRepository.createOrJoinWatchParty(
                                    roomId = trimmed,
                                    hostId = currentUserId,
                                    hostUsername = currentUser?.username?.ifBlank { "Guest" } ?: "Guest"
                                )
                            }
                        }
                    }
                ) { Text("Join Room") }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) { Text("Cancel") }
            }
        )
    }
}
