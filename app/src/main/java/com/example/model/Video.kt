package com.example.model
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Stable
@Serializable
data class User(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    val isAdmin: Boolean = false,
    val isVerified: Boolean = false,
    val verificationRequested: Boolean = false,
    val verificationStatus: String = "none",
    val isBanned: Boolean = false,
    val status: String = "offline",
    @SerialName("last_seen") val lastSeen: Long = 0,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val verificationBadgeUrl: String = "",
    val pronouns: String = "",
    val gender: String = "Male",
    val isAiCreator: Boolean = false,
    val isProfessionalAccount: Boolean = false,
    val category: String = "",
    val websiteUrl: String = "",
    val closeFriends: List<String> = emptyList(),
    val noteText: String = "",
    val noteExpiry: Long = 0,
    val pinnedPostIds: List<String> = emptyList(),
    val isMonetized: Boolean = false,
    val monetizationRequested: Boolean = false,
    val monetizationStatus: String = "NOT_ELIGIBLE", // NOT_ELIGIBLE, ELIGIBLE, REQUESTED, PENDING, APPROVED, ACTIVE, REJECTED, SUSPENDED, REVOKED
    val earnings: Double = 0.0,
    val walletBalance: Double = 0.0,
    val bankAccountNumber: String = "",
    val isLive: Boolean = false,
    val currentLiveId: String = "",
    val bio: String = "",
    val displayName: String = "",
    val isPrivate: Boolean = false,
    val suggestionReason: String = "",
    val customLinks: Map<String, String> = emptyMap(),
    val profileTheme: String = "default",
    val accountType: String = "PERSONAL",
    val businessName: String = "",
    val businessCategory: String = "",
    val businessDescription: String = "",
    val businessWebsite: String = "",
    val businessEmail: String = "",
    val businessPhone: String = "",
    val businessAddress: String = "",
    val businessCity: String = "",
    val businessState: String = "",
    val businessCountry: String = "",
    val businessHours: String = "",
    val businessActionButton: String = "",
    val businessActionUrl: String = "",
    val verificationType: String = "PERSONAL",
    val verificationStartTimestamp: Long = 0,
    val verificationExpiryTimestamp: Long = 0,
    val verificationPlanId: String = "",
    val verificationPlanDurationMonths: Int = 0,
    val verificationSuspended: Boolean = false,
    val businessProfileId: String = "",
    val businessVerifiedAt: Long = 0,
    val accountStatus: String = "ACTIVE",
    val suspensionReason: String = "",
    val suspensionDate: Long = 0,
    val suspendedUntil: Long = 0L,
    val restrictionType: String = "NONE",
    val isShadowbanned: Boolean = false,
    val strikesCount: Int = 0,
    val kycVideoVerified: Boolean = false,
    val reviewStatus: String = "NONE",
    val reviewRequestedAt: Long = 0,
    val reviewReason: String = "",
    val faceLockEnabled: Boolean = false,
    val verificationReference: String = "",
    val verificationPaid: Boolean = false,
    val verificationTxnId: String = "",
    val coins: Long = 0L,
    val profileMusicTitle: String = "",
    val profileMusicArtist: String = "",
    val profileMusicUrl: String = "",
    val voiceBioUrl: String = "",
    val voiceBioDurationSec: Int = 0,
    val creatorBadgeTier: String = "", // "GOLD", "SILVER", "BRONZE", "VERIFIED_STAR"
    val weeklyScore: Long = 0L,
    val dmStreakCount: Int = 0,
    val liveActivityStatus: String = ""
)

@Immutable
@Serializable
data class SuggestedUser(
    val user: User,
    val reason: String = "Suggested for you",
    val mutualFollowersCount: Int = 0,
    val isDismissed: Boolean = false
)

