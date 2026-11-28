package com.example.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.LivePresenceManager
import com.example.utils.NumberFormatUtils

// Premium vibrant live streaming colors
val LivePrimaryRed = Color(0xFFFE2C55)
val LiveSecondaryOrange = Color(0xFFFF8A00)
val LiveAccentMagenta = Color(0xFFE040FB)
val LiveCyanAccent = Color(0xFF00F0FF)

/**
 * Animated sweep gradient for the rotating live ring
 */
@Composable
fun rememberLiveRingBrush(): Brush {
    val infiniteTransition = rememberInfiniteTransition(label = "live_ring_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "live_ring_angle"
    )

    return remember(rotation) {
        Brush.sweepGradient(
            listOf(
                LivePrimaryRed,
                LiveSecondaryOrange,
                LiveAccentMagenta,
                LiveCyanAccent,
                LivePrimaryRed
            )
        )
    }
}

/**
 * Animated Live Pill Badge with pulsing white/red dot indicator
 */
@Composable
fun LivePillBadge(
    modifier: Modifier = Modifier,
    viewersCount: Int? = null,
    isLarge: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_dot_alpha"
    )

    val fontSize = if (isLarge) 10.sp else 8.5.sp
    val horizontalPadding = if (isLarge) 7.dp else 5.dp
    val verticalPadding = if (isLarge) 2.5.dp else 1.5.dp

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                brush = Brush.horizontalGradient(
                    listOf(LivePrimaryRed, Color(0xFFFF1744))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Pulsing indicator dot
        Box(
            modifier = Modifier
                .size(if (isLarge) 6.dp else 4.5.dp)
                .scale(dotAlpha)
                .background(Color.White, CircleShape)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = if (viewersCount != null && viewersCount > 0) {
                "LIVE · ${NumberFormatUtils.formatCount(viewersCount)}"
            } else {
                "LIVE"
            },
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.6.sp,
            lineHeight = fontSize
        )
    }
}

/**
 * Standard Live Avatar Component
 * Handles real-time live presence tracking, animated gradient rings,
 * pulsing badges, and direct Live tapping.
 */
@Composable
fun LiveAvatar(
    avatarUrl: String,
    userId: String? = null,
    isLiveOverride: Boolean? = null,
    size: Dp = 48.dp,
    showLiveBadge: Boolean = true,
    showRing: Boolean = true,
    hasStory: Boolean = false,
    viewersCount: Int? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLiveClick: (() -> Unit)? = null
) {
    // Observe global live presence in real time
    val activeLives by LivePresenceManager.activeLives.collectAsStateWithLifecycle()
    val isActuallyLive = remember(isLiveOverride, userId, activeLives) {
        isLiveOverride ?: (userId != null && activeLives.containsKey(userId))
    }

    val liveSession = remember(userId, activeLives) {
        if (userId != null) activeLives[userId] else null
    }
    val effectiveViewerCount = viewersCount ?: liveSession?.viewersCount

    // Pulsing breathing scale when user is live
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_breath")
    val pulseScale by if (isActuallyLive) {
        infiniteTransition.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "live_avatar_pulse"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    val liveRingBrush = rememberLiveRingBrush()
    val storyRingBrush = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFF833AB4),
                Color(0xFFFD1D1D),
                Color(0xFFFCAF45),
                Color(0xFF833AB4)
            )
        )
    }
    val ringStroke = if (size >= 72.dp) 3.5.dp else if (size >= 44.dp) 2.5.dp else 2.dp
    val innerPadding = if ((isActuallyLive || hasStory) && showRing) ringStroke + 1.dp else 0.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .scale(pulseScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isActuallyLive && onLiveClick != null) {
                    onLiveClick()
                } else {
                    onClick()
                }
            }
    ) {
        // Outer Animated Ring (Visible ONLY when live) or Story Ring
        if ((isActuallyLive || hasStory) && showRing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(ringStroke, if (isActuallyLive) liveRingBrush else storyRingBrush, CircleShape)
            )
        }

        // Circular Avatar Content
        AsyncImage(
            model = avatarUrl.ifEmpty {
                "https://api.dicebear.com/7.x/avataaars/png?seed=${userId ?: "user"}"
            },
            contentDescription = "User Avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clip(CircleShape)
                .background(Color.DarkGray)
        )

        // Live Badge Pill (Overlapping avatar bottom)
        if (isActuallyLive && showLiveBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = if (size >= 64.dp) 4.dp else 3.dp)
            ) {
                LivePillBadge(
                    viewersCount = effectiveViewerCount,
                    isLarge = size >= 64.dp
                )
            }
        }
    }
}
