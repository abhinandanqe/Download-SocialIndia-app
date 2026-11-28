package com.example.ui

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.api.ITunesTrack
import com.example.model.StorySticker
import com.example.utils.ExoPlayerUtils
import com.example.utils.SoundManager
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun StoryInteractiveCanvas(
    mediaUri: Uri?,
    isVideo: Boolean,
    canvasGradient: Brush,
    activeFilterTint: Color,
    drawingStrokes: List<DrawnStroke>,
    textElements: List<StoryTextElement>,
    stickerElements: List<StoryStickerElement>,
    selectedMusic: ITunesTrack?,
    isMusicPlaying: Boolean,
    onToggleMusicPlay: () -> Unit,
    onRemoveMusic: () -> Unit,
    onEditTextElement: (StoryTextElement) -> Unit,
    onDeleteTextElement: (String) -> Unit,
    onDeleteStickerElement: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isDraggingAnyElement by remember { mutableStateOf(false) }
    var trashHovered by remember { mutableStateOf(false) }

    // Video player if media is video
    val videoPlayer = remember(mediaUri, isVideo) {
        if (isVideo && mediaUri != null) {
            try {
                ExoPlayerUtils.createExoPlayer(context).apply {
                    repeatMode = Player.REPEAT_MODE_ALL
                    setMediaItem(MediaItem.fromUri(mediaUri))
                    prepare()
                    playWhenReady = true
                }
            } catch (e: Exception) {
                null
            }
        } else null
    }

    DisposableEffect(videoPlayer) {
        onDispose {
            ExoPlayerUtils.safeRelease(videoPlayer)
        }
    }

    // Two-finger pinch-to-zoom, pan & rotation state on base photo/video media
    var mediaScale by remember(mediaUri) { mutableFloatStateOf(1f) }
    var mediaRotation by remember(mediaUri) { mutableFloatStateOf(0f) }
    var mediaOffsetX by remember(mediaUri) { mutableFloatStateOf(0f) }
    var mediaOffsetY by remember(mediaUri) { mutableFloatStateOf(0f) }
    var isFramedMode by remember(mediaUri) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(canvasGradient)
            .pointerInput(mediaUri) {
                if (mediaUri != null) {
                    detectTransformGestures { _, pan, zoom, rotationChange ->
                        mediaScale = (mediaScale * zoom).coerceIn(0.45f, 4.0f)
                        mediaRotation += rotationChange
                        mediaOffsetX += pan.x
                        mediaOffsetY += pan.y
                    }
                }
            }
    ) {
        // Base Media or Canvas Gradient
        if (mediaUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = mediaScale * (if (isFramedMode) 0.80f else 1f)
                        scaleY = mediaScale * (if (isFramedMode) 0.80f else 1f)
                        rotationZ = mediaRotation
                        translationX = mediaOffsetX
                        translationY = mediaOffsetY
                    }
                    .clip(if (isFramedMode) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp))
                    .then(
                        if (isFramedMode) Modifier.border(2.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                        else Modifier
                    )
            ) {
                if (isVideo && videoPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                player = videoPlayer
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = mediaUri,
                        contentDescription = "Story Media",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Quick Framed vs Full-Screen / Reset Gesture Pill
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 92.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .clickable {
                        isFramedMode = !isFramedMode
                        mediaScale = 1f
                        mediaRotation = 0f
                        mediaOffsetX = 0f
                        mediaOffsetY = 0f
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isFramedMode) Icons.Default.Fullscreen else Icons.Default.CropFree,
                    contentDescription = "Toggle Frame",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isFramedMode) "Full Screen" else "Framed Card",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            // Gradient canvas for text/sticker-only stories
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(canvasGradient)
            )
        }

        // Active Color Filter Tint Overlay
        if (activeFilterTint != Color.Transparent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(activeFilterTint)
            )
        }

        // Drawing Layer
        if (drawingStrokes.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (stroke in drawingStrokes) {
                    if (stroke.points.size > 1) {
                        val path = Path().apply {
                            moveTo(stroke.points.first().x, stroke.points.first().y)
                            for (i in 1 until stroke.points.size) {
                                lineTo(stroke.points[i].x, stroke.points[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = stroke.color,
                            style = Stroke(
                                width = stroke.strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    } else if (stroke.points.size == 1) {
                        drawCircle(
                            color = stroke.color,
                            radius = stroke.strokeWidth / 2,
                            center = stroke.points.first()
                        )
                    }
                }
            }
        }

        // Interactive Text Elements Layer
        textElements.forEach { item ->
            key(item.id) {
                InteractiveTextItem(
                    item = item,
                    onTap = { onEditTextElement(item) },
                    onDragStart = { isDraggingAnyElement = true },
                    onDragEnd = { inTrash ->
                        isDraggingAnyElement = false
                        trashHovered = false
                        if (inTrash) {
                            SoundManager.playClickSound()
                            onDeleteTextElement(item.id)
                        }
                    },
                    onTrashHoverChanged = { trashHovered = it }
                )
            }
        }

        // Interactive Sticker Elements Layer
        stickerElements.forEach { item ->
            key(item.id) {
                InteractiveStickerItem(
                    item = item,
                    onDragStart = { isDraggingAnyElement = true },
                    onDragEnd = { inTrash ->
                        isDraggingAnyElement = false
                        trashHovered = false
                        if (inTrash) {
                            SoundManager.playClickSound()
                            onDeleteStickerElement(item.id)
                        }
                    },
                    onTrashHoverChanged = { trashHovered = it }
                )
            }
        }

        // Floating Music Badge (if track selected)
        if (selectedMusic != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp)
            ) {
                StoryMusicBadge(
                    track = selectedMusic,
                    isPlaying = isMusicPlaying,
                    onTogglePlay = onToggleMusicPlay,
                    onRemove = onRemoveMusic
                )
            }
        }

        // Trash Zone at Bottom Center (Visible when dragging any element)
        AnimatedVisibility(
            visible = isDraggingAnyElement,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            val scale by animateFloatAsState(if (trashHovered) 1.25f else 1.0f)
            val bgColor = if (trashHovered) Color(0xFFEE1D52) else Color.Black.copy(alpha = 0.7f)

            Box(
                modifier = Modifier
                    .scale(scale)
                    .clip(CircleShape)
                    .background(bgColor)
                    .border(2.dp, Color.White, CircleShape)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Item",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * Draggable, scalable, rotatable Text element
 */
@Composable
private fun BoxScope.InteractiveTextItem(
    item: StoryTextElement,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: (inTrash: Boolean) -> Unit,
    onTrashHoverChanged: (Boolean) -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(item.offsetX) }
    var offsetY by remember { mutableFloatStateOf(item.offsetY) }
    var scale by remember { mutableFloatStateOf(item.scale) }
    var rotation by remember { mutableFloatStateOf(item.rotation) }

    val fonts = StoryFont.values()
    val font = fonts[item.fontIndex % fonts.size]

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .scale(scale)
            .rotate(rotation)
            .pointerInput(item.id) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(item.id) {
                detectTransformGestures(
                    onGesture = { _, pan, zoom, rotate ->
                        offsetX += pan.x
                        offsetY += pan.y
                        scale = (scale * zoom).coerceIn(0.5f, 4f)
                        rotation += rotate

                        item.offsetX = offsetX
                        item.offsetY = offsetY
                        item.scale = scale
                        item.rotation = rotation

                        val isNearTrash = offsetY > 320f
                        onTrashHoverChanged(isNearTrash)
                    }
                )
            }
            .pointerInput(item.id) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                        item.offsetX = offsetX
                        item.offsetY = offsetY

                        val isNearTrash = offsetY > 320f
                        onTrashHoverChanged(isNearTrash)
                    },
                    onDragEnd = {
                        val inTrash = offsetY > 320f
                        onDragEnd(inTrash)
                    },
                    onDragCancel = {
                        onDragEnd(false)
                    }
                )
            }
            .then(
                if (item.backgroundColor != Color.Transparent) {
                    Modifier
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(item.backgroundColor)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                } else {
                    Modifier.padding(8.dp)
                }
            )
    ) {
        Text(
            text = item.text,
            style = TextStyle(
                color = item.color,
                fontSize = 28.sp,
                fontFamily = font.fontFamily,
                fontWeight = font.fontWeight,
                fontStyle = font.fontStyle,
                textAlign = item.alignment,
                shadow = if (font == StoryFont.NEON) {
                    Shadow(color = item.color.copy(alpha = 0.8f), blurRadius = 14f)
                } else {
                    Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(2f, 2f), blurRadius = 4f)
                }
            )
        )
    }
}