@Immutable
@Serializable
data class VideoDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("video_url") val videoUrl: String = "",
    val description: String = "",
    @SerialName("likes_count") val likesCount: Int = 0,
    @SerialName("views_count") val viewsCount: Int = 0,
    @SerialName("comments_count") val commentsCount: Int = 0,
    @SerialName("music_track") val musicTrack: String? = null,
    @SerialName("music_track_url") val musicTrackUrl: String? = null,
    @SerialName("music_track_image_url") val musicTrackImageUrl: String? = null,
    val musicTrackId: String? = null,
    val musicTrackArtist: String? = null,
    @SerialName("collaborator_ids") val collaboratorIds: List<String>? = null,
    @SerialName("video_width") val videoWidth: Int = 0,
    @SerialName("video_height") val videoHeight: Int = 0,
    @SerialName("aspect_ratio") val aspectRatio: Float = 0f
)

@Stable
data class Video(
    val id: String = "",
    val userId: String = "",
    val user: User = User(),
    val collaborators: List<User> = emptyList(),
    val collaboratorIds: List<String> = emptyList(),
    val pendingCollaboratorIds: List<String> = emptyList(),
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val description: String = "",
    val caption: String = "",
    val tags: List<String> = emptyList(),
    var likesCount: Int = 0,
    var viewsCount: Int = 0,
    var commentsCount: Int = 0,
    var savesCount: Int = 0,
    var reportsCount: Int = 0,
    var sharesCount: Int = 0,
    var repostsCount: Int = 0,
    val musicTrack: String = "",
    val musicTrackUrl: String? = null,
    val musicTrackImageUrl: String? = null,
    val musicTrackId: String? = null,
    val musicTrackArtist: String? = null,
    var isLiked: Boolean = false,
    var isSaved: Boolean = false,
    var isReposted: Boolean = false,
    var repostThought: String? = null,
    var reposterUsername: String? = null,
    var reposterAvatarUrl: String? = null,
    val videoUrls: List<String> = emptyList(), var isPremium: Boolean = false, var premiumPrice: Double = 0.0,
    var isTrialReel: Boolean = false,
    var trialAudience: String = "test_audience",
    var addYoursPromptId: String? = null,
    var addYoursPromptTitle: String? = null,
    val mediaType: String = "",
    val timestamp: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val aspectRatio: Float = 0f,
    var isPaidPost: Boolean = false,
    var postUnlockPrice: Double = 0.0,
    var accessType: String = "PUBLIC",
    var visibility: String = "PUBLIC",
    var isExclusiveContent: Boolean = false,
    var blurredThumbnailUrl: String = "",
    var teaserCaption: String = "",
    var hasVaultMedia: Boolean = false,
    var unlockCount: Int = 0,
    var exclusiveRevenue: Double = 0.0
) {
    val isExclusiveLockedEligible: Boolean
        get() = (isExclusiveContent || isPaidPost) && postUnlockPrice >= 10.0

    val isMediaAnImage: Boolean
        get() {
            if (hasVaultMedia) {
                return !(mediaType.equals("video", ignoreCase = true) || mediaType.equals("reel", ignoreCase = true))
            }
            if (videoUrls.size > 1) {
                return true
            }
            if (videoUrl.isNotBlank() && com.example.utils.MediaResolver.isImage(videoUrl)) {
                return true
            }
            if (mediaType.equals("video", ignoreCase = true) || mediaType.equals("reel", ignoreCase = true)) {
                return videoUrl.isBlank()
            }
            if (mediaType.equals("image", ignoreCase = true)) {
                return true
            }
            if (videoUrl.isNotBlank()) {
                return com.example.utils.MediaResolver.isImage(videoUrl)
            }
            if (thumbnailUrl.isNotBlank()) {
                return com.example.utils.MediaResolver.isImage(thumbnailUrl)
            }
            return true
        }
}

@Stable
data class VideoPost(
    val id: String = "",
    val userId: String = "",
    val username: String = "User",
    val userAvatar: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val mediaUrls: List<String> = emptyList(),
    val caption: String = "",
    val hashtags: List<String> = emptyList(),
    val musicTrack: String = "Original Audio",
    val likes: Int = 0,
    val views: Int = 0,
    val isVideo: Boolean = true
)

