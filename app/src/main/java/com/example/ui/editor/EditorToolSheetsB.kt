package com.example.ui.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.utils.VoiceRecorderHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 5. Stickers Bottom Sheet supporting all 10 sticker types:
 *    Emoji, GIF, Location, Mention, Hashtag, Poll, Question, Countdown, Link, Music.
 */
@Composable
fun StickersBottomSheet(
    currentMusic: MusicTrimConfig?,
    onAddSticker: (EditorStickerItem) -> Unit,
    onOpenGifPicker: () -> Unit,
    onDone: () -> Unit
) {
    var selectedKind by remember { mutableStateOf(StickerKind.EMOJI) }
    var primaryInput by remember { mutableStateOf("") }
    var secondaryInput by remember { mutableStateOf("") }
    var option1 by remember { mutableStateOf("Yes 🔥") }
    var option2 by remember { mutableStateOf("No 🙅") }

    val quickEmojis = remember {
        listOf("🔥", "❤️", "😍", "🇮🇳", "✨", "🎉", "😂", "👑", "💯", "🙌", "🥳", "🚀", "💥", "🎵", "📸", "🌟", "🫶", "😎")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12121A))
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        EditorSheetHeader(title = "Stickers", onDone = onDone)

        // Category pills for all 10 sticker types
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StickerKind.entries.forEach { kind ->
                val sel = selectedKind == kind
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (sel) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.08f),
                    modifier = Modifier
                        .clickable {
                            selectedKind = kind
                            primaryInput = ""
                            secondaryInput = ""
                        }
                        .testTag("editor_sticker_tab_${kind.name.lowercase()}")
                ) {
                    Text(
                        text = kind.label,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (selectedKind) {
                StickerKind.EMOJI -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        quickEmojis.chunked(6).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                row.forEach { emoji ->
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.07f))
                                            .clickable {
                                                onAddSticker(EditorStickerItem(kind = StickerKind.EMOJI, primaryText = emoji))
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(emoji, fontSize = 26.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                StickerKind.GIF -> {
                    Button(
                        onClick = onOpenGifPicker,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.GifBox, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Browse Trending GIFs & Stickers", fontWeight = FontWeight.Bold)
                    }
                }
                StickerKind.MUSIC -> {
                    val songTitle = currentMusic?.name?.ifBlank { "Trending Audio" } ?: "Trending Audio"
                    val songArtist = currentMusic?.artist?.ifBlank { "Social India Music" } ?: "Social India Music"
                    Button(
                        onClick = {
                            onAddSticker(
                                EditorStickerItem(
                                    kind = StickerKind.MUSIC,
                                    primaryText = songTitle,
                                    secondaryText = songArtist,
                                    mediaUrl = currentMusic?.artworkUrl.orEmpty()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add \"$songTitle\" Music Sticker", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                StickerKind.POLL -> {
                    StickerTextField(primaryInput, { primaryInput = it }, "Ask a poll question...", "Which vibe wins?")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StickerTextField(option1, { option1 = it }, "Option 1", "Yes", Modifier.weight(1f))
                        StickerTextField(option2, { option2 = it }, "Option 2", "No", Modifier.weight(1f))
                    }
                    StickerAddButton("Add Poll Sticker") {
                        onAddSticker(
                            EditorStickerItem(
                                kind = StickerKind.POLL,
                                primaryText = primaryInput.ifBlank { "Vote Now!" },
                                extraOptions = listOf(option1.ifBlank { "Yes" }, option2.ifBlank { "No" })
                            )
                        )
                    }
                }
                else -> {
                    val placeholder = when (selectedKind) {
                        StickerKind.LOCATION -> "e.g., Mumbai, India"
                        StickerKind.MENTION -> "e.g., ananya.k"
                        StickerKind.HASHTAG -> "e.g., SocialIndia"
                        StickerKind.QUESTION -> "e.g., Ask me anything!"
                        StickerKind.COUNTDOWN -> "e.g., New Drop Live"
                        StickerKind.LINK -> "e.g., socialindia.app"
                        else -> "Enter sticker text"
                    }
                    StickerTextField(primaryInput, { primaryInput = it }, selectedKind.label, placeholder)
                    if (selectedKind == StickerKind.COUNTDOWN) {
                        StickerTextField(secondaryInput, { secondaryInput = it }, "Target Date / Note", "Tomorrow 8:00 PM")
                    }
                    StickerAddButton("Add ${selectedKind.label} Sticker") {
                        onAddSticker(
                            EditorStickerItem(
                                kind = selectedKind,
                                primaryText = primaryInput.ifBlank { placeholder.removePrefix("e.g., ") },
                                secondaryText = secondaryInput
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp) },
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFEE1D52),
            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )
}

@Composable
private fun StickerAddButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(46.dp)
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/**
 * 6. Music Library Search, 15-30s Section Picker, & Beat-Sync Auto-Cut Sheet.
 */
@Composable
fun MusicTrimBeatSyncSheet(
    musicConfig: MusicTrimConfig?,
    onOpenMusicLibrary: () -> Unit,
    onUpdateMusic: (MusicTrimConfig?) -> Unit,
    onApplyBeatSyncAutoCut: (Int) -> Unit,
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
            title = "Music & Beat-Sync",
            onReset = if (musicConfig != null) { { onUpdateMusic(null) } } else null,
            onDone = onDone
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onOpenMusicLibrary,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("editor_open_music_library_btn")
            ) {
                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (musicConfig == null) "Search Music Library" else "Change Music Track",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (musicConfig == null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Pick a song from the library to trim your 15–30s highlight and sync clip cuts to the beat.",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                // Selected Track Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (musicConfig.artworkUrl.isNotBlank()) {
                        AsyncImage(
                            model = musicConfig.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1DB954)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Black)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(musicConfig.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(musicConfig.artist.ifBlank { "Selected Audio" }, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1)
                    }
                }

                // 15s - 30s Window Length Picker
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15f, 20f, 25f, 30f).forEach { sec ->
                        val sel = musicConfig.windowDurationSec == sec
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (sel) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.08f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onUpdateMusic(musicConfig.copy(windowDurationSec = sec)) }
                        ) {
                            Text("${sec.toInt()}s clip", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }

                // Waveform Visual + Start Section Scrubber
                val maxStart = (60f - musicConfig.windowDurationSec).coerceAtLeast(1f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1A26))
                ) {
                    val bars = 42
                    val barW = size.width / bars
                    val startFrac = (musicConfig.startSec / 60f).coerceIn(0f, 1f)
                    val endFrac = ((musicConfig.startSec + musicConfig.windowDurationSec) / 60f).coerceIn(0f, 1f)
                    for (i in 0 until bars) {
                        val f = i.toFloat() / bars
                        val inWin = f in startFrac..endFrac
                        val hFrac = 0.25f + (((i * 37) % 10) / 14f)
                        val barH = size.height * hFrac
                        drawLine(
                            color = if (inWin) Color(0xFF1DB954) else Color.White.copy(alpha = 0.25f),
                            start = Offset(i * barW + barW / 2f, (size.height - barH) / 2f),
                            end = Offset(i * barW + barW / 2f, (size.height + barH) / 2f),
                            strokeWidth = (barW * 0.52f).coerceAtLeast(2f)
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Start at: ${musicConfig.startSec.toInt()}s", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text("End at: ${(musicConfig.startSec + musicConfig.windowDurationSec).toInt()}s", color = Color(0xFF4ADE80), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = musicConfig.startSec.coerceIn(0f, maxStart),
                    onValueChange = { onUpdateMusic(musicConfig.copy(startSec = it)) },
                    valueRange = 0f..maxStart,
                    colors = SliderDefaults.colors(activeTrackColor = Color(0xFF1DB954), thumbColor = Color.White)
                )

                // Beat-Sync Auto-Cut Toggle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (musicConfig.beatSyncAutoCut) Color(0xFF1DB954).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, if (musicConfig.beatSyncAutoCut) Color(0xFF1DB954) else Color.White.copy(alpha = 0.14f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Beat-Sync Auto-Cut (${musicConfig.bpm} BPM)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Automatically snaps clip cuts to musical beats", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                        }
                        Switch(
                            checked = musicConfig.beatSyncAutoCut,
                            onCheckedChange = { enabled ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onUpdateMusic(musicConfig.copy(beatSyncAutoCut = enabled))
                                if (enabled) onApplyBeatSyncAutoCut(musicConfig.bpm)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFF1DB954))
                        )
                    }
                }
            }
        }
    }
}

