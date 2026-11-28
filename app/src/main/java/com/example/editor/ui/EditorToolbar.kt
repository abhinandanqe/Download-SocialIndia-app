package com.example.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.editor.viewmodel.VideoEditorViewModel

@Composable
fun EditorToolbar(
    viewModel: VideoEditorViewModel,
    activeTool: String? = null,
    onSelectTool: (String?) -> Unit = {}
) {
    Surface(
        color = Color(0xFF0F0F0F),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tools = listOf(
                Triple(Icons.Default.AutoAwesome, "Auto Cut", "autocut"),
                Triple(Icons.Default.ContentCut, "Trim", "trim"),
                Triple(Icons.Default.CallSplit, "Split", "split"),
                Triple(Icons.Default.Speed, "Speed", "speed"),
                Triple(Icons.Default.Crop, "Crop", "crop"),
                Triple(Icons.Default.TextFields, "Text", "text"),
                Triple(Icons.Default.FilterVintage, "Filters", "filters"),
                Triple(Icons.Default.Tune, "Adjust", "adjust"),
                Triple(Icons.Default.MusicNote, "Audio", "audio"),
                Triple(Icons.Default.AutoFixHigh, "Effects", "effects"),
                Triple(Icons.Default.FormatShapes, "Stickers", "stickers"),
                Triple(Icons.Default.PictureInPicture, "PiP", "pip")
            )

            tools.forEach { (icon, label, key) ->
                val isSelected = activeTool == key
                ToolbarItem(
                    icon = icon,
                    label = label,
                    isSelected = isSelected,
                    onClick = {
                        onSelectTool(if (isSelected) null else key)
                    }
                )
            }
        }
    }
}

@Composable
fun ToolbarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color.White else Color(0xFF222222)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isSelected) Color.Black else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color.Gray
        )
    }
}
