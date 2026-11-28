package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.bounceClick

@Composable
fun FlagshipCameraTopBar(
    onClose: () -> Unit,
    onFlashToggle: () -> Unit,
    isFlashOn: Boolean,
    onHdrToggle: () -> Unit,
    isHdrOn: Boolean,
    onMusicClick: () -> Unit,
    hasMusic: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                .size(40.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        // Central Music Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (hasMusic) Color(0xFFFE2C55) else Color.Black.copy(alpha = 0.3f))
                .clickable { onMusicClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .bounceClick()
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Music",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (hasMusic) "Audio selected" else "Add sound",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(
                onClick = onHdrToggle,
                modifier = Modifier
                    .background(if (isHdrOn) Color(0xFFFE2C55) else Color.Black.copy(alpha = 0.3f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(Icons.Default.HdrOn, contentDescription = "HDR", tint = Color.White)
            }
        }
    }
}

@Composable
fun FlagshipCameraSideToolbar(
    onSwitchCamera: () -> Unit,
    onToggleTimer: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleFilters: () -> Unit,
    onToggleBeauty: () -> Unit,
    isTimerOn: Boolean,
    isGridOn: Boolean,
    isBeautyOn: Boolean
) {
    Column(
        modifier = Modifier
            .padding(end = 16.dp, top = 20.dp)
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CameraToolItem(icon = Icons.Default.FlipCameraAndroid, label = "Flip", onClick = onSwitchCamera)
        CameraToolItem(icon = Icons.Default.FaceRetouchingNatural, label = "Beauty", isActive = isBeautyOn, onClick = onToggleBeauty)
        CameraToolItem(icon = Icons.Default.AutoFixHigh, label = "Filters", onClick = onToggleFilters)
        CameraToolItem(icon = Icons.Default.Timer, label = "Timer", isActive = isTimerOn, onClick = onToggleTimer)
        CameraToolItem(icon = Icons.Default.Grid3x3, label = "Grid", isActive = isGridOn, onClick = onToggleGrid)
    }
}

@Composable
private fun CameraToolItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .bounceClick()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .size(26.dp)
                    .offset(x = 1.dp, y = 1.dp)
            )
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color(0xFFFE2C55) else Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isActive) Color(0xFFFE2C55) else Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun FlagshipCaptureButton(
    isRecording: Boolean,
    captureMode: String,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val innerSize by animateDpAsState(targetValue = if (isRecording) 30.dp else 60.dp)
    val innerRadius by animateDpAsState(targetValue = if (isRecording) 8.dp else 30.dp)
    val outerColor = if (captureMode == "Live") Color(0xFF00E5FF) else Color(0xFFFE2C55)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(80.dp)
            .clickable { onClick() } // Would use combinedClickable for long press in real app
    ) {
        Box(
            modifier = Modifier
                .size(80.dp * pulse)
                .border(6.dp, outerColor.copy(alpha = 0.5f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(innerSize)
                .background(outerColor, RoundedCornerShape(innerRadius))
        )
    }
}

@Composable
fun FlagshipCameraModeSelector(
    currentMode: String,
    onModeSelected: (String) -> Unit
) {
    val modes = listOf("Post", "Story", "Reel", "Templates", "Live", "Audio Room")
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(bottom = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { mode ->
            val isSelected = currentMode == mode ||
                (mode == "Post" && currentMode == "Photo") ||
                (mode == "Reel" && currentMode == "Video")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected && mode == "Templates") Color(0xFFEE1D52).copy(alpha = 0.28f) else Color.Transparent)
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("create_mode_${mode.lowercase().replace(" ", "_")}")
            ) {
                Text(
                    text = if (mode == "Templates") "✨ Templates" else mode,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(width = if (isSelected) 18.dp else 0.dp, height = 2.5.dp)
                        .clip(CircleShape)
                        .background(if (mode == "Templates") Color(0xFFFFD54F) else Color.White)
                )
            }
        }
    }
}
