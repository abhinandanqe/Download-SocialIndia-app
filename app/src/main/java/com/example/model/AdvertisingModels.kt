package com.example.model

import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.serialization.Serializable

object AccountType {
    const val PERSONAL = "PERSONAL"
    const val CREATOR = "CREATOR"
    const val BUSINESS = "BUSINESS"
}

object CampaignObjective {
    const val AWARENESS = "AWARENESS"
    const val REACH = "REACH"
    const val ENGAGEMENT = "ENGAGEMENT"
    const val WEBSITE_VISITS = "WEBSITE_VISITS"
    const val PROFILE_VISITS = "PROFILE_VISITS"
    const val MESSAGES = "MESSAGES"
    const val APP_PROMOTION = "APP_PROMOTION"

    fun getDisplayName(objective: String): String = when (objective) {
        AWARENESS -> "Brand Awareness"
        REACH -> "Maximum Reach"
        ENGAGEMENT -> "Post Engagement"
        WEBSITE_VISITS -> "Website Traffic"
        PROFILE_VISITS -> "Profile Visits"
        MESSAGES -> "Direct Messages"
        APP_PROMOTION -> "App Promotion"
        else -> objective
    }

    fun getDescription(objective: String): String = when (objective) {
        AWARENESS -> "Introduce your brand to people most likely to remember it."
        REACH -> "Show your ad to the maximum number of unique people in your target audience."
        ENGAGEMENT -> "Get more likes, comments, saves, and shares on your posts."
        WEBSITE_VISITS -> "Drive traffic directly to your website, store, or landing page."
        PROFILE_VISITS -> "Attract interested people to your business profile."
        MESSAGES -> "Encourage prospective customers to start conversations with your business."
        APP_PROMOTION -> "Drive installs or engagement for your mobile app."
        else -> ""
    }
}

object AdStatus {
    const val DRAFT = "DRAFT"
    const val SUBMITTED = "SUBMITTED"
    const val UNDER_REVIEW = "UNDER_REVIEW"
    const val APPROVED = "APPROVED"
    const val ACTIVE = "ACTIVE"
    const val PAUSED = "PAUSED"
    const val REJECTED = "REJECTED"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"
}

object AdFormat {
    const val PHOTO = "PHOTO"
    const val VIDEO = "VIDEO"
    const val STORY = "STORY"
    const val REEL = "REEL"
    const val CAROUSEL = "CAROUSEL"
}

object AdPlacement {
    const val HOME_FEED = "HOME_FEED"
    const val STORIES = "STORIES"
    const val REELS = "REELS"
    const val EXPLORE = "EXPLORE"

    val allPlacements = listOf(HOME_FEED, STORIES, REELS, EXPLORE)

    fun getDisplayName(placement: String): String = when (placement) {
        HOME_FEED -> "Home Feed"
        STORIES -> "Stories"
        REELS -> "Reels"
        EXPLORE -> "Explore"
        else -> placement
    }
}

object BudgetType {
    const val DAILY = "DAILY"
    const val LIFETIME = "LIFETIME"
}

object CallToAction {
    const val LEARN_MORE = "Learn More"
    const val VISIT_WEBSITE = "Visit Website"
    const val SHOP_NOW = "Shop Now"
    const val CONTACT_US = "Contact Us"
    const val DOWNLOAD = "Download"
    const val BOOK_NOW = "Book Now"
    const val CALL_NOW = "Call Now"
    const val SEND_MESSAGE = "Send Message"

    val supportedCTAs = listOf(
        LEARN_MORE,
        VISIT_WEBSITE,
        SHOP_NOW,
        CONTACT_US,
        DOWNLOAD,
        BOOK_NOW,
        CALL_NOW,
        SEND_MESSAGE
    )
}

object RefundStatus {
    const val NONE = "NONE"
    const val REQUESTED = "REQUESTED"
    const val PROCESSING = "PROCESSING"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"
    const val REFUNDED = "REFUNDED"
}

@IgnoreExtraProperties
data class TargetingCriteria(
    val countries: List<String> = listOf("India"),
    val states: List<String> = emptyList(),
    val cities: List<String> = emptyList(),
    val minAge: Int = 18,
    val maxAge: Int = 65,
    val interests: List<String> = emptyList(),
    val languages: List<String> = listOf("English", "Hindi")
)

