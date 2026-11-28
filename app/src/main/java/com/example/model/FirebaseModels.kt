package com.example.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@Stable
data class FirebasePost(
    val id: String = "",
    val userId: String = "",
    val postUrl: String = "",
    val thumbnailUrl: String? = null,
    val mediaType: String? = null,
    val caption: String = "",
    val likesCount: Int = 0,
    val viewsCount: Int = 0,
    val commentsCount: Int = 0,
    val savesCount: Int = 0,
    val reportsCount: Int = 0,
    val sharesCount: Int = 0,
    val repostsCount: Int = 0,
    @ServerTimestamp
    val timestamp: Date? = null,
    val musicTrack: String? = null,
    val musicTrackUrl: String? = null,
    val musicTrackImageUrl: String? = null,
    val musicTrackId: String? = null,
    val musicTrackArtist: String? = null,
    val postUrls: List<String> = emptyList(),
    val collaboratorIds: List<String> = emptyList(),
    val pendingCollaboratorIds: List<String> = emptyList(),
    val repostedByUserIds: List<String> = emptyList(),
    val repostThoughts: Map<String, String> = emptyMap(),
    val isSubscribersOnly: Boolean = false, val premiumPrice: Double = 0.0, val isPinned: Boolean = false,
    val isPaidPost: Boolean = false,
    val postUnlockPrice: Double = 0.0,
    val accessType: String = "PUBLIC",
    val isExclusive: Boolean = false,
    val blurredThumbnailUrl: String = "",
    val teaserCaption: String = "",
    val hasVaultMedia: Boolean = false,
    val unlockCount: Int = 0,
    val exclusiveRevenue: Double = 0.0,
    val isRemovedByAdmin: Boolean = false,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val isTrialReel: Boolean = false,
    val trialAudience: String = "test_audience",
    val trialWatchTimeMs: Long = 0L,
    val addYoursPromptId: String? = null,
    val addYoursPromptTitle: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val aspectRatio: Float = 0f,
    val username: String? = null,
    val userAvatarUrl: String? = null,
    val userProfilePic: String? = null,
    val userDisplayName: String? = null,
    val isUserVerified: Boolean = false,
    val userVerificationBadgeUrl: String? = null,
    val visibility: String = "PUBLIC",
    val createdAtMillis: Long = 0L,
    val likedByUserIds: List<String> = emptyList(),
    val likes: Map<String, Boolean> = emptyMap()
)

@Stable
data class Comment(
    val id: String = "",
    val videoId: String = "",
    val userId: String = "",
    val text: String = "",
    val gifUrl: String? = null,
    val audioUrl: String? = null,
    val audioDurationSec: Int = 0,
    val username: String = "",
    val avatarUrl: String = "",
    @get:com.google.firebase.firestore.PropertyName("isVerified")
    val isVerified: Boolean = false,
    val verificationBadgeUrl: String = "",
    @ServerTimestamp
    val timestamp: Date? = null,
    val parentCommentId: String? = null,
    val parentUsername: String? = null,
    val likedBy: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isEdited: Boolean = false,
    val type: String = "TEXT",
    val stickerId: String? = null,
    val packId: String? = null,
    val driveFileId: String? = null,
    val thumbnailDriveFileId: String? = null,
    val isAnimated: Boolean = false
)

@Stable
data class Story(
    val id: String = "",
    val userId: String = "",
    val mediaUrl: String = "",
    @ServerTimestamp
    val timestamp: Date? = null,
    val expireAt: Date? = null,
    val expiryTime: Long = 0,
    val viewers: List<String> = emptyList(),
    val viewCount: Int = 0,
    val likesCount: Int = 0,
    val viewsCount: Int = 0,
    val commentsCount: Int = 0,
    val textOverlay: String = "",
    val mentionedUserId: String? = null,
    val mentionedUsername: String? = null,
    val isReback: Boolean = false,
    val musicTrackName: String? = null,
    val musicTrackArtist: String? = null,
    val musicTrackUrl: String? = null,
    val isLive: Boolean = false,
    val isPremium: Boolean = false,
    val premiumPrice: Double = 0.0, val isPinned: Boolean = false,
    val isExclusiveContent: Boolean = false,
    val exclusivePrice: Double = 0.0,
    val teaserCaption: String = "",
    val blurredThumbnailUrl: String = "",
    val hasVaultMedia: Boolean = false,
    val unlockCount: Int = 0,
    val exclusiveRevenue: Double = 0.0,
    val isRemovedByAdmin: Boolean = false,
    val repostedPostId: String? = null,
    val isCloseFriendsOnly: Boolean = false,
    val stickersJson: String = "",
    val addYoursPromptId: String? = null,
    val addYoursPromptTitle: String? = null,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)

@Stable
data class Conversation(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val lastMessage: String = "",
    @ServerTimestamp
    val lastMessageTimestamp: Date? = null,
    val unreadCountMap: Map<String, Int> = emptyMap(),
    val isGroup: Boolean = false,
    val groupName: String? = null,
    val groupOwnerId: String? = null,
    val groupAvatarUrl: String? = null
)

@Immutable
data class FirebaseMessage(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val mediaUrl: String? = null,
    @ServerTimestamp
    val timestamp: Date? = null,
    val isRead: Boolean = false
)


data class GiftTransaction(
    val id: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val senderAvatar: String = "",
    val receiverId: String = "",
    val giftId: String = "",
    val giftName: String = "",
    val emoji: String = "🎁",
    val sticker3dUrl: String = "",
    val coins: Int = 0,
    val liveId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class CreatorSubscription(
    val id: String = "",
    val subscriberId: String = "",
    val creatorId: String = "",
    val active: Boolean = true,
    val startDate: Long = System.currentTimeMillis(),
    val expiresAt: Long = 0L,
    val tier: String = "Basic"
)

data class ContentDraft(
    val id: String = "",
    val userId: String = "",
    val type: String = "post", // post, reel, story
    val mediaUri: String = "",
    val caption: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class ScheduledContent(
    val id: String = "",
    val userId: String = "",
    val type: String = "post",
    val mediaUri: String = "",
    val caption: String = "",
    val scheduledTime: Long = 0L,
    val status: String = "scheduled" // scheduled, published, failed, cancelled
)
