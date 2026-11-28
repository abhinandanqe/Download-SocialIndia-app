package com.example.ui.editor

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Shared dark glass bottom sheet header for all editor tools.
 */
@Composable
fun EditorSheetHeader(
    title: String,
    actionLabel: String = "Done",
    onReset: (() -> Unit)? = null,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (onReset != null) {
            TextButton(onClick = onReset) {
                Text("Reset", color = Color(0xFF9CA3AF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Spacer(modifier = Modifier.width(56.dp))
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp).testTag("editor_sheet_done_btn")
        ) {
            Text(actionLabel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 1. Speed (0.3x - 3.0x), Volume Mixer, Mute, and Reverse Clip Sheet.
 */
@Composable
fun SpeedAudioSheet(
    clip: EditorClip?,
    musicVolume: Float,
    voiceoverVolume: Float,
    onUpdateClip: (EditorClip) -> Unit,
    onMusicVolumeChange: (Float) -> Unit,
    onVoiceoverVolumeChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12121A))
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        EditorSheetHeader(
            title = "Speed & Audio",
            onReset = {
                if (clip != null) {
                    onUpdateClip(clip.copy(speed = 1f, volume = 1f, isMuted = false, isReversed = false))
                }
            },
            onDone = onDone
        )

        if (clip == null) {
            Text(
                "Select a clip on the timeline to adjust speed or audio.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(20.dp)
            )
            return
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Speed presets 0.3x .. 3.0x
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Playback Speed", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(String.format(Locale.US, "%.1fx", clip.speed), color = Color(0xFFFF4D79), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.3f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f).forEach { spd ->
                    val selected = kotlin.math.abs(clip.speed - spd) < 0.05f
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUpdateClip(clip.copy(speed = spd))
                            }
                    ) {
                        Text(
                            text = "${spd}x",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            Slider(
                value = clip.speed,
                onValueChange = { onUpdateClip(clip.copy(speed = it)) },
                valueRange = 0.3f..3.0f,
                colors = SliderDefaults.colors(activeTrackColor = Color(0xFFEE1D52), thumbColor = Color.White)
            )

            // Mute & Reverse quick toggles
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (clip.isMuted) Color(0xFFEE1D52).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, if (clip.isMuted) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.14f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onUpdateClip(clip.copy(isMuted = !clip.isMuted))
                        }
                        .testTag("editor_mute_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (clip.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Mute clip",
                            tint = if (clip.isMuted) Color(0xFFFF4D79) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (clip.isMuted) "Muted" else "Mute Clip", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (clip.isReversed) Color(0xFFFFC107).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, if (clip.isReversed) Color(0xFFFFC107) else Color.White.copy(alpha = 0.14f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onUpdateClip(clip.copy(isReversed = !clip.isReversed))
                        }
                        .testTag("editor_reverse_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FastRewind,
                            contentDescription = "Reverse clip",
                            tint = if (clip.isReversed) Color(0xFFFFD54F) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (clip.isReversed) "Reversed" else "Reverse", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Volume sliders
            AdjustSliderRow("Clip Volume", if (clip.isMuted) 0f else clip.volume, 0f..1f, "${((if (clip.isMuted) 0f else clip.volume) * 100).toInt()}%") {
                onUpdateClip(clip.copy(volume = it, isMuted = it <= 0.01f))
            }
            AdjustSliderRow("Music Volume", musicVolume, 0f..1f, "${(musicVolume * 100).toInt()}%") {
                onMusicVolumeChange(it)
            }
            AdjustSliderRow("Voiceover", voiceoverVolume, 0f..1f, "${(voiceoverVolume * 100).toInt()}%") {
                onVoiceoverVolumeChange(it)
            }
        }
    }
}

/**
 * 2. Instagram Filters (12 presets) + 7 Pro Adjustments (Brightness, Contrast,
 *    Saturation, Warmth, Fade, Vignette, Sharpen).
 */
@Composable
fun FiltersAndAdjustSheet(
    initialTab: Int, // 0 = Filters, 1 = Adjust
    previewUriString: String?,
    selectedFilterId: String,
    adjustments: ColorAdjustments,
    onSelectFilter: (String) -> Unit,
    onUpdateAdjustments: (ColorAdjustments) -> Unit,
    onDone: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var tab by remember(initialTab) { mutableIntStateOf(initialTab) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12121A))
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
    ) {
        EditorSheetHeader(
            title = if (tab == 0) "Filters (Swipe preview too)" else "Adjust Color",
            onReset = {
                if (tab == 0) onSelectFilter("normal")
                else onUpdateAdjustments(ColorAdjustments())
            },
            onDone = onDone
        )

        // Tab switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Filters", "Adjust (7)").forEachIndexed { idx, label ->
                val sel = tab == idx
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (sel) Color.White else Color.White.copy(alpha = 0.08f),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { tab = idx }
                ) {
                    Text(
                        text = label,
                        color = if (sel) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (tab == 0) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(EDITOR_FILTER_PRESETS, key = { it.id }) { preset ->
                    val sel = preset.id == selectedFilterId
                    val cm = remember(preset.id) { buildCombinedComposeColorMatrix(preset, ColorAdjustments()) }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(74.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectFilter(preset.id)
                            }
                            .testTag("editor_filter_${preset.id}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (sel) 2.5.dp else 1.dp,
                                    color = if (sel) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(Color(0xFF1F1F2B))
                        ) {
                            if (!previewUriString.isNullOrBlank()) {
                                AsyncImage(
                                    model = Uri.parse(previewUriString),
                                    contentDescription = preset.name,
                                    contentScale = ContentScale.Crop,
                                    colorFilter = ColorFilter.colorMatrix(cm),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = preset.name,
                            color = if (sel) Color(0xFFFF4D79) else Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AdjustSliderRow("Brightness", adjustments.brightness, -0.45f..0.45f, "${(adjustments.brightness * 100).toInt()}") {
                    onUpdateAdjustments(adjustments.copy(brightness = it))
                }
                AdjustSliderRow("Contrast", adjustments.contrast, 0.5f..1.8f, "${((adjustments.contrast - 1f) * 100).toInt()}") {
                    onUpdateAdjustments(adjustments.copy(contrast = it))
                }
                AdjustSliderRow("Saturation", adjustments.saturation, 0f..2.0f, "${((adjustments.saturation - 1f) * 100).toInt()}") {
                    onUpdateAdjustments(adjustments.copy(saturation = it))
                }
                AdjustSliderRow("Warmth", adjustments.warmth, -1f..1f, "${(adjustments.warmth * 100).toInt()}") {
                    onUpdateAdjustments(adjustments.copy(warmth = it))
                }
                AdjustSliderRow("Fade", adjustments.fade, 0f..1f, "${(adjustments.fade * 100).toInt()}%") {
                    onUpdateAdjustments(adjustments.copy(fade = it))
                }
                AdjustSliderRow("Vignette", adjustments.vignette, 0f..1f, "${(adjustments.vignette * 100).toInt()}%") {
                    onUpdateAdjustments(adjustments.copy(vignette = it))
                }
                AdjustSliderRow("Sharpen", adjustments.sharpen, 0f..1f, "${(adjustments.sharpen * 100).toInt()}%") {
                    onUpdateAdjustments(adjustments.copy(sharpen = it))
                }
            }
        }
    }
}

@Composable
private fun AdjustSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(86.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(activeTrackColor = Color(0xFFEE1D52), thumbColor = Color.White)
        )
        Text(valueLabel, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/**
 * 3. Text Editor Bottom Sheet: 16 bundled fonts (5 Hindi/Devanagari + 11 English),
 *    color picker, background chip, alignment, outline, size & 6 animations.
 */
@Composable
fun TextEditorBottomSheet(
    editingItem: EditorTextItem?,
    onSaveText: (EditorTextItem) -> Unit,
    onCancel: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var text by remember(editingItem?.id) { mutableStateOf(editingItem?.text ?: "") }
    var fontKey by remember(editingItem?.id) { mutableStateOf(editingItem?.fontKey ?: "poppins") }
    var textColor by remember(editingItem?.id) { mutableStateOf(Color((editingItem?.colorArgb ?: 0xFFFFFFFF).toInt())) }
    var bgColor by remember(editingItem?.id) { mutableStateOf(Color((editingItem?.bgColorArgb ?: 0xCC000000).toInt())) }
    var bgStyle by remember(editingItem?.id) { mutableStateOf(editingItem?.bgStyle ?: TextBgStyle.SOLID_CHIP) }
    var alignment by remember(editingItem?.id) { mutableStateOf(editingItem?.alignment ?: TextAlignMode.CENTER) }
    var hasOutline by remember(editingItem?.id) { mutableStateOf(editingItem?.hasOutline ?: false) }
    var animStyle by remember(editingItem?.id) { mutableStateOf(editingItem?.animStyle ?: TextAnimStyle.NONE) }
    var fontSizeSp by remember(editingItem?.id) { mutableFloatStateOf(editingItem?.fontSizeSp ?: 26f) }

    val colorSwatches = remember {
        listOf(
            Color.White, Color.Black, Color(0xFFEE1D52), Color(0xFFFFD54F),
            Color(0xFF38BDF8), Color(0xFF34D399), Color(0xFFA855F7),
            Color(0xFFFF6D00), Color(0xFFF472B6), Color(0xFFFACC15)
        )
    }
    val selectedFont = remember(fontKey) { findEditorFont(fontKey) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12121A))
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        EditorSheetHeader(
            title = if (editingItem == null) "Add Text (Hindi / English)" else "Edit Text",
            actionLabel = "Apply",
            onReset = onCancel,
            onDone = {
                val trimmed = text.trim()
                if (trimmed.isNotEmpty()) {
                    val item = (editingItem ?: EditorTextItem(text = trimmed)).copy(
                        text = trimmed,
                        fontKey = fontKey,
                        colorArgb = textColor.toArgbLong(),
                        bgColorArgb = bgColor.toArgbLong(),
                        bgStyle = bgStyle,
                        alignment = alignment,
                        hasOutline = hasOutline,
                        animStyle = animStyle,
                        fontSizeSp = fontSizeSp
                    )
                    onSaveText(item)
                } else {
                    onCancel()
                }
            }
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 180) text = it },
                placeholder = { Text("Type text or हिन्दी लिखें...", color = Color.White.copy(alpha = 0.45f)) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = selectedFont.fontFamily,
                    color = Color.White,
                    fontSize = 18.sp
                ),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("editor_text_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFEE1D52),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // 16 Fonts Strip (Hindi/Devanagari first + English)
            Text("Fonts (16 • हिन्दी + English)", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EDITOR_FONTS, key = { it.key }) { font ->
                    val sel = font.key == fontKey
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (sel) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.09f),
                        border = BorderStroke(1.dp, if (sel) Color.White else Color.White.copy(alpha = 0.14f)),
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                fontKey = font.key
                            }
                            .testTag("editor_font_${font.key}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = font.displayName,
                                fontFamily = font.fontFamily,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            if (font.isHindi) {
                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFFD54F).copy(alpha = 0.25f)) {
                                    Text("HI", color = Color(0xFFFFD54F), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Text Colors
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                colorSwatches.forEach { c ->
                    val sel = c == textColor
                    Box(
                        modifier = Modifier
                            .size(if (sel) 30.dp else 26.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (sel) 2.5.dp else 1.dp, if (sel) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.45f), CircleShape)
                            .clickable { textColor = c }
                    )
                }
            }

            // Alignment, Outline, Background Chip style
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        TextAlignMode.START to Icons.AutoMirrored.Filled.FormatAlignLeft,
                        TextAlignMode.CENTER to Icons.Default.FormatAlignCenter,
                        TextAlignMode.END to Icons.AutoMirrored.Filled.FormatAlignRight
                    ).forEach { (mode, icon) ->
                        IconButton(
                            onClick = { alignment = mode },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (alignment == mode) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(icon, contentDescription = mode.name, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                FilterChip(
                    selected = hasOutline,
                    onClick = { hasOutline = !hasOutline },
                    label = { Text("Outline", fontSize = 12.sp) }
                )
            }

            // Background chip styles
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextBgStyle.entries.forEach { style ->
                    val sel = bgStyle == style
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (sel) Color.White else Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.clickable { bgStyle = style }
                    ) {
                        Text(
                            text = style.label,
                            color = if (sel) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Text Animation Picker
            Text("Text Animation", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextAnimStyle.entries.forEach { anim ->
                    val sel = animStyle == anim
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (sel) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.clickable { animStyle = anim }
                    ) {
                        Text(
                            text = anim.label,
                            color = if (sel) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            AdjustSliderRow("Size", fontSizeSp, 16f..54f, "${fontSizeSp.toInt()}sp") {
                fontSizeSp = it
            }
        }
    }
}

/**
 * 4. Draw Controls Bar: Pen, Marker, Neon, Eraser + Interactive HSV Color Wheel + Size.
 */
@Composable
fun DrawControlSheet(
    activeBrush: DrawBrushType,
    activeColor: Color,
    strokeWidthDp: Float,
    strokeCount: Int,
    onSelectBrush: (DrawBrushType) -> Unit,
    onSelectColor: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onUndoStroke: () -> Unit,
    onClearAll: () -> Unit,
    onDone: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showColorWheel by remember { mutableStateOf(false) }
    val presets = remember {
        listOf(Color.White, Color(0xFFEE1D52), Color(0xFFFFD54F), Color(0xFF34D399), Color(0xFF38BDF8), Color(0xFFA855F7), Color.Black)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12121A))
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
    ) {
        EditorSheetHeader(
            title = "Draw on Canvas",
            onReset = if (strokeCount > 0) onClearAll else null,
            onDone = onDone
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Brush Type Selector + Undo Stroke
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DrawBrushType.entries.forEach { brush ->
                    val sel = activeBrush == brush
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (sel) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectBrush(brush)
                            }
                            .testTag("editor_brush_${brush.name.lowercase()}")
                    ) {
                        Text(
                            text = brush.label,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onUndoStroke,
                    enabled = strokeCount > 0,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo stroke", tint = if (strokeCount > 0) Color.White else Color.Gray, modifier = Modifier.size(18.dp))
                }
            }

            // Color Swatches + Color Wheel Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    presets.forEach { c ->
                        val sel = c == activeColor
                        Box(
                            modifier = Modifier
                                .size(if (sel) 28.dp else 24.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(if (sel) 2.dp else 1.dp, Color.White, CircleShape)
                                .clickable { onSelectColor(c) }
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (showColorWheel) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.clickable { showColorWheel = !showColorWheel }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.ColorLens, contentDescription = "Color wheel", tint = if (showColorWheel) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                        Text("Wheel", color = if (showColorWheel) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (showColorWheel) {
                HsvColorWheelPicker(
                    selectedColor = activeColor,
                    onColorPicked = onSelectColor,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            AdjustSliderRow("Brush Size", strokeWidthDp, 2f..24f, "${strokeWidthDp.toInt()}dp") {
                onWidthChange(it)
            }
        }
    }
}

/**
 * Interactive HSV Color Wheel Canvas for Draw Tool.
 */
@Composable
private fun HsvColorWheelPicker(
    selectedColor: Color,
    onColorPicked: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val wheelColors = remember {
        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
    }
    var indicatorOffset by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier.size(136.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    fun pickAt(pos: Offset) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = minOf(cx, cy)
                        val dx = pos.x - cx
                        val dy = pos.y - cy
                        val dist = hypot(dx, dy).coerceAtMost(r)
                        val angle = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat()
                        val sat = (dist / r).coerceIn(0f, 1f)
                        val hsvInt = android.graphics.Color.HSVToColor(floatArrayOf(angle, sat, 1f))
                        indicatorOffset = Offset(cx + cos(Math.toRadians(angle.toDouble())).toFloat() * dist, cy + sin(Math.toRadians(angle.toDouble())).toFloat() * dist)
                        onColorPicked(Color(hsvInt))
                    }
                    detectTapGestures { pickAt(it) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val pos = change.position
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = minOf(cx, cy)
                        val dx = pos.x - cx
                        val dy = pos.y - cy
                        val dist = hypot(dx, dy).coerceAtMost(r)
                        val angle = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat()
                        val sat = (dist / r).coerceIn(0f, 1f)
                        val hsvInt = android.graphics.Color.HSVToColor(floatArrayOf(angle, sat, 1f))
                        indicatorOffset = Offset(cx + cos(Math.toRadians(angle.toDouble())).toFloat() * dist, cy + sin(Math.toRadians(angle.toDouble())).toFloat() * dist)
                        onColorPicked(Color(hsvInt))
                    }
                }
        ) {
            drawCircle(brush = Brush.sweepGradient(wheelColors))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    center = center,
                    radius = size.minDimension / 2f
                )
            )
            val pt = indicatorOffset ?: center
            drawCircle(color = Color.White, radius = 9.dp.toPx(), center = pt)
            drawCircle(color = selectedColor, radius = 6.dp.toPx(), center = pt)
        }
    }
}
