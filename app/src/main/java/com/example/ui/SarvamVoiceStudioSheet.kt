package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.SarvamVoice
import com.example.ai.SarvamVoiceRegistry
import com.example.viewmodel.MahiAiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SarvamVoiceStudioSheet(
    viewModel: MahiAiViewModel,
    onDismiss: () -> Unit
) {
    val currentSettings by viewModel.sarvamSettings.collectAsState()
    val isSpeaking by viewModel.isMahiSpeaking.collectAsState()
    val previewingVoiceId by viewModel.previewingVoiceId.collectAsState()

    var selectedTab by remember { mutableStateOf("All") }
    val tabs = listOf("All", "Female", "Male", "Regional")

    val filteredVoices = remember(selectedTab) {
        when (selectedTab) {
            "Female" -> SarvamVoiceRegistry.ALL_VOICES.filter { it.gender == "Female" }
            "Male" -> SarvamVoiceRegistry.ALL_VOICES.filter { it.gender == "Male" }
            "Regional" -> SarvamVoiceRegistry.ALL_VOICES.filter { it.languageCode != "hi-IN" || it.model == "bulbul:v4-flash" }
            else -> SarvamVoiceRegistry.ALL_VOICES
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFEE1D52), Color(0xFF7A00FF))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Sarvam AI Voice Studio",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ultra-Realistic Indian Neural Voices",
                            fontSize = 12.sp,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Info Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEE1D52).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFEE1D52),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Apni pasand ki natural awaaz select karein. Mahi call aur chat me isi voice me bolegi!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabs) { tab ->
                    val isSelected = selectedTab == tab
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        label = { Text(tab, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFEE1D52),
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pace / Speed Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Speaking Speed: ${String.format("%.2fx", currentSettings.pace)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (currentSettings.pace != 1.0f) {
                    TextButton(
                        onClick = { viewModel.updatePace(1.0f) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Reset", fontSize = 12.sp, color = Color(0xFF00E5FF))
                    }
                }
            }

            Slider(
                value = currentSettings.pace,
                onValueChange = { viewModel.updatePace(it) },
                valueRange = 0.8f..1.3f,
                steps = 4,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFEE1D52),
                    activeTrackColor = Color(0xFFEE1D52)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Choose Mahi's Voice (${filteredVoices.size} Voices):",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Voices List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredVoices, key = { it.id }) { voice ->
                    val isSelected = currentSettings.voiceId == voice.id
                    val isPreviewingThis = previewingVoiceId == voice.id && isSpeaking

                    Surface(
                        onClick = { viewModel.selectVoice(voice) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFFEE1D52).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 1.5.dp else 0.8.dp,
                            color = if (isSelected) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar icon with gender indicator
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (voice.gender == "Female") Color(0xFFFF4081).copy(alpha = 0.2f)
                                        else Color(0xFF00E5FF).copy(alpha = 0.2f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (voice.gender == "Female") "👩" else "👨",
                                    fontSize = 20.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = voice.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isSelected) Color(0xFFEE1D52) else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (voice.isDefault) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text("Default", fontSize = 9.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "${voice.languageName} • ${voice.tag}",
                                    fontSize = 11.5.sp,
                                    color = Color.Gray
                                )
                            }

                            // Preview Button
                            FilledTonalIconButton(
                                onClick = { viewModel.previewVoice(voice) },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (isPreviewingThis) Color(0xFF00E5FF) else MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPreviewingThis) Icons.Default.VolumeUp else Icons.Default.PlayArrow,
                                    contentDescription = "Preview Sample",
                                    tint = if (isPreviewingThis) Color.Black else Color(0xFF00E5FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Radio Check
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.selectVoice(voice) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFFEE1D52)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
