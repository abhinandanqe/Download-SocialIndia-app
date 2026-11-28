package com.example.ui

import android.content.Context
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.model.Comment
import com.example.model.StoryUiModel
import com.example.utils.NumberFormatUtils
import com.example.utils.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

// MARK: - Models for Live Streaming UI

data class FloatingParticle(
    val id: Long,
    val emoji: String,
    val startXOffset: Float, // relative horizontal start: -30dp to 30dp
    val swayAmount: Float,   // horizontal wobble amplitude in dp
    val targetScale: Float = 1.2f,
    val durationMs: Int = 2200,
    val imageUrl: String = ""
)

data class LiveGift(
    val id: String,
    val name: String,
    val emoji: String,
    val coins: Int,
    val color: Color,
    val sticker3dUrl: String = ""
)

val LIVE_GIFTS_CATALOG = listOf(
    LiveGift("rose", "Rose", "🌹", 1, Color(0xFFFF2A6D), "https://fonts.gstatic.com/s/e/notoemoji/latest/1f339/512.gif"),
    LiveGift("heart", "Heart", "💖", 5, Color(0xFFFF007F), "https://fonts.gstatic.com/s/e/notoemoji/latest/1f496/512.gif"),
    LiveGift("applause", "Applause", "👏", 10, Color(0xFFFFD700), "https://fonts.gstatic.com/s/e/notoemoji/latest/1f44f/512.gif"),
    LiveGift("diamond", "Diamond", "💎", 50, Color(0xFF00F0FF), "https://fonts.gstatic.com/s/e/notoemoji/latest/1f48e/512.gif"),
    LiveGift("crown", "Crown", "👑", 100, Color(0xFFFFC107), "https://fonts.gstatic.com/s/e/notoemoji/latest/1f451/512.gif"),
    LiveGift("rocket", "Rocket", "🚀", 500, Color(0xFF9D00FF), "https://fonts.gstatic.com/s/e/notoemoji/latest/1f680/512.gif"),
    LiveGift("sports_car", "Sports Car", "🏎️", 1000, Color(0xFFFF1744), "https://fonts.gstatic.com/s/e/notoemoji/latest/1f3ce_fe0f/512.gif"),
    LiveGift("galaxy", "Galaxy", "🌌", 2000, Color(0xFF00E5FF), "https://fonts.gstatic.com/s/e/notoemoji/latest/1f30c/512.gif")
)

data class CoHostRequest(
    val userId: String,
    val username: String,
    val avatarUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LiveCameraFilter(val label: String, val colorMatrix: ColorMatrix?) {
    NORMAL("Normal", null),
    WARM("Warm Glow", ColorMatrix(floatArrayOf(
        1.1f, 0f, 0f, 0f, 15f,
        0f, 1.05f, 0f, 0f, 5f,
        0f, 0f, 0.9f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))),
    VIBRANT("Vibrant", ColorMatrix(floatArrayOf(
        1.2f, 0f, 0f, 0f, 0f,
        0f, 1.2f, 0f, 0f, 0f,
        0f, 0f, 1.2f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))),
    CINEMA_NOIR("Noir", ColorMatrix().apply { setToSaturation(0f) }),
    SUNSET("Sunset", ColorMatrix(floatArrayOf(
        1.25f, 0f, 0f, 0f, 20f,
        0f, 0.95f, 0f, 0f, 0f,
        0f, 0f, 1.1f, 0f, 10f,
        0f, 0f, 0f, 1f, 0f
    )))
}

// MARK: - Floating Reactions Engine

@Composable
fun LiveFloatingReactionsEngine(
    particles: List<FloatingParticle>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        particles.forEach { particle ->
            key(particle.id) {
                SingleFloatingParticleItem(particle = particle)
            }
        }
    }
}

