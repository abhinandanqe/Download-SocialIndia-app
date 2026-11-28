package com.example.model

import com.google.firebase.Timestamp
import java.util.UUID

/**
 * Product Types supported by Social India Payments
 */
enum class PaymentProductType {
    CREATOR_SUBSCRIPTION,      // Recurring monthly/annual creator membership
    INDIVIDUAL_POST_UNLOCK,    // One-time lifetime purchase of a specific post/video
    WALLET_DEPOSIT,            // Adding funds to cash wallet or purchasing coins
    BLUE_TICK_VERIFICATION,    // Verified creator badge application fee
    VIP_PRO_PASS,              // Global platform VIP pass
    CREATOR_TIP,               // Direct peer-to-peer tip / payment to creator
    STORE_PURCHASE,            // Official store merchandise or digital product
    AD_BOOST_PROMOTION         // Sponsored post / advertisement campaign boost
}

/**
 * Payment Order Status
 */
enum class PaymentOrderStatus {
    CREATED,
    PENDING,
    PENDING_VERIFICATION,
    VERIFIED,
    FAILED,
    CANCELLED,
    REFUNDED
}

/**
 * Payment Order Record (Backend-controlled)
 */
data class PaymentOrder(
    val orderId: String = "ORD_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6).uppercase()}",
    val userId: String = "",
    val userName: String = "",
    val productType: String = PaymentProductType.CREATOR_SUBSCRIPTION.name,
    val productId: String = "",
    val creatorId: String = "",
    val contentId: String = "",
    val targetTitle: String = "",
    val amountMinorUnits: Long = 9900L, // Amount in Paise (e.g. 9900 = ₹99.00)
    val amountRupees: Double = 99.0,
    val currency: String = "INR",
    val provider: String = "DIRECT_UPI", // DIRECT_UPI, SANDBOX, PLAY_BILLING
    val status: String = PaymentOrderStatus.CREATED.name,
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val providerTxnId: String = "",
    val utrNumber: String = "",
    val upiIdUsed: String = "",
    val providerResponse: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val verifiedAt: Long? = null,
    val remarks: String = ""
)

/**
 * Immutable Payment Transaction Audit Record
 */
data class PaymentTransactionRecord(
    val txnId: String = "TXN_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8).uppercase()}",
    val orderId: String = "",
    val userId: String = "",
    val creatorId: String = "",
    val productType: String = "",
    val amountRupees: Double = 0.0,
    val currency: String = "INR",
    val paymentMode: String = "UPI_AUTO",
    val payeeUpiId: String = "",
    val status: String = "SUCCESS",
    val providerRef: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Entitlement Record: Grants specific access rights to a user
 */
data class ContentEntitlement(
    val entitlementId: String = "", // e.g. "${userId}_${contentId}" or "${userId}_${creatorId}_SUB"
    val userId: String = "",
    val creatorId: String = "",
    val contentId: String = "",
    val entitlementType: String = "INDIVIDUAL_POST", // INDIVIDUAL_POST, CREATOR_MEMBERSHIP, VIP_PRO
    val validUntil: Long = -1L, // -1L = Permanent access, or timestamp for subscription
    val active: Boolean = true,
    val purchaseTxnId: String = "",
    val grantedAt: Long = System.currentTimeMillis()
)

/**
 * One-time Content Purchase Record (Lifetime access for a post)
 */
data class ContentPurchaseRecord(
    val purchaseId: String = "PUR_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6).uppercase()}",
    val buyerId: String = "",
    val creatorId: String = "",
    val contentId: String = "",
    val contentTitle: String = "",
    val amountPaid: Double = 0.0,
    val currency: String = "INR",
    val txnId: String = "",
    val status: String = "ACTIVE",
    val purchasedAt: Long = System.currentTimeMillis()
)

/**
 * Creator Earnings Ledger (Double-entry accounting, 90% creator, 10% platform)
 */
data class CreatorEarningsEntry(
    val ledgerId: String = "LEDGER_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6).uppercase()}",
    val creatorId: String = "",
    val buyerId: String = "",
    val sourceType: String = "SUBSCRIPTION", // SUBSCRIPTION, PAID_POST, LIVE_GIFT, TIP
    val orderId: String = "",
    val contentId: String = "",
    val grossAmount: Double = 0.0,
    val platformFee: Double = 0.0,    // 10% Platform fee
    val netCreatorAmount: Double = 0.0, // 90% to Creator
    val status: String = "AVAILABLE", // AVAILABLE, PENDING, PAID_OUT, REFUNDED
    val currency: String = "INR",
    val timestamp: Long = System.currentTimeMillis()
)
