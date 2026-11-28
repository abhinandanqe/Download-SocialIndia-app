package com.example.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.security.InputGuard
import com.example.utils.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class UploadStatus {
    IDLE,
    PREPARING,
    UPLOADING,
    PROCESSING,
    SUCCESS,
    ERROR
}

data class ActiveUpload(
    val id: String = java.util.UUID.randomUUID().toString(),
    val thumbnailUri: Uri? = null,
    val caption: String = "",
    val isVideo: Boolean = true,
    val progress: Float = 0f, // 0.0 to 1.0
    val statusText: String = "Preparing upload...",
    val status: UploadStatus = UploadStatus.IDLE,
    val errorMessage: String? = null,
    val canRetry: Boolean = false
)

private data class UploadRequest(
    val mediaUris: List<Uri>,
    val caption: String,
    val userId: String,
    val musicTrack: String?,
    val musicTrackUrl: String?,
    val musicTrackImageUrl: String?,
    val musicTrackId: String?,
    val musicTrackArtist: String?,
    val isVideo: Boolean,
    val collaboratorIds: List<String>,
    val isPremium: Boolean,
    val premiumPrice: Double,
    val isTrialReel: Boolean,
    val trialAudience: String,
    val addYoursPromptId: String?,
    val addYoursPromptText: String?,
    val videoWidth: Int,
    val videoHeight: Int,
    val aspectRatio: Float,
    val mentionedUsers: List<com.example.model.User>,
    val isPaidPost: Boolean,
    val postUnlockPrice: Double,
    val accessType: String,
    val visibility: String,
    val teaserCaption: String,
    val customCoverUri: Uri?
)

object GlobalUploadManager {
    private const val TAG = "GlobalUploadManager"
    private const val MAX_VIDEO_BYTES = 250L * 1024L * 1024L // 250 MB
    private const val MAX_IMAGE_BYTES = 25L * 1024L * 1024L  // 25 MB
    private const val MAX_UPLOAD_RETRIES = 2

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentUpload = MutableStateFlow<ActiveUpload?>(null)
    val currentUpload: StateFlow<ActiveUpload?> = _currentUpload.asStateFlow()

    @Volatile
    private var lastUploadRequest: UploadRequest? = null

    // Callback to trigger feed refresh across ViewModels
    var onUploadSuccessCallback: (() -> Unit)? = null