@Composable
private fun SingleFloatingParticleItem(particle: FloatingParticle) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(particle.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = particle.durationMs,
                easing = LinearEasing
            )
        )
    }

    val currentProgress = progress.value
    // Height float path: from bottom up to -480dp
    val translateY = -currentProgress * 460f
    // Sine wave wobble
    val translateX = particle.startXOffset + (sin((currentProgress * 3.5f * Math.PI).toFloat()) * particle.swayAmount)
    // Scale starts small, pops quickly, then shrinks slightly as it rises
    val scale = when {
        currentProgress < 0.2f -> (currentProgress / 0.2f) * particle.targetScale
        currentProgress < 0.8f -> particle.targetScale
        else -> particle.targetScale * (1f - ((currentProgress - 0.8f) / 0.2f) * 0.3f)
    }
    // Alpha fades out during the final 35% of travel
    val alpha = when {
        currentProgress < 0.65f -> 1f
        else -> 1f - ((currentProgress - 0.65f) / 0.35f)
    }

    if (particle.imageUrl.isNotEmpty()) {
        AsyncImage(
            model = particle.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .padding(end = 24.dp, bottom = 80.dp)
                .size(44.dp)
                .graphicsLayer {
                    translationX = translateX
                    translationY = translateY
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha.coerceIn(0f, 1f)
                },
            contentScale = ContentScale.Fit
        )
    } else {
        Text(
            text = particle.emoji,
            fontSize = 28.sp,
            modifier = Modifier
                .padding(end = 24.dp, bottom = 80.dp)
                .graphicsLayer {
                    translationX = translateX
                    translationY = translateY
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha.coerceIn(0f, 1f)
                }
        )
    }
}

// MARK: - Live Top Creator Header

@Composable
fun LiveTopBar(
    story: StoryUiModel,
    viewersCount: Int,
    isFollowing: Boolean,
    isHost: Boolean,
    onFollowClick: () -> Unit,
    onViewersClick: () -> Unit,
    onCloseClick: () -> Unit,
    onHostControlsClick: () -> Unit,
    onShareClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "LivePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RingRotation"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Creator Profile Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(26.dp)
                )
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(26.dp))
                .padding(start = 3.dp, top = 3.dp, bottom = 3.dp, end = 12.dp)
        ) {
            // Profile Avatar with animated gradient live border
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFFFE2C55),
                                    Color(0xFFFF8A00),
                                    Color(0xFFE040FB),
                                    Color(0xFF00F0FF),
                                    Color(0xFFFE2C55)
                                )
                            )
                        )
                    }
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = story.avatarUrl.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150" },
                    contentDescription = "Creator Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = story.username.ifEmpty { "Live Host" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    VerificationBadge(
                        isVerified = story.isVerified,
                        badgeUrl = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "Live Stream",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Follow / Host badge
            if (!isHost) {
                AnimatedContent(
                    targetState = isFollowing,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    },
                    label = "FollowAnimation"
                ) { following ->
                    if (following) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                SoundManager.playClickSound()
                                onFollowClick()
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "Following",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                SoundManager.playClickSound()
                                onFollowClick()
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFE2C55)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Follow",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFD700).copy(alpha = 0.25f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "👑 Host",
                        color = Color(0xFFFFD700),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Live Badge + Viewer Count + Close / Host tools
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Animated LIVE pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFE2C55).copy(alpha = pulseAlpha))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Viewer count pill
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    SoundManager.playClickSound()
                    onViewersClick()
                },
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Viewer count",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = NumberFormatUtils.formatCount(viewersCount),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Host Controls Quick Button (if Host)
            if (isHost) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        SoundManager.playClickSound()
                        onHostControlsClick()
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Host Controls",
                        tint = Color(0xFF00F0FF),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Share Button
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    SoundManager.playClickSound()
                    onShareClick()
                },
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share Live",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Close button
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    SoundManager.playClickSound()
                    onCloseClick()
                },
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Live",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// MARK: - Live Chat List UI

@Composable
fun LiveChatFeed(
    comments: List<Comment>,
    pinnedComment: Comment?,
    onCommentClick: (Comment) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll when new comments arrive
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            listState.animateScrollToItem(comments.size - 1)
        }
    }

    Column(modifier = modifier) {
        // Pinned comment banner if active
        if (pinnedComment != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFB300).copy(alpha = 0.22f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pinned: ",
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${pinnedComment.username}: ${pinnedComment.text}",
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Live Chat Feed
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(comments, key = { it.id }) { comment ->
                LiveChatBubble(
                    comment = comment,
                    onClick = { onCommentClick(comment) }
                )
            }
        }
    }
}

