package com.example.repository

import android.net.Uri
import android.util.Log
import com.example.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class VerificationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "VerificationRepo"
        const val COLLECTION_REQUESTS = "verification_requests"
        const val COLLECTION_PLANS = "verification_plans"
        const val COLLECTION_SETTINGS = "verification_settings"
        const val COLLECTION_AUDIT_LOGS = "verification_audit_logs"
        const val COLLECTION_TICKETS = "verification_support_tickets"
        const val COLLECTION_BUSINESS_PROFILES = "business_profiles"
        const val COLLECTION_NOTIFICATIONS = "notifications"
    }

    // Default seeded plans for Social India
    val defaultPlans = listOf(
        VerificationPlan(
            id = "plan_1m",
            name = "Monthly Verified",
            durationMonths = 1,
            price = 499.0,
            currency = "INR",
            benefits = listOf(
                "Official Blue Checkmark Badge",
                "Authenticity Signal & Impersonation Defense",
                "Priority Review Queue"
            ),
            isActive = true
        ),
        VerificationPlan(
            id = "plan_3m",
            name = "Quarterly Verified",
            durationMonths = 3,
            price = 1399.0,
            currency = "INR",
            benefits = listOf(
                "Official Blue Checkmark Badge",
                "Authenticity Signal & Impersonation Defense",
                "Priority Support Tickets",
                "Creator Analytics Boost"
            ),
            isActive = true
        ),
        VerificationPlan(
            id = "plan_6m",
            name = "Semi-Annual Verified",
            durationMonths = 6,
            price = 2499.0,
            currency = "INR",
            benefits = listOf(
                "Official Blue Checkmark Badge",
                "Eligible for Verified Business Account",
                "Priority Support Tickets & Dedicated Queue",
                "Advanced Creator Dashboard Tools"
            ),
            isActive = true
        ),
        VerificationPlan(
            id = "plan_12m",
            name = "Annual Enterprise Verified",
            durationMonths = 12,
            price = 4499.0,
            currency = "INR",
            benefits = listOf(
                "Official Blue Checkmark Badge for 365 Days",
                "Exclusive Eligibility for Business Account & Storefront",
                "Maximum Impersonation Protection",
                "VIP 24/7 Priority Support",
                "Early Access to All Platform Features"
            ),
            isActive = true,
            popularBadge = "Best Value"
        )
    )

    /**
     * Observes the active verification request for a specific user.
     */
    fun getUserVerificationRequest(userId: String): Flow<VerificationRequest?> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = firestore.collection(COLLECTION_REQUESTS)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching user verification request", error)
                    trySend(null)
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    trySend(null)
                } else {
                    // Pick the most recent request
                    val requests = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(VerificationRequest::class.java)?.copy(id = doc.id)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val latest = requests.maxByOrNull { it.submittedAt }
                    trySend(latest)
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Observes available verification plans.
     */
    fun getVerificationPlans(): Flow<List<VerificationPlan>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_PLANS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching plans", error)
                    trySend(defaultPlans)
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    trySend(defaultPlans)
                } else {
                    val plans = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(VerificationPlan::class.java)?.copy(id = doc.id)
                    }.filter { it.isActive }
                    
                    if (plans.isEmpty()) {
                        trySend(defaultPlans)
                    } else {
                        trySend(plans.sortedBy { it.durationMonths })
                    }
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Observes verification admin settings.
     */
    fun getVerificationSettings(): Flow<VerificationSettings> = callbackFlow {
        val listener = firestore.collection(COLLECTION_SETTINGS).document("default")
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) {
                    trySend(VerificationSettings())
                    return@addSnapshotListener
                }
                val settings = doc.toObject(VerificationSettings::class.java) ?: VerificationSettings()
                trySend(settings)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Submits a multi-step verification application.
     */
    suspend fun submitApplication(
        userId: String,
        username: String,
        userAvatarUrl: String,
        fullName: String,
        email: String,
        phone: String,
        country: String,
        category: String,
        verificationType: String,
        businessName: String = "",
        businessCategory: String = "",
        businessContact: String = "",
        businessEmail: String = "",
        businessPhone: String = "",
        businessWebsite: String = "",
        businessAddress: String = "",
        businessRegistrationNumber: String = "",
        reason: String,
        documentUri: Uri? = null,
        selfieUri: Uri? = null,
        businessDocUri: Uri? = null,
        selectedPlan: VerificationPlan
    ): Result<VerificationRequest> {
        return try {
            var docUrl = ""
            var selfieUrl = ""
            var bizDocUrl = ""

            documentUri?.let {
                docUrl = RepositoryManager.mediaRepository.uploadMedia(it) ?: ""
            }
            selfieUri?.let {
                selfieUrl = RepositoryManager.mediaRepository.uploadMedia(it) ?: ""
            }
            businessDocUri?.let {
                bizDocUrl = RepositoryManager.mediaRepository.uploadMedia(it) ?: ""
            }

            val requestId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val request = VerificationRequest(
                id = requestId,
                userId = userId,
                username = username,
                userAvatarUrl = userAvatarUrl,
                fullName = fullName,
                email = email,
                phone = phone,
                country = country,
                category = category,
                verificationType = verificationType,
                businessName = businessName,
                businessCategory = businessCategory,
                businessContact = businessContact,
                businessEmail = businessEmail,
                businessPhone = businessPhone,
                businessWebsite = businessWebsite,
                businessAddress = businessAddress,
                businessRegistrationNumber = businessRegistrationNumber,
                reason = reason,
                documentUrl = docUrl,
                selfieUrl = selfieUrl,
                businessDocUrl = bizDocUrl,
                status = VerificationStatus.APPLICATION_SUBMITTED,
                paymentStatus = "UNPAID",
                planId = selectedPlan.id,
                planName = selectedPlan.name,
                planDurationMonths = selectedPlan.durationMonths,
                planPrice = selectedPlan.price,
                planCurrency = selectedPlan.currency,
                submittedAt = now,
                timestamp = now
            )

            firestore.collection(COLLECTION_REQUESTS).document(requestId).set(request).await()

            firestore.collection("users").document(userId).update(
                mapOf(
                    "verificationRequested" to true,
                    "verificationStatus" to VerificationStatus.APPLICATION_SUBMITTED,
                    "verificationType" to verificationType
                )
            ).await()

            logAuditAction(
                verificationId = requestId,
                userId = userId,
                adminId = "system",
                action = "APPLICATION_SUBMITTED",
                notes = "User submitted verification application for $verificationType ($category)"
            )

            sendNotification(
                recipientId = userId,
                title = "Verification Application Received",
                message = "Your $verificationType verification application has been submitted and is in the review queue.",
                deepLink = "verification"
            )

            Result.success(request)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit verification application", e)
            Result.failure(e)
        }
    }

    /**
     * Submits payment reference and proof for manual admin approval.
     */
    suspend fun submitPaymentDetails(
        requestId: String,
        userId: String,
        paymentReference: String,
        proofUri: Uri?
    ): Result<Unit> {
        return try {
            var proofUrl = ""
            proofUri?.let {
                proofUrl = RepositoryManager.mediaRepository.uploadMedia(it) ?: ""
            }

            val now = System.currentTimeMillis()
            val updates = mapOf(
                "paymentReference" to paymentReference,
                "paymentProofUrl" to proofUrl,
                "paymentStatus" to "PAYMENT_PENDING_ADMIN_REVIEW",
                "status" to VerificationStatus.PAYMENT_VERIFICATION,
                "paymentSubmittedAt" to now
            )

            firestore.collection(COLLECTION_REQUESTS).document(requestId).update(updates).await()

            firestore.collection("users").document(userId).update(
                mapOf("verificationStatus" to VerificationStatus.PAYMENT_VERIFICATION)
            ).await()

            logAuditAction(
                verificationId = requestId,
                userId = userId,
                adminId = "user",
                action = "PAYMENT_SUBMITTED",
                notes = "Submitted payment reference: $paymentReference"
            )

            sendNotification(
                recipientId = userId,
                title = "Payment Verification Pending",
                message = "Your payment reference ($paymentReference) was submitted. Our admin team will verify it shortly.",
                deepLink = "verification"
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit payment details", e)
            Result.failure(e)
        }
    }

    /**
     * Checks if current user is actively verified and enforces automatic expiration.
     */
    suspend fun checkAndEnforceExpiration(userId: String) {
        if (userId.isEmpty()) return
        try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            if (!userDoc.exists()) return

            val isVerified = userDoc.getBoolean("isVerified") ?: false
            val expiry = userDoc.getLong("verificationExpiryTimestamp") ?: 0L
            val suspended = userDoc.getBoolean("verificationSuspended") ?: false

            val now = System.currentTimeMillis()
            if (isVerified && expiry > 0L && now >= expiry) {
                Log.d(TAG, "Verification expired for user $userId (now=$now, expiry=$expiry)")

                // Update user document
                firestore.collection("users").document(userId).update(
                    mapOf(
                        "isVerified" to false,
                        "verificationStatus" to VerificationStatus.EXPIRED,
                        "verificationBadgeUrl" to ""
                    )
                ).await()

                // Mark requests as EXPIRED
                val requests = firestore.collection(COLLECTION_REQUESTS)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("status", VerificationStatus.VERIFIED_ACTIVE)
                    .get()
                    .await()

                for (doc in requests.documents) {
                    doc.reference.update(mapOf("status" to VerificationStatus.EXPIRED)).await()
                }

                // If user had a business account, restrict it
                firestore.collection(COLLECTION_BUSINESS_PROFILES).document(userId).update(
                    mapOf("isVerified" to false)
                ).await()

                logAuditAction(
                    verificationId = "auto_expire",
                    userId = userId,
                    adminId = "system_scheduler",
                    action = "VERIFICATION_EXPIRED",
                    notes = "Verification automatically expired on reaching $expiry"
                )

                sendNotification(
                    recipientId = userId,
                    title = "Verification Expired",
                    message = "Your Social India Blue Badge has expired. Tap here to renew your plan.",
                    deepLink = "verification"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking expiration", e)
        }
    }

    /**
     * Checks whether user is eligible for Business Account.
     * Enforces the hard rule: User must have active, unexpired, non-suspended verification.
     */
    suspend fun isUserEligibleForBusiness(userId: String): Boolean {
        if (userId.isEmpty()) return false
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            if (!doc.exists()) return false

            val isVerified = doc.getBoolean("isVerified") ?: false
            val status = doc.getString("verificationStatus") ?: ""
            val suspended = doc.getBoolean("verificationSuspended") ?: false
            val expiry = doc.getLong("verificationExpiryTimestamp") ?: 0L
            val now = System.currentTimeMillis()

            if (suspended) return false
            if (expiry > 0L && now >= expiry) return false

            isVerified || status == "approved" || status == VerificationStatus.VERIFIED_ACTIVE
        } catch (e: Exception) {
            Log.e(TAG, "Error checking business eligibility", e)
            false
        }
    }

    /**
     * Submits a renewal application.
     */
    suspend fun submitRenewal(
        userId: String,
        previousVerificationId: String,
        selectedPlan: VerificationPlan
    ): Result<VerificationRequest> {
        return try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            val username = userDoc.getString("username") ?: ""
            val avatarUrl = userDoc.getString("avatar_url") ?: ""
            val fullName = userDoc.getString("displayName") ?: username
            val vType = userDoc.getString("verificationType") ?: VerificationType.PERSONAL

            val requestId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val request = VerificationRequest(
                id = requestId,
                userId = userId,
                username = username,
                userAvatarUrl = avatarUrl,
                fullName = fullName,
                verificationType = vType,
                status = VerificationStatus.PAYMENT_PENDING,
                paymentStatus = "UNPAID",
                planId = selectedPlan.id,
                planName = selectedPlan.name,
                planDurationMonths = selectedPlan.durationMonths,
                planPrice = selectedPlan.price,
                planCurrency = selectedPlan.currency,
                isRenewal = true,
                previousVerificationId = previousVerificationId,
                submittedAt = now,
                timestamp = now
            )

            firestore.collection(COLLECTION_REQUESTS).document(requestId).set(request).await()

            firestore.collection("users").document(userId).update(
                mapOf(
                    "verificationStatus" to VerificationStatus.PAYMENT_PENDING,
                    "verificationRequested" to true
                )
            ).await()

            logAuditAction(
                verificationId = requestId,
                userId = userId,
                adminId = "user",
                action = "RENEWAL_REQUESTED",
                notes = "Renewal requested for plan ${selectedPlan.name}"
            )

            Result.success(request)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit renewal", e)
            Result.failure(e)
        }
    }

    // ----------------------------------------------------
    // ADMIN ACTIONS
    // ----------------------------------------------------

    fun getAllAdminRequests(): Flow<List<VerificationRequest>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_REQUESTS)
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error loading admin requests", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(VerificationRequest::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }

        awaitClose { listener.remove() }
    }

    suspend fun adminApproveApplication(requestId: String, adminId: String, notes: String): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            firestore.collection(COLLECTION_REQUESTS).document(requestId).update(
                mapOf(
                    "status" to VerificationStatus.PAYMENT_PENDING,
                    "reviewedAt" to now,
                    "adminId" to adminId,
                    "adminNotes" to notes
                )
            ).await()

            val req = firestore.collection(COLLECTION_REQUESTS).document(requestId).get().await()
                .toObject(VerificationRequest::class.java)

            req?.let {
                firestore.collection("users").document(it.userId).update(
                    mapOf("verificationStatus" to VerificationStatus.PAYMENT_PENDING)
                ).await()

                sendNotification(
                    recipientId = it.userId,
                    title = "Application Approved!",
                    message = "Your documents have been approved. Please complete the subscription payment to activate your Blue Badge.",
                    deepLink = "verification"
                )
            }

            logAuditAction(
                verificationId = requestId,
                userId = req?.userId ?: "",
                adminId = adminId,
                action = "APPLICATION_APPROVED",
                notes = notes
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adminRejectApplication(requestId: String, adminId: String, reason: String): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            firestore.collection(COLLECTION_REQUESTS).document(requestId).update(
                mapOf(
                    "status" to VerificationStatus.REJECTED,
                    "rejectionReason" to reason,
                    "reviewedAt" to now,
                    "adminId" to adminId
                )
            ).await()

            val req = firestore.collection(COLLECTION_REQUESTS).document(requestId).get().await()
                .toObject(VerificationRequest::class.java)

            req?.let {
                firestore.collection("users").document(it.userId).update(
                    mapOf(
                        "isVerified" to false,
                        "verificationStatus" to VerificationStatus.REJECTED,
                        "verificationRequested" to false
                    )
                ).await()

                sendNotification(
                    recipientId = it.userId,
                    title = "Verification Request Update",
                    message = "Your verification request could not be approved at this time: $reason",
                    deepLink = "verification"
                )
            }

            logAuditAction(
                verificationId = requestId,
                userId = req?.userId ?: "",
                adminId = adminId,
                action = "APPLICATION_REJECTED",
                notes = reason
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Admin manually approves the payment and activates the verification.
     */
    suspend fun adminApprovePayment(requestId: String, adminId: String, notes: String): Result<Unit> {
        return try {
            val reqDoc = firestore.collection(COLLECTION_REQUESTS).document(requestId).get().await()
            val req = reqDoc.toObject(VerificationRequest::class.java) ?: throw Exception("Request not found")

            val now = System.currentTimeMillis()
            val durationMonths = if (req.planDurationMonths > 0) req.planDurationMonths else 12
            val durationMillis = durationMonths.toLong() * 30L * 24 * 3600 * 1000L

            val userDoc = firestore.collection("users").document(req.userId).get().await()
            val existingExpiry = userDoc.getLong("verificationExpiryTimestamp") ?: 0L

            val startTimestamp = if (req.isRenewal && existingExpiry > now) {
                userDoc.getLong("verificationStartTimestamp") ?: now
            } else {
                now
            }

            val expiryTimestamp = if (req.isRenewal && existingExpiry > now) {
                existingExpiry + durationMillis
            } else {
                now + durationMillis
            }

            val badgeUrl = "https://i.ibb.co/8Lhv9YND/image.png"

            // Update verification request
            firestore.collection(COLLECTION_REQUESTS).document(requestId).update(
                mapOf(
                    "status" to VerificationStatus.VERIFIED_ACTIVE,
                    "paymentStatus" to "APPROVED",
                    "paymentApprovedAt" to now,
                    "verifiedAt" to now,
                    "startTimestamp" to startTimestamp,
                    "expiryTimestamp" to expiryTimestamp,
                    "adminId" to adminId,
                    "adminNotes" to notes
                )
            ).await()

            // Update User profile
            firestore.collection("users").document(req.userId).update(
                mapOf(
                    "isVerified" to true,
                    "verificationStatus" to "approved",
                    "verificationType" to req.verificationType,
                    "verificationBadgeUrl" to badgeUrl,
                    "verificationStartTimestamp" to startTimestamp,
                    "verificationExpiryTimestamp" to expiryTimestamp,
                    "verificationPlanId" to req.planId,
                    "verificationPlanDurationMonths" to durationMonths,
                    "verificationSuspended" to false,
                    "verificationRequested" to false
                )
            ).await()

            logAuditAction(
                verificationId = requestId,
                userId = req.userId,
                adminId = adminId,
                action = "PAYMENT_APPROVED_AND_ACTIVATED",
                notes = "Verification activated until timestamp $expiryTimestamp"
            )

            sendNotification(
                recipientId = req.userId,
                title = "✓ Social India Verified!",
                message = "Congratulations! Your payment has been approved and your official verification badge is now live.",
                deepLink = "verification"
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error approving payment", e)
            Result.failure(e)
        }
    }

    suspend fun adminRejectPayment(requestId: String, adminId: String, reason: String): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_REQUESTS).document(requestId).update(
                mapOf(
                    "paymentStatus" to "REJECTED",
                    "status" to VerificationStatus.PAYMENT_PENDING,
                    "rejectionReason" to reason,
                    "adminId" to adminId
                )
            ).await()

            val req = firestore.collection(COLLECTION_REQUESTS).document(requestId).get().await()
                .toObject(VerificationRequest::class.java)

            req?.let {
                firestore.collection("users").document(it.userId).update(
                    mapOf("verificationStatus" to VerificationStatus.PAYMENT_PENDING)
                ).await()

                sendNotification(
                    recipientId = it.userId,
                    title = "Payment Verification Unsuccessful",
                    message = "Your payment reference could not be verified: $reason. Please re-submit valid payment details.",
                    deepLink = "verification"
                )
            }

            logAuditAction(
                verificationId = requestId,
                userId = req?.userId ?: "",
                adminId = adminId,
                action = "PAYMENT_REJECTED",
                notes = reason
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adminSuspendVerification(requestId: String, adminId: String, reason: String): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_REQUESTS).document(requestId).update(
                mapOf(
                    "status" to VerificationStatus.SUSPENDED,
                    "suspensionReason" to reason
                )
            ).await()

            val req = firestore.collection(COLLECTION_REQUESTS).document(requestId).get().await()
                .toObject(VerificationRequest::class.java)

            req?.let {
                firestore.collection("users").document(it.userId).update(
                    mapOf(
                        "isVerified" to false,
                        "verificationSuspended" to true,
                        "verificationStatus" to VerificationStatus.SUSPENDED
                    )
                ).await()

                sendNotification(
                    recipientId = it.userId,
                    title = "Verification Suspended",
                    message = "Your verification badge has been suspended: $reason",
                    deepLink = "verification"
                )
            }

            logAuditAction(
                verificationId = requestId,
                userId = req?.userId ?: "",
                adminId = adminId,
                action = "VERIFICATION_SUSPENDED",
                notes = reason
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adminRestoreVerification(requestId: String, adminId: String): Result<Unit> {
        return try {
            val reqDoc = firestore.collection(COLLECTION_REQUESTS).document(requestId).get().await()
            val req = reqDoc.toObject(VerificationRequest::class.java) ?: throw Exception("Request not found")

            val now = System.currentTimeMillis()
            val isValid = req.expiryTimestamp > now

            val newStatus = if (isValid) VerificationStatus.VERIFIED_ACTIVE else VerificationStatus.EXPIRED

            firestore.collection(COLLECTION_REQUESTS).document(requestId).update(
                mapOf(
                    "status" to newStatus,
                    "suspensionReason" to ""
                )
            ).await()

            firestore.collection("users").document(req.userId).update(
                mapOf(
                    "isVerified" to isValid,
                    "verificationSuspended" to false,
                    "verificationStatus" to if (isValid) "approved" else VerificationStatus.EXPIRED
                )
            ).await()

            logAuditAction(
                verificationId = requestId,
                userId = req.userId,
                adminId = adminId,
                action = "VERIFICATION_RESTORED",
                notes = "Verification restored by admin"
            )

            sendNotification(
                recipientId = req.userId,
                title = "Verification Restored",
                message = "Your verification status has been restored.",
                deepLink = "verification"
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adminExtendVerification(requestId: String, adminId: String, additionalMonths: Int): Result<Unit> {
        return try {
            val reqDoc = firestore.collection(COLLECTION_REQUESTS).document(requestId).get().await()
            val req = reqDoc.toObject(VerificationRequest::class.java) ?: throw Exception("Request not found")

            val now = System.currentTimeMillis()
            val baseTime = if (req.expiryTimestamp > now) req.expiryTimestamp else now
            val addedMillis = additionalMonths.toLong() * 30L * 24 * 3600 * 1000L
            val newExpiry = baseTime + addedMillis

            firestore.collection(COLLECTION_REQUESTS).document(requestId).update(
                mapOf(
                    "expiryTimestamp" to newExpiry,
                    "status" to VerificationStatus.VERIFIED_ACTIVE
                )
            ).await()

            firestore.collection("users").document(req.userId).update(
                mapOf(
                    "isVerified" to true,
                    "verificationExpiryTimestamp" to newExpiry,
                    "verificationStatus" to "approved"
                )
            ).await()

            logAuditAction(
                verificationId = requestId,
                userId = req.userId,
                adminId = adminId,
                action = "VERIFICATION_EXTENDED",
                notes = "Extended by $additionalMonths months until $newExpiry"
            )

            sendNotification(
                recipientId = req.userId,
                title = "Verification Extended",
                message = "Your verification period has been extended by $additionalMonths months.",
                deepLink = "verification"
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveAdminSettings(settings: VerificationSettings): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_SETTINGS).document("default").set(settings).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveVerificationPlan(plan: VerificationPlan): Result<Unit> {
        return try {
            val id = if (plan.id.isNotEmpty()) plan.id else "plan_${System.currentTimeMillis()}"
            firestore.collection(COLLECTION_PLANS).document(id).set(plan.copy(id = id)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ----------------------------------------------------
    // AUDIT LOGS
    // ----------------------------------------------------

    private suspend fun logAuditAction(
        verificationId: String,
        userId: String,
        adminId: String,
        action: String,
        notes: String
    ) {
        try {
            val logId = UUID.randomUUID().toString()
            val log = VerificationAuditLog(
                id = logId,
                verificationId = verificationId,
                userId = userId,
                adminId = adminId,
                action = action,
                notes = notes,
                timestamp = System.currentTimeMillis()
            )
            firestore.collection(COLLECTION_AUDIT_LOGS).document(logId).set(log).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing audit log", e)
        }
    }

    fun getAuditLogs(verificationId: String? = null): Flow<List<VerificationAuditLog>> = callbackFlow {
        var query: Query = firestore.collection(COLLECTION_AUDIT_LOGS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)

        if (!verificationId.isNullOrEmpty()) {
            query = query.whereEqualTo("verificationId", verificationId)
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val logs = snapshot?.documents?.mapNotNull { it.toObject(VerificationAuditLog::class.java) } ?: emptyList()
            trySend(logs)
        }

        awaitClose { listener.remove() }
    }

    // ----------------------------------------------------
    // SUPPORT TICKETS (PART 20)
    // ----------------------------------------------------

    suspend fun createSupportTicket(
        userId: String,
        username: String,
        subject: String,
        message: String,
        category: String = "Verification Support"
    ): Result<Unit> {
        return try {
            val isVerified = isUserEligibleForBusiness(userId)
            val ticketId = UUID.randomUUID().toString()
            val ticket = VerificationSupportTicket(
                id = ticketId,
                userId = userId,
                username = username,
                category = category,
                subject = subject,
                message = message,
                status = "OPEN",
                isPriority = isVerified,
                createdAt = System.currentTimeMillis()
            )
            firestore.collection(COLLECTION_TICKETS).document(ticketId).set(ticket).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getUserSupportTickets(userId: String): Flow<List<VerificationSupportTicket>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_TICKETS)
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(VerificationSupportTicket::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    private suspend fun sendNotification(recipientId: String, title: String, message: String, deepLink: String) {
        try {
            val notifId = UUID.randomUUID().toString()
            val data = mapOf(
                "id" to notifId,
                "userId" to recipientId,
                "title" to title,
                "body" to message,
                "message" to message,
                "deepLink" to deepLink,
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false,
                "type" to "verification"
            )
            firestore.collection(COLLECTION_NOTIFICATIONS).document(notifId).set(data).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending verification notification", e)
        }
    }
}
