package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Composable
fun rememberShimmerBrush(): Brush {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shimmerColors = if (isDark) {
        listOf(
            Color(0xFF1E1E24).copy(alpha = 0.5f),
            Color(0xFF2C2C34).copy(alpha = 0.8f),
            Color(0xFF1E1E24).copy(alpha = 0.5f)
        )
    } else {
        listOf(
            Color(0xFFE5E7EB).copy(alpha = 0.6f),
            Color(0xFFF3F4F6).copy(alpha = 0.95f),
            Color(0xFFE5E7EB).copy(alpha = 0.6f)
        )
    }

    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnim - 350f, y = translateAnim - 350f),
        end = Offset(x = translateAnim, y = translateAnim)
    )
}

@Composable
fun InstagramFeedLoadingSpinner(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ig_spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ig_spin_rot"
    )

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation }
    ) {
        val sweepAngle = 270f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = strokeWidth.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        // Subtle track
        drawCircle(
            color = Color.Gray.copy(alpha = 0.2f),
            style = stroke
        )
        // Instagram vibrant gradient arc
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFFF58529),
                    Color(0xFFDD2A7B),
                    Color(0xFF8134AF),
                    Color(0xFF515BD4)
                )
            ),
            startAngle = 0f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = stroke
        )
    }
}

@Composable
fun HomeFeedPostSkeleton() {
    val brush = rememberShimmerBrush()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
    ) {
        // Post Header Skeleton
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(brush)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                // Username
                Box(
                    modifier = Modifier
                        .height(13.dp)
                        .width(110.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle/Time
                Box(
                    modifier = Modifier
                        .height(10.dp)
                        .width(70.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Options dots
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
        }

        // Media Skeleton Container (Instagram 4:5 vertical photo / video ratio)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .background(brush),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(48.dp)
            )
        }

        // Action Buttons Skeleton
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Like, Comment, Share, Repost pills
            repeat(4) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save icon pill
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
        }

        // Caption Lines Skeleton
        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            Box(
                modifier = Modifier
                    .height(12.dp)
                    .fillMaxWidth(0.75f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .height(12.dp)
                    .fillMaxWidth(0.45f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
fun MediaShimmerPlaceholder(
    modifier: Modifier = Modifier
) {
    val brush = rememberShimmerBrush()
    Box(
        modifier = modifier.background(brush),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun FeedPaginationLoadingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        InstagramFeedLoadingSpinner(
            size = 28.dp,
            strokeWidth = 2.5.dp
        )
    }
}
