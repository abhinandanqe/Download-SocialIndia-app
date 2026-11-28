package com.example.ui

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.motion.SocialMotion
import com.example.ui.motion.rememberReducedMotionEnabled
import com.example.utils.bounceClick
import coil.compose.AsyncImage
import com.example.model.GeminiService
import com.example.model.RepositoryManager
import com.example.model.VideoPost
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Crisp tactile tick helper for 60fps/120fps micro-interactions
 */
fun triggerCrispMicroHaptic(context: android.content.Context, strong: Boolean = false) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(
                VibrationEffect.createPredefined(
                    if (strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_TICK
                )
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(if (strong) 24L else 12L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } catch (_: Exception) {}
}

/**
 * 1:1 Instagram Repost Button with 360-degree GPU rotation, scale bounce (1.0 -> 1.3 -> 1.0),
 * smooth green color transition (#00E676), light haptic feedback, and vertical roll counter.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun InstagramRepostButton(
    isReposted: Boolean,
    repostCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 25.dp,
    inactiveTint: Color = Color.White,
    activeTint: Color = Color(0xFF00E676),
    isVerticalLayout: Boolean = false,
    formatCount: (Int) -> String = { it.toString() }
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotionEnabled()

    val rotationAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(1f) }
    var burstActive by remember { mutableStateOf(false) }
    val burstProgress = remember { Animatable(0f) }

    val tintColor by animateColorAsState(
        targetValue = if (isReposted) activeTint else inactiveTint,
        animationSpec = SocialMotion.colorSpec(SocialMotion.DurationFast, reducedMotion),
        label = "repost_tint"
    )

    val triggerBounceAndRotate = {
        triggerCrispMicroHaptic(context, strong = false)
        try {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } catch (_: Throwable) {}
        if (!reducedMotion) {
            scope.launch {
                launch {
                    val targetRot = rotationAnim.value + 360f
                    rotationAnim.animateTo(
                        targetValue = targetRot,
                        animationSpec = SocialMotion.floatSpec(SocialMotion.DurationMedium)
                    )
                }
                launch {
                    scaleAnim.snapTo(1f)
                    scaleAnim.animateTo(
                        targetValue = SocialMotion.RepostBouncePeak,
                        animationSpec = SocialMotion.floatSpec(SocialMotion.DurationQuick)
                    )
                    scaleAnim.animateTo(
                        targetValue = 1.0f,
                        animationSpec = SocialMotion.springPop()
                    )
                }
                if (!isReposted) {
                    burstActive = true
                    burstProgress.snapTo(0f)
                    burstProgress.animateTo(1f, SocialMotion.floatSpec(SocialMotion.DurationMedium))
                    burstActive = false
                }
            }
        }
        onClick()
    }

    if (isVerticalLayout) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .bounceClick(enableHaptics = false)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { triggerBounceAndRotate() }
                .testTag("instagram_repost_button")
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(iconSize + 12.dp)) {
                if (burstActive) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = (size.minDimension / 2f) * (0.6f + burstProgress.value * 0.7f)
                        for (i in 0 until 8) {
                            val angle = (i * 45.0) * (Math.PI / 180.0)
                            val px = center.x + (cos(angle) * radius).toFloat()
                            val py = center.y + (sin(angle) * radius).toFloat()
                            drawCircle(
                                color = activeTint.copy(alpha = (1f - burstProgress.value).coerceIn(0f, 1f)),
                                radius = 3.dp.toPx() * (1f - burstProgress.value * 0.5f),
                                center = Offset(px, py)
                            )
                        }
                    }
                }
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Repost",
                    tint = tintColor,
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            rotationZ = rotationAnim.value
                            scaleX = scaleAnim.value
                            scaleY = scaleAnim.value
                        }
                )
            }
            if (repostCount > 0) {
                AnimatedCounterText(
                    count = repostCount,
                    formatCount = formatCount,
                    color = if (isReposted) activeTint else inactiveTint
                )
            } else {
                Text(
                    text = if (isReposted) "Reposted" else "Repost",
                    color = tintColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .bounceClick(enableHaptics = false)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { triggerBounceAndRotate() }
                .testTag("instagram_repost_button")
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(iconSize + 6.dp)) {
                if (burstActive) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = (size.minDimension / 2f) * (0.6f + burstProgress.value * 0.7f)
                        for (i in 0 until 8) {
                            val angle = (i * 45.0) * (Math.PI / 180.0)
                            val px = center.x + (cos(angle) * radius).toFloat()
                            val py = center.y + (sin(angle) * radius).toFloat()
                            drawCircle(
                                color = activeTint.copy(alpha = (1f - burstProgress.value).coerceIn(0f, 1f)),
                                radius = 2.5.dp.toPx() * (1f - burstProgress.value * 0.5f),
                                center = Offset(px, py)
                            )
                        }
                    }
                }
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Repost",
                    tint = tintColor,
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            rotationZ = rotationAnim.value
                            scaleX = scaleAnim.value
                            scaleY = scaleAnim.value
                        }
                )
            }
            if (repostCount > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                AnimatedCounterText(
                    count = repostCount,
                    formatCount = formatCount,
                    color = if (isReposted) activeTint else inactiveTint
                )
            }
        }
    }
}

/**
 * Vertical Slide-Fade Counter Animation for Like / Comment / Repost / Share counts.
 * Uses SocialMotion presets and respects reduced motion.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedCounterText(
    count: Int,
    formatCount: (Int) -> String = { it.toString() },
    color: Color = Color.White,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    style: androidx.compose.ui.text.TextStyle? = null
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val duration = if (reducedMotion) 1 else SocialMotion.DurationFast
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(1)).togetherWith(fadeOut(tween(1)))
            } else if (targetState > initialState) {
                (slideInVertically(tween(duration, easing = SocialMotion.EasingStandard)) { height -> height / 2 } +
                    fadeIn(tween(duration, easing = SocialMotion.EasingStandard))).togetherWith(
                    slideOutVertically(tween(duration, easing = SocialMotion.EasingStandard)) { height -> -height / 2 } +
                        fadeOut(tween(duration, easing = SocialMotion.EasingStandard))
                )
            } else {
                (slideInVertically(tween(duration, easing = SocialMotion.EasingStandard)) { height -> -height / 2 } +
                    fadeIn(tween(duration, easing = SocialMotion.EasingStandard))).togetherWith(
                    slideOutVertically(tween(duration, easing = SocialMotion.EasingStandard)) { height -> height / 2 } +
                        fadeOut(tween(duration, easing = SocialMotion.EasingStandard))
                )
            }.using(SizeTransform(clip = false))
        },
        label = "counter_slide"
    ) { targetCount ->
        if (style != null) {
            Text(
                text = formatCount(targetCount),
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                style = style
            )
        } else {
            Text(
                text = formatCount(targetCount),
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight
            )
        }
    }
}

/**
 * 1:1 Instagram Paper-Plane Share Button with upward-right glide impulse & spring bounce
 */
@Composable
fun InstagramShareButton(
    shareCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 23.dp,
    tint: Color = Color.White,
    isVerticalLayout: Boolean = false,
    formatCount: (Int) -> String = { it.toString() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotionEnabled()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }

    val handleTap = {
        triggerCrispMicroHaptic(context)
        if (!reducedMotion) {
            scope.launch {
                launch {
                    offsetX.animateTo(5f, SocialMotion.floatSpec(SocialMotion.DurationQuick))
                    offsetX.animateTo(0f, SocialMotion.springSnappy())
                }
                launch {
                    offsetY.animateTo(-5f, SocialMotion.floatSpec(SocialMotion.DurationQuick))
                    offsetY.animateTo(0f, SocialMotion.springSnappy())
                }
                launch {
                    scale.snapTo(SocialMotion.PressScale)
                    scale.animateTo(1.12f, SocialMotion.floatSpec(SocialMotion.DurationQuick))
                    scale.animateTo(1f, SocialMotion.springSnappy())
                }
            }
        }
        onClick()
    }

    if (isVerticalLayout) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .bounceClick(enableHaptics = false)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { handleTap() }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Share",
                tint = tint,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        translationX = offsetX.value
                        translationY = offsetY.value
                        rotationZ = -22f
                        scaleX = scale.value
                        scaleY = scale.value
                    }
            )
            if (shareCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                AnimatedCounterText(count = shareCount, formatCount = formatCount, color = tint, fontSize = 12.sp)
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { handleTap() }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Share",
                tint = tint,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        translationX = offsetX.value
                        translationY = offsetY.value
                        rotationZ = -22f
                        scaleX = scale.value
                        scaleY = scale.value
                    }
            )
            if (shareCount > 0) {
                Spacer(modifier = Modifier.width(5.dp))
                AnimatedCounterText(count = shareCount, formatCount = formatCount, color = tint)
            }
        }
    }
}

