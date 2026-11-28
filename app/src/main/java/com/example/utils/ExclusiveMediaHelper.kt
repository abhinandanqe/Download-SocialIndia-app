package com.example.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Security & Media Helper for Exclusive Content:
 * 1. Generates an irreversible, heavily blurred preview JPEG file at upload time so the
 *    original high-res photo/video is NEVER sent to locked viewers.
 * 2. Provides Android FLAG_SECURE screenshot/screen-recording protection while viewing
 *    exclusive content.
 */
object ExclusiveMediaHelper {
    private const val TAG = "ExclusiveMediaHelper"

    /**
     * Extracts a frame (for videos) or decodes the image (for photos), aggressively downscales
     * to a tiny privacy-safe thumbnail (~56px), applies multi-pass stack blur + dark frosted scrim,
     * and writes a separate JPEG preview file to app cache ready for upload.
     *
     * Because the image is downsampled to <=56px and blurred across 3 passes before upscaling,
     * the original visual details are mathematically unrecoverable from the resulting preview file.
     */
    suspend fun generateBlurredPreviewFile(
        context: Context,
        mediaUri: Uri,
        isVideo: Boolean
    ): File? = withContext(Dispatchers.IO) {
        var sourceBitmap: Bitmap? = null
        var tinyBitmap: Bitmap? = null
        var blurredBitmap: Bitmap? = null
        var outputBitmap: Bitmap? = null
        try {
            sourceBitmap = if (isVideo) {
                extractVideoFrame(context, mediaUri)
            } else {
                decodeSampledImage(context, mediaUri, 240)
            }

            if (sourceBitmap == null) {
                // Fallback abstract dark-gold privacy placeholder if media frame extraction fails
                outputBitmap = createFallbackBlurredPlaceholder()
            } else {
                val srcW = sourceBitmap.width.coerceAtLeast(1)
                val srcH = sourceBitmap.height.coerceAtLeast(1)
                val aspect = srcW.toFloat() / srcH.toFloat()

                // Step 1: Aggressive downscale (max 56px) destroys fine detail permanently
                val tinyW: Int
                val tinyH: Int
                if (aspect >= 1f) {
                    tinyW = 56
                    tinyH = max(32, (56f / aspect).toInt())
                } else {
                    tinyH = 56
                    tinyW = max(32, (56f * aspect).toInt())
                }
                tinyBitmap = Bitmap.createScaledBitmap(sourceBitmap, tinyW, tinyH, true)

                // Step 2: 3-pass heavy box blur on the tiny bitmap
                blurredBitmap = fastBoxBlur(tinyBitmap, radius = 6, passes = 3)

                // Step 3: Upscale smoothly with bilinear filtering & dark privacy tint
                val outW = if (aspect >= 1f) 360 else max(220, (360f * aspect).toInt())
                val outH = if (aspect >= 1f) max(220, (360f / aspect).toInt()) else 360
                val scaled = Bitmap.createScaledBitmap(blurredBitmap, outW, outH, true)

                outputBitmap = scaled.copy(Bitmap.Config.ARGB_8888, true)
                if (scaled !== outputBitmap) scaled.recycle()

                val canvas = Canvas(outputBitmap)
                val scrimPaint = Paint().apply {
                    color = Color.argb(95, 10, 10, 18)
                }
                canvas.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), scrimPaint)
            }

