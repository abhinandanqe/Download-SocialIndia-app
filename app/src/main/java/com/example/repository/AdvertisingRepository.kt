package com.example.repository

import android.util.Log
import com.example.model.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

object AdvertisingRepository {
    private const val TAG = "AdvertisingRepository"
    private val firestore: FirebaseFirestore get() = RepositoryManager.firestore

    // Session cache to prevent duplicate impression spam
    private val sessionImpressionCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
    // Session hidden ads list (User ad controls)
    private val sessionHiddenAdIds = java.util.concurrent.CopyOnWriteArraySet<String>()

    fun hideAdForSession(adId: String) {
        sessionHiddenAdIds.add(adId)
    }

    fun isAdHidden(adId: String): Boolean {
        return sessionHiddenAdIds.contains(adId)
    }

    suspend fun switchAccountType(userId: String, newType: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (userId.isEmpty()) return@withContext Result.failure(Exception("User not authenticated"))
            if (newType == AccountType.BUSINESS) {
                val isEligible = RepositoryManager.verificationRepository.isUserEligibleForBusiness(userId)
                if (!isEligible) {
                    return@withContext Result.failure(Exception("Business Account requires an active Social India Verification."))
                }
            }
            firestore.collection("users").document(userId).update(
                mapOf(
                    "accountType" to newType,
                    "isProfessionalAccount" to (newType == AccountType.BUSINESS || newType == AccountType.CREATOR)
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error switching account type", e)
            Result.failure(e)
        }
    }

    suspend fun updateBusinessProfile(
        userId: String,
        businessName: String,
        category: String,
        description: String,
        website: String,
        email: String,
        phone: String,
        address: String,
        city: String,
        state: String,
        country: String,
        hours: String,
        actionButton: String,
        actionUrl: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (userId.isEmpty()) return@withContext Result.failure(Exception("User not authenticated"))
            val isEligible = RepositoryManager.verificationRepository.isUserEligibleForBusiness(userId)
            if (!isEligible) {
                return@withContext Result.failure(Exception("Business Account requires an active Social India Verification."))
            }

            val updates = mapOf(
                "accountType" to AccountType.BUSINESS,
                "isProfessionalAccount" to true,
                "businessName" to businessName,
                "businessCategory" to category,
                "businessDescription" to description,
                "businessWebsite" to website,
                "businessEmail" to email,
                "businessPhone" to phone,
                "businessAddress" to address,
                "businessCity" to city,
                "businessState" to state,
                "businessCountry" to country,
                "businessHours" to hours,
                "businessActionButton" to actionButton,
                "businessActionUrl" to actionUrl
            )
            firestore.collection("users").document(userId).update(updates).await()

            val bizProfile = BusinessProfile(
                userId = userId,
                businessName = businessName,
                category = category,
                description = description,
                website = website,
                email = email,
                phone = phone,
                address = address,
                city = city,
                state = state,
                country = country,
                hours = hours,
                actionButton = actionButton,
                actionUrl = actionUrl,
                verifiedAccountUid = userId,
                isVerified = true,
                updatedAt = System.currentTimeMillis()
            )
            firestore.collection("business_profiles").document(userId).set(bizProfile).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error updating business profile", e)
            Result.failure(e)
        }
    }

