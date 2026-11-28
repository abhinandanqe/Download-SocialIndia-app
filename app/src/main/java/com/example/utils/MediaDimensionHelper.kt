package com.example.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log

data class MediaDimensions(
    val width: Int = 0,
    val height: Int = 0,
    val aspectRatio: Float = 0f,
    val isLandscape: Boolean = false,
    val isPortrait: Boolean = false,
    val isSquare: Boolean = false,
    val label: String = "Auto"
)

object MediaDimensionHelper {
    private const val TAG = "MediaDimensionHelper"

    fun getVideoDimensions(context: Context, uri: Uri): MediaDimensions {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            var width = widthStr?.toIntOrNull() ?: 0
            var height = heightStr?.toIntOrNull() ?: 0
            val rotation = rotationStr?.toIntOrNull() ?: 0

            // If rotation is 90 or 270, swap dimensions
            if (rotation == 90 || rotation == 270) {
                val temp = width
                width = height
                height = temp
            }

            if (width > 0 && height > 0) {
                val ratio = width.toFloat() / height.toFloat()
                val label = categorizeAspectRatio(ratio)
                return MediaDimensions(
                    width = width,
                    height = height,
                    aspectRatio = ratio,
                    isLandscape = ratio > 1.15f,
                    isPortrait = ratio < 0.85f,
                    isSquare = ratio in 0.85f..1.15f,
                    label = label
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract video dimensions from uri: $uri", e)
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {}
        }
        return MediaDimensions()
    }

    fun getImageDimensions(context: Context, uri: Uri): MediaDimensions {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val width = options.outWidth
            val height = options.outHeight

            if (width > 0 && height > 0) {
                val ratio = width.toFloat() / height.toFloat()
                val label = categorizeAspectRatio(ratio)
                return MediaDimensions(
                    width = width,
                    height = height,
                    aspectRatio = ratio,
                    isLandscape = ratio > 1.15f,
                    isPortrait = ratio < 0.85f,
                    isSquare = ratio in 0.85f..1.15f,
                    label = label
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract image dimensions from uri: $uri", e)
        }
        return MediaDimensions()
    }

    fun categorizeAspectRatio(ratio: Float): String {
        return when {
            ratio <= 0f -> "Auto"
            ratio in 0.50f..0.62f -> "9:16 (Story/Reel)"
            ratio in 0.70f..0.85f -> "4:5 (Portrait)"
            ratio in 0.85f..1.15f -> "1:1 (Square)"
            ratio in 1.25f..1.45f -> "4:3 (Classic)"
            ratio in 1.65f..1.85f -> "16:9 (Landscape)"
            ratio > 1.85f -> "Cinematic"
            else -> String.format(java.util.Locale.US, "%.2f", ratio)
        }
    }

    fun extractVideoThumbnail(context: Context, uri: Uri): java.io.File? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            if (bitmap != null) {
                val thumbFile = java.io.File(context.cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
                java.io.FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()
                thumbFile
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract thumbnail bitmap: ${e.message}")
            null
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
        }
    }
}
