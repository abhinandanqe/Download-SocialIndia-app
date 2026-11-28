package com.example.repository

import android.util.Log
import com.example.model.RepositoryManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

data class AccountTransparencyInfo(
    val userId: String,
    val username: String,
    val displayName: String,
    val joinedDateFormatted: String,
    val isVerified: Boolean,
    val verificationType: String?,
    val isBusinessAccount: Boolean,
    val businessCategory: String?,
    val country: String = "India",
    val profilePhotoUrl: String = ""
)

class ReelsActionsRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val TAG = "ReelsActionsRepo"

    suspend fun markNotInterested(
        userId: String,
        videoId: String,
        creatorId: String,
        reason: String = "General"
    ): Boolean = withContext(Dispatchers.IO) {
        if (userId.isEmpty() || videoId.isEmpty()) return@withContext false
        try {
            val data = hashMapOf(
                "videoId" to videoId,
                "creatorId" to creatorId,
                "reason" to reason,
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(userId)
                .collection("not_interested").document(videoId)
                .set(data).await()

            // Also increment notInterested count on post document if desired
            try {
                firestore.collection("posts").document(videoId)
                    .update("notInterestedCount", FieldValue.increment(1)).await()
            } catch (_: Exception) {}

            true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error marking not interested", e)
            false
        }
    }

    suspend fun undoNotInterested(userId: String, videoId: String): Boolean = withContext(Dispatchers.IO) {
        if (userId.isEmpty() || videoId.isEmpty()) return@withContext false
        try {
            firestore.collection("users").document(userId)
                .collection("not_interested").document(videoId)
                .delete().await()
            try {
                firestore.collection("posts").document(videoId)
                    .update("notInterestedCount", FieldValue.increment(-1)).await()
            } catch (_: Exception) {}
            true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error undoing not interested", e)
            false
        }
    }

    suspend fun hideCreator(
        userId: String,
        creatorId: String,
        creatorUsername: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (userId.isEmpty() || creatorId.isEmpty()) return@withContext false
        try {
            val data = hashMapOf(
                "creatorId" to creatorId,
                "creatorUsername" to creatorUsername,
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(userId)
                .collection("hidden_creators").document(creatorId)
                .set(data).await()
            true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding creator", e)
            false
        }
    }

    suspend fun undoHideCreator(userId: String, creatorId: String): Boolean = withContext(Dispatchers.IO) {
        if (userId.isEmpty() || creatorId.isEmpty()) return@withContext false
        try {
            firestore.collection("users").document(userId)
                .collection("hidden_creators").document(creatorId)
                .delete().await()
            true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error undoing hide creator", e)
            false
        }
    }

    suspend fun blockCreator(
        userId: String,
        creatorId: String,
        creatorUsername: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (userId.isEmpty() || creatorId.isEmpty()) return@withContext false
        try {
            val blockData = hashMapOf(
                "blockedUserId" to creatorId,
                "username" to creatorUsername,
                "blockedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(userId)
                .collection("blocked_users").document(creatorId)
                .set(blockData).await()

            // Also unfollow automatically if following
            try {
                RepositoryManager.socialRepository.unfollowUser(userId, creatorId)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Exception) {}

            true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking creator", e)
            false
        }
    }

    suspend fun unblockCreator(userId: String, creatorId: String): Boolean = withContext(Dispatchers.IO) {
        if (userId.isEmpty() || creatorId.isEmpty()) return@withContext false
        try {
            firestore.collection("users").document(userId)
                .collection("blocked_users").document(creatorId)
                .delete().await()
            true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error unblocking creator", e)
            false
        }
    }

    fun getHiddenCreatorIds(userId: String): Flow<Set<String>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(userId)
            .collection("hidden_creators")
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptySet())
                    return@addSnapshotListener
                }
                val ids = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
                trySend(ids)
            }
        awaitClose { listener.remove() }
    }

    fun getNotInterestedVideoIds(userId: String): Flow<Set<String>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(userId)
            .collection("not_interested")
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptySet())
                    return@addSnapshotListener
                }
                val ids = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
                trySend(ids)
            }
        awaitClose { listener.remove() }
    }

    suspend fun reportReel(
        reporterId: String,
        videoId: String,
        creatorId: String,
        reason: String,
        details: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        if (reporterId.isBlank() || videoId.isBlank()) return@withContext false
        if (!com.example.security.InputGuard.allowAction("report:$videoId", 5000L)) return@withContext false
        try {
            val reportId = UUID.randomUUID().toString()
            val safeReason = com.example.security.InputGuard.sanitizeReportReason(reason).ifBlank { "Community Guideline Flag" }
            val safeDetails = com.example.security.InputGuard.sanitizeReportReason(details)
            val reportData = hashMapOf(
                "reportId" to reportId,
                "reporterId" to reporterId,
                "contentId" to videoId,
                "contentType" to "REEL",
                "reportedUserId" to creatorId,
                "reason" to safeReason,
                "details" to safeDetails,
                "status" to "PENDING",
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection("reports").document(reportId).set(reportData).await()
            try {
                firestore.collection("posts").document(videoId)
                    .update("reportsCount", FieldValue.increment(1)).await()
            } catch (_: Exception) {}
            true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting report", e)
            false
        }
    }

    suspend fun updateCaption(videoId: String, newCaption: String): Boolean = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext false
        val safeCaption = com.example.security.InputGuard.sanitizeCaption(newCaption)
        try {
            firestore.collection("posts").document(videoId).update("caption", safeCaption).await()
            try {
                firestore.collection("videos").document(videoId).update("description", safeCaption).await()
            } catch (_: Exception) {}
            true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error updating caption", e)
            false
        }
    }

    suspend fun toggleComments(videoId: String, allowComments: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("posts").document(videoId).update("allowComments", allowComments).await()
            true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error updating comment permission", e)
            false
        }
    }

    suspend fun toggleRemix(videoId: String, allowRemix: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("posts").document(videoId).update("allowRemix", allowRemix).await()
            true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error updating remix permission", e)
            false
        }
    }

    suspend fun archiveReel(videoId: String, isArchived: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("posts").document(videoId).update("isArchived", isArchived).await()
            true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error archiving reel", e)
            false
        }
    }

    suspend fun deleteReel(videoId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("posts").document(videoId).delete().await()
            try {
                firestore.collection("videos").document(videoId).delete().await()
            } catch (_: Exception) {}
            true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting reel", e)
            false
        }
    }

    suspend fun getAccountTransparency(userId: String): AccountTransparencyInfo? = withContext(Dispatchers.IO) {
        if (userId.isEmpty()) return@withContext null
        try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            if (!userDoc.exists()) return@withContext null

            val username = userDoc.getString("username") ?: "creator"
            val displayName = userDoc.getString("displayName") ?: userDoc.getString("name") ?: username
            val avatarUrl = userDoc.getString("profilePicUrl") ?: userDoc.getString("avatarUrl") ?: ""
            val createdAtMillis = userDoc.getLong("createdAt") ?: userDoc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
            val dateFormat = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
            val joinedDateFormatted = dateFormat.format(java.util.Date(createdAtMillis))

            val isVerified = userDoc.getBoolean("isVerified") ?: false
            val verificationType = userDoc.getString("verificationType")
            val isBusiness = userDoc.getBoolean("isBusinessAccount") ?: false
            val businessCategory = userDoc.getString("businessCategory")
            val country = userDoc.getString("country") ?: "India"

            AccountTransparencyInfo(
                userId = userId,
                username = username,
                displayName = displayName,
                joinedDateFormatted = joinedDateFormatted,
                isVerified = isVerified,
                verificationType = verificationType,
                isBusinessAccount = isBusiness,
                businessCategory = businessCategory,
                country = country,
                profilePhotoUrl = avatarUrl
            )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching account transparency", e)
            null
        }
    }
}
