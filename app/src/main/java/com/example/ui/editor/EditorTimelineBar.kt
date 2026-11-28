package com.example.ui.editor

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.util.Locale

/**
 * Multi-clip gesture-driven Timeline Dock + Bottom Tool Tabs:
 * - Play / Pause + live timecode
 * - Pinch-to-zoom clip track (0.6x .. 3.0x)
 * - Draggable playhead scrubber
 * - Trim start/end handles on selected clip
 * - Split at playhead, Delete clip, Reorder clip left/right, Add clip
 * - Music & Voiceover track strips (with Beat-Sync beat markers)
 * - Bottom tool tabs with 48dp accessible touch targets
 */
@Composable
fun EditorBottomTimelineDock(
    snapshot: EditorProjectSnapshot,
    isPlaying: Boolean,
    playheadFraction: Float,
    activeTool: EditorTool,
    onTogglePlayPause: () -> Unit,
    onScrubPlayhead: (Float) -> Unit,
    onSelectClip: (Int) -> Unit,
    onTrimSelectedClip: (Float, Float) -> Unit,
    onSplitSelectedClip: () -> Unit,
    onDeleteSelectedClip: () -> Unit,
    onReorderClip: (Int, Int) -> Unit,
    onAddClipClick: () -> Unit,
    onSelectTool: (EditorTool) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var timelineZoom by remember { mutableFloatStateOf(1f) }
    val scrollState = rememberScrollState()

    val totalDurationMs = remember(snapshot.clips) {
        snapshot.clips.sumOf { it.effectiveDurationMs }.coerceAtLeast(1000L)
    }
    val currentTimeMs = (playheadFraction.coerceIn(0f, 1f) * totalDurationMs).toLong()
    val selectedClip = snapshot.clips.getOrNull(snapshot.selectedClipIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0D12))
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 6.dp)
    ) {
        // 1. Transport & Quick Clip Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Play/Pause + Timecode
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTogglePlayPause()
                        }
                        .testTag("editor_play_pause_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "${formatEditorTime(currentTimeMs)} / ${formatEditorTime(totalDurationMs)}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (timelineZoom != 1f) {
                    Surface(shape = RoundedCornerShape(6.dp), color = Color.White.copy(alpha = 0.1f)) {
                        Text(
                            text = String.format(Locale.US, "%.1fx", timelineZoom),
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Quick Clip Ops: Split, Reorder Left/Right, Delete, Add
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniTimelineChip(
                    icon = Icons.Default.ContentCut,
                    label = "Split",
                    testTag = "editor_split_btn",
                    enabled = selectedClip != null
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSplitSelectedClip()
                }
                if (snapshot.clips.size > 1) {
                    IconButton(
                        onClick = {
                            if (snapshot.selectedClipIndex > 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onReorderClip(snapshot.selectedClipIndex, snapshot.selectedClipIndex - 1)
                            }
                        },
                        enabled = snapshot.selectedClipIndex > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Move clip left",
                            tint = if (snapshot.selectedClipIndex > 0) Color.White else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (snapshot.selectedClipIndex < snapshot.clips.lastIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onReorderClip(snapshot.selectedClipIndex, snapshot.selectedClipIndex + 1)
                            }
                        },
                        enabled = snapshot.selectedClipIndex < snapshot.clips.lastIndex,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Move clip right",
                            tint = if (snapshot.selectedClipIndex < snapshot.clips.lastIndex) Color.White else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (snapshot.clips.size > 1) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeleteSelectedClip()
                        },
                        modifier = Modifier.size(32.dp).testTag("editor_delete_clip_btn")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete clip", tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEE1D52))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAddClipClick()
                        }
                        .testTag("editor_add_clip_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add clip", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Pinch-to-Zoom Multi-Clip Track + Playhead + Trim Handles
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(62.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF171721))
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            timelineZoom = (timelineZoom * zoom).coerceIn(0.65f, 3.0f)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        onScrubPlayhead(frac)
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                snapshot.clips.forEachIndexed { idx, clip ->
                    val isSelected = idx == snapshot.selectedClipIndex
                    val clipWidthDp = ((clip.effectiveDurationMs / 1000f) * 26f * timelineZoom).coerceIn(64f, 360f).dp
                    Box(
                        modifier = Modifier
                            .width(clipWidthDp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF232332))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectClip(idx)
                            }
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            repeat(4) {
                                AsyncImage(
                                    model = Uri.parse(clip.uriString),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxHeight().alpha(0.75f)
                                )
                            }
                        }

                        // Clip duration + speed badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier.align(Alignment.TopStart).padding(3.dp)
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", clip.effectiveDurationMs / 1000f)}s" +
                                    if (clip.speed != 1f) " • ${String.format(Locale.US, "%.1fx", clip.speed)}" else "",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        // Interactive Left & Right Trim Handles on selected clip
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .fillMaxHeight()
                                    .width(12.dp)
                                    .background(Color(0xFFEE1D52))
                                    .pointerInput(clip.id) {
                                        detectHorizontalDragGestures { change, dragAmount ->
                                            change.consume()
                                            val delta = dragAmount / 320f
                                            val nextStart = (clip.trimStartFraction + delta)
                                                .coerceIn(0f, (clip.trimEndFraction - 0.1f).coerceAtLeast(0f))
                                            onTrimSelectedClip(nextStart, clip.trimEndFraction)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.width(2.dp).height(18.dp).background(Color.White, CircleShape))
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(12.dp)
                                    .background(Color(0xFFEE1D52))
                                    .pointerInput(clip.id) {
                                        detectHorizontalDragGestures { change, dragAmount ->
                                            change.consume()
                                            val delta = dragAmount / 320f
                                            val nextEnd = (clip.trimEndFraction + delta)
                                                .coerceIn((clip.trimStartFraction + 0.1f).coerceAtMost(1f), 1f)
                                            onTrimSelectedClip(clip.trimStartFraction, nextEnd)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.width(2.dp).height(18.dp).background(Color.White, CircleShape))
                            }
                        }
                    }
                }
            }

            // Moving White Playhead Needle
            Canvas(modifier = Modifier.fillMaxSize()) {
                val x = (playheadFraction.coerceIn(0f, 1f) * size.width)
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.5.dp.toPx())
                drawCircle(Color.White, radius = 4.5.dp.toPx(), center = Offset(x, 5.dp.toPx()))
            }
        }

        // 3. Compact Audio / Music / Voiceover Track Bar (with Beat-Sync ticks)
        val activeMusicConfig = snapshot.musicConfig
        val hasMusic = activeMusicConfig != null
        val hasVoice = !snapshot.voiceoverFilePath.isNullOrBlank()
        if (hasMusic || hasVoice) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (activeMusicConfig != null) {
                    val m = activeMusicConfig
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1DB954).copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color(0xFF1DB954).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectTool(EditorTool.MUSIC) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(13.dp))
                            Text(
                                text = "${m.name} (${m.windowDurationSec.toInt()}s)" + if (m.beatSyncAutoCut) " • Beat Sync" else "",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (hasVoice) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF3B82F6).copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clickable { onSelectTool(EditorTool.VOICE_CAPTIONS) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(13.dp))
                            Text("Voiceover", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Bottom Tool Tabs Strip
        val toolTabs = listOf(
            Triple(EditorTool.SPEED_AUDIO, Icons.Default.Speed, "Speed"),
            Triple(EditorTool.FILTERS, Icons.Default.AutoAwesome, "Filters"),
            Triple(EditorTool.ADJUST, Icons.Default.Tune, "Adjust"),
            Triple(EditorTool.TEXT, Icons.Default.TextFields, "Text"),
            Triple(EditorTool.STICKERS, Icons.Default.EmojiEmotions, "Stickers"),
            Triple(EditorTool.DRAW, Icons.Default.Brush, "Draw"),
            Triple(EditorTool.MUSIC, Icons.Default.MusicNote, "Music"),
            Triple(EditorTool.VOICE_CAPTIONS, Icons.Default.Subtitles, "Captions"),
            Triple(EditorTool.CROP_COVER, Icons.Default.Crop, "Crop/Cover"),
            Triple(EditorTool.DRAFTS, Icons.Default.Inventory2, "Drafts")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            toolTabs.forEach { (tool, icon, label) ->
                val selected = activeTool == tool
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 54.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color(0xFFEE1D52).copy(alpha = 0.22f) else Color.Transparent)
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            color = if (selected) Color(0xFFEE1D52) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectTool(if (selected) EditorTool.NONE else tool)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("editor_tab_${tool.name.lowercase()}")
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selected) Color(0xFFFF4D79) else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = label,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniTimelineChip(
    icon: ImageVector,
    label: String,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = if (enabled) 0.12f else 0.05f),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = label, tint = if (enabled) Color.White else Color.Gray, modifier = Modifier.size(14.dp))
            Text(label, color = if (enabled) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

fun formatEditorTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