/**
 * 1:1 Instagram Bookmark Button with press-scale (0.92 -> 1.0), smooth color morph, and GPU transform.
 */
@Composable
fun InstagramBookmarkButton(
    isBookmarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 25.dp,
    activeTint: Color = Color(0xFFFFD54F),
    inactiveTint: Color = Color.White
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotionEnabled()
    val scale = remember { Animatable(1f) }
    val iconTint by animateColorAsState(
        targetValue = if (isBookmarked) activeTint else inactiveTint,
        animationSpec = SocialMotion.colorSpec(SocialMotion.DurationFast, reducedMotion),
        label = "bookmark_tint"
    )

    IconButton(
        onClick = {
            triggerCrispMicroHaptic(context, strong = false)
            if (!reducedMotion) {
                scope.launch {
                    scale.snapTo(SocialMotion.PressScale)
                    scale.animateTo(1.2f, SocialMotion.floatSpec(SocialMotion.DurationQuick))
                    scale.animateTo(1f, SocialMotion.springPop())
                }
            }
            onClick()
        },
        modifier = modifier
            .size(36.dp)
            .bounceClick(enableHaptics = false)
    ) {
        Icon(
            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = "Bookmark",
            tint = iconTint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
        )
    }
}

/**
 * 1:1 Instagram Floating "Reposted to your profile / Undo / Tap to add a thought..." Overlay Toast.
 * Must be placed inside an overlay Box (never in a Column flow) so surrounding UI never shifts.
 * Animates strictly transform (slide up 24dp -> 0) and opacity (fade 0 -> 1) over 250ms cubic-bezier(0.2,0,0,1).
 */
