package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StickerInteraction
import com.example.model.StorySticker
import com.example.repository.FlagshipFeaturesRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun StoryStickersOverlay(
    stickers: List<StorySticker>,
    storyId: String,
    currentUserId: String,
    currentUsername: String,
    currentUserAvatar: String,
    modifier: Modifier = Modifier,
    onAddYoursClick: (prompt: String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val responses by FlagshipFeaturesRepository.listenStickerResponses(storyId).collectAsState(initial = emptyList())

    Box(modifier = modifier.fillMaxSize()) {
        stickers.forEach { sticker ->
            val userInteraction = responses.find { it.stickerId == sticker.id && it.userId == currentUserId }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = when {
                    sticker.yPercent < 0.35f -> Alignment.TopCenter
                    sticker.yPercent > 0.65f -> Alignment.BottomCenter
                    else -> Alignment.Center
                }
            ) {
                when (sticker.type) {
                    "poll" -> {
                        PollStickerView(
                            sticker = sticker,
                            userVote = userInteraction?.selectedOption,
                            onVote = { optionIdx ->
                                scope.launch {
                                    val inter = StickerInteraction(
                                        storyId = storyId,
                                        userId = currentUserId,
                                        username = currentUsername,
                                        avatarUrl = currentUserAvatar,
                                        stickerId = sticker.id,
                                        stickerType = "poll",
                                        selectedOption = optionIdx
                                    )
                                    FlagshipFeaturesRepository.recordStickerInteraction(inter)
                                }
                            }
                        )
                    }
                    "question" -> {
                        QuestionStickerView(
                            sticker = sticker,
                            onSubmitAnswer = { answer ->
                                scope.launch {
                                    val inter = StickerInteraction(
                                        storyId = storyId,
                                        userId = currentUserId,
                                        username = currentUsername,
                                        avatarUrl = currentUserAvatar,
                                        stickerId = sticker.id,
                                        stickerType = "question",
                                        answerText = answer
                                    )
                                    FlagshipFeaturesRepository.recordStickerInteraction(inter)
                                    Toast.makeText(context, "Response sent to creator!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    "slider" -> {
                        EmojiSliderStickerView(
                            sticker = sticker,
                            initialValue = userInteraction?.sliderValue ?: 0.5f,
                            hasVoted = userInteraction != null,
                            onSlideComplete = { value ->
                                scope.launch {
                                    val inter = StickerInteraction(
                                        storyId = storyId,
                                        userId = currentUserId,
                                        username = currentUsername,
                                        avatarUrl = currentUserAvatar,
                                        stickerId = sticker.id,
                                        stickerType = "slider",
                                        sliderValue = value
                                    )
                                    FlagshipFeaturesRepository.recordStickerInteraction(inter)
                                }
                            }
                        )
                    }
                    "quiz" -> {
                        QuizStickerView(
                            sticker = sticker,
                            userAnswer = userInteraction?.selectedOption,
                            onAnswer = { idx ->
                                scope.launch {
                                    val inter = StickerInteraction(
                                        storyId = storyId,
                                        userId = currentUserId,
                                        username = currentUsername,
                                        avatarUrl = currentUserAvatar,
                                        stickerId = sticker.id,
                                        stickerType = "quiz",
                                        selectedOption = idx
                                    )
                                    FlagshipFeaturesRepository.recordStickerInteraction(inter)
                                }
                            }
                        )
                    }
                    "add_yours" -> {
                        AddYoursStickerView(
                            sticker = sticker,
                            onAddYoursClick = { onAddYoursClick(sticker.addYoursPrompt) }
                        )
                    }
                    "location" -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(sticker.locationName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    "mention" -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE91E63).copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = "@${sticker.mentionUsername}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PollStickerView(
    sticker: StorySticker,
    userVote: Int?,
    onVote: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = sticker.question.ifBlank { "Poll" },
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))

            val opts = if (sticker.options.isNotEmpty()) sticker.options else listOf("Yes", "No")
            opts.forEachIndexed { index, option ->
                val isSelected = userVote == index
                Surface(
                    onClick = { onVote(index) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0xFF1E88E5) else Color(0xFFF0F2F5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isSelected) Color.White else Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuestionStickerView(
    sticker: StorySticker,
    onSubmitAnswer: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        onClick = { showDialog = true },
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = sticker.question.ifBlank { "Ask me a question" },
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF0F2F5),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Type something...",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (showDialog) {
        var answerText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(sticker.question.ifBlank { "Ask me a question" }) },
            text = {
                OutlinedTextField(
                    value = answerText,
                    onValueChange = { answerText = it },
                    placeholder = { Text("Send your response...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (answerText.isNotBlank()) {
                            onSubmitAnswer(answerText.trim())
                            showDialog = false
                        }
                    }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun EmojiSliderStickerView(
    sticker: StorySticker,
    initialValue: Float,
    hasVoted: Boolean,
    onSlideComplete: (Float) -> Unit
) {
    var sliderVal by remember { mutableFloatStateOf(initialValue) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = sticker.question.ifBlank { "How much do you love this?" },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = sliderVal,
                    onValueChange = { sliderVal = it },
                    onValueChangeFinished = { onSlideComplete(sliderVal) },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFE91E63),
                        activeTrackColor = Color(0xFFE91E63)
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(sticker.emoji.ifBlank { "🔥" }, fontSize = 24.sp)
            }
        }
    }
}

@Composable
fun QuizStickerView(
    sticker: StorySticker,
    userAnswer: Int?,
    onAnswer: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = sticker.question.ifBlank { "Guess the answer!" },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))

            sticker.options.forEachIndexed { index, opt ->
                val isSelected = userAnswer == index
                val isCorrect = sticker.correctOptionIndex == index
                val bgColor = when {
                    userAnswer == null -> Color(0xFFF0F2F5)
                    isCorrect -> Color(0xFF4CAF50)
                    isSelected && !isCorrect -> Color(0xFFE53935)
                    else -> Color(0xFFF0F2F5)
                }

                Surface(
                    onClick = { if (userAnswer == null) onAnswer(index) },
                    shape = RoundedCornerShape(10.dp),
                    color = bgColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "${('A' + index)}. $opt",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (userAnswer != null && (isCorrect || isSelected)) Color.White else Color.Black,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddYoursStickerView(
    sticker: StorySticker,
    onAddYoursClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(min = 200.dp, max = 260.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color(0xFF1E88E5), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Yours",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF1E88E5)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = sticker.addYoursPrompt.ifBlank { "Share your best moment 📸" },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onAddYoursClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Yours", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
