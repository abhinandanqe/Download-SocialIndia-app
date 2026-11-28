package com.example.model

import java.util.UUID

/**
 * Authoritative Creator Monetization State as prescribed:
 * NOT_ELIGIBLE, ELIGIBLE, REQUESTED, PENDING, APPROVED, ACTIVE, REJECTED, SUSPENDED, REVOKED
 */
enum class AuthoritativeMonetizationState {
    NOT_ELIGIBLE,
    ELIGIBLE,
    REQUESTED,
    PENDING,
    APPROVED,
    ACTIVE,
    REJECTED,
    SUSPENDED,
    REVOKED
}

/**
 * Authoritative document stored in `creator_monetizations/{userId}`
 */
data class CreatorMonetizationDoc(
    val userId: String = "",
    val status: String = AuthoritativeMonetizationState.NOT_ELIGIBLE.name,
    val applicationId: String = "",
    val appliedAt: Long = 0L,
    val reviewedAt: Long = 0L,
    val approvedAt: Long = 0L,
    val reviewedBy: String = "",
    val reviewNotes: String = "",
    val eligibility: Boolean = false,
    val enabledProducts: List<String> = listOf("subscriptions", "paid_content", "gifts", "ads"),
    val policyStatus: String = "CLEAN", // CLEAN, WARNING, STRIKE
    val earningsEnabled: Boolean = false,
    val subscriptionsEnabled: Boolean = true,
    val giftsEnabled: Boolean = true,
    val adsEnabled: Boolean = true,
    val paidContentEnabled: Boolean = true
)

/**
 * Earning Ledger Transaction (Real immutable transaction record)
 */
data class EarningTransaction(
    val transactionId: String = "ETXN_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
    val creatorUid: String = "",
    val senderUid: String = "",
    val sourceType: String = "SUBSCRIPTION", // SUBSCRIPTION, GIFT, PAID_CONTENT, AD, BONUS
    val sourceId: String = "",
    val grossAmount: Double = 0.0,
    val platformFee: Double = 0.0,
    val creatorAmount: Double = 0.0,
    val currency: String = "INR",
    val status: String = "PENDING", // PENDING, AVAILABLE, PAID, REFUNDED, REVERSED, DISPUTED
    val createdAt: Long = System.currentTimeMillis(),
    val availableAt: Long = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000), // 7 days settlement
    val payoutId: String? = null,
    val contentTitle: String = ""
)

/**
 * Creator Subscription Plan configured by Creator
 */
data class CreatorSubscriptionPlan(
    val planId: String = "PLAN_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
    val creatorUid: String = "",
    val name: String = "Creator Supporter Pass",
    val description: String = "Exclusive subscriber-only reels, stories, badge and community access.",
    val price: Double = 49.0, // INR
    val currency: String = "INR",
    val billingInterval: String = "MONTHLY",
    val benefits: List<String> = listOf("Exclusive Posts", "Exclusive Reels", "Subscriber Badge", "Priority Comments"),
    val status: String = "ACTIVE", // ACTIVE, PAUSED, ARCHIVED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Verified Subscriber Access Record
 */
data class AuthoritativeSubscription(
    val id: String = "SUB_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
    val subscriberId: String = "",
    val creatorId: String = "",
    val planId: String = "",
    val status: String = "ACTIVE", // ACTIVE, EXPIRED, CANCELLED, PAST_DUE, REFUNDED
    val startDate: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000),
    val pricePaid: Double = 49.0,
    val currency: String = "INR",
    val paymentTransactionId: String = ""
)

/**
 * Audit Log Record for Sensitive Monetization Actions
 */
data class MonetizationAuditLog(
    val auditId: String = "AUDIT_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
    val adminUid: String = "",
    val creatorUid: String = "",
    val action: String = "", // MONETIZATION_APPROVED, MONETIZATION_REJECTED, MONETIZATION_SUSPENDED, MONETIZATION_REVOKED
    val previousState: String = "",
    val newState: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String = "",
    val relatedObjectId: String = ""
)
