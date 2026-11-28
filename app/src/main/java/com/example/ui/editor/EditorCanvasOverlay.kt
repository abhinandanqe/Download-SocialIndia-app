package com.example.ui.editor

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.utils.ExoPlayerUtils
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full-screen gesture-first preview canvas supporting:
 * - Animated 9:16, 4:5, and 1:1 framing
 * - Horizontal swipe left/right to cycle Instagram filters
 * - Real-time ColorMatrix, warmth, fade, vignette, speed, reverse, mute, rotation & flip
 * - Two-finger drag, pinch & rotate for Text & Stickers
 * - Center/safe-zone snapping guides with haptic feedback
 * - Drag-to-trash delete target
 * - Smooth Bezier drawing layer (Pen, Marker, Neon, Eraser)
 * - Live synced Hindi + English Auto-Captions overlay
 */
@Composable
fun EditorPreviewCanvas(
    snapshot: EditorProjectSnapshot,
    activeClip: EditorClip?,
    isPlaying: Boolean,
    playheadFraction: Float,
    isDrawMode: Boolean,
    activeBrush: DrawBrushType,
    activeBrushColor: Color,
    activeBrushWidthDp: Float,
    selectedOverlayId: String?,
    lowEndMode: Boolean,
    onPlayheadTick: (Float, Long) -> Unit,
    onSwipeFilter: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSelectOverlay: (String?) -> Unit,
    onEditTextItem: (EditorTextItem) -> Unit,
    onUpdateTextTransform: (String, Float, Float, Float, Float) -> Unit,
    onDeleteTextItem: (String) -> Unit,
    onUpdateStickerTransform: (String, Float, Float, Float, Float) -> Unit,
    onDeleteStickerItem: (String) -> Unit,
    onCommitDrawStroke: (EditorDrawStroke) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    // Drag & snap guide state
    var isDraggingOverlay by remember { mutableStateOf(false) }
    var isOverTrash by remember { mutableStateOf(false) }
    var snapVerticalCenter by remember { mutableStateOf(false) }
    var snapHorizontalCenter by remember { mutableStateOf(false) }
    var lastSnapV by remember { mutableStateOf(false) }
    var lastSnapH by remember { mutableStateOf(false) }

    // Swipe-to-filter HUD banner state
    var swipeAccumX by remember { mutableFloatStateOf(0f) }
    var activeFilterBanner by remember { mutableStateOf<String?>(null) }
    var filterBannerKey by remember { mutableIntStateOf(0) }

    // Live drawing stroke in progress
    var inProgressStroke by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }

    val activeFilter = remember(snapshot.filterId) {
        EDITOR_FILTER_PRESETS.firstOrNull { it.id == snapshot.filterId } ?: EDITOR_FILTER_PRESETS.first()
    }
    val combinedMatrix = remember(activeFilter, snapshot.adjustments) {
        buildCombinedComposeColorMatrix(activeFilter, snapshot.adjustments)
    }
    val animatedAspect by animateFloatAsState(
        targetValue = snapshot.aspectRatio.ratio,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "editor_aspect_ratio"
    )

    LaunchedEffect(filterBannerKey) {
        if (activeFilterBanner != null) {
            delay(1100)
            activeFilterBanner = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07070A)),
        contentAlignment = Alignment.Center
    ) {
        if (activeClip == null) {
            // Empty Media State
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PermMedia,
                    contentDescription = "No media",
                    tint = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "No clip selected",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Add a photo or video from the timeline or open a saved draft.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            return@Box
        }

        // Aspect-ratio constrained stage
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(animatedAspect, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(if (snapshot.aspectRatio == AspectRatioPreset.RATIO_9_16) 14.dp else 18.dp))
                .background(Color(0xFF101016))
                .onSizeChanged { canvasSize = it }
                .pointerInput(isDrawMode, activeFilter.id) {
                    if (!isDrawMode) {
                        detectHorizontalDragGestures(
                            onDragStart = { swipeAccumX = 0f },
                            onDragEnd = {
                                if (abs(swipeAccumX) > 90f) {
                                    val dir = if (swipeAccumX < 0f) 1 else -1
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSwipeFilter(dir)
                                    val curIdx = EDITOR_FILTER_PRESETS.indexOfFirst { it.id == snapshot.filterId }.coerceAtLeast(0)
                                    val nextIdx = (curIdx + dir + EDITOR_FILTER_PRESETS.size) % EDITOR_FILTER_PRESETS.size
                                    activeFilterBanner = EDITOR_FILTER_PRESETS[nextIdx].name
                                    filterBannerKey++
                                }
                                swipeAccumX = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                swipeAccumX += dragAmount
                            }
                        )
                    }
                }
                .pointerInput(isDrawMode) {
                    if (!isDrawMode) {
                        detectTapGestures(
                            onTap = {
                                if (selectedOverlayId != null) {
                                    onSelectOverlay(null)
                                } else {
                                    onTogglePlayPause()
                                }
                            }
                        )
                    }
                }
        ) {
            // 1. Base Media Layer (Video or Photo)
            if (activeClip.isVideo) {
                VideoPreviewLayer(
                    clip = activeClip,
                    isPlaying = isPlaying,
                    externalPlayheadFraction = playheadFraction,
                    filter = activeFilter,
                    adjustments = snapshot.adjustments,
                    onPlayheadTick = onPlayheadTick
                )
            } else {
                PhotoPreviewLayer(
                    clip = activeClip,
                    colorMatrix = combinedMatrix,
                    filter = activeFilter,
                    adjustments = snapshot.adjustments,
                    lowEndMode = lowEndMode
                )
            }

            // 2. Vignette & Fade/Warmth overlay (applies consistently to both photo & video)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val filterTintAlpha = activeFilter.tintAlpha
                if (filterTintAlpha > 0f) {
                    drawRect(color = activeFilter.tintColor.copy(alpha = filterTintAlpha))
                }
                val totalFade = (activeFilter.fadeDelta + snapshot.adjustments.fade).coerceIn(0f, 1f)
                if (totalFade > 0.01f && activeClip.isVideo) {
                    drawRect(color = Color.White.copy(alpha = totalFade * 0.14f))
                }
                val totalWarmth = (activeFilter.warmthDelta + snapshot.adjustments.warmth).coerceIn(-1f, 1f)
                if (abs(totalWarmth) > 0.02f && activeClip.isVideo) {
                    val warmTint = if (totalWarmth > 0f) Color(0xFFFFA726) else Color(0xFF42A5F5)
                    drawRect(color = warmTint.copy(alpha = (abs(totalWarmth) * 0.16f).coerceIn(0f, 0.24f)))
                }
                val vig = snapshot.adjustments.vignette
                if (vig > 0.01f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.42f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = (vig * 0.85f).coerceIn(0f, 0.88f))
                            ),
                            center = center,
                            radius = size.maxDimension * 0.72f
                        )
                    )
                }
            }

            // 3. Drawing Layer (GPU Offscreen layer so Eraser clears only drawn ink!)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .then(
                        if (isDrawMode) {
                            Modifier.pointerInput(activeBrush, activeBrushColor, activeBrushWidthDp) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        inProgressStroke = listOf(NormalizedPoint(nx, ny))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val pos = change.position
                                        val nx = (pos.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (pos.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        inProgressStroke = inProgressStroke + NormalizedPoint(nx, ny)
                                    },
                                    onDragEnd = {
                                        if (inProgressStroke.size >= 2) {
                                            onCommitDrawStroke(
                                                EditorDrawStroke(
                                                    brush = activeBrush,
                                                    colorArgb = activeBrushColor.toArgbLong(),
                                                    strokeWidthDp = activeBrushWidthDp,
                                                    points = inProgressStroke
                                                )
                                            )
                                        }
                                        inProgressStroke = emptyList()
                                    },
                                    onDragCancel = { inProgressStroke = emptyList() }
                                )
                            }
                        } else Modifier
                    )
            ) {
                fun drawSingleStroke(stroke: EditorDrawStroke) {
                    if (stroke.points.size < 2) return
                    val path = Path().apply {
                        moveTo(stroke.points.first().x * size.width, stroke.points.first().y * size.height)
                        for (i in 1 until stroke.points.size) {
                            val prev = stroke.points[i - 1]
                            val cur = stroke.points[i]
                            val midX = ((prev.x + cur.x) / 2f) * size.width
                            val midY = ((prev.y + cur.y) / 2f) * size.height
                            quadraticTo(prev.x * size.width, prev.y * size.height, midX, midY)
                        }
                        val last = stroke.points.last()
                        lineTo(last.x * size.width, last.y * size.height)
                    }
                    val strokePx = stroke.strokeWidthDp.dp.toPx()
                    val color = Color(stroke.colorArgb.toInt())
                    when (stroke.brush) {
                        DrawBrushType.PEN -> {
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                        DrawBrushType.MARKER -> {
                            drawPath(
                                path = path,
                                color = color.copy(alpha = 0.52f),
                                style = Stroke(width = strokePx * 2f, cap = StrokeCap.Square, join = StrokeJoin.Bevel)
                            )
                        }
                        DrawBrushType.NEON -> {
                            drawPath(
                                path = path,
                                color = color.copy(alpha = 0.30f),
                                style = Stroke(width = strokePx * 2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                            drawPath(
                                path = path,
                                color = color.copy(alpha = 0.65f),
                                style = Stroke(width = strokePx * 1.55f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                            drawPath(
                                path = path,
                                color = Color.White,
                                style = Stroke(width = strokePx * 0.65f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                        DrawBrushType.ERASER -> {
                            drawPath(
                                path = path,
                                color = Color.Transparent,
                                style = Stroke(width = strokePx * 2.3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                blendMode = BlendMode.Clear
                            )
                        }
                    }
                }

                snapshot.drawStrokes.forEach { drawSingleStroke(it) }
                if (inProgressStroke.size >= 2) {
                    drawSingleStroke(
                        EditorDrawStroke(
                            brush = activeBrush,
                            colorArgb = activeBrushColor.toArgbLong(),
                            strokeWidthDp = activeBrushWidthDp,
                            points = inProgressStroke
                        )
                    )
                }
            }

            // 4. Snapping Guides (visible while dragging overlays near center / safe margins)
            if (isDraggingOverlay) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val guideColor = Color(0xFF38BDF8)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                    if (snapVerticalCenter) {
                        drawLine(guideColor, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 2.dp.toPx())
                    }
                    if (snapHorizontalCenter) {
                        drawLine(guideColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 2.dp.toPx())
                    }
                    // Subtle safe-zone boundary guides
                    val mX = size.width * 0.08f
                    val mY = size.height * 0.12f
                    drawRect(
                        color = Color.White.copy(alpha = 0.22f),
                        topLeft = Offset(mX, mY),
                        size = androidx.compose.ui.geometry.Size(size.width - 2 * mX, size.height - 2 * mY),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = dash)
                    )
                }
            }

            // 5. Draggable, Pinchable, Rotatable Stickers
            snapshot.stickerItems.forEach { sticker ->
                TransformableOverlayWrapper(
                    id = sticker.id,
                    xFraction = sticker.xFraction,
                    yFraction = sticker.yFraction,
                    scale = sticker.scale,
                    rotationDeg = sticker.rotationDeg,
                    canvasSize = canvasSize,
                    isSelected = selectedOverlayId == sticker.id,
                    enabled = !isDrawMode,
                    onTap = { onSelectOverlay(sticker.id) },
                    onDoubleTap = { onSelectOverlay(sticker.id) },
                    onDragStateChanged = { dragging, overTrash, snapV, snapH ->
                        isDraggingOverlay = dragging
                        isOverTrash = overTrash
                        snapVerticalCenter = snapV
                        snapHorizontalCenter = snapH
                        if ((snapV && !lastSnapV) || (snapH && !lastSnapH)) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        lastSnapV = snapV
                        lastSnapH = snapH
                    },
                    onCommitTransform = { x, y, sc, rot ->
                        onUpdateStickerTransform(sticker.id, x, y, sc, rot)
                    },
                    onDeleteDrop = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDeleteStickerItem(sticker.id)
                    }
                ) {
                    RichStickerRenderer(sticker = sticker)
                }
            }

            // 6. Draggable, Pinchable, Rotatable Text Overlays (16 Fonts + Animations)
            snapshot.textItems.forEach { textItem ->
                TransformableOverlayWrapper(
                    id = textItem.id,
                    xFraction = textItem.xFraction,
                    yFraction = textItem.yFraction,
                    scale = textItem.scale,
                    rotationDeg = textItem.rotationDeg,
                    canvasSize = canvasSize,
                    isSelected = selectedOverlayId == textItem.id,
                    enabled = !isDrawMode,
                    onTap = { onSelectOverlay(textItem.id) },
                    onDoubleTap = { onEditTextItem(textItem) },
                    onDragStateChanged = { dragging, overTrash, snapV, snapH ->
                        isDraggingOverlay = dragging
                        isOverTrash = overTrash
                        snapVerticalCenter = snapV
                        snapHorizontalCenter = snapH
                        if ((snapV && !lastSnapV) || (snapH && !lastSnapH)) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        lastSnapV = snapV
                        lastSnapH = snapH
                    },
                    onCommitTransform = { x, y, sc, rot ->
                        onUpdateTextTransform(textItem.id, x, y, sc, rot)
                    },
                    onDeleteDrop = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDeleteTextItem(textItem.id)
                    }
                ) {
                    AnimatedEditorTextRenderer(item = textItem)
                }
            }

            // 7. Live Synced Auto-Captions Overlay (Hindi + English)
            if (snapshot.captionsEnabled && snapshot.captions.isNotEmpty()) {
                val totalDurMs = snapshot.clips.sumOf { it.effectiveDurationMs }.coerceAtLeast(1000L)
                val currentMs = (playheadFraction * totalDurMs).toLong()
                val activeCaption = snapshot.captions.firstOrNull { currentMs in it.startMs..it.endMs }
                    ?: snapshot.captions.firstOrNull()
                if (activeCaption != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 52.dp, start = 20.dp, end = 20.dp)
                    ) {
                        StyledCaptionOverlay(segment = activeCaption, style = snapshot.captionStyle)
                    }
                }
            }

            // 8. Reverse Playback Indicator Badge
            if (activeClip.isReversed) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.7f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.FastRewind, contentDescription = "Reversed", tint = Color(0xFFFFD54F), modifier = Modifier.size(14.dp))
                        Text("REVERSE", color = Color(0xFFFFD54F), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            // 9. Swipe Filter Name Pill HUD
            AnimatedVisibility(
                visible = activeFilterBanner != null,
                enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.9f),
                exit = fadeOut(tween(220)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = activeFilterBanner.orEmpty(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }

            // 10. Drag-to-Trash Delete Zone (animates in while dragging any overlay)
            AnimatedVisibility(
                visible = isDraggingOverlay,
                enter = fadeIn(tween(200)) + scaleIn(tween(220), initialScale = 0.75f),
                exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
            ) {
                val trashScale by animateFloatAsState(
                    targetValue = if (isOverTrash) 1.28f else 1f,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
                    label = "trash_scale"
                )
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer {
                            scaleX = trashScale
                            scaleY = trashScale
                        }
                        .clip(CircleShape)
                        .background(if (isOverTrash) Color(0xFFEF4444) else Color.Black.copy(alpha = 0.72f))
                        .border(2.dp, if (isOverTrash) Color.White else Color.White.copy(alpha = 0.5f), CircleShape)
                        .testTag("editor_trash_zone"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Drag here to delete",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPreviewLayer(
    clip: EditorClip,
    isPlaying: Boolean,
    externalPlayheadFraction: Float,
    filter: EditorFilterPreset,
    adjustments: ColorAdjustments,
    onPlayheadTick: (Float, Long) -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember(clip.uriString) {
        ExoPlayerUtils.createExoPlayer(context).apply {
            var playUrl = clip.uriString
            if (playUrl.contains("drive.google.com/uc") && !playUrl.contains("confirm=t")) {
                playUrl += "&confirm=t"
            }
            setMediaItem(MediaItem.fromUri(Uri.parse(playUrl)))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = isPlaying
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { ExoPlayerUtils.safeRelease(exoPlayer) }
    }

    LaunchedEffect(clip.speed, clip.volume, clip.isMuted, isPlaying, clip.isReversed) {
        exoPlayer.setPlaybackParameters(PlaybackParameters(clip.speed.coerceIn(0.3f, 3.0f)))
        exoPlayer.volume = if (clip.isMuted) 0f else clip.volume.coerceIn(0f, 1f)
        if (isPlaying && !clip.isReversed) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Playhead loop & trim boundary enforcement (plus smooth reverse stepping when reversed)
    LaunchedEffect(exoPlayer, clip.trimStartFraction, clip.trimEndFraction, isPlaying, clip.isReversed, clip.speed) {
        while (true) {
            val dur = exoPlayer.duration.takeIf { it > 0L } ?: clip.durationMs
            val startMs = (dur * clip.trimStartFraction).toLong()
            val endMs = (dur * clip.trimEndFraction).toLong().coerceAtLeast(startMs + 300L)
            if (clip.isReversed && isPlaying) {
                val cur = exoPlayer.currentPosition
                val step = (90L * clip.speed).toLong().coerceAtLeast(35L)
                val prev = if (cur - step <= startMs) endMs else cur - step
                exoPlayer.seekTo(prev)
                val frac = ((prev - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                onPlayheadTick(frac, dur)
                delay(90)
            } else {
                val cur = exoPlayer.currentPosition
                if (cur < startMs || cur >= endMs) {
                    exoPlayer.seekTo(startMs)
                }
                val frac = ((cur - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                onPlayheadTick(frac, dur)
                delay(50)
            }
        }
    }

    // Respond to manual playhead scrub jumps
    LaunchedEffect(externalPlayheadFraction) {
        if (!isPlaying) {
            val dur = exoPlayer.duration.takeIf { it > 0L } ?: clip.durationMs
            val startMs = (dur * clip.trimStartFraction).toLong()
            val endMs = (dur * clip.trimEndFraction).toLong().coerceAtLeast(startMs + 300L)
            val targetMs = startMs + ((endMs - startMs) * externalPlayheadFraction).toLong()
            if (abs(exoPlayer.currentPosition - targetMs) > 120L) {
                exoPlayer.seekTo(targetMs)
            }
        }
    }

    val brightnessAlpha = (filter.brightnessDelta + adjustments.brightness).coerceIn(-0.45f, 0.45f)

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                ExoPlayerUtils.createTexturePlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            update = { view ->
                if (view.player != exoPlayer) view.player = exoPlayer
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = clip.rotationDeg
                    scaleX = if (clip.flipHorizontal) -1f else 1f
                    scaleY = if (clip.flipVertical) -1f else 1f
                }
        )
        if (abs(brightnessAlpha) > 0.02f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (brightnessAlpha > 0f) Color.White.copy(alpha = brightnessAlpha * 0.45f)
                        else Color.Black.copy(alpha = -brightnessAlpha * 0.65f)
                    )
            )
        }
    }
}

@Composable
private fun PhotoPreviewLayer(
    clip: EditorClip,
    colorMatrix: ColorMatrix,
    filter: EditorFilterPreset,
    adjustments: ColorAdjustments,
    lowEndMode: Boolean
) {
    val context = LocalContext.current
    val imageRequest = remember(clip.uriString, lowEndMode) {
        ImageRequest.Builder(context)
            .data(Uri.parse(clip.uriString))
            .apply {
                if (lowEndMode) size(Size(720, 1280))
            }
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = imageRequest,
        contentDescription = "Photo preview with ${filter.name} filter",
        contentScale = ContentScale.Crop,
        colorFilter = ColorFilter.colorMatrix(colorMatrix),
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationZ = clip.rotationDeg
                scaleX = if (clip.flipHorizontal) -1f else 1f
                scaleY = if (clip.flipVertical) -1f else 1f
            }
    )
}

/**
 * Reusable two-finger transform wrapper (drag, pinch zoom, rotate) with snapping guides
 * and drag-to-trash deletion.
 */
@Composable
private fun TransformableOverlayWrapper(
    id: String,
    xFraction: Float,
    yFraction: Float,
    scale: Float,
    rotationDeg: Float,
    canvasSize: IntSize,
    isSelected: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onDragStateChanged: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onCommitTransform: (Float, Float, Float, Float) -> Unit,
    onDeleteDrop: () -> Unit,
    content: @Composable () -> Unit
) {
    var curX by remember(id, xFraction) { mutableFloatStateOf(xFraction) }
    var curY by remember(id, yFraction) { mutableFloatStateOf(yFraction) }
    var curScale by remember(id, scale) { mutableFloatStateOf(scale) }
    var curRot by remember(id, rotationDeg) { mutableFloatStateOf(rotationDeg) }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }

    val w = canvasSize.width.coerceAtLeast(1).toFloat()
    val h = canvasSize.height.coerceAtLeast(1).toFloat()
    val px = (curX * w - itemSize.width / 2f).roundToInt()
    val py = (curY * h - itemSize.height / 2f).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(px, py) }
            .onSizeChanged { itemSize = it }
            .graphicsLayer {
                scaleX = curScale
                scaleY = curScale
                rotationZ = curRot
            }
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(10.dp))
                } else Modifier
            )
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(id) {
                            detectTapGestures(
                                onTap = { onTap() },
                                onDoubleTap = { onDoubleTap() }
                            )
                        }
                        .pointerInput(id, canvasSize) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                val nextX = (curX + pan.x / w).coerceIn(0.05f, 0.95f)
                                val nextY = (curY + pan.y / h).coerceIn(0.05f, 0.96f)
                                // Snap gently to center lines within 2.5% threshold
                                val snappedV = abs(nextX - 0.5f) < 0.025f
                                val snappedH = abs(nextY - 0.5f) < 0.025f
                                curX = if (snappedV) 0.5f else nextX
                                curY = if (snappedH) 0.5f else nextY
                                curScale = (curScale * zoom).coerceIn(0.45f, 3.2f)
                                curRot = (curRot + rotation) % 360f
                                val overTrash = curY > 0.85f && abs(curX - 0.5f) < 0.16f
                                onDragStateChanged(true, overTrash, snappedV, snappedH)
                                if (overTrash) {
                                    onDragStateChanged(false, false, false, false)
                                    onDeleteDrop()
                                } else {
                                    onCommitTransform(curX, curY, curScale, curRot)
                                }
                            }
                        }
                        .pointerInput(id, canvasSize) {
                            // Ensure drag guide hides cleanly when pointer lifts
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.all { !it.pressed }) {
                                        onDragStateChanged(false, false, false, false)
                                    }
                                }
                            }
                        }
                } else Modifier
            )
            .padding(4.dp)
    ) {
        content()
    }
}

