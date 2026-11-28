package com.example.ui.templates

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.PaymentProductType
import com.example.model.RepositoryManager
import com.example.repository.ExclusiveContentRepository
import com.example.repository.ExclusiveUnlockResult
import com.example.ui.components.RealUpiPaymentBottomSheet
import com.example.ui.editor.EDITOR_FONTS
import com.example.ui.editor.EditorProjectSnapshot
import com.example.ui.editor.findEditorFont
import kotlinx.coroutines.launch
import java.util.Locale

private val BRAND_COLOR_SWATCHES = listOf(
    0xFFEE1D52L,
    0xFFFF6F00L,
    0xFFFFD54FL,
    0xFF10B981L,
    0xFF2563EBL,
    0xFF7C3AEDL,
    0xFFEC4899L,
    0xFF141422L,
    0xFFFFFFFFL
)

private fun isUriVideo(context: Context, uri: Uri): Boolean {
    return try {
        val mime = context.contentResolver.getType(uri)
        if (mime != null) mime.startsWith("video/")
        else {
            val s = uri.toString().lowercase(Locale.ROOT)
            s.endsWith(".mp4") || s.endsWith(".mov") || s.endsWith(".mkv") || s.endsWith(".webm")
        }
    } catch (_: Exception) {
        false
    }
}

/**
 * Slot-Filler & Auto-Fill Sheet:
 * Lets the user drop photos/videos into every template slot in 1 tap (Auto-Fill) or replace
 * individual slots, toggle Brand Kit application, and launch straight into InstagramProEditorScreen.
 */
