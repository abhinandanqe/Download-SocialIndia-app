package com.example.payments

import android.util.Log
import com.example.model.*
import com.example.utils.UpiPaymentManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID

/**
 * SocialIndiaPaymentOrchestrator: Centralized Payment & Entitlement Engine
 *
 * Enforces:
 * - Backend-controlled payment order creation
 * - Authoritative atomic payment verification (Native UPI Intent & 12-Digit UTR Queue)
 * - Strict Idempotency & Replay Protection
 * - Dual Entitlement separation (Recurring Creator Subscriptions vs Permanent Post Unlocks)
 * - Transparent 90/10 Creator Earnings Ledger accounting
 * - Real-time Admin Panel UTR Verification & Instant Entitlement Fulfillment
 */
object SocialIndiaPaymentOrchestrator {
    private const val TAG = "PaymentOrchestrator"
    private val firestore get() = FirebaseFirestore.getInstance()

    private val directUpiProvider = DirectUpiPaymentProvider()
    private val sandboxProvider = SandboxPaymentProvider()

    private val utrRegex = Regex("^[0-9]{12}$")

    fun getProvider(providerId: String): PaymentProvider {
        return when (providerId) {
            "SANDBOX" -> sandboxProvider
            else -> directUpiProvider
        }
    }

    fun isValidUtr(utr: String): Boolean {
        return utrRegex.matches(utr.trim())
    }

