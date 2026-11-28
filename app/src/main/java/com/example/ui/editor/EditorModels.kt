package com.example.ui.editor

import android.app.ActivityManager
import android.content.Context
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.api.ITunesTrack
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Core data models, 16 bundled Google Fonts (including 5 Hindi/Devanagari fonts),
 * Instagram-style color filter matrices, undo/redo history stack, low-end device
 * detection, and offline-first JSON draft persistence for the Social India Pro Editor.
 */

enum class AspectRatioPreset(val label: String, val ratio: Float) {
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_4_5("4:5", 4f / 5f),
    RATIO_1_1("1:1", 1f)
}

enum class EditorTool {
    NONE,
    TIMELINE,
    SPEED_AUDIO,
    FILTERS,
    ADJUST,
    TEXT,
    STICKERS,
    DRAW,
    MUSIC,
    VOICE_CAPTIONS,
    CROP_COVER,
    DRAFTS,
    MORE
}

@Serializable
data class EditorClip(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val isVideo: Boolean = false,
    val durationMs: Long = 10_000L,
    val trimStartFraction: Float = 0f,
    val trimEndFraction: Float = 1f,
    val speed: Float = 1f, // 0.3f .. 3.0f
    val volume: Float = 1f, // 0f .. 1f
    val isMuted: Boolean = false,
    val isReversed: Boolean = false,
    val rotationDeg: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
) {
    val effectiveDurationMs: Long
        get() = (((trimEndFraction - trimStartFraction).coerceIn(0.05f, 1f) * durationMs) / speed.coerceIn(0.3f, 3.0f))
            .toLong()
            .coerceAtLeast(300L)
}

@Serializable
data class ColorAdjustments(
    val brightness: Float = 0f,  // -0.5f .. 0.5f
    val contrast: Float = 1f,    // 0.5f .. 1.8f
    val saturation: Float = 1f,  // 0f .. 2.0f
    val warmth: Float = 0f,      // -1.0f .. 1.0f
    val fade: Float = 0f,        // 0f .. 1.0f
    val vignette: Float = 0f,    // 0f .. 1.0f
    val sharpen: Float = 0f      // 0f .. 1.0f
) {
    val isDefault: Boolean
        get() = brightness == 0f && contrast == 1f && saturation == 1f &&
            warmth == 0f && fade == 0f && vignette == 0f && sharpen == 0f
}

data class EditorFilterPreset(
    val id: String,
    val name: String,
    val brightnessDelta: Float = 0f,
    val contrastMul: Float = 1f,
    val saturationMul: Float = 1f,
    val warmthDelta: Float = 0f,
    val fadeDelta: Float = 0f,
    val tintColor: Color = Color.Transparent,
    val tintAlpha: Float = 0f
)

val EDITOR_FILTER_PRESETS: List<EditorFilterPreset> = listOf(
    EditorFilterPreset("normal", "Original"),
    EditorFilterPreset("clarendon", "Clarendon", contrastMul = 1.22f, saturationMul = 1.28f, warmthDelta = -0.10f, tintColor = Color(0xFF4FC3F7), tintAlpha = 0.06f),
    EditorFilterPreset("juno", "Juno", brightnessDelta = 0.05f, contrastMul = 1.15f, saturationMul = 1.35f, warmthDelta = 0.20f, tintColor = Color(0xFFFFB74D), tintAlpha = 0.07f),
    EditorFilterPreset("lark", "Lark", brightnessDelta = 0.08f, contrastMul = 0.96f, saturationMul = 1.14f, warmthDelta = -0.06f),
    EditorFilterPreset("gingham", "Gingham", brightnessDelta = 0.04f, contrastMul = 0.90f, saturationMul = 0.80f, fadeDelta = 0.25f, tintColor = Color(0xFFE1BEE7), tintAlpha = 0.08f),
    EditorFilterPreset("moon", "Moon", brightnessDelta = 0.05f, contrastMul = 1.18f, saturationMul = 0.0f, fadeDelta = 0.12f),
    EditorFilterPreset("ludwig", "Ludwig", brightnessDelta = 0.02f, contrastMul = 1.12f, saturationMul = 0.88f, warmthDelta = 0.14f),
    EditorFilterPreset("aden", "Aden", brightnessDelta = 0.06f, contrastMul = 0.88f, saturationMul = 0.82f, fadeDelta = 0.22f, tintColor = Color(0xFFF48FB1), tintAlpha = 0.09f),
    EditorFilterPreset("perpetua", "Perpetua", brightnessDelta = 0.04f, contrastMul = 1.05f, saturationMul = 1.10f, warmthDelta = -0.14f, tintColor = Color(0xFF80CBC4), tintAlpha = 0.08f),
    EditorFilterPreset("reyes", "Reyes", brightnessDelta = 0.07f, contrastMul = 0.86f, saturationMul = 0.78f, warmthDelta = 0.24f, fadeDelta = 0.28f),
    EditorFilterPreset("crema", "Crema", brightnessDelta = 0.02f, contrastMul = 0.95f, saturationMul = 0.86f, warmthDelta = 0.22f, tintColor = Color(0xFFFFE0B2), tintAlpha = 0.07f),
    EditorFilterPreset("delhi_gold", "Delhi Gold", brightnessDelta = 0.05f, contrastMul = 1.16f, saturationMul = 1.32f, warmthDelta = 0.35f, tintColor = Color(0xFFFFD54F), tintAlpha = 0.08f)
)

