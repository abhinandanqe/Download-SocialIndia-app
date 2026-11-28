package com.example.ui.editor

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.api.ITunesTrack
import com.example.ui.GifPickerBottomSheet
import com.example.ui.isVideoUri
import com.example.utils.ExoPlayerUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Full-Screen Dark Gesture-First Instagram-Style Pro Editor for Social India.
 *
 * Layout:
 * - Edge-to-edge dark canvas (9:16, 4:5, 1:1)
 * - Top bar: Close (X), Undo, Redo, Aspect Ratio pill, Next / Share
 * - Right-side vertical tool column: Text, Stickers, Music, Effects, Draw, More
 * - Bottom: Multi-clip timeline with playhead & pinch-to-zoom + Tool tabs
 * - 220-280ms animated bottom sheets for every tool + haptic feedback
 * - Auto-saving offline drafts, discard confirmation, background export with progress bar
 */
@Composable
fun InstagramProEditorScreen(
    context: Context,
    selectedMediaIsVideo: Boolean,
    selectedMediaUrl: String?,
    selectedMediaUris: List<Uri>,
    selectedMusic: ITunesTrack?,
    initialDraft: EditorProjectSnapshot? = null,
    onNavigateBack: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigateToMusicPicker: () -> Unit,
    onExportComplete: ((EditorExportResult) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val autoLowEnd = remember { EditorDeviceProfile.isLowEndDevice(context) }
    var lowEndMode by remember { mutableStateOf(autoLowEnd) }

    // Build initial project snapshot (either resuming offline draft or from selected media URIs)
    val initialSnapshot = remember(selectedMediaUris, selectedMediaUrl, initialDraft?.draftId) {
        if (initialDraft != null) {
            initialDraft
        } else {
            val uris = if (selectedMediaUris.isNotEmpty()) selectedMediaUris
            else listOfNotNull(selectedMediaUrl?.let { Uri.parse(it) })
            val clips = uris.map { uri ->
                val isVid = try { isVideoUri(context, uri) } catch (_: Exception) { selectedMediaIsVideo }
                EditorClip(
                    uriString = uri.toString(),
                    isVideo = isVid,
                    durationMs = if (isVid) 15_000L else 5_000L
                )
            }
            val musicCfg = selectedMusic?.let {
                MusicTrimConfig(
                    trackId = it.id ?: 0L,
                    name = it.name.orEmpty(),
                    artist = it.artistName.orEmpty(),
                    artworkUrl = it.artworkUrl.orEmpty(),
                    previewUrl = it.previewUrl.orEmpty()
                )
            }
            EditorProjectSnapshot(
                aspectRatio = if (selectedMediaIsVideo) AspectRatioPreset.RATIO_9_16 else AspectRatioPreset.RATIO_4_5,
                clips = clips,
                musicConfig = musicCfg
            )
        }
    }

    val history = remember(initialSnapshot.draftId) { EditorHistoryStack(initialSnapshot) }
    var snapshot by remember(initialSnapshot.draftId) { mutableStateOf(initialSnapshot) }
    var historyRevision by remember { mutableIntStateOf(0) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }

    fun applyChange(transform: (EditorProjectSnapshot) -> EditorProjectSnapshot) {
        val next = transform(snapshot)
        if (next != snapshot) {
            history.commit(next)
            snapshot = next
            historyRevision++
            hasUnsavedChanges = true
        }
    }

    // Keep external music picker selection synced into project snapshot
    LaunchedEffect(selectedMusic?.id, selectedMusic?.previewUrl) {
        if (selectedMusic != null && snapshot.musicConfig?.trackId != (selectedMusic.id ?: 0L)) {
            applyChange {
                it.copy(
                    musicConfig = MusicTrimConfig(
                        trackId = selectedMusic.id ?: 0L,
                        name = selectedMusic.name.orEmpty(),
                        artist = selectedMusic.artistName.orEmpty(),
                        artworkUrl = selectedMusic.artworkUrl.orEmpty(),
                        previewUrl = selectedMusic.previewUrl.orEmpty(),
                        startSec = 0f,
                        windowDurationSec = 15f
                    )
                )
            }
        }
    }

    // Auto-save offline draft smoothly (debounced 2s)
    var autoSavedToastVisible by remember { mutableStateOf(false) }
    LaunchedEffect(snapshot) {
        if (hasUnsavedChanges && snapshot.clips.isNotEmpty()) {
            delay(1800)
            OfflineEditorDraftStore.saveDraft(context, snapshot)
        }
    }

    // Active tool & sub-modal state
    var activeTool by remember { mutableStateOf(EditorTool.NONE) }
    var showMoreDrawer by remember { mutableStateOf(false) }
    var showGifPicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showBrandKitSheet by remember { mutableStateOf(false) }
    var showPublishTemplateSheet by remember { mutableStateOf(false) }
    var editingTextItem by remember { mutableStateOf<EditorTextItem?>(null) }
    var selectedOverlayId by remember { mutableStateOf<String?>(null) }

    // Playback & playhead state
    var isPlaying by remember { mutableStateOf(true) }
    var playheadFraction by remember { mutableFloatStateOf(0f) }

    // Drawing brush state
    var activeBrush by remember { mutableStateOf(DrawBrushType.PEN) }
    var activeBrushColor by remember { mutableStateOf(Color.White) }
    var activeBrushWidthDp by remember { mutableFloatStateOf(7f) }

    // Export state
    var exportState by remember { mutableStateOf<EditorExportEngine.ExportState>(EditorExportEngine.ExportState.Idle) }
    var exportJob by remember { mutableStateOf<Job?>(null) }

    // Add more clips launcher
    val addClipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            val newClips = uris.map { uri ->
                val isVid = try { isVideoUri(context, uri) } catch (_: Exception) { false }
                EditorClip(uriString = uri.toString(), isVideo = isVid, durationMs = if (isVid) 12_000L else 5_000L)
            }
            applyChange { it.copy(clips = it.clips + newClips) }
            Toast.makeText(context, "Added ${newClips.size} clip(s) to timeline", Toast.LENGTH_SHORT).show()
        }
    }

    // Synced Background Music ExoPlayer (respects 15-30s section start & end)
    val musicUrl = snapshot.musicConfig?.previewUrl?.takeIf { it.isNotBlank() }
    val musicPlayer = remember(musicUrl) {
        if (musicUrl != null) {
            try {
                ExoPlayerUtils.createExoPlayer(context, isMusic = true).apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(musicUrl)))
                    repeatMode = Player.REPEAT_MODE_ALL
                    prepare()
                }
            } catch (_: Exception) { null }
        } else null
    }
    DisposableEffect(musicPlayer) {
        onDispose { ExoPlayerUtils.safeRelease(musicPlayer) }
    }
    LaunchedEffect(isPlaying, musicPlayer, snapshot.musicConfig?.volume, snapshot.musicConfig?.startSec, snapshot.musicConfig?.windowDurationSec) {
        val cfg = snapshot.musicConfig
        if (musicPlayer != null && cfg != null) {
            musicPlayer.volume = cfg.volume
            if (isPlaying) musicPlayer.play() else musicPlayer.pause()
            while (isPlaying) {
                val startMs = (cfg.startSec * 1000f).toLong()
                val endMs = ((cfg.startSec + cfg.windowDurationSec) * 1000f).toLong()
                val dur = musicPlayer.duration.takeIf { it > 0L } ?: 30_000L
                val clampedStart = startMs.coerceIn(0L, (dur - 1000L).coerceAtLeast(0L))
                val clampedEnd = endMs.coerceAtMost(dur)
                if (musicPlayer.currentPosition < clampedStart || musicPlayer.currentPosition >= clampedEnd) {
                    musicPlayer.seekTo(clampedStart)
                }
                delay(250)
            }
        }
    }

    // Synced Voiceover ExoPlayer
    val voicePath = snapshot.voiceoverFilePath?.takeIf { File(it).exists() }
    val voicePlayer = remember(voicePath) {
        if (voicePath != null) {
            try {
                ExoPlayerUtils.createExoPlayer(context, isMusic = true).apply {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(File(voicePath))))
                    repeatMode = Player.REPEAT_MODE_ALL
                    prepare()
                }
            } catch (_: Exception) { null }
        } else null
    }
    DisposableEffect(voicePlayer) {
        onDispose { ExoPlayerUtils.safeRelease(voicePlayer) }
    }
    LaunchedEffect(isPlaying, voicePlayer, snapshot.voiceoverVolume) {
        voicePlayer?.volume = snapshot.voiceoverVolume
        if (isPlaying) voicePlayer?.play() else voicePlayer?.pause()
    }

    // Playhead timer for photo-only projects so captions & timeline stay live
    val activeClip = snapshot.clips.getOrNull(snapshot.selectedClipIndex) ?: snapshot.clips.firstOrNull()
    LaunchedEffect(isPlaying, activeClip?.id, activeClip?.isVideo, activeClip?.speed) {
        if (activeClip != null && !activeClip.isVideo && isPlaying) {
            val durMs = activeClip.effectiveDurationMs.coerceAtLeast(2000L)
            while (isPlaying) {
                delay(60)
                playheadFraction = (playheadFraction + (60f / durMs.toFloat())) % 1f
            }
        }
    }

    // Intercept system back to confirm discard/save draft
    BackHandler {
        if (activeTool != EditorTool.NONE) {
            activeTool = EditorTool.NONE
            editingTextItem = null
        } else if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }

    fun startBackgroundExport() {
        if (exportState is EditorExportEngine.ExportState.Exporting) return
        isPlaying = false
        exportJob = scope.launch {
            exportState = EditorExportEngine.ExportState.Exporting(0.05f, "Starting export...")
            val result = EditorExportEngine.exportProject(
                context = context,
                snapshot = snapshot,
                lowEndMode = lowEndMode,
                onProgress = { progress, stage ->
                    exportState = EditorExportEngine.ExportState.Exporting(progress, stage)
                }
            )
            result.onSuccess { exported ->
                exportState = EditorExportEngine.ExportState.Idle
                if (onExportComplete != null) {
                    onExportComplete(exported)
                } else {
                    onNavigateNext()
                }
            }.onFailure { err ->
                exportState = EditorExportEngine.ExportState.Failed(
                    err.message ?: "Export failed. Please check device storage and try again."
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07070A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Edge-to-Edge Preview + Floating Top Bar + Right Vertical Tool Column
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                EditorPreviewCanvas(
                    snapshot = snapshot,
                    activeClip = activeClip,
                    isPlaying = isPlaying,
                    playheadFraction = playheadFraction,
                    isDrawMode = activeTool == EditorTool.DRAW,
                    activeBrush = activeBrush,
                    activeBrushColor = activeBrushColor,
                    activeBrushWidthDp = activeBrushWidthDp,
                    selectedOverlayId = selectedOverlayId,
                    lowEndMode = lowEndMode,
                    onPlayheadTick = { frac, detectedDur ->
                        playheadFraction = frac
                        if (activeClip != null && detectedDur > 0L && kotlin.math.abs(activeClip.durationMs - detectedDur) > 800L) {
                            val idx = snapshot.selectedClipIndex.coerceIn(0, snapshot.clips.lastIndex)
                            val updated = snapshot.clips.toMutableList()
                            updated[idx] = updated[idx].copy(durationMs = detectedDur)
                            snapshot = snapshot.copy(clips = updated)
                        }
                    },
                    onSwipeFilter = { dir ->
                        val curIdx = EDITOR_FILTER_PRESETS.indexOfFirst { it.id == snapshot.filterId }.coerceAtLeast(0)
                        val nextIdx = (curIdx + dir + EDITOR_FILTER_PRESETS.size) % EDITOR_FILTER_PRESETS.size
                        applyChange { it.copy(filterId = EDITOR_FILTER_PRESETS[nextIdx].id) }
                    },
                    onTogglePlayPause = { isPlaying = !isPlaying },
                    onSelectOverlay = { selectedOverlayId = it },
                    onEditTextItem = { item ->
                        editingTextItem = item
                        activeTool = EditorTool.TEXT
                    },
                    onUpdateTextTransform = { id, x, y, sc, rot ->
                        applyChange { snap ->
                            snap.copy(textItems = snap.textItems.map {
                                if (it.id == id) it.copy(xFraction = x, yFraction = y, scale = sc, rotationDeg = rot) else it
                            })
                        }
                    },
                    onDeleteTextItem = { id ->
                        applyChange { snap -> snap.copy(textItems = snap.textItems.filterNot { it.id == id }) }
                        if (selectedOverlayId == id) selectedOverlayId = null
                    },
                    onUpdateStickerTransform = { id, x, y, sc, rot ->
                        applyChange { snap ->
                            snap.copy(stickerItems = snap.stickerItems.map {
                                if (it.id == id) it.copy(xFraction = x, yFraction = y, scale = sc, rotationDeg = rot) else it
                            })
                        }
                    },
                    onDeleteStickerItem = { id ->
                        applyChange { snap -> snap.copy(stickerItems = snap.stickerItems.filterNot { it.id == id }) }
                        if (selectedOverlayId == id) selectedOverlayId = null
                    },
                    onCommitDrawStroke = { stroke ->
                        applyChange { snap -> snap.copy(drawStrokes = snap.drawStrokes + stroke) }
                    }
                )

                // Top Bar: Close (X), Undo, Redo, Aspect Ratio, Next/Share
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent))
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Close (X)
                        EditorTopCircleButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Close Editor",
                            testTag = "editor_close_btn"
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (hasUnsavedChanges) showDiscardDialog = true else onNavigateBack()
                        }

                        // Undo
                        val canUndo = remember(historyRevision) { history.canUndo }
                        EditorTopCircleButton(
                            icon = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            enabled = canUndo,
                            testTag = "editor_undo_btn"
                        ) {
                            history.undo()?.let { restored ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                snapshot = restored
                                historyRevision++
                            }
                        }

                        // Redo
                        val canRedo = remember(historyRevision) { history.canRedo }
                        EditorTopCircleButton(
                            icon = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            enabled = canRedo,
                            testTag = "editor_redo_btn"
                        ) {
                            history.redo()?.let { restored ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                snapshot = restored
                                historyRevision++
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Quick Aspect Ratio toggle pill (9:16 -> 4:5 -> 1:1)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            modifier = Modifier
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val entries = AspectRatioPreset.entries
                                    val next = entries[(entries.indexOf(snapshot.aspectRatio) + 1) % entries.size]
                                    applyChange { it.copy(aspectRatio = next) }
                                }
                                .testTag("editor_aspect_pill")
                        ) {
                            Text(
                                text = snapshot.aspectRatio.label,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        // Next / Share Primary Pill
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                startBackgroundExport()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.height(40.dp).testTag("editor_next_share_btn")
                        ) {
                            Text("Next", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Right-Side Vertical Tool Column (Instagram Stories & Reels Layout)
                androidx.compose.animation.AnimatedVisibility(
                    visible = activeTool == EditorTool.NONE,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(180)),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RightToolRailButton(Icons.Default.TextFields, "Text", "editor_rail_text") {
                            editingTextItem = null
                            activeTool = EditorTool.TEXT
                        }
                        RightToolRailButton(Icons.Default.EmojiEmotions, "Stickers", "editor_rail_stickers") {
                            activeTool = EditorTool.STICKERS
                        }
                        RightToolRailButton(
                            Icons.Default.MusicNote,
                            "Music",
                            "editor_rail_music",
                            highlighted = snapshot.musicConfig != null
                        ) {
                            activeTool = EditorTool.MUSIC
                        }
                        RightToolRailButton(
                            Icons.Default.AutoAwesome,
                            "Effects",
                            "editor_rail_effects",
                            highlighted = snapshot.filterId != "normal" || !snapshot.adjustments.isDefault
                        ) {
                            activeTool = EditorTool.FILTERS
                        }
                        RightToolRailButton(
                            Icons.Default.Brush,
                            "Draw",
                            "editor_rail_draw",
                            highlighted = snapshot.drawStrokes.isNotEmpty()
                        ) {
                            activeTool = EditorTool.DRAW
                        }
                        RightToolRailButton(
                            if (showMoreDrawer) Icons.Default.ExpandLess else Icons.Default.MoreHoriz,
                            "More",
                            "editor_rail_more"
                        ) {
                            showMoreDrawer = !showMoreDrawer
                        }

                        // Expandable "More" quick tools
                        AnimatedVisibility(
                            visible = showMoreDrawer,
                            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -20 },
                            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -20 }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RightToolRailButton(Icons.Default.Subtitles, "Captions", "editor_rail_captions") {
                                    showMoreDrawer = false
                                    activeTool = EditorTool.VOICE_CAPTIONS
                                }
                                RightToolRailButton(Icons.Default.Crop, "Crop", "editor_rail_crop") {
                                    showMoreDrawer = false
                                    activeTool = EditorTool.CROP_COVER
                                }
                                RightToolRailButton(Icons.Default.Inventory2, "Drafts", "editor_rail_drafts") {
                                    showMoreDrawer = false
                                    activeTool = EditorTool.DRAFTS
                                }
                                RightToolRailButton(Icons.Default.Palette, "Brand Kit", "editor_rail_brand_kit") {
                                    showMoreDrawer = false
                                    showBrandKitSheet = true
                                }
                                RightToolRailButton(Icons.Default.AutoAwesomeMosaic, "Template", "editor_rail_publish_template") {
                                    showMoreDrawer = false
                                    showPublishTemplateSheet = true
                                }
                            }
                        }
                    }
                }
            }

            // 2. Bottom Workspace: Either Timeline Dock OR Animated Tool Bottom Sheet (240ms)
            AnimatedContent(
                targetState = activeTool,
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 4 })
                        .togetherWith(fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 4 })
                },
                label = "editor_bottom_workspace"
            ) { tool ->
                when (tool) {
                    EditorTool.NONE, EditorTool.TIMELINE, EditorTool.MORE -> {
                        EditorBottomTimelineDock(
                            snapshot = snapshot,
                            isPlaying = isPlaying,
                            playheadFraction = playheadFraction,
                            activeTool = activeTool,
                            onTogglePlayPause = { isPlaying = !isPlaying },
                            onScrubPlayhead = { frac ->
                                isPlaying = false
                                playheadFraction = frac
                            },
                            onSelectClip = { idx ->
                                applyChange { it.copy(selectedClipIndex = idx.coerceIn(0, it.clips.lastIndex)) }
                            },
                            onTrimSelectedClip = { s, e ->
                                val idx = snapshot.selectedClipIndex.coerceIn(0, snapshot.clips.lastIndex)
                                applyChange { snap ->
                                    val list = snap.clips.toMutableList()
                                    list[idx] = list[idx].copy(trimStartFraction = s, trimEndFraction = e)
                                    snap.copy(clips = list)
                                }
                            },
                            onSplitSelectedClip = {
                                val idx = snapshot.selectedClipIndex.coerceIn(0, snapshot.clips.lastIndex)
                                val clip = snapshot.clips[idx]
                                val splitFrac = (clip.trimStartFraction + (clip.trimEndFraction - clip.trimStartFraction) * playheadFraction.coerceIn(0.15f, 0.85f))
                                val left = clip.copy(id = UUID.randomUUID().toString(), trimEndFraction = splitFrac)
                                val right = clip.copy(id = UUID.randomUUID().toString(), trimStartFraction = splitFrac)
                                applyChange { snap ->
                                    val list = snap.clips.toMutableList()
                                    list.removeAt(idx)
                                    list.add(idx, right)
                                    list.add(idx, left)
                                    snap.copy(clips = list, selectedClipIndex = idx)
                                }
                                Toast.makeText(context, "Clip split at playhead ✂️", Toast.LENGTH_SHORT).show()
                            },
                            onDeleteSelectedClip = {
                                if (snapshot.clips.size > 1) {
                                    val idx = snapshot.selectedClipIndex.coerceIn(0, snapshot.clips.lastIndex)
                                    applyChange { snap ->
                                        val list = snap.clips.toMutableList().apply { removeAt(idx) }
                                        snap.copy(clips = list, selectedClipIndex = idx.coerceAtMost(list.lastIndex))
                                    }
                                }
                            },
                            onReorderClip = { from, to ->
                                if (from in snapshot.clips.indices && to in snapshot.clips.indices) {
                                    applyChange { snap ->
                                        val list = snap.clips.toMutableList()
                                        val item = list.removeAt(from)
                                        list.add(to, item)
                                        snap.copy(clips = list, selectedClipIndex = to)
                                    }
                                }
                            },
                            onAddClipClick = { addClipLauncher.launch("*/*") },
                            onSelectTool = { next ->
                                if (next == EditorTool.TEXT) editingTextItem = null
                                activeTool = next
                            }
                        )
                    }

                    EditorTool.SPEED_AUDIO -> {
                        SpeedAudioSheet(
                            clip = activeClip,
                            musicVolume = snapshot.musicConfig?.volume ?: 0.85f,
                            voiceoverVolume = snapshot.voiceoverVolume,
                            onUpdateClip = { updatedClip ->
                                val idx = snapshot.selectedClipIndex.coerceIn(0, snapshot.clips.lastIndex)
                                applyChange { snap ->
                                    val list = snap.clips.toMutableList()
                                    list[idx] = updatedClip
                                    snap.copy(clips = list)
                                }
                            },
                            onMusicVolumeChange = { vol ->
                                snapshot.musicConfig?.let { m ->
                                    applyChange { it.copy(musicConfig = m.copy(volume = vol)) }
                                }
                            },
                            onVoiceoverVolumeChange = { vol ->
                                applyChange { it.copy(voiceoverVolume = vol) }
                            },
                            onDone = { activeTool = EditorTool.NONE }
                        )
                    }

                    EditorTool.FILTERS, EditorTool.ADJUST -> {
                        FiltersAndAdjustSheet(
                            initialTab = if (tool == EditorTool.ADJUST) 1 else 0,
                            previewUriString = activeClip?.uriString,
                            selectedFilterId = snapshot.filterId,
                            adjustments = snapshot.adjustments,
                            onSelectFilter = { id -> applyChange { it.copy(filterId = id) } },
                            onUpdateAdjustments = { adj -> applyChange { it.copy(adjustments = adj) } },
                            onDone = { activeTool = EditorTool.NONE }
                        )
                    }

                    EditorTool.TEXT -> {
                        TextEditorBottomSheet(
                            editingItem = editingTextItem,
                            onSaveText = { saved ->
                                applyChange { snap ->
                                    val exists = snap.textItems.any { it.id == saved.id }
                                    val list = if (exists) snap.textItems.map { if (it.id == saved.id) saved else it }
                                    else snap.textItems + saved
                                    snap.copy(textItems = list)
                                }
                                selectedOverlayId = saved.id
                                editingTextItem = null
                                activeTool = EditorTool.NONE
                            },
                            onCancel = {
                                editingTextItem = null
                                activeTool = EditorTool.NONE
                            }
                        )
                    }

                    EditorTool.STICKERS -> {
                        StickersBottomSheet(
                            currentMusic = snapshot.musicConfig,
                            onAddSticker = { st ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                applyChange { it.copy(stickerItems = it.stickerItems + st) }
                                selectedOverlayId = st.id
                                activeTool = EditorTool.NONE
                            },
                            onOpenGifPicker = { showGifPicker = true },
                            onDone = { activeTool = EditorTool.NONE }
                        )
                    }

                    EditorTool.DRAW -> {
                        DrawControlSheet(
                            activeBrush = activeBrush,
                            activeColor = activeBrushColor,
                            strokeWidthDp = activeBrushWidthDp,
                            strokeCount = snapshot.drawStrokes.size,
                            onSelectBrush = { activeBrush = it },
                            onSelectColor = { activeBrushColor = it },
                            onWidthChange = { activeBrushWidthDp = it },
                            onUndoStroke = {
                                if (snapshot.drawStrokes.isNotEmpty()) {
                                    applyChange { it.copy(drawStrokes = it.drawStrokes.dropLast(1)) }
                                }
                            },
                            onClearAll = {
                                applyChange { it.copy(drawStrokes = emptyList()) }
                            },
                            onDone = { activeTool = EditorTool.NONE }
                        )
                    }

                    EditorTool.MUSIC -> {
                        MusicTrimBeatSyncSheet(
                            musicConfig = snapshot.musicConfig,
                            onOpenMusicLibrary = { onNavigateToMusicPicker() },
                            onUpdateMusic = { cfg -> applyChange { it.copy(musicConfig = cfg) } },
                            onApplyBeatSyncAutoCut = { bpm ->
                                // Auto-snap active clips cleanly to 4-beat bar duration
                                val barMs = ((60_000f / bpm.coerceIn(70, 180)) * 4f).toLong().coerceIn(1500L, 4000L)
                                applyChange { snap ->
                                    val synced = snap.clips.map { clip ->
                                        val frac = (barMs.toFloat() / clip.durationMs.coerceAtLeast(1000L)).coerceIn(0.2f, 1f)
                                        clip.copy(trimStartFraction = 0f, trimEndFraction = frac)
                                    }
                                    snap.copy(clips = synced)
                                }
                                Toast.makeText(context, "Beat-Sync auto-cut applied ($bpm BPM) 🎵", Toast.LENGTH_SHORT).show()
                            },
                            onDone = { activeTool = EditorTool.NONE }
                        )
                    }

                    EditorTool.VOICE_CAPTIONS -> {
                        VoiceoverCaptionsSheet(
                            voiceoverFilePath = snapshot.voiceoverFilePath,
                            captions = snapshot.captions,
                            captionStyle = snapshot.captionStyle,
                            captionsEnabled = snapshot.captionsEnabled,
                            totalDurationMs = snapshot.clips.sumOf { it.effectiveDurationMs }.coerceAtLeast(3000L),
                            onVoiceoverSaved = { path -> applyChange { it.copy(voiceoverFilePath = path) } },
                            onUpdateCaptions = { list, style, enabled ->
                                applyChange { it.copy(captions = list, captionStyle = style, captionsEnabled = enabled) }
                            },
                            onDone = { activeTool = EditorTool.NONE }
                        )
                    }

                    EditorTool.CROP_COVER -> {
                        CropCoverSheet(
                            aspectRatio = snapshot.aspectRatio,
                            activeClip = activeClip,
                            coverFrameTimeMs = snapshot.coverFrameTimeMs,
                            lowEndMode = lowEndMode,
                            onSelectAspectRatio = { ratio -> applyChange { it.copy(aspectRatio = ratio) } },
                            onUpdateClip = { updated ->
                                val idx = snapshot.selectedClipIndex.coerceIn(0, snapshot.clips.lastIndex)
                                applyChange { snap ->
                                    val list = snap.clips.toMutableList()
                                    list[idx] = updated
                                    snap.copy(clips = list)
                                }
                            },
                            onSelectCoverFrame = { timeMs, filePath ->
                                applyChange { it.copy(coverFrameTimeMs = timeMs, coverFrameFilePath = filePath) }
                                Toast.makeText(context, "Reel cover frame updated!", Toast.LENGTH_SHORT).show()
                            },
                            onDone = { activeTool = EditorTool.NONE }
                        )
                    }

                    EditorTool.DRAFTS -> {
                        OfflineDraftsSheet(
                            currentDraftId = snapshot.draftId,
                            onResumeDraft = { loaded ->
                                history.reset(loaded)
                                snapshot = loaded
                                historyRevision++
                                hasUnsavedChanges = false
                                activeTool = EditorTool.NONE
                                Toast.makeText(context, "Draft resumed offline ✨", Toast.LENGTH_SHORT).show()
                            },
                            onSaveCurrentNow = {
                                val ok = OfflineEditorDraftStore.saveDraft(context, snapshot)
                                Toast.makeText(context, if (ok) "Saved to offline drafts!" else "Could not save draft", Toast.LENGTH_SHORT).show()
                            },
                            onDone = { activeTool = EditorTool.NONE }
                        )
                    }
                }
            }
        }

        // GIF & Sticker Library Bottom Sheet (reuses existing app component)
        if (showGifPicker) {
            GifPickerBottomSheet(
                onDismissRequest = { showGifPicker = false },
                onGifSelected = { gif ->
                    val url = gif.fullUrl.ifBlank { gif.previewUrl }
                    applyChange {
                        it.copy(stickerItems = it.stickerItems + EditorStickerItem(kind = StickerKind.GIF, primaryText = gif.title, mediaUrl = url))
                    }
                    showGifPicker = false
                    activeTool = EditorTool.NONE
                },
                onStickerSelected = { sticker ->
                    val url = sticker.mediaUrl.ifBlank { sticker.thumbnailUrl }
                    applyChange {
                        it.copy(stickerItems = it.stickerItems + EditorStickerItem(kind = StickerKind.GIF, primaryText = sticker.name, mediaUrl = url))
                    }
                    showGifPicker = false
                    activeTool = EditorTool.NONE
                },
                onEmojiSelected = { emoji ->
                    applyChange {
                        it.copy(stickerItems = it.stickerItems + EditorStickerItem(kind = StickerKind.EMOJI, primaryText = emoji))
                    }
                    showGifPicker = false
                    activeTool = EditorTool.NONE
                }
            )
        }

        // Discard Confirmation Dialog (Save Draft / Discard / Keep Editing)
        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                containerColor = Color(0xFF171721),
                title = { Text("Discard your edits?", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Save as an offline draft to resume editing anytime — even without internet — or discard your changes.",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            OfflineEditorDraftStore.saveDraft(context, snapshot)
                            Toast.makeText(context, "Saved to offline drafts 📁", Toast.LENGTH_SHORT).show()
                            showDiscardDialog = false
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Save Draft", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { showDiscardDialog = false }) {
                            Text("Keep Editing", color = Color.White.copy(alpha = 0.8f))
                        }
                        TextButton(onClick = {
                            OfflineEditorDraftStore.deleteDraft(context, snapshot.draftId)
                            showDiscardDialog = false
                            onNavigateBack()
                        }) {
                            Text("Discard", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }

        // Background Export Progress & Error Recovery Overlay
        when (val ex = exportState) {
            is EditorExportEngine.ExportState.Exporting -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.78f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF161622),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        modifier = Modifier.fillMaxWidth(0.84f)
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Exporting Edit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(ex.stage, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, textAlign = TextAlign.Center)
                            LinearProgressIndicator(
                                progress = { ex.progress.coerceIn(0f, 1f) },
                                color = Color(0xFFEE1D52),
                                trackColor = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                            )
                            Text("${(ex.progress * 100).toInt()}%", color = Color(0xFFFF4D79), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            TextButton(onClick = {
                                exportJob?.cancel()
                                exportState = EditorExportEngine.ExportState.Idle
                            }) {
                                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
            is EditorExportEngine.ExportState.Failed -> {
                AlertDialog(
                    onDismissRequest = { exportState = EditorExportEngine.ExportState.Idle },
                    containerColor = Color(0xFF171721),
                    title = { Text("Export Failed", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold) },
                    text = { Text(ex.message, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp) },
                    confirmButton = {
                        Button(
                            onClick = { startBackgroundExport() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
                        ) {
                            Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { exportState = EditorExportEngine.ExportState.Idle }) {
                            Text("Close", color = Color.White.copy(alpha = 0.75f))
                        }
                    }
                )
            }
            EditorExportEngine.ExportState.Idle -> Unit
        }

        if (showBrandKitSheet) {
            val savedKit = remember { com.example.ui.templates.TemplateRepository.loadBrandKitLocally(context) }
            com.example.ui.templates.BrandKitBottomSheet(
                initialKit = savedKit,
                onDismiss = { showBrandKitSheet = false },
                onSaveAndApply = { kit ->
                    showBrandKitSheet = false
                    applyChange { snap ->
                        val updatedTexts = snap.textItems.mapIndexed { idx, txt ->
                            txt.copy(
                                fontKey = kit.fontKey,
                                colorArgb = if (idx == 0) kit.accentColorArgb else 0xFFFFFFFF,
                                bgColorArgb = if (idx == 0) kit.primaryColorArgb else kit.secondaryColorArgb
                            )
                        }.toMutableList()
                        if (kit.handle.isNotBlank() && updatedTexts.none { it.text == kit.handle }) {
                            updatedTexts.add(
                                EditorTextItem(
                                    text = kit.handle,
                                    fontKey = kit.fontKey,
                                    colorArgb = kit.accentColorArgb,
                                    bgColorArgb = kit.secondaryColorArgb,
                                    bgStyle = TextBgStyle.FROSTED,
                                    xFraction = 0.5f,
                                    yFraction = 0.90f,
                                    fontSizeSp = 15f
                                )
                            )
                        }
                        val updatedStickers = snap.stickerItems.map {
                            it.copy(accentColorArgb = kit.primaryColorArgb)
                        }.toMutableList()
                        if (kit.logoUriString.isNotBlank() && updatedStickers.none { it.mediaUrl == kit.logoUriString }) {
                            updatedStickers.add(
                                EditorStickerItem(
                                    kind = StickerKind.GIF,
                                    primaryText = kit.brandName,
                                    mediaUrl = kit.logoUriString,
                                    xFraction = 0.84f,
                                    yFraction = 0.14f,
                                    scale = 0.65f
                                )
                            )
                        }
                        snap.copy(textItems = updatedTexts, stickerItems = updatedStickers)
                    }
                }
            )
        }

        if (showPublishTemplateSheet) {
            com.example.ui.templates.PublishTemplateBottomSheet(
                sourceSnapshot = snapshot,
                defaultTitle = "",
                defaultType = if (snapshot.clips.any { it.isVideo }) "reel" else "post",
                onDismiss = { showPublishTemplateSheet = false },
                onPublished = { showPublishTemplateSheet = false }
            )
        }
    }
}

@Composable
private fun EditorTopCircleButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = if (enabled) 0.55f else 0.25f))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.24f else 0.08f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun RightToolRailButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (highlighted) Color(0xFFEE1D52).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.52f))
                .border(1.dp, if (highlighted) Color.White else Color.White.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
