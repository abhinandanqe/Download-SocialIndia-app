package com.example.model

import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.serialization.Serializable

@Serializable
@IgnoreExtraProperties
data class Mention(
    val uid: String = "",
    val username: String = "",
    val start: Int = 0,
    val end: Int = 0
)

@Serializable
@IgnoreExtraProperties
data class MentionDoc(
    val mentionId: String = "",
    val mentionedUserId: String = "",
    val mentionedUsername: String = "",
    val mentionedByUserId: String = "",
    val mentionedByUsername: String = "",
    val mentionedByAvatar: String = "",
    val mentionedByDisplayName: String = "",
    val contentType: String = "post", // "post", "reel", "photo", "video", "story", "comment", "message"
    val contentId: String = "",
    val parentContentId: String = "",
    val sourceContext: String = "caption", // "caption", "picker", "media_tag", "sticker", "comment", "message"
    val mentionText: String = "",
    val mediaUrl: String = "",
    val xRatio: Float? = null,
    val yRatio: Float? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val visibility: String = "public", // "public", "followers", "private"
    val status: String = "active" // "active", "hidden_by_user", "removed_by_user"
)

@Serializable
@IgnoreExtraProperties
data class MentionTagLocation(
    val userId: String = "",
    val username: String = "",
    val xRatio: Float = 0.5f,
    val yRatio: Float = 0.5f
)