/**
 * Renders animated text with bundled Google Font (Hindi/English), alignment, background chip,
 * outline, and live text animation styles.
 */
@Composable
private fun AnimatedEditorTextRenderer(item: EditorTextItem) {
    val fontOpt = remember(item.fontKey) { findEditorFont(item.fontKey) }
    val textColor = Color(item.colorArgb.toInt())
    val bgColor = Color(item.bgColorArgb.toInt())
    val align = when (item.alignment) {
        TextAlignMode.START -> TextAlign.Start
        TextAlignMode.CENTER -> TextAlign.Center
        TextAlignMode.END -> TextAlign.End
    }

    val infiniteTransition = rememberInfiniteTransition(label = "text_anim_${item.id}")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val slideOffset by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "slide"
    )

    var typewriterCount by remember(item.text, item.animStyle) { mutableIntStateOf(item.text.length) }
    LaunchedEffect(item.text, item.animStyle) {
        if (item.animStyle == TextAnimStyle.TYPEWRITER && item.text.isNotEmpty()) {
            while (true) {
                for (i in 1..item.text.length) {
                    typewriterCount = i
                    delay(85)
                }
                delay(1200)
            }
        } else {
            typewriterCount = item.text.length
        }
    }

    val displayedText = if (item.animStyle == TextAnimStyle.TYPEWRITER) {
        item.text.take(typewriterCount.coerceAtLeast(1))
    } else item.text

    val animModifier = when (item.animStyle) {
        TextAnimStyle.BOUNCE -> Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }
        TextAnimStyle.FADE_POP -> Modifier.alpha(0.55f + (pulse - 0.92f) * 2.8f)
        TextAnimStyle.SLIDE_UP -> Modifier.graphicsLayer { translationY = slideOffset }
        else -> Modifier
    }

    val chipModifier = when (item.bgStyle) {
        TextBgStyle.NONE -> Modifier
        TextBgStyle.SOLID_CHIP -> Modifier.background(bgColor, RoundedCornerShape(10.dp))
        TextBgStyle.FROSTED -> Modifier
            .background(bgColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
        TextBgStyle.OUTLINE_ONLY -> Modifier.border(2.dp, textColor, RoundedCornerShape(10.dp))
        TextBgStyle.GRADIENT_PILL -> Modifier.background(
            Brush.horizontalGradient(listOf(Color(0xFFEE1D52), Color(0xFFFF6D00))),
            RoundedCornerShape(14.dp)
        )
    }

    Box(
        modifier = animModifier
            .then(chipModifier)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (item.hasOutline || item.animStyle == TextAnimStyle.GLOW_PULSE) {
            Text(
                text = displayedText,
                fontFamily = fontOpt.fontFamily,
                fontSize = item.fontSizeSp.sp,
                textAlign = align,
                style = TextStyle(
                    color = if (item.animStyle == TextAnimStyle.GLOW_PULSE) Color(0xFF38BDF8).copy(alpha = 0.75f) else Color.Black,
                    drawStyle = Stroke(width = if (item.animStyle == TextAnimStyle.GLOW_PULSE) 9f else 5f)
                )
            )
        }
        Text(
            text = displayedText,
            color = textColor,
            fontFamily = fontOpt.fontFamily,
            fontSize = item.fontSizeSp.sp,
            textAlign = align,
            lineHeight = (item.fontSizeSp * 1.25f).sp
        )
    }
}