/**
 * Draggable, scalable, rotatable Sticker element
 */
@Composable
private fun BoxScope.InteractiveStickerItem(
    item: StoryStickerElement,
    onDragStart: () -> Unit,
    onDragEnd: (inTrash: Boolean) -> Unit,
    onTrashHoverChanged: (Boolean) -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(item.offsetX) }
    var offsetY by remember { mutableFloatStateOf(item.offsetY) }
    var scale by remember { mutableFloatStateOf(item.scale) }
    var rotation by remember { mutableFloatStateOf(item.rotation) }

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .scale(scale)
            .rotate(rotation)
            .pointerInput(item.id) {
                detectTransformGestures(
                    onGesture = { _, pan, zoom, rotate ->
                        offsetX += pan.x
                        offsetY += pan.y
                        scale = (scale * zoom).coerceIn(0.5f, 3.5f)
                        rotation += rotate

                        item.offsetX = offsetX
                        item.offsetY = offsetY
                        item.scale = scale
                        item.rotation = rotation

                        val isNearTrash = offsetY > 320f
                        onTrashHoverChanged(isNearTrash)
                    }
                )
            }
            .pointerInput(item.id) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                        item.offsetX = offsetX
                        item.offsetY = offsetY

                        val isNearTrash = offsetY > 320f
                        onTrashHoverChanged(isNearTrash)
                    },
                    onDragEnd = {
                        val inTrash = offsetY > 320f
                        onDragEnd(inTrash)
                    },
                    onDragCancel = {
                        onDragEnd(false)
                    }
                )
            }
    ) {
        if (!item.gifUrl.isNullOrEmpty()) {
            // Animated GIF Sticker
            AsyncImage(
                model = item.gifUrl,
                contentDescription = "GIF Sticker",
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        } else {
            // Rich Interactive Sticker Visual
            StoryStickerVisual(item.sticker)
        }
    }
}