/**
 * 7. Voiceover Recorder (real VoiceRecorderHelper) & Hindi + English Auto-Captions Sheet.
 */
@Composable
fun VoiceoverCaptionsSheet(
    voiceoverFilePath: String?,
    captions: List<CaptionSegment>,
    captionStyle: CaptionStyle,
    captionsEnabled: Boolean,
    totalDurationMs: Long,
    onVoiceoverSaved: (String?) -> Unit,
    onUpdateCaptions: (List<CaptionSegment>, CaptionStyle, Boolean) -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Auto Captions, 1 = Voiceover
    var captionLang by remember { mutableStateOf("hi-IN") } // "hi-IN" or "en-IN"
    var isListening by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }
    var manualCaptionText by remember { mutableStateOf("") }

    val recorder = remember { VoiceRecorderHelper(context) }
    val isRecordingVoice by recorder.isRecording.collectAsStateWithLifecycle()
    val voiceSeconds by recorder.recordingDurationSeconds.collectAsStateWithLifecycle()
    val voiceAmplitude by recorder.amplitude.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            if (recorder.isRecording.value) recorder.stopRecording()
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, "Microphone permission is required for voiceover & captions", Toast.LENGTH_SHORT).show()
        }
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun startSpeechCapture() {
        if (!hasMicPermission()) {
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            speechError = "On-device speech recognition unavailable. Add or edit captions manually below."
            return
        }
        speechError = null
        try {
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, captionLang)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { isListening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListening = false }
                override fun onError(error: Int) {
                    isListening = false
                    speechError = "Could not detect speech (code $error). Try again or type captions below."
                    sr.destroy()
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) {
                        val words = spoken.trim().split(Regex("\\s+"))
                        val chunks = words.chunked(5).map { it.joinToString(" ") }
                        val step = (totalDurationMs / chunks.size.coerceAtLeast(1)).coerceAtLeast(1200L)
                        val generated = chunks.mapIndexed { idx, line ->
                            CaptionSegment(
                                id = "cap_${System.currentTimeMillis()}_$idx",
                                startMs = idx * step,
                                endMs = (idx + 1) * step,
                                text = line,
                                language = captionLang
                            )
                        }
                        onUpdateCaptions(generated, captionStyle, true)
                    }
                    sr.destroy()
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            sr.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            speechError = "Speech error: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12121A))
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        EditorSheetHeader(title = "Voiceover & Auto Captions", onDone = onDone)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Auto Captions (HI/EN)", "Voiceover Mic").forEachIndexed { idx, label ->
                val sel = activeTab == idx
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (sel) Color.White else Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.weight(1f).clickable { activeTab = idx }
                ) {
                    Text(label, color = if (sel) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (activeTab == 0) {
                // Language Switcher: Hindi + English
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("hi-IN" to "हिन्दी (Hindi)", "en-IN" to "English (IN)").forEach { (code, label) ->
                        val sel = captionLang == code
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (sel) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.weight(1f).clickable { captionLang = code }
                        ) {
                            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                    Switch(
                        checked = captionsEnabled,
                        onCheckedChange = { onUpdateCaptions(captions, captionStyle, it) }
                    )
                }

                // Live Dictation Auto-Caption Button
                Button(
                    onClick = { startSpeechCapture() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isListening) Color(0xFFEF4444) else Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("editor_auto_caption_btn")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isListening) "Listening (${if (captionLang == "hi-IN") "Hindi" else "English"})..." else "Speak to Auto-Generate Captions",
                        fontWeight = FontWeight.Bold
                    )
                }

                speechError?.let { err ->
                    Text(err, color = Color(0xFFFFB74D), fontSize = 11.sp)
                }

                // Caption Style Selector (5 styles)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CaptionStyle.entries.forEach { st ->
                        val sel = st == captionStyle
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (sel) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.09f),
                            modifier = Modifier.clickable { onUpdateCaptions(captions, st, true) }
                        ) {
                            Text(st.label, color = if (sel) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }

                // Add manual caption line
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualCaptionText,
                        onValueChange = { manualCaptionText = it },
                        placeholder = { Text("Add caption line (हिन्दी / English)...", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFEE1D52), unfocusedBorderColor = Color.White.copy(alpha = 0.2f), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Button(
                        onClick = {
                            val clean = manualCaptionText.trim()
                            if (clean.isNotEmpty()) {
                                val nextStart = captions.lastOrNull()?.endMs ?: 0L
                                val nextSeg = CaptionSegment(
                                    id = "cap_${System.currentTimeMillis()}",
                                    startMs = nextStart,
                                    endMs = (nextStart + 3000L).coerceAtMost(totalDurationMs.coerceAtLeast(3000L)),
                                    text = clean,
                                    language = captionLang
                                )
                                onUpdateCaptions(captions + nextSeg, captionStyle, true)
                                manualCaptionText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
                    ) {
                        Text("Add", fontWeight = FontWeight.Bold)
                    }
                }

                if (captions.isEmpty()) {
                    Text("No captions yet. Tap Speak or add a line above.", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                } else {
                    captions.forEach { seg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(seg.text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("${formatEditorTime(seg.startMs)} - ${formatEditorTime(seg.endMs)}", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                            }
                            IconButton(onClick = { onUpdateCaptions(captions.filterNot { it.id == seg.id }, captionStyle, captionsEnabled) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Delete caption", tint = Color(0xFFFF6B6B), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            } else {
                // Voiceover Recorder Tab
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isRecordingVoice) "Recording Voiceover • ${formatEditorTime(voiceSeconds * 1000L)}"
                            else if (!voiceoverFilePath.isNullOrBlank()) "Voiceover Track Saved ✅"
                            else "Tap Record to narrate over your Reel",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        // Live Mic Level Bar
                        if (isRecordingVoice) {
                            val norm = (voiceAmplitude / 18000f).coerceIn(0.08f, 1f)
                            LinearProgressIndicator(
                                progress = { norm },
                                color = Color(0xFFEE1D52),
                                trackColor = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    if (!hasMicPermission()) {
                                        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        return@Button
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isRecordingVoice) {
                                        val saved = recorder.stopRecording()
                                        if (saved != null) {
                                            onVoiceoverSaved(saved.absolutePath)
                                            Toast.makeText(context, "Voiceover saved!", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        recorder.startRecording()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isRecordingVoice) Color(0xFFEF4444) else Color(0xFFEE1D52)),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (isRecordingVoice) "Stop Recording" else "Start Recording", fontWeight = FontWeight.Bold)
                            }

                            if (!voiceoverFilePath.isNullOrBlank() && !isRecordingVoice) {
                                OutlinedButton(
                                    onClick = { onVoiceoverSaved(null) },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete voiceover", tint = Color(0xFFFF6B6B))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 8. Crop, Rotate, Flip, Aspect Ratio (9:16, 4:5, 1:1) & Reel Cover-Frame Picker Sheet.
 */
@Composable
fun CropCoverSheet(
    aspectRatio: AspectRatioPreset,
    activeClip: EditorClip?,
    coverFrameTimeMs: Long,
    lowEndMode: Boolean,
    onSelectAspectRatio: (AspectRatioPreset) -> Unit,
    onUpdateClip: (EditorClip) -> Unit,
    onSelectCoverFrame: (Long, String?) -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var coverFrames by remember { mutableStateOf<List<Pair<Long, Bitmap>>>(emptyList()) }
    var isLoadingFrames by remember { mutableStateOf(false) }

    LaunchedEffect(activeClip?.uriString, activeClip?.isVideo) {
        if (activeClip != null && activeClip.isVideo) {
            isLoadingFrames = true
            val (_, extracted) = EditorExportEngine.extractVideoFrames(
                context = context,
                uri = Uri.parse(activeClip.uriString),
                frameCount = if (lowEndMode) 6 else 8,
                lowEndMode = lowEndMode
            )
            coverFrames = extracted
            isLoadingFrames = false
        } else {
            coverFrames = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12121A))
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        EditorSheetHeader(
            title = "Crop, Transform & Cover",
            onReset = {
                onSelectAspectRatio(AspectRatioPreset.RATIO_9_16)
                if (activeClip != null) {
                    onUpdateClip(activeClip.copy(rotationDeg = 0f, flipHorizontal = false, flipVertical = false))
                }
            },
            onDone = onDone
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Aspect Ratio 9:16, 4:5, 1:1
            Text("Aspect Ratio", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectRatioPreset.entries.forEach { preset ->
                    val sel = preset == aspectRatio
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (sel) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectAspectRatio(preset)
                            }
                            .testTag("editor_ratio_${preset.label}")
                    ) {
                        Text(preset.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
                    }
                }
            }

            // Rotate & Flip Controls
            if (activeClip != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransformActionChip(Icons.AutoMirrored.Filled.RotateRight, "Rotate 90°", Modifier.weight(1f)) {
                        onUpdateClip(activeClip.copy(rotationDeg = (activeClip.rotationDeg + 90f) % 360f))
                    }
                    TransformActionChip(Icons.Default.Flip, "Flip H", Modifier.weight(1f), active = activeClip.flipHorizontal) {
                        onUpdateClip(activeClip.copy(flipHorizontal = !activeClip.flipHorizontal))
                    }
                    TransformActionChip(Icons.Default.SwapVert, "Flip V", Modifier.weight(1f), active = activeClip.flipVertical) {
                        onUpdateClip(activeClip.copy(flipVertical = !activeClip.flipVertical))
                    }
                }
            }

            // Reel Cover-Frame Picker
            if (activeClip != null && activeClip.isVideo) {
                Text("Reel Cover Frame", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (isLoadingFrames) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFFEE1D52))
                        Text("Extracting video frames...", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                    }
                } else if (coverFrames.isEmpty()) {
                    Text("Cover frame preview unavailable for this clip.", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(coverFrames, key = { it.first }) { (timeMs, bmp) ->
                            val sel = kotlin.math.abs(coverFrameTimeMs - timeMs) < 350L
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(88.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(if (sel) 2.5.dp else 1.dp, if (sel) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        scope.launch {
                                            val savedUri = EditorExportEngine.saveCoverFrameAtTime(context, Uri.parse(activeClip.uriString), timeMs, lowEndMode)
                                            onSelectCoverFrame(timeMs, savedUri?.path)
                                        }
                                    }
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Cover at ${formatEditorTime(timeMs)}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Text(
                                    text = formatEditorTime(timeMs),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(vertical = 2.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransformActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (active) Color(0xFFEE1D52).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (active) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.14f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * 9. Offline Auto-Saved Drafts Sheet (works 100% offline!).
 */
@Composable
fun OfflineDraftsSheet(
    currentDraftId: String,
    onResumeDraft: (EditorProjectSnapshot) -> Unit,
    onSaveCurrentNow: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var drafts by remember { mutableStateOf(OfflineEditorDraftStore.listDrafts(context)) }
    val dateFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12121A))
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        EditorSheetHeader(title = "Offline Drafts", onDone = onDone)

        Button(
            onClick = {
                onSaveCurrentNow()
                drafts = OfflineEditorDraftStore.listDrafts(context)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(44.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Save Current Edit to Offline Drafts", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (drafts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.FolderOff, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(38.dp))
                Text("No offline drafts yet", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Your edits auto-save locally so you can resume anytime, even offline.", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(drafts, key = { it.draftId }) { draft ->
                    val isCurrent = draft.draftId == currentDraftId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = if (isCurrent) 0.12f else 0.06f))
                            .border(1.dp, if (isCurrent) Color(0xFFEE1D52) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .clickable { onResumeDraft(draft) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val thumb = draft.clips.firstOrNull()?.uriString
                        if (!thumb.isNullOrBlank()) {
                            AsyncImage(
                                model = Uri.parse(thumb),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isCurrent) "Current Draft (${draft.aspectRatio.label})" else "Draft • ${draft.aspectRatio.label}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${draft.clips.size} clip(s) • ${draft.textItems.size} text • ${draft.stickerItems.size} stickers",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                            Text(
                                text = dateFmt.format(Date(draft.updatedAtMs)),
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 10.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                OfflineEditorDraftStore.deleteDraft(context, draft.draftId)
                                drafts = OfflineEditorDraftStore.listDrafts(context)
                            }
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete draft", tint = Color(0xFFFF6B6B))
                        }
                    }
                }
            }
        }
    }
}
