package com.example.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Curated flagship multi-color palettes for Social India's dynamic like animations.
 * Provides rich complementary gradients (upper-half/lower-half, diagonal, multi-color).
 */
object LikeColors {
    val CrimsonHeart = Color(0xFFFF1744)
    val InstagramPink = Color(0xFFFE2C55)
    val RubyRed = Color(0xFFE1306C)
    val NeonHeart = Color(0xFFFF2D55)

    val SparkleColors = listOf(
        Color(0xFFFF1744),
        Color(0xFFFF4081),
        Color(0xFFFFD700),
        Color(0xFFFF6D00),
        Color(0xFFE040FB),
        Color(0xFF00E5FF),
        Color(0xFF00E676),
        Color(0xFF7C4DFF)
    )

    // Curated 16 complementary multi-color palettes
    val Palettes = listOf(
        listOf(Color(0xFFFF1744), Color(0xFFFF4081)), // 1. Red + Pink
        listOf(Color(0xFFFF2D55), Color(0xFF9C27B0)), // 2. Pink + Purple
        listOf(Color(0xFF7C4DFF), Color(0xFF2979FF)), // 3. Purple + Blue
        listOf(Color(0xFF0091EA), Color(0xFF00E5FF)), // 4. Blue + Cyan
        listOf(Color(0xFF00E5FF), Color(0xFF00E676)), // 5. Cyan + Green
        listOf(Color(0xFF00E676), Color(0xFFFFEA00)), // 6. Green + Yellow
        listOf(Color(0xFFFFD600), Color(0xFFFF6D00)), // 7. Yellow + Orange
        listOf(Color(0xFFFF6D00), Color(0xFFFF1744)), // 8. Orange + Red
        listOf(Color(0xFF2979FF), Color(0xFF651FFF)), // 9. Blue + Violet
        listOf(Color(0xFFFF4081), Color(0xFFFF9100)), // 10. Pink + Orange
        listOf(Color(0xFFD500F9), Color(0xFF00E5FF)), // 11. Purple + Cyan
        listOf(Color(0xFF1DE9B6), Color(0xFF2979FF)), // 12. Green + Blue
        listOf(Color(0xFFFF5252), Color(0xFFFF4081), Color(0xFFFFD740)), // 13. Sunset Glow
        listOf(Color(0xFFFF007F), Color(0xFF7928CA), Color(0xFF00DFD8)), // 14. Neon Fusion
        listOf(Color(0xFFFF1744), Color(0xFFFF80AB), Color(0xFFFFD54F)), // 15. Golden Crimson
        listOf(Color(0xFFE040FB), Color(0xFFFF1744), Color(0xFF00E5FF))  // 16. Cyberpunk Velvet
    )

    fun getPalette(index: Int): List<Color> {
        val safeIdx = Math.floorMod(index, Palettes.size)
        return Palettes[safeIdx]
    }
}

/**
 * Creates a clean, scalable vector Bezier heart path for Canvas drawing.
 */
fun createHeartPath(w: Float, h: Float): Path {
    val path = Path()
    val top = h * 0.12f
    val bottom = h * 0.90f
    val midX = w / 2f

    path.moveTo(midX, bottom)
    // Left curve (bottom to left lobe top)
    path.cubicTo(
        w * 0.04f, h * 0.62f,
        w * 0.02f, h * 0.28f,
        w * 0.26f, top
    )
    // Left lobe dip to center cleft
    path.cubicTo(
        w * 0.38f, top * 0.8f,
        w * 0.48f, h * 0.22f,
        midX, h * 0.32f
    )
    // Right lobe from center cleft to top right
    path.cubicTo(
        w * 0.52f, h * 0.22f,
        w * 0.62f, top * 0.8f,
        w * 0.74f, top
    )
    // Right curve back to bottom
    path.cubicTo(
        w * 0.98f, h * 0.28f,
        w * 0.96f, h * 0.62f,
        midX, bottom
    )
    path.close()
    return path
}

/**
 * Draws a mini heart particle for explosive burst effects.
 */
