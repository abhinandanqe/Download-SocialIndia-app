package com.example.repository

import android.util.Log
import com.example.model.FirebasePost
import com.example.model.Mention
import com.example.model.MentionDoc
import com.example.model.RepositoryManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

object MentionRepository {
    private const val TAG = "MentionRepository"
    private val db: FirebaseFirestore
        get() = RepositoryManager.firestore

    /**
     * Persists real structured mention relationships to the authoritative `mentions` collection
     * and sends targeted notifications to mentioned users with direct navigation destinations.
     */
    suspend fun saveContentMentions(
        contentId: String,
        contentType: String, // "post", "reel", "photo", "video", "story", "comment", "message"
        authorUid: String,
        authorUsername: String,
        authorAvatar: String,
        authorDisplayName: String = "",
        mentions: List<MentionDoc>,
        caption: String = "",
        mediaUrl: String = "",
        parentContentId: String = ""
    ) = withContext(Dispatchers.IO) {
        if (contentId.isEmpty() || mentions.isEmpty()) return@withContext

        val processedTargetUids = mutableSetOf<String>()

        for (mention in mentions) {
            val targetUid = mention.mentionedUserId.trim()
            if (targetUid.isEmpty() || targetUid == authorUid || processedTargetUids.contains(targetUid)) {
                continue
            }
            processedTargetUids.add(targetUid)

            try {
                // 1. Verify target account validity & block / privacy permissions
                val targetUserDoc = db.collection("users").document(targetUid).get().await()
                if (!targetUserDoc.exists()) continue
                if (targetUserDoc.getBoolean("isDeactivated") == true || targetUserDoc.getBoolean("isDeleted") == true) continue

                val whoCanMention = targetUserDoc.getString("whoCanMention") ?: "everyone"
                if (whoCanMention.equals("nobody", ignoreCase = true)) continue

                // Check block relationship
                val blockedByTarget = db.collection("users").document(targetUid)
                    .collection("blockedUsers").document(authorUid).get().await()
                if (blockedByTarget.exists()) continue

                val targetUsername = targetUserDoc.getString("username") ?: mention.mentionedUsername

                // Deterministic mention document ID
                val mentionId = if (mention.mentionId.isNotEmpty()) mention.mentionId else "mention_${contentType}_${contentId}_${targetUid}"

                val mentionData = hashMapOf(
                    "mentionId" to mentionId,
                    "mentionedUserId" to targetUid,
                    "mentionedUsername" to targetUsername,
                    "mentionedByUserId" to authorUid,
                    "mentionedByUsername" to authorUsername.ifEmpty { "Someone" },
                    "mentionedByAvatar" to authorAvatar,
                    "mentionedByDisplayName" to authorDisplayName.ifEmpty { authorUsername },
                    "contentType" to contentType,
                    "contentId" to contentId,
                    "parentContentId" to parentContentId,
                    "sourceContext" to mention.sourceContext,
                    "mentionText" to (mention.mentionText.ifEmpty { caption }),
                    "mediaUrl" to (mention.mediaUrl.ifEmpty { mediaUrl }),
                    "xRatio" to mention.xRatio,
                    "yRatio" to mention.yRatio,
                    "createdAt" to System.currentTimeMillis(),
                    "serverTimestamp" to FieldValue.serverTimestamp(),
                    "visibility" to mention.visibility,
                    "status" to "active"
                )

                // 2. Save to global mentions collection
                db.collection("mentions").document(mentionId).set(mentionData, SetOptions.merge()).await()

                // 3. Save reference in target user's tagged mentions subcollection for indexed queries
                db.collection("users").document(targetUid)
                    .collection("mentions").document(mentionId)
                    .set(mentionData, SetOptions.merge()).await()

                // 4. Dispatch structured direct-navigation Notification
                val notifId = "mention_${contentType}_${contentId}_${targetUid}"
                val notifData = hashMapOf(
                    "id" to notifId,
                    "recipientId" to targetUid,
                    "type" to "mention",
                    "actorId" to authorUid,
                    "actorUsername" to authorUsername.ifEmpty { "Someone" },
                    "actorAvatar" to authorAvatar,
                    "content" to "mentioned you in a $contentType: \"${caption.take(60)}\"",
                    "postId" to if (contentType in listOf("post", "reel", "comment", "photo", "video")) contentId else "",
                    "storyId" to if (contentType == "story") contentId else "",
                    "channelId" to if (contentType == "message" || contentType == "channel") contentId else "",
                    "contentType" to contentType,
                    "postThumbnail" to mediaUrl,
                    "mentionId" to mentionId,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "isRead" to false
                )

                db.collection("users").document(targetUid)
                    .collection("notifications").document(notifId)
                    .set(notifData, SetOptions.merge()).await()

            } catch (e: Exception) {
                Log.w(TAG, "Failed saving mention for $targetUid on $contentId", e)
            }
        }
    }