/**
 * Renders all 10 Instagram-grade Sticker types cleanly on the canvas.
 */
@Composable
private fun RichStickerRenderer(sticker: EditorStickerItem) {
    val accent = Color(sticker.accentColorArgb.toInt())
    when (sticker.kind) {
        StickerKind.EMOJI -> {
            Text(text = sticker.primaryText, fontSize = 46.sp)
        }
        StickerKind.GIF -> {
            AsyncImage(
                model = sticker.mediaUrl.ifBlank { sticker.primaryText },
                contentDescription = "GIF Sticker",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(116.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        StickerKind.LOCATION -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.94f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFEE1D52), modifier = Modifier.size(16.dp))
                    Text(sticker.primaryText.uppercase(), color = Color(0xFF111827), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        StickerKind.MENTION -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFF58529), Color(0xFFDD2A7B), Color(0xFF8134AF))))
            ) {
                Text(
                    text = "@${sticker.primaryText.removePrefix("@")}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
        StickerKind.HASHTAG -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.94f)
            ) {
                Text(
                    text = "#${sticker.primaryText.removePrefix("#")}",
                    color = Color(0xFF2563EB),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
        StickerKind.POLL -> {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF151520).copy(alpha = 0.94f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.width(196.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(sticker.primaryText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    val opts = sticker.extraOptions.ifEmpty { listOf("Yes 🔥", "No 🙅") }
                    opts.take(3).forEach { opt ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(opt, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        StickerKind.QUESTION -> {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier.width(196.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(sticker.primaryText, color = Color(0xFF111827), fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF3F4F6))
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Type something...", color = Color(0xFF6B7280), fontSize = 11.sp)
                    }
                }
            }
        }
        StickerKind.COUNTDOWN -> {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .width(196.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(sticker.primaryText.uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("02D", "14H", "30M").forEach { unit ->
                            Surface(shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.35f)) {
                                Text(unit, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                    if (sticker.secondaryText.isNotBlank()) {
                        Text(sticker.secondaryText, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
                    }
                }
            }
        }
        StickerKind.LINK -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.94f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                    Text(sticker.primaryText, color = Color(0xFF2563EB), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        StickerKind.MUSIC -> {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.78f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
                modifier = Modifier.widthIn(min = 160.dp, max = 230.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (sticker.mediaUrl.isNotBlank()) {
                        AsyncImage(
                            model = sticker.mediaUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sticker.primaryText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (sticker.secondaryText.isNotBlank()) {
                            Text(sticker.secondaryText, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StyledCaptionOverlay(segment: CaptionSegment, style: CaptionStyle) {
    val hindiFont = remember { findEditorFont("noto_devanagari").fontFamily }
    when (style) {
        CaptionStyle.CLASSIC_BOX -> {
            Surface(shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.72f)) {
                Text(
                    text = segment.text,
                    color = Color.White,
                    fontFamily = hindiFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        CaptionStyle.BOLD_POP -> {
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFD54F)) {
                Text(
                    text = segment.text,
                    color = Color.Black,
                    fontFamily = hindiFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
        CaptionStyle.KARAOKE_HIGHLIGHT -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEE1D52).copy(alpha = 0.92f),
                border = BorderStroke(1.5.dp, Color.White)
            ) {
                Text(
                    text = segment.text,
                    color = Color.White,
                    fontFamily = hindiFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
        CaptionStyle.MINIMAL_OUTLINE -> {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = segment.text,
                    fontFamily = hindiFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    style = TextStyle(color = Color.Black, drawStyle = Stroke(width = 6f))
                )
                Text(
                    text = segment.text,
                    color = Color.White,
                    fontFamily = hindiFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
        CaptionStyle.HINDI_CINEMA -> {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Text(
                    text = segment.text,
                    color = Color(0xFFFFF59D),
                    fontFamily = hindiFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
        }
    }
}

fun Color.toArgbLong(): Long {
    val a = (alpha * 255f).roundToInt().coerceIn(0, 255).toLong()
    val r = (red * 255f).roundToInt().coerceIn(0, 255).toLong()
    val g = (green * 255f).roundToInt().coerceIn(0, 255).toLong()
    val b = (blue * 255f).roundToInt().coerceIn(0, 255).toLong()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