@Composable
fun LiveChatBubble(
    comment: Comment,
    onClick: () -> Unit
) {
    val isSystemEvent = comment.text.startsWith("👋") ||
            comment.text.startsWith("🎁") ||
            comment.text.startsWith("⭐")

    val bubbleColor = when {
        comment.text.startsWith("🎁") -> Color(0xFFFFD700).copy(alpha = 0.28f)
        comment.text.startsWith("⭐") -> Color(0xFFFE2C55).copy(alpha = 0.28f)
        comment.text.startsWith("👋") -> Color(0xFF00F0FF).copy(alpha = 0.22f)
        else -> Color.Black.copy(alpha = 0.48f)
    }

    val bubbleBorder = when {
        comment.text.startsWith("🎁") -> Color(0xFFFFD700).copy(alpha = 0.5f)
        comment.text.startsWith("⭐") -> Color(0xFFFE2C55).copy(alpha = 0.5f)
        comment.text.startsWith("👋") -> Color(0xFF00F0FF).copy(alpha = 0.4f)
        else -> Color.White.copy(alpha = 0.1f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bubbleColor)
            .border(1.dp, bubbleBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // User Avatar
        AsyncImage(
            model = comment.avatarUrl.ifEmpty { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100" },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.username.ifEmpty { "Viewer" },
                    color = if (isSystemEvent) Color(0xFFFFD700) else Color(0xFF69C9D0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (comment.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    VerificationBadge(
                        isVerified = true,
                        badgeUrl = null,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
            if (comment.text.isNotBlank() && !comment.text.startsWith("Sent a sticker")) {
                Text(
                    text = comment.text,
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 15.sp
                )
            }
            if (!comment.gifUrl.isNullOrEmpty()) {
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
                        .build()
                }
                Spacer(modifier = Modifier.height(4.dp))
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(comment.gifUrl)
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = "Sticker",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

// MARK: - Live Bottom Action Bar

@Composable
fun LiveBottomActionBar(
    commentText: String,
    onCommentChange: (String) -> Unit,
    onSendComment: () -> Unit,
    onGiftClick: () -> Unit,
    onReactionClick: (String) -> Unit,
    onJoinLiveClick: () -> Unit,
    onHostToolsClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onStickerClick: (() -> Unit)? = null,
    isHost: Boolean,
    isCoHostActive: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showQuickEmojis by remember { mutableStateOf(false) }
    val quickEmojis = listOf("❤️", "🔥", "👏", "😂", "💯", "⭐")

    Column(modifier = modifier.fillMaxWidth()) {
        // Quick reaction bar (expandable)
        AnimatedVisibility(
            visible = showQuickEmojis,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickEmojis.forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                SoundManager.playClickSound()
                                onReactionClick(emoji)
                            }
                            .padding(2.dp)
                    )
                }
            }
        }

        // Primary Bottom Dock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Comment input pill
            OutlinedTextField(
                value = commentText,
                onValueChange = onCommentChange,
                placeholder = {
                    Text(
                        "Add a comment...",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White.copy(alpha = 0.25f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Black.copy(alpha = 0.55f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.55f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            if (commentText.isNotBlank()) {
                // Send button
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        SoundManager.playClickSound()
                        onSendComment()
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(0xFFFE2C55), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // Action icons cluster
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick Emoji Drawer toggle
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showQuickEmojis = !showQuickEmojis
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Text("✨", fontSize = 18.sp)
                    }

                    // Sticker / GIF Drawer toggle
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onStickerClick?.invoke()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SentimentSatisfiedAlt,
                            contentDescription = "Stickers & GIFs",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Gift button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            SoundManager.playClickSound()
                            onGiftClick()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = "Send Gift",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Join Live / Host Controls button
                    if (!isHost) {
                        // Join Live (Co-Host) Request button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                SoundManager.playClickSound()
                                onJoinLiveClick()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (isCoHostActive) Color(0xFF00F0FF).copy(alpha = 0.3f)
                                    else Color.Black.copy(alpha = 0.55f),
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    if (isCoHostActive) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.GroupAdd,
                                contentDescription = "Join Live",
                                tint = if (isCoHostActive) Color(0xFF00F0FF) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // Host Controls button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                SoundManager.playClickSound()
                                onHostToolsClick()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Host Settings",
                                tint = Color(0xFF00F0FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Share button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            SoundManager.playClickSound()
                            onShareClick()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Live",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Floating Reaction / Heart burst button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            SoundManager.playClickSound()
                            onReactionClick("❤️")
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFFE2C55), CircleShape)
                            .shadow(8.dp, CircleShape, spotColor = Color(0xFFFE2C55))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Like Live",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Live Gift Sheet

@Composable
fun LiveGiftBottomSheet(
    walletBalance: Double = 0.0,
    onDismiss: () -> Unit,
    onGiftSelected: (LiveGift) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181818),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Send a Gift",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Balance: ${com.example.utils.NumberFormatUtils.formatCount(walletBalance.toInt())} ",
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        com.example.ui.components.CoinIcon(size = 14.dp)
                    }
                }
            }

            val context = androidx.compose.ui.platform.LocalContext.current
            val dynamicGifts by com.example.model.RepositoryManager.giftRepository.observeActiveGifts()
                .collectAsState(initial = emptyList())

            val allGifts = remember(dynamicGifts) {
                if (dynamicGifts.isNotEmpty()) {
                    dynamicGifts.map { LiveGift(it.id, it.name, it.emoji, it.coins, it.composeColor, it.sticker3dUrl) }
                } else {
                    LIVE_GIFTS_CATALOG
                }
            }

            var selectedCategory by remember { mutableStateOf("All") }
            val categories = listOf("All", "Popular", "Luxury", "Fun", "Special")
            var selectedGift by remember { mutableStateOf<LiveGift?>(null) }

            // Category Chips
            Spacer(modifier = Modifier.height(10.dp))
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories.size) { idx ->
                    val cat = categories[idx]
                    val isSelected = selectedCategory == cat
                    Surface(
                        onClick = { selectedCategory = cat },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFFFE2C55) else Color(0xFF262626)
                    ) {
                        Text(
                            text = cat,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val filteredGifts = remember(selectedCategory, allGifts, dynamicGifts) {
                if (selectedCategory == "All") allGifts
                else {
                    val matchingIds = dynamicGifts.filter { it.category.equals(selectedCategory, ignoreCase = true) }.map { it.id }
                    if (matchingIds.isNotEmpty()) allGifts.filter { it.id in matchingIds }
                    else allGifts
                }
            }

            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 260.dp)
            ) {
                items(filteredGifts.size) { index ->
                    val gift = filteredGifts[index]
                    val isChosen = selectedGift?.id == gift.id
                    Surface(
                        onClick = {
                            selectedGift = gift
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isChosen) Color(0xFF383838) else Color(0xFF222222),
                        border = androidx.compose.foundation.BorderStroke(
                            if (isChosen) 2.dp else 1.dp,
                            if (isChosen) Color(0xFFFFD700) else gift.color.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (gift.sticker3dUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = gift.sticker3dUrl,
                                    contentDescription = gift.name,
                                    modifier = Modifier.size(36.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(
                                    text = gift.emoji,
                                    fontSize = 30.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = gift.name,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.example.ui.components.CoinIcon(size = 11.dp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${gift.coins}",
                                    color = Color(0xFFFFD700),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action / Confirmation Bar
            val gift = selectedGift
            if (gift != null) {
                val hasEnoughCoins = walletBalance >= gift.coins
                Surface(
                    color = Color(0xFF222222),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            // Animated GIF / Emoji Preview Box
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF141414)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (gift.sticker3dUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = gift.sticker3dUrl,
                                        contentDescription = gift.name,
                                        modifier = Modifier.size(40.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Text(gift.emoji, fontSize = 28.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(gift.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    com.example.ui.components.CoinIcon(size = 12.dp)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${gift.coins} Coins",
                                        color = Color(0xFFFFD700),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = if (hasEnoughCoins) "Remaining: ${(walletBalance - gift.coins).toInt()} coins"
                                           else "⚠️ Need ${(gift.coins - walletBalance).toInt()} more coins",
                                    fontSize = 11.sp,
                                    color = if (hasEnoughCoins) Color.LightGray else Color(0xFFFF6B6B)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (hasEnoughCoins) {
                                    onGiftSelected(gift)
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Insufficient balance! Need ${gift.coins} coins, you have ${walletBalance.toInt()} coins.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasEnoughCoins) Color(0xFFFE2C55) else Color.DarkGray
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (hasEnoughCoins) "Send ${gift.emoji}" else "Insufficient Coins",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Tap any gift above to preview and send",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// MARK: - Join Live (Co-Host) Bottom Sheet

@Composable
fun LiveJoinRequestSheet(
    hostUsername: String,
    isRequestPending: Boolean,
    isCoHostActive: Boolean,
    onRequestJoin: () -> Unit,
    onTestSplitScreen: () -> Unit,
    onLeaveCoHost: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161616),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF00F0FF).copy(alpha = 0.15f), CircleShape)
                    .border(2.dp, Color(0xFF00F0FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GroupAdd,
                    contentDescription = null,
                    tint = Color(0xFF00F0FF),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isCoHostActive) "Connected as Co-Host" else "Go Live with @$hostUsername",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isCoHostActive)
                    "You are currently broadcasting together in split-screen mode!"
                else
                    "Your camera and microphone will be shared. The live broadcast will switch into a dual split-screen once the host accepts.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!isCoHostActive) {
                Button(
                    onClick = onRequestJoin,
                    enabled = !isRequestPending,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = if (isRequestPending) "Request Sent • Waiting for Host..." else "Send Co-Host Request",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onTestSplitScreen,
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F0FF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Instant Preview: Try Split-Screen Live",
                        color = Color(0xFF00F0FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            } else {
                Button(
                    onClick = onLeaveCoHost,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Leave Co-Host Stream",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// MARK: - Host Controls Bottom Sheet

@Composable
fun LiveHostControlsSheet(
    isCameraMuted: Boolean,
    isMicMuted: Boolean,
    currentFilter: LiveCameraFilter,
    commentsEnabled: Boolean,
    pendingRequests: List<CoHostRequest>,
    onFlipCamera: () -> Unit,
    onToggleCameraMute: () -> Unit,
    onToggleMicMute: () -> Unit,
    onSelectFilter: (LiveCameraFilter) -> Unit,
    onToggleComments: () -> Unit,
    onAcceptCoHost: (CoHostRequest) -> Unit,
    onDeclineCoHost: (CoHostRequest) -> Unit,
    onEndLiveClick: () -> Unit,
    onAnalyticsClick: () -> Unit = {},
    onModeratorToolsClick: () -> Unit = {},
    onInviteFriendsClick: () -> Unit = {},
    onShareLiveClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161616),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Host Studio Controls",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = "Manage your stream, camera, audio, and audience requests",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Tool Bar: Flip, Camera, Mic, Comments
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HostToolItem(
                    icon = Icons.Default.Cameraswitch,
                    label = "Flip Camera",
                    isActive = false,
                    onClick = onFlipCamera
                )
                HostToolItem(
                    icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMicMuted) "Mic Off" else "Mic On",
                    isActive = !isMicMuted,
                    activeColor = Color(0xFF00E676),
                    inactiveColor = Color(0xFFFF5252),
                    onClick = onToggleMicMute
                )
                HostToolItem(
                    icon = if (isCameraMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                    label = if (isCameraMuted) "Video Off" else "Video On",
                    isActive = !isCameraMuted,
                    activeColor = Color(0xFF00E676),
                    inactiveColor = Color(0xFFFF5252),
                    onClick = onToggleCameraMute
                )
                HostToolItem(
                    icon = Icons.Default.ChatBubbleOutline,
                    label = if (commentsEnabled) "Comments On" else "Muted",
                    isActive = commentsEnabled,
                    onClick = onToggleComments
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Studio & Social Management Row: Analytics, Moderator, Invite, Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HostToolItem(
                    icon = Icons.Default.Analytics,
                    label = "Analytics",
                    isActive = true,
                    activeColor = Color(0xFFFFD700),
                    onClick = onAnalyticsClick
                )
                HostToolItem(
                    icon = Icons.Default.Security,
                    label = "Moderator",
                    isActive = true,
                    activeColor = Color(0xFF00F0FF),
                    onClick = onModeratorToolsClick
                )
                HostToolItem(
                    icon = Icons.Default.PersonAdd,
                    label = "Invite",
                    isActive = true,
                    activeColor = Color(0xFFE040FB),
                    onClick = onInviteFriendsClick
                )
                HostToolItem(
                    icon = Icons.Default.Share,
                    label = "Share",
                    isActive = true,
                    activeColor = Color(0xFF448AFF),
                    onClick = onShareLiveClick
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Camera Beauty / Video Filters
            Text(
                text = "Live Video Filters",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiveCameraFilter.values().forEach { filter ->
                    val isSelected = filter == currentFilter
                    Surface(
                        onClick = { onSelectFilter(filter) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFFFE2C55) else Color(0xFF262626),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFFFE2C55) else Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Text(
                            text = filter.label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Co-Host Requests section
            if (pendingRequests.isNotEmpty()) {
                Text(
                    text = "Co-Host Join Requests (${pendingRequests.size})",
                    color = Color(0xFF00F0FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                pendingRequests.forEach { req ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF222222), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = req.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = req.username,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onAcceptCoHost(req) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Accept", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                            OutlinedButton(
                                onClick = { onDeclineCoHost(req) },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Decline", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // End Live Broadcast button
            Button(
                onClick = onEndLiveClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("End Live Stream", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HostToolItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = Color(0xFF00F0FF),
    inactiveColor: Color = Color.White.copy(alpha = 0.5f),
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    if (isActive) activeColor.copy(alpha = 0.2f) else Color(0xFF262626),
                    CircleShape
                )
                .border(
                    1.dp,
                    if (isActive) activeColor else Color.White.copy(alpha = 0.15f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else inactiveColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// MARK: - Viewers Attendees List Sheet

@Composable
fun LiveViewersBottomSheet(
    viewersCount: Int,
    comments: List<Comment>,
    onDismiss: () -> Unit
) {
    // Collect distinct viewers from comments and active session
    val distinctViewers = remember(comments) {
        comments.map { it.username to it.avatarUrl }.distinctBy { it.first }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161616),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Audience (${NumberFormatUtils.formatCount(viewersCount)})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Live Now",
                    color = Color(0xFFFE2C55),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (distinctViewers.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No audience members yet", color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                } else {
                    items(distinctViewers.size) { index ->
                        val (name, avatar) = distinctViewers[index]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = avatar.ifEmpty { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100" },
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = name,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Viewer",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Text(
                                text = "👋 Watching",
                                color = Color(0xFF00F0FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

// MARK: - Live Stream Summary Ended Recap

@Composable
fun LiveStreamSummaryDialog(
    durationSeconds: Long,
    totalViewers: Int,
    totalLikes: Int,
    giftsCount: Int,
    onDismiss: () -> Unit
) {
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val formattedDuration = String.format("%02d:%02d", minutes, seconds)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Live Video Ended",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Here's your stream summary",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryStatRow(label = "Stream Duration", value = formattedDuration, icon = Icons.Default.PlayArrow)
                SummaryStatRow(label = "Peak Viewers", value = NumberFormatUtils.formatCount(totalViewers), icon = Icons.Default.Visibility)
                SummaryStatRow(label = "Reactions & Likes", value = NumberFormatUtils.formatCount(totalLikes), icon = Icons.Default.Favorite)
                SummaryStatRow(label = "Gifts Received", value = "$giftsCount Gifts 🎁", icon = Icons.Default.CardGiftcard)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun SummaryStatRow(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF282828), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
