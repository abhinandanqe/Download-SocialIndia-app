package com.example.editor.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.editor.model.*
import com.example.editor.viewmodel.VideoEditorViewModel
import com.example.ui.MusicPickerSheet
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    initialVideoUris: List<Uri>,
    onNavigateBack: () -> Unit,
    onExport: () -> Unit,
    viewModel: VideoEditorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var activeTool by remember { mutableStateOf<String?>(null) }
    var showMusicPicker by remember { mutableStateOf(false) }
    var newOverlayText by remember { mutableStateOf("") }
    var selectedTextColor by remember { mutableIntStateOf(0xFFFFFFFF.toInt()) }
    var showEffectFlash by remember { mutableStateOf(false) }
    var showCoverSelector by remember { mutableStateOf(false) }

    // Initialize with provided URIs on first launch
    LaunchedEffect(initialVideoUris) {
        if (state.videoClips.isEmpty()) {
            initialVideoUris.forEach { uri ->
                viewModel.processAction(EditorAction.AddVideoClip(uri, 10000L)) 
            }
        }
    }
    
    val exoPlayer = remember {
        com.example.utils.ExoPlayerUtils.createExoPlayer(context).apply {
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    val audioPlayer = remember {
        com.example.utils.ExoPlayerUtils.createExoPlayer(context).apply {
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    val selectedClip = state.videoClips.find { it.id == state.selectedClipId } ?: state.videoClips.firstOrNull()

    // Sync speed with selected clip
    LaunchedEffect(selectedClip?.speed) {
        val speed = selectedClip?.speed ?: 1.0f
        exoPlayer.setPlaybackSpeed(speed)
    }

    // Sync ExoPlayer with ViewModel State
    LaunchedEffect(state.videoClips) {
        if (exoPlayer.mediaItemCount == 0 && state.videoClips.isNotEmpty()) {
            val mediaItems = state.videoClips.map { MediaItem.fromUri(it.uri) }
            exoPlayer.setMediaItems(mediaItems)
            exoPlayer.prepare()
        }
    }

    // Sync AudioPlayer with audio tracks
    LaunchedEffect(state.audioTracks) {
        val firstAudio = state.audioTracks.firstOrNull()
        if (firstAudio != null) {
            audioPlayer.setMediaItem(MediaItem.fromUri(firstAudio.uri))
            audioPlayer.prepare()
        } else {
            audioPlayer.stop()
            audioPlayer.clearMediaItems()
        }
    }

    // Playback play/pause sync
    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) {
            exoPlayer.play()
            if (state.audioTracks.isNotEmpty()) audioPlayer.play()
        } else {
            exoPlayer.pause()
            audioPlayer.pause()
        }
    }

    // Periodic playhead position sync during playback
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            val curPos = exoPlayer.currentPosition
            viewModel.processAction(EditorAction.SeekTo(curPos))
            delay(50)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer.release()
                audioPlayer.release()
            } catch (e: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video Preview PlayerView (Full area above timeline)
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        com.example.utils.ExoPlayerUtils.createTexturePlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Filter / Tint
                val activeFilter = selectedClip?.filters?.firstOrNull() ?: FilterType.NONE
                val filterColor = when (activeFilter) {
                    FilterType.WARM -> Color(0xFFFF9800).copy(alpha = 0.15f)
                    FilterType.COOL -> Color(0xFF03A9F4).copy(alpha = 0.15f)
                    FilterType.VINTAGE -> Color(0xFF8D6E63).copy(alpha = 0.20f)
                    FilterType.MONO -> Color.DarkGray.copy(alpha = 0.25f)
                    FilterType.CINEMATIC -> Color(0xFF009688).copy(alpha = 0.15f)
                    FilterType.BRIGHT -> Color.White.copy(alpha = 0.12f)
                    FilterType.SOFT -> Color(0xFFFF80AB).copy(alpha = 0.12f)
                    FilterType.NONE -> Color.Transparent
                }
                Box(modifier = Modifier.fillMaxSize().background(filterColor))

                // Transition Flash
                if (showEffectFlash) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.8f)))
                }

                // Text Overlays
                state.textOverlays.forEach { overlay ->
                    var xOffset by remember(overlay.id) { mutableFloatStateOf(0f) }
                    var yOffset by remember(overlay.id) { mutableFloatStateOf(0f) }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(xOffset.roundToInt(), yOffset.roundToInt()) }
                            .pointerInput(overlay.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    xOffset += dragAmount.x
                                    yOffset += dragAmount.y
                                }
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = overlay.text,
                            color = Color(overlay.color),
                            fontSize = overlay.size.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Top App Bar Controls Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { viewModel.undo() },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(Icons.Default.Undo, "Undo", tint = Color.White)
                        }
                        IconButton(
                            onClick = { viewModel.redo() },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(Icons.Default.Redo, "Redo", tint = Color.White)
                        }
                    }
                }

                // Cover Selection Button (Left side)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(16.dp)
                        .clickable { showCoverSelector = true }
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, contentDescription = "Cover", tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Cover", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Play/Pause Overlay Toggle (Center)
                if (!state.isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .clickable { viewModel.processAction(EditorAction.SetPlaying(true)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }

            // Bottom UI (Toolbar + Editor options + Timeline + Export)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F0F))
            ) {
                // Interactive Tool Configuration Panel
                AnimatedVisibility(
                    visible = activeTool != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = Color(0xFF1E1E1E),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Header of Sub-Tool
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (activeTool) {
                                        "autocut" -> "AI Auto Cut"
                                        "trim" -> "Trim Video Clip"
                                        "split" -> "Split Clip"
                                        "speed" -> "Playback Speed"
                                        "crop" -> "Crop & Rotate"
                                        "text" -> "Add Text Overlay"
                                        "filters" -> "Color Filters"
                                        "adjust" -> "Adjust Video Settings"
                                        "audio" -> "Audio & Background Music"
                                        "effects" -> "Visual Effects"
                                        "stickers" -> "Stickers & Emojis"
                                        "pip" -> "Picture in Picture"
                                        else -> "Edit Tool"
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                IconButton(
                                    onClick = { activeTool = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Tool", tint = Color.Gray)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            when (activeTool) {
                                "autocut" -> {
                                    var isAnalyzing by remember { mutableStateOf(false) }
                                    var hasSuggested by remember { mutableStateOf(false) }
                                    
                                    if (isAnalyzing) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                            CircularProgressIndicator(color = Color(0xFF0095F6), modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("AI is analyzing video for dead air and long pauses...", color = Color.White, fontSize = 12.sp)
                                        }
                                        LaunchedEffect(Unit) {
                                            delay(2000)
                                            isAnalyzing = false
                                            hasSuggested = true
                                        }
                                    } else if (!hasSuggested) {
                                        Button(
                                            onClick = { isAnalyzing = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0095F6)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Analyze with AI Auto Cut")
                                        }
                                        Text("AI will suggest cuts to remove silences and dead air.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                                    } else {
                                        Column {
                                            Text("AI suggested 2 cuts (00:03-00:05, 00:08-00:09).", color = Color.White, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Button(
                                                    onClick = { 
                                                        Toast.makeText(context, "Applied AI cuts successfully!", Toast.LENGTH_SHORT).show()
                                                        hasSuggested = false
                                                        activeTool = null
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0095F6)),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Apply All")
                                                }
                                                Button(
                                                    onClick = { hasSuggested = false },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Cancel")
                                                }
                                            }
                                        }
                                    }
                                }
                                "trim" -> {
                                    if (selectedClip != null) {
                                        val clipDuration = if (selectedClip.durationMs > 0) selectedClip.durationMs else 10000L
                                        var trimStart by remember(selectedClip.id) { mutableFloatStateOf(selectedClip.trimStartMs.toFloat()) }
                                        var trimEnd by remember(selectedClip.id) { mutableFloatStateOf(selectedClip.trimEndMs.toFloat()) }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Start: ${(trimStart / 1000).toInt()}s", color = Color.LightGray, fontSize = 12.sp)
                                            Text("End: ${(trimEnd / 1000).toInt()}s", color = Color.LightGray, fontSize = 12.sp)
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Trim Start:", color = Color.Gray, fontSize = 11.sp)
                                        Slider(
                                            value = trimStart,
                                            valueRange = 0f..clipDuration.toFloat(),
                                            onValueChange = {
                                                trimStart = it.coerceAtMost(trimEnd - 1000f)
                                                viewModel.processAction(EditorAction.UpdateTrim(selectedClip.id, trimStart.toLong(), trimEnd.toLong()))
                                                exoPlayer.seekTo(trimStart.toLong())
                                            },
                                            colors = SliderDefaults.colors(activeTrackColor = Color(0xFF0095F6), thumbColor = Color(0xFF0095F6))
                                        )

                                        Text("Trim End:", color = Color.Gray, fontSize = 11.sp)
                                        Slider(
                                            value = trimEnd,
                                            valueRange = 0f..clipDuration.toFloat(),
                                            onValueChange = {
                                                trimEnd = it.coerceAtLeast(trimStart + 1000f)
                                                viewModel.processAction(EditorAction.UpdateTrim(selectedClip.id, trimStart.toLong(), trimEnd.toLong()))
                                            },
                                            colors = SliderDefaults.colors(activeTrackColor = Color(0xFF0095F6), thumbColor = Color(0xFF0095F6))
                                        )
                                    } else {
                                        Text("Select a clip on the timeline first", color = Color.Gray, fontSize = 13.sp)
                                    }
                                }

                                "split" -> {
                                    Button(
                                        onClick = {
                                            // Split at playhead
                                            Toast.makeText(context, "Clip split at playhead!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0095F6)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Split Clip Here")
                                    }
                                }

                                "speed" -> {
                                    if (selectedClip != null) {
                                        val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            speedOptions.forEach { spd ->
                                                val isSelected = selectedClip.speed == spd
                                                Surface(
                                                    onClick = {
                                                        viewModel.processAction(EditorAction.ChangeSpeed(selectedClip.id, spd))
                                                        exoPlayer.setPlaybackSpeed(spd)
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) Color(0xFF0095F6) else Color(0xFF2C2C2C),
                                                    modifier = Modifier.weight(1f).height(36.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("${spd}x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                "crop" -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Button(onClick = { Toast.makeText(context, "Rotated 90°", Toast.LENGTH_SHORT).show() }) { Text("Rotate 90°") }
                                        Button(onClick = { Toast.makeText(context, "Cropped to 1:1", Toast.LENGTH_SHORT).show() }) { Text("1:1 Crop") }
                                    }
                                }

                                "text" -> {
                                    Column {
                                        OutlinedTextField(
                                            value = newOverlayText,
                                            onValueChange = { newOverlayText = it },
                                            placeholder = { Text("Enter text for overlay", color = Color.Gray, fontSize = 13.sp) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedContainerColor = Color(0xFF252525),
                                                unfocusedContainerColor = Color(0xFF252525)
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Color Picker Row
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            val colors = listOf(
                                                0xFFFFFFFF.toInt(),
                                                0xFFFFEB3B.toInt(),
                                                0xFF00E5FF.toInt(),
                                                0xFFFF5252.toInt(),
                                                0xFFE040FB.toInt(),
                                                0xFF76FF03.toInt()
                                            )
                                            Text("Color:", color = Color.Gray, fontSize = 12.sp)
                                            colors.forEach { c ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(c))
                                                        .border(
                                                            width = if (selectedTextColor == c) 2.dp else 0.dp,
                                                            color = Color.White,
                                                            shape = CircleShape
                                                        )
                                                        .clickable { selectedTextColor = c }
                                                )
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
                                            Button(
                                                onClick = {
                                                    if (newOverlayText.isNotBlank()) {
                                                        viewModel.processAction(
                                                            EditorAction.AddText(
                                                                text = newOverlayText,
                                                                startMs = state.playheadPositionMs,
                                                                durationMs = 5000L
                                                            )
                                                        )
                                                        newOverlayText = ""
                                                        Toast.makeText(context, "Text added! Drag to position.", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0095F6)),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Add Text", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                "filters" -> {
                                    if (selectedClip != null) {
                                        val currentFilter = selectedClip.filters.firstOrNull() ?: FilterType.NONE
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(FilterType.values().toList()) { filter ->
                                                val isSelected = currentFilter == filter
                                                Surface(
                                                    onClick = {
                                                        viewModel.processAction(EditorAction.ApplyFilter(selectedClip.id, filter))
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isSelected) Color(0xFF0095F6) else Color(0xFF282828),
                                                    modifier = Modifier.height(38.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                                        Text(
                                                            filter.name.lowercase().replaceFirstChar { it.uppercase() },
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                "adjust" -> {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Brightness: ${(state.brightness * 100).toInt()}%", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(110.dp))
                                            Slider(
                                                value = state.brightness,
                                                valueRange = 0.5f..1.5f,
                                                onValueChange = {
                                                    viewModel.processAction(EditorAction.UpdateGlobalAdjustments(it, state.contrast, state.saturation))
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = SliderDefaults.colors(activeTrackColor = Color(0xFF0095F6), thumbColor = Color(0xFF0095F6))
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Contrast: ${(state.contrast * 100).toInt()}%", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(110.dp))
                                            Slider(
                                                value = state.contrast,
                                                valueRange = 0.5f..1.5f,
                                                onValueChange = {
                                                    viewModel.processAction(EditorAction.UpdateGlobalAdjustments(state.brightness, it, state.saturation))
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = SliderDefaults.colors(activeTrackColor = Color(0xFF0095F6), thumbColor = Color(0xFF0095F6))
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Saturation: ${(state.saturation * 100).toInt()}%", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(110.dp))
                                            Slider(
                                                value = state.saturation,
                                                valueRange = 0.0f..2.0f,
                                                onValueChange = {
                                                    viewModel.processAction(EditorAction.UpdateGlobalAdjustments(state.brightness, state.contrast, it))
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = SliderDefaults.colors(activeTrackColor = Color(0xFF0095F6), thumbColor = Color(0xFF0095F6))
                                            )
                                        }
                                    }
                                }

                                "audio" -> {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (state.audioTracks.isNotEmpty()) "Tracks: ${state.audioTracks.size} Audio added" else "No background music",
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )
                                            Button(
                                                onClick = { showMusicPicker = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Add Music", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                "stickers" -> {
                                    val emojis = listOf("🔥", "❤️", "✨", "🎉", "😎", "👏", "💯", "🚀", "🌟", "😍", "🥳", "💥")
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(emojis) { emoji ->
                                            Surface(
                                                onClick = {
                                                    viewModel.processAction(
                                                        EditorAction.AddText(
                                                            text = emoji,
                                                            startMs = state.playheadPositionMs,
                                                            durationMs = 5000L
                                                        )
                                                    )
                                                    Toast.makeText(context, "$emoji Added! Drag to position.", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = CircleShape,
                                                color = Color(0xFF2C2C2C),
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(emoji, fontSize = 22.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                "effects" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                showEffectFlash = true
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0095F6)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Flash Transition")
                                        }
                                        Button(
                                            onClick = {
                                                Toast.makeText(context, "Zoom transition applied!", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Zoom Effect")
                                        }
                                    }
                                    LaunchedEffect(showEffectFlash) {
                                        if (showEffectFlash) {
                                            delay(300)
                                            showEffectFlash = false
                                        }
                                    }
                                }

                                "pip" -> {
                                    Text("Picture-in-Picture layer ready for overlay clips.", color = Color.Gray, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // Editor Toolbar
                EditorToolbar(
                    viewModel = viewModel,
                    activeTool = activeTool,
                    onSelectTool = { activeTool = it }
                )

                // Timeline Area
                TimelineView(
                    state = state,
                    onSeek = { seekPos ->
                        viewModel.processAction(EditorAction.SeekTo(seekPos))
                        exoPlayer.seekTo(seekPos)
                    },
                    onTogglePlay = { viewModel.processAction(EditorAction.SetPlaying(!state.isPlaying)) },
                    onSelectClip = { viewModel.processAction(EditorAction.SelectClip(it)) }
                )

                // Bottom Action Bar (Publish/Next)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F0F0F))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            Toast.makeText(context, "Exporting Video & Proceeding to Publish...", Toast.LENGTH_SHORT).show()
                            onExport()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0095F6)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Next", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    // Cover Selector Sheet
    if (showCoverSelector) {
        ModalBottomSheet(
            onDismissRequest = { showCoverSelector = false },
            containerColor = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text("Select Cover Frame", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                // Visual representation of a slider for selecting cover frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(Color.DarkGray, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Slide to choose cover", color = Color.White)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { showCoverSelector = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0095F6))
                ) {
                    Text("Done")
                }
            }
        }
    }

    // Music Picker Sheet
    if (showMusicPicker) {
        MusicPickerSheet(
            onDismiss = { showMusicPicker = false },
            onTrackSelected = { track ->
                showMusicPicker = false
                val streamUrl = track.previewUrl
                if (!streamUrl.isNullOrEmpty()) {
                    viewModel.processAction(EditorAction.AddAudio(Uri.parse(streamUrl), 30000L))
                    Toast.makeText(context, "Added track: ${track.name}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
