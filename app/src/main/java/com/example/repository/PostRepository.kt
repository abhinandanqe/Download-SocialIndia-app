package com.example.repository

import com.example.model.Comment
import com.example.model.FirebasePost
import com.example.model.Story
import com.example.model.StoryHighlight
import com.google.firebase.firestore.FieldValue
import com.example.model.RepositoryManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostRepository {
    private val db = FirebaseFirestore.getInstance()
    @Volatile
    var cachedPosts: List<com.example.model.VideoPost> = emptyList()

    suspend fun createPost(
        userId: String, 
        mediaUrl: String, 
        caption: String,
        musicTrack: String? = null,
        musicTrackUrl: String? = null,
        musicTrackImageUrl: String? = null,
        postUrls: List<String> = emptyList(),
        collaboratorIds: List<String> = emptyList(),
        isSubscribersOnly: Boolean = false,
        premiumPrice: Double = 0.0,
        isTrialReel: Boolean = false,
        trialAudience: String = "test_audience",
        addYoursPromptId: String? = null,
        addYoursPromptText: String? = null,
        videoWidth: Int = 0,
        videoHeight: Int = 0,
        aspectRatio: Float = 0f,
        explicitMentions: List<com.example.model.User> = emptyList(),
        thumbnailUrl: String? = null,
        isPaidPost: Boolean = false,
        postUnlockPrice: Double = 0.0,
        accessType: String = "PUBLIC",
        visibility: String = "PUBLIC",
        isVideoOverride: Boolean? = null,
        blurredThumbnailUrl: String = "",
        teaserCaption: String = ""
    ): Boolean {
        var savedToFirestore = false
        return try {
            val postId = UUID.randomUUID().toString()
            val actualUserId = userId.takeIf { it.isNotBlank() && it != "anonymous_user" }
                ?: com.example.model.RepositoryManager.getCurrentUserId().takeIf { it.isNotBlank() && it != "anonymous_user" }
                ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() && it != "anonymous_user" }
                ?: return false
            val isMediaImage = when (isVideoOverride) {
                false -> true
                true -> false
                null -> com.example.utils.MediaResolver.isImage(mediaUrl)
            }
            val isReel = !isMediaImage && videoWidth > 0 && (videoHeight > videoWidth)
            val detectedContentType = if (isMediaImage) "image" else if (isReel) "reel" else "post"
            val thumb = if (isMediaImage) {
                com.example.utils.MediaResolver.resolveImageUrl(thumbnailUrl?.takeIf { it.isNotBlank() } ?: mediaUrl).ifBlank { mediaUrl }
            } else {
                com.example.utils.MediaResolver.resolveVideoPosterUrl(mediaUrl, thumbnailUrl)
                    .ifBlank { musicTrackImageUrl?.takeIf { it.isNotBlank() } ?: "" }
            }
            val normalizedVisibility = when {
                visibility.equals("PRIVATE", ignoreCase = true) || visibility.contains("Only Me", ignoreCase = true) -> "PRIVATE"
                visibility.equals("FOLLOWERS_ONLY", ignoreCase = true) || visibility.contains("Followers", ignoreCase = true) -> "FOLLOWERS_ONLY"
                else -> "PUBLIC"
            }
            android.util.Log.d("PostRepository", "Creating post for user: $actualUserId, media: $mediaUrl, isImage: $isMediaImage, visibility: $normalizedVisibility, thumb: $thumb, dimensions: ${videoWidth}x${videoHeight}, ratio: $aspectRatio")
            
            val taggedUserIds = explicitMentions.map { it.id }.filter { it.isNotEmpty() }
            val authorDoc = try { db.collection("users").document(actualUserId).get().await() } catch (_: Exception) { null }
            val activeAcc = com.example.auth.MultiAccountManager.activeAccount.value
            val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val realAuthorUsername = authorDoc?.getString("username")
                ?: activeAcc?.username?.takeIf { it.isNotBlank() }
                ?: authUser?.displayName?.takeIf { it.isNotBlank() }
                ?: authUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: "User"
            val realAuthorAvatar = authorDoc?.getString("profilePicUrl")
                ?: authorDoc?.getString("avatarUrl")
                ?: activeAcc?.avatarUrl?.takeIf { it.isNotBlank() }
                ?: authUser?.photoUrl?.toString()
                ?: ""
            val realAuthorDisplayName = authorDoc?.getString("name")
                ?: authorDoc?.getString("displayName")
                ?: activeAcc?.displayName
                ?: realAuthorUsername
            val isAuthorVerified = authorDoc?.getBoolean("isVerified") ?: activeAcc?.isVerified ?: false
            val authorBadgeUrl = authorDoc?.getString("verificationBadgeUrl") ?: ""

            val isExclusivePaid = isPaidPost && postUnlockPrice >= 10.0
            val realMediaUrls = postUrls.ifEmpty { listOf(mediaUrl) }
            val publicPreviewUrl = if (isExclusivePaid) {
                blurredThumbnailUrl.ifBlank { "" }
            } else {
                mediaUrl
            }
            val publicThumbUrl = if (isExclusivePaid) {
                blurredThumbnailUrl.ifBlank { "" }
            } else {
                thumb
            }
            val publicPostUrls = if (isExclusivePaid) {
                if (publicPreviewUrl.isNotBlank()) listOf(publicPreviewUrl) else emptyList()
            } else {
                realMediaUrls
            }

            // CRITICAL SECURITY: Store original unblurred media ONLY in private exclusive_media_vault/{postId}
            if (isExclusivePaid) {
                val vaultSaved = ExclusiveContentRepository.saveVaultMedia(
                    contentId = postId,
                    creatorId = actualUserId,
                    contentType = if (isMediaImage) "POST" else "REEL",
                    mediaUrl = mediaUrl,
                    postUrls = realMediaUrls,
                    thumbnailUrl = thumb
                )
                if (!vaultSaved) {
                    android.util.Log.e("PostRepository", "Failed to save exclusive vault media for $postId")
                    return false
                }
            }

            val post = hashMapOf(
                "id" to postId,
                "userId" to actualUserId,
                "username" to realAuthorUsername,
                "userAvatarUrl" to realAuthorAvatar,
                "userProfilePic" to realAuthorAvatar,
                "userDisplayName" to realAuthorDisplayName,
                "isUserVerified" to isAuthorVerified,
                "userVerificationBadgeUrl" to authorBadgeUrl,
                "postUrl" to publicPreviewUrl,
                "thumbnailUrl" to publicThumbUrl,
                "mediaType" to (if (isMediaImage) "image" else "video"),
                "caption" to caption,
                "visibility" to normalizedVisibility,
                "isDeleted" to false,
                "isArchived" to false,
                "isPinned" to false,
                "likesCount" to 0,
                "commentsCount" to 0,
                "viewsCount" to 0,
                "savesCount" to 0,
                "sharesCount" to 0,
                "repostsCount" to 0,
                "reportsCount" to 0,
                "likedByUserIds" to emptyList<String>(),
                "repostedByUserIds" to emptyList<String>(),
                "createdAtMillis" to System.currentTimeMillis(),
                "timestamp" to FieldValue.serverTimestamp(),
                "musicTrack" to musicTrack,
                "musicTrackUrl" to musicTrackUrl,
                "musicTrackImageUrl" to musicTrackImageUrl,
                "postUrls" to publicPostUrls,
                "collaboratorIds" to emptyList<String>(),
                "pendingCollaboratorIds" to collaboratorIds,
                "taggedUserIds" to taggedUserIds,
                "isSubscribersOnly" to isSubscribersOnly,
                "isExclusive" to (isSubscribersOnly || isExclusivePaid),
                "premiumPrice" to if (isExclusivePaid) postUnlockPrice else premiumPrice,
                "isPaidPost" to isExclusivePaid,
                "postUnlockPrice" to if (isExclusivePaid) postUnlockPrice else 0.0,
                "blurredThumbnailUrl" to if (isExclusivePaid) blurredThumbnailUrl else "",
                "teaserCaption" to if (isExclusivePaid) teaserCaption.trim() else "",
                "hasVaultMedia" to isExclusivePaid,
                "unlockCount" to 0,
                "exclusiveRevenue" to 0.0,
                "isRemovedByAdmin" to false,
                "accessType" to (if (isExclusivePaid) "INDIVIDUAL_PAID" else if (isSubscribersOnly) "SUBSCRIBERS_ONLY" else accessType),
                "isTrialReel" to isTrialReel,
                "trialAudience" to trialAudience,
                "trialWatchTimeMs" to 0L,
                "addYoursPromptId" to addYoursPromptId,
                "addYoursPromptText" to addYoursPromptText,
                "videoWidth" to videoWidth,
                "videoHeight" to videoHeight,
                "aspectRatio" to aspectRatio
            )
            db.collection("posts").document(postId).set(post).await()
            savedToFirestore = true
            // Send collaboration requests
            if (collaboratorIds.isNotEmpty()) {
                val currentUserDoc = db.collection("users").document(actualUserId).get().await()
                val username = currentUserDoc.getString("username") ?: "User"
                val avatarUrl = currentUserDoc.getString("profilePicUrl") ?: currentUserDoc.getString("avatarUrl") ?: ""
                collaboratorIds.forEach { collabId ->
                    val notificationId = "collab_req_${postId}_${collabId}"
                    val notification = hashMapOf(
                        "id" to notificationId,
                        "type" to "collaboration_request",
                        "actorId" to actualUserId,
                        "actorUsername" to username,
                        "actorAvatar" to avatarUrl,
                        "postId" to postId,
                        "postThumbnail" to mediaUrl,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "isRead" to false
                    )
                    db.collection("users").document(collabId).collection("notifications").document(notificationId).set(notification)
                }
            }

            // Save Authoritative Mentions & Send Direct Navigation Notifications
            try {
                val currentUserDoc = db.collection("users").document(actualUserId).get().await()
                val username = currentUserDoc.getString("username") ?: "User"
                val avatarUrl = currentUserDoc.getString("profilePicUrl") ?: currentUserDoc.getString("avatarUrl") ?: ""
                val displayName = currentUserDoc.getString("displayName") ?: username

                val mentionDocs = mutableListOf<com.example.model.MentionDoc>()

                // 1. Explicitly selected user mentions
                explicitMentions.forEach { user ->
                    if (user.id.isNotEmpty() && user.id != actualUserId) {
                        mentionDocs.add(
                            com.example.model.MentionDoc(
                                mentionedUserId = user.id,
                                mentionedUsername = user.username,
                                contentType = detectedContentType,
                                contentId = postId,
                                sourceContext = "picker",
                                mentionText = caption,
                                mediaUrl = mediaUrl
                            )
                        )
                    }
                }

                // 2. Extracted text mentions from caption
                val extractedMentions = com.example.utils.MentionHelper.extractMentionsFromText(caption)
                extractedMentions.forEach { m ->
                    if (m.uid.isNotEmpty() && m.uid != actualUserId && !mentionDocs.any { it.mentionedUserId == m.uid }) {
                        mentionDocs.add(
                            com.example.model.MentionDoc(
                                mentionedUserId = m.uid,
                                mentionedUsername = m.username,
                                contentType = detectedContentType,
                                contentId = postId,
                                sourceContext = "caption",
                                mentionText = caption,
                                mediaUrl = mediaUrl
                            )
                        )
                    }
                }

                if (mentionDocs.isNotEmpty()) {
                    MentionRepository.saveContentMentions(
                        contentId = postId,
                        contentType = detectedContentType,
                        authorUid = actualUserId,
                        authorUsername = username,
                        authorAvatar = avatarUrl,
                        authorDisplayName = displayName,
                        mentions = mentionDocs,
                        caption = caption,
                        mediaUrl = mediaUrl
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("PostRepository", "Failed saving authoritative mentions", e)
            }

            try {
                val followersSnapshot = db.collection("follows")
                    .whereEqualTo("followingId", actualUserId)
                    .get().await()
                
                if (!followersSnapshot.isEmpty) {
                    val currentUserDoc = db.collection("users").document(actualUserId).get().await()
                    val username = currentUserDoc.getString("username") ?: "User"
                    val avatarUrl = currentUserDoc.getString("profilePicUrl") ?: currentUserDoc.getString("avatarUrl") ?: ""
                    
                    val followerIds = followersSnapshot.documents.mapNotNull { it.getString("followerId") }
                    
                    for (followerId in followerIds) {
                        val notifId = "newpost_${postId}_${followerId}"
                        val notification = hashMapOf(
                            "id" to notifId,
                            "type" to "new_post",
                            "actorId" to actualUserId,
                            "actorUsername" to username,
                            "actorAvatar" to avatarUrl,
                            "postId" to postId,
                            "postThumbnail" to mediaUrl,
                            "timestamp" to FieldValue.serverTimestamp(),
                            "isRead" to false
                        )
                        db.collection("users").document(followerId).collection("notifications").document(notifId).set(notification)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PostRepository", "Failed to notify followers", e)
            }

            android.util.Log.d("PostRepository", "Post created successfully: $postId")
            true
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Failed to create post", e)
            savedToFirestore
        }
    }

    suspend fun deletePost(postId: String): Boolean {
        return try {
            db.collection("posts").document(postId).delete().await()
            android.util.Log.d("PostRepository", "Post deleted successfully: $postId")
            true
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Failed to delete post: $postId", e)
            false
        }
    }

    suspend fun updatePost(
        postId: String,
        caption: String,
        tags: String? = null,
        location: String? = null,
        commentsDisabled: Boolean = false,
        downloadsDisabled: Boolean = false
    ): Boolean {
        return try {
            val updates = hashMapOf<String, Any>(
                "caption" to caption,
                "commentsDisabled" to commentsDisabled,
                "downloadsDisabled" to downloadsDisabled
            )
            if (tags != null) updates["tags"] = tags
            if (location != null) updates["location"] = location
            
            db.collection("posts").document(postId).update(updates).await()
            android.util.Log.d("PostRepository", "Post updated successfully: $postId")
            true
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Failed to update post: $postId", e)
            false
        }
    }

    suspend fun createStory(userId: String, mediaUrl: String, textOverlay: String = ""): Boolean {
        return try {
            val storyId = UUID.randomUUID().toString()
            val actualUserId = userId.takeIf { it.isNotBlank() && it != "anonymous_user" }
                ?: com.example.model.RepositoryManager.getCurrentUserId().takeIf { it.isNotBlank() && it != "anonymous_user" }
                ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() && it != "anonymous_user" }
                ?: return false
            // 24 hours from now
            val expireAt = Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000)
            
            val story = hashMapOf(
                "id" to storyId,
                "userId" to actualUserId,
                "mediaUrl" to mediaUrl,
                "timestamp" to FieldValue.serverTimestamp(),
                "expireAt" to expireAt,
                "textOverlay" to textOverlay,
                "viewers" to emptyList<String>(),
                "likesCount" to 0,
                "commentsCount" to 0
            )
            db.collection("stories").document(storyId).set(story).await()
            android.util.Log.d("PostRepository", "Story created successfully: $storyId")
            true
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Failed to create story", e)
            e.printStackTrace()
            false
        }
    }

    suspend fun createLiveStory(userId: String, mediaUrl: String): String? {
        return try {
            val storyId = UUID.randomUUID().toString()
            val actualUserId = userId.takeIf { it.isNotBlank() && it != "anonymous_user" }
                ?: com.example.model.RepositoryManager.getCurrentUserId().takeIf { it.isNotBlank() && it != "anonymous_user" }
                ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() && it != "anonymous_user" }
                ?: return null
            val expireAt = Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000)
            
            val story = hashMapOf(
                "id" to storyId,
                "userId" to actualUserId,
                "mediaUrl" to mediaUrl,
                "timestamp" to FieldValue.serverTimestamp(),
                "expireAt" to expireAt,
                "isLive" to true,
                "viewers" to emptyList<String>(),
                "likesCount" to 0,
                "commentsCount" to 0
            )
            db.collection("stories").document(storyId).set(story).await()
            if (actualUserId.isNotEmpty() && actualUserId != "anonymous_user") {
                try {
                    db.collection("users").document(actualUserId).update(
                        mapOf(
                            "isLive" to true,
                            "currentLiveId" to storyId
                        )
                    ).await()
                } catch (ignored: Exception) {}
            }
            android.util.Log.d("PostRepository", "Live story created: $storyId")
            storyId
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Failed to create live story", e)
            e.printStackTrace()
            null
        }
    }

    suspend fun endLiveStory(storyId: String): Boolean {
        return try {
            val doc = db.collection("stories").document(storyId).get().await()
            val hostUserId = doc.getString("userId") ?: ""
            db.collection("stories").document(storyId).update("isLive", false).await()
            if (hostUserId.isNotEmpty()) {
                try {
                    db.collection("users").document(hostUserId).update(
                        mapOf(
                            "isLive" to false,
                            "currentLiveId" to ""
                        )
                    ).await()
                } catch (ignored: Exception) {}
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Failed to end live story", e)
            false
        }
    }

    @Volatile
    private var anonymousCleanupDone = false

    suspend fun cleanupAnonymousUploads() = withContext(Dispatchers.IO) {
        if (anonymousCleanupDone) return@withContext
        val authUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (authUid.isNullOrBlank() || authUid == "anonymous_user") return@withContext
        anonymousCleanupDone = true
        try {
            val removedPostIds = mutableSetOf<String>()
            for (badUid in listOf("anonymous_user", "")) {
                try {
                    val badPosts = db.collection("posts").whereEqualTo("userId", badUid).limit(50).get().await()
                    for (doc in badPosts.documents) {
                        try {
                            doc.reference.delete().await()
                            removedPostIds.add(doc.id)
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
                try {
                    val badStories = db.collection("stories").whereEqualTo("userId", badUid).limit(50).get().await()
                    for (doc in badStories.documents) {
                        try {
                            doc.reference.delete().await()
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
            if (removedPostIds.isNotEmpty()) {
                cachedPosts = cachedPosts.filter { it.id !in removedPostIds && it.userId != "anonymous_user" && it.userId.isNotBlank() }
                val cachedFeed = RepositoryManager.getCachedFeedVideos()
                    .filter { it.id !in removedPostIds && it.userId != "anonymous_user" && it.userId.isNotBlank() }
                if (cachedFeed.isNotEmpty()) {
                    RepositoryManager.setCachedFeedVideos(cachedFeed)
                }
            }
            try {
                db.collection("users").document("anonymous_user").delete().await()
            } catch (_: Exception) {}
        } catch (e: Exception) {
            android.util.Log.w("PostRepository", "Anonymous upload cleanup skipped: ${e.message}")
        }
    }

    suspend fun incrementViews(postId: String) {
        if (postId.isEmpty()) return
        try {
            db.collection("posts").document(postId)
                .update("viewsCount", FieldValue.increment(1)).await()
                
            val postDoc = db.collection("posts").document(postId).get().await()
            val creatorId = postDoc.getString("creatorId")
            if (creatorId != null) {
                db.collection("users").document(creatorId).update(
                    "walletBalance", FieldValue.increment(0.0005),
                    "earnings", FieldValue.increment(0.0005)
                ).await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addLike(postId: String, userId: String) {
        if (postId.isEmpty() || userId.isEmpty()) return
        db.collection("posts").document(postId)
            .collection("likes").document(userId)
            .set(mapOf("timestamp" to FieldValue.serverTimestamp())).await()
        db.collection("posts").document(postId)
            .update("likesCount", FieldValue.increment(1)).await()
    }

    suspend fun addComment(postId: String, comment: Comment) {
        if (postId.isEmpty()) return
        val commentId = java.util.UUID.randomUUID().toString()
        val finalComment = comment.copy(id = commentId)
        db.collection("posts").document(postId).collection("comments").document(commentId).set(finalComment).await()
        try {
            val postDoc = db.collection("posts").document(postId).get().await()
            val ownerId = postDoc.getString("creatorId") ?: postDoc.getString("userId")
            if (ownerId != null && ownerId != comment.userId) {
                val notificationId = java.util.UUID.randomUUID().toString()
                val notifMap = hashMapOf(
                    "id" to notificationId,
                    "recipientId" to ownerId,
                    "type" to "COMMENT",
                    "actorId" to comment.userId,
                    "actorUsername" to comment.username,
                    "actorAvatar" to comment.avatarUrl,
                    "content" to "commented: ${comment.text}",
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                db.collection("users").document(ownerId).collection("notifications").document(notificationId).set(notifMap).await()
            }

            // Mentions in comment
            val mentionRegex = Regex("@([a-zA-Z0-9_.]+)")
            val mentionedUsernames = mentionRegex.findAll(comment.text).map { it.groupValues[1] }.toSet()
            for (mUsername in mentionedUsernames) {
                val userSnap = db.collection("users").whereEqualTo("username", mUsername).limit(1).get().await()
                val mDoc = userSnap.documents.firstOrNull()
                if (mDoc != null && mDoc.id != comment.userId && mDoc.id != ownerId) {
                    val notif = hashMapOf(
                        "type" to "mention",
                        "actorId" to comment.userId,
                        "actorUsername" to comment.username,
                        "actorAvatar" to comment.avatarUrl,
                        "postId" to postId,
                        "content" to "mentioned you in a comment: ${comment.text}",
                        "timestamp" to FieldValue.serverTimestamp(),
                        "isRead" to false
                    )
                    db.collection("users").document(mDoc.id).collection("notifications").add(notif)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("PostRepository", "Error adding comment notif", e)
        }
    }

    fun getCommentsListener(postId: String, onResult: (List<Comment>) -> Unit): com.google.firebase.firestore.ListenerRegistration? {
        if (postId.isEmpty()) return null
        return db.collection("posts").document(postId)
            .collection("comments")
            .orderBy("timestamp")
            .limit(100)
            .addSnapshotListener { snapshot, _ ->
                val comments = snapshot?.documents?.mapNotNull { doc ->
                    val c = doc.toObject(Comment::class.java)
                    c?.copy(id = doc.id)
                } ?: emptyList()
                onResult(comments)
            }
    }

    suspend fun markStoryAsViewed(storyId: String, userId: String) {
        if (storyId.isEmpty() || userId.isEmpty()) return
        try {
            val storyRef = db.collection("stories").document(storyId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(storyRef)
                if (snapshot.exists()) {
                    val viewers = (snapshot.get("viewers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    if (!viewers.contains(userId)) {
                        val newViewers = viewers.toMutableList().apply { add(userId) }
                        transaction.update(storyRef, "viewers", newViewers)
                    }
                }
                null
            }.await()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Error marking story viewed", e)
        }
    }

    suspend fun likeStory(storyId: String, userId: String) {
        if (storyId.isEmpty() || userId.isEmpty()) return
        val key = "story_like:${storyId}:${userId}"
        if (!com.example.security.InputGuard.tryBeginMutation(key)) return
        try {
            val storyRef = db.collection("stories").document(storyId)
            val likeRef = storyRef.collection("likes").document(userId)
            db.runTransaction { txn ->
                val storySnap = txn.get(storyRef)
                val likeSnap = txn.get(likeRef)
                if (storySnap.exists() && !likeSnap.exists()) {
                    val currentLikes = (storySnap.getLong("likesCount") ?: 0L).coerceAtLeast(0L)
                    txn.set(likeRef, mapOf("timestamp" to FieldValue.serverTimestamp()))
                    txn.update(storyRef, "likesCount", currentLikes + 1L)
                }
                null
            }.await()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Error liking story $storyId", e)
        } finally {
            com.example.security.InputGuard.endMutation(key)
        }
    }

    suspend fun unlikeStory(storyId: String, userId: String) {
        if (storyId.isEmpty() || userId.isEmpty()) return
        val key = "story_like:${storyId}:${userId}"
        if (!com.example.security.InputGuard.tryBeginMutation(key)) return
        try {
            val storyRef = db.collection("stories").document(storyId)
            val likeRef = storyRef.collection("likes").document(userId)
            db.runTransaction { txn ->
                val storySnap = txn.get(storyRef)
                val likeSnap = txn.get(likeRef)
                if (storySnap.exists() && likeSnap.exists()) {
                    val currentLikes = (storySnap.getLong("likesCount") ?: 0L).coerceAtLeast(0L)
                    txn.delete(likeRef)
                    txn.update(storyRef, "likesCount", (currentLikes - 1L).coerceAtLeast(0L))
                }
                null
            }.await()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Error unliking story $storyId", e)
        } finally {
            com.example.security.InputGuard.endMutation(key)
        }
    }

    fun isStoryLiked(storyId: String, userId: String): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.callbackFlow {
        if (storyId.isEmpty() || userId.isEmpty()) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val listener = db.collection("stories").document(storyId).collection("likes").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(false)
                    return@addSnapshotListener
                }
                trySend(snapshot != null && snapshot.exists())
            }
        awaitClose { listener.remove() }
    }

    suspend fun reactToStory(storyId: String, userId: String, reaction: String = "❤️") {
        if (storyId.isEmpty() || userId.isEmpty()) return
        if (!com.example.security.InputGuard.allowAction("story_react:$storyId", 350L)) return
        try {
            val storyRef = db.collection("stories").document(storyId)
            val likeRef = storyRef.collection("likes").document(userId)
            db.runTransaction { txn ->
                val storySnap = txn.get(storyRef)
                if (!storySnap.exists()) return@runTransaction null
                val alreadyLiked = txn.get(likeRef).exists()
                val currentLikes = (storySnap.getLong("likesCount") ?: 0L).coerceAtLeast(0L)
                txn.set(likeRef, mapOf("timestamp" to FieldValue.serverTimestamp(), "reaction" to reaction.take(16)), SetOptions.merge())
                if (!alreadyLiked) {
                    txn.update(storyRef, "likesCount", currentLikes + 1L)
                }
                null
            }.await()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Error reacting to story $storyId", e)
        }
    }

    fun getStoryReactions(storyId: String): kotlinx.coroutines.flow.Flow<Map<String, String>> = kotlinx.coroutines.flow.callbackFlow {
        if (storyId.isEmpty()) {
            trySend(emptyMap())
            close()
            return@callbackFlow
        }
        val listener = db.collection("stories").document(storyId).collection("likes")
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyMap())
                    return@addSnapshotListener
                }
                val result = mutableMapOf<String, String>()
                snapshot?.documents?.forEach { doc ->
                    val reaction = doc.getString("reaction")?.takeIf { it.isNotBlank() } ?: "❤️"
                    result[doc.id] = reaction
                }
                trySend(result)
            }
        awaitClose { listener.remove() }
    }

    fun getStoryUserReaction(storyId: String, userId: String): kotlinx.coroutines.flow.Flow<String?> = kotlinx.coroutines.flow.callbackFlow {
        if (storyId.isEmpty() || userId.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = db.collection("stories").document(storyId).collection("likes").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val reaction = if (snapshot != null && snapshot.exists()) {
                    snapshot.getString("reaction") ?: "❤️"
                } else null
                trySend(reaction)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addStoryComment(storyId: String, comment: Comment) {
        if (storyId.isEmpty()) return
        val cleanText = com.example.security.InputGuard.sanitizeComment(comment.text)
        if (cleanText.isEmpty()) return
        if (!com.example.security.InputGuard.allowAction("story_comment:$storyId", 800L)) return
        try {
            val storyRef = db.collection("stories").document(storyId)
            val storySnap = storyRef.get().await()
            if (!storySnap.exists()) return
            val commentId = java.util.UUID.randomUUID().toString()
            val finalComment = comment.copy(id = commentId, text = cleanText)
            val batch = db.batch()
            batch.set(storyRef.collection("comments").document(commentId), finalComment)
            batch.set(storyRef, mapOf("commentsCount" to FieldValue.increment(1)), SetOptions.merge())
            batch.commit().await()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Error adding story comment", e)
        }
    }

    fun getStoryCommentsListener(storyId: String, onResult: (List<Comment>) -> Unit): com.google.firebase.firestore.ListenerRegistration? {
        if (storyId.isEmpty()) {
            onResult(emptyList())
            return null
        }
        return db.collection("stories").document(storyId)
            .collection("comments")
            .orderBy("timestamp")
            .limit(100)
            .addSnapshotListener { snapshot, _ ->
                val comments = snapshot?.documents?.mapNotNull { doc ->
                    val c = doc.toObject(Comment::class.java)
                    c?.copy(id = doc.id)
                } ?: emptyList()
                onResult(comments)
            }
    }

    suspend fun getStoryLikers(storyId: String): List<com.example.model.User> {
        if (storyId.isEmpty()) return emptyList()
        return try {
            val likesSnapshot = db.collection("stories").document(storyId).collection("likes").limit(200).get().await()
            val likerIds = likesSnapshot.documents.map { it.id }
            if (likerIds.isEmpty()) return emptyList()
            
            val users = mutableListOf<com.example.model.User>()
            for (chunk in likerIds.chunked(30)) {
                val snapshot = db.collection("users").whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk).get().await()
                val mappedUsers = snapshot.documents.mapNotNull { doc ->
                    com.example.model.User(
                        id = doc.id,
                        username = doc.getString("username") ?: "User",
                        avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: doc.getString("avatar_url") ?: "",
                        isVerified = doc.getBoolean("isVerified") ?: false
                    )
                }
                users.addAll(mappedUsers)
            }
            users
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getStoryViewers(storyId: String): List<com.example.model.User> {
        if (storyId.isEmpty()) return emptyList()
        return try {
            val storyDoc = db.collection("stories").document(storyId).get().await()
            val viewerIds = (storyDoc.get("viewers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (viewerIds.isEmpty()) return emptyList()
            
            val users = mutableListOf<com.example.model.User>()
            for (chunk in viewerIds.take(150).chunked(30)) {
                val snapshot = db.collection("users").whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk).get().await()
                val mappedUsers = snapshot.documents.mapNotNull { doc ->
                    com.example.model.User(
                        id = doc.id,
                        username = doc.getString("username") ?: "User",
                        avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: doc.getString("avatar_url") ?: "",
                        isVerified = doc.getBoolean("isVerified") ?: false
                    )
                }
                users.addAll(mappedUsers)
            }
            users
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getStoryViewersCountListener(storyId: String, onUpdate: (Int) -> Unit): com.google.firebase.firestore.ListenerRegistration? {
        if (storyId.isEmpty()) return null
        return db.collection("stories").document(storyId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val viewerIds = (snapshot.get("viewers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                onUpdate(viewerIds.size)
            }
        }
    }

    fun getStoryLikesCountListener(storyId: String, onUpdate: (Int) -> Unit): com.google.firebase.firestore.ListenerRegistration? {
        if (storyId.isEmpty()) return null
        return db.collection("stories").document(storyId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val likesCount = (snapshot.getLong("likesCount")?.toInt() ?: 0).coerceAtLeast(0)
                onUpdate(likesCount)
            }
        }
    }

    suspend fun deleteStory(storyId: String) = withContext(Dispatchers.IO) {
        if (storyId.isEmpty()) return@withContext
        try {
            db.collection("stories").document(storyId).delete().await()
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Error deleting story $storyId", e)
            throw e
        }
    }

    suspend fun updateStory(storyId: String, updates: Map<String, Any>) = withContext(Dispatchers.IO) {
        if (storyId.isEmpty()) return@withContext
        try {
            db.collection("stories").document(storyId).set(updates, SetOptions.merge()).await()
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Error updating story $storyId", e)
            throw e
        }
    }

    fun listenUserHighlights(userId: String): Flow<List<StoryHighlight>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = db.collection("highlights")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("PostRepository", "Error listening to highlights", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        StoryHighlight(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            title = doc.getString("title") ?: "Highlight",
                            coverUrl = doc.getString("coverUrl") ?: "",
                            storyIds = doc.get("storyIds") as? List<String> ?: emptyList(),
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.createdAt } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun createHighlight(
        userId: String,
        title: String,
        coverUrl: String,
        storyIds: List<String>
    ): String = withContext(Dispatchers.IO) {
        val highlightId = UUID.randomUUID().toString()
        val data = hashMapOf(
            "id" to highlightId,
            "userId" to userId,
            "title" to title,
            "coverUrl" to coverUrl,
            "storyIds" to storyIds,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("highlights").document(highlightId).set(data).await()
        highlightId
    }

    suspend fun updateHighlight(
        highlightId: String,
        title: String,
        coverUrl: String,
        storyIds: List<String>
    ) = withContext(Dispatchers.IO) {
        val data = hashMapOf<String, Any>(
            "title" to title,
            "coverUrl" to coverUrl,
            "storyIds" to storyIds,
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("highlights").document(highlightId).set(data, SetOptions.merge()).await()
    }

    suspend fun deleteHighlight(highlightId: String) = withContext(Dispatchers.IO) {
        db.collection("highlights").document(highlightId).delete().await()
    }

    suspend fun addStoryToHighlight(highlightId: String, storyId: String) = withContext(Dispatchers.IO) {
        db.collection("highlights").document(highlightId)
            .update("storyIds", FieldValue.arrayUnion(storyId)).await()
    }

    suspend fun acceptCollaboration(postId: String) {
        val currentUserId = RepositoryManager.authRepository.getCurrentUserId()
        if (currentUserId.isEmpty()) return
        
        try {
            val postRef = db.collection("posts").document(postId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(postRef)
                if (snapshot.exists()) {
                    val pending = snapshot.get("pendingCollaboratorIds") as? List<String> ?: emptyList()
                    val accepted = snapshot.get("collaboratorIds") as? List<String> ?: emptyList()
                    
                    if (pending.contains(currentUserId)) {
                        val newPending = pending.toMutableList().apply { remove(currentUserId) }
                        val newAccepted = accepted.toMutableList().apply { if (!contains(currentUserId)) add(currentUserId) }
                        transaction.update(postRef, "pendingCollaboratorIds", newPending, "collaboratorIds", newAccepted)
                    }
                }
            }.await()
            
            // Notify the post owner
            val postSnapshot = postRef.get().await()
            val ownerId = postSnapshot.getString("userId")
            if (ownerId != null && ownerId != currentUserId) {
                // Get current user details
                val currentUserDoc = db.collection("users").document(currentUserId).get().await()
                val username = currentUserDoc.getString("username") ?: "User"
                val avatarUrl = currentUserDoc.getString("profilePicUrl") ?: currentUserDoc.getString("avatarUrl") ?: ""
                
                val notification = hashMapOf(
                    "type" to "collaboration_accepted",
                    "actorId" to currentUserId,
                    "actorUsername" to username,
                    "actorAvatar" to avatarUrl,
                    "postId" to postId,
                    "postThumbnail" to postSnapshot.getString("postUrl"),
                    "timestamp" to FieldValue.serverTimestamp(),
                    "isRead" to false
                )
                db.collection("users").document(ownerId).collection("notifications").add(notification).await()
            }
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Error accepting collaboration", e)
        }
    }

    suspend fun declineCollaboration(postId: String) {
        val currentUserId = RepositoryManager.authRepository.getCurrentUserId()
        if (currentUserId.isEmpty()) return
        
        try {
            val postRef = db.collection("posts").document(postId)
            var postOwnerId: String? = null
            var postThumbnail: String? = null

            db.runTransaction { transaction ->
                val snapshot = transaction.get(postRef)
                if (snapshot.exists()) {
                    postOwnerId = snapshot.getString("userId")
                    postThumbnail = snapshot.getString("postUrl")
                    val pending = snapshot.get("pendingCollaboratorIds") as? List<String> ?: emptyList()
                    
                    if (pending.contains(currentUserId)) {
                        val newPending = pending.toMutableList().apply { remove(currentUserId) }
                        transaction.update(postRef, "pendingCollaboratorIds", newPending)
                    }
                }
            }.await()

            // Notify post owner of decline
            val ownerId = postOwnerId
            if (!ownerId.isNullOrBlank() && ownerId != currentUserId) {
                val currentUserDoc = db.collection("users").document(currentUserId).get().await()
                val username = currentUserDoc.getString("username") ?: "User"
                val avatarUrl = currentUserDoc.getString("profilePicUrl") ?: currentUserDoc.getString("avatarUrl") ?: ""
                val notification = hashMapOf(
                    "type" to "collaboration_declined",
                    "actorId" to currentUserId,
                    "actorUsername" to username,
                    "actorAvatar" to avatarUrl,
                    "postId" to postId,
                    "postThumbnail" to postThumbnail,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "isRead" to false
                )
                db.collection("users").document(ownerId).collection("notifications").add(notification).await()
            }
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Error declining collaboration", e)
        }
    }

    suspend fun removeCollaborator(postId: String, collaboratorUserId: String) {
        val currentUserId = RepositoryManager.authRepository.getCurrentUserId()
        if (currentUserId.isEmpty() || postId.isEmpty() || collaboratorUserId.isEmpty()) return
        
        try {
            val postRef = db.collection("posts").document(postId)
            var postOwnerId = ""
            var postMediaUrl = ""
            
            db.runTransaction { transaction ->
                val snapshot = transaction.get(postRef)
                if (snapshot.exists()) {
                    postOwnerId = snapshot.getString("userId") ?: ""
                    postMediaUrl = snapshot.getString("postUrl") ?: ""
                    val pending = snapshot.get("pendingCollaboratorIds") as? List<String> ?: emptyList()
                    val accepted = snapshot.get("collaboratorIds") as? List<String> ?: emptyList()
                    
                    val newPending = pending.filter { it != collaboratorUserId }
                    val newAccepted = accepted.filter { it != collaboratorUserId }
                    transaction.update(postRef, "pendingCollaboratorIds", newPending, "collaboratorIds", newAccepted)
                }
            }.await()
            
            // If owner removed the collaborator, notify the collaborator
            if (currentUserId == postOwnerId && collaboratorUserId != currentUserId) {
                val ownerDoc = db.collection("users").document(currentUserId).get().await()
                val ownerUsername = ownerDoc.getString("username") ?: "Creator"
                val ownerAvatar = ownerDoc.getString("profilePicUrl") ?: ownerDoc.getString("avatarUrl") ?: ""
                val notification = hashMapOf(
                    "type" to "collaboration_removed",
                    "actorId" to currentUserId,
                    "actorUsername" to ownerUsername,
                    "actorAvatar" to ownerAvatar,
                    "postId" to postId,
                    "postThumbnail" to postMediaUrl,
                    "content" to "@$ownerUsername removed you as a collaborator.",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "isRead" to false
                )
                db.collection("users").document(collaboratorUserId).collection("notifications").add(notification).await()
            }
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Error removing collaborator", e)
        }
    }
}
