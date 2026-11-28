package com.example.notification.model

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Next-Generation Notification Data Model for Social India.
 * Fully UID-scoped, supports smart grouping, idempotency, and rich media deep linking.
 */
data class SocialIndiaNotification(
    val id: String = "",
    val recipientUid: String = "",
    val actorUid: String = "",
    val actorUsername: String = "",
    val actorDisplayName: String = "",
    val actorPhoto: String = "",
    val actorIsVerified: Boolean = false,
    val type: String = "",
    val targetId: String? = null,
    val targetType: String? = null,
    val text: String = "",
    val mediaUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val read: Boolean = false,
    val groupKey: String? = null,
    val deepLink: String = "",
    val metadata: Map<String, Any> = emptyMap(),
    // Transient fields for grouped presentation
    val groupedActors: List<NotificationActor> = emptyList(),
    val totalGroupCount: Int = 1
)

data class NotificationActor(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val isVerified: Boolean = false
)

enum class NotificationFilter(val label: String, val types: List<String>) {
    ALL("All", emptyList()),
    LIKES("Likes", listOf("like", "story_like", "reel_like")),
    COMMENTS("Comments", listOf("comment", "reply", "story_reply")),
    MENTIONS("Mentions", listOf("mention", "story_mention")),
    FOLLOWS("Follows", listOf("follow", "follow_request", "follow_accepted")),
    MESSAGES("Messages", listOf("message", "group_message", "group_invite")),
    LIVE("Live", listOf("live_start", "live_invite", "live_gift")),
    CREATOR("Creator", listOf(
        "creator_milestone", "monetization", "content_processed",
        "exclusive_request", "exclusive_pending", "exclusive_approved",
        "exclusive_rejected", "exclusive_refunded", "exclusive_unlock", "payment_success"
    )),
    SECURITY("Security", listOf("security_alert", "new_login", "password_change")),
    SYSTEM("System", listOf("system", "verification", "announcement"))
}

object NotificationMapper {

    fun fromDocument(doc: DocumentSnapshot): SocialIndiaNotification? {
        val data = doc.data ?: return null
        val id = doc.id
        val recipientUid = data["recipientUid"]?.toString() 
            ?: data["recipientId"]?.toString() 
            ?: ""
        val actorUid = data["actorUid"]?.toString() 
            ?: data["actorId"]?.toString() 
            ?: data["senderId"]?.toString() 
            ?: ""
        val actorUsername = data["actorUsername"]?.toString() 
            ?: data["senderName"]?.toString() 
            ?: data["senderUsername"]?.toString() 
            ?: "User"
        val actorDisplayName = data["actorDisplayName"]?.toString() 
            ?: data["displayName"]?.toString() 
            ?: actorUsername
        val actorPhoto = data["actorPhoto"]?.toString() 
            ?: data["actorAvatar"]?.toString() 
            ?: data["senderProfilePic"]?.toString() 
            ?: data["avatarUrl"]?.toString() 
            ?: ""
        val actorIsVerified = data["actorIsVerified"] as? Boolean 
            ?: data["isVerified"] as? Boolean 
            ?: false

        val rawType = data["type"]?.toString()?.lowercase() ?: "general"
        val targetId = data["targetId"]?.toString() 
            ?: data["postId"]?.toString() 
            ?: data["storyId"]?.toString() 
            ?: data["channelId"]?.toString()
        val targetType = data["targetType"]?.toString()
        val text = data["text"]?.toString() 
            ?: data["message"]?.toString() 
            ?: data["content"]?.toString() 
            ?: ""
        val mediaUrl = data["mediaUrl"]?.toString() 
            ?: data["postThumbnail"]?.toString() 
            ?: data["thumbnailUrl"]?.toString()

        val createdAt = when (val ts = data["createdAt"] ?: data["timestamp"]) {
            is com.google.firebase.Timestamp -> ts.toDate().time
            is Date -> ts.time
            is Number -> ts.toLong()
            is String -> ts.toLongOrNull() ?: System.currentTimeMillis()
            else -> System.currentTimeMillis()
        }

        val read = data["read"] as? Boolean 
            ?: data["isRead"] as? Boolean 
            ?: false

        val groupKey = data["groupKey"]?.toString() 
            ?: if (!targetId.isNullOrEmpty()) "${rawType}_$targetId" else null

        val deepLink = data["deepLink"]?.toString() ?: computeDeepLink(rawType, targetId, actorUid)

        @Suppress("UNCHECKED_CAST")
        val metadata = (data["metadata"] as? Map<String, Any>) ?: emptyMap()

        return SocialIndiaNotification(
            id = id,
            recipientUid = recipientUid,
            actorUid = actorUid,
            actorUsername = actorUsername,
            actorDisplayName = actorDisplayName,
            actorPhoto = actorPhoto,
            actorIsVerified = actorIsVerified,
            type = rawType,
            targetId = targetId,
            targetType = targetType,
            text = text,
            mediaUrl = mediaUrl,
            createdAt = createdAt,
            read = read,
            groupKey = groupKey,
            deepLink = deepLink,
            metadata = metadata
        )
    }

    private fun computeDeepLink(type: String, targetId: String?, actorUid: String): String {
        return when (type) {
            "like", "comment", "reply", "post_interaction", "repost", "share" -> {
                if (!targetId.isNullOrEmpty()) "post_detail/$targetId" else "home"
            }
            "reel_like", "reel_interaction" -> {
                if (!targetId.isNullOrEmpty()) "post_detail/$targetId" else "reels"
            }
            "follow", "follow_request", "follow_accepted" -> {
                if (actorUid.isNotEmpty()) "profile/$actorUid" else "home"
            }
            "message" -> {
                if (actorUid.isNotEmpty()) "inbox_chat/$actorUid" else "inbox/messages"
            }
            "group_message", "group_invite" -> {
                if (!targetId.isNullOrEmpty()) "broadcast_chat/$targetId" else "inbox/groups"
            }
            "live_start", "live_invite" -> {
                if (!targetId.isNullOrEmpty()) "live_viewer/$targetId" else "home"
            }
            "story_mention", "story_reply", "story_like" -> {
                "home"
            }
            "security_alert", "new_login" -> {
                "privacy_settings"
            }
            "creator_milestone", "monetization" -> {
                "professional_dashboard"
            }
            "exclusive_request" -> {
                "exclusive_requests"
            }
            "exclusive_pending", "exclusive_rejected", "exclusive_refunded", "payment_success" -> {
                "wallet"
            }
            "exclusive_approved", "exclusive_unlock" -> {
                if (!targetId.isNullOrEmpty()) "post_detail/$targetId" else "home"
            }
            else -> {
                if (!targetId.isNullOrEmpty()) "post_detail/$targetId"
                else if (actorUid.isNotEmpty()) "profile/$actorUid"
                else "home"
            }
        }
    }
}
