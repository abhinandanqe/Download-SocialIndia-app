package com.example.model

import com.google.firebase.firestore.ServerTimestamp
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class BroadcastChannel(
    val id: String = "",
    val creatorId: String = "",
    val creatorUsername: String = "",
    val creatorAvatarUrl: String = "",
    val title: String = "",
    val username: String = "", // @handle
    val description: String = "",
    val imageUrl: String = "",
    val category: String = "Creator", // Technology, Entertainment, Creator, Music, Gaming, News, Lifestyle, Art, Education
    val visibilityType: String = "PUBLIC", // "PUBLIC" or "FOLLOWERS_ONLY"
    val visibility: String = "public", // "public", "followers", "subscribers"
    val whoCanJoin: String = "everyone", // "everyone", "followers", "close_friends", "subscribers"
    val notificationPreference: String = "all", // "all", "some", "mute"
    val rules: String = "",
    val membersCount: Int = 0,
    val pinnedMessageId: String? = null,
    val pinnedMessageText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isVerified: Boolean = false,
    val welcomeMessage: String = ""
) {
    val isFollowersOnly: Boolean
        get() = visibilityType.equals("FOLLOWERS_ONLY", ignoreCase = true) ||
                visibility.equals("followers", ignoreCase = true) ||
                whoCanJoin.equals("followers", ignoreCase = true)
}

@Serializable
data class ChannelMember(
    val userId: String = "",
    val channelId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val joinedAt: Long = System.currentTimeMillis(),
    val isMuted: Boolean = false,
    val notificationPreference: String = "all", // "all", "mute"
    val isOwner: Boolean = false,
    val lastReadTimestamp: Long = 0L
)

