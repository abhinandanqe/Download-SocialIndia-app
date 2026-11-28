package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.GifItem
import com.example.model.GifRepository
import com.example.model.StorySticker
import com.example.utils.SoundManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

// Data Models for Active Elements
data class DrawnStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

data class StoryTextElement(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var color: Color = Color.White,
    var backgroundColor: Color = Color.Transparent,
    var fontIndex: Int = 0,
    var alignment: TextAlign = TextAlign.Center,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var scale: Float = 1f,
    var rotation: Float = 0f
)

data class StoryStickerElement(
    val id: String = UUID.randomUUID().toString(),
    val sticker: StorySticker,
    val gifUrl: String? = null,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var scale: Float = 1f,
    var rotation: Float = 0f
)

// Font Options
enum class StoryFont(val displayName: String, val fontFamily: FontFamily, val fontWeight: FontWeight, val fontStyle: FontStyle = FontStyle.Normal) {
    CLASSIC("Classic", FontFamily.SansSerif, FontWeight.Bold),
    MODERN("Modern", FontFamily.SansSerif, FontWeight.ExtraBold),
    NEON("Neon", FontFamily.Cursive, FontWeight.Bold, FontStyle.Italic),
    TYPEWRITER("Typewriter", FontFamily.Monospace, FontWeight.Medium),
    STRONG("Strong", FontFamily.Default, FontWeight.Black)
}

// Preset Colors
val STORY_COLOR_PALETTE = listOf(
    Color.White,
    Color.Black,
    Color(0xFFEE1D52), // Brand Pink/Red
    Color(0xFFFF5722), // Sunset Orange
    Color(0xFFFFC107), // Gold
    Color(0xFF10B981), // Instagram Emerald Green
    Color(0xFF00E5FF), // Electric Cyan
    Color(0xFF3F51B5), // Royal Blue
    Color(0xFF9C27B0), // Purple
    Color(0xFFE91E63)  // Neon Magenta
)

// Preset Filter Matrix Colors
val STORY_FILTER_NAMES = listOf("Normal", "Warm", "Cool", "Vintage", "Vivid", "Noir", "Glow")
val STORY_FILTER_TINTS = listOf(
    Color.Transparent,
    Color(0x33FF9800), // Warm
    Color(0x3300BCD4), // Cool
    Color(0x44795548), // Vintage
    Color(0x22E91E63), // Vivid
    Color(0x88333333), // Noir
    Color(0x339C27B0)  // Glow
)

/**
 * Full-screen Drawing Mode Overlay
 */
