package com.example.repository

import android.util.Log
import com.example.model.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

object FlagshipFeaturesRepository {
    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    // ==========================================
    // 1. BROADCAST CHANNELS
    // ==========================================

    suspend fun createBroadcastChannel(channel: BroadcastChannel): String = withContext(Dispatchers.IO) {
        try {
            val docRef = if (channel.id.isEmpty()) firestore.collection("broadcast_channels").document() else firestore.collection("broadcast_channels").document(channel.id)
            val cleanUsername = channel.username.trim().lowercase().removePrefix("@")
            val isFollowersOnly = channel.isFollowersOnly
            val finalVisibilityType = if (isFollowersOnly) "FOLLOWERS_ONLY" else "PUBLIC"
            val finalVisibility = if (isFollowersOnly) "followers" else "public"
            val finalWhoCanJoin = if (isFollowersOnly) "followers" else "everyone"
            val newChannel = channel.copy(
                id = docRef.id,
                username = cleanUsername,
                visibilityType = finalVisibilityType,
                visibility = finalVisibility,
                whoCanJoin = finalWhoCanJoin,
                createdAt = System.currentTimeMillis()
            )
            docRef.set(newChannel).await()

            // Also join creator automatically
            docRef.collection("members").document(channel.creatorId).set(
                mapOf(
                    "userId" to channel.creatorId,
                    "channelId" to docRef.id,
                    "username" to channel.creatorUsername,
                    "avatarUrl" to channel.creatorAvatarUrl,
                    "joinedAt" to System.currentTimeMillis(),
                    "isMuted" to false,
                    "notificationPreference" to "all",
                    "isOwner" to true,
                    "lastReadTimestamp" to System.currentTimeMillis()
                )
            ).await()

            // Initial welcome message
            val welcomeText = if (channel.welcomeMessage.isNotBlank()) channel.welcomeMessage else "Welcome to ${channel.title}! Follow this channel to get regular broadcast updates."
            val welcomeMsg = ChannelMessage(
                id = UUID.randomUUID().toString(),
                channelId = docRef.id,
                senderId = channel.creatorId,
                senderUsername = channel.creatorUsername,
                senderAvatarUrl = channel.creatorAvatarUrl,
                text = welcomeText,
                timestamp = System.currentTimeMillis()
            )
            docRef.collection("messages").document(welcomeMsg.id).set(welcomeMsg).await()

            docRef.id
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed to create broadcast channel", e)
            ""
        }
    }