    suspend fun createCampaignWithAd(
        campaign: Campaign,
        adSet: AdSet,
        ad: Advertisement
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cId = if (campaign.campaignId.isNotEmpty()) campaign.campaignId else UUID.randomUUID().toString()
            val sId = if (adSet.adSetId.isNotEmpty()) adSet.adSetId else UUID.randomUUID().toString()
            val aId = if (ad.adId.isNotEmpty()) ad.adId else UUID.randomUUID().toString()

            val finalCampaign = campaign.copy(
                campaignId = cId,
                status = AdStatus.UNDER_REVIEW,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val finalAdSet = adSet.copy(
                adSetId = sId,
                campaignId = cId,
                status = AdStatus.ACTIVE
            )
            val finalAd = ad.copy(
                adId = aId,
                campaignId = cId,
                adSetId = sId,
                status = AdStatus.UNDER_REVIEW,
                createdAt = System.currentTimeMillis()
            )

            val batch = firestore.batch()
            val cRef = firestore.collection("ad_campaigns").document(cId)
            val sRef = firestore.collection("ad_sets").document(sId)
            val aRef = firestore.collection("ads").document(aId)

            batch.set(cRef, finalCampaign)
            batch.set(sRef, finalAdSet)
            batch.set(aRef, finalAd)

            // Record initial billing ledger entry
            val transactionId = "TXN_" + UUID.randomUUID().toString().substring(0, 8).uppercase()
            val billing = BillingTransaction(
                transactionId = transactionId,
                advertiserUid = finalAd.advertiserUid,
                campaignId = cId,
                amount = finalCampaign.budgetAmount,
                currency = finalCampaign.currency,
                status = "PENDING", // Real billing status
                paymentMethod = "Standard Billing",
                createdAt = System.currentTimeMillis(),
                invoiceNumber = "INV-" + System.currentTimeMillis().toString().takeLast(6)
            )
            val bRef = firestore.collection("billing_transactions").document(transactionId)
            batch.set(bRef, billing)

            batch.commit().await()
            Result.success(aId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error creating campaign and ad", e)
            Result.failure(e)
        }
    }

    suspend fun getEligibleAdsForPlacement(
        placement: String,
        currentUserId: String
    ): List<Advertisement> = withContext(Dispatchers.IO) {
        try {
            val querySnapshot = firestore.collection("ads")
                .whereEqualTo("status", AdStatus.ACTIVE)
                .get()
                .await()

            val now = System.currentTimeMillis()
            val eligibleAds = mutableListOf<Advertisement>()

            for (doc in querySnapshot.documents) {
                val ad = doc.toObject(Advertisement::class.java)?.copy(adId = doc.id) ?: continue

                // Check hidden by user
                if (sessionHiddenAdIds.contains(ad.adId)) continue

                // Check placement support
                if (ad.placements.isNotEmpty() && !ad.placements.contains(placement)) continue

                // Don't show own ad if not desired
                if (currentUserId.isNotEmpty() && ad.advertiserUid == currentUserId) {
                    // Allowed for testing/preview or omit for real feed
                }

                eligibleAds.add(ad)
            }

            // Shuffle slightly for fair delivery among eligible ads
            eligibleAds.shuffled()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error querying eligible ads for $placement", e)
            emptyList()
        }
    }

    suspend fun recordImpression(
        adId: String,
        campaignId: String,
        advertiserUid: String,
        placement: String,
        viewerUid: String
    ) = withContext(Dispatchers.IO) {
        if (adId.isEmpty()) return@withContext
        val cacheKey = "${viewerUid}_$adId"
        val now = System.currentTimeMillis()
        val lastSeen = sessionImpressionCache[cacheKey] ?: 0L

        // Deduplication: prevent duplicate impression inflation within 60 seconds
        if (now - lastSeen < 60_000L) {
            return@withContext
        }
        sessionImpressionCache[cacheKey] = now

        try {
            // Privacy-safe hashed viewer identifier
            val hashedViewer = hashViewerId(viewerUid)
            val eventId = UUID.randomUUID().toString()
            val event = AdImpressionEvent(
                eventId = eventId,
                adId = adId,
                campaignId = campaignId,
                advertiserUid = advertiserUid,
                viewerHashedId = hashedViewer,
                placement = placement,
                timestamp = now
            )

            val adRef = firestore.collection("ads").document(adId)
            val eventRef = firestore.collection("ad_impressions").document(eventId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(adRef)
                if (snapshot.exists()) {
                    val currentImpressions = snapshot.getLong("impressionsCount") ?: 0L
                    val currentSpend = snapshot.getDouble("spendAmount") ?: 0.0
                    // Incremental spend calculation: ~0.15 per view
                    val newSpend = currentSpend + 0.15
                    transaction.update(adRef, mapOf(
                        "impressionsCount" to (currentImpressions + 1),
                        "spendAmount" to newSpend
                    ))
                    transaction.set(eventRef, event)
                }
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record impression: ${e.message}")
        }
    }

    suspend fun recordClick(
        adId: String,
        campaignId: String,
        advertiserUid: String,
        clickType: String,
        destinationUrl: String
    ) = withContext(Dispatchers.IO) {
        if (adId.isEmpty()) return@withContext
        try {
            val eventId = UUID.randomUUID().toString()
            val event = AdClickEvent(
                eventId = eventId,
                adId = adId,
                campaignId = campaignId,
                advertiserUid = advertiserUid,
                clickType = clickType,
                destinationUrl = destinationUrl,
                timestamp = System.currentTimeMillis()
            )

            val adRef = firestore.collection("ads").document(adId)
            val eventRef = firestore.collection("ad_clicks").document(eventId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(adRef)
                if (snapshot.exists()) {
                    val currentClicks = snapshot.getLong("clicksCount") ?: 0L
                    transaction.update(adRef, "clicksCount", currentClicks + 1)
                    transaction.set(eventRef, event)
                }
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record click: ${e.message}")
        }
    }

    suspend fun getAdvertiserAds(advertiserUid: String): List<Advertisement> = withContext(Dispatchers.IO) {
        if (advertiserUid.isEmpty()) return@withContext emptyList()
        try {
            val snap = firestore.collection("ads")
                .whereEqualTo("advertiserUid", advertiserUid)
                .get()
                .await()
            snap.documents.mapNotNull { it.toObject(Advertisement::class.java)?.copy(adId = it.id) }
                .sortedByDescending { it.createdAt }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching advertiser ads", e)
            emptyList()
        }
    }

    suspend fun getAdvertiserCampaigns(advertiserUid: String): List<Campaign> = withContext(Dispatchers.IO) {
        if (advertiserUid.isEmpty()) return@withContext emptyList()
        try {
            val snap = firestore.collection("ad_campaigns")
                .whereEqualTo("advertiserUid", advertiserUid)
                .get()
                .await()
            snap.documents.mapNotNull { it.toObject(Campaign::class.java)?.copy(campaignId = it.id) }
                .sortedByDescending { it.createdAt }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching advertiser campaigns", e)
            emptyList()
        }
    }

    suspend fun getAdAnalytics(advertiserUid: String): AdAnalyticsSummary = withContext(Dispatchers.IO) {
        if (advertiserUid.isEmpty()) return@withContext AdAnalyticsSummary()
        try {
            val ads = getAdvertiserAds(advertiserUid)
            var totalImpressions = 0
            var totalClicks = 0
            var totalSpend = 0.0
            var activeCount = 0
            var pendingCount = 0

            for (ad in ads) {
                totalImpressions += ad.impressionsCount
                totalClicks += ad.clicksCount
                totalSpend += ad.spendAmount
                if (ad.status == AdStatus.ACTIVE) activeCount++
                if (ad.status == AdStatus.UNDER_REVIEW || ad.status == AdStatus.SUBMITTED) pendingCount++
            }

            val ctr = if (totalImpressions > 0) (totalClicks.toDouble() / totalImpressions.toDouble()) * 100.0 else 0.0
            val estimatedReach = (totalImpressions * 0.85).toInt() // Unique reached ratio

            AdAnalyticsSummary(
                totalImpressions = totalImpressions,
                totalReach = estimatedReach,
                totalClicks = totalClicks,
                clickThroughRate = Math.round(ctr * 100.0) / 100.0,
                totalSpend = Math.round(totalSpend * 100.0) / 100.0,
                totalEngagements = totalClicks + (totalImpressions * 0.05).toInt(),
                profileVisits = (totalClicks * 0.35).toInt(),
                websiteClicks = (totalClicks * 0.65).toInt(),
                activeCampaignsCount = activeCount,
                pendingAdsCount = pendingCount
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error computing ad analytics", e)
            AdAnalyticsSummary()
        }
    }

    suspend fun getBillingTransactions(advertiserUid: String): List<BillingTransaction> = withContext(Dispatchers.IO) {
        if (advertiserUid.isEmpty()) return@withContext emptyList()
        try {
            val snap = firestore.collection("billing_transactions")
                .whereEqualTo("advertiserUid", advertiserUid)
                .get()
                .await()
            snap.documents.mapNotNull { it.toObject(BillingTransaction::class.java)?.copy(transactionId = it.id) }
                .sortedByDescending { it.createdAt }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching billing transactions", e)
            emptyList()
        }
    }

    suspend fun recordBillingPayment(
        advertiserUid: String,
        campaignId: String,
        amount: Double,
        currency: String = "INR",
        paymentMethod: String = "UPI / Card"
    ): Result<BillingTransaction> = withContext(Dispatchers.IO) {
        try {
            val txnId = "TXN_" + UUID.randomUUID().toString().take(10).uppercase()
            val invNum = "INV-" + System.currentTimeMillis().toString().takeLast(6)
            val txn = BillingTransaction(
                transactionId = txnId,
                advertiserUid = advertiserUid,
                campaignId = campaignId,
                amount = amount,
                currency = currency,
                status = "SUCCESS",
                paymentMethod = paymentMethod,
                createdAt = System.currentTimeMillis(),
                invoiceNumber = invNum
            )

            // Server-side financial ledger calculation:
            // 18% GST (Tax)
            // 2% Payment processing fee
            // Net Platform Revenue = Gross - Tax - PG fee
            val taxAmount = Math.round(amount * 0.18 * 100.0) / 100.0
            val pgFee = Math.round(amount * 0.02 * 100.0) / 100.0
            val netPlatformRev = Math.round((amount - taxAmount - pgFee) * 100.0) / 100.0

            val revenueLedger = PlatformRevenueLedger(
                recordId = UUID.randomUUID().toString(),
                transactionId = txnId,
                grossAmount = amount,
                taxAmount = taxAmount,
                paymentProcessingFee = pgFee,
                platformRevenue = netPlatformRev,
                advertiserCredit = amount,
                currency = currency,
                createdAt = System.currentTimeMillis()
            )

            val batch = firestore.batch()
            batch.set(firestore.collection("billing_transactions").document(txnId), txn)
            batch.set(firestore.collection("platform_revenue").document(revenueLedger.recordId), revenueLedger)

            // If campaignId exists, mark campaign as ACTIVE
            if (campaignId.isNotEmpty()) {
                batch.update(firestore.collection("ad_campaigns").document(campaignId), "status", AdStatus.ACTIVE)
            }

            batch.commit().await()
            Result.success(txn)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error recording billing payment", e)
            Result.failure(e)
        }
    }

    suspend fun reportAd(report: AdReport): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val rId = if (report.reportId.isNotEmpty()) report.reportId else UUID.randomUUID().toString()
            val finalReport = report.copy(reportId = rId, timestamp = System.currentTimeMillis())
            firestore.collection("ad_reports").document(rId).set(finalReport).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error reporting ad", e)
            Result.failure(e)
        }
    }

    // ==========================================
    // ADMIN AD MANAGEMENT OPERATIONS
    // ==========================================

    suspend fun getPendingAdsForReview(): List<Advertisement> = withContext(Dispatchers.IO) {
        try {
            val snap = firestore.collection("ads")
                .whereIn("status", listOf(AdStatus.UNDER_REVIEW, AdStatus.SUBMITTED))
                .get()
                .await()
            snap.documents.mapNotNull { it.toObject(Advertisement::class.java)?.copy(adId = it.id) }
                .sortedByDescending { it.createdAt }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching pending ads", e)
            emptyList()
        }
    }

    suspend fun getAllAdsForAdmin(): List<Advertisement> = withContext(Dispatchers.IO) {
        try {
            val snap = firestore.collection("ads")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()
            snap.documents.mapNotNull { it.toObject(Advertisement::class.java)?.copy(adId = it.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching ads for admin", e)
            emptyList()
        }
    }

    suspend fun approveAd(adId: String, campaignId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val batch = firestore.batch()
            batch.update(firestore.collection("ads").document(adId), mapOf(
                "status" to AdStatus.ACTIVE,
                "rejectionReason" to ""
            ))
            if (campaignId.isNotEmpty()) {
                batch.update(firestore.collection("ad_campaigns").document(campaignId), "status", AdStatus.ACTIVE)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error approving ad", e)
            Result.failure(e)
        }
    }

    suspend fun rejectAd(adId: String, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("ads").document(adId).update(
                mapOf(
                    "status" to AdStatus.REJECTED,
                    "rejectionReason" to reason
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting ad", e)
            Result.failure(e)
        }
    }

    suspend fun pauseAd(adId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("ads").document(adId).update("status", AdStatus.PAUSED).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resumeAd(adId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("ads").document(adId).update("status", AdStatus.ACTIVE).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPlatformRevenueRecords(): List<PlatformRevenueLedger> = withContext(Dispatchers.IO) {
        try {
            val snap = firestore.collection("platform_revenue")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .await()
            snap.documents.mapNotNull { it.toObject(PlatformRevenueLedger::class.java)?.copy(recordId = it.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching platform revenue records", e)
            emptyList()
        }
    }

    suspend fun getAdReports(): List<AdReport> = withContext(Dispatchers.IO) {
        try {
            val snap = firestore.collection("ad_reports")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()
            snap.documents.mapNotNull { it.toObject(AdReport::class.java)?.copy(reportId = it.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching ad reports", e)
            emptyList()
        }
    }

    private fun hashViewerId(viewerId: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(viewerId.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            viewerId.take(16)
        }
    }
}
