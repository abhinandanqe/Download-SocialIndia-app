package com.example.repository

import android.util.Log
import com.example.model.GroupChat
import com.example.model.GroupMessage
import com.example.model.GroupPoll
import com.example.model.RepositoryManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

object GroupChatRepository {
    private const val TAG = "GroupChatRepository"
    private val firestore get() = RepositoryManager.firestore

    /**
     * Create a new group chat in Firestore with creator as Owner/Admin and initial system message.
     */
    suspend fun createGroup(
        name: String,
        description: String,
        avatarUrl: String,
        memberIds: List<String>,
        creatorId: String,
        creatorName: String,
        onlyAdminsCanPost: Boolean = false
    ): String {
        val groupId = "grp_${UUID.randomUUID().toString().take(12)}"
        val allMembers = (memberIds + creatorId).distinct()
        val allAdmins = listOf(creatorId)
        val finalAvatar = avatarUrl.trim()

        val group = GroupChat(
            id = groupId,
            name = name.trim(),
            description = description.trim(),
            avatarUrl = finalAvatar,
            createdBy = creatorId,
            createdAt = System.currentTimeMillis(),
            members = allMembers,
            admins = allAdmins,
            onlyAdminsCanPost = onlyAdminsCanPost,
            onlyAdminsCanEditGroup = true,
            lastMessage = "$creatorName created the group",
            lastMessageTime = System.currentTimeMillis(),
            lastMessageSender = "System"
        )

        val groupRef = firestore.collection("groups").document(groupId)
        groupRef.set(group).await()

        // Create welcome system message
        val welcomeMessageId = "msg_${UUID.randomUUID().toString().take(12)}"
        val systemMessage = GroupMessage(
            id = welcomeMessageId,
            groupId = groupId,
            senderId = "system",
            senderName = "System",
            message = "$creatorName created the group \"${name.trim()}\"",
            type = "system",
            timestamp = System.currentTimeMillis()
        )
        groupRef.collection("messages").document(welcomeMessageId).set(systemMessage).await()

        return groupId
    }