@IgnoreExtraProperties
data class Campaign(
    val campaignId: String = "",
    val advertiserUid: String = "",
    val name: String = "",
    val objective: String = CampaignObjective.WEBSITE_VISITS,
    val status: String = AdStatus.UNDER_REVIEW,
    val budgetType: String = BudgetType.DAILY,
    val budgetAmount: Double = 500.0,
    val spentAmount: Double = 0.0,
    val currency: String = "INR",
    val startAt: Long = System.currentTimeMillis(),
    val endAt: Long = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class AdSet(
    val adSetId: String = "",
    val campaignId: String = "",
    val name: String = "",
    val audience: TargetingCriteria = TargetingCriteria(),
    val placements: List<String> = listOf(AdPlacement.HOME_FEED, AdPlacement.REELS, AdPlacement.STORIES),
    val status: String = AdStatus.ACTIVE,
    val scheduleStart: Long = System.currentTimeMillis(),
    val scheduleEnd: Long = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000)
)

@IgnoreExtraProperties
data class Advertisement(
    val adId: String = "",
    val campaignId: String = "",
    val adSetId: String = "",
    val advertiserUid: String = "",
    val advertiserName: String = "",
    val advertiserUsername: String = "",
    val advertiserAvatarUrl: String = "",
    val advertiserCategory: String = "",
    val format: String = AdFormat.PHOTO,
    val mediaUrls: List<String> = emptyList(),
    val thumbnailUrl: String = "",
    val headline: String = "",
    val primaryText: String = "",
    val caption: String = "",
    val callToAction: String = CallToAction.LEARN_MORE,
    val destinationUrl: String = "",
    val status: String = AdStatus.UNDER_REVIEW,
    val rejectionReason: String = "",
    val impressionsCount: Int = 0,
    val clicksCount: Int = 0,
    val spendAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val sourcePostId: String? = null,
    val placements: List<String> = listOf(AdPlacement.HOME_FEED, AdPlacement.REELS, AdPlacement.STORIES)
)

@IgnoreExtraProperties
data class AdImpressionEvent(
    val eventId: String = "",
    val adId: String = "",
    val campaignId: String = "",
    val advertiserUid: String = "",
    val viewerHashedId: String = "",
    val placement: String = AdPlacement.HOME_FEED,
    val timestamp: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class AdClickEvent(
    val eventId: String = "",
    val adId: String = "",
    val campaignId: String = "",
    val advertiserUid: String = "",
    val clickType: String = "CTA_CLICK",
    val destinationUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class AdReport(
    val reportId: String = "",
    val adId: String = "",
    val reporterUid: String = "",
    val reason: String = "",
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING" // PENDING, RESOLVED, DISMISSED
)

@IgnoreExtraProperties
data class BillingTransaction(
    val transactionId: String = "",
    val advertiserUid: String = "",
    val campaignId: String = "",
    val amount: Double = 0.0,
    val currency: String = "INR",
    val status: String = "SUCCESS", // SUCCESS, PENDING, FAILED, REFUNDED
    val paymentMethod: String = "UPI / Card",
    val createdAt: Long = System.currentTimeMillis(),
    val invoiceNumber: String = "",
    val refundStatus: String = RefundStatus.NONE,
    val refundReason: String = ""
)

@IgnoreExtraProperties
data class PlatformRevenueLedger(
    val recordId: String = "",
    val transactionId: String = "",
    val grossAmount: Double = 0.0,
    val taxAmount: Double = 0.0,
    val paymentProcessingFee: Double = 0.0,
    val platformRevenue: Double = 0.0,
    val advertiserCredit: Double = 0.0,
    val currency: String = "INR",
    val createdAt: Long = System.currentTimeMillis()
)

data class AdAnalyticsSummary(
    val totalImpressions: Int = 0,
    val totalReach: Int = 0,
    val totalClicks: Int = 0,
    val clickThroughRate: Double = 0.0,
    val totalSpend: Double = 0.0,
    val totalEngagements: Int = 0,
    val profileVisits: Int = 0,
    val websiteClicks: Int = 0,
    val activeCampaignsCount: Int = 0,
    val pendingAdsCount: Int = 0
)