/**
 * Visual rendering for each StorySticker type on creator canvas
 */
@Composable
fun StoryStickerVisual(sticker: StorySticker) {
    when (sticker.type) {
        "poll" -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.width(220.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = sticker.question,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val opt1 = sticker.options.getOrNull(0) ?: "Yes"
                        val opt2 = sticker.options.getOrNull(1) ?: "No"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2196F3).copy(alpha = 0.15f))
                                .border(1.5.dp, Color(0xFF2196F3), RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                opt1,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2196F3),
                                fontSize = 13.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2196F3).copy(alpha = 0.15f))
                                .border(1.5.dp, Color(0xFF2196F3), RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                opt2,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2196F3),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
        "question" -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.width(230.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = sticker.question.ifEmpty { "Ask me a question" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF3F4F6))
                            .padding(vertical = 10.dp, horizontal = 12.dp)
                    ) {
                        Text("Type something...", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
        "quiz" -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.width(230.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = sticker.question,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    sticker.options.forEachIndexed { i, opt ->
                        val isCorrect = sticker.correctOptionIndex == i
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCorrect) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFF3F4F6))
                                .border(1.dp, if (isCorrect) Color(0xFF4CAF50) else Color.Transparent, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = opt,
                                fontSize = 12.sp,
                                fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCorrect) Color(0xFF2E7D32) else Color.Black
                            )
                        }
                    }
                }
            }
        }
        "slider" -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.width(220.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = sticker.question,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFFF9800).copy(alpha = 0.3f))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, Color.LightGray, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(sticker.emoji.ifEmpty { "😍" }, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
        "add_yours" -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF10B981))
                    .border(2.dp, Color.White, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Column {
                        Text(
                            sticker.addYoursPrompt.ifEmpty { "Add Yours" },
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                        Text("Add Yours", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        "mention" -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.5.dp, Color(0xFFFF5722), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "@${sticker.mentionUsername.removePrefix("@")}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
        "location" -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.5.dp, Color(0xFF00BCD4), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF00BCD4), modifier = Modifier.size(16.dp))
                    Text(text = sticker.locationName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
        "hashtag" -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.5.dp, Color(0xFF3F51B5), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val tag = sticker.hashtag
                Text(
                    text = if (tag.startsWith("#")) tag else "#$tag",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
        "emoji" -> {
            Text(sticker.emoji.ifEmpty { "🔥" }, fontSize = 64.sp)
        }
        else -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(8.dp)
            ) {
                Text(sticker.question.ifEmpty { sticker.id }, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Instagram-style floating Music Pill
 */
@Composable
fun StoryMusicBadge(
    track: ITunesTrack,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val artUrl = track.artworkUrl.orEmpty()
            if (artUrl.isNotEmpty()) {
                AsyncImage(
                    model = artUrl,
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFFEE1D52),
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.clickable {
                    SoundManager.playClickSound()
                    onTogglePlay()
                }
            ) {
                Text(
                    text = track.name.orEmpty(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1
                )
                Text(
                    text = track.artistName.orEmpty(),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = {
                    SoundManager.playClickSound()
                    onTogglePlay()
                },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = {
                    SoundManager.playClickSound()
                    onRemove()
                },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove Track",
                    tint = Color.Gray,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