@Serializable
data class ChannelMessage(
    val id: String = "",
    val channelId: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val senderAvatarUrl: String = "",
    val text: String = "",
    val mediaUrl: String? = null,
    val mediaType: String? = null, // "photo", "video", "voice", "music"
    val voiceDurationSeconds: Int = 0,
    val musicTrackId: String? = null,
    val musicTrackName: String? = null,
    val musicTrackArtist: String? = null,
    val musicTrackArtwork: String? = null,
    val musicTrackUrl: String? = null,
    val sharedPostId: String? = null,
    val sharedPostThumbnail: String? = null,
    val sharedPostCaption: String? = null,
    val sharedPostUsername: String? = null,
    val sharedReelId: String? = null,
    val sharedStoryId: String? = null,
    val isPinned: Boolean = false,
    val isAnnouncement: Boolean = false,
    val viewsCount: Int = 0,
    val pollQuestion: String? = null,
    val pollOptions: List<String> = emptyList(),
    val pollVotes: Map<String, Int> = emptyMap(), // optionIndex as string -> count
    val votedUsers: Map<String, Int> = emptyMap(), // userId -> optionIndex
    val pollIsClosed: Boolean = false,
    val pollIsQuiz: Boolean = false,
    val quizCorrectOptionIndex: Int = -1,
    val pollAllowMultiple: Boolean = false,
    val reactions: Map<String, Int> = emptyMap(), // emoji -> count
    val userReactions: Map<String, String> = emptyMap(), // userId -> emoji
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ChannelAnalyticsData(
    val channelId: String = "",
    val totalMembers: Int = 0,
    val newMembers: Int = 0,
    val membersLost: Int = 0,
    val postViews: Int = 0,
    val reactions: Int = 0,
    val pollVotes: Int = 0,
    val engagementRate: Double = 0.0,
    val notificationReachPercent: Int = 100
)

@Serializable
data class UserNote(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val text: String = "",
    val emoji: String = "",
    val mood: String = "",
    val musicTrackId: String? = null,
    val musicTrackName: String? = null,
    val musicTrackArtist: String? = null,
    val musicTrackUrl: String? = null,
    val musicTrackArtwork: String? = null,
    val musicProvider: String? = "S45",
    val musicDurationSeconds: Int = 30,
    val audience: String = "followers", // "followers", "close_friends"
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 24 * 60 * 60 * 1000,
    val updatedAt: Long = System.currentTimeMillis(),
    val status: String = "active",
    val reactions: Map<String, String> = emptyMap(), // Map of uid -> emoji
    val viewers: List<String> = emptyList(), // User IDs of people who viewed the note
    val viewerDetails: Map<String, Long> = emptyMap() // Map of userId -> timestamp when viewed
)

@Serializable
data class TrialReelAnalytics(
    val contentId: String = "",
    val views: Int = 0,
    val uniqueViewers: Int = 0,
    val watchTimeSeconds: Long = 0L,
    val avgWatchDurationSeconds: Double = 0.0,
    val completionRatePercent: Int = 0,
    val likes: Int = 0,
    val comments: Int = 0,
    val shares: Int = 0,
    val saves: Int = 0,
    val followersGained: Int = 0,
    val status: String = "trial", // "trial", "published", "archived"
    val audienceRule: String = "test_audience", // "test_audience", "close_friends"
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class AddYoursPrompt(
    val id: String = "",
    val creatorId: String = "",
    val creatorUsername: String = "",
    val creatorAvatar: String = "",
    val promptTitle: String = "",
    val starterContentUrl: String = "",
    val starterContentType: String = "story", // "story", "reel", "post"
    val participantsCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isClosed: Boolean = false,
    val allowParticipation: Boolean = true
)

@Serializable
data class AddYoursParticipant(
    val id: String = "",
    val promptId: String = "",
    val contentId: String = "",
    val contentType: String = "story", // "story", "reel", "post"
    val mediaUrl: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class SavedPostItem(
    val id: String = "",
    val userId: String = "",
    val postId: String = "",
    val mediaUrl: String = "",
    val thumbnailUrl: String = "",
    val caption: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String = "",
    val collectionId: String? = null,
    val savedAt: Long = System.currentTimeMillis()
)

@Serializable
data class SavedCollection(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val coverUrl: String = "",
    val itemCount: Int = 0,
    val isPrivate: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class StorySticker(
    val id: String = "",
    val type: String = "poll", // "poll", "question", "slider", "quiz", "mention", "hashtag", "location", "add_yours"
    val xPercent: Float = 0.5f,
    val yPercent: Float = 0.5f,
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctOptionIndex: Int = 0,
    val emoji: String = "❤️",
    val mentionUsername: String = "",
    val mentionUserId: String = "",
    val mentionUserAvatar: String = "",
    val hashtag: String = "",
    val locationName: String = "",
    val addYoursPrompt: String = "",
    val addYoursCount: Int = 1
)

@Serializable
data class StickerInteraction(
    val id: String = "",
    val storyId: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val stickerId: String = "",
    val stickerType: String = "",
    val selectedOption: Int? = null,
    val sliderValue: Float? = null,
    val answerText: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ContentInsightsData(
    val contentId: String = "",
    val views: Int = 0,
    val reach: Int = 0,
    val likes: Int = 0,
    val comments: Int = 0,
    val shares: Int = 0,
    val saves: Int = 0,
    val reposts: Int = 0,
    val watchTimeSeconds: Long = 0L,
    val avgWatchTimeSeconds: Double = 0.0,
    val followerReachPercent: Int = 68,
    val nonFollowerReachPercent: Int = 32
)

@Serializable
data class PrivacyControlsData(
    val isPrivateAccount: Boolean = false,
    val activityStatusEnabled: Boolean = true,
    val mentionPermission: String = "everyone", // "everyone", "following", "nobody"
    val tagPermission: String = "everyone", // "everyone", "following", "nobody"
    val manualTagApproval: Boolean = false,
    val filterOffensiveComments: Boolean = true,
    val customBlockedWords: List<String> = emptyList()
)