    /**
     * Real-time flow of all groups where the user is a member.
     */
    fun listenUserGroups(userId: String): Flow<List<GroupChat>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener: ListenerRegistration = firestore.collection("groups")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to user groups", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val groups = snapshot.documents.mapNotNull { doc ->
                        try {
                            val grp = doc.toObject(GroupChat::class.java)?.copy(id = doc.id)
                            if (grp != null && grp.members.contains(userId)) {
                                grp
                            } else null
                        } catch (e: Exception) {
                            Log.w(TAG, "Error mapping group doc ${doc.id}", e)
                            null
                        }
                    }.sortedByDescending { it.lastMessageTime }

                    trySend(groups)
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Real-time flow of a single group's metadata.
     */
    fun listenGroupDetails(groupId: String): Flow<GroupChat?> = callbackFlow {
        val listener = firestore.collection("groups").document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                try {
                    val grp = snapshot.toObject(GroupChat::class.java)?.copy(id = snapshot.id)
                    trySend(grp)
                } catch (e: Exception) {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Real-time flow of all messages in a group.
     */
    fun listenGroupMessages(groupId: String): Flow<List<GroupMessage>> = callbackFlow {
        val listener = firestore.collection("groups")
            .document(groupId)
            .collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to group messages", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(GroupMessage::class.java)?.copy(id = doc.id)
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedBy { it.timestamp }

                    trySend(messages)
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Send a message to a group (text, media, gif, voice, poll, announcement).
     */
    suspend fun sendGroupMessage(groupId: String, message: GroupMessage) {
        val msgId = message.id.ifEmpty { "msg_${UUID.randomUUID().toString().take(12)}" }
        val finalMessage = message.copy(
            id = msgId,
            groupId = groupId,
            timestamp = if (message.timestamp > 0) message.timestamp else System.currentTimeMillis()
        )

        val groupRef = firestore.collection("groups").document(groupId)
        val messageRef = groupRef.collection("messages").document(msgId)

        messageRef.set(finalMessage).await()

        val snippet = when (finalMessage.type) {
            "image" -> "📷 Photo"
            "video" -> "🎥 Video"
            "gif" -> "🎭 GIF"
            "voice" -> "🎤 Voice Message"
            "poll" -> "📊 Poll: ${finalMessage.poll?.question ?: "New poll"}"
            "announcement" -> "📢 Announcement: ${finalMessage.message.take(40)}"
            else -> finalMessage.message
        }

        val updateMap = hashMapOf<String, Any>(
            "lastMessage" to snippet,
            "lastMessageTime" to finalMessage.timestamp,
            "lastMessageSender" to finalMessage.senderName
        )
        if (finalMessage.type == "announcement") {
            updateMap["announcementCount"] = FieldValue.increment(1)
        }

        groupRef.set(updateMap, SetOptions.merge()).await()
    }

    /**
     * Cast or toggle vote on a poll.
     */
    suspend fun votePoll(groupId: String, messageId: String, optionId: Int, userId: String) {
        val messageRef = firestore.collection("groups").document(groupId)
            .collection("messages").document(messageId)

        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(messageRef)
                val msg = snapshot.toObject(GroupMessage::class.java) ?: return@runTransaction null
                val poll = msg.poll ?: return@runTransaction null
                if (poll.isClosed) return@runTransaction null

                val updatedOptions = poll.options.map { option ->
                    val voters = option.votes.toMutableList()
                    if (option.id == optionId) {
                        if (voters.contains(userId)) {
                            voters.remove(userId)
                        } else {
                            voters.add(userId)
                        }
                    } else if (!poll.multipleChoice) {
                        voters.remove(userId)
                    }
                    option.copy(votes = voters)
                }

                val updatedPoll = poll.copy(options = updatedOptions)
                val updatedMsg = msg.copy(poll = updatedPoll)
                transaction.set(messageRef, updatedMsg)
                null
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vote in poll", e)
        }
    }

    /**
     * Mark unread messages as read by current user.
     */
    suspend fun markMessagesAsRead(groupId: String, userId: String, messages: List<GroupMessage>) {
        val unread = messages.filter { !it.readBy.contains(userId) && it.senderId != userId }
        if (unread.isEmpty()) return

        for (msg in unread) {
            try {
                firestore.collection("groups").document(groupId)
                    .collection("messages").document(msg.id)
                    .update("readBy", FieldValue.arrayUnion(userId))
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Toggle or set reaction on a group message.
     */
    suspend fun reactToGroupMessage(groupId: String, messageId: String, userId: String, emoji: String) {
        val msgRef = firestore.collection("groups").document(groupId)
            .collection("messages").document(messageId)
        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(msgRef)
                val msg = snapshot.toObject(GroupMessage::class.java) ?: return@runTransaction null
                val updatedReactions = msg.reactions.toMutableMap()
                if (updatedReactions[userId] == emoji) {
                    updatedReactions.remove(userId)
                } else {
                    updatedReactions[userId] = emoji
                }
                transaction.update(msgRef, "reactions", updatedReactions)
                null
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to react to group message", e)
        }
    }

    /**
     * Set typing status for user in group.
     */
    suspend fun setTypingStatus(groupId: String, userId: String, username: String, isTyping: Boolean) {
        val typingDoc = firestore.collection("groups").document(groupId)
            .collection("typing").document(userId)

        try {
            if (isTyping) {
                typingDoc.set(
                    mapOf(
                        "userId" to userId,
                        "username" to username,
                        "timestamp" to System.currentTimeMillis()
                    )
                ).await()
            } else {
                typingDoc.delete().await()
            }
        } catch (ignored: Exception) {}
    }

    /**
     * Listen to other members who are currently typing in this group.
     */
    fun listenTypingStatus(groupId: String, currentUserId: String): Flow<List<String>> = callbackFlow {
        val listener = firestore.collection("groups").document(groupId)
            .collection("typing")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val now = System.currentTimeMillis()
                val typingUsers = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.getString("userId") ?: doc.id
                    val time = doc.getLong("timestamp") ?: 0L
                    val name = doc.getString("username") ?: "Someone"
                    // Active if within last 4 seconds and not self
                    if (uid != currentUserId && (now - time) < 4000L) {
                        name
                    } else null
                }
                trySend(typingUsers)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Add new members to the group.
     */
    suspend fun addMembers(groupId: String, newMemberIds: List<String>, actorName: String) {
        val groupRef = firestore.collection("groups").document(groupId)
        groupRef.update("members", FieldValue.arrayUnion(*newMemberIds.toTypedArray())).await()

        val sysMsgId = "msg_${UUID.randomUUID().toString().take(12)}"
        val sysMsg = GroupMessage(
            id = sysMsgId,
            groupId = groupId,
            senderId = "system",
            senderName = "System",
            message = "$actorName added ${newMemberIds.size} new member(s)",
            type = "system",
            timestamp = System.currentTimeMillis()
        )
        groupRef.collection("messages").document(sysMsgId).set(sysMsg).await()
    }

    /**
     * Remove a member from the group (Admin only).
     */
    suspend fun removeMember(groupId: String, memberId: String, actorName: String, memberName: String) {
        val groupRef = firestore.collection("groups").document(groupId)
        groupRef.update(
            "members", FieldValue.arrayRemove(memberId),
            "admins", FieldValue.arrayRemove(memberId)
        ).await()

        val sysMsgId = "msg_${UUID.randomUUID().toString().take(12)}"
        val sysMsg = GroupMessage(
            id = sysMsgId,
            groupId = groupId,
            senderId = "system",
            senderName = "System",
            message = "$actorName removed $memberName from the group",
            type = "system",
            timestamp = System.currentTimeMillis()
        )
        groupRef.collection("messages").document(sysMsgId).set(sysMsg).await()
    }

    /**
     * Promote a member to Admin.
     */
    suspend fun promoteToAdmin(groupId: String, memberId: String, actorName: String, memberName: String) {
        val groupRef = firestore.collection("groups").document(groupId)
        groupRef.update("admins", FieldValue.arrayUnion(memberId)).await()

        val sysMsgId = "msg_${UUID.randomUUID().toString().take(12)}"
        val sysMsg = GroupMessage(
            id = sysMsgId,
            groupId = groupId,
            senderId = "system",
            senderName = "System",
            message = "$actorName promoted $memberName to Admin",
            type = "system",
            timestamp = System.currentTimeMillis()
        )
        groupRef.collection("messages").document(sysMsgId).set(sysMsg).await()
    }

    /**
     * Demote an Admin to regular member.
     */
    suspend fun demoteAdmin(groupId: String, memberId: String, actorName: String, memberName: String) {
        val groupRef = firestore.collection("groups").document(groupId)
        groupRef.update("admins", FieldValue.arrayRemove(memberId)).await()

        val sysMsgId = "msg_${UUID.randomUUID().toString().take(12)}"
        val sysMsg = GroupMessage(
            id = sysMsgId,
            groupId = groupId,
            senderId = "system",
            senderName = "System",
            message = "$actorName removed admin privileges from $memberName",
            type = "system",
            timestamp = System.currentTimeMillis()
        )
        groupRef.collection("messages").document(sysMsgId).set(sysMsg).await()
    }

    /**
     * Member leaves the group.
     */
    suspend fun leaveGroup(groupId: String, userId: String, userName: String) {
        val groupRef = firestore.collection("groups").document(groupId)
        groupRef.update(
            "members", FieldValue.arrayRemove(userId),
            "admins", FieldValue.arrayRemove(userId)
        ).await()

        val sysMsgId = "msg_${UUID.randomUUID().toString().take(12)}"
        val sysMsg = GroupMessage(
            id = sysMsgId,
            groupId = groupId,
            senderId = "system",
            senderName = "System",
            message = "$userName left the group",
            type = "system",
            timestamp = System.currentTimeMillis()
        )
        groupRef.collection("messages").document(sysMsgId).set(sysMsg).await()
    }

    /**
     * Update group name, description, avatar.
     */
    suspend fun updateGroupInfo(groupId: String, name: String, description: String, avatarUrl: String) {
        firestore.collection("groups").document(groupId).update(
            mapOf(
                "name" to name.trim(),
                "description" to description.trim(),
                "avatarUrl" to avatarUrl.trim()
            )
        ).await()
    }

    /**
     * Update granular group permissions (e.g. only admins can post, edit info).
     */
    suspend fun updateGroupPermissions(
        groupId: String,
        onlyAdminsCanPost: Boolean,
        onlyAdminsCanEditGroup: Boolean
    ) {
        firestore.collection("groups").document(groupId).update(
            mapOf(
                "onlyAdminsCanPost" to onlyAdminsCanPost,
                "onlyAdminsCanEditGroup" to onlyAdminsCanEditGroup
            )
        ).await()
    }

    /**
     * Delete the entire group (Creator only).
     */
    suspend fun deleteGroup(groupId: String) {
        firestore.collection("groups").document(groupId).delete().await()
    }
}