@Composable
fun StoryDrawOverlay(
    strokes: List<DrawnStroke>,
    currentPoints: List<Offset>,
    selectedColor: Color,
    selectedStrokeWidth: Float,
    onPointAdded: (Offset) -> Unit,
    onStrokeFinished: () -> Unit,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthSelected: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
    ) {
        // Drawing Surface
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onPointAdded(offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            onPointAdded(change.position)
                        },
                        onDragEnd = {
                            onStrokeFinished()
                        },
                        onDragCancel = {
                            onStrokeFinished()
                        }
                    )
                }
        ) {
            // Render completed strokes
            for (stroke in strokes) {
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

            // Render current stroke
            if (currentPoints.size > 1) {
                val path = Path().apply {
                    moveTo(currentPoints.first().x, currentPoints.first().y)
                    for (i in 1 until currentPoints.size) {
                        lineTo(currentPoints[i].x, currentPoints[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = selectedColor,
                    style = Stroke(
                        width = selectedStrokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            } else if (currentPoints.size == 1) {
                drawCircle(
                    color = selectedColor,
                    radius = selectedStrokeWidth / 2,
                    center = currentPoints.first()
                )
            }
        }

        // Top Controls: Undo, Clear, Done
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = {
                        SoundManager.playClickSound()
                        onUndo()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Undo", tint = Color.White)
                }
                IconButton(
                    onClick = {
                        SoundManager.playClickSound()
                        onClear()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear All", tint = Color.White)
                }
            }

            // Brush Size Selector
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val widths = listOf(6f, 14f, 26f, 42f)
                widths.forEach { w ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable {
                                SoundManager.playClickSound()
                                onStrokeWidthSelected(w)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size((w / 2).coerceIn(4f, 20f).dp)
                                .background(
                                    if (selectedStrokeWidth == w) Color(0xFFEE1D52) else Color.White,
                                    CircleShape
                                )
                        )
                    }
                }
            }

            Button(
                onClick = {
                    SoundManager.playClickSound()
                    onDone()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Done", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // Bottom Color Palette
        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(STORY_COLOR_PALETTE) { col ->
                val isSelected = selectedColor == col
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 38.dp else 30.dp)
                        .clip(CircleShape)
                        .background(col)
                        .border(
                            width = if (isSelected) 3.dp else 1.5.dp,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .clickable {
                            SoundManager.playClickSound()
                            onColorSelected(col)
                        }
                )
            }
        }
    }
}

/**
 * Full-screen Typography & Text Editor Modal
 */
@Composable
fun StoryTextEditorModal(
    initialText: String = "",
    initialColor: Color = Color.White,
    initialBgColor: Color = Color.Transparent,
    initialFontIndex: Int = 0,
    initialAlignment: TextAlign = TextAlign.Center,
    onDone: (text: String, color: Color, bgColor: Color, fontIndex: Int, alignment: TextAlign) -> Unit,
    onDismiss: () -> Unit
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(initialText)) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var backgroundMode by remember { mutableIntStateOf(if (initialBgColor == Color.Transparent) 0 else if (initialBgColor.alpha < 0.9f) 1 else 2) }
    var fontIndex by remember { mutableIntStateOf(initialFontIndex) }
    var alignment by remember { mutableStateOf(initialAlignment) }

    val fonts = StoryFont.values()
    val activeFont = fonts[fontIndex % fonts.size]

    val effectiveBgColor = remember(backgroundMode, selectedColor) {
        when (backgroundMode) {
            0 -> Color.Transparent
            1 -> if (selectedColor == Color.Black) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
            2 -> if (selectedColor == Color.White) Color(0xFFEE1D52) else selectedColor
            else -> Color.Transparent
        }
    }
    val effectiveTextColor = remember(backgroundMode, selectedColor) {
        if (backgroundMode == 2 && selectedColor != Color.White) Color.White else selectedColor
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Font Switcher Pill
            Button(
                onClick = {
                    SoundManager.playClickSound()
                    fontIndex = (fontIndex + 1) % fonts.size
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(activeFont.displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Alignment Toggle
                IconButton(
                    onClick = {
                        SoundManager.playClickSound()
                        alignment = when (alignment) {
                            TextAlign.Center -> TextAlign.Start
                            TextAlign.Start -> TextAlign.End
                            else -> TextAlign.Center
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        when (alignment) {
                            TextAlign.Start -> Icons.Default.FormatAlignLeft
                            TextAlign.End -> Icons.Default.FormatAlignRight
                            else -> Icons.Default.FormatAlignCenter
                        },
                        contentDescription = "Alignment",
                        tint = Color.White
                    )
                }

                // Background Style Toggle (None / Highlight / Solid)
                IconButton(
                    onClick = {
                        SoundManager.playClickSound()
                        backgroundMode = (backgroundMode + 1) % 3
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        when (backgroundMode) {
                            1 -> Icons.Filled.FormatColorFill
                            2 -> Icons.Filled.Layers
                            else -> Icons.Outlined.FormatColorFill
                        },
                        contentDescription = "Highlight",
                        tint = if (backgroundMode > 0) Color(0xFFEE1D52) else Color.White
                    )
                }

                // Done Button
                Button(
                    onClick = {
                        SoundManager.playClickSound()
                        if (textFieldValue.text.trim().isNotEmpty()) {
                            onDone(
                                textFieldValue.text.trim(),
                                effectiveTextColor,
                                effectiveBgColor,
                                fontIndex,
                                alignment
                            )
                        } else {
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Done", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Center Input Field
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    textStyle = TextStyle(
                        color = effectiveTextColor,
                        fontSize = 32.sp,
                        fontFamily = activeFont.fontFamily,
                        fontWeight = activeFont.fontWeight,
                        fontStyle = activeFont.fontStyle,
                        textAlign = alignment,
                        shadow = if (activeFont == StoryFont.NEON) {
                            Shadow(color = selectedColor.copy(alpha = 0.8f), blurRadius = 16f)
                        } else {
                            Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(2f, 2f), blurRadius = 6f)
                        }
                    ),
                    cursorBrush = SolidColor(Color(0xFFEE1D52)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (textFieldValue.text.trim().isNotEmpty()) {
                                onDone(
                                    textFieldValue.text.trim(),
                                    effectiveTextColor,
                                    effectiveBgColor,
                                    fontIndex,
                                    alignment
                                )
                            } else {
                                onDismiss()
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (effectiveBgColor != Color.Transparent) {
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(effectiveBgColor)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            } else {
                                Modifier
                            }
                        ),
                    decorationBox = { innerTextField ->
                        Box(
                            contentAlignment = when (alignment) {
                                TextAlign.Start -> Alignment.CenterStart
                                TextAlign.End -> Alignment.CenterEnd
                                else -> Alignment.Center
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (textFieldValue.text.isEmpty()) {
                                Text(
                                    "Type something...",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 32.sp,
                                    fontFamily = activeFont.fontFamily,
                                    textAlign = alignment
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // Mention auto-suggestion overlay
                val mentionQuery = extractMentionQuery(textFieldValue.text, textFieldValue.selection.start)
                if (mentionQuery != null) {
                    MentionSuggestions(
                        query = mentionQuery,
                        onUserSelected = { user ->
                            val (newText, newCursor) = replaceMention(
                                textFieldValue.text,
                                textFieldValue.selection.start,
                                user.username
                            )
                            textFieldValue = TextFieldValue(newText, androidx.compose.ui.text.TextRange(newCursor))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }

        // Bottom Color Palette
        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(STORY_COLOR_PALETTE) { col ->
                val isSelected = selectedColor == col
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 38.dp else 30.dp)
                        .clip(CircleShape)
                        .background(col)
                        .border(
                            width = if (isSelected) 3.dp else 1.5.dp,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .clickable {
                            SoundManager.playClickSound()
                            selectedColor = col
                        }
                )
            }
        }
    }
}

/**
 * Interactive Stickers & GIFs Modal Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryStickerPickerSheet(
    onDismiss: () -> Unit,
    onStickerSelected: (StorySticker) -> Unit,
    onGifSelected: (String) -> Unit,
    onOpenPollBuilder: () -> Unit,
    onOpenQuestionBuilder: () -> Unit,
    onOpenQuizBuilder: () -> Unit,
    onOpenSliderBuilder: () -> Unit,
    onOpenAddYoursBuilder: () -> Unit,
    onOpenMentionBuilder: () -> Unit,
    onOpenLocationBuilder: () -> Unit,
    onOpenHashtagBuilder: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Stickers, 1 = GIFs
    var gifSearchQuery by remember { mutableStateOf("") }
    var selectedGifCategory by remember { mutableStateOf("trending") }
    var searchedGifs by remember { mutableStateOf<List<GifItem>>(emptyList()) }
    var isSearchingGifs by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(gifSearchQuery, selectedGifCategory) {
        if (gifSearchQuery.isNotBlank()) {
            isSearchingGifs = true
            val res = GifRepository.searchGifs(gifSearchQuery)
            searchedGifs = res
            isSearchingGifs = false
        } else {
            searchedGifs = GifRepository.CURATED_GIFS.filter {
                if (selectedGifCategory == "trending") true else it.category == selectedGifCategory
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.Gray)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp)
        ) {
            // Tab Selector: Stickers / GIFs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF2C2C2E),
                    contentColor = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .width(240.dp)
                        .height(38.dp),
                    indicator = {},
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            SoundManager.playClickSound()
                            selectedTab = 0
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selectedTab == 0) Color(0xFFEE1D52) else Color.Transparent)
                    ) {
                        Text("Stickers", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            SoundManager.playClickSound()
                            selectedTab = 1
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selectedTab == 1) Color(0xFFEE1D52) else Color.Transparent)
                    ) {
                        Text("GIFs", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            if (selectedTab == 0) {
                // Interactive Stickers Grid
                val stickerActions = listOf(
                    Triple("POLL", Icons.Default.Poll, Color(0xFF2196F3)) to onOpenPollBuilder,
                    Triple("QUESTIONS", Icons.Default.HelpOutline, Color(0xFFE91E63)) to onOpenQuestionBuilder,
                    Triple("QUIZ", Icons.Default.Quiz, Color(0xFF9C27B0)) to onOpenQuizBuilder,
                    Triple("SLIDER", Icons.Default.LinearScale, Color(0xFFFF9800)) to onOpenSliderBuilder,
                    Triple("ADD YOURS", Icons.Default.AddBox, Color(0xFF10B981)) to onOpenAddYoursBuilder,
                    Triple("@MENTION", Icons.Default.AlternateEmail, Color(0xFFFF5722)) to onOpenMentionBuilder,
                    Triple("LOCATION", Icons.Default.LocationOn, Color(0xFF00BCD4)) to onOpenLocationBuilder,
                    Triple("#HASHTAG", Icons.Default.Tag, Color(0xFF3F51B5)) to onOpenHashtagBuilder
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    items(stickerActions) { (info, action) ->
                        val (title, icon, color) = info
                        Card(
                            onClick = {
                                SoundManager.playClickSound()
                                action()
                                onDismiss()
                            },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(color.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(22.dp))
                                }
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Quick Emojis Section
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        Text(
                            "Quick Emojis",
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    val quickEmojis = listOf("🔥", "❤️", "😂", "😍", "🎉", "👏", "✨", "💯", "🙌", "🚀", "🍕", "🐶")
                    items(quickEmojis.size) { idx ->
                        val emoji = quickEmojis[idx]
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF252528))
                                .clickable {
                                    SoundManager.playClickSound()
                                    onStickerSelected(
                                        StorySticker(
                                            id = UUID.randomUUID().toString(),
                                            type = "emoji",
                                            emoji = emoji,
                                            question = emoji
                                        )
                                    )
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 28.sp)
                        }
                    }
                }
            } else {
                // GIFs Tab with Search & Categories
                OutlinedTextField(
                    value = gifSearchQuery,
                    onValueChange = { gifSearchQuery = it },
                    placeholder = { Text("Search GIPHY & Curated GIFs...", color = Color.Gray, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        if (gifSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { gifSearchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2C2C2E),
                        unfocusedContainerColor = Color(0xFF2C2C2E),
                        focusedBorderColor = Color(0xFFEE1D52),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    singleLine = true
                )

                // Category Chips
                val categories = listOf("trending", "bollywood", "cricket", "funny", "love", "dance")
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val isSel = selectedGifCategory == cat && gifSearchQuery.isEmpty()
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                SoundManager.playClickSound()
                                gifSearchQuery = ""
                                selectedGifCategory = cat
                            },
                            label = { Text(cat.capitalize(), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFEE1D52),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF2C2C2E),
                                labelColor = Color.LightGray
                            ),
                            border = null
                        )
                    }
                }

                if (isSearchingGifs) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFEE1D52), modifier = Modifier.size(32.dp))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        items(searchedGifs) { item ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2C2C2E))
                                    .clickable {
                                        SoundManager.playClickSound()
                                        onGifSelected(item.fullUrl)
                                        onDismiss()
                                    }
                            ) {
                                AsyncImage(
                                    model = item.previewUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Sticker Builder Dialogs
 */
@Composable
fun PollBuilderDialog(
    onDismiss: () -> Unit,
    onCreatePoll: (question: String, option1: String, option2: String) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var opt1 by remember { mutableStateOf("Yes") }
    var opt2 by remember { mutableStateOf("No") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF2196F3).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Poll, contentDescription = null, tint = Color(0xFF2196F3))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Create Poll", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        placeholder = { Text("Ask a question...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2196F3),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = opt1,
                            onValueChange = { opt1 = it },
                            placeholder = { Text("Option 1") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2196F3),
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black
                            )
                        )
                        OutlinedTextField(
                            value = opt2,
                            onValueChange = { opt2 = it },
                            placeholder = { Text("Option 2") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2196F3),
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                SoundManager.playClickSound()
                                if (question.isNotBlank()) {
                                    onCreatePoll(question.trim(), opt1.ifEmpty { "Yes" }, opt2.ifEmpty { "No" })
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                            enabled = question.isNotBlank()
                        ) {
                            Text("Done", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestionBuilderDialog(
    onDismiss: () -> Unit,
    onCreateQuestion: (prompt: String) -> Unit
) {
    var prompt by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFE91E63).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = Color(0xFFE91E63))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Ask Me Anything", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        placeholder = { Text("Ask me a question...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE91E63),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                SoundManager.playClickSound()
                                onCreateQuestion(prompt.ifEmpty { "Ask me a question" })
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                        ) {
                            Text("Done", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuizBuilderDialog(
    onDismiss: () -> Unit,
    onCreateQuiz: (question: String, options: List<String>, correctIndex: Int) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var optA by remember { mutableStateOf("") }
    var optB by remember { mutableStateOf("") }
    var optC by remember { mutableStateOf("") }
    var optD by remember { mutableStateOf("") }
    var correctIndex by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(0.95f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF9C27B0).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Quiz, contentDescription = null, tint = Color(0xFF9C27B0))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Create Quiz", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                    Text("Tap letter to mark correct answer", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        placeholder = { Text("Quiz Question...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF9C27B0),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val options = listOf(optA to { t: String -> optA = t }, optB to { t: String -> optB = t }, optC to { t: String -> optC = t }, optD to { t: String -> optD = t })
                    val labels = listOf("A", "B", "C", "D")

                    options.forEachIndexed { i, (text, setText) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (correctIndex == i) Color(0xFF4CAF50) else Color.LightGray)
                                    .clickable {
                                        SoundManager.playClickSound()
                                        correctIndex = i
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(labels[i], color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = text,
                                onValueChange = setText,
                                placeholder = { Text("Option ${labels[i]}") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (correctIndex == i) Color(0xFF4CAF50) else Color(0xFF9C27B0),
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                SoundManager.playClickSound()
                                val list = listOf(optA, optB, optC, optD).filter { it.isNotBlank() }
                                if (question.isNotBlank() && list.size >= 2) {
                                    onCreateQuiz(question.trim(), list, correctIndex.coerceAtMost(list.size - 1))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                            enabled = question.isNotBlank() && optA.isNotBlank() && optB.isNotBlank()
                        ) {
                            Text("Done", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmojiSliderBuilderDialog(
    onDismiss: () -> Unit,
    onCreateSlider: (question: String, emoji: String) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("😍") }
    val emojis = listOf("😍", "🔥", "😂", "🚀", "❤️", "👏", "💯", "🤯")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFFF9800).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(selectedEmoji, fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Emoji Slider", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        placeholder = { Text("Ask a question...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF9800),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Select Emoji:", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(emojis) { em ->
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedEmoji == em) Color(0xFFFF9800).copy(alpha = 0.3f) else Color(0xFFF0F0F0))
                                    .clickable {
                                        SoundManager.playClickSound()
                                        selectedEmoji = em
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(em, fontSize = 20.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                SoundManager.playClickSound()
                                onCreateSlider(question.ifEmpty { "How much do you like it?" }, selectedEmoji)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                        ) {
                            Text("Done", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleTextPromptDialog(
    title: String,
    hint: String,
    prefix: String = "",
    icon: ImageVector,
    iconColor: Color,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(iconColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = iconColor)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        prefix = if (prefix.isNotEmpty()) {
                            { Text(prefix, fontWeight = FontWeight.Bold, color = iconColor) }
                        } else null,
                        placeholder = { Text(hint) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = iconColor,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                SoundManager.playClickSound()
                                if (text.isNotBlank()) {
                                    onSubmit(text.trim())
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = iconColor),
                            enabled = text.isNotBlank()
                        ) {
                            Text("Done", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