@Composable
fun RepostFloatingPill(
    visible: Boolean,
    userAvatarUrl: String,
    onAddThoughtClick: () -> Unit,
    onUndoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val slideDistancePx = with(LocalDensity.current) { 24.dp.toPx() }
    val alphaAnim = remember { Animatable(0f) }
    val translationYAnim = remember { Animatable(slideDistancePx) }
    var keepComposed by remember { mutableStateOf(visible) }

    LaunchedEffect(visible, reducedMotion) {
        if (visible) {
            keepComposed = true
            if (reducedMotion) {
                translationYAnim.snapTo(0f)
                alphaAnim.snapTo(1f)
            } else {
                translationYAnim.snapTo(slideDistancePx)
                alphaAnim.snapTo(0f)
                launch {
                    translationYAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = SocialMotion.floatSpec(SocialMotion.DurationToast)
                    )
                }
                launch {
                    alphaAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = SocialMotion.floatSpec(SocialMotion.DurationToast)
                    )
                }
            }
        } else if (keepComposed) {
            if (reducedMotion) {
                alphaAnim.snapTo(0f)
                translationYAnim.snapTo(slideDistancePx)
            } else {
                val slideJob = launch {
                    translationYAnim.animateTo(
                        targetValue = slideDistancePx,
                        animationSpec = SocialMotion.floatSpec(SocialMotion.DurationToast)
                    )
                }
                val fadeJob = launch {
                    alphaAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = SocialMotion.floatSpec(SocialMotion.DurationToast)
                    )
                }
                slideJob.join()
                fadeJob.join()
            }
            keepComposed = false
        }
    }

    if (!keepComposed) return

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = translationYAnim.value
                alpha = alphaAnim.value
            }
            .testTag("repost_floating_toast")
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color(0xFF18181B).copy(alpha = 0.96f),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .border(1.dp, Color(0xFF00E676).copy(alpha = 0.5f), RoundedCornerShape(30.dp))
                .clickable(enabled = visible) { onAddThoughtClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    AsyncImage(
                        model = userAvatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/svg?seed=me" },
                        contentDescription = "Your Avatar",
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(Color(0xFF00E676), CircleShape)
                            .border(1.dp, Color.Black, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(9.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Reposted to your profile",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap to add a thought...",
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                TextButton(
                    onClick = { if (visible) onUndoClick() },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .bounceClick(enableHaptics = false)
                        .testTag("repost_undo_button")
                ) {
                    Text(
                        text = "Undo",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 1-Tap Regional Caption Translator (Hindi / Hinglish / Regional Language)
 */
@Composable
fun CaptionWithRegionalTranslator(
    username: String,
    caption: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    maxLines: Int = 2,
    onHashtagClick: (String) -> Unit = {}
) {
    if (caption.isBlank()) return
    val scope = rememberCoroutineScope()
    var isExpanded by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var translatedText by remember(caption) { mutableStateOf<String?>(null) }
    var showTranslated by remember(caption) { mutableStateOf(false) }

    Column(modifier = modifier) {
        val displayCaption = if (showTranslated && !translatedText.isNullOrBlank()) translatedText!! else caption
        Text(
            text = buildAnnotatedString {
                if (username.isNotBlank()) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                        append("$username ")
                    }
                }
                val words = displayCaption.split(" ")
                words.forEachIndexed { idx, word ->
                    if (word.startsWith("#") || word.startsWith("@")) {
                        withStyle(SpanStyle(color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)) {
                            append(word)
                        }
                    } else {
                        withStyle(SpanStyle(color = textColor.copy(alpha = 0.92f))) {
                            append(word)
                        }
                    }
                    if (idx < words.lastIndex) append(" ")
                }
            },
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            maxLines = if (isExpanded) 12 else maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { isExpanded = !isExpanded }
        )

        Spacer(modifier = Modifier.height(3.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        if (showTranslated) {
                            showTranslated = false
                        } else if (translatedText != null) {
                            showTranslated = true
                        } else if (!isTranslating) {
                            isTranslating = true
                            scope.launch {
                                translatedText = GeminiService.translateText(caption, "Hindi")
                                isTranslating = false
                                showTranslated = true
                            }
                        }
                    }
                    .padding(vertical = 2.dp)
            ) {
                Icon(
                    Icons.Default.Translate,
                    contentDescription = "Translate",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when {
                        isTranslating -> "Translating to Hindi..."
                        showTranslated -> "See Original (मूल देखें)"
                        else -> "See Hindi Translation (अ/A)"
                    },
                    color = Color(0xFF38BDF8),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Celebratory Golden Super-Tip Confetti Overlay
 */
@Composable
fun SuperTipConfettiOverlay(
    visible: Boolean,
    amountInr: Int,
    creatorName: String,
    onFinished: () -> Unit
) {
    if (!visible) return
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(2400, easing = FastOutSlowInEasing))
        onFinished()
    }

    val particles = remember {
        List(45) {
            Triple(
                Random.nextFloat(), // x fraction
                Random.nextFloat() * 0.4f, // start y delay
                listOf(Color(0xFFFFD700), Color(0xFFFFA000), Color(0xFF00E676), Color(0xFFE040FB), Color(0xFF00B0FF)).random()
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.forEachIndexed { index, (xFrac, delayFrac, color) ->
                val localP = ((progress.value - delayFrac) / (1f - delayFrac)).coerceIn(0f, 1f)
                if (localP > 0f && localP < 1f) {
                    val x = size.width * xFrac + sin(localP * 10f + index) * 28f
                    val y = size.height * localP
                    drawRect(
                        color = color.copy(alpha = (1f - localP).coerceIn(0f, 1f)),
                        topLeft = Offset(x, y),
                        size = Size(16f, 10f)
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E1B18).copy(alpha = 0.95f),
            modifier = Modifier
                .border(2.dp, Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8F00))), RoundedCornerShape(24.dp))
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("👑✨", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "₹$amountInr Super Tip Sent!",
                    color = Color(0xFFFFD700),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Supporting @$creatorName • Verified in Creator Wallet",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Instant Creator Super-Tip Bottom Sheet (UPI / Pocket Wallet Hook)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorSuperTipSheet(
    creatorId: String,
    creatorName: String,
    creatorAvatar: String,
    postId: String = "",
    onDismiss: () -> Unit,
    onTipSuccess: (Int) -> Unit
) {
    val context = LocalContext.current
    val presets = listOf(10, 20, 50, 100, 250, 500)
    var selectedAmount by remember { mutableStateOf(20) }
    var isCustomAmount by remember { mutableStateOf(false) }
    var customAmountInput by remember { mutableStateOf("") }
    var tipMessage by remember { mutableStateOf("Keep making awesome content! 🔥🇮🇳") }
    var showRealUpiGateway by remember { mutableStateOf(false) }

    val effectiveAmount = remember(selectedAmount, isCustomAmount, customAmountInput) {
        if (isCustomAmount) {
            customAmountInput.toIntOrNull()?.coerceAtLeast(10) ?: 10
        } else {
            selectedAmount
        }
    }

    if (showRealUpiGateway) {
        com.example.ui.components.RealUpiPaymentBottomSheet(
            title = "Super Tip @$creatorName",
            subtitle = tipMessage.ifBlank { "Creator Support Tip" },
            amountRupees = effectiveAmount.toDouble(),
            productType = com.example.model.PaymentProductType.CREATOR_TIP,
            productId = postId.ifBlank { "TIP_${creatorId}" },
            creatorId = creatorId,
            contentId = postId,
            onDismiss = { showRealUpiGateway = false },
            onPaymentVerified = { _ ->
                showRealUpiGateway = false
                onTipSuccess(effectiveAmount)
            },
            onUtrSubmitted = { _, _ ->
                showRealUpiGateway = false
                onTipSuccess(effectiveAmount)
            }
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121214),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = creatorAvatar.ifEmpty { "https://api.dicebear.com/7.x/avataaars/svg?seed=$creatorName" },
                    contentDescription = creatorName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color(0xFFFFD700), CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Super Tip @$creatorName",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("💎", fontSize = 16.sp)
                    }
                    Text(
                        text = "Real NPCI UPI Gateway • Verified Creator Earnings Ledger",
                        color = Color(0xFFFFD700),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Preset grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { amt ->
                            val isSelected = !isCustomAmount && selectedAmount == amt
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) Color(0xFFFFD700).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color(0xFFFFD700) else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        triggerCrispMicroHaptic(context)
                                        isCustomAmount = false
                                        selectedAmount = amt
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "₹$amt",
                                        color = if (isSelected) Color(0xFFFFD700) else Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = when (amt) {
                                            10 -> "Chai ☕"
                                            20 -> "Boost ⚡"
                                            50 -> "Super 🔥"
                                            100 -> "Golden 🌟"
                                            250 -> "VIP 👑"
                                            else -> "Legend 💎"
                                        },
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Custom Amount Option
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = isCustomAmount,
                    onClick = {
                        triggerCrispMicroHaptic(context)
                        isCustomAmount = !isCustomAmount
                        if (isCustomAmount && customAmountInput.isBlank()) {
                            customAmountInput = "150"
                        }
                    },
                    label = {
                        Text(
                            text = if (isCustomAmount) "Custom Amount Active" else "+ Custom Amount (₹)",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFD700).copy(alpha = 0.22f),
                        selectedLabelColor = Color(0xFFFFD700)
                    )
                )

                if (isCustomAmount) {
                    OutlinedTextField(
                        value = customAmountInput,
                        onValueChange = { input ->
                            customAmountInput = input.filter { it.isDigit() }.take(5)
                        },
                        label = { Text("Custom ₹ (Min ₹10)", color = Color.Gray, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFD700),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = tipMessage,
                onValueChange = { tipMessage = it },
                label = { Text("Add a highlighted supporter note", color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD700),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    triggerCrispMicroHaptic(context, strong = true)
                    showRealUpiGateway = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Pay ₹$effectiveAmount Super Tip via UPI / QR",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

/**
 * 1-Tap "Join Challenge" Leaderboard & Camera Launch Sheet on Reels
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeHubBottomSheet(
    challengeTag: String,
    soundName: String,
    post: VideoPost,
    allPosts: List<VideoPost>,
    onDismiss: () -> Unit,
    onJoinChallengeNow: (String, String) -> Unit
) {
    val cleanTag = challengeTag.removePrefix("#").ifBlank { "DesiSwagChallenge" }
    val matchingEntries = remember(allPosts, cleanTag) {
        allPosts.filter {
            it.caption.contains(cleanTag, ignoreCase = true) ||
                it.hashtags.any { tag -> tag.contains(cleanTag, ignoreCase = true) }
        }.ifEmpty { allPosts.take(5) }
            .sortedByDescending { it.likes + it.views }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101216),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFFF512F), Color(0xFFDD2476))),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#$cleanTag",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp
                    )
                    Text(
                        text = "🎵 Audio: ${soundName.ifBlank { "Original Challenge Audio" }} • 15s Timer",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "🏆 Top Challenge Leaderboard",
                color = Color(0xFFFFD700),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(matchingEntries.take(6)) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (index) {
                                0 -> "🥇"
                                1 -> "🥈"
                                2 -> "🥉"
                                else -> "#${index + 1}"
                            },
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            modifier = Modifier.width(32.dp)
                        )
                        AsyncImage(
                            model = item.userAvatar.ifEmpty { "https://api.dicebear.com/7.x/avataaars/svg?seed=${item.username}" },
                            contentDescription = item.username,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "@${item.username}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = "${item.likes} likes • ${item.views} views",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF00E676).copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "+${(item.likes * 10 + 150)} XP",
                                color = Color(0xFF00E676),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onJoinChallengeNow("#$cleanTag", soundName.ifBlank { post.musicTrack }) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C), contentColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Videocam, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Join Challenge (Auto-Load Audio + #Tag)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

/**
 * Co-Watch Party (Synchronized Reels Player for DM Chats & Calls)
 */
@Composable
fun CoWatchPartyOverlay(
    roomId: String,
    partnerName: String,
    partnerAvatar: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allReels by remember {
        mutableStateOf(
            RepositoryManager.postRepository.cachedPosts.filter { it.isVideo || it.videoUrl.isNotEmpty() }
                .ifEmpty { RepositoryManager.postRepository.cachedPosts }
        )
    }

    LaunchedEffect(Unit) {
        if (allReels.isEmpty()) {
            try {
                val snap = FirebaseFirestore.getInstance().collection("posts")
                    .limit(25)
                    .get()
                    .await()
                val fetched = snap.documents.mapNotNull { doc ->
                    val url = doc.getString("postUrl") ?: doc.getString("videoUrl") ?: ""
                    if (url.isBlank()) return@mapNotNull null
                    VideoPost(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        username = doc.getString("username") ?: "Creator",
                        userAvatar = doc.getString("userAvatarUrl") ?: "",
                        videoUrl = url,
                        thumbnailUrl = doc.getString("thumbnailUrl") ?: url,
                        caption = doc.getString("caption") ?: "",
                        musicTrack = doc.getString("musicTrack") ?: "Original Audio",
                        likes = (doc.getLong("likesCount") ?: 0L).toInt(),
                        views = (doc.getLong("viewsCount") ?: 100L).toInt(),
                        isVideo = !com.example.utils.MediaResolver.isImage(url)
                    )
                }
                if (fetched.isNotEmpty()) {
                    RepositoryManager.postRepository.cachedPosts = fetched
                    allReels = fetched
                }
            } catch (_: Exception) {}
        }
    }

    val pagerState = rememberPagerState(pageCount = { allReels.size.coerceAtLeast(1) })
    var latestReaction by remember { mutableStateOf<String?>(null) }
    var isSyncingRemote by remember { mutableStateOf(false) }

    // Real-time Firestore listener for synchronized Reel index + emoji bursts
    DisposableEffect(roomId) {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("cowatch_rooms").document(roomId.ifBlank { "default_room" })
        val reg = docRef.addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) {
                val remotePage = (snap.getLong("currentPage") ?: 0L).toInt()
                val remoteEmoji = snap.getString("lastEmoji")
                if (remotePage in 0 until allReels.size && remotePage != pagerState.currentPage) {
                    isSyncingRemote = true
                    scope.launch {
                        pagerState.animateScrollToPage(remotePage)
                        delay(200)
                        isSyncingRemote = false
                    }
                }
                if (!remoteEmoji.isNullOrBlank()) {
                    latestReaction = remoteEmoji
                }
            }
        }
        onDispose { reg.remove() }
    }

    // Push page changes when current user swipes
    LaunchedEffect(pagerState.currentPage) {
        if (!isSyncingRemote && roomId.isNotBlank()) {
            try {
                FirebaseFirestore.getInstance().collection("cowatch_rooms")
                    .document(roomId)
                    .set(
                        mapOf(
                            "currentPage" to pagerState.currentPage,
                            "updatedBy" to RepositoryManager.currentUsername,
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
            } catch (_: Exception) {}
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (allReels.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E676))
                }
            } else {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val reel = allReels[page]
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = reel.thumbnailUrl.ifEmpty { reel.mediaUrls.firstOrNull() ?: reel.videoUrl },
                            contentDescription = reel.caption,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (reel.videoUrl.isNotBlank() && !com.example.utils.MediaResolver.isImage(reel.videoUrl)) {
                            val isCurrentPage = pagerState.currentPage == page
                            val exoPlayer = remember(reel.videoUrl) {
                                com.example.utils.ExoPlayerUtils.createExoPlayer(context).apply {
                                    val mediaItem = androidx.media3.common.MediaItem.fromUri(reel.videoUrl)
                                    setMediaItem(mediaItem)
                                    repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                                    prepare()
                                }
                            }
                            LaunchedEffect(isCurrentPage) {
                                exoPlayer.playWhenReady = isCurrentPage
                            }
                            DisposableEffect(reel.videoUrl) {
                                onDispose {
                                    exoPlayer.release()
                                }
                            }
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    androidx.media3.ui.PlayerView(ctx).apply {
                                        useController = false
                                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        player = exoPlayer
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Bottom info
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, end = 90.dp, bottom = 84.dp)
                        ) {
                            Text(
                                text = "@${reel.username}",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = reel.caption,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            // Top Sync Header + Partner Floating PiP Bubble
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.border(1.dp, Color(0xFF00E676), RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF00E676), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE Co-Watch with $partnerName",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Leave Co-Watch", tint = Color.White)
                }
            }

            // Floating Partner Face-Cam / Avatar PiP Bubble
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 74.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    AsyncImage(
                        model = partnerAvatar.ifEmpty { "https://api.dicebear.com/7.x/avataaars/svg?seed=$partnerName" },
                        contentDescription = partnerName,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(2.dp, Color(0xFF00E676), RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )
                    if (latestReaction != null) {
                        Text(
                            text = latestReaction!!,
                            fontSize = 22.sp,
                            modifier = Modifier.offset(x = 4.dp, y = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Synced • Reel #${pagerState.currentPage + 1}",
                    color = Color(0xFF00E676),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bottom Reaction Bar for Co-Watch
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("😂", "🔥", "😍", "😮", "👏", "💯", "🎉").forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 26.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                triggerCrispMicroHaptic(context)
                                latestReaction = emoji
                                try {
                                    FirebaseFirestore.getInstance()
                                        .collection("cowatch_rooms")
                                        .document(roomId.ifBlank { "default_room" })
                                        .set(
                                            mapOf(
                                                "lastEmoji" to emoji,
                                                "emojiTs" to System.currentTimeMillis()
                                            ),
                                            com.google.firebase.firestore.SetOptions.merge()
                                        )
                                } catch (_: Exception) {}
                                Toast.makeText(context, "Sent $emoji to Co-Watch!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(6.dp)
                    )
                }
            }
        }
    }
}
