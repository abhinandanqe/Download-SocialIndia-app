package com.example.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class GroupChat(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val avatarUrl: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val members: List<String> = emptyList(),
    val admins: List<String> = emptyList(),
    val onlyAdminsCanPost: Boolean = false,
    val onlyAdminsCanEditGroup: Boolean = true,
    val lastMessage: String = "",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val lastMessageSender: String = "",
    val announcementCount: Int = 0
)

@IgnoreExtraProperties
data class GroupMessage(
    val id: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val senderName: String = "User",
    val senderAvatar: String = "",
    val message: String = "",
    val type: String = "text", // "text", "image", "video", "gif", "voice", "poll", "announcement", "system"
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val videoDurationSeconds: Int = 0,
    val voiceDurationSeconds: Int = 0,
    val poll: GroupPoll? = null,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSender: String? = null,
    val reactions: Map<String, String> = emptyMap(), // userId -> emoji
    val readBy: List<String> = emptyList(),
    val status: String = "sent", // "sending", "sent", "failed"
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val driveFileId: String? = null,
    val thumbnailDriveFileId: String? = null,
    val stickerId: String? = null,
    val packId: String? = null,
    val isAnimated: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class GroupPoll(
    val id: String = "",
    val question: String = "",
    val options: List<GroupPollOption> = emptyList(),
    val multipleChoice: Boolean = false,
    val createdBy: String = "",
    val isClosed: Boolean = false
)

@IgnoreExtraProperties
data class GroupPollOption(
    val id: Int = 0,
    val text: String = "",
    val votes: List<String> = emptyList(),
    val voters: List<String> = emptyList()
)
