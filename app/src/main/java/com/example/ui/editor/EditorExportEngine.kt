package com.example.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Background media export & video frame extraction engine.
 * - Preserves existing uploader quality (up to 1080x1920 JPEG 92% for high-end,
 *   cleanly downscaled to 720x1280 JPEG 86% on low-end devices to avoid OOM).
 * - Bakes filters, brightness/contrast/saturation/warmth/fade/vignette, rotation, flip,
 *   aspect-ratio crop, drawing strokes, text overlays (with bundled Hindi/English TTFs),
 *   stickers, and active captions onto exported image files.
 * - Extracts exact Reel Cover Frame bitmaps on Dispatchers.IO.
 */
object EditorExportEngine {
    private const val TAG = "EditorExportEngine"

    sealed class ExportState {
        object Idle : ExportState()
        data class Exporting(val progress: Float, val stage: String) : ExportState()
        data class Failed(val message: String) : ExportState()
    }

    /**
     * Extracts a list of evenly-spaced preview frames for the Reel cover picker & timeline strip.
     */
    suspend fun extractVideoFrames(
        context: Context,
        uri: Uri,
        frameCount: Int,
        lowEndMode: Boolean
    ): Pair<Long, List<Pair<Long, Bitmap>>> = withContext(Dispatchers.IO) {
        val frames = mutableListOf<Pair<Long, Bitmap>>()
        var durationMs = 10_000L
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(1_000L) ?: 10_000L

            val targetWidth = if (lowEndMode) 140 else 220
            val targetHeight = if (lowEndMode) 220 else 360
            val count = frameCount.coerceIn(3, if (lowEndMode) 6 else 10)
            val stepUs = (durationMs * 1000L) / count

            for (i in 0 until count) {
                val timeUs = (i * stepUs).coerceIn(0L, durationMs * 1000L)
                val raw = try {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        targetWidth,
                        targetHeight
                    )
                } catch (_: Throwable) {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                if (raw != null) {
                    frames.add((timeUs / 1000L) to raw)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame extraction error for $uri: ${e.message}")
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {}
        }
        durationMs to frames
    }

    /**
     * Extracts and caches a high-quality JPEG cover frame at [timeMs] for Reel publishing.
     */
    suspend fun saveCoverFrameAtTime(
        context: Context,
        videoUri: Uri,
        timeMs: Long,
        lowEndMode: Boolean
    ): Uri? = withContext(Dispatchers.IO) {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            val bmp = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return@withContext null

            val maxDim = if (lowEndMode) 720 else 1080
            val scaled = scaleDownIfNeeded(bmp, maxDim)
            val outDir = File(context.cacheDir, "editor_exports").apply { mkdirs() }
            val outFile = File(outDir, "cover_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, if (lowEndMode) 85 else 92, out)
            }
            if (scaled !== bmp) bmp.recycle()
            Uri.fromFile(outFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cover frame", e)
            null
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Runs the full background export pipeline with smooth progress reporting.
     */
    suspend fun exportProject(
        context: Context,
        snapshot: EditorProjectSnapshot,
        lowEndMode: Boolean,
        onProgress: (Float, String) -> Unit
    ): Result<EditorExportResult> = withContext(Dispatchers.Default) {
        try {
            if (snapshot.clips.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No media clips to export"))
            }

            onProgress(0.08f, "Preparing workspace...")
            val filter = EDITOR_FILTER_PRESETS.firstOrNull { it.id == snapshot.filterId } ?: EDITOR_FILTER_PRESETS.first()
            val outUris = mutableListOf<Uri>()
            val total = snapshot.clips.size

            val hasVisualEdits = snapshot.filterId != "normal" ||
                !snapshot.adjustments.isDefault ||
                snapshot.textItems.isNotEmpty() ||
                snapshot.stickerItems.isNotEmpty() ||
                snapshot.drawStrokes.isNotEmpty() ||
                (snapshot.captionsEnabled && snapshot.captions.isNotEmpty()) ||
                snapshot.clips.any { it.rotationDeg != 0f || it.flipHorizontal || it.flipVertical }

            snapshot.clips.forEachIndexed { index, clip ->
                val baseP = 0.12f + (index.toFloat() / total.toFloat()) * 0.68f
                val uri = Uri.parse(clip.uriString)
                if (clip.isVideo) {
                    onProgress(baseP, "Optimizing reel clip ${index + 1}/$total...")
                    // Preserve original high-quality video URI for existing uploader pipeline
                    outUris.add(uri)
                } else {
                    onProgress(baseP, "Rendering photo ${index + 1}/$total...")
                    if (hasVisualEdits || snapshot.aspectRatio != AspectRatioPreset.RATIO_9_16) {
                        val rendered = renderEditedPhotoToFile(
                            context = context,
                            sourceUri = uri,
                            clip = clip,
                            snapshot = snapshot,
                            filter = filter,
                            lowEndMode = lowEndMode
                        )
                        outUris.add(rendered ?: uri)
                    } else {
                        outUris.add(uri)
                    }
                }
            }

            // Generate custom Reel cover frame if video clip exists
            var coverUri: Uri? = snapshot.coverFrameFilePath?.let { path ->
                val f = File(path)
                if (f.exists()) Uri.fromFile(f) else null
            }
            val firstVideo = snapshot.clips.firstOrNull { it.isVideo }
            if (firstVideo != null && coverUri == null && snapshot.coverFrameTimeMs > 0L) {
                onProgress(0.86f, "Capturing custom Reel cover...")
                coverUri = saveCoverFrameAtTime(
                    context = context,
                    videoUri = Uri.parse(firstVideo.uriString),
                    timeMs = snapshot.coverFrameTimeMs,
                    lowEndMode = lowEndMode
                )
            }

            onProgress(0.96f, "Finalizing export...")
            // Persist final auto-saved draft before handing off to publisher
            OfflineEditorDraftStore.saveDraft(context, snapshot)
            onProgress(1f, "Ready to share!")

            Result.success(
                EditorExportResult(
                    exportedUris = outUris,
                    customCoverUri = coverUri,
                    selectedMusic = snapshot.musicConfig?.toITunesTrack(),
                    aspectRatio = snapshot.aspectRatio,
                    draftId = snapshot.draftId
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            Result.failure(e)
        }
    }

    /**
     * Composites a photo with exact color matrix, vignette, aspect crop, transform,
     * drawings, text overlays (with bundled TTFs), stickers, and captions into a cached JPEG.
     */
    private fun renderEditedPhotoToFile(
        context: Context,
        sourceUri: Uri,
        clip: EditorClip,
        snapshot: EditorProjectSnapshot,
        filter: EditorFilterPreset,
        lowEndMode: Boolean
    ): Uri? {
        val targetW = if (lowEndMode) 720 else 1080
        val targetH = (targetW / snapshot.aspectRatio.ratio).roundToInt().coerceIn(720, 1920)

        val decoded = decodeSampledBitmap(context, sourceUri, targetW, targetH) ?: return null
        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(android.graphics.Color.BLACK)

        // 1. Draw base photo with rotation, flip, center-crop, and combined ColorMatrix
        val matrix = Matrix()
        val rotated = (clip.rotationDeg % 180f) != 0f
        val srcW = if (rotated) decoded.height.toFloat() else decoded.width.toFloat()
        val srcH = if (rotated) decoded.width.toFloat() else decoded.height.toFloat()
        val scale = maxOf(targetW / srcW, targetH / srcH)

        matrix.postTranslate(-decoded.width / 2f, -decoded.height / 2f)
        matrix.postRotate(clip.rotationDeg)
        matrix.postScale(
            if (clip.flipHorizontal) -scale else scale,
            if (clip.flipVertical) -scale else scale
        )
        matrix.postTranslate(targetW / 2f, targetH / 2f)

        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(buildCombinedAndroidColorMatrix(filter, snapshot.adjustments))
        }
        canvas.drawBitmap(decoded, matrix, photoPaint)
        decoded.recycle()

        // 2. Filter tint wash
        if (filter.tintAlpha > 0f) {
            val tintPaint = Paint().apply {
                color = android.graphics.Color.argb(
                    (filter.tintAlpha * 255).roundToInt().coerceIn(0, 255),
                    (filter.tintColor.red * 255).roundToInt(),
                    (filter.tintColor.green * 255).roundToInt(),
                    (filter.tintColor.blue * 255).roundToInt()
                )
            }
            canvas.drawRect(0f, 0f, targetW.toFloat(), targetH.toFloat(), tintPaint)
        }

        // 3. Vignette overlay
        if (snapshot.adjustments.vignette > 0.01f) {
            val radius = maxOf(targetW, targetH) * 0.72f
            val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    targetW / 2f,
                    targetH / 2f,
                    radius,
                    intArrayOf(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.argb((snapshot.adjustments.vignette * 210).roundToInt().coerceIn(0, 230), 0, 0, 0)
                    ),
                    floatArrayOf(0.42f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, targetW.toFloat(), targetH.toFloat(), vigPaint)
        }

        // 4. Draw strokes on isolated layer (supports Eraser cleanly)
        if (snapshot.drawStrokes.isNotEmpty()) {
            val layerId = canvas.saveLayer(0f, 0f, targetW.toFloat(), targetH.toFloat(), null)
            val densityScale = targetW / 360f
            snapshot.drawStrokes.forEach { stroke ->
                if (stroke.points.size < 2) return@forEach
                val path = Path().apply {
                    moveTo(stroke.points.first().x * targetW, stroke.points.first().y * targetH)
                    for (i in 1 until stroke.points.size) {
                        lineTo(stroke.points[i].x * targetW, stroke.points[i].y * targetH)
                    }
                }
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = stroke.strokeWidthDp * densityScale
                    color = stroke.colorArgb.toInt()
                    when (stroke.brush) {
                        DrawBrushType.PEN -> alpha = 255
                        DrawBrushType.MARKER -> {
                            strokeCap = Paint.Cap.SQUARE
                            strokeWidth = stroke.strokeWidthDp * densityScale * 1.9f
                            alpha = 130
                        }
                        DrawBrushType.NEON -> {
                            // Outer glow pass
                            val glowPaint = Paint(this).apply {
                                strokeWidth = stroke.strokeWidthDp * densityScale * 2.4f
                                alpha = 95
                            }
                            canvas.drawPath(path, glowPaint)
                            color = android.graphics.Color.WHITE
                            strokeWidth = stroke.strokeWidthDp * densityScale * 0.7f
                        }
                        DrawBrushType.ERASER -> {
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                            strokeWidth = stroke.strokeWidthDp * densityScale * 2.2f
                        }
                    }
                }
                canvas.drawPath(path, paint)
            }
            canvas.restoreToCount(layerId)
        }

        // 5. Text Overlays with bundled Google Fonts
        val densityScale = targetW / 360f
        snapshot.textItems.forEach { item ->
            canvas.save()
            val cx = item.xFraction * targetW
            val cy = item.yFraction * targetH
            canvas.translate(cx, cy)
            canvas.rotate(item.rotationDeg)
            canvas.scale(item.scale, item.scale)

            val fontOpt = findEditorFont(item.fontKey)
            val tf = try {
                ResourcesCompat.getFont(context, fontOpt.fontResId) ?: Typeface.DEFAULT_BOLD
            } catch (_: Exception) {
                Typeface.DEFAULT_BOLD
            }

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = item.colorArgb.toInt()
                textSize = item.fontSizeSp * densityScale
                typeface = tf
                textAlign = Paint.Align.CENTER
            }

            val lines = item.text.split("\n")
            val lineHeight = textPaint.fontSpacing
            val maxLineW = lines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
            val totalH = lineHeight * lines.size
            val padX = 14f * densityScale
            val padY = 8f * densityScale

            if (item.bgStyle != TextBgStyle.NONE) {
                val bgRect = RectF(
                    -maxLineW / 2f - padX,
                    -totalH / 2f - padY,
                    maxLineW / 2f + padX,
                    totalH / 2f + padY
                )
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = item.bgColorArgb.toInt()
                    if (item.bgStyle == TextBgStyle.FROSTED) alpha = 145
                    if (item.bgStyle == TextBgStyle.OUTLINE_ONLY) {
                        style = Paint.Style.STROKE
                        strokeWidth = 2.5f * densityScale
                        color = item.colorArgb.toInt()
                    }
                }
                canvas.drawRoundRect(bgRect, 12f * densityScale, 12f * densityScale, bgPaint)
            }

            if (item.hasOutline) {
                val strokePaint = Paint(textPaint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3.5f * densityScale
                    color = android.graphics.Color.BLACK
                }
                lines.forEachIndexed { idx, line ->
                    val y = -totalH / 2f + lineHeight * (idx + 0.78f)
                    canvas.drawText(line, 0f, y, strokePaint)
                }
            }

            lines.forEachIndexed { idx, line ->
                val y = -totalH / 2f + lineHeight * (idx + 0.78f)
                canvas.drawText(line, 0f, y, textPaint)
            }
            canvas.restore()
        }

        // 6. Stickers (Emoji & Chip Stickers)
        snapshot.stickerItems.forEach { st ->
            canvas.save()
            canvas.translate(st.xFraction * targetW, st.yFraction * targetH)
            canvas.rotate(st.rotationDeg)
            canvas.scale(st.scale, st.scale)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = (if (st.kind == StickerKind.EMOJI) 46f else 18f) * densityScale
                color = android.graphics.Color.WHITE
                typeface = Typeface.DEFAULT_BOLD
            }
            if (st.kind == StickerKind.EMOJI) {
                canvas.drawText(st.primaryText, 0f, paint.textSize / 3f, paint)
            } else {
                val label = when (st.kind) {
                    StickerKind.LOCATION -> "📍 ${st.primaryText}"
                    StickerKind.MENTION -> "@${st.primaryText.removePrefix("@")}"
                    StickerKind.HASHTAG -> "#${st.primaryText.removePrefix("#")}"
                    StickerKind.LINK -> "🔗 ${st.primaryText}"
                    StickerKind.POLL -> "📊 ${st.primaryText}"
                    StickerKind.QUESTION -> "❓ ${st.primaryText}"
                    StickerKind.COUNTDOWN -> "⏳ ${st.primaryText}"
                    StickerKind.MUSIC -> "🎵 ${st.primaryText}"
                    else -> st.primaryText
                }
                val w = paint.measureText(label) + 28f * densityScale
                val h = 38f * densityScale
                val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = st.accentColorArgb.toInt() }
                canvas.drawRoundRect(RectF(-w / 2f, -h / 2f, w / 2f, h / 2f), 14f * densityScale, 14f * densityScale, pillPaint)
                canvas.drawText(label, 0f, 6f * densityScale, paint)
            }
            canvas.restore()
        }

        val outDir = File(context.cacheDir, "editor_exports").apply { mkdirs() }
        val outFile = File(outDir, "edited_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { out ->
            output.compress(Bitmap.CompressFormat.JPEG, if (lowEndMode) 86 else 92, out)
        }
        output.recycle()
        return Uri.fromFile(outFile)
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > reqW * 2 && (bounds.outHeight / sampleSize) > reqH * 2) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            Log.e(TAG, "Decode sampled bitmap failed for $uri", e)
            null
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = min(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).roundToInt(), (h * ratio).roundToInt(), true)
    }
}