    /**
     * Real-time flow of all posts/reels where a user has been mentioned.
     * Respects user's mention removal or hiding ("Mentions Utha Sake").
     */
    fun getUserTaggedPostsFlow(targetUserId: String): Flow<List<FirebasePost>> = callbackFlow {
        if (targetUserId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection("users").document(targetUserId)
            .collection("mentions")
            .whereEqualTo("status", "active")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listen failed for tagged mentions", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val mentionDocs = snapshot.documents.mapNotNull { doc ->
                    try {
                        val contentId = doc.getString("contentId") ?: return@mapNotNull null
                        val contentType = doc.getString("contentType") ?: "post"
                        val mediaUrl = doc.getString("mediaUrl") ?: ""
                        val mentionId = doc.getString("mentionId") ?: doc.id
                        val authorUid = doc.getString("mentionedByUserId") ?: ""
                        val authorUname = doc.getString("mentionedByUsername") ?: ""
                        val authorAvatar = doc.getString("mentionedByAvatar") ?: ""
                        val text = doc.getString("mentionText") ?: ""
                        val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                        Triple(contentId, contentType, MentionDoc(
                            mentionId = mentionId,
                            mentionedUserId = targetUserId,
                            mentionedByUserId = authorUid,
                            mentionedByUsername = authorUname,
                            mentionedByAvatar = authorAvatar,
                            contentType = contentType,
                            contentId = contentId,
                            mentionText = text,
                            mediaUrl = mediaUrl,
                            createdAt = createdAt
                        ))
                    } catch (_: Exception) {
                        null
                    }
                }

                // Fetch actual post/content details for each mention
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val postsList = mutableListOf<FirebasePost>()
                    val processedPostIds = mutableSetOf<String>()

                    for ((contentId, contentType, mDoc) in mentionDocs) {
                        if (processedPostIds.contains(contentId)) continue
                        processedPostIds.add(contentId)

                        try {
                            if (contentType in listOf("post", "reel", "photo", "video")) {
                                val postDoc = db.collection("posts").document(contentId).get().await()
                                if (postDoc.exists()) {
                                    val post = postDoc.toObject(FirebasePost::class.java)
                                    if (post != null && !post.isDeleted) {
                                        postsList.add(post.copy(id = postDoc.id))
                                        continue
                                    }
                                }
                            }
                            // Fallback synthetic post if post doc was a lightweight story or direct media
                            if (mDoc.mediaUrl.isNotEmpty()) {
                                postsList.add(
                                    FirebasePost(
                                        id = contentId,
                                        userId = mDoc.mentionedByUserId,
                                        postUrl = mDoc.mediaUrl,
                                        caption = mDoc.mentionText
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error resolving post for mention $contentId", e)
                        }
                    }

                    // Also merge any posts directly tagged with taggedUserIds array
                    try {
                        val taggedPostsSnap = db.collection("posts")
                            .whereArrayContains("taggedUserIds", targetUserId)
                            .limit(30)
                            .get()
                            .await()
                        for (doc in taggedPostsSnap.documents) {
                            if (!processedPostIds.contains(doc.id)) {
                                processedPostIds.add(doc.id)
                                val p = doc.toObject(FirebasePost::class.java)
                                if (p != null && !p.isDeleted) {
                                    postsList.add(p.copy(id = doc.id))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error fetching taggedUserIds posts", e)
                    }

                    trySend(postsList)
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * "Mentions Utha Sake" Flow:
     * Mentioned user removes their mention from content.
     * The original content stays owned by the author; only the mention association is removed.
     */
    suspend fun removeMention(contentIdOrMentionId: String, currentUserId: String): Boolean = withContext(Dispatchers.IO) {
        if (contentIdOrMentionId.isEmpty() || currentUserId.isEmpty()) return@withContext false

        try {
            // Find all matching mention records
            val userMentionsQuery = db.collection("users").document(currentUserId)
                .collection("mentions")
                .whereIn("contentId", listOf(contentIdOrMentionId, contentIdOrMentionId.removePrefix("post_").removePrefix("reel_")))
                .get().await()

            for (doc in userMentionsQuery.documents) {
                doc.reference.delete().await()
                try {
                    db.collection("mentions").document(doc.id).update("status", "removed_by_user").await()
                } catch (_: Exception) {}
            }

            // Also check direct document by ID
            try {
                db.collection("users").document(currentUserId)
                    .collection("mentions").document(contentIdOrMentionId).delete().await()
            } catch (_: Exception) {}

            try {
                db.collection("mentions").document(contentIdOrMentionId).update("status", "removed_by_user").await()
            } catch (_: Exception) {}

            // Update embedded post tags if any
            try {
                val postRef = db.collection("posts").document(contentIdOrMentionId)
                val postDoc = postRef.get().await()
                if (postDoc.exists()) {
                    val currentTagged = postDoc.get("taggedUserIds") as? List<String> ?: emptyList()
                    if (currentTagged.contains(currentUserId)) {
                        postRef.update("taggedUserIds", currentTagged.filter { it != currentUserId }).await()
                    }
                }
            } catch (_: Exception) {}

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed removing mention", e)
            false
        }
    }

    /**
     * Hides a mention from the user's profile tagged tab without deleting the mention record.
     */
    suspend fun hideMentionFromProfile(mentionId: String, currentUserId: String, hide: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        try {
            val newStatus = if (hide) "hidden_by_user" else "active"
            db.collection("users").document(currentUserId)
                .collection("mentions").document(mentionId)
                .update("status", newStatus).await()
            db.collection("mentions").document(mentionId)
                .update("status", newStatus).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed updating mention visibility", e)
            false
        }
    }

    /**
     * Retrieves all active mentions associated with a specific post or story.
     */
    suspend fun getMentionsForContent(contentId: String): List<MentionDoc> = withContext(Dispatchers.IO) {
        if (contentId.isEmpty()) return@withContext emptyList()
        try {
            val snap = db.collection("mentions")
                .whereEqualTo("contentId", contentId)
                .whereEqualTo("status", "active")
                .get().await()

            snap.documents.mapNotNull { it.toObject(MentionDoc::class.java) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching mentions for content $contentId", e)
            emptyList()
        }
    }
}
