package com.example.model

import java.util.UUID

enum class MonetizationStatus {
    NOT_ELIGIBLE,
    ELIGIBLE,
    UNDER_REVIEW,
    APPROVED,
    LIMITED,
    SUSPENDED,
    DISABLED
}

data class MonetizationRequest(
    val id: String = "REQ_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
    val userId: String = "",
    val username: String = "",
    val userAvatar: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val followerCount: Int = 0,
    val totalViews: Int = 0,
    val contentCategory: String = "Entertainment",
    val audienceAgeGroup: String = "18-34",
    val primaryRegion: String = "India",
    val selectedMethods: List<String> = listOf("video_ads", "reels_bonus", "live_gifts"),
    val govtIdNumber: String = "",
    val panOrTaxId: String = "",
    val acceptedTerms: Boolean = true,
    val status: String = "Submitted", // Draft, Submitted, Under Review, More Information Required, Approved, Rejected, Suspended, Disabled
    val adminReviewNotes: String = "",
    val reviewedBy: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val reviewedAt: Long? = null
)

data class ContentMonetization(
    val postId: String = "",
    val creatorId: String = "",
    val title: String = "",
    val mediaType: String = "video", // video, reel, post, live
    val status: String = "Monetized", // Eligible, Under Review, Monetized, Limited, Disabled, Policy Review
    val qualifiedViews: Int = 0,
    val totalViews: Int = 0,
    val watchTimeSeconds: Long = 0,
    val estimatedEarnings: Double = 0.0,
    val finalizedEarnings: Double = 0.0,
    val restrictionReason: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class WalletTransaction(
    val transactionId: String = "TXN_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
    val userId: String = "",
    val creatorId: String = "",
    val type: String = "EARNING", // GIFT, VIDEO_REVENUE, REEL_REVENUE, LIVE_GIFT, SUBSCRIPTION, WITHDRAWAL, DEPOSIT
    val source: String = "Live Gift",
    val amount: Double = 0.0,
    val currency: String = "INR", // INR / USD / COINS
    val status: String = "Completed", // Pending, Processing, Completed, Failed, Reversed
    val referenceId: String = "",
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class CreatorPayout(
    val id: String = "PAY_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
    val userId: String = "",
    val creatorName: String = "",
    val amount: Double = 0.0,
    val currency: String = "INR",
    val paymentMethod: String = "UPI", // UPI, Bank Transfer, PayPal
    val accountDetails: String = "",
    val ifscOrRouting: String = "",
    val status: String = "Requested", // Requested, Pending, Processing, Completed, Failed, Rejected
    val remarks: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val processedAt: Long? = null
)

data class CreatorAnalyticsSummary(
    val totalViews: Long = 0,
    val uniqueViewers: Long = 0,
    val reach: Long = 0,
    val impressions: Long = 0,
    val watchTimeSeconds: Long = 0,
    val averageWatchTimeSeconds: Double = 0.0,
    val completionRate: Double = 0.0,
    val totalLikes: Long = 0,
    val totalComments: Long = 0,
    val totalShares: Long = 0,
    val totalSaves: Long = 0,
    val totalReposts: Long = 0,
    val profileVisits: Long = 0,
    val followsGained: Long = 0,
    val followsLost: Long = 0,
    val engagementRate: Double = 0.0,
    val postViews: Long = 0,
    val reelViews: Long = 0,
    val storyViews: Long = 0,
    val liveViewers: Long = 0,
    val peakConcurrentViewers: Long = 0,
    val giftCount: Long = 0,
    val giftValueEarnings: Double = 0.0,
    val videoAdEarnings: Double = 0.0,
    val photoEarnings: Double = 0.0,
    val referralEarnings: Double = 0.0,
    val subscriptionEarnings: Double = 0.0,
    val paidPostEarnings: Double = 0.0,
    val platformFeePaid: Double = 0.0,
    val totalEstimatedEarnings: Double = 0.0,
    val walletBalance: Double = 0.0,
    val pendingPayoutBalance: Double = 0.0
)

data class MonetizationSettings(
    val videoCpmInr: Double = 45.0, // INR per 1k valid views
    val reelCpmInr: Double = 35.0,
    val photoCpmInr: Double = 15.0,
    val liveGiftRevenueShare: Double = 0.70, // 70% to creator, 30% platform
    val coinsToInrRate: Double = 0.10, // 1 Coin = 0.10 INR (10 Coins = 1 INR)
    val inrToCoinsRate: Double = 10.0, // 1 INR = 10 Coins
    val scheduledPayoutDayOfMonth: Int = 25, // Auto payment date (e.g. 25th of every month)
    val referralBonusInr: Double = 25.0,
    val minWithdrawalInr: Double = 100.0,
    val maxDailyWithdrawalInr: Double = 50000.0,
    val isMonetizationSystemActive: Boolean = true,
    val fraudDetectionEnabled: Boolean = true,
    val minFollowersForMonetization: Int = 100,
    val minViewsForMonetization: Int = 1000,
    val coinIconUrl: String = "", // Custom animated GIF or image icon for Coins
    val exclusiveCommissionPct: Double = 15.0, // Platform commission % on exclusive unlocks (admin editable)
    val exclusiveMinPriceInr: Double = 10.0, // Minimum ₹10 for exclusive content
    val exclusiveMaxPriceInr: Double = 5000.0 // Maximum configurable price for exclusive content
)

data class FraudAuditLog(
    val logId: String = "FRAUD_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
    val userId: String = "",
    val actionType: String = "", // SELF_VIEW, RAPID_CLICK, BOT_ACTIVITY, FAKE_ENGAGEMENT
    val reason: String = "",
    val detectedAt: Long = System.currentTimeMillis(),
    val isActionBlocked: Boolean = true
)

data class WithdrawalRequest(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val amount: Double = 0.0,
    val payoutMethod: String = "BANK", // "UPI" or "BANK"
    val upiId: String = "",
    val bankName: String = "",
    val accountNumber: String = "",
    val ifscCode: String = "",
    val accountHolderName: String = "",
    val status: String = "Pending", // "Pending", "Processing", "Paid", "Completed", "Rejected"
    val utrReference: String = "",
    val adminRemarks: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class DepositRequest(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val amount: Double = 0.0,
    val utrNumber: String = "",
    val orderId: String = "",
    val productType: String = "WALLET_DEPOSIT",
    val creatorId: String = "",
    val contentId: String = "",
    val upiIdUsed: String = "",
    val screenshotUrl: String = "",
    val status: String = "Pending", // "Pending", "Approved", "Rejected"
    val timestamp: Long = System.currentTimeMillis()
)

data class UniversalTransaction(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val amountText: String = "",
    val formattedDate: String = "",
    val isCredit: Boolean = true,
    val status: String = "Completed",
    val category: String = "CONVERSION",
    val timestamp: Long = System.currentTimeMillis()
)

data class VideoEarningItem(
    val postId: String = "",
    val title: String = "",
    val thumbnailUrl: String = "",
    val views: Long = 0L,
    val likes: Long = 0L,
    val earningsInr: Double = 0.0,
    val isPaid: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

