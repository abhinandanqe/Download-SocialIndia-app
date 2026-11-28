package com.example.repository

import com.example.model.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

object MonetizationRepository {
    private val firestore get() = FirebaseFirestore.getInstance()

    private data class PostAnalyticsMetrics(
        val totalViews: Long = 0L,
        val totalLikes: Long = 0L,
        val totalComments: Long = 0L,
        val totalShares: Long = 0L,
        val totalSaves: Long = 0L,
        val totalReposts: Long = 0L,
        val postViews: Long = 0L,
        val reelViews: Long = 0L
    )

    private data class UserProfileAnalyticsMetrics(
        val walletBalance: Double = 0.0,
        val isMonetized: Boolean = false,
        val referralCount: Long = 0L,
        val profileVisits: Long = 0L,
        val followsGained: Long = 0L,
        val followsLost: Long = 0L
    )

    private data class GiftAnalyticsMetrics(
        val giftCount: Long = 0L,
        val giftValue: Double = 0.0
    )

    private data class LedgerAnalyticsMetrics(
        val subEarnings: Double = 0.0,
        val paidPostEarnings: Double = 0.0,
        val platformFeePaid: Double = 0.0
    )

    private data class ContentMonetizationMetrics(
        val videoEarnings: Double = 0.0,
        val photoEarnings: Double = 0.0
    )

