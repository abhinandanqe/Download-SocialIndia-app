package com.example.ui.motion

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.bounceClick
import kotlin.math.PI
import kotlin.math.sin

/**
 * Central Instagram-style motion configuration for Social India.
 * Keeps all durations (100–300ms), easings, springs, and reduced-motion rules consistent app-wide.
 * Every helper animates transform / opacity only (GPU-accelerated, zero layout shift).
 */
object SocialMotion {
    val EasingStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EasingEmphasis: Easing = FastOutSlowInEasing

    const val DurationTabFade = 110
    const val DurationQuick = 120
    const val DurationFast = 180
    const val DurationScreen = 240
    const val DurationToast = 250
    const val DurationMedium = 280
    const val DurationRepostToastHoldMs = 3000L

    const val PressScale = 0.92f
    const val RepostBouncePeak = 1.3f

    fun <T> springPop(reducedMotion: Boolean = false) = if (reducedMotion) {
        tween<T>(durationMillis = 1)
    } else {
        spring<T>(
            dampingRatio = 0.48f,
            stiffness = Spring.StiffnessMedium
        )
    }

    fun <T> springSnappy(reducedMotion: Boolean = false) = if (reducedMotion) {
        tween<T>(durationMillis = 1)
    } else {
        spring<T>(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMediumLow
        )
    }

    fun <T> springSheet(reducedMotion: Boolean = false) = if (reducedMotion) {
        tween<T>(durationMillis = 1)
    } else {
        spring<T>(
            dampingRatio = 0.84f,
            stiffness = Spring.StiffnessMediumLow
        )
    }

    fun <T> standardTween(
        durationMillis: Int = DurationToast,
        reducedMotion: Boolean = false
    ) = tween<T>(
        durationMillis = if (reducedMotion) 1 else durationMillis,
        easing = EasingStandard
    )

    fun floatSpec(
        durationMillis: Int = DurationToast,
        reducedMotion: Boolean = false
    ) = tween<Float>(
        durationMillis = if (reducedMotion) 1 else durationMillis,
        easing = EasingStandard
    )

    fun colorSpec(
        durationMillis: Int = DurationFast,
        reducedMotion: Boolean = false
    ) = tween<Color>(
        durationMillis = if (reducedMotion) 1 else durationMillis,
        easing = EasingStandard
    )

    fun screenSlideSpec(reducedMotion: Boolean = false) = tween<IntOffset>(
        durationMillis = if (reducedMotion) 1 else DurationScreen,
        easing = EasingStandard
    )
}

/**
 * Respects Android's system-wide reduced motion preference (Animator duration scale == 0).
 */
@Composable
fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * Instagram-style 3-bar mini equalizer rendered directly on Canvas (60fps GPU-friendly).
 * Animates only while [isPlaying] is true and reduced motion is disabled; rests cleanly when paused.
 */
@Composable
fun AnimatedEqualizerBars(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val shouldAnimate = isPlaying && !reducedMotion

    if (shouldAnimate) {
        val infiniteTransition = rememberInfiniteTransition(label = "ig_eq")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ig_eq_phase"
        )
        Canvas(modifier = modifier.size(size)) {
            val barCount = 3
            val gap = this.size.width * 0.14f
            val barWidth = (this.size.width - gap * (barCount - 1)) / barCount
            val maxBarHeight = this.size.height
            val phaseOffsets = floatArrayOf(0f, 1.85f, 3.6f)
            val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

            for (i in 0 until barCount) {
                val wave = (sin(phase + phaseOffsets[i]) + 1f) * 0.5f
                val barHeight = maxBarHeight * (0.28f + 0.72f * wave)
                val x = i * (barWidth + gap)
                val y = maxBarHeight - barHeight
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner
                )
            }
        }
    } else {
        Canvas(modifier = modifier.size(size)) {
            val barCount = 3
            val gap = this.size.width * 0.14f
            val barWidth = (this.size.width - gap * (barCount - 1)) / barCount
            val maxBarHeight = this.size.height
            val restingHeights = floatArrayOf(0.45f, 0.80f, 0.55f)
            val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

            for (i in 0 until barCount) {
                val barHeight = maxBarHeight * restingHeights[i]
                val x = i * (barWidth + gap)
                val y = maxBarHeight - barHeight
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner
                )
            }
        }
    }
}

/**
 * Instagram-style Audio Row:
 * Music-note icon + animated equalizer bars + seamless looping marquee (right-to-left)
 * ONLY when the audio name overflows its container and the post/reel is visible & playing.
 * Remains cleanly static when the text fits or when paused/off-screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InstagramAudioRow(
    audioTitle: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White,
    textColor: Color = Color.White,
    iconSize: Dp = 13.dp,
    textStyle: TextStyle = TextStyle(
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium
    )
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val marqueeIterations = if (isPlaying && !reducedMotion) Int.MAX_VALUE else 0
    val displayTitle = audioTitle.ifBlank { "Original Audio" }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .bounceClick(enableHaptics = false)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag("instagram_audio_row")
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = "Audio",
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
        Spacer(modifier = Modifier.width(4.dp))
        AnimatedEqualizerBars(
            isPlaying = isPlaying,
            tint = iconTint,
            size = (iconSize.value - 1f).coerceAtLeast(10f).dp
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = displayTitle,
            style = textStyle.copy(color = textColor),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .weight(1f, fill = false)
                .basicMarquee(
                    iterations = marqueeIterations,
                    repeatDelayMillis = 1200,
                    initialDelayMillis = 600,
                    spacing = MarqueeSpacing(28.dp),
                    velocity = 34.dp
                )
        )
    }
}

/**
 * GPU-only spring pop-in notification / count badge.
 */
@Composable
fun AnimatedBadgePop(
    count: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFFFF3040),
    contentColor: Color = Color.White
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val popScale = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (count > 0 && !reducedMotion) {
            popScale.snapTo(0.7f)
            popScale.animateTo(1f, SocialMotion.springPop(reducedMotion = false))
        }
    }

    AnimatedVisibility(
        visible = count > 0,
        enter = scaleIn(
            initialScale = 0.4f,
            animationSpec = SocialMotion.springPop(reducedMotion)
        ) + fadeIn(animationSpec = SocialMotion.floatSpec(SocialMotion.DurationQuick, reducedMotion)),
        exit = scaleOut(
            targetScale = 0.4f,
            animationSpec = SocialMotion.floatSpec(SocialMotion.DurationQuick, reducedMotion)
        ) + fadeOut(animationSpec = SocialMotion.floatSpec(SocialMotion.DurationQuick, reducedMotion)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = popScale.value
                    scaleY = popScale.value
                }
                .clip(CircleShape)
                .background(containerColor)
                .padding(horizontal = 4.5.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                color = contentColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Lightweight GPU-only fade-in for feed list items (zero layout shift).
 */
@Composable
fun FeedItemReveal(
    key: Any,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val alphaAnim = remember(key) { Animatable(if (reducedMotion) 1f else 0.01f) }
    LaunchedEffect(key, reducedMotion) {
        if (reducedMotion) {
            alphaAnim.snapTo(1f)
        } else if (alphaAnim.value < 1f) {
            alphaAnim.animateTo(1f, SocialMotion.floatSpec(SocialMotion.DurationFast))
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = alphaAnim.value
        }
    ) {
        content()
    }
}
