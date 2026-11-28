package com.example.utils

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

/**
 * Normalizes uploaded videos into streamable MP4 (H.264 + AAC) using Media3 Transformer,
 * compressing oversized videos before upload while falling back safely if an emulator lacks
 * hardware encoders.
 */
object VideoTranscoder {
    private const val TAG = "VideoTranscoder"
    private const val PASSTHROUGH_MAX_BYTES = 18L * 1024L * 1024L
    private const val TRANSCODE_TIMEOUT_MS = 120_000L

    fun isValidRemoteMediaUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        return (trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)) &&
            trimmed.length > 10
    }

    fun computeRetryBackoffMs(attempt: Int): Long {
        return when (attempt.coerceAtLeast(1)) {
            1 -> 800L
            2 -> 1800L
            else -> 3500L
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun transcodeToMp4(
        context: Context,
        sourceFile: File,
        cacheDir: File,
        onProgress: (Int) -> Unit = {}
    ): File {
        onProgress(8)
        if (!sourceFile.exists() || sourceFile.length() == 0L) return sourceFile

        val alreadyStandardH264Mp4 = isAlreadyH264AacMp4(sourceFile) && sourceFile.length() <= PASSTHROUGH_MAX_BYTES
        if (alreadyStandardH264Mp4) {
            Log.d(TAG, "Source is already compact H.264/AAC MP4 (${sourceFile.length()} bytes); keeping fast path.")
            onProgress(35)
            return sourceFile
        }

        val outFile = File(cacheDir, "transcoded_${System.currentTimeMillis()}.mp4")
        val resultFile = withTimeoutOrNull(TRANSCODE_TIMEOUT_MS) {
            runMedia3Transform(context, sourceFile, outFile, onProgress)
        }

        return if (resultFile != null && resultFile.exists() && resultFile.length() > 0L) {
            onProgress(38)
            Log.d(TAG, "Transcoded MP4 ready: ${sourceFile.length()} -> ${resultFile.length()} bytes")
            resultFile
        } else {
            try { if (outFile.exists()) outFile.delete() } catch (_: Exception) {}
            onProgress(35)
            Log.w(TAG, "Transcoder fallback to original source file (${sourceFile.length()} bytes)")
            sourceFile
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun runMedia3Transform(
        context: Context,
        sourceFile: File,
        outFile: File,
        onProgress: (Int) -> Unit
    ): File? = suspendCancellableCoroutine { cont ->
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                val progressHolder = ProgressHolder()
                var pollRunnable: Runnable? = null
                var activeTransformer: Transformer? = null

                fun stopPolling() {
                    pollRunnable?.let { mainHandler.removeCallbacks(it) }
                    pollRunnable = null
                }

                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        stopPolling()
                        if (cont.isActive) cont.resume(outFile)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        stopPolling()
                        Log.w(TAG, "Media3 Transformer error (${exportException.errorCodeName}): ${exportException.message}")
                        if (cont.isActive) cont.resume(null)
                    }
                }

                val transformer = Transformer.Builder(context.applicationContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(listener)
                    .build()
                activeTransformer = transformer

                val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(sourceFile))).build()
                transformer.start(editedItem, outFile.absolutePath)

                pollRunnable = object : Runnable {
                    override fun run() {
                        val t = activeTransformer ?: return
                        if (!cont.isActive) return
                        try {
                            val state = t.getProgress(progressHolder)
                            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                val mapped = 10 + ((progressHolder.progress.coerceIn(0, 100) * 28) / 100)
                                onProgress(mapped)
                            }
                            if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                                mainHandler.postDelayed(this, 250L)
                            }
                        } catch (_: Exception) {}
                    }
                }
                mainHandler.postDelayed(pollRunnable!!, 250L)

                cont.invokeOnCancellation {
                    mainHandler.post {
                        stopPolling()
                        try { activeTransformer?.cancel() } catch (_: Exception) {}
                        try { if (outFile.exists()) outFile.delete() } catch (_: Exception) {}
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Unable to start Media3 Transformer: ${e.message}")
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private fun isAlreadyH264AacMp4(file: File): Boolean {
        if (!file.name.endsWith(".mp4", ignoreCase = true)) return false
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var hasAvcVideo = false
            var audioCompatible = true
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    hasAvcVideo = mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)
                } else if (mime.startsWith("audio/")) {
                    audioCompatible = mime.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true)
                }
            }
            hasAvcVideo && audioCompatible
        } catch (_: Exception) {
            false
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    fun copyUriToTempFile(context: Context, uri: Uri, extension: String): File? {
        return try {
            val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}$extension")
            val inputStream = if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                java.io.FileInputStream(File(uri.path!!))
            } else {
                context.contentResolver.openInputStream(uri)
            }
            inputStream?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            if (file.exists() && file.length() > 0L) file else null
        } catch (_: Exception) {
            null
        }
    }
}