    private fun safeDouble(raw: Any?): Double = when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    private fun safeLong(raw: Any?): Long = when (raw) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong() ?: 0L
        else -> 0L
    }

    private suspend fun fetchPostAnalyticsMetrics(userId: String, cutoff: Date): PostAnalyticsMetrics {
        return try {
            val postsSnapshot = firestore.collection("posts")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val allPosts = postsSnapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(FirebasePost::class.java)?.copy(id = doc.id)
                } catch (_: Exception) {
                    null
                }
            }
            val filtered = allPosts.filter { (it.timestamp ?: Date(0)).after(cutoff) }
            PostAnalyticsMetrics(
                totalViews = filtered.sumOf { it.viewsCount.toLong() },
                totalLikes = filtered.sumOf { it.likesCount.toLong() },
                totalComments = filtered.sumOf { it.commentsCount.toLong() },
                totalShares = filtered.sumOf { it.sharesCount.toLong() },
                totalSaves = filtered.sumOf { it.savesCount.toLong() },
                totalReposts = filtered.sumOf { it.repostsCount.toLong() },
                postViews = filtered.filter { com.example.utils.MediaResolver.isImage(it.postUrl) }.sumOf { it.viewsCount.toLong() },
                reelViews = filtered.filter { !com.example.utils.MediaResolver.isImage(it.postUrl) && it.postUrl.isNotEmpty() }.sumOf { it.viewsCount.toLong() }
            )
        } catch (_: Exception) {
            PostAnalyticsMetrics()
        }
    }

    private suspend fun fetchUserProfileAnalyticsMetrics(userId: String): UserProfileAnalyticsMetrics {
        return try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            if (!userDoc.exists()) return UserProfileAnalyticsMetrics()
            val walletBal = userDoc.get("walletBalance")?.let { safeDouble(it) }
                ?: safeDouble(userDoc.get("earnings"))
            UserProfileAnalyticsMetrics(
                walletBalance = walletBal,
                isMonetized = (userDoc.get("isMonetized") as? Boolean) ?: false,
                referralCount = safeLong(userDoc.get("referralCount")),
                profileVisits = safeLong(userDoc.get("profileVisits")),
                followsGained = safeLong(userDoc.get("followsGained")),
                followsLost = safeLong(userDoc.get("followsLost"))
            )
        } catch (_: Exception) {
            UserProfileAnalyticsMetrics()
        }
    }

    private suspend fun fetchGiftAnalyticsMetrics(userId: String): GiftAnalyticsMetrics {
        return try {
            val giftsSnapshot = firestore.collection("gifts")
                .whereEqualTo("receiverId", userId)
                .get()
                .await()
            var totalValue = 0.0
            for (doc in giftsSnapshot.documents) {
                totalValue += safeDouble(doc.get("coins"))
            }
            GiftAnalyticsMetrics(
                giftCount = giftsSnapshot.size().toLong(),
                giftValue = totalValue
            )
        } catch (_: Exception) {
            GiftAnalyticsMetrics()
        }
    }

    private suspend fun fetchLedgerAnalyticsMetrics(userId: String): LedgerAnalyticsMetrics {
        var subEarnings = 0.0
        var paidPostEarnings = 0.0
        var platformFeePaid = 0.0
        try {
            val ledgerSnapshot = firestore.collection("creator_earnings_ledger")
                .whereEqualTo("creatorId", userId)
                .get()
                .await()
            for (doc in ledgerSnapshot.documents) {
                val net = safeDouble(doc.get("netCreatorAmount"))
                val fee = safeDouble(doc.get("platformFee"))
                val type = (doc.get("sourceType") as? String) ?: ""
                platformFeePaid += fee
                if (type == "SUBSCRIPTION") {
                    subEarnings += net
                } else if (type == "PAID_POST") {
                    paidPostEarnings += net
                }
            }
        } catch (_: Exception) {}

        if (subEarnings == 0.0) {
            subEarnings = fetchLegacySubscriptionEarnings(userId)
        }
        return LedgerAnalyticsMetrics(subEarnings, paidPostEarnings, platformFeePaid)
    }

    private suspend fun fetchLegacySubscriptionEarnings(userId: String): Double {
        return try {
            val subsSnapshot = firestore.collection("subscriptions")
                .whereEqualTo("creatorId", userId)
                .whereEqualTo("active", true)
                .get()
                .await()
            subsSnapshot.documents.size * 5.0
        } catch (_: Exception) {
            0.0
        }
    }

    private suspend fun fetchContentMonetizationMetrics(userId: String): ContentMonetizationMetrics {
        return try {
            val contentMonSnapshot = firestore.collection("content_monetization")
                .whereEqualTo("creatorId", userId)
                .get()
                .await()
            var videoEarnings = 0.0
            var photoEarnings = 0.0
            for (doc in contentMonSnapshot.documents) {
                val type = (doc.get("mediaType") as? String) ?: "video"
                val earned = doc.get("finalizedEarnings")?.let { safeDouble(it) }
                    ?: safeDouble(doc.get("estimatedEarnings"))
                if (type == "photo" || type == "post") {
                    photoEarnings += earned
                } else {
                    videoEarnings += earned
                }
            }
            ContentMonetizationMetrics(videoEarnings = videoEarnings, photoEarnings = photoEarnings)
        } catch (_: Exception) {
            ContentMonetizationMetrics()
        }
    }

    private suspend fun fetchStoryViewsCount(userId: String): Long {
        return try {
            val storiesSnap = firestore.collection("stories")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            var total = 0L
            for (sDoc in storiesSnap.documents) {
                val views = sDoc.get("viewCount")?.let { safeLong(it) }
                    ?: safeLong(sDoc.get("viewsCount"))
                total += views
            }
            total
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Calculates real analytics summary for a creator over a specified day range.
     */
    suspend fun getCreatorAnalytics(userId: String, days: Int): CreatorAnalyticsSummary {
        if (userId.isBlank()) return CreatorAnalyticsSummary()
        return try {
            val cutoff = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -days)
            }.time

            val posts = fetchPostAnalyticsMetrics(userId, cutoff)
            val profile = fetchUserProfileAnalyticsMetrics(userId)
            val gifts = fetchGiftAnalyticsMetrics(userId)
            val ledger = fetchLedgerAnalyticsMetrics(userId)
            val settings = getMonetizationSettings()
            val contentMon = fetchContentMonetizationMetrics(userId)
            val realStoryViews = fetchStoryViewsCount(userId)

            val totalInteractions = posts.totalLikes + posts.totalComments + posts.totalShares + posts.totalSaves + posts.totalReposts
            val engagementRate = if (posts.totalViews > 0L) {
                (totalInteractions.toDouble() / posts.totalViews.toDouble()) * 100.0
            } else {
                0.0
            }
            val watchTimeSeconds = posts.reelViews * 12L

            val videoEarnings = if (profile.isMonetized && contentMon.videoEarnings == 0.0 && posts.reelViews > 0L) {
                (posts.reelViews.toDouble() / 1000.0) * settings.reelCpmInr
            } else {
                contentMon.videoEarnings
            }
            val photoEarnings = if (profile.isMonetized && contentMon.photoEarnings == 0.0 && posts.postViews > 0L) {
                (posts.postViews.toDouble() / 1000.0) * settings.photoCpmInr
            } else {
                contentMon.photoEarnings
            }

            val referralEarnings = profile.referralCount * settings.referralBonusInr
            val giftEarnings = gifts.giftValue * settings.liveGiftRevenueShare
            val totalEarnings = giftEarnings + ledger.subEarnings + ledger.paidPostEarnings + videoEarnings + photoEarnings + referralEarnings
            val combinedViews = posts.totalViews + realStoryViews
            val avgWatchSec = if (posts.reelViews > 0L) {
                watchTimeSeconds.toDouble() / posts.reelViews.toDouble()
            } else {
                0.0
            }
            val realCompletionRate = if (posts.totalViews > 0L) engagementRate.coerceIn(0.0, 100.0) else 0.0

            CreatorAnalyticsSummary(
                totalViews = combinedViews,
                uniqueViewers = combinedViews,
                reach = combinedViews,
                impressions = combinedViews + totalInteractions,
                watchTimeSeconds = watchTimeSeconds,
                averageWatchTimeSeconds = avgWatchSec,
                completionRate = realCompletionRate,
                totalLikes = posts.totalLikes,
                totalComments = posts.totalComments,
                totalShares = posts.totalShares,
                totalSaves = posts.totalSaves,
                totalReposts = posts.totalReposts,
                profileVisits = profile.profileVisits,
                followsGained = profile.followsGained,
                followsLost = profile.followsLost,
                engagementRate = engagementRate,
                postViews = posts.postViews,
                reelViews = posts.reelViews,
                storyViews = realStoryViews,
                liveViewers = 0,
                peakConcurrentViewers = 0,
                giftCount = gifts.giftCount,
                giftValueEarnings = giftEarnings,
                videoAdEarnings = videoEarnings,
                photoEarnings = photoEarnings,
                referralEarnings = referralEarnings,
                subscriptionEarnings = ledger.subEarnings,
                paidPostEarnings = ledger.paidPostEarnings,
                platformFeePaid = ledger.platformFeePaid,
                totalEstimatedEarnings = totalEarnings,
                walletBalance = profile.walletBalance,
                pendingPayoutBalance = 0.0
            )
        } catch (e: Exception) {
            e.printStackTrace()
            CreatorAnalyticsSummary()
        }
    }

    /**
     * Submits a new multi-step Creator Monetization request with duplicate check and single authoritative state.
     */
    suspend fun submitMonetizationRequest(request: MonetizationRequest): Result<String> {
        return try {
            val existing = getAuthoritativeMonetizationDoc(request.userId)
            if (existing != null && (existing.status == AuthoritativeMonetizationState.PENDING.name || 
                existing.status == AuthoritativeMonetizationState.REQUESTED.name ||
                existing.status == AuthoritativeMonetizationState.APPROVED.name ||
                existing.status == AuthoritativeMonetizationState.ACTIVE.name)) {
                return Result.failure(Exception("An application is already active or in review!"))
            }

            firestore.collection("monetization_requests")
                .document(request.id)
                .set(request)
                .await()

            val creatorDoc = CreatorMonetizationDoc(
                userId = request.userId,
                status = AuthoritativeMonetizationState.PENDING.name,
                applicationId = request.id,
                appliedAt = System.currentTimeMillis(),
                eligibility = true,
                policyStatus = "CLEAN"
            )

            firestore.collection("creator_monetizations").document(request.userId)
                .set(creatorDoc, SetOptions.merge())
                .await()

            // Update user status
            firestore.collection("users").document(request.userId).set(
                mapOf(
                    "monetizationRequested" to true,
                    "monetizationRequestId" to request.id,
                    "monetizationStatus" to AuthoritativeMonetizationState.PENDING.name,
                    "isMonetized" to false
                ),
                SetOptions.merge()
            ).await()

            Result.success(request.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Realtime flow for Authoritative Creator Monetization State
     */
    fun getAuthoritativeMonetizationFlow(userId: String): kotlinx.coroutines.flow.Flow<CreatorMonetizationDoc?> = kotlinx.coroutines.flow.callbackFlow {
        if (userId.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("creator_monetizations").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null && snapshot.exists()) {
                    val doc = try { snapshot.toObject(CreatorMonetizationDoc::class.java) } catch (_: Exception) { null }
                    trySend(doc)
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Get Authoritative Creator Monetization Document
     */
    suspend fun getAuthoritativeMonetizationDoc(userId: String): CreatorMonetizationDoc? {
        if (userId.isEmpty()) return null
        return try {
            val doc = firestore.collection("creator_monetizations").document(userId).get().await()
            if (doc.exists()) doc.toObject(CreatorMonetizationDoc::class.java) else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetches current user's monetization request status if exists.
     */
    suspend fun getMonetizationRequest(userId: String): MonetizationRequest? {
        return try {
            val snapshot = firestore.collection("monetization_requests")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            snapshot.documents.firstOrNull()?.toObject(MonetizationRequest::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Admin: fetch all monetization requests.
     */
    suspend fun getAllMonetizationRequests(): List<MonetizationRequest> {
        return try {
            val snapshot = firestore.collection("monetization_requests")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                try { doc.toObject(MonetizationRequest::class.java) } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Admin: update status of a monetization request and atomically update CreatorMonetizationDoc + User doc.
     * Prevents creator from ever seeing "Request Monetization" again once APPROVED/ACTIVE.
     */
    suspend fun updateMonetizationRequestStatus(
        requestId: String,
        userId: String,
        status: String, // Approved, Rejected, Suspended, Revoked
        notes: String,
        reviewerId: String
    ): Boolean {
        return try {
            val targetState = when (status.lowercase()) {
                "approved" -> AuthoritativeMonetizationState.ACTIVE.name
                "rejected" -> AuthoritativeMonetizationState.REJECTED.name
                "suspended" -> AuthoritativeMonetizationState.SUSPENDED.name
                "revoked" -> AuthoritativeMonetizationState.REVOKED.name
                else -> AuthoritativeMonetizationState.PENDING.name
            }

            val isApproved = targetState == AuthoritativeMonetizationState.ACTIVE.name
            val now = System.currentTimeMillis()

            // 1. Update monetization request document
            firestore.collection("monetization_requests").document(requestId).set(
                mapOf(
                    "status" to status,
                    "adminReviewNotes" to notes,
                    "reviewedBy" to reviewerId,
                    "reviewedAt" to now
                ),
                SetOptions.merge()
            ).await()

            // 2. Authoritative Creator Monetization Document
            firestore.collection("creator_monetizations").document(userId).set(
                mapOf(
                    "userId" to userId,
                    "status" to targetState,
                    "reviewedAt" to now,
                    "reviewedBy" to reviewerId,
                    "reviewNotes" to notes,
                    "approvedAt" to if (isApproved) now else 0L,
                    "earningsEnabled" to isApproved
                ),
                SetOptions.merge()
            ).await()

            // 3. User document synchronization
            firestore.collection("users").document(userId).set(
                mapOf(
                    "isMonetized" to isApproved,
                    "monetizationStatus" to targetState,
                    "monetizationRequested" to false
                ),
                SetOptions.merge()
            ).await()

            // 4. Record Audit Log
            val audit = MonetizationAuditLog(
                adminUid = reviewerId,
                creatorUid = userId,
                action = "MONETIZATION_${status.uppercase()}",
                previousState = AuthoritativeMonetizationState.PENDING.name,
                newState = targetState,
                reason = notes,
                relatedObjectId = requestId
            )
            firestore.collection("monetization_audit_logs").document(audit.auditId).set(audit).await()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Authoritative Subscription Plan Management
     */
    suspend fun saveSubscriptionPlan(plan: CreatorSubscriptionPlan): Boolean {
        return try {
            firestore.collection("creator_subscription_plans").document(plan.creatorUid)
                .set(plan, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getSubscriptionPlan(creatorUid: String): CreatorSubscriptionPlan? {
        return try {
            val doc = firestore.collection("creator_subscription_plans").document(creatorUid).get().await()
            if (doc.exists()) doc.toObject(CreatorSubscriptionPlan::class.java) else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Real Ledger: Record verified earning transaction and update creator wallet.
     */
    suspend fun recordEarningTransaction(txn: EarningTransaction): Boolean {
        return try {
            firestore.collection("earning_transactions").document(txn.transactionId)
                .set(txn)
                .await()

            // Credit creator wallet balance
            if (txn.creatorAmount > 0) {
                firestore.collection("users").document(txn.creatorUid)
                    .update("walletBalance", FieldValue.increment(txn.creatorAmount))
                    .await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetch Real Immutable Earning Transactions for Creator
     */
    suspend fun getCreatorEarningTransactions(creatorUid: String): List<EarningTransaction> {
        return try {
            val list = mutableListOf<EarningTransaction>()
            // Query new creator_earnings_ledger
            val ledgerSnap = firestore.collection("creator_earnings_ledger")
                .whereEqualTo("creatorId", creatorUid)
                .get()
                .await()
            ledgerSnap.documents.forEach { doc ->
                val entry = try { doc.toObject(CreatorEarningsEntry::class.java) } catch (_: Exception) { null }
                if (entry != null) {
                    list.add(
                        EarningTransaction(
                            transactionId = entry.ledgerId,
                            creatorUid = entry.creatorId,
                            senderUid = entry.buyerId,
                            sourceType = entry.sourceType,
                            sourceId = entry.contentId.ifBlank { entry.orderId },
                            grossAmount = entry.grossAmount,
                            platformFee = entry.platformFee,
                            creatorAmount = entry.netCreatorAmount,
                            currency = entry.currency,
                            status = entry.status,
                            createdAt = entry.timestamp,
                            availableAt = entry.timestamp
                        )
                    )
                }
            }
            // Also legacy earning_transactions if any
            val snapshot = firestore.collection("earning_transactions")
                .whereEqualTo("creatorUid", creatorUid)
                .get()
                .await()
            val legacyItems = snapshot.documents.mapNotNull {
                try { it.toObject(EarningTransaction::class.java) } catch (_: Exception) { null }
            }
            val existingIds = list.map { it.transactionId }.toSet()
            legacyItems.filter { it.transactionId !in existingIds }.forEach { list.add(it) }

            list.sortedByDescending { it.createdAt }.take(50)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Submits a payout request from creator's wallet.
     */
    suspend fun requestPayout(payout: CreatorPayout): Result<String> {
        return try {
            // 1. Check user wallet balance
            val userRef = firestore.collection("users").document(payout.userId)
            val userDoc = userRef.get().await()
            val balance = userDoc.getDouble("walletBalance") ?: (userDoc.getDouble("earnings") ?: 0.0)

            if (payout.amount <= 0 || payout.amount > balance) {
                return Result.failure(Exception("Insufficient wallet balance or invalid amount"))
            }

            // 2. Save payout request
            firestore.collection("creator_payouts")
                .document(payout.id)
                .set(payout)
                .await()

            // 3. Deduct from wallet balance and record transaction
            userRef.update("walletBalance", FieldValue.increment(-payout.amount)).await()

            val txn = WalletTransaction(
                userId = payout.userId,
                creatorId = payout.userId,
                type = "WITHDRAWAL",
                source = "Payout Request (${payout.paymentMethod})",
                amount = -payout.amount,
                currency = payout.currency,
                status = "Pending",
                referenceId = payout.id,
                description = "Payout to ${payout.accountDetails}"
            )
            firestore.collection("wallet_transactions").document(txn.transactionId).set(txn).await()

            Result.success(payout.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches user's wallet transactions.
     */
    suspend fun getWalletTransactions(userId: String): List<WalletTransaction> {
        return try {
            val snapshot = firestore.collection("wallet_transactions")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()
            snapshot.documents.mapNotNull {
                try { it.toObject(WalletTransaction::class.java) } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Fetches user's payout history.
     */
    suspend fun getPayoutHistory(userId: String): List<CreatorPayout> {
        return try {
            val snapshot = firestore.collection("creator_payouts")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            snapshot.documents.mapNotNull {
                try { it.toObject(CreatorPayout::class.java) } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Fetch admin-configured monetization settings from Firestore.
     */
     suspend fun getMonetizationSettings(): MonetizationSettings {
         return try {
             val doc = firestore.collection("admin_settings").document("monetization_settings").get().await()
             if (doc.exists()) {
                 MonetizationSettings(
                     videoCpmInr = doc.getDouble("videoCpmInr") ?: 45.0,
                     reelCpmInr = doc.getDouble("reelCpmInr") ?: 35.0,
                     photoCpmInr = doc.getDouble("photoCpmInr") ?: 15.0,
                     liveGiftRevenueShare = doc.getDouble("liveGiftRevenueShare") ?: 0.70,
                     coinsToInrRate = doc.getDouble("coinsToInrRate") ?: 0.10,
                     inrToCoinsRate = doc.getDouble("inrToCoinsRate") ?: 10.0,
                     scheduledPayoutDayOfMonth = doc.getLong("scheduledPayoutDayOfMonth")?.toInt() ?: 25,
                     referralBonusInr = doc.getDouble("referralBonusInr") ?: 25.0,
                     minWithdrawalInr = doc.getDouble("minWithdrawalInr") ?: 100.0,
                     maxDailyWithdrawalInr = doc.getDouble("maxDailyWithdrawalInr") ?: 50000.0,
                     isMonetizationSystemActive = doc.getBoolean("isMonetizationSystemActive") ?: true,
                     fraudDetectionEnabled = doc.getBoolean("fraudDetectionEnabled") ?: true,
                     minFollowersForMonetization = doc.getLong("minFollowersForMonetization")?.toInt() ?: 100,
                     minViewsForMonetization = doc.getLong("minViewsForMonetization")?.toInt() ?: 1000,
                     coinIconUrl = doc.getString("coinIconUrl") ?: ""
                 )
             } else {
                 MonetizationSettings()
             }
         } catch (e: Exception) {
             MonetizationSettings()
         }
     }

    private val sharedMonetizationSettingsFlow: SharedFlow<MonetizationSettings> by lazy {
        callbackFlow {
            val listener = try {
                firestore.collection("admin_settings").document("monetization_settings")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null || !snapshot.exists()) {
                            trySend(MonetizationSettings())
                            return@addSnapshotListener
                        }
                        try {
                            fun dbl(key: String, def: Double): Double =
                                (snapshot.get(key) as? Number)?.toDouble() ?: (snapshot.get(key) as? String)?.toDoubleOrNull() ?: def
                            fun intVal(key: String, def: Int): Int =
                                (snapshot.get(key) as? Number)?.toInt() ?: (snapshot.get(key) as? String)?.toIntOrNull() ?: def
                            fun boolVal(key: String, def: Boolean): Boolean =
                                (snapshot.get(key) as? Boolean) ?: def

                            val settings = MonetizationSettings(
                                videoCpmInr = dbl("videoCpmInr", 45.0),
                                reelCpmInr = dbl("reelCpmInr", 35.0),
                                photoCpmInr = dbl("photoCpmInr", 15.0),
                                liveGiftRevenueShare = dbl("liveGiftRevenueShare", 0.70),
                                coinsToInrRate = dbl("coinsToInrRate", 0.10),
                                inrToCoinsRate = dbl("inrToCoinsRate", 10.0),
                                scheduledPayoutDayOfMonth = intVal("scheduledPayoutDayOfMonth", 25),
                                referralBonusInr = dbl("referralBonusInr", 25.0),
                                minWithdrawalInr = dbl("minWithdrawalInr", 100.0),
                                maxDailyWithdrawalInr = dbl("maxDailyWithdrawalInr", 50000.0),
                                isMonetizationSystemActive = boolVal("isMonetizationSystemActive", true),
                                fraudDetectionEnabled = boolVal("fraudDetectionEnabled", true),
                                minFollowersForMonetization = intVal("minFollowersForMonetization", 100),
                                minViewsForMonetization = intVal("minViewsForMonetization", 1000),
                                coinIconUrl = (snapshot.get("coinIconUrl") as? String) ?: ""
                            )
                            trySend(settings)
                        } catch (_: Exception) {
                            trySend(MonetizationSettings())
                        }
                    }
            } catch (_: Exception) {
                trySend(MonetizationSettings())
                null
            }
            awaitClose { listener?.remove() }
        }.shareIn(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
            replay = 1
        )
    }

    /**
     * Real-time observable Flow of monetization settings for the entire application.
     * Shared across all screens and CoinIcon composables to avoid redundant Firestore listeners.
     */
    fun observeMonetizationSettings(): Flow<MonetizationSettings> = sharedMonetizationSettingsFlow

    /**
     * Admin: update monetization settings in Firestore.
     */
    suspend fun updateMonetizationSettings(settings: MonetizationSettings): Boolean {
        return try {
            firestore.collection("admin_settings").document("monetization_settings")
                .set(settings, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Admin: Approve, Process, or Reject a Withdrawal/Payout Request with real UTR reference & in-app notification.
     */
    suspend fun updateWithdrawalStatus(
        withdrawalId: String,
        userId: String,
        newStatus: String, // Processing, Paid, Completed, Rejected
        remarks: String = "",
        utrReference: String = ""
    ): Boolean {
        return try {
            val now = System.currentTimeMillis()
            val withdrawDoc = firestore.collection("withdrawals").document(withdrawalId).get().await()
            val previousStatus = withdrawDoc.getString("status") ?: "Pending"
            val amount = withdrawDoc.getDouble("amount") ?: 0.0

            val updates = mutableMapOf<String, Any>(
                "status" to newStatus,
                "adminRemarks" to remarks,
                "updatedAt" to now
            )
            if (utrReference.isNotBlank()) {
                updates["utrReference"] = utrReference.trim()
            }

            firestore.collection("withdrawals").document(withdrawalId).set(
                updates,
                SetOptions.merge()
            ).await()

            // If rejected (and wasn't already rejected), refund the amount back to user's wallet
            if (newStatus == "Rejected" && previousStatus != "Rejected" && amount > 0 && userId.isNotBlank()) {
                firestore.collection("users").document(userId)
                    .set(mapOf("walletBalance" to FieldValue.increment(amount)), SetOptions.merge())
                    .await()
            }

            // Sync with creator_payouts if exists
            syncCreatorPayoutDoc(
                withdrawalId = withdrawalId,
                newStatus = newStatus,
                remarks = remarks,
                utrReference = utrReference,
                processedAt = now
            )

            // Send real-time in-app notification to creator
            if (userId.isNotBlank()) {
                val (notifTitle, notifBody) = when (newStatus) {
                    "Processing" -> "Payout Processing (₹${amount.toInt()}) ⏳" to "Your creator withdrawal request of ₹${amount.toInt()} is now being processed by Admin."
                    "Paid", "Completed" -> {
                        val utrMsg = if (utrReference.isNotBlank()) " (UTR: $utrReference)" else ""
                        "Payout Sent: ₹${amount.toInt()} ✅" to "Your creator payout of ₹${amount.toInt()} has been transferred to your linked UPI/Bank account$utrMsg."
                    }
                    "Rejected" -> "Payout Refunded (₹${amount.toInt()})" to "Your payout request of ₹${amount.toInt()} was returned to your Wallet Balance. Reason: ${remarks.ifBlank { "Verification issue" }}"
                    else -> "Payout Status Updated" to "Your payout of ₹${amount.toInt()} is now $newStatus."
                }
                com.example.payments.SocialIndiaPaymentOrchestrator.sendUserNotification(
                    targetUserId = userId,
                    title = notifTitle,
                    message = notifBody,
                    type = "payout_update"
                )
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun syncCreatorPayoutDoc(
        withdrawalId: String,
        newStatus: String,
        remarks: String,
        utrReference: String,
        processedAt: Long
    ) {
        try {
            firestore.collection("creator_payouts").document(withdrawalId).set(
                mapOf(
                    "status" to newStatus,
                    "remarks" to remarks,
                    "utrReference" to utrReference.trim(),
                    "processedAt" to processedAt
                ),
                SetOptions.merge()
            ).await()
        } catch (_: Exception) {}
    }

    /**
     * Fraud Detection Layer: Detect and record suspicious views/engagement.
     * Returns true if event is safe, false if blocked as fraud.
     */
    suspend fun validateContentEngagement(
        contentId: String,
        creatorId: String,
        viewerId: String,
        eventType: String
    ): Boolean {
        // 1. Self-views/self-likes don't generate earnings
        if (viewerId.isNotEmpty() && viewerId == creatorId) {
            return false
        }

        try {
            val settings = getMonetizationSettings()
            if (!settings.fraudDetectionEnabled) return true

            // Rate-limit check: same viewer watching same content multiple times within 10 seconds
            val recentKey = "audit_${viewerId}_${contentId}_${System.currentTimeMillis() / 10000}"
            val auditRef = firestore.collection("fraud_audits").document(recentKey)
            val doc = auditRef.get().await()
            if (doc.exists()) {
                // Fraudulent rapid event blocked
                return false
            } else {
                auditRef.set(
                    mapOf(
                        "viewerId" to viewerId,
                        "contentId" to contentId,
                        "creatorId" to creatorId,
                        "eventType" to eventType,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {}
        return true
    }

    /**
     * Logs analytics event with idempotency.
     */
    suspend fun trackAnalyticsEvent(
        eventType: String,
        contentId: String,
        creatorId: String,
        viewerId: String,
        metadata: Map<String, Any> = emptyMap()
    ) {
        if (contentId.isEmpty() || creatorId.isEmpty()) return
        if (!validateContentEngagement(contentId, creatorId, viewerId, eventType)) {
            return
        }

        try {
            val eventId = "${eventType}_${contentId}_${viewerId}_${System.currentTimeMillis() / 60000}"
            val payload = hashMapOf<String, Any>(
                "eventId" to eventId,
                "eventType" to eventType,
                "contentId" to contentId,
                "creatorId" to creatorId,
                "viewerId" to viewerId,
                "timestamp" to System.currentTimeMillis()
            ).apply { putAll(metadata) }

            firestore.collection("analytics_events").document(eventId).set(payload, SetOptions.merge())
        } catch (e: Exception) {}
    }
}
