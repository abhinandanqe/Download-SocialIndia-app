package com.example.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.utils.VideoTranscoder
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class MediaRepository(private val context: Context) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun uploadMedia(fileUri: Uri, onProgress: (Int) -> Unit = {}): String? = withContext(Dispatchers.IO) {
        onProgress(3)
        val resolverMime = try { context.contentResolver.getType(fileUri) } catch (_: Exception) { null }
        val uriStr = fileUri.toString().lowercase()
        val extFromUrl = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uriStr)?.lowercase().orEmpty()
        val mimeFromExt = if (extFromUrl.isNotEmpty()) {
            android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extFromUrl)
        } else null
        val initialMime = resolverMime?.takeIf { it != "application/octet-stream" }
            ?: mimeFromExt
            ?: "application/octet-stream"

        var isGif = initialMime.contains("gif") || uriStr.endsWith(".gif")
        var isImage = isGif || initialMime.startsWith("image/") ||
                uriStr.endsWith(".jpg") || uriStr.endsWith(".png") ||
                uriStr.endsWith(".jpeg") || uriStr.endsWith(".webp") ||
                uriStr.contains(".jpg") || uriStr.contains(".png") ||
                uriStr.contains(".jpeg") || uriStr.contains(".webp")
        var isVideo = if (isImage) false else (initialMime.contains("video") ||
                uriStr.endsWith(".mp4") || uriStr.endsWith(".mov") ||
                uriStr.endsWith(".mkv") || uriStr.endsWith(".webm") ||
                uriStr.contains(".mp4") || uriStr.contains(".mov") || uriStr.contains(".mkv"))
        val isAudio = if (isImage || isVideo) false else (initialMime.contains("audio") || uriStr.endsWith(".mp3") || uriStr.endsWith(".m4a") || uriStr.endsWith(".wav"))
        var ext = if (isGif) ".gif" else if (isVideo) ".mp4" else if (isAudio) ".mp3" else if (initialMime.contains("png") || uriStr.endsWith(".png")) ".png" else if (initialMime.contains("webp") || uriStr.endsWith(".webp")) ".webp" else ".jpg"

        val tempFile = VideoTranscoder.copyUriToTempFile(context, fileUri, ext)
        if (tempFile == null || !tempFile.exists() || tempFile.length() == 0L) {
            Log.e("MediaRepository", "Failed to prepare temp media file")
            return@withContext null
        }

        // Verify actual image header so photos are never misclassified as video
        var detectedBitmapMime: String? = null
        if (!isAudio) {
            try {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0 && !opts.outMimeType.isNullOrBlank()) {
                    detectedBitmapMime = opts.outMimeType
                    isImage = true
                    isVideo = false
                    isGif = detectedBitmapMime.contains("gif", ignoreCase = true)
                    ext = when {
                        isGif -> ".gif"
                        detectedBitmapMime.contains("png", ignoreCase = true) -> ".png"
                        detectedBitmapMime.contains("webp", ignoreCase = true) -> ".webp"
                        else -> ".jpg"
                    }
                }
            } catch (_: Exception) {}
        }

        var activeUploadFile: File = tempFile
        if (isImage && !isGif) {
            val webpFile = compressImageToWebP(tempFile, context.cacheDir)
            if (webpFile != null && webpFile.exists() && webpFile.length() > 0L) {
                if (webpFile.absolutePath != tempFile.absolutePath) {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
                activeUploadFile = webpFile
                detectedBitmapMime = "image/webp"
                ext = ".webp"
            }
            onProgress(35)
        } else if (isVideo) {
            // Transcode & compress any video to standard H.264 + AAC MP4 before upload
            val transcoded = VideoTranscoder.transcodeToMp4(context, tempFile, context.cacheDir) { p ->
                onProgress(p)
            }
            if (transcoded.absolutePath != tempFile.absolutePath && transcoded.exists() && transcoded.length() > 0L) {
                try { tempFile.delete() } catch (_: Exception) {}
                activeUploadFile = transcoded
            }
            ext = ".mp4"
        }

        val fileSizeBytes = activeUploadFile.length()
        val uploadMime = when {
            isGif -> "image/gif"
            isVideo -> "video/mp4"
            isAudio -> "audio/mpeg"
            detectedBitmapMime != null -> detectedBitmapMime
            initialMime.contains("png") || ext == ".png" -> "image/png"
            initialMime.contains("webp") || ext == ".webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val mediaType = uploadMime.toMediaTypeOrNull()

        fun cleanupAndValidate(candidateUrl: String?): String? {
            if (!VideoTranscoder.isValidRemoteMediaUrl(candidateUrl)) return null
            try { activeUploadFile.delete() } catch (_: Exception) {}
            onProgress(100)
            return candidateUrl!!.trim()
        }

        // =========================================================================
        // PRIMARY TIER: Firebase Cloud Storage (Permanent, Range-capable, CORS/Rules backed)
        // =========================================================================
        val storageCandidates = listOfNotNull(
            runCatching { FirebaseStorage.getInstance() }.getOrNull(),
            runCatching { FirebaseStorage.getInstance("gs://socialindia-2cdf0.firebasestorage.app") }.getOrNull()
        ).distinctBy { it.reference.bucket }

        for (storage in storageCandidates) {
            try {
                onProgress(42)
                val fileName = "${UUID.randomUUID()}_${System.currentTimeMillis()}$ext"
                val mediaRef = storage.reference.child("media/$fileName")
                val metadata = StorageMetadata.Builder()
                    .setContentType(uploadMime)
                    .setCacheControl("public, max-age=31536000")
                    .build()

                val uploadTask = mediaRef.putFile(Uri.fromFile(activeUploadFile), metadata)
                uploadTask.addOnProgressListener { snap ->
                    val total = snap.totalByteCount.coerceAtLeast(1L)
                    val pct = 42 + ((snap.bytesTransferred * 53) / total).toInt().coerceIn(0, 53)
                    onProgress(pct)
                }
                uploadTask.await()
                val downloadUrl = mediaRef.downloadUrl.await().toString()
                cleanupAndValidate(downloadUrl)?.let { validUrl ->
                    com.example.utils.AdminDriveStoragePoolManager.recordMediaUploadInPool(fileSizeBytes)
                    Log.d("MediaRepository", "Uploaded to Firebase Storage (${storage.reference.bucket}): $validUrl")
                    return@withContext validUrl
                }
            } catch (e: Exception) {
                Log.w("MediaRepository", "Firebase Storage upload failed (${storage.reference.bucket}): ${e.message}")
            }
        }

        // =========================================================================
        // FALLBACK TIER 1: Admin Drive Storage Pool (If active account has OAuth token)
        // =========================================================================
        try {
            val adminPoolUrl = com.example.utils.AdminDriveStoragePoolManager.uploadToAdminDrivePool(
                context, Uri.fromFile(activeUploadFile), uploadMime
            ) { p -> onProgress(50 + (p / 2)) }
            cleanupAndValidate(adminPoolUrl)?.let { validUrl ->
                Log.d("MediaRepository", "Uploaded to Admin Drive Storage Pool: $validUrl")
                return@withContext validUrl
            }
        } catch (e: Exception) {
            Log.w("MediaRepository", "Admin Drive pool upload skipped: ${e.message}")
        }

        // =========================================================================
        // FALLBACK TIER 2: Catbox.moe (Permanent CDN)
        // =========================================================================
        try {
            onProgress(70)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart("fileToUpload", activeUploadFile.name, activeUploadFile.asRequestBody(mediaType))
                .build()

            val request = Request.Builder()
                .url("https://catbox.moe/user/api.php")
                .header("User-Agent", "SocialIndia/2.0")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val url = response.body?.string()?.trim()
                if (response.isSuccessful) {
                    cleanupAndValidate(url)?.let { validUrl ->
                        com.example.utils.AdminDriveStoragePoolManager.recordMediaUploadInPool(fileSizeBytes)
                        Log.d("MediaRepository", "Uploaded to permanent Catbox CDN: $validUrl")
                        return@withContext validUrl
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MediaRepository", "Catbox CDN upload failed (${e.message}), trying next provider...")
        }

        // =========================================================================
        // FALLBACK TIER 3: tmpfiles.org
        // =========================================================================
        try {
            onProgress(80)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", activeUploadFile.name, activeUploadFile.asRequestBody(mediaType))
                .build()

            val request = Request.Builder()
                .url("https://tmpfiles.org/api/v1/upload")
                .header("User-Agent", "SocialIndia/2.0")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    if (json.optString("status") == "success") {
                        val pageUrl = json.optJSONObject("data")?.optString("url")
                        if (!pageUrl.isNullOrEmpty()) {
                            val directDlUrl = resolveTmpfilesDirectUrl(pageUrl)
                            cleanupAndValidate(directDlUrl)?.let { validUrl ->
                                com.example.utils.AdminDriveStoragePoolManager.recordMediaUploadInPool(fileSizeBytes)
                                Log.d("MediaRepository", "Uploaded to tmpfiles: $validUrl")
                                return@withContext validUrl
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MediaRepository", "tmpfiles upload failed (${e.message}), trying next provider...")
        }

        // =========================================================================
        // FALLBACK TIER 4: Uguu CDN
        // =========================================================================
        try {
            onProgress(88)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("files[]", activeUploadFile.name, activeUploadFile.asRequestBody(mediaType))
                .build()

            val request = Request.Builder()
                .url("https://uguu.se/upload.php")
                .header("User-Agent", "SocialIndia/2.0")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    if (json.optBoolean("success")) {
                        val filesArr = json.optJSONArray("files")
                        if (filesArr != null && filesArr.length() > 0) {
                            val url = filesArr.getJSONObject(0).optString("url")
                            cleanupAndValidate(url)?.let { validUrl ->
                                com.example.utils.AdminDriveStoragePoolManager.recordMediaUploadInPool(fileSizeBytes)
                                Log.d("MediaRepository", "Uploaded to Uguu CDN: $validUrl")
                                return@withContext validUrl
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MediaRepository", "Uguu CDN upload failed (${e.message}), trying next provider...")
        }

        // =========================================================================
        // FALLBACK TIER 5: User's Own Google Drive
        // =========================================================================
        try {
            val googleAccount = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
            val driveScope = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file")
            if (googleAccount != null && com.google.android.gms.auth.api.signin.GoogleSignIn.hasPermissions(googleAccount, driveScope)) {
                val driveUrl = com.example.utils.GoogleDriveManager.uploadUri(context, Uri.fromFile(activeUploadFile), uploadMime)
                cleanupAndValidate(driveUrl)?.let { validUrl ->
                    Log.d("MediaRepository", "Uploaded to Google Drive: $validUrl")
                    return@withContext validUrl
                }
            }
        } catch (e: Exception) {
            Log.w("MediaRepository", "User Google Drive upload skipped: ${e.message}")
        }

        try { activeUploadFile.delete() } catch (_: Exception) {}
        Log.e("MediaRepository", "All cloud upload tiers failed. Check network connectivity.")
        return@withContext null
    }

    private fun resolveTmpfilesDirectUrl(pageUrl: String): String {
        return try {
            val request = Request.Builder().url(pageUrl).get().build()
            httpClient.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: ""
                val pattern = Regex("href=\"(https://tmpfiles\\.org/dl/[^\"]+)\"")
                val match = pattern.find(html)
                if (match != null) {
                    match.groupValues[1]
                } else {
                    pageUrl.replace("https://tmpfiles.org/", "https://tmpfiles.org/dl/")
                }
            }
        } catch (_: Exception) {
            pageUrl.replace("https://tmpfiles.org/", "https://tmpfiles.org/dl/")
        }
    }

    companion object {
        fun compressImageToWebP(
            sourceFile: File,
            cacheDir: File,
            maxDimension: Int = 1440,
            quality: Int = 82
        ): File? {
            return try {
                val boundsOpts = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFile(sourceFile.absolutePath, boundsOpts)
                val srcW = boundsOpts.outWidth
                val srcH = boundsOpts.outHeight
                if (srcW <= 0 || srcH <= 0) return null

                var sampleSize = 1
                while ((srcW / sampleSize) > maxDimension * 2 || (srcH / sampleSize) > maxDimension * 2) {
                    sampleSize *= 2
                }
                val decodeOpts = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                val rawBitmap = android.graphics.BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOpts) ?: return null
                val maxSide = maxOf(rawBitmap.width, rawBitmap.height)
                val finalBitmap = if (maxSide > maxDimension) {
                    val scale = maxDimension.toFloat() / maxSide.toFloat()
                    val targetW = (rawBitmap.width * scale).toInt().coerceAtLeast(1)
                    val targetH = (rawBitmap.height * scale).toInt().coerceAtLeast(1)
                    val scaled = android.graphics.Bitmap.createScaledBitmap(rawBitmap, targetW, targetH, true)
                    if (scaled !== rawBitmap) rawBitmap.recycle()
                    scaled
                } else {
                    rawBitmap
                }

                val outFile = File(cacheDir, "compressed_${System.currentTimeMillis()}.webp")
                FileOutputStream(outFile).use { fos ->
                    @Suppress("DEPRECATION")
                    val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        android.graphics.Bitmap.CompressFormat.WEBP
                    }
                    finalBitmap.compress(format, quality.coerceIn(50, 95), fos)
                    fos.flush()
                }
                finalBitmap.recycle()
                if (outFile.exists() && outFile.length() > 0L) outFile else null
            } catch (e: Exception) {
                Log.w("MediaRepository", "WebP compression skipped, using original: ${e.message}")
                null
            }
        }
    }
}
