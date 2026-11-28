package com.example.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

@Immutable
data class LeaderboardEntry(
    val userId: String = "",
    val username: String = "Creator",
    val displayName: String = "",
    val avatarUrl: String = "",
    val isVerified: Boolean = false,
    val verificationBadgeUrl: String = "",
    val score: Long = 0L,
    val metricLabel: String = "",
    val coins: Long = 0L,
    val earnings: Double = 0.0,
    val followersCount: Int = 0,
    val rank: Int = 0,
    val equippedFrameId: String = "",
    val supporterBadge: String = "" // "CROWN_1", "CROWN_2", "CROWN_3", "TOP_SUPPORTER"
)

@Immutable
data class DailyRewardsState(
    val coins: Long = 0L,
    val streakDay: Int = 0, // 0..7
    val lastCheckInDate: String = "",
    val lastSpinDate: String = "",
    val canClaimCheckInToday: Boolean = true,
    val canSpinToday: Boolean = true,
    val totalStreakClaims: Int = 0
)

@Immutable
data class LuckyCoinBox(
    val id: String = "",
    val creatorId: String = "",
    val creatorUsername: String = "Creator",
    val creatorAvatar: String = "",
    val isCreatorVerified: Boolean = false,
    val title: String = "Lucky Treasure Box",
    val totalCoins: Int = 100,
    val remainingCoins: Int = 100,
    val maxClaimants: Int = 10,
    val claimedBy: Map<String, Int> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
)

@Immutable
data class CosmeticItem(
    val id: String,
    val type: String, // "FRAME", "THEME", "ENTRANCE"
    val name: String,
    val description: String,
    val priceCoins: Int,
    val primaryColor: Color,
    val secondaryColor: Color,
    val accentColor: Color,
    val emoji: String,
    val badgeLabel: String = ""
)

@Immutable
data class UserCosmeticsState(
    val coins: Long = 0L,
    val ownedIds: Set<String> = emptySet(),
    val equippedFrameId: String = "frame_none",
    val equippedThemeId: String = "theme_default",
    val equippedEntranceId: String = "entrance_none"
)

@Immutable
data class CreatorRateCard(
    val userId: String = "",
    val username: String = "Creator",
    val displayName: String = "",
    val avatarUrl: String = "",
    val isVerified: Boolean = false,
    val verificationBadgeUrl: String = "",
    val followersCount: Int = 0,
    val enabled: Boolean = true,
    val niche: String = "Lifestyle & Entertainment",
    val bioNote: String = "Open for brand shoutouts, product promos & creator collabs",
    val storyShoutoutCoins: Int = 150,
    val reelPromoCoins: Int = 400,
    val collabPostCoins: Int = 250,
    val turnaroundDays: Int = 2,
    val completedDeals: Int = 0
)

@Immutable
data class CollabBooking(
    val id: String = "",
    val creatorId: String = "",
    val creatorUsername: String = "",
    val creatorAvatar: String = "",
    val brandUserId: String = "",
    val brandUsername: String = "",
    val brandAvatar: String = "",
    val packageType: String = "STORY_SHOUTOUT", // STORY_SHOUTOUT, REEL_PROMO, COLLAB_POST
    val brief: String = "",
    val amountCoins: Int = 0,
    val status: String = "ESCROW_HELD", // ESCROW_HELD, ACCEPTED, DELIVERED, COMPLETED, DECLINED
    val deliverableNote: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Stable
data class WatchPartySession(
    val id: String = "",
    val roomCode: String = "",
    val hostId: String = "",
    val hostUsername: String = "Host",
    val chatPartnerId: String = "",
    val videoId: String = "",
    val videoUrl: String = "",
    val videoTitle: String = "Trending Reel",
    val videoThumb: String = "",
    val isPlaying: Boolean = true,
    val positionMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
    val participants: List<String> = emptyList(),
    val lastEmoji: String = "",
    val lastEmojiSender: String = "",
    val lastEmojiTs: Long = 0L
)

@Immutable
data class WatchPartyChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderUsername: String = "Viewer",
    val senderAvatar: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
data class ChatUpgradePrefs(
    val themeId: String = "default",
    val vanishModeEnabled: Boolean = false,
    val voiceFilterId: String = "original"
)

@Immutable
data class SmartReelTemplate(
    val id: String,
    val name: String,
    val subtitle: String,
    val bpm: Int,
    val clipDurationMs: Long,
    val musicTitle: String,
    val musicArtist: String,
    val musicUrl: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val transitionStyle: String, // "ZOOM_PULSE", "SLIDE_FLASH", "CINEMATIC_FADE", "POLAROID_FLIP"
    val defaultCaptionStyle: String
)

@Immutable
data class AutoCaptionStyle(
    val id: String,
    val label: String,
    val textColor: Color,
    val backgroundColor: Color,
    val highlightColor: Color
)