@Composable
fun TemplateSlotFillerDialog(
    template: ContentTemplate,
    sourcePostId: String? = null,
    onDismiss: () -> Unit,
    onOpenInEditor: (snapshot: EditorProjectSnapshot, uris: List<Uri>, template: ContentTemplate) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val safeSlots = remember(template.id) {
        template.slots.ifEmpty {
            listOf(TemplateSlot(id = "default_slot", mediaType = "any", start = 0L, end = template.duration.coerceAtLeast(5000L)))
        }
    }

    var slotUris by remember(template.id) {
        mutableStateOf<List<Uri?>>(List(safeSlots.size) { null })
    }
    var replacingSlotIndex by remember { mutableIntStateOf(-1) }
    var applyBrandKit by remember { mutableStateOf(false) }
    var brandKit by remember { mutableStateOf(TemplateRepository.loadBrandKitLocally(context)) }
    var showBrandKitEditor by remember { mutableStateOf(false) }
    var isOpening by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker for 1-Tap Auto-Fill
    val autoFillPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = safeSlots.size.coerceIn(2, 10))
    ) { picked ->
        if (picked.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val filled = List(safeSlots.size) { idx ->
                picked[idx % picked.size]
            }
            slotUris = filled
            Toast.makeText(
                context,
                "Auto-filled ${safeSlots.size} slots to match template beats ✨",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Single-slot replacement picker
    val singleSlotPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { pickedUri ->
        if (pickedUri != null && replacingSlotIndex in safeSlots.indices) {
            val updated = slotUris.toMutableList()
            updated[replacingSlotIndex] = pickedUri
            slotUris = updated
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        replacingSlotIndex = -1
    }

    val filledCount = slotUris.count { it != null }
    val allFilled = filledCount > 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF13131D),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEE1D52).copy(alpha = 0.22f)
                            ) {
                                Text(
                                    text = template.typeLabel.uppercase(),
                                    color = Color(0xFFFF5C8A),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                text = "${template.aspectRatio} • ${(template.duration / 1000f).let { String.format(Locale.US, "%.1fs", it) }}",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = template.title.ifBlank { "Use Template" },
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("slot_filler_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Quick Story "Add Yours" Chain Context Banner
                if (template.type == "quick_story" && template.promptText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF23162D),
                        border = BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFFFF66B2), modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Add Yours Chain • ${formatUsageCount(template.usageCount)} joined",
                                    color = Color(0xFFFF85C2),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "\"${template.promptText}\"",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Audio & Beat-Sync Info Row
                if (template.audio.name.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.06f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = template.audio.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val beatInfo = if (template.audio.beatMarkers.isNotEmpty()) {
                                    "Beat-Sync Auto-Cut • ${template.audio.beatMarkers.size} beats • ${template.audio.bpm} BPM"
                                } else {
                                    template.audio.artist
                                }
                                Text(beatInfo, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            }
                        }
                    }
                }

                // 1-Tap Auto-Fill Action Card
                Button(
                    onClick = {
                        val requestType = when {
                            safeSlots.all { it.mediaType == "photo" } -> ActivityResultContracts.PickVisualMedia.ImageOnly
                            safeSlots.all { it.mediaType == "video" } -> ActivityResultContracts.PickVisualMedia.VideoOnly
                            else -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
                        }
                        autoFillPicker.launch(PickVisualMediaRequest(requestType))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("template_auto_fill_btn")
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (filledCount == 0) "Auto-Fill ${safeSlots.size} Slots from Gallery" else "Re-Pick & Auto-Fill Clips",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Slot Strip
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Slots ($filledCount/${safeSlots.size} filled)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap any slot to replace",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(safeSlots) { idx, slot ->
                            val uri = slotUris.getOrNull(idx)
                            val durLabel = String.format(Locale.US, "%.1fs", slot.slotDurationMs / 1000f)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(96.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF1D1D2C))
                                    .border(
                                        width = if (uri != null) 1.5.dp else 1.dp,
                                        color = if (uri != null) Color(0xFF10B981) else Color.White.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        replacingSlotIndex = idx
                                        val mediaReq = when (slot.mediaType) {
                                            "photo" -> ActivityResultContracts.PickVisualMedia.ImageOnly
                                            "video" -> ActivityResultContracts.PickVisualMedia.VideoOnly
                                            else -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                        }
                                        singleSlotPicker.launch(PickVisualMediaRequest(mediaReq))
                                    }
                                    .padding(6.dp)
                                    .testTag("template_slot_item_$idx")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(104.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0E0E16)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uri != null) {
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = "Slot ${idx + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                        }
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = if (slot.mediaType == "video") Icons.Default.Videocam else Icons.Default.AddPhotoAlternate,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = slot.mediaType.uppercase(),
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.Black.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = durLabel,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Clip ${idx + 1} • ${slot.transition}",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 1-Tap Brand Kit Row
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(
                        1.dp,
                        if (applyBrandKit) Color(brandKit.primaryColorArgb) else Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(brandKit.primaryColorArgb)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Apply Brand Kit (1 Tap)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${brandKit.brandName} • ${findEditorFont(brandKit.fontKey).displayName}",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { showBrandKitEditor = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Edit", color = Color(0xFFFFD54F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = applyBrandKit,
                            onCheckedChange = { applyBrandKit = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFEE1D52))
                        )
                    }
                }

                // Primary Open in Editor CTA
                Button(
                    onClick = {
                        if (!allFilled || isOpening) return@Button
                        isOpening = true
                        val resolvedUris = safeSlots.mapIndexed { idx, _ ->
                            slotUris[idx] ?: slotUris.firstNotNullOf { it }
                        }
                        val videoFlags = resolvedUris.map { isUriVideo(context, it) }
                        val snapshot = buildEditorSnapshotFromTemplate(
                            template = template,
                            slotUris = resolvedUris,
                            isVideoFlags = videoFlags,
                            brandKit = if (applyBrandKit) brandKit else null
                        )
                        scope.launch {
                            TemplateRepository.recordTemplateUsage(context, template, sourcePostId)
                            isOpening = false
                            onOpenInEditor(snapshot, resolvedUris, template)
                        }
                    },
                    enabled = allFilled && !isOpening,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEE1D52),
                        disabledContainerColor = Color(0xFF3A2430)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("template_open_in_editor_btn")
                ) {
                    if (isOpening) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (allFilled) "Open in Pro Editor →" else "Pick at least 1 Photo/Video to Continue",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }

    if (showBrandKitEditor) {
        BrandKitBottomSheet(
            initialKit = brandKit,
            onDismiss = { showBrandKitEditor = false },
            onSaveAndApply = { savedKit ->
                brandKit = savedKit
                applyBrandKit = true
                showBrandKitEditor = false
            }
        )
    }
}

