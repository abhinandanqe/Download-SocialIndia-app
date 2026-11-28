package com.example.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.RepositoryManager
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.util.UUID

class UploadViewModel : ViewModel() {
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState
    
    // Globals to observe active uploads
    companion object {
        val globalUploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    }

    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
        globalUploadState.value = UploadState.Idle
    }

    fun uploadVideo(
        context: android.content.Context, 
        mediaUris: List<android.net.Uri>, 
        description: String, 
        userId: String, 
        musicTrack: String? = null, 
        musicTrackUrl: String? = null, 
        musicTrackImageUrl: String? = null,
        isVideo: Boolean = true,
        collaboratorIds: List<String> = emptyList(), isPremium: Boolean = false, premiumPrice: Double = 0.0
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.UploadingProgress(0)
            globalUploadState.value = UploadState.UploadingProgress(0)
            
            try {
                // 1. Upload each to Google Drive
                val uploadedUrls = mutableListOf<String>()
                
                for ((index, uri) in mediaUris.withIndex()) {
                    val mimeType = context.contentResolver.getType(uri) ?: if (isVideo) "video/mp4" else "image/jpeg"
                    _uploadState.value = UploadState.UploadingProgress(50) // Mock intermediate progress
                    globalUploadState.value = UploadState.UploadingProgress(50)
                    
                    val secureUrl = RepositoryManager.mediaRepository.uploadMedia(uri, {})
                    
                    if (secureUrl != null) {
                        val normalized = com.example.utils.GoogleDrivePermissionRepairManager.normalizeDriveUrl(secureUrl, isVideo)
                        uploadedUrls.add(normalized)
                    }

                    val overallProgress = (index + 1) * 100 / mediaUris.size
                    _uploadState.value = UploadState.UploadingProgress(overallProgress)
                    globalUploadState.value = UploadState.UploadingProgress(overallProgress)
                }
                
                if (uploadedUrls.isNotEmpty()) {
                    _uploadState.value = UploadState.Uploading // Processing stage
                    globalUploadState.value = UploadState.Uploading
                    
                    val mainMediaUrl = uploadedUrls.first()
                    val firstUri = mediaUris.first()
                    val dims = if (isVideo) {
                        com.example.utils.MediaDimensionHelper.getVideoDimensions(context, firstUri)
                    } else {
                        com.example.utils.MediaDimensionHelper.getImageDimensions(context, firstUri)
                    }

                    var generatedThumbnailUrl: String? = null
                    if (isVideo && mediaUris.isNotEmpty()) {
                        try {
                            val thumbFile = com.example.utils.MediaDimensionHelper.extractVideoThumbnail(context, firstUri)
                            if (thumbFile != null && thumbFile.exists()) {
                                generatedThumbnailUrl = RepositoryManager.mediaRepository.uploadMedia(android.net.Uri.fromFile(thumbFile))
                                try { thumbFile.delete() } catch (_: Exception) {}
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("UploadViewModel", "Failed to upload video thumbnail: ${e.message}")
                        }
                    }

                    // 2. Save to Firestore using PostRepository
                    val success = RepositoryManager.postRepository.createPost(
                        userId = userId, 
                        mediaUrl = mainMediaUrl, 
                        caption = description,
                        musicTrack = musicTrack,
                        musicTrackUrl = musicTrackUrl,
                        musicTrackImageUrl = musicTrackImageUrl,
                        postUrls = uploadedUrls,
                        collaboratorIds = collaboratorIds,
                        isSubscribersOnly = isPremium,
                        premiumPrice = premiumPrice,
                        videoWidth = dims.width,
                        videoHeight = dims.height,
                        aspectRatio = dims.aspectRatio,
                        thumbnailUrl = generatedThumbnailUrl
                    )

                    if (success) {
                        _uploadState.value = UploadState.Success
                        globalUploadState.value = UploadState.Success
                        
                        // Reset global state after showing success
                        delay(2000)
                        globalUploadState.value = UploadState.Idle
                        _uploadState.value = UploadState.Idle
                    } else {
                        _uploadState.value = UploadState.Error("Failed to save post to Firestore")
                        globalUploadState.value = UploadState.Error("Failed to save post to Firestore")
                        delay(2000)
                        globalUploadState.value = UploadState.Idle
                        _uploadState.value = UploadState.Idle
                    }
                } else {
                    val errMsg = "Failed to upload media. Please check your connection and try again."
                    _uploadState.value = UploadState.Error(errMsg)
                    globalUploadState.value = UploadState.Error(errMsg)
                    delay(3000)
                    globalUploadState.value = UploadState.Idle
                    _uploadState.value = UploadState.Idle
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("UploadViewModel", "Upload failed", e)
                val errMsg = "Upload failed: ${e.message ?: "Please check internet connection"}"
                _uploadState.value = UploadState.Error(errMsg)
                globalUploadState.value = UploadState.Error(errMsg)
                delay(3000)
                globalUploadState.value = UploadState.Idle
                _uploadState.value = UploadState.Idle
            }
        }
    }
}

sealed class UploadState {
    object Idle : UploadState()
    object Uploading : UploadState()
    data class UploadingProgress(val seconds: Int) : UploadState()
    object Success : UploadState()
    data class SuccessWithMessage(val message: String) : UploadState()
    data class Error(val message: String) : UploadState()
    data class NeedsDriveAuth(val intent: android.content.Intent?) : UploadState()
}