            val outDir = File(context.cacheDir, "exclusive_previews").apply { mkdirs() }
            val outFile = File(outDir, "blur_preview_${UUID.randomUUID().toString().take(12)}.jpg")
            FileOutputStream(outFile).use { fos ->
                outputBitmap?.compress(Bitmap.CompressFormat.JPEG, 78, fos)
            }
            outFile
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate blurred preview", e)
            null
        } finally {
            try { sourceBitmap?.recycle() } catch (_: Exception) {}
            try { tinyBitmap?.recycle() } catch (_: Exception) {}
            try { blurredBitmap?.recycle() } catch (_: Exception) {}
            try { outputBitmap?.recycle() } catch (_: Exception) {}
        }
    }

    private fun extractVideoFrame(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
        } catch (e: Exception) {
            Log.w(TAG, "Video frame extraction fallback: ${e.message}")
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun decodeSampledImage(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            var sampleSize = 1
            val maxSrc = max(boundsOpts.outWidth, boundsOpts.outHeight)
            while (maxSrc / (sampleSize * 2) >= maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Image decode fallback: ${e.message}")
            null
        }
    }

    private fun createFallbackBlurredPlaceholder(): Bitmap {
        val bmp = Bitmap.createBitmap(360, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(22, 18, 30))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.argb(65, 255, 193, 7)
        canvas.drawCircle(180f, 180f, 140f, paint)
        paint.color = Color.argb(55, 156, 39, 176)
        canvas.drawCircle(100f, 350f, 120f, paint)
        return fastBoxBlur(bmp, radius = 12, passes = 2)
    }

    private fun fastBoxBlur(src: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val temp = IntArray(w * h)
        val r = radius.coerceIn(1, 20)

        repeat(passes) {
            // Horizontal pass
            for (y in 0 until h) {
                val rowOffset = y * w
                var sumR = 0
                var sumG = 0
                var sumB = 0
                val windowSize = r * 2 + 1
                for (dx in -r..r) {
                    val x = dx.coerceIn(0, w - 1)
                    val c = pix[rowOffset + x]
                    sumR += (c shr 16) and 0xFF
                    sumG += (c shr 8) and 0xFF
                    sumB += c and 0xFF
                }
                for (x in 0 until w) {
                    temp[rowOffset + x] = (0xFF shl 24) or
                        ((sumR / windowSize) shl 16) or
                        ((sumG / windowSize) shl 8) or
                        (sumB / windowSize)
                    val removeX = (x - r).coerceIn(0, w - 1)
                    val addX = (x + r + 1).coerceIn(0, w - 1)
                    val cRem = pix[rowOffset + removeX]
                    val cAdd = pix[rowOffset + addX]
                    sumR += ((cAdd shr 16) and 0xFF) - ((cRem shr 16) and 0xFF)
                    sumG += ((cAdd shr 8) and 0xFF) - ((cRem shr 8) and 0xFF)
                    sumB += (cAdd and 0xFF) - (cRem and 0xFF)
                }
            }
            // Vertical pass
            for (x in 0 until w) {
                var sumR = 0
                var sumG = 0
                var sumB = 0
                val windowSize = r * 2 + 1
                for (dy in -r..r) {
                    val y = dy.coerceIn(0, h - 1)
                    val c = temp[y * w + x]
                    sumR += (c shr 16) and 0xFF
                    sumG += (c shr 8) and 0xFF
                    sumB += c and 0xFF
                }
                for (y in 0 until h) {
                    pix[y * w + x] = (0xFF shl 24) or
                        ((sumR / windowSize) shl 16) or
                        ((sumG / windowSize) shl 8) or
                        (sumB / windowSize)
                    val removeY = (y - r).coerceIn(0, h - 1)
                    val addY = (y + r + 1).coerceIn(0, h - 1)
                    val cRem = temp[removeY * w + x]
                    val cAdd = temp[addY * w + x]
                    sumR += ((cAdd shr 16) and 0xFF) - ((cRem shr 16) and 0xFF)
                    sumG += ((cAdd shr 8) and 0xFF) - ((cRem shr 8) and 0xFF)
                    sumB += (cAdd and 0xFF) - (cRem and 0xFF)
                }
            }
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pix, 0, w, 0, 0, w, h)
        }
    }

    /**
     * Finds the hosting Activity from any Compose Context.
     */
    fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}

/**
 * Applies Android FLAG_SECURE (blocking screenshots and screen recording) while viewing
 * sensitive exclusive content, and automatically restores normal screen flags on exit.
 */
@Composable
fun ExclusiveScreenSecurityEffect(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, enabled) {
        val activity = with(ExclusiveMediaHelper) { context.findActivity() }
        if (enabled && activity != null) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (enabled && activity != null) {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
