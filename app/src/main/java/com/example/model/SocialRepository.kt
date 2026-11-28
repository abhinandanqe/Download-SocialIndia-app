package com.example.model

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SocialRepository(context: Context) {

    suspend fun sendLiveGift(liveId: String, senderId: String, receiverId: String, giftId: String, giftName: String, coins: Int) {
        val transaction = GiftTransaction(
            id = firestore.collection("gifts").document().id,
            senderId = senderId,
            receiverId = receiverId,
            giftId = giftId,
            giftName = giftName,
            coins = coins,
            liveId = liveId,
            timestamp = System.currentTimeMillis()
        )
        
        firestore.runTransaction { tx ->
            val senderRef = firestore.collection("users").document(senderId)
            val receiverRef = firestore.collection("users").document(receiverId)
            
            val senderSnapshot = tx.get(senderRef)
            val receiverSnapshot = tx.get(receiverRef)
            
            val senderBalance = senderSnapshot.getDouble("walletBalance") ?: 0.0
            if (senderBalance < coins) {
                // If not enough balance, just give free coins for demo logic but prompt says "real backend, anti-spam".
                // Since this is real data, we throw.
                throw Exception("Insufficient balance")
            }
            
            val receiverEarnings = receiverSnapshot.getDouble("earnings") ?: 0.0
            val receiverWallet = receiverSnapshot.getDouble("walletBalance") ?: 0.0
            
            tx.update(senderRef, "walletBalance", senderBalance - coins)
            tx.update(receiverRef, "earnings", receiverEarnings + coins)
            tx.update(receiverRef, "walletBalance", receiverWallet + coins)
            
            val giftRef = firestore.collection("gifts").document(transaction.id)
            tx.set(giftRef, transaction)
        }.await()
    }

    fun observeLiveGifts(liveId: String): kotlinx.coroutines.flow.Flow<List<GiftTransaction>> = kotlinx.coroutines.flow.callbackFlow {
        val listener = firestore.collection("gifts")
            .whereEqualTo("liveId", liveId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val gifts = snapshot.documents.mapNotNull {
                        try { it.toObject(GiftTransaction::class.java)?.copy(id = it.id) } catch (_: Exception) { null }
                    }
                    trySend(gifts)
                }
            }
        awaitClose { listener.remove() }
    }


    fun getWalletTransactions(userId: String): kotlinx.coroutines.flow.Flow<List<GiftTransaction>> = kotlinx.coroutines.flow.callbackFlow {
        val listener = firestore.collection("gifts")
            .whereEqualTo("receiverId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val gifts = snapshot.documents.mapNotNull {
                        try { it.toObject(GiftTransaction::class.java)?.copy(id = it.id) } catch (_: Exception) { null }
                    }.sortedByDescending { it.timestamp }
                    trySend(gifts)
                }
            }
        awaitClose { listener.remove() }
    }
    
    suspend fun subscribeToCreator(subscriberId: String, creatorId: String, tier: String = "Basic") {
        val sub = CreatorSubscription(
            id = firestore.collection("subscriptions").document().id,
            subscriberId = subscriberId,
            creatorId = creatorId,
            tier = tier,
            expiresAt = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
        )
        firestore.collection("subscriptions").document(sub.id).set(sub).await()
    }
    
    fun getCreatorSubscriptions(creatorId: String): kotlinx.coroutines.flow.Flow<List<CreatorSubscription>> = kotlinx.coroutines.flow.callbackFlow {
        val listener = firestore.collection("subscriptions")
            .whereEqualTo("creatorId", creatorId)
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val subs = snapshot.documents.mapNotNull {
                        try { it.toObject(CreatorSubscription::class.java)?.copy(id = it.id) } catch (_: Exception) { null }
                    }
                    trySend(subs)
                }
            }
        awaitClose { listener.remove() }
    }
    
    private val subscribedCreatorsCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun isSubscribedToCreator(subscriberId: String, creatorId: String): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.callbackFlow {
        if (subscriberId.isEmpty() || creatorId.isEmpty()) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val cacheKey = "${subscriberId}_${creatorId}"
        subscribedCreatorsCache[cacheKey]?.let { trySend(it) }

        val listener = firestore.collection("subscriptions")
            .whereEqualTo("subscriberId", subscriberId)
            .whereEqualTo("creatorId", creatorId)
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, _ ->
                val isSubbed = snapshot != null && snapshot.documents.isNotEmpty()
                subscribedCreatorsCache[cacheKey] = isSubbed
                trySend(isSubbed)
            }
        awaitClose { listener.remove() }
    }

    fun isSubscribedCached(subscriberId: String, creatorId: String): Boolean {
        if (subscriberId.isEmpty() || creatorId.isEmpty()) return false
        return subscribedCreatorsCache["${subscriberId}_${creatorId}"] ?: false
    }
    
    suspend fun saveContentDraft(draft: ContentDraft) {
        val ref = if (draft.id.isEmpty()) firestore.collection("drafts").document() else firestore.collection("drafts").document(draft.id)
        val d = draft.copy(id = ref.id)
        ref.set(d).await()
    }
    
    fun getContentDrafts(userId: String): kotlinx.coroutines.flow.Flow<List<ContentDraft>> = kotlinx.coroutines.flow.callbackFlow {
        val listener = firestore.collection("drafts")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val drafts = snapshot.documents.mapNotNull {
                        try { it.toObject(ContentDraft::class.java)?.copy(id = it.id) } catch (_: Exception) { null }
                    }.sortedByDescending { it.timestamp }
                    trySend(drafts)
                }
            }
        awaitClose { listener.remove() }
    }
    
    suspend fun scheduleContent(content: ScheduledContent) {
        val ref = if (content.id.isEmpty()) firestore.collection("scheduled").document() else firestore.collection("scheduled").document(content.id)
        val s = content.copy(id = ref.id)
        ref.set(s).await()
    }
    
    fun getScheduledContent(userId: String): kotlinx.coroutines.flow.Flow<List<ScheduledContent>> = kotlinx.coroutines.flow.callbackFlow {
        val listener = firestore.collection("scheduled")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val contents = snapshot.documents.mapNotNull {
                        try { it.toObject(ScheduledContent::class.java)?.copy(id = it.id) } catch (_: Exception) { null }
                    }.sortedBy { it.scheduledTime }
                    trySend(contents)
                }
            }
        awaitClose { listener.remove() }
    }

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.socialDao()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun searchUsers(query: String): List<User> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = query.trim().lowercase()
        try {
            var snapshot = firestore.collection("users")
                .whereGreaterThanOrEqualTo("normalizedUsername", q)
                .whereLessThanOrEqualTo("normalizedUsername", q + "\uf8ff")
                .limit(20)
                .get()
                .await()

            if (snapshot.isEmpty) {
                // Fallback: search across users collection directly
                snapshot = firestore.collection("users")
                    .limit(50)
                    .get()
                    .await()
            }

            return@withContext snapshot.documents.mapNotNull { doc ->
                val isDeactivated = doc.getBoolean("isDeactivated") ?: false
                if (isDeactivated) return@mapNotNull null

                val username = doc.getString("username") ?: ""
                val norm = doc.getString("normalizedUsername") ?: username.lowercase()
                if (!norm.contains(q) && !username.lowercase().contains(q) && snapshot.size() > 20) {
                    return@mapNotNull null
                }

                User(
                    id = doc.id,
                    username = username,
                    avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: "",
                    isVerified = doc.getBoolean("isVerified") ?: false,
                    followerCount = doc.getLong("followerCount")?.toInt() ?: 0,
                    bio = doc.getString("bio") ?: ""
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getSuggestedUsers(limit: Long = 15): List<User> = withContext(Dispatchers.IO) {
        val recommendations = getModularRecommendations(limit)
        recommendations.map { it.user }
    }

    suspend fun getModularRecommendations(limit: Long = 20): List<SuggestedUser> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
            
            // 1. Fetch current user's blocked users
            val blockedIds = mutableSetOf<String>()
            if (currentUserId.isNotEmpty()) {
                try {
                    val blockedSnap = firestore.collection("users").document(currentUserId)
                        .collection("blockedUsers").limit(100).get().await()
                    blockedSnap.documents.forEach { blockedIds.add(it.id) }
                } catch (ignored: Exception) {}

                try {
                    val hiddenSnap = firestore.collection("users").document(currentUserId)
                        .collection("hiddenSuggestions").limit(100).get().await()
                    hiddenSnap.documents.forEach { 
                        RepositoryManager.dismissUserCached(it.id)
                    }
                } catch (ignored: Exception) {}
            }

            // 2. Fetch users from Firestore
            val snapshot = firestore.collection("users")
                .limit(limit + 15)
                .get()
                .await()

            val list = mutableListOf<SuggestedUser>()
            for (doc in snapshot.documents) {
                if (doc.id == currentUserId) continue
                if (blockedIds.contains(doc.id)) continue
                if (RepositoryManager.isUserDismissedCached(doc.id)) continue
                
                val isDeactivated = doc.getBoolean("isDeactivated") ?: false
                val isBanned = doc.getBoolean("isBanned") ?: false
                if (isDeactivated || isBanned) continue
                
                val username = doc.getString("username") ?: ""
                if (username.isEmpty()) continue

                // Check if already followed (from cache or Room)
                val isFollowing = if (currentUserId.isNotEmpty()) {
                    RepositoryManager.isFollowingCached(doc.id)
                } else false
                if (isFollowing) continue

                val followerCount = doc.getLong("followerCount")?.toInt() ?: 0
                val isVerified = doc.getBoolean("isVerified") ?: false
                val isPrivate = doc.getBoolean("isPrivate") ?: false
                val category = doc.getString("category") ?: ""
                val bio = doc.getString("bio") ?: ""
                val displayName = doc.getString("displayName") ?: doc.getString("name") ?: username

                // Contextual recommendation reason signal
                val reason = when {
                    isVerified -> "Verified Creator"
                    category.isNotEmpty() -> "Popular in $category"
                    followerCount > 10 -> "Popular Creator • ${followerCount} followers"
                    followerCount > 0 -> "Suggested for you"
                    else -> "New to Social India"
                }

                val user = User(
                    id = doc.id,
                    username = username,
                    displayName = displayName,
                    avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: doc.getString("avatar_url") ?: "",
                    isVerified = isVerified,
                    isPrivate = isPrivate,
                    followerCount = followerCount,
                    followingCount = doc.getLong("followingCount")?.toInt() ?: 0,
                    bio = bio,
                    category = category,
                    suggestionReason = reason
                )

                list.add(
                    SuggestedUser(
                        user = user,
                        reason = reason,
                        mutualFollowersCount = 0
                    )
                )

                if (list.size >= limit.toInt()) break
            }
            list
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error getting recommendations", e)
            emptyList()
        }
    }

    suspend fun dismissSuggestion(targetUserId: String) = withContext(Dispatchers.IO) {
        if (targetUserId.isEmpty()) return@withContext
        RepositoryManager.dismissUserCached(targetUserId)
        val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
        if (currentUserId.isNotEmpty()) {
            try {
                firestore.collection("users").document(currentUserId)
                    .collection("hiddenSuggestions").document(targetUserId)
                    .set(mapOf("dismissedAt" to FieldValue.serverTimestamp()))
            } catch (e: Exception) {
                Log.e("SocialRepository", "Error dismissing suggestion", e)
            }
        }
    }

    suspend fun blockUser(currentUserId: String, targetUserId: String) = withContext(Dispatchers.IO) {
        if (currentUserId.isEmpty() || targetUserId.isEmpty() || currentUserId == targetUserId) return@withContext
        try {
            // Unfollow in both directions
            unfollowUser(currentUserId, targetUserId)
            unfollowUser(targetUserId, currentUserId)
            
            // Mark in blocked collection
            firestore.collection("users").document(currentUserId)
                .collection("blockedUsers").document(targetUserId)
                .set(mapOf("blockedAt" to FieldValue.serverTimestamp())).await()
                
            dismissSuggestion(targetUserId)
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error blocking user", e)
        }
    }

    suspend fun reportUser(currentUserId: String, targetUserId: String, reason: String) = withContext(Dispatchers.IO) {
        if (targetUserId.isEmpty()) return@withContext
        try {
            val reportMap = hashMapOf(
                "reportedUserId" to targetUserId,
                "reporterId" to currentUserId,
                "reason" to reason,
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection("reports").add(reportMap).await()
            dismissSuggestion(targetUserId)
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error reporting user", e)
        }
    }

    suspend fun sendFollowRequest(followerId: String, followingId: String) = withContext(Dispatchers.IO) {
        if (followerId.isEmpty() || followingId.isEmpty() || followerId == followingId) return@withContext
        RepositoryManager.setFollowRequestedCached(followingId, true)
        try {
            val requestMap = hashMapOf(
                "requesterId" to followerId,
                "targetId" to followingId,
                "status" to "PENDING",
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(followingId)
                .collection("followRequests").document(followerId)
                .set(requestMap).await()

            // Notification for private follow request
            val followerDoc = firestore.collection("users").document(followerId).get().await()
            val actorUsername = followerDoc.getString("username") ?: "User"
            val actorAvatar = followerDoc.getString("profilePicUrl") ?: followerDoc.getString("avatarUrl") ?: ""
            val notificationId = java.util.UUID.randomUUID().toString()

            val notifMap = hashMapOf(
                "id" to notificationId,
                "recipientId" to followingId,
                "type" to "FOLLOW_REQUEST",
                "actorId" to followerId,
                "actorUsername" to actorUsername,
                "actorAvatar" to actorAvatar,
                "content" to "requested to follow you",
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(followingId).collection("notifications").document(notificationId).set(notifMap).await()
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error sending follow request", e)
            RepositoryManager.setFollowRequestedCached(followingId, false)
        }
    }

    suspend fun cancelFollowRequest(followerId: String, followingId: String) = withContext(Dispatchers.IO) {
        if (followerId.isEmpty() || followingId.isEmpty()) return@withContext
        RepositoryManager.setFollowRequestedCached(followingId, false)
        try {
            firestore.collection("users").document(followingId)
                .collection("followRequests").document(followerId)
                .delete().await()
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error canceling follow request", e)
        }
    }

    // --- FOLLOWS (Atomic Transactions with Room Persistence & Firestore Fallbacks) ---
    suspend fun followUser(followerId: String, followingId: String) = withContext(Dispatchers.IO) {
        if (followerId == followingId || followerId.isEmpty() || followingId.isEmpty()) return@withContext
        val mutationKey = "follow:${followerId}:${followingId}"
        if (!com.example.security.InputGuard.tryBeginMutation(mutationKey)) return@withContext
        try {
        val followId = "${followerId}_${followingId}"
        val followDocRef = firestore.collection("follows").document(followId)
        val followerRef = firestore.collection("users").document(followerId)
        val followingRef = firestore.collection("users").document(followingId)

        // 1. Save to local Room database immediately
        dao.insertFollow(FollowEntity(followerId, followingId))

        // 2. Optimistically update follow cache
        RepositoryManager.setFollowingCached(followingId, true)

        try {
            var isNewFollow = false
            firestore.runTransaction { transaction ->
                // All reads MUST execute before any writes in a Firestore transaction
                val followSnapshot = transaction.get(followDocRef)
                val followerDoc = transaction.get(followerRef)
                val followingDoc = transaction.get(followingRef)

                if (!followSnapshot.exists()) {
                    isNewFollow = true
                    val followData = hashMapOf(
                        "followerId" to followerId,
                        "followingId" to followingId,
                        "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                    transaction.set(followDocRef, followData)

                    val followerSubDoc = firestore.collection("users").document(followingId).collection("followers").document(followerId)
                    val followingSubDoc = firestore.collection("users").document(followerId).collection("following").document(followingId)
                    transaction.set(followerSubDoc, hashMapOf("followerId" to followerId, "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
                    transaction.set(followingSubDoc, hashMapOf("followingId" to followingId, "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())

                    val nextFollowing = (followerDoc.getLong("followingCount") ?: 0L).coerceAtLeast(0L) + 1L
                    val nextFollowers = (followingDoc.getLong("followerCount") ?: 0L).coerceAtLeast(0L) + 1L
                    transaction.set(followerRef, mapOf("followingCount" to nextFollowing), com.google.firebase.firestore.SetOptions.merge())
                    transaction.set(followingRef, mapOf("followerCount" to nextFollowers), com.google.firebase.firestore.SetOptions.merge())
                }
            }.await()

            if (isNewFollow) {
                // Send deterministic follow notification
                val followerDoc = followerRef.get().await()
                val actorUsername = if (followerDoc.exists()) followerDoc.getString("username") ?: "User" else "User"
                val actorAvatar = if (followerDoc.exists()) followerDoc.getString("profilePicUrl") ?: followerDoc.getString("avatarUrl") ?: "" else ""
                val notificationId = "follow_${followerId}_${followingId}"

                val notifMap = hashMapOf(
                    "id" to notificationId,
                    "recipientId" to followingId,
                    "type" to "FOLLOW",
                    "actorId" to followerId,
                    "actorUsername" to actorUsername,
                    "actorAvatar" to actorAvatar,
                    "content" to "started following you",
                    "userId" to followerId,
                    "isRead" to false,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                try {
                    firestore.collection("users").document(followingId).collection("notifications").document(notificationId).set(notifMap).await()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SocialRepository", "Follow write failed, rolling back optimistic state", e)
            dao.deleteFollow(FollowEntity(followerId, followingId))
            RepositoryManager.setFollowingCached(followingId, false)
            throw e
        }
        } finally {
            com.example.security.InputGuard.endMutation(mutationKey)
        }
    }

    suspend fun unfollowUser(followerId: String, followingId: String) = withContext(Dispatchers.IO) {
        if (followerId.isEmpty() || followingId.isEmpty()) return@withContext
        val mutationKey = "follow:${followerId}:${followingId}"
        if (!com.example.security.InputGuard.tryBeginMutation(mutationKey)) return@withContext
        try {
        val followId = "${followerId}_${followingId}"
        val followDocRef = firestore.collection("follows").document(followId)
        val followerRef = firestore.collection("users").document(followerId)
        val followingRef = firestore.collection("users").document(followingId)

        // 1. Delete from local Room database immediately
        dao.deleteFollow(FollowEntity(followerId, followingId))

        // 2. Optimistically update follow cache
        RepositoryManager.setFollowingCached(followingId, false)

        try {
            // Remove follow notification
            try {
                firestore.collection("users").document(followingId).collection("notifications").document("follow_${followerId}_${followingId}").delete()
            } catch (_: Exception) {}

            val followerSubDoc = firestore.collection("users").document(followingId).collection("followers").document(followerId)
            val followingSubDoc = firestore.collection("users").document(followerId).collection("following").document(followingId)

            firestore.runTransaction { transaction ->
                // All reads MUST execute before any writes in a Firestore transaction
                val followSnapshot = transaction.get(followDocRef)
                val followerDoc = transaction.get(followerRef)
                val followingDoc = transaction.get(followingRef)

                if (followSnapshot.exists()) {
                    transaction.delete(followDocRef)
                }

                transaction.delete(followerSubDoc)
                transaction.delete(followingSubDoc)

                // Update user counts safely
                val currentFollowing = (followerDoc.getLong("followingCount") ?: 0L).coerceAtLeast(0L)
                if (currentFollowing > 0L) {
                    transaction.set(
                        followerRef,
                        mapOf("followingCount" to (currentFollowing - 1L).coerceAtLeast(0L)),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }
                val currentFollowers = (followingDoc.getLong("followerCount") ?: 0L).coerceAtLeast(0L)
                if (currentFollowers > 0L) {
                    transaction.set(
                        followingRef,
                        mapOf("followerCount" to (currentFollowers - 1L).coerceAtLeast(0L)),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }
            }.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SocialRepository", "Unfollow delete failed, rolling back optimistic state", e)
            dao.insertFollow(FollowEntity(followerId, followingId))
            RepositoryManager.setFollowingCached(followingId, true)
            throw e
        }
        } finally {
            com.example.security.InputGuard.endMutation(mutationKey)
        }
    }
    
    fun isFollowing(followerId: String, followingId: String): Flow<Boolean> = callbackFlow {
        if (followerId.isEmpty() || followingId.isEmpty() || followerId == followingId) {
            trySend(false)
            close()
            return@callbackFlow
        }
        // Emit cached value immediately to avoid UI flickering
        val cached = RepositoryManager.isFollowingCached(followingId)
        trySend(cached)

        // Start local Room DB observation
        val localJob = CoroutineScope(Dispatchers.IO).launch {
            dao.isFollowing(followerId, followingId).collect { localIsFollowing ->
                RepositoryManager.setFollowingCached(followingId, localIsFollowing)
                trySend(localIsFollowing)
            }
        }

        // Direct document listener on "${followerId}_${followingId}" (No composite index required!)
        val followId = "${followerId}_${followingId}"
        val listener = firestore.collection("follows").document(followId)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val isFollowingUser = snapshot.exists()
                    RepositoryManager.setFollowingCached(followingId, isFollowingUser)
                    CoroutineScope(Dispatchers.IO).launch {
                        if (isFollowingUser) {
                            dao.insertFollow(FollowEntity(followerId, followingId))
                        } else {
                            dao.deleteFollow(FollowEntity(followerId, followingId))
                        }
                    }
                    trySend(isFollowingUser)
                } else if (error != null) {
                    Log.e("SocialRepository", "isFollowing firestore listener error: ${error.message}", error)
                }
            }
        awaitClose {
            localJob.cancel()
            listener.remove()
        }
    }

    fun observeUserStats(userId: String): Flow<Pair<Int, Int>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(0 to 0)
            close()
            return@callbackFlow
        }
        val listener = try {
            firestore.collection("users").document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null && snapshot.exists()) {
                        try {
                            val followers = (snapshot.get("followerCount") as? Number)?.toInt() ?: 0
                            val following = (snapshot.get("followingCount") as? Number)?.toInt() ?: 0
                            trySend(maxOf(followers, 0) to maxOf(following, 0))
                        } catch (_: Exception) {}
                    }
                }
        } catch (_: Exception) {
            null
        }
        awaitClose { listener?.remove() }
    }

    // --- LIKES ---
    // --- POST LIKES ---

    // --- MESSAGES / CHAT ---
    suspend fun sendMessage(
        senderId: String,
        receiverId: String,
        message: String,
        sharedVideoId: String? = null,
        mediaType: String? = null,
        mediaUrl: String? = null,
        thumbnailUrl: String? = null,
        videoDurationSeconds: Int = 0,
        replyToId: String? = null,
        replyToText: String? = null,
        customMessageId: String? = null,
        driveFileId: String? = null,
        stickerId: String? = null,
        packId: String? = null,
        isAnimated: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val username = try {
            firestore.collection("users").document(senderId).get().await().getString("username") ?: "User"
        } catch (e: Exception) {
            "User"
        }
        val msg = hashMapOf(
            "senderId" to senderId,
            "receiverId" to receiverId,
            "senderUsername" to username,
            "message" to message,
            "sharedVideoId" to sharedVideoId,
            "mediaType" to mediaType,
            "mediaUrl" to mediaUrl,
            "thumbnailUrl" to thumbnailUrl,
            "videoDurationSeconds" to videoDurationSeconds,
            "replyToId" to replyToId,
            "replyToText" to replyToText,
            "driveFileId" to driveFileId,
            "stickerId" to stickerId,
            "packId" to packId,
            "isAnimated" to isAnimated,
            "status" to "sent",
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        val finalMsgId = if (!customMessageId.isNullOrEmpty()) {
            firestore.collection("chats").document(customMessageId).set(msg).await()
            customMessageId
        } else {
            val docRef = firestore.collection("chats").add(msg).await()
            docRef.id
        }

        // Register in MessengerSoundManager so this sent message never produces an incoming echo sound
        com.example.utils.MessengerSoundManager.markLocallySent(finalMsgId)

        // Generate real-time media-aware notification for recipient
        if (senderId != receiverId) {
            try {
                val notifType = when (mediaType?.lowercase()) {
                    "image" -> "chat_photo"
                    "video" -> "chat_video"
                    "voice" -> "chat_voice"
                    "sticker" -> "chat_sticker"
                    "gif" -> "chat_gif"
                    else -> "chat_message"
                }
                val notifActionText = when (mediaType?.lowercase()) {
                    "image" -> "sent you a photo 📸"
                    "video" -> "sent you a video 🎥"
                    "voice" -> "sent a voice message 🎙️"
                    "sticker" -> "sent a sticker 🎨"
                    "gif" -> "sent a GIF 🎭"
                    else -> message.ifEmpty { "sent you a message" }
                }
                val senderDoc = firestore.collection("users").document(senderId).get().await()
                val senderAvatar = senderDoc.getString("profilePicUrl") ?: senderDoc.getString("avatarUrl") ?: ""

                val notif = hashMapOf(
                    "type" to notifType,
                    "actorId" to senderId,
                    "senderId" to senderId,
                    "actorUsername" to username,
                    "senderName" to username,
                    "actorAvatar" to senderAvatar,
                    "senderProfilePic" to senderAvatar,
                    "message" to notifActionText,
                    "content" to notifActionText,
                    "mediaThumbnail" to (thumbnailUrl ?: if (mediaType == "image") mediaUrl else null),
                    "postThumbnail" to (thumbnailUrl ?: if (mediaType == "image") mediaUrl else null),
                    "chatUserId" to senderId,
                    "mediaType" to mediaType,
                    "isRead" to false,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                firestore.collection("users").document(receiverId).collection("notifications").add(notif)
            } catch (e: Exception) {
                android.util.Log.w("SocialRepository", "Failed to create chat notification: ${e.message}")
            }
        }
        finalMsgId
    }

    suspend fun editChatMessage(messageId: String, newText: String) = withContext(Dispatchers.IO) {
        try {
            firestore.collection("chats").document(messageId).update(
                mapOf(
                    "message" to newText,
                    "isEdited" to true
                )
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteChatMessage(messageId: String, deleteForEveryone: Boolean = true, currentUserId: String = "") = withContext(Dispatchers.IO) {
        try {
            if (deleteForEveryone) {
                firestore.collection("chats").document(messageId).update(
                    mapOf(
                        "isDeleted" to true,
                        "message" to "🚫 This message was deleted",
                        "mediaUrl" to null,
                        "thumbnailUrl" to null
                    )
                ).await()
            } else if (currentUserId.isNotEmpty()) {
                firestore.collection("chats").document(messageId).update(
                    "deletedFor", com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId)
                ).await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun markMessagesAsRead(currentUserId: String, otherUserId: String, messages: List<com.example.model.MessageEntity>) = withContext(Dispatchers.IO) {
        val unread = messages.filter { it.receiverId == currentUserId && it.status != "read" }
        for (m in unread) {
            try {
                firestore.collection("chats").document(m.id).update("status", "read")
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    suspend fun reactToChatMessage(messageId: String, reaction: String?) = withContext(Dispatchers.IO) {
        try {
            firestore.collection("chats").document(messageId).update("reaction", reaction).await()
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error reacting to message", e)
        }
    }

    fun getChatMessages(u1: String, u2: String): Flow<List<com.example.model.MessageEntity>> = callbackFlow {
        // We fetch messages and filter/sort in memory to avoid needing complex Firestore composite indexes
        val listener = firestore.collection("chats")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        val sId = doc.getString("senderId") ?: ""
                        val rId = doc.getString("receiverId") ?: ""
                        val deletedFor = doc.get("deletedFor") as? List<*>
                        if (deletedFor?.contains(u1) == true) return@mapNotNull null
                        if ((sId == u1 && rId == u2) || (sId == u2 && rId == u1)) {
                            MessageEntity(
                                id = doc.id,
                                senderId = sId,
                                receiverId = rId,
                                message = doc.getString("message") ?: "",
                                sharedVideoId = doc.getString("sharedVideoId"),
                                mediaType = doc.getString("mediaType"),
                                mediaUrl = doc.getString("mediaUrl"),
                                thumbnailUrl = doc.getString("thumbnailUrl"),
                                videoDurationSeconds = (doc.get("videoDurationSeconds") as? Number)?.toInt() ?: 0,
                                replyToId = doc.getString("replyToId"),
                                replyToText = doc.getString("replyToText"),
                                reaction = doc.getString("reaction"),
                                status = doc.getString("status") ?: "sent",
                                isEdited = doc.getBoolean("isEdited") ?: false,
                                isDeleted = doc.getBoolean("isDeleted") ?: false,
                                timestamp = try {
                                    val raw = doc.get("timestamp")
                                    when (raw) {
                                        is com.google.firebase.Timestamp -> raw.seconds * 1000
                                        is java.util.Date -> raw.time
                                        is Long -> raw
                                        is Double -> raw.toLong()
                                        is Number -> raw.toLong()
                                        else -> 0L
                                    }
                                } catch (e: Exception) {
                                    0L
                                }
                            )
                        } else null
                    }.sortedBy { it.timestamp }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    fun getActiveChatUserIds(userId: String): Flow<Set<String>> = callbackFlow {
        val listener = firestore.collection("chats")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val ids = mutableSetOf<String>()
                    snapshot.documents.forEach { doc ->
                        val sId = doc.getString("senderId") ?: ""
                        val rId = doc.getString("receiverId") ?: ""
                        if (sId == userId && rId.isNotEmpty()) ids.add(rId)
                        if (rId == userId && sId.isNotEmpty()) ids.add(sId)
                    }
                    trySend(ids)
                } else {
                    trySend(emptySet())
                }
            }
        awaitClose { listener.remove() }
    }

    fun getFollowingIds(userId: String): Flow<Set<String>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("follows")
            .whereEqualTo("followerId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val ids = snapshot.documents.mapNotNull { it.getString("followingId") }.toSet()
                    trySend(ids)
                } else {
                    trySend(emptySet())
                }
            }
        awaitClose { listener.remove() }
    }

    fun getFollowerIds(userId: String): Flow<Set<String>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("follows")
            .whereEqualTo("followingId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val ids = snapshot.documents.mapNotNull { it.getString("followerId") }.toSet()
                    trySend(ids)
                } else {
                    trySend(emptySet())
                }
            }
        awaitClose { listener.remove() }
    }

    fun getFollowers(userId: String): Flow<List<User>> = callbackFlow {
        val listener = firestore.collection("follows")
            .whereEqualTo("followingId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val followerIds = snapshot.documents.mapNotNull { it.getString("followerId") }
                    if (followerIds.isEmpty()) {
                        trySend(emptyList())
                    } else {
                        CoroutineScope(Dispatchers.IO).launch {
                            val followers = followerIds.mapNotNull { id ->
                                try {
                                    val doc = firestore.collection("users").document(id).get().await()
                                    if (doc.exists()) {
                                        User(
                                            id = doc.id,
                                            username = doc.getString("username") ?: "User",
                                            avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: "",
                                            isVerified = doc.getBoolean("isVerified") ?: false,
                                            bio = doc.getString("bio") ?: ""
                                        )
                                    } else {
                                        User(id = id, username = "User_${id.take(5)}", avatarUrl = "")
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            trySend(followers)
                        }
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    fun getFollowing(userId: String): Flow<List<User>> = callbackFlow {
        val listener = firestore.collection("follows")
            .whereEqualTo("followerId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val followingIds = snapshot.documents.mapNotNull { it.getString("followingId") }
                    if (followingIds.isEmpty()) {
                        trySend(emptyList())
                    } else {
                        CoroutineScope(Dispatchers.IO).launch {
                            val following = followingIds.mapNotNull { id ->
                                try {
                                    val doc = firestore.collection("users").document(id).get().await()
                                    if (doc.exists()) {
                                        User(
                                            id = doc.id,
                                            username = doc.getString("username") ?: "User",
                                            avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: "",
                                            isVerified = doc.getBoolean("isVerified") ?: false,
                                            bio = doc.getString("bio") ?: ""
                                        )
                                    } else {
                                        User(id = id, username = "User_${id.take(5)}", avatarUrl = "")
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            trySend(following)
                        }
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    fun getTotalLikesCount(userId: String): Flow<Int> = callbackFlow {
        // This is expensive in Firestore if not denormalized. 
        // For this app, we'll listen to posts by this user and sum their likes.
        val listener = firestore.collection("posts")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val totalLikes = snapshot.documents.sumOf { it.getLong("likesCount")?.toInt() ?: 0 }
                    trySend(totalLikes)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun toggleSave(userId: String, videoId: String): Boolean = withContext(Dispatchers.IO) {
        if (userId.isEmpty() || videoId.isEmpty()) return@withContext false
        val previousSaved = RepositoryManager.isPostSavedCached(videoId)
        RepositoryManager.setPostSavedCached(videoId, !previousSaved)
        try {
            val userRef = firestore.collection("users").document(userId)
            val savedRef = userRef.collection("savedPosts").document(videoId)
            val postRef = firestore.collection("posts").document(videoId)
            
            var isSaved = false
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(savedRef)
                if (snapshot.exists()) {
                    transaction.delete(savedRef)
                    transaction.update(postRef, "savesCount", com.google.firebase.firestore.FieldValue.increment(-1))
                    isSaved = false
                } else {
                    transaction.set(savedRef, mapOf("timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                    transaction.update(postRef, "savesCount", com.google.firebase.firestore.FieldValue.increment(1))
                    isSaved = true
                }
            }.await()
            RepositoryManager.setPostSavedCached(videoId, isSaved)
            return@withContext isSaved
        } catch (e: Exception) {
            RepositoryManager.setPostSavedCached(videoId, previousSaved)
            e.printStackTrace()
            throw e
        }
    }

    suspend fun setUserStatus(userId: String, isOnline: Boolean) = withContext(Dispatchers.IO) {
        if (userId.isNotEmpty()) {
            try {
                firestore.collection("users").document(userId)
                    .update(
                        mapOf(
                            "status" to (if (isOnline) "online" else "offline"),
                            "last_seen" to System.currentTimeMillis()
                        )
                    ).await()
            } catch (e: Exception) {
                try {
                    firestore.collection("users").document(userId)
                        .set(
                            mapOf(
                                "status" to (if (isOnline) "online" else "offline"),
                                "last_seen" to System.currentTimeMillis()
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        ).await()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }

    suspend fun getUser(userId: String): User? = withContext(Dispatchers.IO) {
        if (userId.isEmpty()) return@withContext null
        try {
            val doc = firestore.collection("users").document(userId).get().await()
            if (doc.exists()) {
                User(
                    id = doc.id,
                    username = doc.getString("username") ?: "User",
                    avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: "",
                    isVerified = doc.getBoolean("isVerified") ?: false
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun observeUserStatus(userId: String): Flow<String> = callbackFlow {
        if (userId.isEmpty()) {
            trySend("offline")
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && snapshot.exists()) {
                    val isDeactivated = snapshot.getBoolean("isDeactivated") ?: false
                    val showActivityStatus = snapshot.getBoolean("showActivityStatus") ?: true
                    if (isDeactivated || !showActivityStatus) {
                        trySend("offline")
                    } else {
                        val status = snapshot.getString("status") ?: "offline"
                        trySend(status)
                    }
                } else {
                    trySend("offline")
                }
            }
        awaitClose { listener.remove() }
    }

    fun getVideoCount(userId: String): Flow<Int> = callbackFlow {
        val listener = firestore.collection("posts")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    trySend(snapshot.size())
                }
            }
        awaitClose { listener.remove() }
    }


    // --- COMMENTS ---
    suspend fun addComment(
        videoId: String,
        userId: String,
        content: String,
        gifUrl: String? = null,
        parentCommentId: String? = null,
        parentUsername: String? = null,
        mentions: List<com.example.model.Mention> = emptyList(),
        stickerId: String? = null,
        packId: String? = null,
        driveFileId: String? = null,
        thumbnailDriveFileId: String? = null,
        isAnimated: Boolean = false,
        audioUrl: String? = null,
        audioDurationSec: Int = 0
    ) = withContext(Dispatchers.IO) {
        if (videoId.isBlank() || userId.isBlank()) return@withContext
        val safeContent = com.example.security.InputGuard.sanitizeComment(content)
        val hasAttachment = !gifUrl.isNullOrBlank() || !stickerId.isNullOrBlank() || !audioUrl.isNullOrBlank()
        if (safeContent.isEmpty() && !hasAttachment) return@withContext
        if (!com.example.security.InputGuard.allowAction("comment:${videoId}:${userId}", 750L)) return@withContext

        var username = "user_${userId.take(5)}"
        var avatarUrl = ""
        var isVerified = false
        var verificationBadgeUrl = ""
        try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            username = userDoc.getString("username") ?: username
            avatarUrl = userDoc.getString("avatarUrl") ?: userDoc.getString("avatar_url") ?: userDoc.getString("profilePicUrl") ?: avatarUrl
            val vStatus = userDoc.getString("verificationStatus") ?: ""
            val vSuspended = userDoc.getBoolean("verificationSuspended") ?: false
            isVerified = !vSuspended && (
                (userDoc.getBoolean("isVerified") ?: false) ||
                vStatus == "approved" ||
                vStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE
            )
            verificationBadgeUrl = userDoc.getString("verificationBadgeUrl") ?: ""
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {}

        val commentId = java.util.UUID.randomUUID().toString()
        val commentType = when {
            !audioUrl.isNullOrEmpty() -> "AUDIO"
            !stickerId.isNullOrEmpty() -> "STICKER"
            !gifUrl.isNullOrEmpty() -> "GIF"
            else -> "TEXT"
        }
        val comment = hashMapOf(
            "id" to commentId,
            "videoId" to videoId,
            "userId" to userId,
            "username" to username,
            "avatarUrl" to avatarUrl,
            "isVerified" to isVerified,
            "verificationBadgeUrl" to verificationBadgeUrl,
            "text" to safeContent,
            "gifUrl" to gifUrl,
            "audioUrl" to audioUrl,
            "audioDurationSec" to audioDurationSec,
            "type" to commentType,
            "stickerId" to stickerId,
            "packId" to packId,
            "driveFileId" to driveFileId,
            "thumbnailDriveFileId" to thumbnailDriveFileId,
            "isAnimated" to isAnimated,
            "parentCommentId" to parentCommentId,
            "parentUsername" to parentUsername,
            "likedBy" to emptyList<String>(),
            "isPinned" to false,
            "isEdited" to false,
            "mentions" to mentions.map { mapOf("uid" to it.uid, "username" to it.username, "start" to it.start, "end" to it.end) },
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        try {
            val postRef = firestore.collection("posts").document(videoId)
            val commentRef = postRef.collection("comments").document(commentId)
            val batch = firestore.batch()
            batch.set(commentRef, comment)
            batch.update(postRef, "commentsCount", com.google.firebase.firestore.FieldValue.increment(1))
            batch.commit().await()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            android.util.Log.e("SocialRepository", "Failed to add comment", e)
            throw e
        }

        var postVideoUrl = ""
        var postOwnerId = ""
        try {
            val postDoc = firestore.collection("posts").document(videoId).get().await()
            postOwnerId = postDoc.getString("userId") ?: ""
            postVideoUrl = postDoc.getString("videoUrl") ?: ""
        } catch (e: Exception) {}

        // 1. Create Comment Notification for Post Owner
        if (postOwnerId.isNotEmpty() && postOwnerId != userId) {
            val notificationId = "comment_${videoId}_${commentId}"
            val notifMap = hashMapOf(
                "id" to notificationId,
                "recipientId" to postOwnerId,
                "type" to "comment",
                "actorId" to userId,
                "actorUsername" to username,
                "actorAvatar" to avatarUrl,
                "content" to "commented on your video: \"${content.take(60)}\"",
                "postId" to videoId,
                "commentId" to commentId,
                "contentType" to "comment",
                "postThumbnail" to postVideoUrl,
                "isRead" to false,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            try {
                firestore.collection("users").document(postOwnerId).collection("notifications").document(notificationId).set(notifMap).await()
            } catch(inner: Exception) {
                firestore.collection("notifications").document(notificationId).set(notifMap).await()
            }
        }

        // 2. Create Reply Notification if this is a reply to someone
        if (!parentCommentId.isNullOrEmpty()) {
            try {
                val parentDoc = firestore.collection("posts").document(videoId).collection("comments").document(parentCommentId).get().await()
                val parentAuthorId = parentDoc.getString("userId") ?: ""
                if (parentAuthorId.isNotEmpty() && parentAuthorId != userId) {
                    val replyNotifId = "reply_${videoId}_${commentId}"
                    val replyNotifMap = hashMapOf(
                        "id" to replyNotifId,
                        "recipientId" to parentAuthorId,
                        "type" to "reply",
                        "actorId" to userId,
                        "actorUsername" to username,
                        "actorAvatar" to avatarUrl,
                        "content" to "replied to your comment: \"${content.take(60)}\"",
                        "postId" to videoId,
                        "commentId" to commentId,
                        "contentType" to "comment",
                        "postThumbnail" to postVideoUrl,
                        "isRead" to false,
                        "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                    try {
                        firestore.collection("users").document(parentAuthorId).collection("notifications").document(replyNotifId).set(replyNotifMap).await()
                    } catch (e: Exception) {
                        firestore.collection("notifications").document(replyNotifId).set(replyNotifMap).await()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("SocialRepository", "Failed to write reply notification", e)
            }
        }

        // 3. Dispatch Mention Notifications for structured mentions (or regex fallback)
        try {
            val mentionTargets = mutableListOf<com.example.model.Mention>()
            if (mentions.isNotEmpty()) {
                mentionTargets.addAll(mentions)
            } else {
                val mentionRegex = Regex("@([A-Za-z0-9_.]+)")
                val foundUsernames = mentionRegex.findAll(content).map { it.groupValues[1] }.toSet()
                for (mUser in foundUsernames) {
                    if (mUser.equals(username, ignoreCase = true)) continue
                    try {
                        val userQuery = firestore.collection("users").whereEqualTo("username", mUser).limit(1).get().await()
                        val targetUserDoc = userQuery.documents.firstOrNull()
                        if (targetUserDoc != null && targetUserDoc.id != userId) {
                            mentionTargets.add(com.example.model.Mention(uid = targetUserDoc.id, username = mUser))
                        }
                    } catch (_: Exception) {}
                }
            }

            com.example.utils.MentionHelper.dispatchMentionNotifications(
                actorUid = userId,
                actorUsername = username,
                actorAvatar = avatarUrl,
                mentions = mentionTargets,
                contentId = videoId,
                contentType = "comment",
                previewText = content,
                mediaUrl = postVideoUrl
            )
        } catch (e: Exception) {
            android.util.Log.w("SocialRepository", "Failed to dispatch mention notifications", e)
        }
    }

    suspend fun deleteComment(videoId: String, commentId: String) = withContext(Dispatchers.IO) {
        if (videoId.isEmpty() || commentId.isEmpty()) return@withContext
        try {
            val commentsCol = firestore.collection("posts").document(videoId).collection("comments")
            val targetDoc = commentsCol.document(commentId)
            val repliesQuery = commentsCol.whereEqualTo("parentCommentId", commentId).get().await()
            val totalToDelete = 1 + repliesQuery.size()
            for (doc in repliesQuery.documents) {
                doc.reference.delete()
            }
            targetDoc.delete().await()
            firestore.collection("posts").document(videoId)
                .update("commentsCount", com.google.firebase.firestore.FieldValue.increment(-totalToDelete.toLong()))
        } catch (e: Exception) {
            android.util.Log.e("SocialRepository", "Failed to delete comment", e)
        }
    }

    suspend fun editComment(videoId: String, commentId: String, newContent: String) = withContext(Dispatchers.IO) {
        if (videoId.isEmpty() || commentId.isEmpty()) return@withContext
        try {
            firestore.collection("posts").document(videoId).collection("comments").document(commentId)
                .update(mapOf("text" to newContent.trim(), "isEdited" to true))
                .await()
        } catch (e: Exception) {
            android.util.Log.e("SocialRepository", "Failed to edit comment", e)
        }
    }

    suspend fun togglePinComment(videoId: String, commentId: String, currentPinned: Boolean) = withContext(Dispatchers.IO) {
        if (videoId.isEmpty() || commentId.isEmpty()) return@withContext
        try {
            val commentsCol = firestore.collection("posts").document(videoId).collection("comments")
            if (!currentPinned) {
                val pinnedDocs = commentsCol.whereEqualTo("isPinned", true).get().await()
                for (doc in pinnedDocs.documents) {
                    doc.reference.update("isPinned", false)
                }
            }
            commentsCol.document(commentId).update("isPinned", !currentPinned).await()
        } catch (e: Exception) {
            android.util.Log.e("SocialRepository", "Failed to toggle pin comment", e)
        }
    }

    suspend fun setCommentTypingStatus(videoId: String, userId: String, username: String, isTyping: Boolean) = withContext(Dispatchers.IO) {
        if (videoId.isEmpty() || userId.isEmpty()) return@withContext
        try {
            val typingDoc = firestore.collection("posts").document(videoId).collection("comment_typing").document(userId)
            if (isTyping) {
                typingDoc.set(mapOf(
                    "userId" to userId,
                    "username" to username,
                    "isTyping" to true,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ))
            } else {
                typingDoc.delete()
            }
        } catch (e: Exception) {}
    }

    fun getCommentTypingUsers(videoId: String, currentUserId: String): Flow<List<String>> = kotlinx.coroutines.flow.callbackFlow {
        if (videoId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("posts").document(videoId).collection("comment_typing")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val activeTypers = snapshot.documents.mapNotNull { doc ->
                        val uid = doc.getString("userId") ?: doc.id
                        val uname = doc.getString("username") ?: "Someone"
                        val isTyping = doc.getBoolean("isTyping") ?: false
                        val ts = doc.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()
                        if (uid != currentUserId && isTyping && (System.currentTimeMillis() - ts < 8000L)) {
                            uname
                        } else null
                    }
                    trySend(activeTypers)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun setDirectChatTypingStatus(senderId: String, receiverId: String, isTyping: Boolean) = withContext(Dispatchers.IO) {
        if (senderId.isEmpty() || receiverId.isEmpty()) return@withContext
        try {
            val chatId = listOf(senderId, receiverId).sorted().joinToString("_")
            val typingDoc = firestore.collection("chats").document(chatId).collection("typing").document(senderId)
            if (isTyping) {
                typingDoc.set(mapOf(
                    "userId" to senderId,
                    "isTyping" to true,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ))
            } else {
                typingDoc.delete()
            }
        } catch (e: Exception) {}
    }

    fun getDirectChatTypingStatus(senderId: String, receiverId: String): Flow<Boolean> = kotlinx.coroutines.flow.callbackFlow {
        if (senderId.isEmpty() || receiverId.isEmpty()) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val chatId = listOf(senderId, receiverId).sorted().joinToString("_")
        val listener = firestore.collection("chats").document(chatId).collection("typing").document(receiverId)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && snapshot.exists()) {
                    val isTyping = snapshot.getBoolean("isTyping") ?: false
                    val ts = snapshot.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()
                    val active = isTyping && (System.currentTimeMillis() - ts < 8000L)
                    trySend(active)
                } else {
                    trySend(false)
                }
            }
        awaitClose { listener.remove() }
    }

    fun searchUsersForMention(query: String): Flow<List<User>> = kotlinx.coroutines.flow.flow {
        val cleanQuery = query.removePrefix("@").trim().lowercase()
        val currentUserId = RepositoryManager.getCurrentUserId()
        try {
            val snapshot = if (cleanQuery.isEmpty()) {
                firestore.collection("users").limit(15).get().await()
            } else {
                firestore.collection("users")
                    .whereGreaterThanOrEqualTo("username", cleanQuery)
                    .whereLessThanOrEqualTo("username", cleanQuery + "\uf8ff")
                    .limit(15)
                    .get()
                    .await()
            }

            var docs = snapshot.documents.toMutableList()
            if (docs.isEmpty() && cleanQuery.isNotEmpty()) {
                val fallbackSnapshot = firestore.collection("users").limit(25).get().await()
                docs = fallbackSnapshot.documents.filter { doc ->
                    val u = doc.getString("username")?.lowercase() ?: ""
                    val n = doc.getString("displayName")?.lowercase() ?: ""
                    u.contains(cleanQuery) || n.contains(cleanQuery)
                }.toMutableList()
            }

            val blockedUids = mutableSetOf<String>()
            if (currentUserId.isNotEmpty()) {
                try {
                    val blockedDocs = firestore.collection("users").document(currentUserId).collection("blockedUsers").get().await()
                    blockedUids.addAll(blockedDocs.documents.map { it.id })
                } catch (_: Exception) {}
            }

            val users = docs.mapNotNull { doc ->
                if (doc.id == currentUserId) return@mapNotNull null
                if (blockedUids.contains(doc.id)) return@mapNotNull null
                if (doc.getBoolean("isDeactivated") == true || doc.getBoolean("isDeleted") == true) return@mapNotNull null
                val whoCanMention = doc.getString("whoCanMention") ?: "everyone"
                if (whoCanMention.equals("nobody", ignoreCase = true)) return@mapNotNull null

                val uname = doc.getString("username") ?: ""
                if (uname.isBlank()) return@mapNotNull null

                User(
                    id = doc.id,
                    username = uname,
                    avatarUrl = doc.getString("avatar_url") ?: doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: "",
                    isVerified = doc.getBoolean("isVerified") ?: false,
                    bio = doc.getString("bio") ?: ""
                )
            }.take(8)
            emit(users)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    suspend fun toggleCommentLikeFirebase(videoId: String, commentId: String, userId: String) = withContext(Dispatchers.IO) {
        if (videoId.isEmpty() || commentId.isEmpty() || userId.isEmpty()) return@withContext
        val commentRef = firestore.collection("posts").document(videoId).collection("comments").document(commentId)
        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(commentRef)
                if (snapshot.exists()) {
                    val likedBy = snapshot.get("likedBy") as? List<String> ?: emptyList()
                    val newLikedBy = if (likedBy.contains(userId)) {
                        likedBy.toMutableList().apply { remove(userId) }
                    } else {
                        likedBy.toMutableList().apply { add(userId) }
                    }
                    transaction.update(commentRef, "likedBy", newLikedBy)
                }
                null
            }.await()
        } catch (e: Exception) {
            Log.e("SocialRepository", "Failed to toggle comment like", e)
        }
    }

    suspend fun toggleVideoLike(videoId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        if (userId.isEmpty() || videoId.isEmpty()) return@withContext false
        val mutationKey = "like:${videoId}:${userId}"
        if (!com.example.security.InputGuard.tryBeginMutation(mutationKey)) {
            return@withContext RepositoryManager.isPostLikedCached(videoId)
        }
        val postRef = firestore.collection("posts").document(videoId)
        val likeDocRef = postRef.collection("likes").document(userId)
        val userRef = firestore.collection("users").document(userId)

        try {
            firestore.runTransaction { transaction ->
                val likeDoc = transaction.get(likeDocRef)
                val postDoc = transaction.get(postRef)
                val userDoc = transaction.get(userRef)

                val exists = likeDoc.exists()
                val currentLikes = (postDoc.getLong("likesCount") ?: 0L).coerceAtLeast(0L)
                if (exists) {
                    transaction.delete(likeDocRef)
                    if (postDoc.exists()) {
                        transaction.update(postRef, "likesCount", (currentLikes - 1L).coerceAtLeast(0L))
                        val postOwnerId = postDoc.getString("userId") ?: ""
                        if (postOwnerId.isNotEmpty()) {
                            val notifRef = firestore.collection("users").document(postOwnerId).collection("notifications").document("like_${videoId}_${userId}")
                            transaction.delete(notifRef)
                        }
                    }
                    RepositoryManager.setPostLikedCached(videoId, false)
                    false
                } else {
                    transaction.set(likeDocRef, mapOf("timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                    if (postDoc.exists()) {
                        transaction.update(postRef, "likesCount", currentLikes + 1L)
                    } else {
                        transaction.set(postRef, mapOf("likesCount" to 1L), com.google.firebase.firestore.SetOptions.merge())
                    }
                    RepositoryManager.setPostLikedCached(videoId, true)

                    // Create Like Notification safely within transaction writes
                    val username = if (userDoc.exists()) userDoc.getString("username") ?: "user_${userId.take(5)}" else "user_${userId.take(5)}"
                    val avatarUrl = if (userDoc.exists()) {
                        userDoc.getString("avatar_url") ?: userDoc.getString("profilePicUrl") ?: userDoc.getString("avatarUrl") ?: ""
                    } else {
                        ""
                    }
                    
                    val postOwnerId = if (postDoc.exists()) postDoc.getString("userId") ?: "" else ""
                    if (postOwnerId.isNotEmpty() && postOwnerId != userId) {
                        val notificationId = "like_${videoId}_${userId}"
                        val notifRef = firestore.collection("users").document(postOwnerId).collection("notifications").document(notificationId)
                        val notifMap = hashMapOf(
                            "id" to notificationId,
                            "recipientId" to postOwnerId,
                            "type" to "like",
                            "actorId" to userId,
                            "actorUsername" to username,
                            "actorAvatar" to avatarUrl,
                            "content" to "liked your video",
                            "postId" to videoId,
                            "postThumbnail" to (postDoc.getString("videoUrl") ?: ""),
                            "isRead" to false,
                            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        transaction.set(notifRef, notifMap)
                    }
                    true
                }
            }.await()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error in toggleVideoLike transaction", e)
            throw e
        } finally {
            com.example.security.InputGuard.endMutation(mutationKey)
        }
    }

    fun isVideoLiked(videoId: String, userId: String): Flow<Boolean> = kotlinx.coroutines.flow.callbackFlow {
        if (videoId.isEmpty() || userId.isEmpty()) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("posts").document(videoId).collection("likes").document(userId)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.exists() ?: false)
            }
        awaitClose { listener.remove() }
    }

    private val commentAuthorVerifyCache = java.util.concurrent.ConcurrentHashMap<String, Triple<Boolean, String, Long>>()

    fun getComments(videoId: String): Flow<List<com.example.model.Comment>> = kotlinx.coroutines.flow.callbackFlow {
        if (videoId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("posts").document(videoId).collection("comments")
            .limit(150)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val nowMs = System.currentTimeMillis()
                    val rawList = snapshot.documents.mapNotNull { doc ->
                        val c = doc.toObject(com.example.model.Comment::class.java)
                        if (c != null) {
                            val cached = commentAuthorVerifyCache[c.userId]
                            val docVerified = doc.getBoolean("isVerified") ?: doc.getBoolean("verified") ?: c.isVerified
                            val docBadge = doc.getString("verificationBadgeUrl") ?: c.verificationBadgeUrl
                            c.copy(
                                id = doc.id,
                                isVerified = cached?.first ?: docVerified,
                                verificationBadgeUrl = cached?.second?.ifBlank { docBadge } ?: docBadge
                            )
                        } else null
                    }
                    val sorted = rawList.sortedWith(
                        compareByDescending<com.example.model.Comment> { it.isPinned }
                            .thenBy { it.timestamp ?: java.util.Date(0) }
                    )
                    trySend(sorted)

                    // Live-hydrate author verification from users collection (60s TTL cache) so even older comments show verified ticks
                    val missingUids = sorted.map { it.userId }.filter { it.isNotBlank() }.distinct().filter { uid ->
                        val cached = commentAuthorVerifyCache[uid]
                        cached == null || (nowMs - cached.third) > 60_000L
                    }
                    if (missingUids.isNotEmpty()) {
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            var anyUpdated = false
                            for (chunk in missingUids.chunked(10)) {
                                try {
                                    val usersSnap = firestore.collection("users")
                                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                                        .get()
                                        .await()
                                    usersSnap.documents.forEach { uDoc ->
                                        val vStatus = uDoc.getString("verificationStatus") ?: ""
                                        val vSuspended = uDoc.getBoolean("verificationSuspended") ?: false
                                        val liveVerified = !vSuspended && (
                                            (uDoc.getBoolean("isVerified") ?: false) ||
                                            vStatus == "approved" ||
                                            vStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE
                                        )
                                        val liveBadge = uDoc.getString("verificationBadgeUrl") ?: ""
                                        commentAuthorVerifyCache[uDoc.id] = Triple(liveVerified, liveBadge, System.currentTimeMillis())
                                        anyUpdated = true
                                    }
                                } catch (_: Exception) {}
                            }
                            if (anyUpdated) {
                                val enriched = sorted.map { c ->
                                    val cached = commentAuthorVerifyCache[c.userId]
                                    if (cached != null) {
                                        c.copy(
                                            isVerified = cached.first,
                                            verificationBadgeUrl = cached.second.ifBlank { c.verificationBadgeUrl }
                                        )
                                    } else c
                                }
                                trySend(enriched)
                            }
                        }
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    // Comment Likes
    suspend fun toggleCommentLike(userId: String, commentId: Long): Boolean = withContext(Dispatchers.IO) {
        val alreadyLiked = dao.isCommentLiked(userId, commentId).firstOrNull() ?: false
        if (alreadyLiked) {
            dao.deleteCommentLike(CommentLikeEntity(userId, commentId))
            false
        } else {
            dao.insertCommentLike(CommentLikeEntity(userId, commentId))
            true
        }
    }

    fun isCommentLiked(userId: String, commentId: Long): Flow<Boolean> {
        return dao.isCommentLiked(userId, commentId)
    }

    fun getCommentLikesCount(commentId: Long): Flow<Int> {
        return dao.getCommentLikesCount(commentId)
    }


    // --- STORIES ---

    suspend fun getStories(): List<StoryEntity> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val snapshot = firestore.collection("stories")
                .whereGreaterThan("expiryTime", now)
                .orderBy("expiryTime", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
                
            snapshot.documents.mapNotNull { doc ->
                try {
                    StoryEntity(
                        id = doc.getString("id") ?: return@mapNotNull null,
                        userId = doc.getString("userId") ?: "",
                        username = doc.getString("username") ?: "",
                        avatarUrl = doc.getString("avatarUrl") ?: "",
                        textOverlay = doc.getString("textOverlay") ?: "",
                        mediaUrl = doc.getString("mediaUrl") ?: "",
                        timestamp = doc.getLong("timestampMillis") ?: 0L,
                        expiresAt = doc.getLong("expiryTime") ?: 0L,
                        viewCount = doc.getLong("viewCount")?.toInt() ?: 0,
                        mentionedUserId = doc.getString("mentionedUserId"),
                        mentionedUsername = doc.getString("mentionedUsername"),
                        isReback = doc.getBoolean("isReback") ?: false,
                        musicTrackName = doc.getString("musicTrackName"),
                        musicTrackArtist = doc.getString("musicTrackArtist"),
                        musicTrackUrl = doc.getString("musicTrackUrl"),
                        repostedPostId = doc.getString("repostedPostId"),
                        isLive = doc.getBoolean("isLive") ?: false
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SocialRepository", "Error fetching stories", e)
            emptyList()
        }
    }

    suspend fun postStory(
        userId: String,
        textOverlay: String,
        imageUrl: String,
        mentionedUserId: String? = null,
        mentionedUsername: String? = null,
        isReback: Boolean = false,
        musicTrackName: String? = null,
        musicTrackArtist: String? = null,
        musicTrackUrl: String? = null,
        repostedPostId: String? = null,
        isLive: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val (username, avatarUrl) = try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            val uName = userDoc.getString("username") ?: "user_${userId.take(5)}"
            val aUrl = userDoc.getString("avatar_url") ?: userDoc.getString("profilePicUrl") ?: ""
            Pair(uName, aUrl)
        } catch (e: Exception) {
            Pair("user_${userId.take(5)}", "")
        }

        val storyId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expireAt = java.util.Date(now + 24 * 60 * 60 * 1000)
        
        val storyMap = hashMapOf(
            "id" to storyId,
            "userId" to userId,
            "username" to username,
            "avatarUrl" to avatarUrl,
            "textOverlay" to textOverlay,
            "mediaUrl" to imageUrl,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "timestampMillis" to now,
            "expireAt" to expireAt,
            "expiryTime" to expireAt.time,
            "viewCount" to 0,
            "mentionedUserId" to mentionedUserId,
            "mentionedUsername" to mentionedUsername,
            "isReback" to isReback,
            "musicTrackName" to musicTrackName,
            "musicTrackArtist" to musicTrackArtist,
            "musicTrackUrl" to musicTrackUrl,
            "viewers" to emptyList<String>(),
            "likesCount" to 0,
            "commentsCount" to 0,
            "repostedPostId" to repostedPostId,
            "isLive" to isLive
        )
        
        try {
            firestore.collection("stories").document(storyId).set(storyMap).await()
            
            // If going live, also update user document status
            if (isLive) {
                firestore.collection("users").document(userId).update("isLive", true).await()
            }
        } catch (e: Exception) {
            android.util.Log.e("SocialRepository", "Error setting story in firestore", e)
        }

        // Also save locally
        val storyEntity = StoryEntity(
            id = storyId,
            userId = userId,
            username = username,
            avatarUrl = avatarUrl,
            textOverlay = textOverlay,
            mediaUrl = imageUrl,
            timestamp = now,
            expiresAt = expireAt.time,
            viewCount = 0,
            mentionedUserId = mentionedUserId,
            mentionedUsername = mentionedUsername,
            isReback = isReback,
            musicTrackName = musicTrackName,
            musicTrackArtist = musicTrackArtist,
            musicTrackUrl = musicTrackUrl,
            repostedPostId = repostedPostId,
            isLive = isLive
        )
        dao.insertStory(storyEntity)
    }

    fun getAllStories(): Flow<List<StoryEntity>> {
        return dao.getAllStories()
    }

    fun startStoriesSync() {
        // Removed
    }

    private fun fetchStoriesFromAppwrite() {
        // Removed
    }


    // --- MULTI-IMAGE POSTS ---
    suspend fun createMultiImagePost(userId: String, description: String, imagesCsv: String) = withContext(Dispatchers.IO) {
        val username = "user_${userId.take(5)}"
        val avatarUrl = ""

        val post = MultiImagePostEntity(
            id = java.util.UUID.randomUUID().toString(),
            userId = userId,
            username = username,
            avatarUrl = avatarUrl,
            description = description,
            imagesCsv = imagesCsv,
            timestamp = System.currentTimeMillis()
        )
        dao.insertMultiImagePost(post)
    }

    fun getAllMultiImagePosts(): Flow<List<MultiImagePostEntity>> {
        return dao.getAllMultiImagePosts()
    }

    // --- MESSAGES / CHAT (Conversations & Messages) ---
    suspend fun createGroupChat(ownerId: String, name: String, participantIds: List<String>) = withContext(Dispatchers.IO) {
        val conversationId = "group_${java.util.UUID.randomUUID()}"
        val conversation = Conversation(
            id = conversationId,
            participantIds = (participantIds + ownerId).distinct(),
            lastMessage = "Group created",
            isGroup = true,
            groupName = name,
            groupOwnerId = ownerId,
            groupAvatarUrl = ""
        )
        firestore.collection("conversations").document(conversationId).set(conversation).await()
    }

    suspend fun sendMessageToConversation(conversationId: String, senderId: String, text: String, mediaUrl: String? = null) = withContext(Dispatchers.IO) {
        val messageId = java.util.UUID.randomUUID().toString()
        val message = FirebaseMessage(
            id = messageId,
            senderId = senderId,
            receiverId = "",
            text = text,
            mediaUrl = mediaUrl,
            timestamp = null,
            isRead = false
        )
        val conversationRef = firestore.collection("conversations").document(conversationId)
        val messageRef = conversationRef.collection("messages").document(messageId)

        try {
            firestore.runTransaction { transaction ->
                val conversationDoc = transaction.get(conversationRef)
                val conversation = conversationDoc.toObject(Conversation::class.java)?.copy(id = conversationDoc.id)
                if (conversation != null) {
                    val updatedUnreadMap = conversation.unreadCountMap.toMutableMap()
                    conversation.participantIds.forEach { memberId ->
                        if (memberId != senderId) {
                            updatedUnreadMap[memberId] = (updatedUnreadMap[memberId] ?: 0) + 1
                        }
                    }
                    val updatedConversation = conversation.copy(
                        lastMessage = text,
                        unreadCountMap = updatedUnreadMap
                    )
                    transaction.set(messageRef, message)
                    transaction.set(conversationRef, updatedConversation)
                }
                null
            }.await()
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error sending message to conversation", e)
            messageRef.set(message).await()
            val updateMap = mapOf(
                "lastMessage" to text,
                "lastMessageTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            conversationRef.set(updateMap, com.google.firebase.firestore.SetOptions.merge()).await()
        }
    }

    suspend fun addMemberToGroup(conversationId: String, memberId: String) = withContext(Dispatchers.IO) {
        val conversationRef = firestore.collection("conversations").document(conversationId)
        try {
            firestore.runTransaction { transaction ->
                val doc = transaction.get(conversationRef)
                val conversation = doc.toObject(Conversation::class.java)
                if (conversation != null && conversation.isGroup) {
                    val newList = (conversation.participantIds + memberId).distinct()
                    transaction.update(conversationRef, "participantIds", newList)
                }
                null
            }.await()
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error adding member", e)
        }
    }

    suspend fun removeMemberFromGroup(conversationId: String, memberId: String) = withContext(Dispatchers.IO) {
        val conversationRef = firestore.collection("conversations").document(conversationId)
        try {
            firestore.runTransaction { transaction ->
                val doc = transaction.get(conversationRef)
                val conversation = doc.toObject(Conversation::class.java)
                if (conversation != null && conversation.isGroup) {
                    val newList = conversation.participantIds.filter { it != memberId }
                    transaction.update(conversationRef, "participantIds", newList)
                }
                null
            }.await()
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error removing member", e)
        }
    }

    suspend fun sendRealtimeMessage(senderId: String, receiverId: String, text: String, mediaUrl: String? = null) = withContext(Dispatchers.IO) {
        val conversationId = if (senderId < receiverId) "${senderId}_${receiverId}" else "${receiverId}_${senderId}"
        val messageId = java.util.UUID.randomUUID().toString()
        
        val message = FirebaseMessage(
            id = messageId,
            senderId = senderId,
            receiverId = receiverId,
            text = text,
            mediaUrl = mediaUrl,
            timestamp = null, // Will be set by @ServerTimestamp
            isRead = false
        )
        
        val conversationRef = firestore.collection("conversations").document(conversationId)
        val messageRef = conversationRef.collection("messages").document(messageId)
        
        var attempts = 0
        val maxRetry = 3
        var success = false
        
        while (attempts < maxRetry && !success) {
            try {
                        firestore.runTransaction { transaction ->
                    // Read first
                    val conversationDoc = transaction.get(conversationRef)
                    val conversation = conversationDoc.toObject(Conversation::class.java)?.copy(id = conversationDoc.id) ?: Conversation(
                        id = conversationId,
                        participantIds = listOf(senderId, receiverId)
                    )
                    
                    val updatedUnreadMap = conversation.unreadCountMap.toMutableMap()
                    updatedUnreadMap[receiverId] = (updatedUnreadMap[receiverId] ?: 0) + 1
                    
                    val updatedConversation = conversation.copy(
                        lastMessage = text,
                        unreadCountMap = updatedUnreadMap
                    )

                    // Write second
                    transaction.set(messageRef, message)
                    transaction.set(conversationRef, updatedConversation)
                    null
                }.await()
                success = true
            } catch (e: Exception) {
                attempts++
                Log.w("SocialRepository", "Message Transaction attempt $attempts failed", e)
                if (attempts >= maxRetry) {
                    // Fallback to direct writes to allow offline caching queueing
                    try {
                        Log.d("SocialRepository", "Falling back to direct Firestore set for offline message sending queue")
                        messageRef.set(message).await()
                        
                        val offlineConversationData = mapOf(
                            "id" to conversationId,
                            "participantIds" to listOf(senderId, receiverId),
                            "lastMessage" to text,
                            "lastMessageTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        conversationRef.set(offlineConversationData, com.google.firebase.firestore.SetOptions.merge()).await()
                        success = true
                    } catch (offlineEx: Exception) {
                        Log.e("SocialRepository", "Offline direct set fallback failed", offlineEx)
                    }
                } else {
                    kotlinx.coroutines.delay(200L * attempts)
                }
            }
        }
    }

    fun getConversations(userId: String): Flow<List<Conversation>> = callbackFlow {
        val listener = firestore.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val rawList = snapshot.documents.mapNotNull { doc ->
                        val c = doc.toObject(Conversation::class.java)
                        c?.copy(id = doc.id)
                    }
                    val sortedList = rawList.sortedByDescending { it.lastMessageTimestamp ?: java.util.Date(0) }
                    trySend(sortedList)
                } else {
                    trySend(emptyList())
                }
            }
        awaitClose { listener.remove() }
    }

    fun getRealtimeMessages(conversationId: String): Flow<List<FirebaseMessage>> = callbackFlow {
        val listener = firestore.collection("conversations").document(conversationId)
            .collection("messages")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val rawList = snapshot.documents.mapNotNull { doc ->
                        val m = doc.toObject(FirebaseMessage::class.java)
                        m?.copy(id = doc.id)
                    }
                    val sortedList = rawList.sortedBy { it.timestamp ?: java.util.Date(0) }
                    trySend(sortedList)
                } else {
                    trySend(emptyList())
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun markMessagesAsRead(conversationId: String, userId: String) = withContext(Dispatchers.IO) {
        val conversationRef = firestore.collection("conversations").document(conversationId)
        
        firestore.runTransaction { transaction ->
            val docSnap = transaction.get(conversationRef)
            val conversation = docSnap.toObject(Conversation::class.java)?.copy(id = docSnap.id)
            if (conversation != null) {
                val updatedUnreadMap = conversation.unreadCountMap.toMutableMap()
                updatedUnreadMap[userId] = 0
                transaction.update(conversationRef, "unreadCountMap", updatedUnreadMap)
            }
            null
        }.await()
        
        // Mark individual messages as read (Optional optimization: only mark if unread > 0)
        val unreadMessages = firestore.collection("conversations").document(conversationId)
            .collection("messages")
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("read", false)
            .get().await()
            
        for (doc in unreadMessages.documents) {
            doc.reference.update("read", true).await()
        }
    }

    // --- CALL SIGNALING (Firestore Real-time Based) ---
    fun sendCallSignaling(receiverId: String, type: String, data: Map<String, Any>) = CoroutineScope(Dispatchers.IO).launch {
        if (receiverId.isEmpty()) return@launch
        try {
            val currentUserId = RepositoryManager.getCurrentUserId()
            val signalingMap = hashMapOf(
                "type" to type,
                "data" to data,
                "senderId" to currentUserId,
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "timestampMillis" to System.currentTimeMillis()
            )
            firestore.collection("calls").document(receiverId).set(signalingMap).await()

            // When sending an incoming call offer, also push a real-time notification
            if (type == "offer") {
                val mediaType = data["mediaType"] as? String ?: "audio"
                val senderUsername = data["senderUsername"] as? String ?: "User"
                val notificationId = java.util.UUID.randomUUID().toString()
                val notifMap = hashMapOf(
                    "id" to notificationId,
                    "recipientId" to receiverId,
                    "type" to "CALL",
                    "actorId" to currentUserId,
                    "actorUsername" to senderUsername,
                    "actorAvatar" to "",
                    "content" to "Incoming $mediaType call from $senderUsername",
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                try {
                    firestore.collection("users").document(receiverId).collection("notifications").document(notificationId).set(notifMap)
                } catch (ignored: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error sending call signaling", e)
        }
    }

    fun observeCallSignaling(userId: String): Flow<Map<String, Any>?> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("calls").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("SocialRepository", "observeCallSignaling listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.data)
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    fun observeCallSignalingRtdb(userId: String): Flow<Map<String, Any>?> = observeCallSignaling(userId)

    fun clearCallSignaling(userId: String) = CoroutineScope(Dispatchers.IO).launch {
        if (userId.isEmpty()) return@launch
        try {
            firestore.collection("calls").document(userId).delete().await()
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error clearing call signaling", e)
        }
    }

    suspend fun markStoryAsViewed(storyId: String, userId: String) = withContext(Dispatchers.IO) {
        if (storyId.isEmpty() || userId.isEmpty()) return@withContext
        try {
            val storyRef = firestore.collection("stories").document(storyId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(storyRef)
                if (snapshot.exists()) {
                    val viewers = snapshot.get("viewers") as? List<String> ?: emptyList()
                    if (!viewers.contains(userId)) {
                        val newViewers = viewers.toMutableList().apply { add(userId) }
                        transaction.update(storyRef, "viewers", newViewers)
                        transaction.update(storyRef, "viewCount", newViewers.size)
                    }
                }
                null
            }.await()
        } catch (e: Exception) {}
    }

    // ==========================================
    // AUDIO / SOUND & TRENDING RANK ENGINE
    // ==========================================

    suspend fun getSoundDetails(
        soundId: String,
        fallbackTitle: String,
        fallbackArtist: String,
        fallbackCover: String
    ): AudioSound = withContext(Dispatchers.IO) {
        val cleanSoundId = soundId.trim()
        val decodedTitle = try { java.net.URLDecoder.decode(fallbackTitle, "UTF-8") } catch (e: Exception) { fallbackTitle }
        val decodedArtist = try { java.net.URLDecoder.decode(fallbackArtist, "UTF-8") } catch (e: Exception) { fallbackArtist }
        val decodedCover = try { java.net.URLDecoder.decode(fallbackCover, "UTF-8") } catch (e: Exception) { fallbackCover }

        var creatorId = ""
        var creatorUsername = decodedArtist
        var creatorDisplayName = decodedArtist
        var creatorAvatar = decodedCover
        var isVerified = false
        var audioUrl = ""
        var originalVideoId = ""
        var soundCategory = SoundCategory.ORIGINAL_AUDIO

        val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
        var isSaved = false

        try {
            // Check if saved
            if (currentUserId.isNotEmpty() && cleanSoundId.isNotEmpty()) {
                val savedDoc = firestore.collection("users").document(currentUserId)
                    .collection("savedSounds").document(cleanSoundId).get().await()
                isSaved = savedDoc.exists()
            }

            // Query posts matching this sound (bounded to prevent full-collection scans)
            val postsSnapshot = firestore.collection("posts")
                .limit(120)
                .get()
                .await()
            val allPosts = postsSnapshot.documents.mapNotNull {
                try { it.toObject(Video::class.java)?.copy(id = it.id) } catch (_: Exception) { null }
            }

            val matchingPosts = allPosts.filter { post ->
                val postSoundId = post.musicTrackId ?: "orig_${post.id}"
                postSoundId == cleanSoundId ||
                post.id == cleanSoundId ||
                post.id == cleanSoundId.removePrefix("orig_") ||
                (post.musicTrack.isNotEmpty() && post.musicTrack.equals(decodedTitle, ignoreCase = true))
            }

            val reelsCount = matchingPosts.size.coerceAtLeast(1)
            val totalViews = matchingPosts.sumOf { it.viewsCount.toLong() }
            val now = System.currentTimeMillis()
            val sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000L)

            // Velocity algorithm: Recent reels created in last 7 days + total likes & shares
            val recentPosts = matchingPosts.filter { it.timestamp >= sevenDaysAgo || it.timestamp == 0L }
            val recentLikes = matchingPosts.sumOf { it.likesCount }
            val recentShares = matchingPosts.sumOf { it.sharesCount }
            val velocityScore = (recentPosts.size * 15.0) + (recentLikes * 0.4) + (recentShares * 1.5) + (totalViews * 0.05)

            // Original post or user details
            val primaryPost = matchingPosts.firstOrNull { it.userId.isNotEmpty() }
            if (primaryPost != null) {
                creatorId = primaryPost.userId
                creatorUsername = primaryPost.user.username.ifEmpty { decodedArtist }
                creatorDisplayName = primaryPost.user.displayName.ifEmpty { creatorUsername }
                creatorAvatar = primaryPost.user.avatarUrl.ifEmpty { decodedCover }
                isVerified = primaryPost.user.isVerified
                originalVideoId = primaryPost.id
                audioUrl = primaryPost.musicTrackUrl ?: primaryPost.videoUrl
                if (!primaryPost.musicTrackUrl.isNullOrEmpty() && primaryPost.musicTrack.isNotEmpty() && !primaryPost.musicTrack.contains("Original", ignoreCase = true)) {
                    soundCategory = SoundCategory.LICENSED_MUSIC
                }
            } else if (cleanSoundId.startsWith("orig_") || cleanSoundId.isNotEmpty()) {
                val postDoc = firestore.collection("posts").document(cleanSoundId.removePrefix("orig_")).get().await()
                if (postDoc.exists()) {
                    val post = try { postDoc.toObject(Video::class.java)?.copy(id = postDoc.id) } catch (_: Exception) { null }
                    if (post != null) {
                        creatorId = post.userId
                        creatorUsername = post.user.username.ifEmpty { decodedArtist }
                        creatorDisplayName = post.user.displayName.ifEmpty { creatorUsername }
                        creatorAvatar = post.user.avatarUrl.ifEmpty { decodedCover }
                        isVerified = post.user.isVerified
                        originalVideoId = post.id
                        audioUrl = post.musicTrackUrl ?: post.videoUrl
                    }
                }
            }

            // If audioUrl is still empty and title is known, resolve track from MusicClient
            if (audioUrl.isEmpty() && decodedTitle.isNotEmpty() && decodedTitle != "unknown" && !decodedTitle.equals("Original Audio", ignoreCase = true)) {
                try {
                    val searchResult = com.example.api.MusicClient.searchIndianTracks(decodedTitle)
                    val foundTrack = searchResult.results.firstOrNull { !it.previewUrl.isNullOrEmpty() }
                    if (foundTrack != null) {
                        audioUrl = foundTrack.previewUrl ?: ""
                        if (creatorAvatar.isEmpty()) creatorAvatar = foundTrack.artworkUrl ?: ""
                        soundCategory = SoundCategory.LICENSED_MUSIC
                    }
                } catch (e: Exception) {
                    // Ignore fallback resolution failure
                }
            }

            // Calculate trending rank relative to all sounds
            val allSoundGroups = allPosts.groupBy { it.musicTrackId ?: (it.musicTrack.ifEmpty { "orig_${it.id}" }) }
            val soundRankings = allSoundGroups.map { (key, list) ->
                val vScore = (list.filter { it.timestamp >= sevenDaysAgo }.size * 15.0) + (list.sumOf { it.likesCount } * 0.4) + (list.sumOf { it.viewsCount } * 0.05)
                key to vScore
            }.sortedByDescending { it.second }

            val rankIndex = soundRankings.indexOfFirst { it.first == cleanSoundId || it.first.equals(decodedTitle, ignoreCase = true) }
            val isTrending = rankIndex in 0..19 && velocityScore > 20.0
            val trendingRank = if (isTrending) rankIndex + 1 else null

            return@withContext AudioSound(
                id = cleanSoundId,
                title = if (decodedTitle.isEmpty() || decodedTitle == "unknown") "Original Audio" else decodedTitle,
                artist = if (decodedArtist.isEmpty() || decodedArtist == "unknown") creatorUsername.ifEmpty { "Social India" } else decodedArtist,
                category = soundCategory,
                creatorId = creatorId,
                creatorUsername = creatorUsername,
                creatorDisplayName = creatorDisplayName,
                creatorAvatarUrl = creatorAvatar,
                creatorIsVerified = isVerified,
                audioUrl = audioUrl,
                coverImageUrl = creatorAvatar.ifEmpty { decodedCover },
                durationSeconds = 30,
                reelsCount = reelsCount,
                totalViews = totalViews,
                isTrending = isTrending,
                trendingRank = trendingRank,
                velocityScore = velocityScore,
                isSaved = isSaved,
                originalVideoId = originalVideoId
            )
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error getting sound details", e)
            return@withContext AudioSound(
                id = cleanSoundId,
                title = decodedTitle.ifEmpty { "Original Audio" },
                artist = decodedArtist.ifEmpty { "Creator" },
                category = soundCategory,
                creatorUsername = decodedArtist,
                coverImageUrl = decodedCover,
                reelsCount = 1,
                isSaved = isSaved
            )
        }
    }

    suspend fun getVideosForSound(
        soundId: String,
        soundTitle: String,
        limit: Int = 50
    ): List<Video> = withContext(Dispatchers.IO) {
        try {
            val decodedTitle = try { java.net.URLDecoder.decode(soundTitle, "UTF-8") } catch (e: Exception) { soundTitle }
            val cleanSoundId = soundId.trim()

            val snapshot = firestore.collection("posts")
                .limit(150)
                .get()
                .await()

            val allVideos = snapshot.documents.mapNotNull {
                try { it.toObject(Video::class.java)?.copy(id = it.id) } catch (_: Exception) { null }
            }
            val matched = allVideos.filter { v ->
                val vSoundId = v.musicTrackId ?: "orig_${v.id}"
                vSoundId == cleanSoundId ||
                v.id == cleanSoundId ||
                v.id == cleanSoundId.removePrefix("orig_") ||
                (decodedTitle.isNotEmpty() && v.musicTrack.equals(decodedTitle, ignoreCase = true))
            }.sortedByDescending { it.viewsCount }

            return@withContext matched.take(limit)
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error fetching videos for sound", e)
            return@withContext emptyList()
        }
    }

    suspend fun toggleSaveSound(userId: String, sound: AudioSound): Boolean = withContext(Dispatchers.IO) {
        if (userId.isEmpty() || sound.id.isEmpty()) return@withContext false
        try {
            val docRef = firestore.collection("users").document(userId)
                .collection("savedSounds").document(sound.id)
            val doc = docRef.get().await()
            if (doc.exists()) {
                docRef.delete().await()
                return@withContext false
            } else {
                val soundMap = hashMapOf(
                    "id" to sound.id,
                    "title" to sound.title,
                    "artist" to sound.artist,
                    "coverImageUrl" to sound.coverImageUrl,
                    "audioUrl" to sound.audioUrl,
                    "creatorUsername" to sound.creatorUsername,
                    "savedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(soundMap).await()
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error toggling save sound", e)
            return@withContext false
        }
    }

    suspend fun isSoundSaved(userId: String, soundId: String): Boolean = withContext(Dispatchers.IO) {
        if (userId.isEmpty() || soundId.isEmpty()) return@withContext false
        try {
            val doc = firestore.collection("users").document(userId)
                .collection("savedSounds").document(soundId).get().await()
            return@withContext doc.exists()
        } catch (e: Exception) {
            return@withContext false
        }
    }

    suspend fun getSavedSounds(userId: String): List<AudioSound> = withContext(Dispatchers.IO) {
        if (userId.isEmpty()) return@withContext emptyList()
        try {
            val snapshot = firestore.collection("users").document(userId)
                .collection("savedSounds").get().await()
            return@withContext snapshot.documents.mapNotNull { doc ->
                AudioSound(
                    id = doc.getString("id") ?: doc.id,
                    title = doc.getString("title") ?: "Original Audio",
                    artist = doc.getString("artist") ?: "Artist",
                    coverImageUrl = doc.getString("coverImageUrl") ?: "",
                    audioUrl = doc.getString("audioUrl") ?: "",
                    creatorUsername = doc.getString("creatorUsername") ?: "",
                    isSaved = true
                )
            }
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error fetching saved sounds", e)
            return@withContext emptyList()
        }
    }

    suspend fun getTrendingSounds(limit: Int = 20): List<AudioSound> = withContext(Dispatchers.IO) {
        try {
            val postsSnapshot = firestore.collection("posts")
                .limit(200)
                .get()
                .await()
            val allPosts = postsSnapshot.documents.mapNotNull {
                try { it.toObject(Video::class.java)?.copy(id = it.id) } catch (_: Exception) { null }
            }

            val now = System.currentTimeMillis()
            val sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000L)

            // Group by sound identity
            val groups = allPosts.groupBy { post ->
                if (!post.musicTrackId.isNullOrEmpty()) {
                    post.musicTrackId
                } else if (post.musicTrack.isNotEmpty() && !post.musicTrack.contains("Original", ignoreCase = true)) {
                    post.musicTrack
                } else {
                    "orig_${post.id}"
                }
            }

            val rankedSounds = groups.map { (key, posts) ->
                val primary = posts.first()
                val recentCount = posts.count { it.timestamp >= sevenDaysAgo || it.timestamp == 0L }
                val views = posts.sumOf { it.viewsCount.toLong() }
                val likes = posts.sumOf { it.likesCount }
                val velocity = (recentCount * 20.0) + (likes * 0.5) + (views * 0.02)

                AudioSound(
                    id = key,
                    title = primary.musicTrack.ifEmpty { "Original Audio" },
                    artist = primary.musicTrackArtist ?: primary.user.username.ifEmpty { "Social India" },
                    category = if (!primary.musicTrackUrl.isNullOrEmpty() && primary.musicTrack.isNotEmpty()) SoundCategory.LICENSED_MUSIC else SoundCategory.ORIGINAL_AUDIO,
                    creatorId = primary.userId,
                    creatorUsername = primary.user.username,
                    creatorDisplayName = primary.user.displayName,
                    creatorAvatarUrl = primary.user.avatarUrl.ifEmpty { primary.musicTrackImageUrl ?: "" },
                    creatorIsVerified = primary.user.isVerified,
                    audioUrl = primary.musicTrackUrl ?: primary.videoUrl,
                    coverImageUrl = primary.musicTrackImageUrl ?: primary.user.avatarUrl,
                    reelsCount = posts.size,
                    totalViews = views,
                    velocityScore = velocity
                )
            }.sortedByDescending { it.velocityScore }.take(limit)

            return@withContext rankedSounds.mapIndexed { idx, sound ->
                sound.copy(
                    isTrending = true,
                    trendingRank = idx + 1
                )
            }
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error getting trending sounds", e)
            return@withContext emptyList()
        }
    }

    suspend fun repostContent(
        contentId: String,
        ownerUid: String,
        contentType: String,
        note: String?,
        currentUserId: String,
        currentUsername: String,
        currentUserAvatar: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (currentUserId.isEmpty()) return@withContext false
            val repostId = "${contentId}_${currentUserId}"
            val repostMap = mutableMapOf<String, Any>(
                "id" to repostId,
                "originalContentId" to contentId,
                "originalContentType" to contentType,
                "originalOwnerUid" to ownerUid,
                "repostedByUid" to currentUserId,
                "reposterUsername" to currentUsername,
                "reposterAvatarUrl" to currentUserAvatar,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            if (!note.isNullOrBlank()) {
                repostMap["note"] = note
            }

            // Save repost document
            firestore.collection("reposts").document(repostId).set(repostMap).await()

            // Update post/video document
            val postRef = firestore.collection("posts").document(contentId)
            val snapshot = postRef.get().await()
            if (snapshot.exists()) {
                val currentRepostedUsers = snapshot.get("repostedByUserIds") as? List<*> ?: emptyList<Any>()
                val newRepostedUsers = currentRepostedUsers.filterIsInstance<String>().toMutableList()
                if (!newRepostedUsers.contains(currentUserId)) {
                    newRepostedUsers.add(currentUserId)
                }

                @Suppress("UNCHECKED_CAST")
                val thoughts = (snapshot.get("repostThoughts") as? Map<String, String>)?.toMutableMap() ?: mutableMapOf()
                if (!note.isNullOrBlank()) {
                    thoughts[currentUserId] = note
                } else {
                    thoughts.remove(currentUserId)
                }

                postRef.update(
                    mapOf(
                        "repostedByUserIds" to newRepostedUsers,
                        "repostThoughts" to thoughts,
                        "repostsCount" to newRepostedUsers.size
                    )
                ).await()
            }

            // Send notification to owner if different user
            if (ownerUid.isNotEmpty() && ownerUid != currentUserId) {
                val notifId = UUID.randomUUID().toString()
                val notifMap = mapOf(
                    "id" to notifId,
                    "recipientUid" to ownerUid,
                    "actorUid" to currentUserId,
                    "actorUsername" to currentUsername,
                    "actorAvatarUrl" to currentUserAvatar,
                    "type" to "repost",
                    "contentId" to contentId,
                    "message" to "$currentUsername reposted your post",
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "read" to false
                )
                firestore.collection("users").document(ownerUid).collection("notifications").document(notifId).set(notifMap).await()
                firestore.collection("notifications").document(notifId).set(notifMap).await()
            }

            true
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error reposting content", e)
            false
        }
    }

    suspend fun undoRepost(
        contentId: String,
        currentUserId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (currentUserId.isEmpty()) return@withContext false
            val repostId = "${contentId}_${currentUserId}"
            
            // Delete repost document
            firestore.collection("reposts").document(repostId).delete().await()

            // Update post/video document
            val postRef = firestore.collection("posts").document(contentId)
            val snapshot = postRef.get().await()
            if (snapshot.exists()) {
                val currentRepostedUsers = snapshot.get("repostedByUserIds") as? List<*> ?: emptyList<Any>()
                val newRepostedUsers = currentRepostedUsers.filterIsInstance<String>().toMutableList()
                newRepostedUsers.remove(currentUserId)

                @Suppress("UNCHECKED_CAST")
                val thoughts = (snapshot.get("repostThoughts") as? Map<String, String>)?.toMutableMap() ?: mutableMapOf()
                thoughts.remove(currentUserId)

                postRef.update(
                    mapOf(
                        "repostedByUserIds" to newRepostedUsers,
                        "repostThoughts" to thoughts,
                        "repostsCount" to maxOf(0, newRepostedUsers.size)
                    )
                ).await()
            }
            true
        } catch (e: Exception) {
            Log.e("SocialRepository", "Error undoing repost", e)
            false
        }
    }
}