/**
 * Builds a single combined 4x5 ColorMatrix for real-time Compose ColorFilter
 * AND offscreen Bitmap export rendering (exact parity between preview and export).
 */
fun buildCombinedAndroidColorMatrix(
    filter: EditorFilterPreset,
    adjustments: ColorAdjustments
): AndroidColorMatrix {
    val result = AndroidColorMatrix()

    // 1. Saturation
    val totalSat = (filter.saturationMul * adjustments.saturation).coerceIn(0f, 2.5f)
    val satMatrix = AndroidColorMatrix().apply { setSaturation(totalSat) }
    result.postConcat(satMatrix)

    // 2. Contrast + Sharpen micro-contrast + Brightness + Fade lift
    val totalContrast = (filter.contrastMul * adjustments.contrast + adjustments.sharpen * 0.22f).coerceIn(0.4f, 2.4f)
    val totalFade = (filter.fadeDelta + adjustments.fade).coerceIn(0f, 1f)
    val totalBright = ((filter.brightnessDelta + adjustments.brightness) * 255f) + (totalFade * 36f)
    val contrastOffset = (-0.5f * totalContrast + 0.5f) * 255f
    val fadeScale = 1f - (totalFade * 0.16f)
    val finalScale = totalContrast * fadeScale
    val finalOffset = contrastOffset * fadeScale + totalBright

    val bcMatrix = AndroidColorMatrix(
        floatArrayOf(
            finalScale, 0f, 0f, 0f, finalOffset,
            0f, finalScale, 0f, 0f, finalOffset,
            0f, 0f, finalScale, 0f, finalOffset,
            0f, 0f, 0f, 1f, 0f
        )
    )
    result.postConcat(bcMatrix)

    // 3. Warmth (R/B channel balance)
    val totalWarmth = (filter.warmthDelta + adjustments.warmth).coerceIn(-1.2f, 1.2f)
    if (totalWarmth != 0f) {
        val rScale = (1f + totalWarmth * 0.16f).coerceIn(0.7f, 1.35f)
        val gScale = (1f + totalWarmth * 0.03f).coerceIn(0.85f, 1.15f)
        val bScale = (1f - totalWarmth * 0.16f).coerceIn(0.7f, 1.35f)
        val warmMatrix = AndroidColorMatrix(
            floatArrayOf(
                rScale, 0f, 0f, 0f, 0f,
                0f, gScale, 0f, 0f, 0f,
                0f, 0f, bScale, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        result.postConcat(warmMatrix)
    }

    return result
}

fun buildCombinedComposeColorMatrix(
    filter: EditorFilterPreset,
    adjustments: ColorAdjustments
): ColorMatrix = ColorMatrix(buildCombinedAndroidColorMatrix(filter, adjustments).array)

/**
 * 16 Bundled Google Fonts (5 Hindi / Devanagari + 11 English / Display / Script / Mono).
 */
data class EditorFontOption(
    val key: String,
    val displayName: String,
    val fontResId: Int,
    val isHindi: Boolean = false
) {
    val fontFamily: FontFamily by lazy {
        FontFamily(Font(fontResId, FontWeight.Normal))
    }
}

val EDITOR_FONTS: List<EditorFontOption> = listOf(
    // Hindi / Devanagari Fonts (5)
    EditorFontOption("noto_devanagari", "हिन्दी Modern", R.font.noto_sans_devanagari, isHindi = true),
    EditorFontOption("rozha_one", "रोज़ा Bold", R.font.rozha_one, isHindi = true),
    EditorFontOption("kalam", "कलम Hand", R.font.kalam, isHindi = true),
    EditorFontOption("yatra_one", "यात्रा Brush", R.font.yatra_one, isHindi = true),
    EditorFontOption("hind", "हिन्द Clean", R.font.hind, isHindi = true),
    // English & Stylistic Fonts (11)
    EditorFontOption("poppins", "Poppins", R.font.poppins),
    EditorFontOption("montserrat", "Montserrat", R.font.montserrat),
    EditorFontOption("playfair", "Playfair", R.font.playfair_display),
    EditorFontOption("pacifico", "Pacifico", R.font.pacifico),
    EditorFontOption("bebas_neue", "BEBAS", R.font.bebas_neue),
    EditorFontOption("space_grotesk", "Neon Tech", R.font.space_grotesk),
    EditorFontOption("lobster", "Lobster", R.font.lobster),
    EditorFontOption("oswald", "OSWALD", R.font.oswald),
    EditorFontOption("dancing_script", "Signature", R.font.dancing_script),
    EditorFontOption("jetbrains_mono", "Typewriter", R.font.jetbrains_mono),
    EditorFontOption("cinzel", "CINZEL", R.font.cinzel)
)

fun findEditorFont(key: String): EditorFontOption =
    EDITOR_FONTS.firstOrNull { it.key == key } ?: EDITOR_FONTS.first()

@Serializable
enum class TextBgStyle(val label: String) {
    NONE("None"),
    SOLID_CHIP("Chip"),
    FROSTED("Glass"),
    OUTLINE_ONLY("Box"),
    GRADIENT_PILL("Glow")
}

@Serializable
enum class TextAlignMode { START, CENTER, END }

@Serializable
enum class TextAnimStyle(val label: String) {
    NONE("Static"),
    TYPEWRITER("Typewriter"),
    BOUNCE("Bounce"),
    FADE_POP("Pop"),
    GLOW_PULSE("Neon Pulse"),
    SLIDE_UP("Slide Up")
}

@Serializable
data class EditorTextItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val fontKey: String = "poppins",
    val colorArgb: Long = 0xFFFFFFFF,
    val bgColorArgb: Long = 0xCC000000,
    val bgStyle: TextBgStyle = TextBgStyle.SOLID_CHIP,
    val alignment: TextAlignMode = TextAlignMode.CENTER,
    val hasOutline: Boolean = false,
    val animStyle: TextAnimStyle = TextAnimStyle.NONE,
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.45f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val fontSizeSp: Float = 26f
)

@Serializable
enum class StickerKind(val label: String) {
    EMOJI("Emoji"),
    GIF("GIF"),
    LOCATION("Location"),
    MENTION("Mention"),
    HASHTAG("Hashtag"),
    POLL("Poll"),
    QUESTION("Question"),
    COUNTDOWN("Countdown"),
    LINK("Link"),
    MUSIC("Music")
}

@Serializable
data class EditorStickerItem(
    val id: String = UUID.randomUUID().toString(),
    val kind: StickerKind,
    val primaryText: String,
    val secondaryText: String = "",
    val extraOptions: List<String> = emptyList(),
    val mediaUrl: String = "",
    val accentColorArgb: Long = 0xFFEE1D52,
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.5f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f
)

@Serializable
enum class DrawBrushType(val label: String) {
    PEN("Pen"),
    MARKER("Marker"),
    NEON("Neon"),
    ERASER("Eraser")
}

@Serializable
data class NormalizedPoint(val x: Float, val y: Float)

@Serializable
data class EditorDrawStroke(
    val id: String = UUID.randomUUID().toString(),
    val brush: DrawBrushType = DrawBrushType.PEN,
    val colorArgb: Long = 0xFFFFFFFF,
    val strokeWidthDp: Float = 6f,
    val points: List<NormalizedPoint> = emptyList()
)

@Serializable
enum class CaptionStyle(val label: String) {
    CLASSIC_BOX("Classic"),
    BOLD_POP("Bold Pop"),
    KARAOKE_HIGHLIGHT("Karaoke"),
    MINIMAL_OUTLINE("Minimal"),
    HINDI_CINEMA("Cinema")
}

@Serializable
data class CaptionSegment(
    val id: String = UUID.randomUUID().toString(),
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val language: String = "en-IN"
)

@Serializable
data class MusicTrimConfig(
    val trackId: Long = 0L,
    val name: String = "",
    val artist: String = "",
    val artworkUrl: String = "",
    val previewUrl: String = "",
    val startSec: Float = 0f,
    val windowDurationSec: Float = 15f, // 15s .. 30s
    val volume: Float = 0.85f,
    val beatSyncAutoCut: Boolean = false,
    val bpm: Int = 120
) {
    fun toITunesTrack(): ITunesTrack = ITunesTrack(
        id = trackId,
        name = name,
        artistName = artist,
        previewUrl = previewUrl.ifBlank { null },
        artworkUrl = artworkUrl.ifBlank { null }
    )
}

/**
 * Complete serializable project snapshot powering:
 *  - Unlimited Undo / Redo stack
 *  - Offline auto-save & resume drafts
 */
@Serializable
data class EditorProjectSnapshot(
    val draftId: String = UUID.randomUUID().toString(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_9_16,
    val clips: List<EditorClip> = emptyList(),
    val selectedClipIndex: Int = 0,
    val filterId: String = "normal",
    val adjustments: ColorAdjustments = ColorAdjustments(),
    val textItems: List<EditorTextItem> = emptyList(),
    val stickerItems: List<EditorStickerItem> = emptyList(),
    val drawStrokes: List<EditorDrawStroke> = emptyList(),
    val musicConfig: MusicTrimConfig? = null,
    val voiceoverFilePath: String? = null,
    val voiceoverVolume: Float = 1f,
    val captions: List<CaptionSegment> = emptyList(),
    val captionStyle: CaptionStyle = CaptionStyle.BOLD_POP,
    val captionsEnabled: Boolean = true,
    val coverFrameTimeMs: Long = 0L,
    val coverFrameFilePath: String? = null
)

data class EditorExportResult(
    val exportedUris: List<Uri>,
    val customCoverUri: Uri? = null,
    val selectedMusic: ITunesTrack? = null,
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_9_16,
    val draftId: String? = null
)

/**
 * In-memory Undo / Redo history manager capped at 30 states for low memory usage.
 */
class EditorHistoryStack(initial: EditorProjectSnapshot, private val maxSize: Int = 30) {
    private val undoList = ArrayDeque<EditorProjectSnapshot>().apply { addLast(initial) }
    private val redoList = ArrayDeque<EditorProjectSnapshot>()

    val canUndo: Boolean get() = undoList.size > 1
    val canRedo: Boolean get() = redoList.isNotEmpty()
    val current: EditorProjectSnapshot get() = undoList.last()

    fun commit(next: EditorProjectSnapshot) {
        if (next == undoList.lastOrNull()) return
        undoList.addLast(next.copy(updatedAtMs = System.currentTimeMillis()))
        if (undoList.size > maxSize) undoList.removeFirst()
        redoList.clear()
    }

    fun undo(): EditorProjectSnapshot? {
        if (!canUndo) return null
        val popped = undoList.removeLast()
        redoList.addLast(popped)
        return undoList.last()
    }

    fun redo(): EditorProjectSnapshot? {
        if (!canRedo) return null
        val restored = redoList.removeLast()
        undoList.addLast(restored)
        return restored
    }

    fun reset(snapshot: EditorProjectSnapshot) {
        undoList.clear()
        redoList.clear()
        undoList.addLast(snapshot)
    }
}

/**
 * Low-end device detector: automatically downscales preview resolution and
 * thumbnail strip density on low-RAM devices so playback stays locked at 60fps.
 */
object EditorDeviceProfile {
    fun isLowEndDevice(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am == null) return false
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val totalGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
            am.isLowRamDevice || totalGb <= 3.2
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * 100% Offline-first Draft Persistence stored as JSON files inside `filesDir/editor_drafts/`.
 * Drafts open immediately without network connectivity and auto-save safely.
 */
object OfflineEditorDraftStore {
    private const val TAG = "OfflineEditorDraftStore"
    private const val DIR_NAME = "editor_drafts"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private fun draftsDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun saveDraft(context: Context, snapshot: EditorProjectSnapshot): Boolean {
        return try {
            if (snapshot.clips.isEmpty()) return false
            val file = File(draftsDir(context), "${snapshot.draftId}.json")
            val stamped = snapshot.copy(updatedAtMs = System.currentTimeMillis())
            file.writeText(json.encodeToString(stamped))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save offline draft", e)
            false
        }
    }

    fun listDrafts(context: Context): List<EditorProjectSnapshot> {
        return try {
            val files = draftsDir(context).listFiles { f -> f.extension == "json" } ?: return emptyList()
            files.mapNotNull { file ->
                try {
                    json.decodeFromString<EditorProjectSnapshot>(file.readText())
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping corrupt draft ${file.name}", e)
                    null
                }
            }.sortedByDescending { it.updatedAtMs }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list offline drafts", e)
            emptyList()
        }
    }

    fun loadDraft(context: Context, draftId: String): EditorProjectSnapshot? {
        return try {
            val file = File(draftsDir(context), "$draftId.json")
            if (!file.exists()) return null
            json.decodeFromString<EditorProjectSnapshot>(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load offline draft $draftId", e)
            null
        }
    }

    fun deleteDraft(context: Context, draftId: String): Boolean {
        return try {
            File(draftsDir(context), "$draftId.json").delete()
        } catch (_: Exception) {
            false
        }
    }
}