fun DrawScope.drawMiniHeart(
    center: Offset,
    sizePx: Float,
    color: Color,
    rotationDeg: Float = 0f
) {
    val half = sizePx / 2f
    val p = Path()
    p.moveTo(center.x, center.y + half * 0.8f)
    p.cubicTo(
        center.x - half, center.y + half * 0.2f,
        center.x - half, center.y - half * 0.6f,
        center.x - half * 0.3f, center.y - half * 0.8f
    )
    p.cubicTo(
        center.x - half * 0.1f, center.y - half * 0.9f,
        center.x, center.y - half * 0.4f,
        center.x, center.y - half * 0.2f
    )
    p.cubicTo(
        center.x, center.y - half * 0.4f,
        center.x + half * 0.1f, center.y - half * 0.9f,
        center.x + half * 0.3f, center.y - half * 0.8f
    )
    p.cubicTo(
        center.x + half, center.y - half * 0.6f,
        center.x + half, center.y + half * 0.2f,
        center.x, center.y + half * 0.8f
    )
    p.close()
    drawPath(p, color = color, style = Fill)
}

/**
 * State holding a double-tap burst instance with individual multi-color palette.
 */
data class DoubleTapBurstState(
    val id: Long = System.currentTimeMillis() + Random.nextLong(1000),
    val offset: Offset,
    val rotation: Float = Random.nextFloat() * 24f - 12f,
    val palette: List<Color> = LikeColors.getPalette(Random.nextInt(LikeColors.Palettes.size)),
    val scaleMultiplier: Float = 1.0f + Random.nextFloat() * 0.25f,
    val glowColor: Color = LikeColors.SparkleColors[Random.nextInt(LikeColors.SparkleColors.size)]
)

/**
 * Controller to trigger double-tap heart animations imperatively.
 */
class DoubleTapLikeController {
    var activeBursts = mutableStateListOf<DoubleTapBurstState>()
        private set

    fun triggerBurst(offset: Offset) {
        if (activeBursts.size > 5) {
            activeBursts.removeAt(0) // Prevent memory accumulation on rapid taps
        }
        activeBursts.add(DoubleTapBurstState(offset = offset))
    }

    fun removeBurst(id: Long) {
        activeBursts.removeAll { it.id == id }
    }
}

@Composable
fun rememberDoubleTapLikeController(): DoubleTapLikeController {
    return remember { DoubleTapLikeController() }
}

/**
 * Container that intercepts double-tap to like at exact tap coordinates,
 * with optional single tap forwarder. Renders multi-color bursting heart and particle shockwave.
 */
@Composable
fun DoubleTapLikeBox(
    onDoubleTapLike: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    onSingleTap: (() -> Unit)? = null,
    onLongPress: ((Offset) -> Unit)? = null,
    controller: DoubleTapLikeController = rememberDoubleTapLikeController(),
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onDoubleTap = { offset ->
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        } catch (_: Exception) {}

                        com.example.utils.SoundManager.playClickSound()
                        controller.triggerBurst(offset)
                        onDoubleTapLike(offset)
                    },
                    onTap = {
                        onSingleTap?.invoke()
                    },
                    onLongPress = { offset ->
                        onLongPress?.invoke(offset)
                    }
                )
            }
    ) {
        content()

        // Double-Tap Floating Hearts Overlay
        DoubleTapHeartsOverlay(
            activeBursts = controller.activeBursts,
            onBurstFinished = { burstId ->
                controller.removeBurst(burstId)
            }
        )
    }
}

/**
 * Floating Double-Tap Heart Overlay:
 * Renders multiple rapid double-tap hearts with spring physics,
 * upward elevation drift, multi-color gradient heart, radial particle sparklers,
 * mini floating particle hearts, and expanding glow shockwave.
 */
@Composable
fun DoubleTapHeartsOverlay(
    activeBursts: List<DoubleTapBurstState>,
    onBurstFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        activeBursts.forEach { burst ->
            key(burst.id) {
                SingleDoubleTapHeartItem(
                    burst = burst,
                    onFinished = { onBurstFinished(burst.id) }
                )
            }
        }
    }
}