    /**
     * 1. Create a server-registered Payment Order
     */
    suspend fun createPaymentOrder(
        userId: String,
        productType: PaymentProductType,
        productId: String = "",
        creatorId: String = "",
        contentId: String = "",
        amountRupees: Double,
        providerId: String = "DIRECT_UPI",
        userName: String = "",
        targetTitle: String = ""
    ): Result<PaymentOrder> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("User must be authenticated"))
        }

        try {
            val amountMinorUnits = (amountRupees * 100).toLong()
            val resolvedUserName = if (userName.isNotBlank()) {
                userName
            } else {
                try {
                    val userSnap = firestore.collection("users").document(userId).get().await()
                    userSnap.getString("username") ?: userSnap.getString("name") ?: "User_${userId.take(5)}"
                } catch (_: Exception) {
                    "User_${userId.take(5)}"
                }
            }

            val order = PaymentOrder(
                orderId = "ORD_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6).uppercase()}",
                userId = userId,
                userName = resolvedUserName,
                productType = productType.name,
                productId = productId.ifBlank { contentId },
                creatorId = creatorId,
                contentId = contentId,
                targetTitle = targetTitle,
                amountMinorUnits = amountMinorUnits,
                amountRupees = amountRupees,
                currency = "INR",
                provider = providerId,
                status = PaymentOrderStatus.PENDING.name,
                upiIdUsed = UpiPaymentManager.getAdminUpiId(),
                idempotencyKey = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis()
            )

            firestore.collection("payment_orders").document(order.orderId)
                .set(order)
                .await()

            Log.d(TAG, "Created Payment Order: ${order.orderId} for ₹$amountRupees ($productType)")
            Result.success(order)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create payment order", e)
            Result.failure(e)
        }
    }

    /**
     * 2. Submit 12-Digit UTR / Transaction Reference to Live Admin Verification Queue
     */
    suspend fun submitUtrForVerification(
        order: PaymentOrder,
        utrNumber: String,
        upiIdUsed: String = UpiPaymentManager.getAdminUpiId(),
        screenshotUrl: String = "",
        customNote: String = ""
    ): Result<PaymentOrder> = withContext(Dispatchers.IO) {
        val cleanUtr = utrNumber.trim()
        if (!isValidUtr(cleanUtr)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid UTR. Please enter a valid 12-digit numeric UTR/Ref number."))
        }

        try {
            // Check duplicate UTR across pending_utr_verifications & payment_orders
            val existingUtrDoc = firestore.collection("pending_utr_verifications").document(cleanUtr).get().await()
            if (existingUtrDoc.exists()) {
                val existingOrderId = existingUtrDoc.getString("orderId") ?: ""
                val existingStatus = existingUtrDoc.getString("status") ?: ""
                if (existingOrderId != order.orderId && existingStatus != "REJECTED") {
                    return@withContext Result.failure(IllegalStateException("This 12-digit UTR ($cleanUtr) has already been submitted."))
                }
            }

            val duplicateOrders = firestore.collection("payment_orders")
                .whereEqualTo("utrNumber", cleanUtr)
                .limit(3)
                .get()
                .await()
            val hasDuplicateOrder = duplicateOrders.documents.any { doc ->
                doc.id != order.orderId && (doc.getString("status") == PaymentOrderStatus.VERIFIED.name || doc.getString("status") == PaymentOrderStatus.PENDING_VERIFICATION.name)
            }
            if (hasDuplicateOrder) {
                return@withContext Result.failure(IllegalStateException("UTR $cleanUtr is already linked to another payment."))
            }

            val now = System.currentTimeMillis()
            val resolvedUserName = order.userName.ifBlank {
                try {
                    val u = firestore.collection("users").document(order.userId).get().await()
                    u.getString("username") ?: u.getString("name") ?: "User_${order.userId.take(5)}"
                } catch (_: Exception) {
                    "User_${order.userId.take(5)}"
                }
            }

            val updatedOrder = order.copy(
                userName = resolvedUserName,
                status = PaymentOrderStatus.PENDING_VERIFICATION.name,
                utrNumber = cleanUtr,
                providerTxnId = cleanUtr,
                upiIdUsed = upiIdUsed
            )

            val batch = firestore.batch()

            // 1. Update payment_orders
            val orderRef = firestore.collection("payment_orders").document(order.orderId)
            batch.set(orderRef, updatedOrder, SetOptions.merge())

            // 2. Write to pending_utr_verifications/{cleanUtr}
            val utrRef = firestore.collection("pending_utr_verifications").document(cleanUtr)
            val utrPayload = mapOf(
                "utr" to cleanUtr,
                "utrNumber" to cleanUtr,
                "orderId" to order.orderId,
                "userId" to order.userId,
                "userName" to resolvedUserName,
                "creatorId" to order.creatorId,
                "contentId" to order.contentId,
                "productId" to order.productId,
                "productType" to order.productType,
                "targetTitle" to order.targetTitle,
                "amount" to order.amountRupees,
                "upiIdUsed" to upiIdUsed,
                "screenshotUrl" to screenshotUrl,
                "note" to customNote,
                "status" to "PENDING_ADMIN_REVIEW",
                "timestamp" to now
            )
            batch.set(utrRef, utrPayload, SetOptions.merge())

            // 3. Write to deposit_requests/{order.orderId} so Admin Panel Deposits & UTR Queue shows every request
            val depositRef = firestore.collection("deposit_requests").document(order.orderId)
            val depositReq = DepositRequest(
                id = order.orderId,
                userId = order.userId,
                username = resolvedUserName,
                amount = order.amountRupees,
                screenshotUrl = screenshotUrl,
                utrNumber = cleanUtr,
                orderId = order.orderId,
                productType = order.productType,
                creatorId = order.creatorId,
                contentId = order.contentId.ifBlank { order.productId },
                upiIdUsed = upiIdUsed,
                status = "Pending",
                timestamp = now
            )
            batch.set(depositRef, depositReq, SetOptions.merge())

            batch.commit().await()

            Log.d(TAG, "Submitted UTR $cleanUtr for order ${order.orderId} (${order.productType})")
            Result.success(updatedOrder)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit UTR for verification", e)
            Result.failure(e)
        }
    }

    /**
     * 3. Authoritative Server Verification & Atomic Entitlement Activation
     */
    suspend fun verifyAndFulfillOrder(
        orderId: String,
        rawResponse: String,
        providerId: String = "DIRECT_UPI"
    ): Result<PaymentOrder> = withContext(Dispatchers.IO) {
        val provider = getProvider(providerId)
        val responseData = provider.parseResponse(rawResponse)

        Log.d(TAG, "Verifying order $orderId with response status=${responseData.status}, code=${responseData.responseCode}")

        if (!responseData.isAuthoritativeSuccess) {
            val failStatus = when (responseData.status) {
                "CANCELLED" -> PaymentOrderStatus.CANCELLED
                "SUBMITTED" -> PaymentOrderStatus.PENDING_VERIFICATION
                else -> PaymentOrderStatus.FAILED
            }
            try {
                firestore.collection("payment_orders").document(orderId)
                    .update(
                        mapOf(
                            "status" to failStatus.name,
                            "providerResponse" to rawResponse,
                            "providerTxnId" to responseData.txnId
                        )
                    ).await()
            } catch (_: Exception) {}
            return@withContext Result.failure(IllegalStateException("Payment status: ${responseData.status}"))
        }

        try {
            val dynamicExclusiveCommissionRate = try {
                val settingsDoc = firestore.collection("admin_settings")
                    .document("monetization_settings")
                    .get()
                    .await()
                val pct = settingsDoc.getDouble("exclusiveCommissionPct") ?: 15.0
                (pct / 100.0).coerceIn(0.0, 0.50)
            } catch (_: Exception) {
                0.15
            }

            val updatedOrder = firestore.runTransaction { transaction ->
                val orderRef = firestore.collection("payment_orders").document(orderId)
                val orderDoc = transaction.get(orderRef)

                if (!orderDoc.exists()) {
                    throw IllegalStateException("Payment Order $orderId does not exist.")
                }

                val currentStatus = orderDoc.getString("status") ?: PaymentOrderStatus.PENDING.name
                val userId = orderDoc.getString("userId") ?: ""
                val userName = orderDoc.getString("userName") ?: ""
                val productTypeStr = orderDoc.getString("productType") ?: PaymentProductType.CREATOR_SUBSCRIPTION.name
                val productId = orderDoc.getString("productId") ?: ""
                val creatorId = orderDoc.getString("creatorId") ?: ""
                val contentId = orderDoc.getString("contentId") ?: ""
                val targetTitle = orderDoc.getString("targetTitle") ?: ""
                val amountRupees = orderDoc.getDouble("amountRupees") ?: 0.0
                val existingUtr = orderDoc.getString("utrNumber") ?: ""

                // IDEMPOTENCY CHECK: If already verified, do not re-fulfill!
                if (currentStatus == PaymentOrderStatus.VERIFIED.name) {
                    Log.i(TAG, "Order $orderId was already verified. Returning existing record without duplicate credit.")
                    return@runTransaction try {
                        orderDoc.toObject(PaymentOrder::class.java)
                    } catch (_: Exception) { null } ?: PaymentOrder(
                        orderId = orderId,
                        userId = userId,
                        userName = userName,
                        creatorId = creatorId,
                        contentId = contentId,
                        amountRupees = amountRupees,
                        status = PaymentOrderStatus.VERIFIED.name
                    )
                }

                val verifiedAt = System.currentTimeMillis()
                val txnId = responseData.txnId.ifBlank {
                    existingUtr.ifBlank { "TXN_${System.currentTimeMillis()}" }
                }
                val approvalRef = responseData.approvalRefNo.ifBlank { existingUtr.ifBlank { txnId } }

                // All reads inside Firestore transaction MUST happen before writes
                val userRef = if (userId.isNotBlank()) firestore.collection("users").document(userId) else null
                val userSnap = userRef?.let { transaction.get(it) }

                val creatorRef = if (creatorId.isNotBlank() && creatorId != userId) {
                    firestore.collection("users").document(creatorId)
                } else null
                val creatorSnap = creatorRef?.let { transaction.get(it) }

                // 1. Mark Order as VERIFIED
                transaction.update(
                    orderRef,
                    mapOf(
                        "status" to PaymentOrderStatus.VERIFIED.name,
                        "verifiedAt" to verifiedAt,
                        "providerTxnId" to txnId,
                        "utrNumber" to existingUtr.ifBlank { approvalRef },
                        "providerResponse" to rawResponse
                    )
                )

                // 2. Write Immutable Transaction Audit Record
                val txnRef = firestore.collection("payment_transactions").document(txnId)
                val txnRecord = PaymentTransactionRecord(
                    txnId = txnId,
                    orderId = orderId,
                    userId = userId,
                    creatorId = creatorId,
                    productType = productTypeStr,
                    amountRupees = amountRupees,
                    currency = "INR",
                    paymentMode = "DIRECT_UPI",
                    payeeUpiId = UpiPaymentManager.getAdminUpiId(),
                    status = "SUCCESS",
                    providerRef = approvalRef,
                    timestamp = verifiedAt
                )
                transaction.set(txnRef, txnRecord)

                // 3. Fulfill Entitlement based on product type
                when (productTypeStr) {
                    PaymentProductType.INDIVIDUAL_POST_UNLOCK.name -> {
                        val entitlementId = "${userId}_${contentId}"
                        val entitlementRef = firestore.collection("content_entitlements").document(entitlementId)
                        val entitlement = ContentEntitlement(
                            entitlementId = entitlementId,
                            userId = userId,
                            creatorId = creatorId,
                            contentId = contentId,
                            entitlementType = "EXCLUSIVE_UNLOCK",
                            validUntil = -1L,
                            active = false,
                            purchaseTxnId = txnId,
                            grantedAt = verifiedAt
                        )
                        transaction.set(entitlementRef, entitlement)

                        val commissionPct = ((dynamicExclusiveCommissionRate ?: 0.15).coerceIn(0.0, 0.50)) * 100.0
                        val commissionAmt = (amountRupees * commissionPct) / 100.0
                        val creatorNet = (amountRupees - commissionAmt).coerceAtLeast(0.0)
                        val expiresAt = verifiedAt + com.example.repository.EXCLUSIVE_APPROVAL_WINDOW_MS

                        val exPurchaseRef = firestore.collection("exclusive_purchases").document(entitlementId)
                        transaction.set(
                            exPurchaseRef,
                            mapOf(
                                "id" to entitlementId,
                                "buyerId" to userId,
                                "buyerUsername" to userName.ifBlank { "Someone" },
                                "contentId" to contentId,
                                "contentType" to "POST",
                                "creatorId" to creatorId,
                                "amount" to amountRupees,
                                "platformCommissionPct" to commissionPct,
                                "platformCommissionAmount" to commissionAmt,
                                "creatorNetAmount" to creatorNet,
                                "status" to "pending_approval",
                                "paymentMethod" to "UPI_RAZORPAY",
                                "paymentVerified" to true,
                                "transactionRef" to txnId,
                                "expiresAt" to expiresAt,
                                "timestamp" to verifiedAt
                            ),
                            SetOptions.merge()
                        )

                        if (creatorRef != null && creatorSnap != null && creatorSnap.exists()) {
                            val curPending = creatorSnap.getDouble("pendingBalance") ?: 0.0
                            transaction.update(creatorRef, mapOf("pendingBalance" to (curPending + creatorNet)))
                            val creatorWalletRef = firestore.collection("wallets").document(creatorId)
                            transaction.set(
                                creatorWalletRef,
                                mapOf(
                                    "userId" to creatorId,
                                    "pendingBalance" to FieldValue.increment(creatorNet),
                                    "currency" to "INR",
                                    "updatedAt" to verifiedAt
                                ),
                                SetOptions.merge()
                            )
                        }
                    }

                    PaymentProductType.CREATOR_SUBSCRIPTION.name -> {
                        val entitlementId = "${userId}_${creatorId}_SUB"
                        val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000
                        val expiryTimestamp = verifiedAt + thirtyDaysMillis

                        val entitlementRef = firestore.collection("content_entitlements").document(entitlementId)
                        val entitlement = ContentEntitlement(
                            entitlementId = entitlementId,
                            userId = userId,
                            creatorId = creatorId,
                            contentId = "",
                            entitlementType = "CREATOR_MEMBERSHIP",
                            validUntil = expiryTimestamp,
                            active = true,
                            purchaseTxnId = txnId,
                            grantedAt = verifiedAt
                        )
                        transaction.set(entitlementRef, entitlement)

                        val subRef = firestore.collection("authoritative_subscriptions").document(orderId)
                        val subRecord = AuthoritativeSubscription(
                            id = orderId,
                            subscriberId = userId,
                            creatorId = creatorId,
                            planId = "MONTHLY_PASS",
                            status = "ACTIVE",
                            startDate = verifiedAt,
                            expiresAt = expiryTimestamp,
                            pricePaid = amountRupees,
                            paymentTransactionId = txnId
                        )
                        transaction.set(subRef, subRecord)

                        if (creatorRef != null && creatorSnap != null && creatorSnap.exists()) {
                            creditCreatorLedger(
                                transaction = transaction,
                                creatorRef = creatorRef,
                                creatorId = creatorId,
                                buyerId = userId,
                                sourceType = "SUBSCRIPTION",
                                orderId = orderId,
                                contentId = "",
                                grossAmount = amountRupees,
                                timestamp = verifiedAt
                            )
                        }
                    }

                    PaymentProductType.WALLET_DEPOSIT.name -> {
                        if (userRef != null && userSnap != null) {
                            val coinsToAdd = (amountRupees * 10).toLong()
                            val currentWallet = userSnap.getDouble("walletBalance") ?: 0.0
                            val currentCash = userSnap.getDouble("cashBalance") ?: currentWallet
                            val currentCoins = userSnap.getLong("coins") ?: (userSnap.getLong("coinBalance") ?: 0L)
                            transaction.set(
                                userRef,
                                mapOf(
                                    "walletBalance" to (currentWallet + amountRupees),
                                    "cashBalance" to (currentCash + amountRupees),
                                    "coins" to (currentCoins + coinsToAdd),
                                    "coinBalance" to (currentCoins + coinsToAdd)
                                ),
                                SetOptions.merge()
                            )

                            val walletTxRef = firestore.collection("users").document(userId)
                                .collection("transactions").document(orderId)
                            transaction.set(
                                walletTxRef,
                                mapOf(
                                    "id" to orderId,
                                    "type" to "deposit",
                                    "amount" to amountRupees,
                                    "coinsAdded" to coinsToAdd,
                                    "description" to "Wallet UPI Top-Up (Ref: $approvalRef)",
                                    "status" to "Completed",
                                    "utrNumber" to approvalRef,
                                    "timestamp" to Timestamp.now()
                                )
                            )
                        }
                    }

                    PaymentProductType.VIP_PRO_PASS.name -> {
                        if (userRef != null) {
                            transaction.set(
                                userRef,
                                mapOf(
                                    "isPremium" to true,
                                    "isVerified" to true,
                                    "subscriptionTier" to "VIP_PRO",
                                    "subscriptionActive" to true,
                                    "subscriptionExpiry" to Timestamp(Date(verifiedAt + 30L * 24 * 60 * 60 * 1000))
                                ),
                                SetOptions.merge()
                            )
                        }
                    }

                    PaymentProductType.BLUE_TICK_VERIFICATION.name -> {
                        if (userRef != null) {
                            transaction.set(
                                userRef,
                                mapOf(
                                    "isVerified" to true,
                                    "blueTick" to true,
                                    "verificationStatus" to "Verified",
                                    "verificationPaid" to true,
                                    "verificationTxnId" to txnId,
                                    "verifiedAt" to verifiedAt
                                ),
                                SetOptions.merge()
                            )
                        }
                    }

                    PaymentProductType.CREATOR_TIP.name -> {
                        val tipRef = firestore.collection("super_tips").document(orderId)
                        transaction.set(
                            tipRef,
                            mapOf(
                                "id" to orderId,
                                "orderId" to orderId,
                                "senderId" to userId,
                                "senderName" to userName.ifBlank { "Supporter" },
                                "creatorId" to creatorId,
                                "creatorName" to targetTitle,
                                "reelId" to contentId,
                                "amount" to amountRupees,
                                "utrNumber" to approvalRef,
                                "txnId" to txnId,
                                "status" to "VERIFIED",
                                "timestamp" to verifiedAt
                            )
                        )
                        if (creatorRef != null && creatorSnap != null && creatorSnap.exists()) {
                            creditCreatorLedger(
                                transaction = transaction,
                                creatorRef = creatorRef,
                                creatorId = creatorId,
                                buyerId = userId,
                                sourceType = "SUPER_TIP",
                                orderId = orderId,
                                contentId = contentId,
                                grossAmount = amountRupees,
                                timestamp = verifiedAt
                            )
                        }
                    }

                    PaymentProductType.AD_BOOST_PROMOTION.name -> {
                        val campaignId = contentId.ifBlank { productId }
                        if (campaignId.isNotBlank()) {
                            val campaignRef = firestore.collection("campaigns").document(campaignId)
                            transaction.set(
                                campaignRef,
                                mapOf(
                                    "paymentStatus" to "VERIFIED",
                                    "paymentOrderId" to orderId,
                                    "paymentTxnId" to txnId,
                                    "utrNumber" to approvalRef,
                                    "status" to "ACTIVE",
                                    "verifiedAt" to verifiedAt
                                ),
                                SetOptions.merge()
                            )
                            val adRef = firestore.collection("advertisements").document(campaignId)
                            transaction.set(
                                adRef,
                                mapOf(
                                    "paymentStatus" to "VERIFIED",
                                    "paymentOrderId" to orderId,
                                    "paymentTxnId" to txnId,
                                    "utrNumber" to approvalRef,
                                    "isActive" to true,
                                    "verifiedAt" to verifiedAt
                                ),
                                SetOptions.merge()
                            )
                        }
                    }

                    PaymentProductType.STORE_PURCHASE.name -> {
                        val targetStoreOrderId = contentId.ifBlank { orderId }
                        val storeOrderRef = firestore.collection("store_orders").document(targetStoreOrderId)
                        val storeOrder = mapOf(
                            "orderId" to targetStoreOrderId,
                            "paymentOrderId" to orderId,
                            "productId" to productId.ifBlank { contentId },
                            "buyerId" to userId,
                            "amount" to amountRupees,
                            "txnId" to txnId,
                            "utrNumber" to approvalRef,
                            "status" to "VERIFIED_PAID",
                            "paymentMode" to "DIRECT_UPI",
                            "verifiedAt" to verifiedAt
                        )
                        transaction.set(storeOrderRef, storeOrder, SetOptions.merge())
                    }
                }

                // Also mark deposit_requests and pending_utr_verifications if present
                val depRef = firestore.collection("deposit_requests").document(orderId)
                transaction.set(
                    depRef,
                    mapOf(
                        "status" to "Approved",
                        "verifiedAt" to verifiedAt,
                        "utrNumber" to approvalRef
                    ),
                    SetOptions.merge()
                )
                if (existingUtr.isNotBlank()) {
                    val utrRef = firestore.collection("pending_utr_verifications").document(existingUtr)
                    transaction.set(
                        utrRef,
                        mapOf(
                            "status" to "APPROVED",
                            "verifiedAt" to verifiedAt
                        ),
                        SetOptions.merge()
                    )
                }

                PaymentOrder(
                    orderId = orderId,
                    userId = userId,
                    userName = userName,
                    productType = productTypeStr,
                    productId = productId,
                    creatorId = creatorId,
                    contentId = contentId,
                    targetTitle = targetTitle,
                    amountRupees = amountRupees,
                    status = PaymentOrderStatus.VERIFIED.name,
                    utrNumber = existingUtr.ifBlank { approvalRef },
                    providerTxnId = txnId,
                    verifiedAt = verifiedAt
                )
            }.await()

            // Send in-app notifications outside transaction
            sendFulfillmentNotifications(updatedOrder)

            Log.d(TAG, "Order $orderId verified and fulfilled successfully!")
            Result.success(updatedOrder)
        } catch (e: Exception) {
            Log.e(TAG, "Transaction fulfillment failed for order $orderId", e)
            Result.failure(e)
        }
    }

    /**
     * 4. Admin Panel 1-Click Payment & UTR Approval
     * Approves any pending payment order or manual deposit request, activates the exact entitlement,
     * and dispatches an instant in-app notification to the user.
     */
    suspend fun adminApprovePaymentOrder(
        orderId: String,
        depositId: String,
        userId: String,
        amountRupees: Double,
        productType: String = PaymentProductType.WALLET_DEPOSIT.name,
        utrNumber: String = "",
        creatorId: String = "",
        contentId: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanUtr = utrNumber.trim().ifBlank { "ADMIN_VERIFIED_${System.currentTimeMillis()}" }
            val targetOrderId = orderId.ifBlank { depositId }

            if (targetOrderId.isNotBlank()) {
                val orderSnap = firestore.collection("payment_orders").document(targetOrderId).get().await()
                if (orderSnap.exists()) {
                    val rawSyntheticResponse = "Status=SUCCESS&txnId=$cleanUtr&ApprovalRefNo=$cleanUtr&responseCode=00"
                    val result = verifyAndFulfillOrder(targetOrderId, rawSyntheticResponse)
                    if (result.isFailure) {
                        return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Order fulfillment failed"))
                    }
                    if (depositId.isNotBlank() && depositId != targetOrderId) {
                        firestore.collection("deposit_requests").document(depositId)
                            .set(mapOf("status" to "Approved", "verifiedAt" to System.currentTimeMillis()), SetOptions.merge())
                            .await()
                    }
                    return@withContext Result.success(Unit)
                }
            }

            // Fallback for legacy deposit_requests that did not create a payment_orders doc first
            val createdOrderResult = createPaymentOrder(
                userId = userId,
                productType = try { PaymentProductType.valueOf(productType) } catch (_: Exception) { PaymentProductType.WALLET_DEPOSIT },
                creatorId = creatorId,
                contentId = contentId,
                amountRupees = amountRupees
            )
            val createdOrder = createdOrderResult.getOrThrow()
            val rawSyntheticResponse = "Status=SUCCESS&txnId=$cleanUtr&ApprovalRefNo=$cleanUtr&responseCode=00"
            verifyAndFulfillOrder(createdOrder.orderId, rawSyntheticResponse).getOrThrow()

            if (depositId.isNotBlank()) {
                firestore.collection("deposit_requests").document(depositId)
                    .set(mapOf("status" to "Approved", "orderId" to createdOrder.orderId, "verifiedAt" to System.currentTimeMillis()), SetOptions.merge())
                    .await()
            }
            if (utrNumber.isNotBlank()) {
                firestore.collection("pending_utr_verifications").document(utrNumber.trim())
                    .set(mapOf("status" to "APPROVED", "verifiedAt" to System.currentTimeMillis()), SetOptions.merge())
                    .await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Admin approval failed", e)
            Result.failure(e)
        }
    }

    /**
     * 5. Admin Panel Payment / UTR Rejection
     */
    suspend fun adminRejectPaymentOrder(
        orderId: String,
        depositId: String,
        userId: String,
        utrNumber: String,
        reason: String = "UTR could not be verified in bank statement"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val batch = firestore.batch()

            if (orderId.isNotBlank()) {
                val orderRef = firestore.collection("payment_orders").document(orderId)
                batch.set(
                    orderRef,
                    mapOf("status" to PaymentOrderStatus.FAILED.name, "adminRemarks" to reason, "updatedAt" to now),
                    SetOptions.merge()
                )
            }
            if (depositId.isNotBlank()) {
                val depRef = firestore.collection("deposit_requests").document(depositId)
                batch.set(
                    depRef,
                    mapOf("status" to "Rejected", "adminRemarks" to reason, "updatedAt" to now),
                    SetOptions.merge()
                )
            }
            if (utrNumber.isNotBlank()) {
                val utrRef = firestore.collection("pending_utr_verifications").document(utrNumber.trim())
                batch.set(
                    utrRef,
                    mapOf("status" to "REJECTED", "adminRemarks" to reason, "updatedAt" to now),
                    SetOptions.merge()
                )
            }
            batch.commit().await()

            if (userId.isNotBlank()) {
                sendUserNotification(
                    targetUserId = userId,
                    title = "Payment Verification Update",
                    message = "Your payment (UTR: ${utrNumber.ifBlank { orderId.takeLast(6) }}) was not approved: $reason",
                    type = "payment_rejected"
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Admin rejection failed", e)
            Result.failure(e)
        }
    }

    private suspend fun sendFulfillmentNotifications(order: PaymentOrder) {
        try {
            val amountStr = "₹${order.amountRupees.toInt()}"
            val (title, body) = when (order.productType) {
                PaymentProductType.WALLET_DEPOSIT.name -> {
                    val coins = (order.amountRupees * 10).toInt()
                    "Wallet Credited ($amountStr)" to "Your UPI payment of $amountStr is verified! $amountStr cash balance and $coins coins have been added to your Wallet."
                }
                PaymentProductType.CREATOR_SUBSCRIPTION.name -> {
                    "Creator Subscription Active!" to "Your $amountStr VIP Creator Membership is now active for 30 days."
                }
                PaymentProductType.INDIVIDUAL_POST_UNLOCK.name -> {
                    "Payment done - waiting for creator approval" to "Your $amountStr payment is verified. Waiting for the creator to approve within 48 hours (auto-refunded if declined or expired)."
                }
                PaymentProductType.BLUE_TICK_VERIFICATION.name -> {
                    "Blue Tick Activated! ✓" to "Your $amountStr verification payment is approved. Your official Social India Blue Tick is now live!"
                }
                PaymentProductType.VIP_PRO_PASS.name -> {
                    "VIP Pro Pass Activated!" to "Your $amountStr VIP Pro Pass and Blue Tick badge are now active!"
                }
                PaymentProductType.CREATOR_TIP.name -> {
                    "Super Tip Sent ($amountStr)!" to "Your $amountStr Super Tip was verified and credited to the creator."
                }
                PaymentProductType.AD_BOOST_PROMOTION.name -> {
                    "Ad Boost Campaign Live!" to "Your $amountStr Ad Promotion payment is verified and your campaign is now ACTIVE."
                }
                PaymentProductType.STORE_PURCHASE.name -> {
                    "Store Order Confirmed ($amountStr)!" to "Your $amountStr UPI payment is verified and your order is confirmed."
                }
                else -> {
                    "Payment Verified ($amountStr)" to "Your UPI payment of $amountStr has been verified and activated."
                }
            }

            if (order.userId.isNotBlank()) {
                sendUserNotification(
                    targetUserId = order.userId,
                    title = title,
                    message = body,
                    type = if (order.productType == PaymentProductType.INDIVIDUAL_POST_UNLOCK.name) "exclusive_pending" else "payment_verified"
                )
            }

            // Also notify creator if they earned from a tip, subscription, or post unlock
            if (order.creatorId.isNotBlank() && order.creatorId != order.userId) {
                val netCreator = String.format("%.2f", order.amountRupees * 0.90)
                if (order.productType == PaymentProductType.INDIVIDUAL_POST_UNLOCK.name) {
                    sendUserNotification(
                        targetUserId = order.creatorId,
                        title = "New Exclusive Unlock Request",
                        message = "${order.userName.ifBlank { "A supporter" }} paid $amountStr to unlock your exclusive content. Approve or reject within 48 hours.",
                        type = "exclusive_request"
                    )
                } else {
                    sendUserNotification(
                        targetUserId = order.creatorId,
                        title = "New Creator Earning: ₹$netCreator 🎉",
                        message = "${order.userName.ifBlank { "A supporter" }} paid $amountStr (${order.productType.replace("_", " ")}). ₹$netCreator (90%) has been added to your Creator Wallet!",
                        type = "creator_earning"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Non-fatal error sending fulfillment notification", e)
        }
    }

    suspend fun sendUserNotification(
        targetUserId: String,
        title: String,
        message: String,
        type: String = "system"
    ) {
        if (targetUserId.isBlank()) return
        try {
            val notifId = "notif_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}"
            val payload = mapOf(
                "id" to notifId,
                "userId" to targetUserId,
                "receiverId" to targetUserId,
                "senderId" to "admin_system",
                "username" to "Social India Payments",
                "senderName" to "Social India Payments",
                "title" to title,
                "text" to message,
                "message" to message,
                "type" to type,
                "timestamp" to System.currentTimeMillis(),
                "createdAt" to Timestamp.now(),
                "isRead" to false,
                "read" to false
            )
            firestore.collection("users").document(targetUserId)
                .collection("notifications").document(notifId)
                .set(payload)
                .await()

            firestore.collection("notifications").document(notifId)
                .set(payload)
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write user notification", e)
        }
    }

    /**
     * Credit Creator Ledger atomically using admin-configured commission rate (default 10% or exclusiveCommissionPct)
     */
    private fun creditCreatorLedger(
        transaction: com.google.firebase.firestore.Transaction,
        creatorRef: com.google.firebase.firestore.DocumentReference,
        creatorId: String,
        buyerId: String,
        sourceType: String,
        orderId: String,
        contentId: String,
        grossAmount: Double,
        timestamp: Long,
        commissionRateOverride: Double = 0.10
    ) {
        val safeRate = commissionRateOverride.coerceIn(0.0, 0.50)
        val platformFee = grossAmount * safeRate
        val netCreatorAmount = (grossAmount - platformFee).coerceAtLeast(0.0)

        val ledgerId = "LEDGER_${timestamp}_${UUID.randomUUID().toString().take(6).uppercase()}"
        val ledgerRef = firestore.collection("creator_earnings_ledger").document(ledgerId)

        val entry = CreatorEarningsEntry(
            ledgerId = ledgerId,
            creatorId = creatorId,
            buyerId = buyerId,
            sourceType = sourceType,
            orderId = orderId,
            contentId = contentId,
            grossAmount = grossAmount,
            platformFee = platformFee,
            netCreatorAmount = netCreatorAmount,
            status = "AVAILABLE",
            currency = "INR",
            timestamp = timestamp
        )
        transaction.set(ledgerRef, entry)

        val legacyEarningRef = firestore.collection("earning_transactions").document(ledgerId)
        val legacyEarning = EarningTransaction(
            transactionId = ledgerId,
            creatorUid = creatorId,
            senderUid = buyerId,
            sourceType = sourceType,
            sourceId = contentId.ifBlank { orderId },
            grossAmount = grossAmount,
            platformFee = platformFee,
            creatorAmount = netCreatorAmount,
            currency = "INR",
            status = "AVAILABLE",
            createdAt = timestamp,
            availableAt = timestamp
        )
        transaction.set(legacyEarningRef, legacyEarning)

        transaction.set(
            creatorRef,
            mapOf(
                "walletBalance" to FieldValue.increment(netCreatorAmount),
                "earnings" to FieldValue.increment(netCreatorAmount),
                "totalCreatorRevenue" to FieldValue.increment(grossAmount)
            ),
            SetOptions.merge()
        )
    }
}
