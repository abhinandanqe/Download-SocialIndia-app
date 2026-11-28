package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.model.MonetizationSettings
import com.example.repository.MonetizationRepository
import kotlin.math.cos
import kotlin.math.sin

/**
 * Universal dynamic Coin Icon that respects Admin-configured custom GIF or Image.
 * Automatically falls back to a guaranteed vector-rendered 3D Gold Coin graphic so that
 * NO TOFU BOX / UNRENDERED SQUARE can ever appear on any Android device or screen.
 */
@Composable
fun CoinIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    customUrl: String? = null
) {
    val settings by MonetizationRepository.observeMonetizationSettings()
        .collectAsState(initial = MonetizationSettings())

    val effectiveUrl = customUrl?.ifBlank { null } ?: settings.coinIconUrl.ifBlank { null }

    if (!effectiveUrl.isNullOrEmpty()) {
        val context = LocalContext.current
        val request = remember(effectiveUrl) {
            ImageRequest.Builder(context)
                .data(effectiveUrl)
                .crossfade(true)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build()
        }
        val painter = rememberAsyncImagePainter(model = request)
        val painterState = painter.state

        if (painterState is AsyncImagePainter.State.Success) {
            Image(
                painter = painter,
                contentDescription = "Coins",
                modifier = modifier.size(size),
                contentScale = ContentScale.Fit
            )
        } else {
            // While loading or if error, render the vector gold coin so no empty box or tofu glitch occurs
            DefaultGoldCoinGraphic(
                modifier = modifier,
                size = size
            )
        }
    } else {
        DefaultGoldCoinGraphic(
            modifier = modifier,
            size = size
        )
    }
}

/**
 * High-definition, hardware-accelerated vector gold coin.
 * Guaranteed to render identically on 100% of Android devices with zero tofu boxes or missing font glitches.
 */
@Composable
fun DefaultGoldCoinGraphic(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val radius = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // Outer drop ring / edge shadow
            drawCircle(
                color = Color(0xFFB8860B),
                radius = radius,
                center = center
            )

            // Outer gold rim gradient
            val rimBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFDF00),
                    Color(0xFFFFB300),
                    Color(0xFFD4AF37)
                ),
                center = center,
                radius = radius
            )
            drawCircle(
                brush = rimBrush,
                radius = radius * 0.94f,
                center = center
            )

            // Outer groove line
            drawCircle(
                color = Color(0xFFCC8400),
                radius = radius * 0.86f,
                center = center,
                style = Stroke(width = radius * 0.08f)
            )

            // Inner coin face with highlight gradient
            val faceBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFEE58),
                    Color(0xFFFFC107),
                    Color(0xFFFF9800)
                ),
                start = Offset(center.x - radius, center.y - radius),
                end = Offset(center.x + radius, center.y + radius)
            )
            drawCircle(
                brush = faceBrush,
                radius = radius * 0.82f,
                center = center
            )

            // Subtle top highlight reflection
            val highlightBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.5f),
                    Color.Transparent
                ),
                start = Offset(center.x, center.y - radius * 0.8f),
                end = Offset(center.x, center.y + radius * 0.2f)
            )
            drawCircle(
                brush = highlightBrush,
                radius = radius * 0.74f,
                center = center
            )

            // Embossed 5-point star in center
            val starRadiusOuter = radius * 0.45f
            val starRadiusInner = radius * 0.20f
            val starPath = Path()
            val points = 5
            val angleStep = Math.PI / points
            var currentAngle = -Math.PI / 2.0

            for (i in 0 until (points * 2)) {
                val r = if (i % 2 == 0) starRadiusOuter else starRadiusInner
                val x = (center.x + r * cos(currentAngle)).toFloat()
                val y = (center.y + r * sin(currentAngle)).toFloat()
                if (i == 0) {
                    starPath.moveTo(x, y)
                } else {
                    starPath.lineTo(x, y)
                }
                currentAngle += angleStep
            }
            starPath.close()

            // Draw center star shadow then bright face
            drawPath(
                path = starPath,
                color = Color(0xFFB26A00).copy(alpha = 0.6f)
            )
            drawPath(
                path = starPath,
                color = Color(0xFFFFF9C4),
                style = Stroke(width = radius * 0.08f)
            )
        }
    }
}