    private fun resolveFileSizeBytes(context: Context, uri: Uri): Long {
        return try {
            if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                File(uri.path!!).length()
            } else {
                var size = -1L
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (idx >= 0 && !cursor.isNull(idx)) {
                            size = cursor.getLong(idx)
                        }
                    }
                }
                size
            }
        } catch (_: Exception) {
            -1L
        }
    }

    fun startUpload(
        context: Context,
        mediaUris: List<Uri>,
        caption: String,
        userId: String,
        musicTrack: String? = null,
        musicTrackUrl: String? = null,
        musicTrackImageUrl: String? = null,
        musicTrackId: String? = null,
        musicTrackArtist: String? = null,
        isVideo: Boolean = true,
        collaboratorIds: List<String> = emptyList(),
        isPremium: Boolean = false,
        premiumPrice: Double = 0.0,
        isTrialReel: Boolean = false,
        trialAudience: String = "test_audience",
        addYoursPromptId: String? = null,
        addYoursPromptText: String? = null,
        videoWidth: Int = 0,
        videoHeight: Int = 0,
        aspectRatio: Float = 0f,
        mentionedUsers: List<com.example.model.User> = emptyList(),
        isPaidPost: Boolean = false,
        postUnlockPrice: Double = 0.0,
        accessType: String = "PUBLIC",
        visibility: String = "PUBLIC",
        teaserCaption: String = "",
        customCoverUri: Uri? = null
    ) {
        if (mediaUris.isEmpty()) return
        val existingStatus = _currentUpload.value?.status
        if (existingStatus != null && existingStatus != UploadStatus.SUCCESS && existingStatus != UploadStatus.ERROR) return

        val appContext = context.applicationContext
        val safeCaption = InputGuard.sanitizeCaption(caption)
        val safeTeaserCaption = InputGuard.sanitizeCaption(teaserCaption)

        val request = UploadRequest(
            mediaUris = mediaUris,
            caption = safeCaption,
            userId = userId,
            musicTrack = musicTrack,
            musicTrackUrl = musicTrackUrl,
            musicTrackImageUrl = musicTrackImageUrl,
            musicTrackId = musicTrackId,
            musicTrackArtist = musicTrackArtist,
            isVideo = isVideo,
            collaboratorIds = collaboratorIds,
            isPremium = isPremium,
            premiumPrice = premiumPrice,
            isTrialReel = isTrialReel,
            trialAudience = trialAudience,
            addYoursPromptId = addYoursPromptId,
            addYoursPromptText = addYoursPromptText,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            aspectRatio = aspectRatio,
            mentionedUsers = mentionedUsers,
            isPaidPost = isPaidPost,
            postUnlockPrice = postUnlockPrice,
            accessType = accessType,
            visibility = visibility,
            teaserCaption = safeTeaserCaption,
            customCoverUri = customCoverUri
        )
        lastUploadRequest = request

        val firstUri = mediaUris.first()

        // Guard 1: Check connectivity before starting upload
        if (!NetworkMonitor.recheck(appContext)) {
            _currentUpload.value = ActiveUpload(
                thumbnailUri = firstUri,
                caption = safeCaption,
                isVideo = isVideo,
                progress = 0f,
                statusText = "No internet connection. Tap Retry when online.",
                status = UploadStatus.ERROR,
                errorMessage = "Offline",
                canRetry = true
            )
            return
        }

        // Guard 2: Validate file sizes to prevent OOM / timeout failures
        val maxBytes = if (isVideo) MAX_VIDEO_BYTES else MAX_IMAGE_BYTES
        val maxMbLabel = if (isVideo) "250 MB" else "25 MB"
        for (uri in mediaUris) {
            val sizeBytes = resolveFileSizeBytes(appContext, uri)
            if (sizeBytes > maxBytes) {
                _currentUpload.value = ActiveUpload(
                    thumbnailUri = firstUri,
                    caption = safeCaption,
                    isVideo = isVideo,
                    progress = 0f,
                    statusText = "File exceeds $maxMbLabel limit. Please trim or compress.",
                    status = UploadStatus.ERROR,
                    errorMessage = "File too large",
                    canRetry = false
                )
                return
            }
        }

        // Auto-extract original dimensions if not passed
        val (finalWidth, finalHeight, finalRatio) = if (videoWidth > 0 && videoHeight > 0) {
            Triple(videoWidth, videoHeight, if (aspectRatio > 0f) aspectRatio else videoWidth.toFloat() / videoHeight.toFloat())
        } else {
            val dims = if (isVideo) {
                com.example.utils.MediaDimensionHelper.getVideoDimensions(appContext, firstUri)
            } else {
                com.example.utils.MediaDimensionHelper.getImageDimensions(appContext, firstUri)
            }
            Triple(dims.width, dims.height, dims.aspectRatio)
        }

        val uploadTask = ActiveUpload(
            thumbnailUri = firstUri,
            caption = safeCaption,
            isVideo = isVideo,
            progress = 0.05f,
            statusText = "Preparing upload...",
            status = UploadStatus.PREPARING,
            canRetry = false
        )

        _currentUpload.value = uploadTask

        managerScope.launch {
            try {
                // Step 1: Smooth preparation animation
                delay(300)
                _currentUpload.value = _currentUpload.value?.copy(
                    progress = 0.15f,
                    statusText = "Optimizing media...",
                    status = UploadStatus.UPLOADING
                )

                // Step 2: Upload media files with automatic transient retries
                val uploadedUrls = mutableListOf<String>()
                val totalCount = mediaUris.size

                for ((index, uri) in mediaUris.withIndex()) {
                    val baseProgress = 0.20f + (index.toFloat() / totalCount.toFloat()) * 0.60f
                    _currentUpload.value = _currentUpload.value?.copy(
                        progress = baseProgress,
                        statusText = "Uploading (${(baseProgress * 100).toInt()}%)...",
                        status = UploadStatus.UPLOADING
                    )

                    var secureUrl: String? = null
                    var lastError: Exception? = null
                    for (attempt in 0..MAX_UPLOAD_RETRIES) {
                        try {
                            if (attempt > 0) {
                                val backoffMs = if (attempt == 1) 1200L else 2500L
                                _currentUpload.value = _currentUpload.value?.copy(
                                    statusText = "Retrying upload (${attempt}/$MAX_UPLOAD_RETRIES)..."
                                )
                                delay(backoffMs)
                            }
                            secureUrl = RepositoryManager.mediaRepository.uploadMedia(uri) { partProgress ->
                                val currentTotal = baseProgress + ((partProgress / 100f) * (0.60f / totalCount))
                                val stageLabel = if (isVideo && partProgress < 40) {
                                    "Converting MP4 (${partProgress}%)..."
                                } else {
                                    "Uploading (${(currentTotal * 100).toInt().coerceIn(0, 100)}%)..."
                                }
                                _currentUpload.value = _currentUpload.value?.copy(
                                    progress = currentTotal.coerceIn(0.15f, 0.85f),
                                    statusText = stageLabel
                                )
                            }
                            if (com.example.utils.VideoTranscoder.isValidRemoteMediaUrl(secureUrl)) {
                                lastError = null
                                break
                            } else {
                                secureUrl = null
                                lastError = Exception("Upload did not return a valid download URL")
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            lastError = e
                            Log.w(TAG, "Media upload attempt ${attempt + 1} failed: ${e.message}")
                        }
                    }

                    if (com.example.utils.VideoTranscoder.isValidRemoteMediaUrl(secureUrl)) {
                        uploadedUrls.add(secureUrl!!)
                    } else {
                        Log.e(TAG, "Media upload failed after retries", lastError)
                        _currentUpload.value = _currentUpload.value?.copy(
                            status = UploadStatus.ERROR,
                            errorMessage = lastError?.message ?: "Network error",
                            statusText = "Upload failed. Tap Retry to try again.",
                            canRetry = true
                        )
                        return@launch
                    }
                }

                if (uploadedUrls.isEmpty() || !com.example.utils.VideoTranscoder.isValidRemoteMediaUrl(uploadedUrls.first())) {
                    _currentUpload.value = _currentUpload.value?.copy(
                        status = UploadStatus.ERROR,
                        errorMessage = "No valid download URL returned",
                        statusText = "Upload failed. Tap Retry to try again.",
                        canRetry = true
                    )
                    return@launch
                }

                // Step 3: Finalizing post in Firestore (only after verified downloadUrl)
                _currentUpload.value = _currentUpload.value?.copy(
                    progress = 0.92f,
                    statusText = "Saving post...",
                    status = UploadStatus.PROCESSING
                )

                val mainMediaUrl = uploadedUrls.first()

                var generatedThumbnailUrl: String? = null
                if (customCoverUri != null) {
                    try {
                        generatedThumbnailUrl = RepositoryManager.mediaRepository.uploadMedia(customCoverUri)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to upload custom Reel cover frame: ${e.message}")
                    }
                }
                if (generatedThumbnailUrl == null && isVideo && mediaUris.isNotEmpty()) {
                    try {
                        val thumbFile = com.example.utils.MediaDimensionHelper.extractVideoThumbnail(appContext, firstUri)
                        if (thumbFile != null && thumbFile.exists()) {
                            generatedThumbnailUrl = RepositoryManager.mediaRepository.uploadMedia(Uri.fromFile(thumbFile))
                            try { thumbFile.delete() } catch (_: Exception) {}
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to upload video thumbnail: ${e.message}")
                    }
                }

                var blurredPreviewUrl = ""
                if (isPaidPost && postUnlockPrice >= 10.0 && mediaUris.isNotEmpty()) {
                    try {
                        _currentUpload.value = _currentUpload.value?.copy(
                            progress = 0.88f,
                            statusText = "Generating secure blurred preview...",
                            status = UploadStatus.PROCESSING
                        )
                        val blurFile = com.example.utils.ExclusiveMediaHelper.generateBlurredPreviewFile(
                            context = appContext,
                            mediaUri = mediaUris.first(),
                            isVideo = isVideo
                        )
                        if (blurFile != null && blurFile.exists()) {
                            blurredPreviewUrl = RepositoryManager.mediaRepository.uploadMedia(Uri.fromFile(blurFile)) ?: ""
                            try { blurFile.delete() } catch (_: Exception) {}
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to generate/upload exclusive blurred preview: ${e.message}")
                    }
                }

                val resolvedUserId = userId.takeIf { it.isNotBlank() && it != "anonymous_user" }
                    ?: RepositoryManager.getCurrentUserId().takeIf { it.isNotBlank() && it != "anonymous_user" }
                    ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() && it != "anonymous_user" }
                    ?: ""

                if (resolvedUserId.isBlank()) {
                    _currentUpload.value = _currentUpload.value?.copy(
                        status = UploadStatus.ERROR,
                        errorMessage = "Please log in to share posts or reels",
                        statusText = "Login required to upload",
                        canRetry = false
                    )
                    return@launch
                }

                val createdOk = RepositoryManager.postRepository.createPost(
                    userId = resolvedUserId,
                    mediaUrl = mainMediaUrl,
                    caption = safeCaption,
                    musicTrack = musicTrack,
                    musicTrackUrl = musicTrackUrl,
                    musicTrackImageUrl = musicTrackImageUrl,
                    postUrls = uploadedUrls,
                    collaboratorIds = collaboratorIds,
                    isSubscribersOnly = isPremium,
                    premiumPrice = premiumPrice,
                    isTrialReel = isTrialReel,
                    trialAudience = trialAudience,
                    addYoursPromptId = addYoursPromptId,
                    addYoursPromptText = addYoursPromptText,
                    videoWidth = finalWidth,
                    videoHeight = finalHeight,
                    aspectRatio = finalRatio,
                    explicitMentions = mentionedUsers,
                    thumbnailUrl = generatedThumbnailUrl,
                    isPaidPost = isPaidPost,
                    postUnlockPrice = postUnlockPrice,
                    accessType = accessType,
                    visibility = visibility,
                    isVideoOverride = isVideo,
                    blurredThumbnailUrl = blurredPreviewUrl,
                    teaserCaption = safeTeaserCaption
                )

                if (!createdOk) {
                    _currentUpload.value = _currentUpload.value?.copy(
                        status = UploadStatus.ERROR,
                        errorMessage = "Could not save post to feed",
                        statusText = "Could not publish post. Tap Retry.",
                        canRetry = true
                    )
                    return@launch
                }

                lastUploadRequest = null
                _currentUpload.value = _currentUpload.value?.copy(
                    progress = 1.0f,
                    statusText = "Shared successfully! 🎉",
                    status = UploadStatus.SUCCESS,
                    canRetry = false
                )

                // Trigger UI feed refreshes across Home Feed, Reels, and Profile
                com.example.ui.GlobalState.homeFeedRefreshTrigger.value = System.currentTimeMillis()
                onUploadSuccessCallback?.invoke()

                // Keep success banner visible briefly then dismiss with animation
                delay(3000)
                _currentUpload.value = null

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Background upload error", e)
                _currentUpload.value = _currentUpload.value?.copy(
                    statusText = "Upload failed. Tap Retry to try again.",
                    errorMessage = e.message,
                    status = UploadStatus.ERROR,
                    canRetry = true
                )
            }
        }
    }

    fun retryLastUpload(context: Context) {
        val req = lastUploadRequest ?: return
        _currentUpload.value = null
        startUpload(
            context = context,
            mediaUris = req.mediaUris,
            caption = req.caption,
            userId = req.userId,
            musicTrack = req.musicTrack,
            musicTrackUrl = req.musicTrackUrl,
            musicTrackImageUrl = req.musicTrackImageUrl,
            musicTrackId = req.musicTrackId,
            musicTrackArtist = req.musicTrackArtist,
            isVideo = req.isVideo,
            collaboratorIds = req.collaboratorIds,
            isPremium = req.isPremium,
            premiumPrice = req.premiumPrice,
            isTrialReel = req.isTrialReel,
            trialAudience = req.trialAudience,
            addYoursPromptId = req.addYoursPromptId,
            addYoursPromptText = req.addYoursPromptText,
            videoWidth = req.videoWidth,
            videoHeight = req.videoHeight,
            aspectRatio = req.aspectRatio,
            mentionedUsers = req.mentionedUsers,
            isPaidPost = req.isPaidPost,
            postUnlockPrice = req.postUnlockPrice,
            accessType = req.accessType,
            visibility = req.visibility,
            teaserCaption = req.teaserCaption,
            customCoverUri = req.customCoverUri
        )
    }

    fun dismissUpload() {
        _currentUpload.value = null
    }
}
