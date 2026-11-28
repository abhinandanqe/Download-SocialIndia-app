package com.example.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.editor.model.EditorState
import kotlin.math.roundToInt

@Composable
fun TimelineView(
    state: EditorState,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onSelectClip: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(Color(0xFF0F0F0F))
    ) {
        // Playback Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTime(state.playheadPositionMs)} / ${formatTime(state.totalDurationMs)}",
                color = Color.LightGray,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            // Removed central play button as we moved it to overlay on video and it's cleaner without it here
        }

        // Multi-Track Container
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val usableWidth = (totalWidthPx - 32f).coerceAtLeast(10f)
            val totalDur = if (state.totalDurationMs > 0) state.totalDurationMs else 10000L
            val progress = (state.playheadPositionMs.toFloat() / totalDur).coerceIn(0f, 1f)
            val playheadX = 16f + (progress * usableWidth)

            // Tap on timeline background to seek
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(totalDur, usableWidth) {
                        detectTapGestures { offset ->
                            val seekFraction = ((offset.x - 16f) / usableWidth).coerceIn(0f, 1f)
                            val seekMs = (seekFraction * totalDur).toLong()
                            onSeek(seekMs)
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Track 1: Video Track
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = "Video Track",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Video", color = Color.Gray, fontSize = 10.sp)
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(state.videoClips) { clip ->
                            val isSelected = state.selectedClipId == clip.id
                            Surface(
                                onClick = { onSelectClip(clip.id) },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) Color(0xFF0095F6).copy(alpha = 0.8f) else Color(0xFF2A2A2A),
                                modifier = Modifier
                                    .height(48.dp)
                                    .width(100.dp)
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "Clip (${(clip.durationMs / 1000)}s)",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (clip.speed != 1.0f) {
                                            Text("${clip.speed}x", color = Color(0xFFFFD700), fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Track 2: Audio Track
                    if (state.audioTracks.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "Audio Track",
                                tint = Color(0xFF0095F6),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Audio", color = Color(0xFF0095F6), fontSize = 10.sp)
                        }

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(state.audioTracks) { track ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF0095F6).copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .width(120.dp)
                                        .border(1.dp, Color(0xFF0095F6).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            "🎵 Background Audio",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Track 3: Text / Overlays Track
                    if (state.textOverlays.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.TextFields,
                                contentDescription = "Text Track",
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Overlays", color = Color(0xFFFF9800), fontSize = 10.sp)
                        }

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(state.textOverlays) { overlay ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFFF9800).copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .height(24.dp)
                                        .width(80.dp)
                                        .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    ) {
                                        Text(
                                            overlay.text,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Playhead Vertical Line with Head Handle
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .offset { IntOffset(playheadX.roundToInt(), 0) }
                    .background(Color.White)
                    .pointerInput(totalDur, usableWidth) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val newX = (playheadX + dragAmount.x - 16f).coerceIn(0f, usableWidth)
                            val newTime = ((newX / usableWidth) * totalDur).toLong()
                            onSeek(newTime)
                        }
                    }
            ) {
                // Playhead Drag Handle at Top
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(12.dp)
                        .offset(y = (-3).dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Color.Gray, CircleShape)
                )
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
