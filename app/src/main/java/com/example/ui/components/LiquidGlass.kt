package com.example.ui.components

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Advanced Liquid Glass Design System
 * Inspired by VisionOS liquid acrylic and futuristic water refraction physics.
 * Features:
 * - Multi-layer refractive transparency (Caustic water depth + specular surface highlights)
 * - Living organic water mesh background (Aurora liquid light bleeding through glass)
 * - Specular prism borders with chromatic dispersion (White frost top-left -> Deep acrylic bottom-right)
 * - Ultra-smooth 60/120 FPS hardware-accelerated rendering
 */

object LiquidGlassDefaults {
    // Translucent Liquid Colors
    val LiquidDark = Color(0x2412121A)
    val LiquidDeepDark = Color(0x380D0E15)
    val LiquidLight = Color(0x28FFFFFF)
    val LiquidFrost = Color(0x40FFFFFF)
    
    // Prismatic chromatic edge highlights
    val SpecularEdgeDark = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.45f),  // Top-left glint
            Color.White.copy(alpha = 0.12f),  // Mid refraction
            Color(0xFF8A2BE2).copy(alpha = 0.20f), // Chromatic violet dispersion
            Color.White.copy(alpha = 0.05f)   // Bottom-right shadow rim
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )
    
    val SpecularEdgeLight = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE8E8ED),
            Color(0xFFE5E5EA),
            Color(0xFFE0E0E6)
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    val TopHeaderBgDark = Brush.verticalGradient(
        colors = listOf(Color(0xEB10111A), Color(0xD910111A), Color(0xB310111A))
    )
    val TopHeaderBgLight = Brush.verticalGradient(
        colors = listOf(Color(0xF0FFFFFF), Color(0xE0FFFFFF), Color(0xB3FFFFFF))
    )
    val TopHeaderLineDark = Brush.horizontalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.05f),
            Color.White.copy(alpha = 0.28f),
            Color(0xFFFF3366).copy(alpha = 0.22f),
            Color.White.copy(alpha = 0.28f),
            Color.White.copy(alpha = 0.05f)
        )
    )
    val TopHeaderLineLight = Brush.horizontalGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0.04f),
            Color.Black.copy(alpha = 0.15f),
            Color.Black.copy(alpha = 0.04f)
        )
    )
    val DockBgDark = Brush.verticalGradient(
        colors = listOf(Color(0x9910111A), Color(0xCC10111A), Color(0xF210111A))
    )
    val DockBgLight = Brush.verticalGradient(
        colors = listOf(Color(0xB3FFFFFF), Color(0xDEFFFFFF), Color(0xF5FFFFFF))
    )
    val DockTopLineDark = Brush.horizontalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.06f),
            Color.White.copy(alpha = 0.35f),
            Color(0xFF7928CA).copy(alpha = 0.30f),
            Color.White.copy(alpha = 0.35f),
            Color.White.copy(alpha = 0.06f)
        )
    )
    val DockTopLineLight = Brush.horizontalGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0.03f),
            Color.Black.copy(alpha = 0.14f),
            Color.Black.copy(alpha = 0.03f)
        )
    )
}

/**
 * Modifier extension to apply clean surface styling to any Composable.
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    tint: Color? = null,
    borderWidth: Dp = 1.dp,
    borderBrush: Brush? = null,
    isDark: Boolean = true,
    sheenAlpha: Float = 0.20f,
    hasGlint: Boolean = true
): Modifier = this
    .clip(shape)
    .drawBehind {
        val baseTint = tint ?: if (isDark) {
            Color(0xFF15151C)
        } else {
            Color.White
        }
        drawRect(color = baseTint)
    }
    .border(
        width = borderWidth,
        brush = borderBrush ?: if (isDark) LiquidGlassDefaults.SpecularEdgeDark else LiquidGlassDefaults.SpecularEdgeLight,
        shape = shape
    )

/**
 * Clean, zero-overdraw background container.
 * Eliminates continuous infiniteTransition Canvas redraws to guarantee 60/90/120 FPS scrolling
 * and a pure, consistent white theme in Light Mode.
 */
@Composable
fun LiquidMeshBackground(
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable BoxScope.() -> Unit
) {
    val baseBgColor = if (isDark) Color(0xFF0D0D11) else Color.White

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBgColor)
    ) {
        content()
    }
}

/**
 * Liquid Glass Card Container with water reflection depth.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color? = null,
    borderWidth: Dp = 1.2.dp,
    borderBrush: Brush? = null,
    isDark: Boolean = isSystemInDarkTheme(),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "LiquidCardPress"
    )

    Box(
        modifier = modifier
            .scale(pressScale)
            .liquidGlass(
                shape = shape,
                tint = tint,
                borderWidth = borderWidth,
                borderBrush = borderBrush,
                isDark = isDark,
                sheenAlpha = if (isPressed) 0.32f else 0.20f
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = Color.White.copy(alpha = 0.25f)),
                        onClick = onClick
                    )
                } else Modifier
            ),
        content = content
    )
}

/**
 * Liquid Glass Pill Action Button (like, comment, share, or follow pills).
 */
@Composable
fun LiquidGlassPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(100.dp),
    tint: Color? = null,
    borderWidth: Dp = 1.dp,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "PillScale"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .liquidGlass(
                shape = shape,
                tint = tint ?: if (isDark) Color(0x33181824) else Color(0x35FFFFFF),
                borderWidth = borderWidth,
                isDark = isDark,
                sheenAlpha = if (isPressed) 0.40f else 0.25f
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.35f)),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}

/**
 * Liquid Glass Top Navigation Header with water refraction line.
 */
@Composable
fun LiquidGlassTopHeader(
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(brush = if (isDark) LiquidGlassDefaults.TopHeaderBgDark else LiquidGlassDefaults.TopHeaderBgLight)
                drawLine(
                    brush = if (isDark) LiquidGlassDefaults.TopHeaderLineDark else LiquidGlassDefaults.TopHeaderLineLight,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
            .statusBarsPadding(),
        content = content
    )
}

/**
 * Liquid Glass Dock (Bottom Navigation Bar)
 * Features floating acrylic pill, specular light glint, and haptic spring response.
 */
@Composable
fun LiquidGlassDock(
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(brush = if (isDark) LiquidGlassDefaults.DockBgDark else LiquidGlassDefaults.DockBgLight)
                drawLine(
                    brush = if (isDark) LiquidGlassDefaults.DockTopLineDark else LiquidGlassDefaults.DockTopLineLight,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.8.dp.toPx()
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}