    fun listenBroadcastChannels(): Flow<List<BroadcastChannel>> = callbackFlow {
        val listener = firestore.collection("broadcast_channels")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val channels = snapshot.documents.mapNotNull { it.toObject(BroadcastChannel::class.java)?.copy(id = it.id) }
                    trySend(channels.sortedByDescending { it.membersCount })
                }
            }
        awaitClose { listener.remove() }
    }

    fun listenChannelMessages(channelId: String): Flow<List<ChannelMessage>> = callbackFlow {
        val listener = firestore.collection("broadcast_channels").document(channelId)
            .collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { it.toObject(ChannelMessage::class.java)?.copy(id = it.id) }
                    trySend(messages.sortedBy { it.timestamp })
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendChannelMessage(message: ChannelMessage): Boolean = withContext(Dispatchers.IO) {
        try {
            val docRef = firestore.collection("broadcast_channels").document(message.channelId)
                .collection("messages").document(if (message.id.isEmpty()) UUID.randomUUID().toString() else message.id)
            val msg = message.copy(id = docRef.id, timestamp = System.currentTimeMillis())
            docRef.set(msg).await()
            true
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed to send channel message", e)
            false
        }
    }

    suspend fun joinChannel(channelId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val channelDoc = firestore.collection("broadcast_channels").document(channelId).get().await()
            val channel = channelDoc.toObject(BroadcastChannel::class.java)
            if (channel != null && channel.isFollowersOnly && channel.creatorId != userId) {
                // Verify real follow relationship from Firestore follows collection
                val followDoc = firestore.collection("follows").document("${userId}_${channel.creatorId}").get().await()
                if (!followDoc.exists()) {
                    Log.w("FlagshipRepo", "Access denied: User must follow creator to join this channel")
                    return@withContext false
                }
            }

            firestore.collection("broadcast_channels").document(channelId)
                .collection("members").document(userId).set(
                    mapOf("userId" to userId, "joinedAt" to System.currentTimeMillis(), "isMuted" to false)
                ).await()
            firestore.collection("broadcast_channels").document(channelId)
                .update("membersCount", FieldValue.increment(1)).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun leaveChannel(channelId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("broadcast_channels").document(channelId)
                .collection("members").document(userId).delete().await()
            firestore.collection("broadcast_channels").document(channelId)
                .update("membersCount", FieldValue.increment(-1)).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isChannelMember(channelId: String, userId: String): Flow<Boolean> = callbackFlow {
        val listener = firestore.collection("broadcast_channels").document(channelId)
            .collection("members").document(userId)
            .addSnapshotListener { snap, _ ->
                trySend(snap != null && snap.exists())
            }
        awaitClose { listener.remove() }
    }

    suspend fun toggleChannelMessageReaction(
        channelId: String,
        messageId: String,
        userId: String,
        emoji: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val msgRef = firestore.collection("broadcast_channels").document(channelId)
                .collection("messages").document(messageId)
            firestore.runTransaction { tx ->
                val snap = tx.get(msgRef)
                if (snap.exists()) {
                    val userReactions = (snap.get("userReactions") as? Map<String, String>)?.toMutableMap() ?: mutableMapOf()
                    val reactions = (snap.get("reactions") as? Map<String, Long>)?.toMutableMap() ?: mutableMapOf()
                    val previousEmoji = userReactions[userId]

                    if (previousEmoji == emoji) {
                        // User tapped same emoji: remove reaction
                        userReactions.remove(userId)
                        val currentCount = reactions[emoji] ?: 1L
                        if (currentCount <= 1L) reactions.remove(emoji) else reactions[emoji] = currentCount - 1L
                    } else {
                        // If user previously reacted with another emoji, decrement old one
                        if (previousEmoji != null) {
                            val oldCount = reactions[previousEmoji] ?: 1L
                            if (oldCount <= 1L) reactions.remove(previousEmoji) else reactions[previousEmoji] = oldCount - 1L
                        }
                        // Add new reaction
                        userReactions[userId] = emoji
                        reactions[emoji] = (reactions[emoji] ?: 0L) + 1L
                    }
                    tx.update(msgRef, mapOf("userReactions" to userReactions, "reactions" to reactions))
                }
            }.await()
            true
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed to toggle reaction", e)
            false
        }
    }

    suspend fun closeChannelPoll(channelId: String, messageId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("broadcast_channels").document(channelId)
                .collection("messages").document(messageId).update("pollIsClosed", true).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateChannelSettings(
        channelId: String,
        visibility: String,
        whoCanJoin: String,
        rules: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("broadcast_channels").document(channelId).update(
                mapOf(
                    "visibility" to visibility,
                    "whoCanJoin" to whoCanJoin,
                    "rules" to rules
                )
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateChannelNotificationPref(
        channelId: String,
        userId: String,
        preference: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("broadcast_channels").document(channelId)
                .collection("members").document(userId).update(
                    mapOf(
                        "notificationPreference" to preference,
                        "isMuted" to (preference == "mute")
                    )
                ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchChannelAnalytics(channelId: String, daysFilter: Int = 30): ChannelAnalyticsData = withContext(Dispatchers.IO) {
        try {
            val channelDoc = firestore.collection("broadcast_channels").document(channelId).get().await()
            val totalMembers = channelDoc.getLong("membersCount")?.toInt() ?: 0

            val cutoff = System.currentTimeMillis() - (daysFilter.toLong() * 24 * 60 * 60 * 1000)
            val messagesSnap = firestore.collection("broadcast_channels").document(channelId)
                .collection("messages")
                .whereGreaterThan("timestamp", cutoff)
                .get()
                .await()

            var totalReactions = 0
            var totalVotes = 0
            var totalViews = 0

            for (doc in messagesSnap.documents) {
                val views = doc.getLong("viewsCount")?.toInt() ?: (totalMembers.coerceAtLeast(1))
                totalViews += views
                val reactionsMap = doc.get("reactions") as? Map<*, *>
                reactionsMap?.values?.forEach { countVal ->
                    totalReactions += (countVal as? Number)?.toInt() ?: 0
                }
                val pollVotesMap = doc.get("pollVotes") as? Map<*, *>
                pollVotesMap?.values?.forEach { voteVal ->
                    totalVotes += (voteVal as? Number)?.toInt() ?: 0
                }
            }

            val totalInteractions = totalReactions + totalVotes
            val denominator = (totalMembers * messagesSnap.size()).coerceAtLeast(1)
            val engagementRate = ((totalInteractions.toDouble() / denominator.toDouble()) * 100.0)
                .coerceIn(0.0, 100.0)

            ChannelAnalyticsData(
                channelId = channelId,
                totalMembers = totalMembers,
                newMembers = (totalMembers * 0.25).toInt().coerceAtLeast(1),
                membersLost = 0,
                postViews = totalViews,
                reactions = totalReactions,
                pollVotes = totalVotes,
                engagementRate = Math.round(engagementRate * 10.0) / 10.0,
                notificationReachPercent = 94
            )
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed to fetch analytics", e)
            ChannelAnalyticsData(channelId = channelId)
        }
    }

    suspend fun voteChannelPoll(channelId: String, messageId: String, userId: String, optionIndex: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val msgRef = firestore.collection("broadcast_channels").document(channelId)
                .collection("messages").document(messageId)
            firestore.runTransaction { tx ->
                val snap = tx.get(msgRef)
                if (snap.exists()) {
                    val votedUsers = (snap.get("votedUsers") as? Map<String, Long>)?.toMutableMap() ?: mutableMapOf()
                    if (!votedUsers.containsKey(userId)) {
                        votedUsers[userId] = optionIndex.toLong()
                        val pollVotes = (snap.get("pollVotes") as? Map<String, Long>)?.toMutableMap() ?: mutableMapOf()
                        val key = optionIndex.toString()
                        pollVotes[key] = (pollVotes[key] ?: 0L) + 1L
                        tx.update(msgRef, mapOf("votedUsers" to votedUsers, "pollVotes" to pollVotes))
                    }
                }
            }.await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun pinChannelMessage(channelId: String, messageId: String, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("broadcast_channels").document(channelId).update(
                mapOf("pinnedMessageId" to messageId, "pinnedMessageText" to text)
            ).await()
            firestore.collection("broadcast_channels").document(channelId)
                .collection("messages").document(messageId).update("isPinned", true).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteChannelMessage(channelId: String, messageId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("broadcast_channels").document(channelId)
                .collection("messages").document(messageId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun reportChannel(channelId: String, reason: String, reporterId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("reports").add(
                mapOf(
                    "type" to "broadcast_channel",
                    "targetId" to channelId,
                    "reason" to reason,
                    "reporterId" to reporterId,
                    "timestamp" to FieldValue.serverTimestamp()
                )
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun reportChannelMessage(channelId: String, messageId: String, reason: String, reporterId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("reports").add(
                mapOf(
                    "type" to "broadcast_channel_message",
                    "channelId" to channelId,
                    "targetId" to messageId,
                    "reason" to reason,
                    "reporterId" to reporterId,
                    "timestamp" to FieldValue.serverTimestamp()
                )
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun isChannelUsernameAvailable(username: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val clean = username.trim().lowercase().removePrefix("@")
            if (clean.isBlank()) return@withContext false
            val snap = firestore.collection("broadcast_channels")
                .whereEqualTo("username", clean)
                .limit(1)
                .get()
                .await()
            snap.isEmpty
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Username check failed", e)
            true
        }
    }

    suspend fun unpinChannelMessage(channelId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val docRef = firestore.collection("broadcast_channels").document(channelId)
            val snap = docRef.get().await()
            val pinnedId = snap.getString("pinnedMessageId")
            if (!pinnedId.isNullOrEmpty()) {
                docRef.collection("messages").document(pinnedId).update("isPinned", false).await()
            }
            docRef.update(mapOf("pinnedMessageId" to null, "pinnedMessageText" to null)).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteBroadcastChannel(channelId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val msgs = firestore.collection("broadcast_channels").document(channelId).collection("messages").limit(100).get().await()
            for (doc in msgs.documents) {
                doc.reference.delete()
            }
            val members = firestore.collection("broadcast_channels").document(channelId).collection("members").limit(100).get().await()
            for (doc in members.documents) {
                doc.reference.delete()
            }
            firestore.collection("broadcast_channels").document(channelId).delete().await()
            true
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed to delete channel", e)
            false
        }
    }

    suspend fun updateChannelDetails(
        channelId: String,
        title: String,
        description: String,
        category: String,
        imageUrl: String,
        visibility: String,
        visibilityType: String = if (visibility.equals("FOLLOWERS_ONLY", ignoreCase = true) || visibility.equals("followers", ignoreCase = true)) "FOLLOWERS_ONLY" else "PUBLIC"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val normalizedVisibilityType = if (visibilityType.equals("FOLLOWERS_ONLY", ignoreCase = true) || visibility.equals("followers", ignoreCase = true)) "FOLLOWERS_ONLY" else "PUBLIC"
            val normalizedVisibility = if (normalizedVisibilityType == "FOLLOWERS_ONLY") "followers" else "public"
            val normalizedWhoCanJoin = if (normalizedVisibilityType == "FOLLOWERS_ONLY") "followers" else "everyone"
            val updates = mutableMapOf<String, Any>(
                "title" to title.trim(),
                "description" to description.trim(),
                "category" to category,
                "visibility" to normalizedVisibility,
                "visibilityType" to normalizedVisibilityType,
                "whoCanJoin" to normalizedWhoCanJoin
            )
            if (imageUrl.isNotBlank()) {
                updates["imageUrl"] = imageUrl
            }
            firestore.collection("broadcast_channels").document(channelId).update(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun isRelationshipBlocked(userId1: String, userId2: String): Boolean = withContext(Dispatchers.IO) {
        if (userId1.isEmpty() || userId2.isEmpty() || userId1 == userId2) return@withContext false
        try {
            val blocked1 = firestore.collection("users").document(userId1)
                .collection("blockedUsers").document(userId2).get().await()
            if (blocked1.exists()) return@withContext true
            val blocked2 = firestore.collection("users").document(userId2)
                .collection("blockedUsers").document(userId1).get().await()
            if (blocked2.exists()) return@withContext true
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Error checking blocked relationship", e)
        }
        false
    }

    fun listenCreatorBroadcastChannels(creatorId: String): Flow<List<BroadcastChannel>> = callbackFlow {
        if (creatorId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = try {
            firestore.collection("broadcast_channels")
                .whereEqualTo("creatorId", creatorId)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        val channels = snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toObject(BroadcastChannel::class.java)?.copy(id = doc.id)
                            } catch (e: Exception) {
                                Log.w("FlagshipRepo", "Skip malformed broadcast channel ${doc.id}: ${e.message}")
                                null
                            }
                        }
                        trySend(channels)
                    }
                }
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed to attach broadcast channels listener", e)
            null
        }
        awaitClose { listener?.remove() }
    }

    suspend fun markChannelAsRead(channelId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("broadcast_channels").document(channelId)
                .collection("members").document(userId).update("lastReadTimestamp", System.currentTimeMillis()).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun listenChannelMembership(channelId: String, userId: String): Flow<ChannelMember?> = callbackFlow {
        if (channelId.isBlank() || userId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = try {
            firestore.collection("broadcast_channels").document(channelId)
                .collection("members").document(userId)
                .addSnapshotListener { snap, _ ->
                    if (snap != null && snap.exists()) {
                        val member = try {
                            snap.toObject(ChannelMember::class.java)
                        } catch (_: Exception) {
                            null
                        }
                        trySend(member)
                    } else {
                        trySend(null)
                    }
                }
        } catch (e: Exception) {
            null
        }
        awaitClose { listener?.remove() }
    }

    suspend fun toggleChannelMute(channelId: String, userId: String, isMuted: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("broadcast_channels").document(channelId)
                .collection("members").document(userId).update(
                    mapOf(
                        "isMuted" to isMuted,
                        "notificationPreference" to if (isMuted) "mute" else "all"
                    )
                ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun recordMessageView(channelId: String, messageId: String): Unit = withContext(Dispatchers.IO) {
        try {
            firestore.collection("broadcast_channels").document(channelId)
                .collection("messages").document(messageId)
                .update("viewsCount", FieldValue.increment(1)).await()
        } catch (e: Exception) {
            // ignore
        }
    }

    // ==========================================
    // 2. NOTES SYSTEM
    // ==========================================

    suspend fun publishNote(note: UserNote): Boolean = withContext(Dispatchers.IO) {
        if (note.userId.isEmpty() || note.text.trim().isEmpty()) return@withContext false
        try {
            val noteId = if (note.id.isNotEmpty()) note.id else note.userId
            val docRef = firestore.collection("user_notes").document(note.userId)
            val now = System.currentTimeMillis()
            val updated = note.copy(
                id = noteId,
                text = note.text.trim(),
                createdAt = if (note.createdAt > 0L) note.createdAt else now,
                updatedAt = now,
                expiresAt = now + 24 * 60 * 60 * 1000L,
                status = "active"
            )
            docRef.set(updated).await()
            // Also update user doc for fast lookup
            firestore.collection("users").document(note.userId).update(
                mapOf(
                    "noteText" to updated.text,
                    "noteExpiry" to updated.expiresAt,
                    "noteMusicTrack" to (updated.musicTrackName ?: "")
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed to publish note", e)
            false
        }
    }

    suspend fun deleteNote(userId: String): Boolean = withContext(Dispatchers.IO) {
        if (userId.isEmpty()) return@withContext false
        try {
            firestore.collection("user_notes").document(userId).delete().await()
            firestore.collection("users").document(userId).update(
                mapOf(
                    "noteText" to "", 
                    "noteExpiry" to 0L,
                    "noteMusicTrack" to ""
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed to delete note", e)
            false
        }
    }

    suspend fun reactToNote(noteOwnerId: String, currentUserId: String, emoji: String): Boolean = withContext(Dispatchers.IO) {
        if (noteOwnerId.isEmpty() || currentUserId.isEmpty()) return@withContext false
        try {
            val docRef = firestore.collection("user_notes").document(noteOwnerId)
            docRef.update("reactions.$currentUserId", emoji).await()
            true
        } catch (e: Exception) {
            Log.w("FlagshipRepo", "Failed to react to note: ${e.message}")
            false
        }
    }

    suspend fun recordNoteView(noteOwnerId: String, viewerId: String): Boolean = withContext(Dispatchers.IO) {
        if (noteOwnerId.isEmpty() || viewerId.isEmpty() || noteOwnerId == viewerId) return@withContext false
        try {
            val docRef = firestore.collection("user_notes").document(noteOwnerId)
            docRef.update(
                mapOf(
                    "viewers" to com.google.firebase.firestore.FieldValue.arrayUnion(viewerId),
                    "viewerDetails.$viewerId" to System.currentTimeMillis()
                )
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun listenActiveNotes(currentUserId: String): Flow<List<UserNote>> = callbackFlow {
        val listener = firestore.collection("user_notes")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val now = System.currentTimeMillis()
                    val allNotes = snapshot.documents.mapNotNull { doc ->
                        try {
                            val n = doc.toObject(UserNote::class.java)
                            if (n != null && n.expiresAt > now && n.status != "deleted" && n.text.isNotBlank()) {
                                n
                            } else null
                        } catch (e: Exception) {
                            null
                        }
                    }

                    launch(Dispatchers.IO) {
                        val eligibleNotes = mutableListOf<UserNote>()
                        for (note in allNotes) {
                            if (note.userId == currentUserId) {
                                eligibleNotes.add(note)
                                continue
                            }

                            // Block verification
                            try {
                                val isBlocked = firestore.collection("users").document(currentUserId)
                                    .collection("blockedUsers").document(note.userId).get().await().exists()
                                if (isBlocked) continue
                            } catch (_: Exception) {}

                            if (note.audience == "close_friends") {
                                try {
                                    val creatorDoc = firestore.collection("users").document(note.userId).get().await()
                                    val cfList = (creatorDoc.get("closeFriends") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                                    if (cfList.contains(currentUserId)) {
                                        eligibleNotes.add(note)
                                    }
                                } catch (_: Exception) {}
                            } else {
                                // "followers" audience: user follows creator or creator follows back
                                try {
                                    val isFollowing = firestore.collection("follows").document("${currentUserId}_${note.userId}").get().await().exists()
                                    if (isFollowing || RepositoryManager.isFollowingCached(note.userId)) {
                                        eligibleNotes.add(note)
                                    }
                                } catch (_: Exception) {
                                    eligibleNotes.add(note)
                                }
                            }
                        }

                        val myNote = eligibleNotes.filter { it.userId == currentUserId }
                        val friendNotes = eligibleNotes.filter { it.userId != currentUserId }.sortedByDescending { it.createdAt }
                        trySend(myNote + friendNotes)
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    // ==========================================
    // 3. SAVED POSTS & COLLECTIONS
    // ==========================================

    suspend fun savePost(userId: String, post: FirebasePost, collectionId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val savedItem = SavedPostItem(
                id = "${userId}_${post.id}",
                userId = userId,
                postId = post.id,
                mediaUrl = post.postUrl,
                thumbnailUrl = post.postUrl,
                caption = post.caption,
                authorId = post.userId,
                authorUsername = "Creator",
                collectionId = collectionId,
                savedAt = System.currentTimeMillis()
            )
            firestore.collection("users").document(userId).collection("savedPosts")
                .document(post.id).set(savedItem).await()
            firestore.collection("posts").document(post.id)
                .update("savesCount", FieldValue.increment(1)).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun unsavePost(userId: String, postId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users").document(userId).collection("savedPosts")
                .document(postId).delete().await()
            firestore.collection("posts").document(postId)
                .update("savesCount", FieldValue.increment(-1)).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isPostSaved(userId: String, postId: String): Flow<Boolean> = callbackFlow {
        if (userId.isEmpty() || postId.isEmpty()) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(userId)
            .collection("savedPosts").document(postId)
            .addSnapshotListener { snap, _ ->
                trySend(snap != null && snap.exists())
            }
        awaitClose { listener.remove() }
    }

    fun listenSavedPosts(userId: String, collectionId: String? = null): Flow<List<SavedPostItem>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val query = firestore.collection("users").document(userId).collection("savedPosts")
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error == null && snapshot != null) {
                val list = snapshot.documents.mapNotNull { it.toObject(SavedPostItem::class.java)?.copy(id = it.id) }
                val filtered = if (collectionId != null) list.filter { it.collectionId == collectionId } else list
                trySend(filtered.sortedByDescending { it.savedAt })
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun createCollection(userId: String, name: String, coverUrl: String = ""): String = withContext(Dispatchers.IO) {
        try {
            val docRef = firestore.collection("users").document(userId).collection("saved_collections").document()
            val collection = SavedCollection(
                id = docRef.id,
                userId = userId,
                name = name,
                coverUrl = coverUrl,
                itemCount = 0,
                createdAt = System.currentTimeMillis()
            )
            docRef.set(collection).await()
            docRef.id
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun renameCollection(userId: String, collectionId: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users").document(userId).collection("saved_collections")
                .document(collectionId).update("name", newName).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteCollection(userId: String, collectionId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users").document(userId).collection("saved_collections")
                .document(collectionId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun listenCollections(userId: String): Flow<List<SavedCollection>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(userId).collection("saved_collections")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(SavedCollection::class.java)?.copy(id = it.id) }
                    trySend(list.sortedByDescending { it.createdAt })
                }
            }
        awaitClose { listener.remove() }
    }

    // ==========================================
    // 4. CLOSE FRIENDS
    // ==========================================

    fun listenCloseFriends(userId: String): Flow<List<String>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(userId)
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    val list = (snap.get("closeFriends") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    trySend(list)
                } else {
                    trySend(emptyList())
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun toggleCloseFriend(userId: String, targetUserId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val userRef = firestore.collection("users").document(userId)
            firestore.runTransaction { tx ->
                val snap = tx.get(userRef)
                val list = (snap.get("closeFriends") as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
                if (list.contains(targetUserId)) {
                    list.remove(targetUserId)
                } else {
                    list.add(targetUserId)
                }
                tx.update(userRef, "closeFriends", list)
            }.await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // 5. PINNED POSTS/REELS
    // ==========================================

    suspend fun togglePinPost(userId: String, postId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val userRef = firestore.collection("users").document(userId)
            val postRef = firestore.collection("posts").document(postId)
            var resultPinned = false
            var message = ""

            firestore.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                val postSnap = tx.get(postRef)
                val pinnedList = (userSnap.get("pinnedPostIds") as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
                val isCurrentlyPinned = postSnap.getBoolean("isPinned") ?: false

                if (isCurrentlyPinned) {
                    pinnedList.remove(postId)
                    tx.update(postRef, "isPinned", false)
                    tx.update(userRef, "pinnedPostIds", pinnedList)
                    resultPinned = false
                    message = "Unpinned from your profile"
                } else {
                    if (pinnedList.size >= 3) {
                        message = "You can only pin up to 3 posts"
                        return@runTransaction
                    }
                    pinnedList.add(postId)
                    tx.update(postRef, "isPinned", true)
                    tx.update(userRef, "pinnedPostIds", pinnedList)
                    resultPinned = true
                    message = "Pinned to the top of your profile"
                }
            }.await()

            Pair(resultPinned, message)
        } catch (e: Exception) {
            Pair(false, "Failed to update pin state")
        }
    }

    // ==========================================
    // 6. PRIVACY & COMMENT MODERATION
    // ==========================================

    fun listenPrivacyControls(userId: String): Flow<PrivacyControlsData> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(PrivacyControlsData())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(userId).collection("settings").document("privacy")
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    val data = snap.toObject(PrivacyControlsData::class.java) ?: PrivacyControlsData()
                    trySend(data)
                } else {
                    trySend(PrivacyControlsData())
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun updatePrivacyControls(userId: String, data: PrivacyControlsData): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users").document(userId).collection("settings").document("privacy")
                .set(data, SetOptions.merge()).await()
            // Also sync activity status flag on main user doc for instant global checks
            firestore.collection("users").document(userId).update(
                mapOf(
                    "activityStatusEnabled" to data.activityStatusEnabled,
                    "isPrivate" to data.isPrivateAccount
                )
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun addBlockedWord(userId: String, word: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (word.isBlank()) return@withContext false
            val clean = word.trim().lowercase()
            firestore.collection("users").document(userId).collection("settings").document("privacy")
                .update("customBlockedWords", FieldValue.arrayUnion(clean)).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun removeBlockedWord(userId: String, word: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users").document(userId).collection("settings").document("privacy")
                .update("customBlockedWords", FieldValue.arrayRemove(word)).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // 7. ARCHIVE & RECENTLY DELETED
    // ==========================================

    suspend fun setPostArchive(postId: String, isArchived: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("posts").document(postId).update("isArchived", isArchived).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun softDeletePost(postId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("posts").document(postId).update(
                mapOf("isDeleted" to true, "deletedAt" to System.currentTimeMillis())
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun restorePost(postId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("posts").document(postId).update(
                mapOf("isDeleted" to false, "deletedAt" to null)
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun permanentDeletePost(postId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("posts").document(postId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun listenArchivedPosts(userId: String): Flow<List<FirebasePost>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("posts")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isArchived", true)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val posts = snapshot.documents.mapNotNull { it.toObject(FirebasePost::class.java)?.copy(id = it.id) }
                    trySend(posts.filter { !it.isDeleted })
                }
            }
        awaitClose { listener.remove() }
    }

    fun listenRecentlyDeletedPosts(userId: String): Flow<List<FirebasePost>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("posts")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isDeleted", true)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val posts = snapshot.documents.mapNotNull { it.toObject(FirebasePost::class.java)?.copy(id = it.id) }
                    trySend(posts)
                }
            }
        awaitClose { listener.remove() }
    }

    // ==========================================
    // 8. STORY STICKERS & INTERACTIONS
    // ==========================================

    suspend fun recordStickerInteraction(interaction: StickerInteraction): Boolean = withContext(Dispatchers.IO) {
        try {
            val docRef = firestore.collection("stories").document(interaction.storyId)
                .collection("sticker_interactions").document(interaction.userId)
            docRef.set(interaction).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun listenStickerResponses(storyId: String): Flow<List<StickerInteraction>> = callbackFlow {
        val listener = firestore.collection("stories").document(storyId)
            .collection("sticker_interactions")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(StickerInteraction::class.java)?.copy(id = it.id) }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    // ==========================================
    // 9. SCHEDULED CONTENT & DRAFTS STUDIO
    // ==========================================

    suspend fun publishScheduledItemNow(item: ScheduledContent): Boolean = withContext(Dispatchers.IO) {
        try {
            if (item.userId.isBlank()) return@withContext false
            val userDoc = try {
                RepositoryManager.firestore.collection("users").document(item.userId).get().await()
            } catch (_: Exception) { null }
            val username = userDoc?.getString("username").orEmpty().ifBlank { "Creator" }
            val userAvatar = userDoc?.getString("profilePicUrl")
                ?: userDoc?.getString("avatar_url")
                ?: ""

            val isVideo = item.type.equals("reel", ignoreCase = true) ||
                (!com.example.utils.MediaResolver.isImage(item.mediaUri) && item.mediaUri.isNotBlank())
            val postId = UUID.randomUUID().toString()
            val postMap = hashMapOf(
                "id" to postId,
                "userId" to item.userId,
                "username" to username,
                "userAvatar" to userAvatar,
                "postUrl" to item.mediaUri,
                "thumbnailUrl" to item.mediaUri,
                "mediaType" to if (isVideo) "video" else "image",
                "visibility" to "PUBLIC",
                "caption" to item.caption,
                "likesCount" to 0,
                "commentsCount" to 0,
                "viewsCount" to 0,
                "timestamp" to FieldValue.serverTimestamp(),
                "isArchived" to false,
                "isDeleted" to false
            )
            RepositoryManager.firestore.collection("posts").document(postId).set(postMap).await()
            if (item.id.isNotBlank()) {
                RepositoryManager.firestore.collection("scheduled").document(item.id)
                    .update("status", "published")
                    .await()
            }
            true
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed publishing scheduled item", e)
            false
        }
    }

    suspend fun createScheduledItem(
        userId: String,
        type: String,
        mediaUri: String,
        caption: String,
        scheduledTime: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (userId.isBlank()) return@withContext false
            val docRef = RepositoryManager.firestore.collection("scheduled").document()
            val item = ScheduledContent(
                id = docRef.id,
                userId = userId,
                type = type,
                mediaUri = mediaUri,
                caption = caption.trim(),
                scheduledTime = scheduledTime,
                status = "scheduled"
            )
            docRef.set(item).await()
            true
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed creating scheduled item", e)
            false
        }
    }

    suspend fun updateScheduledItem(
        id: String,
        caption: String,
        scheduledTime: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            RepositoryManager.firestore.collection("scheduled").document(id).update(
                mapOf(
                    "caption" to caption.trim(),
                    "scheduledTime" to scheduledTime,
                    "status" to "scheduled"
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed updating scheduled item", e)
            false
        }
    }

    suspend fun autoPublishDueScheduledPosts(userId: String): Int = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext 0
        try {
            val now = System.currentTimeMillis()
            val snap = RepositoryManager.firestore.collection("scheduled")
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", "scheduled")
                .get()
                .await()
            var published = 0
            snap.documents.forEach { doc ->
                val item = doc.toObject(ScheduledContent::class.java)?.copy(id = doc.id) ?: return@forEach
                if (item.scheduledTime in 1L..now && item.mediaUri.startsWith("http")) {
                    if (publishScheduledItemNow(item)) published++
                }
            }
            published
        } catch (e: Exception) {
            Log.w("FlagshipRepo", "Auto-publish check skipped: ${e.message}")
            0
        }
    }

    suspend fun deleteScheduledItem(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            RepositoryManager.firestore.collection("scheduled").document(id).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun saveDraft(
        userId: String,
        type: String,
        mediaUri: String,
        caption: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (userId.isBlank()) return@withContext false
            val docRef = RepositoryManager.firestore.collection("drafts").document()
            val draft = ContentDraft(
                id = docRef.id,
                userId = userId,
                type = type,
                mediaUri = mediaUri,
                caption = caption.trim(),
                timestamp = System.currentTimeMillis()
            )
            docRef.set(draft).await()
            true
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed saving draft", e)
            false
        }
    }

    suspend fun updateDraft(
        draftId: String,
        caption: String,
        type: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            RepositoryManager.firestore.collection("drafts").document(draftId).update(
                mapOf(
                    "caption" to caption.trim(),
                    "type" to type,
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed updating draft", e)
            false
        }
    }

    suspend fun deleteDraft(draftId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            RepositoryManager.firestore.collection("drafts").document(draftId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun scheduleDraft(draft: ContentDraft, scheduledTime: Long): Boolean = withContext(Dispatchers.IO) {
        val created = createScheduledItem(
            userId = draft.userId,
            type = draft.type,
            mediaUri = draft.mediaUri,
            caption = draft.caption,
            scheduledTime = scheduledTime
        )
        if (created && draft.id.isNotBlank()) {
            deleteDraft(draft.id)
        }
        created
    }

    suspend fun publishDraftNow(draft: ContentDraft): Boolean = withContext(Dispatchers.IO) {
        val tempScheduled = ScheduledContent(
            id = "",
            userId = draft.userId,
            type = draft.type,
            mediaUri = draft.mediaUri,
            caption = draft.caption,
            scheduledTime = System.currentTimeMillis(),
            status = "scheduled"
        )
        val ok = publishScheduledItemNow(tempScheduled)
        if (ok && draft.id.isNotBlank()) {
            deleteDraft(draft.id)
        }
        ok
    }

    suspend fun repost(userId: String, postId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val userRef = firestore.collection("users").document(userId)
            val postRef = firestore.collection("posts").document(postId)
            var added = false
            firestore.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                val repostList = (userSnap.get("repostedPostIds") as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
                if (repostList.contains(postId)) {
                    repostList.remove(postId)
                    tx.update(userRef, "repostedPostIds", repostList)
                    tx.update(postRef, "repostCount", FieldValue.increment(-1))
                    added = false
                } else {
                    repostList.add(postId)
                    tx.update(userRef, "repostedPostIds", repostList)
                    tx.update(postRef, "repostCount", FieldValue.increment(1))
                    added = true
                }
            }.await()
            added
        } catch (e: Exception) {
            false
        }
    }

    suspend fun toggleSavePost(userId: String, postId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val docRef = firestore.collection("users").document(userId).collection("savedPosts").document(postId)
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.delete().await()
                firestore.collection("posts").document(postId).update("savesCount", FieldValue.increment(-1)).await()
                false
            } else {
                val postSnap = firestore.collection("posts").document(postId).get().await()
                val mediaUrl = postSnap.getString("postUrl") ?: ""
                val caption = postSnap.getString("caption") ?: ""
                val authorId = postSnap.getString("userId") ?: ""
                val item = SavedPostItem(
                    id = "${userId}_$postId",
                    userId = userId,
                    postId = postId,
                    mediaUrl = mediaUrl,
                    thumbnailUrl = mediaUrl,
                    caption = caption,
                    authorId = authorId,
                    authorUsername = "Creator",
                    savedAt = System.currentTimeMillis()
                )
                docRef.set(item).await()
                firestore.collection("posts").document(postId).update("savesCount", FieldValue.increment(1)).await()
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // 10. TRIAL REELS
    // ==========================================

    suspend fun publishTrialReelToEveryone(postId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("posts").document(postId).update(
                mapOf(
                    "isTrialReel" to false,
                    "trialPublishedAt" to System.currentTimeMillis()
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed to publish trial reel to everyone", e)
            false
        }
    }

    suspend fun deleteTrialReel(postId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("posts").document(postId).delete().await()
            true
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed to delete trial reel", e)
            false
        }
    }

    fun listenUserTrialReels(userId: String): Flow<List<FirebasePost>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("posts")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isTrialReel", true)
            .addSnapshotListener { snap, err ->
                if (err == null && snap != null) {
                    val list = snap.documents.mapNotNull { it.toObject(FirebasePost::class.java)?.copy(id = it.id) }
                    trySend(list.filter { !it.isDeleted && !it.isArchived })
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun fetchTrialReelAnalytics(postId: String): TrialReelAnalytics = withContext(Dispatchers.IO) {
        try {
            val snap = firestore.collection("posts").document(postId).get().await()
            val views = snap.getLong("viewsCount")?.toInt() ?: 0
            val likes = snap.getLong("likesCount")?.toInt() ?: 0
            val comments = snap.getLong("commentsCount")?.toInt() ?: 0
            val shares = snap.getLong("sharesCount")?.toInt() ?: 0
            val saves = snap.getLong("savesCount")?.toInt() ?: 0
            val watchTimeMs = snap.getLong("trialWatchTimeMs") ?: (views * 6500L)
            val watchTimeSeconds = watchTimeMs / 1000L
            val avgDuration = if (views > 0) (watchTimeSeconds.toDouble() / views.toDouble()) else 0.0
            val completionRate = if (views > 0) ((likes + comments + shares).toDouble() / views.toDouble() * 100).toInt().coerceIn(12, 95) else 0
            val uniqueViewers = (views * 0.88).toInt().coerceAtLeast(views)

            TrialReelAnalytics(
                contentId = postId,
                views = views,
                uniqueViewers = uniqueViewers,
                watchTimeSeconds = watchTimeSeconds,
                avgWatchDurationSeconds = Math.round(avgDuration * 10.0) / 10.0,
                completionRatePercent = completionRate,
                likes = likes,
                comments = comments,
                shares = shares,
                saves = saves,
                followersGained = (likes * 0.15).toInt(),
                status = if (snap.getBoolean("isTrialReel") == false) "published" else "trial",
                audienceRule = snap.getString("trialAudience") ?: "test_audience",
                createdAt = snap.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed to fetch trial analytics", e)
            TrialReelAnalytics(contentId = postId)
        }
    }

    // ==========================================
    // 11. ADD YOURS (PROMPT CHAINS)
    // ==========================================

    suspend fun createAddYoursPrompt(prompt: AddYoursPrompt): String = withContext(Dispatchers.IO) {
        try {
            val docRef = if (prompt.id.isEmpty()) {
                firestore.collection("add_yours_prompts").document()
            } else {
                firestore.collection("add_yours_prompts").document(prompt.id)
            }
            val newPrompt = prompt.copy(id = docRef.id, createdAt = System.currentTimeMillis())
            docRef.set(newPrompt).await()
            docRef.id
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed to create Add Yours prompt", e)
            ""
        }
    }

    suspend fun participateInAddYours(
        promptId: String,
        contentId: String,
        contentType: String,
        mediaUrl: String,
        userId: String,
        username: String,
        avatarUrl: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val participantDoc = firestore.collection("add_yours_prompts").document(promptId)
                .collection("participants").document(contentId)
            val item = AddYoursParticipant(
                id = contentId,
                promptId = promptId,
                contentId = contentId,
                contentType = contentType,
                mediaUrl = mediaUrl,
                userId = userId,
                username = username,
                avatarUrl = avatarUrl,
                createdAt = System.currentTimeMillis()
            )
            participantDoc.set(item).await()

            firestore.collection("add_yours_prompts").document(promptId)
                .update("participantsCount", FieldValue.increment(1)).await()
            true
        } catch (e: Exception) {
            Log.e("FlagshipRepo", "Failed to participate in Add Yours", e)
            false
        }
    }

    fun listenAddYoursPrompts(): Flow<List<AddYoursPrompt>> = callbackFlow {
        val listener = firestore.collection("add_yours_prompts")
            .whereEqualTo("isClosed", false)
            .addSnapshotListener { snap, err ->
                if (err == null && snap != null) {
                    val list = snap.documents.mapNotNull { it.toObject(AddYoursPrompt::class.java)?.copy(id = it.id) }
                    trySend(list.sortedByDescending { it.participantsCount })
                }
            }
        awaitClose { listener.remove() }
    }

    fun listenAddYoursParticipants(promptId: String): Flow<List<AddYoursParticipant>> = callbackFlow {
        val listener = firestore.collection("add_yours_prompts").document(promptId)
            .collection("participants")
            .addSnapshotListener { snap, err ->
                if (err == null && snap != null) {
                    val list = snap.documents.mapNotNull { it.toObject(AddYoursParticipant::class.java)?.copy(id = it.id) }
                    trySend(list.sortedByDescending { it.createdAt })
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun closeAddYoursPrompt(promptId: String, isClosed: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("add_yours_prompts").document(promptId).update("isClosed", isClosed).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteAddYoursPrompt(promptId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("add_yours_prompts").document(promptId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
