package com.example.model

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import java.util.Date

class MediaUploadRepository(private val context: Context, private val firestore: FirebaseFirestore) {

    private val TAG = "MediaUploadRepo"

    /**
     * Uploads media using RepositoryManager and then saves post data to Firestore.
     */
    suspend fun uploadAndCreatePost(
        userId: String,
        caption: String,
        mediaUri: Uri,
        onSuccess: (Post) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // 1. Upload using RepositoryManager
            val secureUrl = RepositoryManager.mediaRepository.uploadMedia(mediaUri)
            
            if (secureUrl != null) {
                val isVideo = com.example.utils.MediaResolver.isImage(secureUrl).not()
                val normalizedUrl = com.example.utils.GoogleDrivePermissionRepairManager.normalizeDriveUrl(secureUrl, isVideo)
                // 2. Upload Success -> Save to Firestore
                savePostToFirestore(userId, caption, normalizedUrl, onSuccess, onError)
            } else {
                onError("Failed to upload media")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Post Upload Workflow Error", e)
            onError(e.message ?: "Unknown Error during upload workflow")
        }
    }

    private fun savePostToFirestore(
        userId: String,
        caption: String,
        postUrl: String,
        onSuccess: (Post) -> Unit,
        onError: (String) -> Unit
    ) {
        val postId = UUID.randomUUID().toString()
        val post = Post(
            id = postId,
            userId = userId,
            caption = caption,
            postUrl = postUrl
        )

        firestore.collection("posts")
            .document(postId)
            .set(post)
            .addOnSuccessListener {
                Log.d(TAG, "Firestore Post Saved: $postId")
                onSuccess(post)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firestore Post Error", e)
                onError(e.message ?: "Failed to save post to Firestore")
            }
    }

    /**
     * Uploads story media and then saves story data to Firestore.
     */
    suspend fun uploadAndCreateStory(
        userId: String,
        textOverlay: String,
        mediaUri: Uri,
        onSuccess: (Story) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // 1. Upload using RepositoryManager
            val secureUrl = RepositoryManager.mediaRepository.uploadMedia(mediaUri)
            
            if (secureUrl != null) {
                val isVideo = com.example.utils.MediaResolver.isImage(secureUrl).not()
                val normalizedUrl = com.example.utils.GoogleDrivePermissionRepairManager.normalizeDriveUrl(secureUrl, isVideo)
                // 2. Upload Success -> Save Story to Firestore
                saveStoryToFirestore(userId, textOverlay, normalizedUrl, onSuccess, onError)
            } else {
                onError("Failed to upload story media")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Story Upload Workflow Error", e)
            onError(e.message ?: "Unknown Error during story upload workflow")
        }
    }

    private fun saveStoryToFirestore(
        userId: String,
        textOverlay: String,
        mediaUrl: String,
        onSuccess: (Story) -> Unit,
        onError: (String) -> Unit
    ) {
        val storyId = UUID.randomUUID().toString()
        val expiryDate = Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000) // 24 Hours expiry
        
        val story = Story(
            id = storyId,
            userId = userId,
            mediaUrl = mediaUrl,
            timestamp = Date(),
            expireAt = expiryDate,
            expiryTime = expiryDate.time,
            viewers = emptyList(),
            viewCount = 0,
            likesCount = 0,
            commentsCount = 0,
            textOverlay = textOverlay
        )

        firestore.collection("stories")
            .document(storyId)
            .set(story)
            .addOnSuccessListener {
                Log.d(TAG, "Firestore Story Saved: $storyId")
                onSuccess(story)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firestore Story Error", e)
                onError(e.message ?: "Failed to save story to Firestore")
            }
    }
}