/**
 * Brand Kit Editor Sheet: Save brand name, handle, colors, Hindi/English font & logo and apply in 1 tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandKitBottomSheet(
    initialKit: BrandKit,
    onDismiss: () -> Unit,
    onSaveAndApply: (BrandKit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var brandName by remember { mutableStateOf(initialKit.brandName) }
    var handle by remember { mutableStateOf(initialKit.handle) }
    var primaryColor by remember { mutableStateOf(initialKit.primaryColorArgb) }
    var secondaryColor by remember { mutableStateOf(initialKit.secondaryColorArgb) }
    var accentColor by remember { mutableStateOf(initialKit.accentColorArgb) }
    var fontKey by remember { mutableStateOf(initialKit.fontKey) }
    var logoUri by remember { mutableStateOf(initialKit.logoUriString) }
    var isSaving by remember { mutableStateOf(false) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            logoUri = uri.toString()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141F),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Brand Kit", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Save colors, logo & fonts to apply in 1 tap", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Live Preview Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(secondaryColor),
                border = BorderStroke(1.5.dp, Color(primaryColor)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(primaryColor))
                            .clickable {
                                logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (logoUri.isNotBlank()) {
                            AsyncImage(
                                model = Uri.parse(logoUri),
                                contentDescription = "Brand Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.AddAPhoto, contentDescription = "Pick Logo", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = brandName.ifBlank { "My Brand" },
                            color = Color(accentColor),
                            fontFamily = findEditorFont(fontKey).fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = handle.ifBlank { "@socialindia" },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(if (logoUri.isBlank()) "Add Logo" else "Change", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            OutlinedTextField(
                value = brandName,
                onValueChange = { brandName = it },
                label = { Text("Brand / Creator Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFEE1D52),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                )
            )

            OutlinedTextField(
                value = handle,
                onValueChange = { handle = it },
                label = { Text("Watermark Handle (e.g. @yourbrand)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFEE1D52),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                )
            )

            // Primary Color Picker
            Text("Primary Brand Color", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BRAND_COLOR_SWATCHES.forEach { argb ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (primaryColor == argb) 3.dp else 1.dp,
                                color = if (primaryColor == argb) Color.White else Color.White.copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                            .clickable { primaryColor = argb }
                    )
                }
            }

            // Accent Color Picker
            Text("Accent Text Color", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BRAND_COLOR_SWATCHES.forEach { argb ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (accentColor == argb) 3.dp else 1.dp,
                                color = if (accentColor == argb) Color.White else Color.White.copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                            .clickable { accentColor = argb }
                    )
                }
            }

            // Font Picker (includes Hindi & Regional Fonts!)
            Text("Brand Font (Hindi + English)", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EDITOR_FONTS.forEach { font ->
                    val selected = font.key == fontKey
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.clickable { fontKey = font.key }
                    ) {
                        Text(
                            text = if (font.isHindi) "${font.displayName} (हिन्दी)" else font.displayName,
                            color = Color.White,
                            fontFamily = font.fontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (isSaving) return@Button
                    isSaving = true
                    val kit = BrandKit(
                        brandName = brandName.trim().ifBlank { "My Brand" },
                        handle = handle.trim(),
                        primaryColorArgb = primaryColor,
                        secondaryColorArgb = secondaryColor,
                        accentColorArgb = accentColor,
                        fontKey = fontKey,
                        logoUriString = logoUri
                    )
                    scope.launch {
                        TemplateRepository.saveBrandKit(context, kit)
                        isSaving = false
                        Toast.makeText(context, "Brand Kit saved & applied! 🎨", Toast.LENGTH_SHORT).show()
                        onSaveAndApply(kit)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("brand_kit_save_apply_btn")
            ) {
                Text(if (isSaving) "Saving..." else "Save & Apply Brand Kit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

/**
 * Creator Publish-as-Template Bottom Sheet:
 * Publishes either the current Pro Editor snapshot or a fresh template to Firestore `templates`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishTemplateBottomSheet(
    sourceSnapshot: EditorProjectSnapshot? = null,
    defaultTitle: String = "",
    defaultType: String = "reel",
    onDismiss: () -> Unit,
    onPublished: (ContentTemplate) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by RepositoryManager.currentUserState.collectAsState()
    val isVerifiedCreator = currentUser?.isVerified == true

    var title by remember { mutableStateOf(defaultTitle) }
    var selectedType by remember { mutableStateOf(defaultType) }
    var selectedCategory by remember { mutableStateOf("Recommended") }
    var promptText by remember { mutableStateOf("") }
    var isPremium by remember { mutableStateOf(false) }
    var priceText by remember { mutableStateOf("29") }
    var isPublishing by remember { mutableStateOf(false) }

    val types = listOf("reel" to "Reel", "story" to "Story", "quick_story" to "Add Yours", "post" to "Post", "carousel" to "Carousel")
    val categories = listOf("Recommended", "Festival", "Birthday", "Wedding", "Business", "Trending")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141F),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Publish as Template ✨", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Others can fill your beat-synced slots, text layers & stickers right inside the editor.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Template Title (e.g. Wedding Sangeet Beat)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("publish_template_title_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFEE1D52),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                )
            )

            Text("Format", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                types.forEach { (key, label) ->
                    FilterChip(
                        selected = selectedType == key,
                        onClick = { selectedType = key },
                        label = { Text(label) }
                    )
                }
            }

            Text("Category", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            if (selectedType == "quick_story") {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("Add Yours Chain Prompt") },
                    placeholder = { Text("e.g. Show your weekend vibe ✨") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFEC4899),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                    )
                )
            }

            // Premium Template Toggle (Monetization)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, if (isPremium) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Premium Template (Paid)", color = Color(0xFFFFD54F), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isVerifiedCreator) "Free users see locked preview • Unlock via Wallet/UPI"
                                else "Verified creator badge required for paid templates",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isPremium && isVerifiedCreator,
                            onCheckedChange = { checked ->
                                if (!isVerifiedCreator) {
                                    Toast.makeText(context, "Get Verified to sell Premium Templates", Toast.LENGTH_SHORT).show()
                                } else {
                                    isPremium = checked
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFFB300))
                        )
                    }
                    if (isPremium && isVerifiedCreator) {
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it.filter { c -> c.isDigit() } },
                            label = { Text("Price (₹10 – ₹999)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFFD54F),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                            )
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val cleanTitle = title.trim()
                    if (cleanTitle.isBlank()) {
                        Toast.makeText(context, "Enter a template title first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val parsedPrice = if (isPremium && isVerifiedCreator) {
                        (priceText.toDoubleOrNull() ?: 29.0).coerceIn(10.0, 999.0)
                    } else 0.0

                    isPublishing = true
                    var cursor = 0L
                    val builtSlots = if (sourceSnapshot != null && sourceSnapshot.clips.isNotEmpty()) {
                        sourceSnapshot.clips.mapIndexed { idx, clip ->
                            val dur = clip.effectiveDurationMs.coerceIn(1000L, 10_000L)
                            val slot = TemplateSlot(
                                id = "s_${idx + 1}",
                                mediaType = if (clip.isVideo) "video" else "photo",
                                start = cursor,
                                end = cursor + dur,
                                transition = if (idx == 0) "beat_flash" else "fade",
                                defaultFilter = sourceSnapshot.filterId
                            )
                            cursor += dur
                            slot
                        }
                    } else {
                        val count = when (selectedType) {
                            "carousel" -> 4
                            "post" -> 3
                            "reel" -> 3
                            else -> 1
                        }
                        List(count) { idx ->
                            val start = idx * 3000L
                            TemplateSlot(
                                id = "s_${idx + 1}",
                                mediaType = if (selectedType == "post" || selectedType == "carousel") "photo" else "any",
                                start = start,
                                end = start + 3000L,
                                transition = if (selectedType == "reel") "beat_flash" else "fade",
                                defaultFilter = sourceSnapshot?.filterId ?: "normal"
                            )
                        }
                    }

                    val totalDur = builtSlots.lastOrNull()?.end ?: 9000L
                    val beatMarkers = builtSlots.map { it.start } + totalDur
                    val newTemplate = ContentTemplate(
                        type = selectedType,
                        title = cleanTitle,
                        category = selectedCategory,
                        aspectRatio = sourceSnapshot?.aspectRatio?.label ?: if (selectedType == "post") "4:5" else if (selectedType == "carousel") "1:1" else "9:16",
                        duration = totalDur,
                        slots = builtSlots,
                        textLayers = sourceSnapshot?.textItems ?: emptyList(),
                        stickers = sourceSnapshot?.stickerItems ?: emptyList(),
                        audio = TemplateAudio(
                            url = sourceSnapshot?.musicConfig?.previewUrl ?: "",
                            beatMarkers = beatMarkers,
                            name = sourceSnapshot?.musicConfig?.name ?: "Original Audio",
                            artist = sourceSnapshot?.musicConfig?.artist ?: (currentUser?.username ?: "Creator"),
                            bpm = sourceSnapshot?.musicConfig?.bpm ?: 120
                        ),
                        usageCount = 1L,
                        isPremium = isPremium && isVerifiedCreator,
                        price = parsedPrice,
                        layoutStyle = when (selectedType) {
                            "quick_story" -> "ADD_YOURS_CHAIN"
                            "story" -> "STORY_FRAME"
                            "carousel" -> "CAROUSEL_MULTI"
                            "post" -> "COLLAGE_3"
                            else -> "SINGLE"
                        },
                        promptText = if (selectedType == "quick_story") promptText.trim() else "",
                        previewGradientStart = 0xFF1F1135,
                        previewGradientEnd = 0xFFEE1D52,
                        tags = listOf(selectedType, selectedCategory.lowercase(Locale.ROOT), cleanTitle.lowercase(Locale.ROOT))
                    )

                    scope.launch {
                        val res = TemplateRepository.publishCreatorTemplate(context, newTemplate)
                        isPublishing = false
                        res.onSuccess { published ->
                            Toast.makeText(context, "Template published! 🎉", Toast.LENGTH_SHORT).show()
                            onPublished(published)
                        }.onFailure { err ->
                            Toast.makeText(context, "Publish failed: ${err.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isPublishing,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("publish_template_submit_btn")
            ) {
                Text(if (isPublishing) "Publishing..." else "Publish Template", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
}

/**
 * Premium Template Unlock Sheet:
 * Reuses the existing Wallet + RealUpiPaymentBottomSheet flow seamlessly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePremiumUnlockSheet(
    template: ContentTemplate,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = RepositoryManager.getCurrentUserId()

    var walletBalance by remember { mutableDoubleStateOf(0.0) }
    var isProcessingWallet by remember { mutableStateOf(false) }
    var showUpiGateway by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            walletBalance = ExclusiveContentRepository.getUserWalletBalance(uid)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14141F),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(template.previewGradientStart), Color(template.previewGradientEnd))
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Premium Lock", tint = Color(0xFFFFD54F), modifier = Modifier.size(24.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PREMIUM TEMPLATE", color = Color(0xFFFFD54F), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        Text(template.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "by ${template.creatorName} • ${template.slots.size} Beat-Synced Slots",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = "₹${template.price.toInt()}",
                            color = Color(0xFFFFD54F),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("What you unlock forever:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("• Unlimited exports in HD without watermark", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                Text("• Full beat-sync cuts, custom fonts, stickers & offline access", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                Text("• Direct support to creator ${template.creatorName}", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
            }

            errorMsg?.let { err ->
                Text(err, color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            // Wallet Unlock CTA
            Button(
                onClick = {
                    if (isProcessingWallet) return@Button
                    isProcessingWallet = true
                    errorMsg = null
                    scope.launch {
                        when (val res = TemplateRepository.unlockPremiumTemplateWithWallet(context, template)) {
                            is ExclusiveUnlockResult.Success -> {
                                isProcessingWallet = false
                                Toast.makeText(context, "Template Unlocked! 🎉", Toast.LENGTH_SHORT).show()
                                onUnlocked()
                            }
                            is ExclusiveUnlockResult.PendingApproval -> {
                                isProcessingWallet = false
                                Toast.makeText(context, "Payment done — waiting for creator approval!", Toast.LENGTH_LONG).show()
                                onDismiss()
                            }
                            is ExclusiveUnlockResult.InsufficientWallet -> {
                                isProcessingWallet = false
                                errorMsg = "Wallet balance ₹${res.currentBalance.toInt()} is low. Pay directly via UPI below!"
                            }
                            is ExclusiveUnlockResult.Failure -> {
                                isProcessingWallet = false
                                errorMsg = res.message
                            }
                        }
                    }
                },
                enabled = !isProcessingWallet,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("template_unlock_wallet_btn")
            ) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isProcessingWallet) "Unlocking..." else "Unlock with Wallet (Bal: ₹${walletBalance.toInt()})",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }

            // Direct UPI / Razorpay CTA (Reusing RealUpiPaymentBottomSheet)
            OutlinedButton(
                onClick = { showUpiGateway = true },
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("template_unlock_upi_btn")
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pay ₹${template.price.toInt()} via UPI / QR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }

    if (showUpiGateway) {
        RealUpiPaymentBottomSheet(
            title = "Unlock ${template.title}",
            subtitle = "Premium Template by ${template.creatorName}",
            amountRupees = template.price.coerceAtLeast(10.0),
            productType = PaymentProductType.INDIVIDUAL_POST_UNLOCK,
            productId = template.id,
            creatorId = template.creatorId,
            contentId = template.id,
            onDismiss = { showUpiGateway = false },
            onPaymentVerified = { order ->
                scope.launch {
                    TemplateRepository.recordGatewayTemplateUnlock(context, template, order.orderId)
                    showUpiGateway = false
                    Toast.makeText(context, "Premium Template Unlocked via UPI! 🎉", Toast.LENGTH_SHORT).show()
                    onUnlocked()
                }
            },
            onUtrSubmitted = { order, utr ->
                scope.launch {
                    TemplateRepository.recordGatewayTemplateUnlock(context, template, utr.ifBlank { order.orderId })
                    showUpiGateway = false
                    Toast.makeText(context, "Payment recorded — Template Unlocked! 🎉", Toast.LENGTH_SHORT).show()
                    onUnlocked()
                }
            }
        )
    }
}

fun formatUsageCount(count: Long): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000f)
    count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000f)
    else -> count.toString()
}
