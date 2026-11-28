package com.example.manager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.model.RepositoryManager
import com.example.model.ShareContentType
import com.example.model.SharePayload
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Standard Universal Share Actions across Social India.
 */
enum class ShareAction {
    ADD_TO_STORY,
    SEND_TO_MESSAGE,
    SEND_TO_GROUP,
    COPY_LINK,
    SYSTEM_SHARE,
    SAVE
}

/**
 * SocialIndiaShareManager
 * 
 * Centralized, unified manager handling all sharing operations across
 * Home Feed, Reels, Profile, Stories, Search, Discover, and Saved Content.
 */
object SocialIndiaShareManager {

    /**
     * Determines whether the content is eligible to be shared to a Story.
     */
    fun canShareToStory(payload: SharePayload): Boolean {
        // Restricted, deleted, or locked exclusive content cannot be shared to Story until unlocked
        if (payload.contentId.isBlank()) return false
        if (payload.isExclusiveContent && !payload.isUnlocked) return false
        return when (payload.contentType) {
            ShareContentType.POST,
            ShareContentType.REEL,
            ShareContentType.VIDEO,
            ShareContentType.PHOTO,
            ShareContentType.PROFILE,
            ShareContentType.AUDIO,
            ShareContentType.STORY -> true
            else -> false
        }
    }

    /**
     * Copies the canonical content link to device clipboard with user feedback.
     */
    fun copyLink(context: Context, payload: SharePayload) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val link = if (payload.canonicalUrl.isNotEmpty()) {
            payload.canonicalUrl
        } else {
            "https://socialindia.app/${payload.contentType.name.lowercase()}/${payload.contentId}"
        }
        val clip = ClipData.newPlainText("Social India Link", link)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    /**
     * Launches the standard Android system share sheet with proper title and text.
     */
    fun shareToSystem(context: Context, payload: SharePayload) {
        try {
            val link = if (payload.canonicalUrl.isNotEmpty()) {
                payload.canonicalUrl
            } else {
                "https://socialindia.app/${payload.contentType.name.lowercase()}/${payload.contentId}"
            }
            val shareText = if (payload.caption.isNotEmpty()) {
                "Check out this ${payload.contentType.label} by @${payload.ownerUsername} on Social India:\n\"${payload.caption}\"\n$link"
            } else {
                "Check out this ${payload.contentType.label} on Social India:\n$link"
            }

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Share ${payload.contentType.label} via")
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "No app available to share", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Toggles saving the item into the user's private saved collection.
     */
    suspend fun toggleSave(payload: SharePayload): Boolean = withContext(Dispatchers.IO) {
        val uid = RepositoryManager.getCurrentUserId()
        if (uid.isEmpty()) return@withContext false

        try {
            val savedRef = RepositoryManager.firestore.collection("users").document(uid)
                .collection("saved_items").document(payload.contentId)

            val doc = savedRef.get().await()
            if (doc.exists()) {
                savedRef.delete().await()
                return@withContext false // Unsaved
            } else {
                val data = hashMapOf(
                    "id" to payload.contentId,
                    "contentType" to payload.contentType.name,
                    "thumbnailUrl" to payload.thumbnailUrl,
                    "caption" to payload.caption,
                    "ownerUsername" to payload.ownerUsername,
                    "ownerAvatar" to payload.ownerAvatar,
                    "savedAt" to FieldValue.serverTimestamp()
                )
                savedRef.set(data).await()
                return@withContext true // Saved
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }
}