@Composable
private fun BoxScope.SingleDoubleTapHeartItem(
    burst: DoubleTapBurstState,
    onFinished: () -> Unit
) {
    val density = LocalDensity.current

    val scaleAnim = remember { Animatable(0f) }
    val translationYAnim = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(1f) }
    val particleProgress = remember { Animatable(0f) }
    val gradientShift = remember { Animatable(0f) }

    LaunchedEffect(burst.id) {
        coroutineScope {
            // Stage 1: Explosive bouncy pop
            launch {
                scaleAnim.animateTo(
                    targetValue = 1.38f * burst.scaleMultiplier,
                    animationSpec = spring(
                        dampingRatio = 0.45f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                scaleAnim.animateTo(
                    targetValue = 1.15f * burst.scaleMultiplier,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }

            // Stage 2: Gradient shimmer inside double-tap heart
            launch {
                gradientShift.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(700, easing = LinearEasing)
                )
            }

            // Stage 3: Particle & shockwave expansion
            launch {
                particleProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(580, easing = FastOutSlowInEasing)
                )
            }

            // Stage 4: Smooth float upward and dissolve
            launch {
                delay(380)
                launch {
                    translationYAnim.animateTo(
                        targetValue = -85f,
                        animationSpec = tween(420, easing = LinearOutSlowInEasing)
                    )
                }
                launch {
                    alphaAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(420, easing = FastOutLinearInEasing)
                    )
                }
            }
        }
        onFinished()
    }

    val heartSizeDp = 110.dp
    val heartSizePx = with(density) { heartSizeDp.toPx() }

    val posX = (burst.offset.x - heartSizePx / 2f).coerceAtLeast(0f)
    val posY = (burst.offset.y - heartSizePx / 2f).coerceAtLeast(0f)

    Box(
        modifier = Modifier
            .offset { IntOffset(posX.toInt(), posY.toInt()) }
            .size(heartSizeDp)
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                translationY = translationYAnim.value * density.density
                rotationZ = burst.rotation
                rotationY = (burst.rotation * 1.8f).coerceIn(-24f, 24f)
                rotationX = ((scaleAnim.value - 1f) * -10f).coerceIn(-18f, 18f)
                cameraDistance = 18 * density.density
                alpha = alphaAnim.value
            },
        contentAlignment = Alignment.Center
    ) {
        // Particle Shockwave, Star Sparkles & Mini Floating Hearts
        if (particleProgress.value > 0f && particleProgress.value < 1f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val p = particleProgress.value
                val particleAlpha = (1f - p).coerceIn(0f, 1f)

                // Expanding glow halo ring
                val shockwaveRadius = 32.dp.toPx() + (60.dp.toPx() * p)
                val shockwaveStroke = (4.dp.toPx() * (1f - p)).coerceAtLeast(0.5f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(burst.palette.first().copy(alpha = particleAlpha * 0.8f), Color.Transparent),
                        center = center,
                        radius = shockwaveRadius + 12.dp.toPx()
                    ),
                    radius = shockwaveRadius + 8.dp.toPx()
                )
                drawCircle(
                    color = burst.palette.first().copy(alpha = particleAlpha * 0.7f),
                    radius = shockwaveRadius,
                    center = center,
                    style = Stroke(width = shockwaveStroke)
                )

                // 14 radial particles (alternating sparkling dots and mini hearts)
                val numParticles = 14
                for (i in 0 until numParticles) {
                    val angle = (i * (360f / numParticles) + burst.rotation * 1.2f) * (PI / 180f)
                    val distance = 40.dp.toPx() + (58.dp.toPx() * p)
                    val px = center.x + distance * cos(angle).toFloat()
                    val py = center.y + distance * sin(angle).toFloat()
                    val color = LikeColors.SparkleColors[i % LikeColors.SparkleColors.size]

                    if (i % 3 == 0) {
                        // Mini particle heart
                        drawMiniHeart(
                            center = Offset(px, py),
                            sizePx = (10.dp.toPx() * (1f - p * 0.5f)).coerceAtLeast(2f),
                            color = color.copy(alpha = particleAlpha)
                        )
                    } else {
                        // Twinkling star particle circle
                        val dotRadius = (4.5.dp.toPx() * (1f - p * 0.65f)).coerceAtLeast(1.2f)
                        drawCircle(
                            color = color.copy(alpha = particleAlpha),
                            radius = dotRadius,
                            center = Offset(px, py)
                        )
                    }
                }
            }
        }

        // Precision Multi-Color Vector Heart Canvas
        Canvas(modifier = Modifier.size(86.dp)) {
            val heartPath = createHeartPath(size.width, size.height)

            // Deep 3D Drop Shadow
            val shadowPath = Path().apply {
                addPath(heartPath, Offset(0f, 6.dp.toPx()))
            }
            drawPath(
                path = shadowPath,
                color = Color.Black.copy(alpha = 0.45f),
                style = Fill
            )

            // Multi-color dynamic gradient with smooth blending & animated shift
            val shift = gradientShift.value
            val gradientBrush = Brush.linearGradient(
                colors = burst.palette,
                start = Offset(0f, size.height * shift),
                end = Offset(size.width, size.height * (1f + shift))
            )

            // Draw filled multi-color gradient heart
            drawPath(
                path = heartPath,
                brush = gradientBrush,
                style = Fill
            )

            // Inner specular gloss highlight
            clipPath(heartPath) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(size.width * 0.35f, size.height * 0.28f),
                        radius = size.width * 0.45f
                    ),
                    radius = size.width * 0.45f,
                    center = Offset(size.width * 0.35f, size.height * 0.28f)
                )
            }

            // Subtle outer stroke for crisp contrast against any video background
            drawPath(
                path = heartPath,
                color = Color.White.copy(alpha = 0.25f),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

/**
 * Flagship Like Button Component:
 * - Multi-color gradient fill with smooth complementary color transitions
 * - Dynamic color rotation on every tap from curated palette list
 * - Fluid spring bounce with fresh per-tap physics
 * - Subtle radiant glow aura and flying micro-heart particles
 * - Smooth contraction on unlike
 * - Animated sliding number ticker for likes count
 * - Accessible, 60fps GPU-accelerated Compose Canvas
 */
/**
 * Global Super Hype Rain Controller:
 * When the user holds any Like button, triggers a full-screen & local "Super Hype" particle rain
 * with multi-pulse haptics, golden/crimson hearts, and glowing hype badge.
 */
object SuperHypeRainController {
    val hypeBurstTrigger = kotlinx.coroutines.flow.MutableStateFlow(0L)

    fun triggerSuperHype() {
        hypeBurstTrigger.value = System.currentTimeMillis()
    }
}

@Composable
fun SuperHypeRainOverlay(modifier: Modifier = Modifier) {
    val triggerTime by SuperHypeRainController.hypeBurstTrigger.collectAsState()
    var isRaining by remember { mutableStateOf(false) }
    val rainProgress = remember { Animatable(0f) }
    val badgeScale = remember { Animatable(0f) }

    val particles = remember(triggerTime) {
        if (triggerTime == 0L) emptyList()
        else List(36) { idx ->
            SuperHypeParticle(
                xFraction = (idx * 0.028f + Random.nextFloat() * 0.06f).coerceIn(0.04f, 0.96f),
                startDelay = Random.nextFloat() * 0.35f,
                speed = 0.65f + Random.nextFloat() * 0.55f,
                sizeDp = 16f + Random.nextFloat() * 18f,
                wobblePhase = Random.nextFloat() * 6.28f,
                color = LikeColors.SparkleColors[idx % LikeColors.SparkleColors.size],
                isStar = idx % 4 == 0
            )
        }
    }

    LaunchedEffect(triggerTime) {
        if (triggerTime > 0L) {
            isRaining = true
            rainProgress.snapTo(0f)
            badgeScale.snapTo(0.4f)
            launch {
                badgeScale.animateTo(
                    targetValue = 1.15f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
                )
                badgeScale.animateTo(1.0f, animationSpec = tween(180))
            }
            rainProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1650, easing = FastOutSlowInEasing)
            )
            isRaining = false
        }
    }

    if (isRaining && particles.isNotEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val p = rainProgress.value
                particles.forEach { pt ->
                    val localP = ((p - pt.startDelay) / (1f - pt.startDelay).coerceAtLeast(0.2f)).coerceIn(0f, 1f)
                    if (localP > 0f && localP < 1f) {
                        val alpha = (1f - (localP - 0.65f).coerceAtLeast(0f) / 0.35f).coerceIn(0f, 1f)
                        val x = size.width * pt.xFraction + sin(localP * 8f + pt.wobblePhase) * 24.dp.toPx()
                        // Rain both from top and rising fountain from bottom
                        val y = size.height * (0.88f - localP * 0.82f * pt.speed)
                        drawMiniHeart(
                            center = Offset(x, y),
                            sizePx = pt.sizeDp.dp.toPx() * (1f - localP * 0.25f),
                            color = pt.color.copy(alpha = alpha)
                        )
                    }
                }
            }

            // Floating "SUPER HYPE! 🔥" Pill Banner
            val bannerAlpha = (1f - (rainProgress.value - 0.7f).coerceAtLeast(0f) / 0.3f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .padding(top = 96.dp)
                    .graphicsLayer {
                        scaleX = badgeScale.value
                        scaleY = badgeScale.value
                        alpha = bannerAlpha
                    }
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFFFF1744), Color(0xFFFF6D00), Color(0xFFFFD600))
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "🔥 SUPER HYPE! x10 LOVE ✨",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

