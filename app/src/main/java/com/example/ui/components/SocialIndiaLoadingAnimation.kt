package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Official Social India Animated Logo Badge.
 * Uses the EXACT same application icon (ic_social_india_logo) that appears on the device launcher,
 * enhanced with smooth ambient radiant glow and breathing pulse animations.
 */
@Composable
fun SocialIndiaAnimatedLogo(
    modifier: Modifier = Modifier,
    size: Dp = 104.dp,
    showGlow: Boolean = true,
    isAnimated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "app_icon_anim")

    // Ambient glow pulse
    val glowScale by if (isAnimated) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow_scale"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    val glowAlpha by if (isAnimated) {
        infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow_alpha"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.3f) }
    }

    // Subtle gentle breathing scale for the icon itself
    val iconBreathing by if (isAnimated) {
        infiniteTransition.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.025f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "icon_breathe"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // 1. Ambient Brand Glow Aura matching application icon gradient
        if (showGlow) {
            Box(
                modifier = Modifier
                    .size(size * 1.22f)
                    .graphicsLayer {
                        scaleX = glowScale
                        scaleY = glowScale
                        alpha = glowAlpha
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF1E56).copy(alpha = 0.55f),
                                Color(0xFF8E24AA).copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // 2. The Official Application Icon
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = iconBreathing
                    scaleY = iconBreathing
                }
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(size * 0.22f),
                    spotColor = Color(0xFFFF1E56),
                    ambientColor = Color(0xFF8E24AA)
                )
                .clip(RoundedCornerShape(size * 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_social_india_logo),
                contentDescription = "Social India Application Icon",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Reusable animated Social India Loading Indicator for screens and dialogs.
 */
@Composable
fun SocialIndiaLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    showLabel: Boolean = false,
    label: String = "Loading..."
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SocialIndiaAnimatedLogo(
            size = size,
            showGlow = size >= 60.dp,
            isAnimated = true
        )

        if (showLabel) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                color = Color(0xFFB0B3BC),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Three-dot pulse animation in Social India signature brand colors (Crimson, Violet, Amber).
 */
@Composable
fun SocialIndiaBrandDots(
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    spacing: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots_anim")

    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dot 1: Brand Crimson Red
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    scaleX = dot1Scale
                    scaleY = dot1Scale
                }
                .clip(CircleShape)
                .background(Color(0xFFFF1E56))
        )
        // Dot 2: Brand Purple / Violet
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    scaleX = dot2Scale
                    scaleY = dot2Scale
                }
                .clip(CircleShape)
                .background(Color(0xFF8E24AA))
        )
        // Dot 3: Brand Amber / Gold
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    scaleX = dot3Scale
                    scaleY = dot3Scale
                }
                .clip(CircleShape)
                .background(Color(0xFFFFB300))
        )
    }
}
