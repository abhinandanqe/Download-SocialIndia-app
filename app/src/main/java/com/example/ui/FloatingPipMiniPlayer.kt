package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.utils.ExoPlayerUtils
import com.example.utils.VideoCacheManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

enum class PipMode {
    VIDEO_REEL,
    LIVE_STREAM,
    ACTIVE_CALL
}

@Immutable
data class PipMediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val mode: PipMode = PipMode.VIDEO_REEL,
    val isAudioCall: Boolean = false
)

/**
 * Global Picture-in-Picture (PiP) Mini-Player Controller
 * Allows Live Streams, Active Calls, and Reels/Feed videos to shrink into a
 * draggable floating corner mini-player while browsing Home Feed, Search, or DMs.
 */
object GlobalPipController {
    private val _activePipItem = MutableStateFlow<PipMediaItem?>(null)
    val activePipItem: StateFlow<PipMediaItem?> = _activePipItem.asStateFlow()

    fun startVideoPip(id: String, title: String, authorName: String, videoUrl: String, thumbnailUrl: String = "") {
        if (videoUrl.isBlank()) return
        _activePipItem.value = PipMediaItem(
            id = id,
            title = title.ifBlank { "Reel Video" },
            subtitle = "@$authorName",
            videoUrl = videoUrl,
            thumbnailUrl = thumbnailUrl,
            mode = PipMode.VIDEO_REEL
        )
    }

    fun startLivePip(streamId: String, hostName: String, title: String, streamUrl: String, avatarUrl: String = "") {
        _activePipItem.value = PipMediaItem(
            id = streamId,
            title = title.ifBlank { "$hostName Live" },
            subtitle = "LIVE • @$hostName",
            videoUrl = streamUrl,
            thumbnailUrl = avatarUrl,
            mode = PipMode.LIVE_STREAM
        )
    }

    fun startCallPip(callId: String, peerName: String, peerAvatar: String, isAudio: Boolean) {
        _activePipItem.value = PipMediaItem(
            id = callId,
            title = peerName.ifBlank { "Active Call" },
            subtitle = if (isAudio) "Voice Call Active" else "Video Call Active",
            thumbnailUrl = peerAvatar,
            mode = PipMode.ACTIVE_CALL,
            isAudioCall = isAudio
        )
    }

    fun closePip() {
        _activePipItem.value = null
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun FloatingPipMiniPlayerOverlay(
    onExpandVideo: (PipMediaItem) -> Unit = {},
    onExpandCall: (PipMediaItem) -> Unit = {},
    onEndCallFromPip: (PipMediaItem) -> Unit = {}
) {
    val activeItem by GlobalPipController.activePipItem.collectAsState()
    val context = LocalContext.current

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isMuted by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }

    AnimatedVisibility(
        visible = activeItem != null,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f)
    ) {
        val item = activeItem ?: return@AnimatedVisibility

        val exoPlayer = remember(item.videoUrl) {
            if (item.videoUrl.isNotBlank()) {
                ExoPlayerUtils.acquireRecycledPlayer(context).apply {
                    val mediaSource = VideoCacheManager.buildCachedMediaSource(context, item.videoUrl)
                    setMediaSource(mediaSource)
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = if (isMuted) 0f else 1f
                    playWhenReady = true
                    prepare()
                }
            } else null
        }

        DisposableEffect(item.videoUrl) {
            onDispose {
                exoPlayer?.let { ExoPlayerUtils.releaseToPool(it) }
            }
        }

        LaunchedEffect(isMuted) {
            exoPlayer?.volume = if (isMuted) 0f else 1f
        }

        LaunchedEffect(isPlaying) {
            if (isPlaying) exoPlayer?.play() else exoPlayer?.pause()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 14.dp, bottom = 92.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Surface(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .width(156.dp)
                    .height(238.dp)
                    .shadow(16.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFFF007F), Color(0xFF00E5FF))
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(-650f, 120f)
                            offsetY = (offsetY + dragAmount.y).coerceIn(-1350f, 180f)
                        }
                    },
                color = Color(0xFF101218)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (exoPlayer != null && item.videoUrl.isNotBlank()) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    player = exoPlayer
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF1E1B2E), Color(0xFF0D0F14))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = item.thumbnailUrl.ifBlank { "https://picsum.photos/seed/${item.id}/200/200" },
                                contentDescription = item.title,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color(0xFF00E676), CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // Top gradient & Mode Badge + Close / Expand
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val badgeColor = when (item.mode) {
                            PipMode.LIVE_STREAM -> Color(0xFFFF1744)
                            PipMode.ACTIVE_CALL -> Color(0xFF00C853)
                            PipMode.VIDEO_REEL -> Color(0xFF7C4DFF)
                        }
                        val badgeLabel = when (item.mode) {
                            PipMode.LIVE_STREAM -> "LIVE"
                            PipMode.ACTIVE_CALL -> "CALL"
                            PipMode.VIDEO_REEL -> "PiP"
                        }
                        Box(
                            modifier = Modifier
                                .background(badgeColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeLabel,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                    .clickable {
                                        if (item.mode == PipMode.ACTIVE_CALL) {
                                            onExpandCall(item)
                                        } else {
                                            onExpandVideo(item)
                                        }
                                        GlobalPipController.closePip()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Expand PiP",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                    .clickable {
                                        if (item.mode == PipMode.ACTIVE_CALL) {
                                            onEndCallFromPip(item)
                                        }
                                        GlobalPipController.closePip()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (item.mode == PipMode.ACTIVE_CALL) Icons.Default.CallEnd else Icons.Default.Close,
                                    contentDescription = "Close PiP",
                                    tint = if (item.mode == PipMode.ACTIVE_CALL) Color(0xFFFF5252) else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    // Bottom Info + Controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.subtitle,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (exoPlayer != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                                        .clickable { isPlaying = !isPlaying },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play or Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                                        .clickable { isMuted = !isMuted },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Mute or Unmute",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