private data class SuperHypeParticle(
    val xFraction: Float,
    val startDelay: Float,
    val speed: Float,
    val sizeDp: Float,
    val wobblePhase: Float,
    val color: Color,
    val isStar: Boolean
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FlagshipLikeButton(
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    likesCount: Number? = null,
    iconSize: Dp = 28.dp,
    likedColor: Color = LikeColors.CrimsonHeart,
    unlikedColor: Color = MaterialTheme.colorScheme.onBackground,
    isOverlayMode: Boolean = false,
    showCount: Boolean = true,
    countStyle: TextStyle? = null,
    isVertical: Boolean = false,
    hapticFeedbackEnabled: Boolean = true,
    onCountClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Incremented on each tap for dynamic color rotation and physics variations
    var tapCounter by remember { mutableIntStateOf(if (isLiked) 1 else 0) }
    var superHypeActive by remember { mutableStateOf(false) }

    // Spring scale animatable for heart bounce
    val heartScale = remember { Animatable(if (isLiked) 1f else 1f) }
    // Particle burst animatables for sparkles and mini hearts
    val burstProgress = remember { Animatable(0f) }
    val burstAlpha = remember { Animatable(0f) }
    // Gradient fill opacity for smooth unlike transition
    val likedFillAlpha = remember { Animatable(if (isLiked) 1f else 0f) }

    // Keep heart gradient crisp and static when idle so feed/reels lists never recompose at 60fps while scrolling
    val gradientShift = 0.5f

    var lastLikedState by remember { mutableStateOf(isLiked) }

    // Determine current color palette for this interaction
    val currentPalette = remember(tapCounter, likedColor) {
        if (tapCounter > 0) {
            LikeColors.getPalette(tapCounter)
        } else {
            listOf(likedColor, Color(0xFFFF4081))
        }
    }

    // Dynamic per-tap variations (overshoot, particle count, angle)
    val currentOvershoot = remember(tapCounter) {
        1.32f + (Math.floorMod(tapCounter * 7, 16) / 100f)
    }

    // Trigger spring physics and multi-color burst on state toggle
    LaunchedEffect(isLiked) {
        if (isLiked != lastLikedState) {
            lastLikedState = isLiked
            if (isLiked) {
                tapCounter++
                // Like: Quick anticipation squash -> Bouncy Overshoot -> Natural Settle
                launch {
                    likedFillAlpha.snapTo(1f)
                    heartScale.animateTo(0.70f, animationSpec = tween(65, easing = FastOutSlowInEasing))
                    heartScale.animateTo(
                        targetValue = currentOvershoot,
                        animationSpec = spring(
                            dampingRatio = 0.40f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    heartScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }

                // Micro particles & glow shockwave
                launch {
                    burstProgress.snapTo(0f)
                    burstAlpha.snapTo(1f)
                    launch {
                        burstProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(420, easing = FastOutSlowInEasing)
                        )
                    }
                    launch {
                        delay(140)
                        burstAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(280, easing = FastOutLinearInEasing)
                        )
                    }
                }
            } else {
                // Unlike: Smooth contraction -> Fade gradient out -> Settle
                launch {
                    likedFillAlpha.animateTo(0f, animationSpec = tween(160, easing = FastOutSlowInEasing))
                }
                launch {
                    heartScale.animateTo(0.80f, animationSpec = tween(80, easing = FastOutSlowInEasing))
                    heartScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.65f,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
        }
    }

    val outlineColor = if (isOverlayMode) Color.White else unlikedColor

    val finalCountStyle = countStyle ?: TextStyle(
        fontSize = if (isVertical) 12.sp else 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (isOverlayMode) Color.White else MaterialTheme.colorScheme.onBackground,
        shadow = if (isOverlayMode) Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(1f, 1f),
            blurRadius = 3f
        ) else null
    )

    val content = @Composable {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(iconSize + 18.dp)
        ) {
            // Radiant Glow & Confetti Burst Layer behind heart
            if (burstAlpha.value > 0f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val p = burstProgress.value
                    val alpha = burstAlpha.value

                    // Ambient soft glow aura
                    val maxRadius = size.width / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                currentPalette.first().copy(alpha = alpha * 0.6f),
                                currentPalette.last().copy(alpha = alpha * 0.2f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = maxRadius * (if (superHypeActive) 1.7f else 1.2f)
                        ),
                        radius = maxRadius * (if (superHypeActive) 1.6f else 1.1f),
                        center = center
                    )

                    // Expanding shockwave ring
                    val ringRadius = maxRadius * (if (superHypeActive) 1.45f else 0.95f) * p
                    val strokeWidth = (2.6.dp.toPx() * (1f - p)).coerceAtLeast(0.5f)
                    drawCircle(
                        color = currentPalette.first().copy(alpha = alpha * 0.75f),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = strokeWidth)
                    )

                    // 10 or 18 micro particles (alternating sparkles and mini hearts)
                    val numDots = if (superHypeActive) 18 else 10
                    for (i in 0 until numDots) {
                        val angle = (i * (360f / numDots) + (tapCounter * 18f)) * (PI / 180f)
                        val dotDist = (maxRadius * 0.35f) + (maxRadius * (if (superHypeActive) 1.35f else 0.85f) * p)
                        val dx = center.x + dotDist * cos(angle).toFloat()
                        val dy = center.y + dotDist * sin(angle).toFloat()
                        val dotColor = LikeColors.SparkleColors[(i + tapCounter) % LikeColors.SparkleColors.size]

                        if (i % 2 == 0) {
                            // Mini particle heart
                            drawMiniHeart(
                                center = Offset(dx, dy),
                                sizePx = ((if (superHypeActive) 10.dp else 7.dp).toPx() * (1f - p * 0.5f)).coerceAtLeast(1.5f),
                                color = dotColor.copy(alpha = alpha)
                            )
                        } else {
                            // Twinkling dot
                            val dotRadius = (2.4.dp.toPx() * (1f - p * 0.6f)).coerceAtLeast(0.8f)
                            drawCircle(
                                color = dotColor.copy(alpha = alpha),
                                radius = dotRadius,
                                center = Offset(dx, dy)
                            )
                        }
                    }
                }
            }

            // Precision Vector Multi-Color Heart
            Canvas(
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        scaleX = heartScale.value
                        scaleY = heartScale.value
                        if (isOverlayMode) {
                            shadowElevation = 4f
                        }
                    }
            ) {
                val heartPath = createHeartPath(size.width, size.height)

                // 1. Unliked Outline Base (fades smoothly when liked)
                val unlikedAlphaVal = (1f - likedFillAlpha.value).coerceIn(0f, 1f)
                if (unlikedAlphaVal > 0f) {
                    drawPath(
                        path = heartPath,
                        color = outlineColor.copy(alpha = unlikedAlphaVal),
                        style = Stroke(width = 2.0.dp.toPx())
                    )
                }

                // 2. Multi-Color Gradient Filled Heart (fades in smoothly when liked)
                val likedAlphaVal = likedFillAlpha.value
                if (likedAlphaVal > 0f) {
                    // Soft aura behind active heart
                    drawPath(
                        path = heartPath,
                        color = currentPalette.first().copy(alpha = likedAlphaVal * 0.35f),
                        style = Stroke(width = 4.dp.toPx())
                    )

                    // Dynamic gradient with half-half blending and moving shift
                    val startOffset = Offset(0f, size.height * gradientShift)
                    val endOffset = Offset(size.width, size.height * (1f + gradientShift))

                    val gradientBrush = Brush.linearGradient(
                        colors = currentPalette.map { it.copy(alpha = likedAlphaVal) },
                        start = startOffset,
                        end = endOffset
                    )

                    drawPath(
                        path = heartPath,
                        brush = gradientBrush,
                        style = Fill
                    )

                    // Specular light sheen across the heart lobe
                    clipPath(heartPath) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.30f * likedAlphaVal), Color.Transparent),
                                center = Offset(size.width * 0.32f, size.height * 0.28f),
                                radius = size.width * 0.40f
                            ),
                            radius = size.width * 0.40f,
                            center = Offset(size.width * 0.32f, size.height * 0.28f)
                        )
                    }
                }
            }
        }

        // Animated Likes Count
        if (showCount && likesCount != null) {
            val countModifier = if (onCountClick != null) {
                (if (isVertical) Modifier.padding(top = 2.dp) else Modifier.padding(start = 6.dp))
                    .clickable { onCountClick() }
            } else {
                if (isVertical) Modifier.padding(top = 2.dp) else Modifier.padding(start = 6.dp)
            }
            AnimatedLikesCounter(
                count = likesCount,
                textStyle = finalCountStyle,
                modifier = countModifier
            )
        }
    }

    val clickModifier = modifier
        .semantics {
            contentDescription = if (isLiked) "Unlike" else "Like"
        }
        .combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                superHypeActive = false
                if (hapticFeedbackEnabled) {
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    } catch (_: Exception) {}
                }
                com.example.utils.SoundManager.playClickSound()
                onClick()
            },
            onLongClick = {
                // Holding the Like button triggers "Super Hype" particle burst & multi-pulse tactile haptics!
                superHypeActive = true
                tapCounter += 2
                SuperHypeRainController.triggerSuperHype()
                if (!isLiked) {
                    onClick()
                }
                com.example.utils.SoundManager.playClickSound()
                scope.launch {
                    repeat(4) {
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        } catch (_: Exception) {}
                        delay(70)
                    }
                }
                scope.launch {
                    likedFillAlpha.snapTo(1f)
                    burstProgress.snapTo(0f)
                    burstAlpha.snapTo(1f)
                    launch {
                        heartScale.animateTo(1.65f, animationSpec = spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
                        heartScale.animateTo(1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                    }
                    launch {
                        burstProgress.animateTo(1f, animationSpec = tween(680, easing = FastOutSlowInEasing))
                    }
                    launch {
                        delay(280)
                        burstAlpha.animateTo(0f, animationSpec = tween(400, easing = FastOutLinearInEasing))
                    }
                }
            }
        )

    if (isVertical) {
        Column(
            modifier = clickModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            content()
        }
    } else {
        Row(
            modifier = clickModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

/**
 * Animated sliding number ticker for likes count.
 * Slides in from bottom when incrementing, from top when decrementing.
 */
@Composable
fun AnimatedLikesCounter(
    count: Number,
    textStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    val formattedCount = remember(count) {
        when (count) {
            is Long -> com.example.utils.NumberFormatUtils.formatCount(count)
            else -> com.example.utils.NumberFormatUtils.formatCount(count.toInt())
        }
    }

    AnimatedContent(
        targetState = formattedCount,
        transitionSpec = {
            if (targetState.length >= initialState.length) {
                // Slide up on increment
                (slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) { height -> height / 2 } + fadeIn())
                    .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut())
            } else {
                // Slide down on decrement
                (slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) { height -> -height / 2 } + fadeIn())
                    .togetherWith(slideOutVertically { height -> height / 2 } + fadeOut())
            }
        },
        label = "LikesCountAnimation",
        modifier = modifier
    ) { targetText ->
        Text(
            text = targetText,
            style = textStyle
        )
    }
}
