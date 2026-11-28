package com.example.repository

import android.util.Log
import com.example.BuildConfig
import com.example.model.MonetizationSettings
import com.example.model.RepositoryManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

const val EXCLUSIVE_APPROVAL_WINDOW_MS = 48L * 60L * 60L * 1000L

fun isExclusiveStatusUnlocked(status: String?): Boolean {
    val s = status?.trim()?.lowercase(Locale.US) ?: return false
    return s == "approved" || s == "completed"
}

fun isExclusiveStatusPending(status: String?): Boolean {
    val s = status?.trim()?.lowercase(Locale.US) ?: return false
    return s == "pending_approval" || s == "pending"
}

fun canBuyerPurchaseExclusive(existingStatus: String?): Boolean {
    return !isExclusiveStatusUnlocked(existingStatus) && !isExclusiveStatusPending(existingStatus)
}

fun isExclusiveRequestExpired(status: String?, expiresAt: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
    return isExclusiveStatusPending(status) && expiresAt > 0L && nowMs >= expiresAt
}

data class ExclusiveCommissionSplit(
    val grossAmount: Double,
    val commissionPct: Double,
    val commissionAmount: Double,
    val creatorNetAmount: Double
)

fun computeExclusiveSplit(priceInr: Double, commissionPct: Double): ExclusiveCommissionSplit {
    val safePct = commissionPct.coerceIn(0.0, 50.0)
    val safePrice = priceInr.coerceAtLeast(0.0)
    val fee = (safePrice * safePct) / 100.0
    val net = (safePrice - fee).coerceAtLeast(0.0)
    return ExclusiveCommissionSplit(
        grossAmount = safePrice,
        commissionPct = safePct,
        commissionAmount = fee,
        creatorNetAmount = net
    )
}

data class WalletBalanceTransition(
    val newPendingBalance: Double,
    val newAvailableBalance: Double
)

fun computeApprovalBalanceTransition(
    currentPendingBalance: Double,
    currentAvailableBalance: Double,
    netAmount: Double
): WalletBalanceTransition {
    val settled = netAmount.coerceAtLeast(0.0)
    return WalletBalanceTransition(
        newPendingBalance = (currentPendingBalance - settled).coerceAtLeast(0.0),
        newAvailableBalance = currentAvailableBalance + settled
    )
}

data class VaultMediaPayload(
    val contentId: String = "",
    val creatorId: String = "",
    val contentType: String = "POST", // POST, REEL, STORY
    val mediaUrl: String = "",
    val postUrls: List<String> = emptyList(),
    val thumbnailUrl: String = ""
)

data class ExclusivePurchaseRecord(
    val id: String = "",
    val buyerId: String = "",
    val buyerUsername: String = "",
    val buyerAvatar: String = "",
    val contentId: String = "",
    val contentType: String = "POST", // POST, REEL, STORY
    val creatorId: String = "",
    val creatorUsername: String = "",
    val creatorAvatar: String = "",
    val title: String = "",
    val teaserCaption: String = "",
    val blurredThumbnailUrl: String = "",
    val amount: Double = 0.0,
    val platformCommissionPct: Double = 15.0,
    val platformCommissionAmount: Double = 0.0,
    val creatorNetAmount: Double = 0.0,
    val status: String = "pending_approval", // pending_approval, approved, rejected, refunded (legacy: COMPLETED, PENDING, REFUNDED)
    val paymentMethod: String = "WALLET", // WALLET, UPI_RAZORPAY
    val paymentVerified: Boolean = false,
    val transactionRef: String = "",
    val expiresAt: Long = 0L,
    val approvedAt: Long = 0L,
    val refundedAt: Long = 0L,
    val refundReason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class WalletSnapshot(
    val userId: String = "",
    val availableBalance: Double = 0.0,
    val pendingBalance: Double = 0.0,
    val coinsBalance: Long = 0L,
    val currency: String = "INR"
)

data class ExclusiveReportRecord(
    val id: String = "",
    val contentId: String = "",
    val contentType: String = "POST",
    val creatorId: String = "",
    val creatorUsername: String = "",
    val reporterId: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "OPEN", // OPEN, REMOVED, DISMISSED
    val timestamp: Long = System.currentTimeMillis()
)

data class CreatorExclusiveItemStat(
    val contentId: String,
    val contentType: String,
    val title: String,
    val teaserCaption: String,
    val thumbnailUrl: String,
    val priceInr: Double,
    val unlockCount: Int,
    val grossRevenueInr: Double,
    val netRevenueInr: Double,
    val createdAt: Long
)

data class CreatorExclusiveDashboardStats(
    val totalGrossRevenue: Double = 0.0,
    val totalNetRevenue: Double = 0.0,
    val totalPlatformCommission: Double = 0.0,
    val totalUnlocks: Int = 0,
    val pendingBalance: Double = 0.0,
    val availableBalance: Double = 0.0,
    val items: List<CreatorExclusiveItemStat> = emptyList(),
    val recentPurchases: List<ExclusivePurchaseRecord> = emptyList()
)

sealed class ExclusiveUnlockResult {
    data class PendingApproval(
        val purchaseId: String,
        val expiresAt: Long,
        val record: ExclusivePurchaseRecord
    ) : ExclusiveUnlockResult()

    data class Success(
        val purchaseId: String,
        val unlockedMedia: VaultMediaPayload?,
        val alreadyOwned: Boolean = false
    ) : ExclusiveUnlockResult()

    data class InsufficientWallet(
        val currentBalance: Double,
        val requiredAmount: Double
    ) : ExclusiveUnlockResult()

    data class Failure(val message: String) : ExclusiveUnlockResult()
}

sealed class ExclusiveActionResult {
    data class Success(val message: String) : ExclusiveActionResult()
    data class Failure(val message: String) : ExclusiveActionResult()
}

/**
 * Authoritative Exclusive Content Repository:
 * - Isolates original high-res media URLs inside `exclusive_media_vault/{contentId}` (protected by Firestore Rules
 *   and `getExclusiveSignedMediaUrl` Cloud Function) so locked viewers NEVER receive original URLs.
 * - Executes paid unlock flow with mandatory Creator Approval within 48 hours:
 *   1. Verified payment creates `exclusive_purchases/{buyerId}_{contentId}` with status `"pending_approval"`
 *      and `content_entitlements.active = false`.
 *   2. Creator net payout sits in Creator's `pendingBalance` (`users` + `wallets`).
 *   3. Creator Approve -> status `"approved"`, `active = true`, live viewer unlock, funds move `pendingBalance` -> `availableBalance`.
 *   4. Creator Reject OR 48h timeout -> auto-refund to Buyer wallet, remove from Creator `pendingBalance`, notify both.
 */
object ExclusiveContentRepository {
    private const val TAG = "ExclusiveContentRepo"

    private val db get() = RepositoryManager.firestore
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val vaultCache = ConcurrentHashMap<String, VaultMediaPayload>()

    fun getCachedVaultMedia(contentId: String): VaultMediaPayload? = vaultCache[contentId]

    fun clearVaultCache() {
        vaultCache.clear()
    }

    private fun cloudFunctionUrl(functionName: String): String {
        val base = try {
            BuildConfig.CLOUD_FUNCTIONS_BASE_URL.trimEnd('/')
        } catch (_: Throwable) {
            "https://us-central1-socialindia-2cdf0.cloudfunctions.net"
        }.ifBlank { "https://us-central1-socialindia-2cdf0.cloudfunctions.net" }
        return "$base/$functionName"
    }

    private suspend fun callAuthenticatedCloudFunction(
        functionName: String,
        payload: JSONObject
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
            val body = JSONObject().apply { put("data", payload) }
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(cloudFunctionUrl(functionName))
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}: $raw"))
                }
                val parsed = try {
                    val root = JSONObject(raw)
                    root.optJSONObject("result") ?: root.optJSONObject("data") ?: root
                } catch (_: Exception) {
                    JSONObject()
                }
                Result.success(parsed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun ensureWalletDocument(userId: String): WalletSnapshot {
        if (userId.isBlank()) return WalletSnapshot()
        return try {
            val userSnap = db.collection("users").document(userId).get().await()
            val walletRef = db.collection("wallets").document(userId)
            val walletSnap = walletRef.get().await()

            val userAvailable = userSnap.getDouble("walletBalance") ?: userSnap.getDouble("earnings") ?: 0.0
            val userPending = userSnap.getDouble("pendingBalance") ?: 0.0
            val userCoins = userSnap.getLong("coins") ?: (userSnap.getDouble("coins")?.toLong() ?: 0L)

            if (!walletSnap.exists()) {
                val initData = hashMapOf(
                    "userId" to userId,
                    "availableBalance" to userAvailable,
                    "pendingBalance" to userPending,
                    "coinsBalance" to userCoins,
                    "currency" to "INR",
                    "updatedAt" to System.currentTimeMillis()
                )
                walletRef.set(initData, SetOptions.merge()).await()
                WalletSnapshot(
                    userId = userId,
                    availableBalance = userAvailable,
                    pendingBalance = userPending,
                    coinsBalance = userCoins
                )
            } else {
                val wAvail = walletSnap.getDouble("availableBalance") ?: userAvailable
                val wPend = walletSnap.getDouble("pendingBalance") ?: userPending
                val wCoins = walletSnap.getLong("coinsBalance") ?: userCoins
                WalletSnapshot(
                    userId = userId,
                    availableBalance = maxOf(userAvailable, wAvail),
                    pendingBalance = maxOf(userPending, wPend),
                    coinsBalance = maxOf(userCoins, wCoins)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "ensureWalletDocument warning: ${e.message}")
            WalletSnapshot(userId = userId)
        }
    }

    fun observeWalletDocument(userId: String): Flow<WalletSnapshot> = callbackFlow {
        if (userId.isBlank()) {
            trySend(WalletSnapshot())
            close()
            return@callbackFlow
        }
        val reg = db.collection("wallets").document(userId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
                trySend(
                    WalletSnapshot(
                        userId = userId,
                        availableBalance = snap.getDouble("availableBalance") ?: 0.0,
                        pendingBalance = snap.getDouble("pendingBalance") ?: 0.0,
                        coinsBalance = snap.getLong("coinsBalance") ?: 0L,
                        currency = snap.getString("currency") ?: "INR"
                    )
                )
            }
        awaitClose { reg.remove() }
    }

    suspend fun saveVaultMedia(
        contentId: String,
        creatorId: String,
        contentType: String,
        mediaUrl: String,
        postUrls: List<String> = emptyList(),
        thumbnailUrl: String = ""
    ): Boolean {
        if (contentId.isBlank() || creatorId.isBlank()) return false
        return try {
            val payload = hashMapOf(
                "contentId" to contentId,
                "creatorId" to creatorId,
                "contentType" to contentType.uppercase(),
                "mediaUrl" to mediaUrl,
                "postUrls" to postUrls,
                "thumbnailUrl" to thumbnailUrl.ifBlank { mediaUrl },
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("exclusive_media_vault")
                .document(contentId)
                .set(payload, SetOptions.merge())
                .await()

            vaultCache[contentId] = VaultMediaPayload(
                contentId = contentId,
                creatorId = creatorId,
                contentType = contentType.uppercase(),
                mediaUrl = mediaUrl,
                postUrls = postUrls,
                thumbnailUrl = thumbnailUrl.ifBlank { mediaUrl }
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write exclusive vault media for $contentId", e)
            false
        }
    }

    /**
     * Resolves the real media URL ONLY if authorized (creator OR approved entitlement).
     * Tries the server-side `getExclusiveSignedMediaUrl` Cloud Function first for signed URL issuance,
     * falling back to security-rule-protected `exclusive_media_vault/{contentId}` read.
     */
    suspend fun resolveUnlockedMedia(
        contentId: String,
        forceRefresh: Boolean = false
    ): VaultMediaPayload? {
        if (contentId.isBlank()) return null
        if (!forceRefresh) {
            vaultCache[contentId]?.let { return it }
        }

        // Try server-side signed URL endpoint first
        val serverResp = callAuthenticatedCloudFunction(
            "getExclusiveSignedMediaUrl",
            JSONObject().put("contentId", contentId)
        )
        if (serverResp.isSuccess) {
            val json = serverResp.getOrNull()
            if (json != null && json.optBoolean("authorized", false)) {
                val signedUrl = json.optString("mediaUrl", "")
                val signedList = mutableListOf<String>()
                val arr = json.optJSONArray("postUrls")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).takeIf { it.isNotBlank() }?.let { signedList.add(it) }
                    }
                }
                val payload = VaultMediaPayload(
                    contentId = contentId,
                    creatorId = json.optString("creatorId", ""),
                    contentType = json.optString("contentType", "POST"),
                    mediaUrl = signedUrl,
                    postUrls = signedList,
                    thumbnailUrl = json.optString("thumbnailUrl", signedUrl)
                )
                if (payload.mediaUrl.isNotBlank() || payload.postUrls.isNotEmpty()) {
                    vaultCache[contentId] = payload
                    return payload
                }
            }
        }

        return try {
            val doc = db.collection("exclusive_media_vault").document(contentId).get().await()
            if (!doc.exists()) return null
            val payload = VaultMediaPayload(
                contentId = contentId,
                creatorId = doc.getString("creatorId") ?: "",
                contentType = doc.getString("contentType") ?: "POST",
                mediaUrl = doc.getString("mediaUrl") ?: "",
                postUrls = (doc.get("postUrls") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                thumbnailUrl = doc.getString("thumbnailUrl") ?: ""
            )
            if (payload.mediaUrl.isNotBlank() || payload.postUrls.isNotEmpty()) {
                vaultCache[contentId] = payload
            }
            payload
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Vault media locked or unavailable for $contentId: ${e.message}")
            null
        }
    }

    suspend fun fetchMonetizationSettings(): MonetizationSettings {
        return try {
            val doc = db.collection("admin_settings").document("monetization_settings").get().await()
            doc.toObject(MonetizationSettings::class.java) ?: MonetizationSettings()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            MonetizationSettings()
        }
    }

    suspend fun getUserWalletBalance(userId: String): Double {
        if (userId.isBlank()) return 0.0
        return try {
            val doc = db.collection("users").document(userId).get().await()
            doc.getDouble("walletBalance") ?: 0.0
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            0.0
        }
    }

    /**
     * Wallet purchase for Exclusive Content with mandatory Creator Approval (48h window):
     * - Deducts buyer's available wallet balance
     * - Adds creator's net share to Creator `pendingBalance` (`users` + `wallets`), NOT `availableBalance`
     * - Creates `exclusive_purchases/{buyerId}_{contentId}` with `status = "pending_approval"`
     * - Creates `content_entitlements/{buyerId}_{contentId}` with `active = false` (locked until Creator approves)
     * - Prevents duplicate purchase if already `pending_approval` or `approved`/`COMPLETED`
     */
    suspend fun unlockWithWallet(
        buyerId: String,
        creatorId: String,
        contentId: String,
        contentType: String,
        title: String,
        teaserCaption: String,
        blurredThumbnailUrl: String,
        creatorUsername: String,
        creatorAvatar: String,
        priceInr: Double
    ): ExclusiveUnlockResult {
        if (buyerId.isBlank() || creatorId.isBlank() || contentId.isBlank()) {
            return ExclusiveUnlockResult.Failure("Please sign in to unlock exclusive content.")
        }
        if (buyerId == creatorId) {
            val media = resolveUnlockedMedia(contentId, forceRefresh = true)
            return ExclusiveUnlockResult.Success("${buyerId}_${contentId}", media, alreadyOwned = true)
        }

        val settings = fetchMonetizationSettings()
        val safePrice = priceInr.coerceAtLeast(settings.exclusiveMinPriceInr.coerceAtLeast(10.0))
        val split = computeExclusiveSplit(safePrice, settings.exclusiveCommissionPct)
        val commissionPct = split.commissionPct
        val commissionAmount = split.commissionAmount
        val creatorNetAmount = split.creatorNetAmount

        val deterministicId = "${buyerId}_${contentId}"
        val now = System.currentTimeMillis()
        val expiresAt = now + EXCLUSIVE_APPROVAL_WINDOW_MS
        val txnRef = "EXW_${now}_${UUID.randomUUID().toString().take(6).uppercase()}"

        val entitlementRef = db.collection("content_entitlements").document(deterministicId)
        val purchaseRef = db.collection("exclusive_purchases").document(deterministicId)
        val buyerRef = db.collection("users").document(buyerId)
        val creatorRef = db.collection("users").document(creatorId)
        val creatorWalletRef = db.collection("wallets").document(creatorId)
        val buyerWalletRef = db.collection("wallets").document(buyerId)
        val buyerWalletTxnRef = db.collection("wallet_transactions").document("WTX_BUY_${txnRef}")
        val creatorWalletTxnRef = db.collection("wallet_transactions").document("WTX_CR_${txnRef}")

        return try {
            var alreadyOwned = false
            var alreadyPendingRecord: ExclusivePurchaseRecord? = null
            var insufficientBalanceValue: Double? = null
            var buyerUsername = "A fan"
            var buyerAvatar = ""

            db.runTransaction { txn ->
                val existingEntitlement = txn.get(entitlementRef)
                val existingPurchase = txn.get(purchaseRef)
                val existingStatus = existingPurchase.getString("status")

                if ((existingEntitlement.exists() && existingEntitlement.getBoolean("active") == true) ||
                    isExclusiveStatusUnlocked(existingStatus)
                ) {
                    alreadyOwned = true
                    return@runTransaction null
                }

                if (existingPurchase.exists() && isExclusiveStatusPending(existingStatus)) {
                    alreadyPendingRecord = existingPurchase.toObject(ExclusivePurchaseRecord::class.java)
                        ?.copy(id = existingPurchase.id)
                    return@runTransaction null
                }

                val buyerSnap = txn.get(buyerRef)
                val buyerBalance = buyerSnap.getDouble("walletBalance") ?: 0.0
                buyerUsername = buyerSnap.getString("username")?.takeIf { it.isNotBlank() } ?: "Someone"
                buyerAvatar = buyerSnap.getString("avatarUrl") ?: ""

                if (buyerBalance < safePrice) {
                    insufficientBalanceValue = buyerBalance
                    return@runTransaction null
                }

                val creatorSnap = txn.get(creatorRef)
                val newBuyerBalance = (buyerBalance - safePrice).coerceAtLeast(0.0)
                txn.update(buyerRef, mapOf("walletBalance" to newBuyerBalance))
                txn.set(
                    buyerWalletRef,
                    mapOf(
                        "userId" to buyerId,
                        "availableBalance" to newBuyerBalance,
                        "currency" to "INR",
                        "updatedAt" to now
                    ),
                    SetOptions.merge()
                )

                if (creatorSnap.exists()) {
                    val curPending = creatorSnap.getDouble("pendingBalance") ?: 0.0
                    txn.update(creatorRef, mapOf("pendingBalance" to (curPending + creatorNetAmount)))
                }
                txn.set(
                    creatorWalletRef,
                    mapOf(
                        "userId" to creatorId,
                        "pendingBalance" to FieldValue.increment(creatorNetAmount),
                        "currency" to "INR",
                        "updatedAt" to now
                    ),
                    SetOptions.merge()
                )

                // Entitlement remains LOCKED until Creator approves
                txn.set(
                    entitlementRef,
                    mapOf(
                        "entitlementId" to deterministicId,
                        "userId" to buyerId,
                        "creatorId" to creatorId,
                        "contentId" to contentId,
                        "contentType" to contentType.uppercase(),
                        "entitlementType" to "EXCLUSIVE_UNLOCK",
                        "active" to false,
                        "amountPaid" to safePrice,
                        "validUntil" to -1L,
                        "orderId" to txnRef,
                        "grantedAt" to now
                    ),
                    SetOptions.merge()
                )

                txn.set(
                    purchaseRef,
                    mapOf(
                        "id" to deterministicId,
                        "buyerId" to buyerId,
                        "buyerUsername" to buyerUsername,
                        "buyerAvatar" to buyerAvatar,
                        "contentId" to contentId,
                        "contentType" to contentType.uppercase(),
                        "creatorId" to creatorId,
                        "creatorUsername" to creatorUsername,
                        "creatorAvatar" to creatorAvatar,
                        "title" to title,
                        "teaserCaption" to teaserCaption,
                        "blurredThumbnailUrl" to blurredThumbnailUrl,
                        "amount" to safePrice,
                        "platformCommissionPct" to commissionPct,
                        "platformCommissionAmount" to commissionAmount,
                        "creatorNetAmount" to creatorNetAmount,
                        "status" to "pending_approval",
                        "paymentMethod" to "WALLET",
                        "paymentVerified" to true,
                        "transactionRef" to txnRef,
                        "expiresAt" to expiresAt,
                        "timestamp" to now
                    ),
                    SetOptions.merge()
                )

                txn.set(
                    buyerWalletTxnRef,
                    mapOf(
                        "id" to buyerWalletTxnRef.id,
                        "userId" to buyerId,
                        "purchaseId" to deterministicId,
                        "contentId" to contentId,
                        "type" to "EXCLUSIVE_PAYMENT",
                        "direction" to "DEBIT",
                        "amount" to safePrice,
                        "status" to "pending_approval",
                        "description" to "Exclusive unlock request for @$creatorUsername (₹${safePrice.toInt()})",
                        "timestamp" to now
                    )
                )

                txn.set(
                    creatorWalletTxnRef,
                    mapOf(
                        "id" to creatorWalletTxnRef.id,
                        "userId" to creatorId,
                        "purchaseId" to deterministicId,
                        "contentId" to contentId,
                        "type" to "EXCLUSIVE_PENDING",
                        "direction" to "CREDIT_PENDING",
                        "amount" to creatorNetAmount,
                        "grossAmount" to safePrice,
                        "status" to "pending_approval",
                        "description" to "Pending approval from @$buyerUsername (₹${safePrice.toInt()})",
                        "timestamp" to now
                    )
                )

                null
            }.await()

            val lowBalance = insufficientBalanceValue
            if (lowBalance != null) {
                return ExclusiveUnlockResult.InsufficientWallet(
                    currentBalance = lowBalance,
                    requiredAmount = safePrice
                )
            }

            if (alreadyOwned) {
                EntitlementRepository.markUnlockedLocally(buyerId, contentId)
                val unlockedMedia = resolveUnlockedMedia(contentId, forceRefresh = true)
                return ExclusiveUnlockResult.Success(deterministicId, unlockedMedia, alreadyOwned = true)
            }

            alreadyPendingRecord?.let { existingRec ->
                EntitlementRepository.markPurchasePendingLocally(existingRec)
                return ExclusiveUnlockResult.PendingApproval(
                    purchaseId = existingRec.id,
                    expiresAt = existingRec.expiresAt,
                    record = existingRec
                )
            }

            val createdRecord = ExclusivePurchaseRecord(
                id = deterministicId,
                buyerId = buyerId,
                buyerUsername = buyerUsername,
                buyerAvatar = buyerAvatar,
                contentId = contentId,
                contentType = contentType.uppercase(),
                creatorId = creatorId,
                creatorUsername = creatorUsername,
                creatorAvatar = creatorAvatar,
                title = title,
                teaserCaption = teaserCaption,
                blurredThumbnailUrl = blurredThumbnailUrl,
                amount = safePrice,
                platformCommissionPct = commissionPct,
                platformCommissionAmount = commissionAmount,
                creatorNetAmount = creatorNetAmount,
                status = "pending_approval",
                paymentMethod = "WALLET",
                paymentVerified = true,
                transactionRef = txnRef,
                expiresAt = expiresAt,
                timestamp = now
            )

            EntitlementRepository.markPurchasePendingLocally(createdRecord)
            dispatchPendingApprovalNotifications(
                buyerId = buyerId,
                buyerUsername = buyerUsername,
                creatorId = creatorId,
                creatorUsername = creatorUsername,
                contentId = contentId,
                contentType = contentType,
                amount = safePrice
            )

            ExclusiveUnlockResult.PendingApproval(
                purchaseId = deterministicId,
                expiresAt = expiresAt,
                record = createdRecord
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Wallet unlock request failed for $contentId", e)
            ExclusiveUnlockResult.Failure(e.message ?: "Payment failed. Please try again.")
        }
    }

    /**
     * Server-side Razorpay verification via Cloud Function `verifyExclusiveRazorpayPayment`.
     */
    suspend fun verifyExclusiveRazorpayServerSide(
        razorpayOrderId: String,
        razorpayPaymentId: String,
        razorpaySignature: String,
        contentId: String,
        contentType: String,
        creatorId: String,
        amountInr: Double,
        title: String,
        teaserCaption: String,
        blurredThumbnailUrl: String
    ): ExclusiveUnlockResult {
        val buyerId = RepositoryManager.getCurrentUserId()
        if (buyerId.isBlank()) {
            return ExclusiveUnlockResult.Failure("Please sign in to verify payment.")
        }
        val payload = JSONObject().apply {
            put("razorpayOrderId", razorpayOrderId)
            put("razorpayPaymentId", razorpayPaymentId)
            put("razorpaySignature", razorpaySignature)
            put("contentId", contentId)
            put("contentType", contentType.uppercase())
            put("creatorId", creatorId)
            put("amountInr", amountInr)
            put("title", title)
            put("teaserCaption", teaserCaption)
            put("blurredThumbnailUrl", blurredThumbnailUrl)
        }
        val res = callAuthenticatedCloudFunction("verifyExclusiveRazorpayPayment", payload)
        if (res.isSuccess) {
            val data = res.getOrNull()
            val purchaseId = data?.optString("purchaseId")?.ifBlank { "${buyerId}_${contentId}" }
                ?: "${buyerId}_${contentId}"
            val expiresAt = data?.optLong("expiresAt", 0L)?.takeIf { it > 0L }
                ?: (System.currentTimeMillis() + EXCLUSIVE_APPROVAL_WINDOW_MS)
            val rec = ExclusivePurchaseRecord(
                id = purchaseId,
                buyerId = buyerId,
                contentId = contentId,
                contentType = contentType.uppercase(),
                creatorId = creatorId,
                title = title,
                teaserCaption = teaserCaption,
                blurredThumbnailUrl = blurredThumbnailUrl,
                amount = amountInr,
                status = "pending_approval",
                paymentMethod = "RAZORPAY",
                paymentVerified = true,
                transactionRef = razorpayPaymentId,
                expiresAt = expiresAt
            )
            EntitlementRepository.markPurchasePendingLocally(rec)
            return ExclusiveUnlockResult.PendingApproval(purchaseId, expiresAt, rec)
        }
        return ExclusiveUnlockResult.Failure(
            res.exceptionOrNull()?.message ?: "Server-side payment verification failed."
        )
    }

    /**
     * Records a verified UPI / Razorpay gateway exclusive payment as `pending_approval` (48h creator approval window).
     * Never grants immediate viewer unlock; credits Creator's `pendingBalance`.
     */
    suspend fun recordVerifiedGatewayUnlock(
        buyerId: String,
        creatorId: String,
        contentId: String,
        contentType: String,
        title: String,
        teaserCaption: String,
        blurredThumbnailUrl: String,
        creatorUsername: String,
        creatorAvatar: String,
        priceInr: Double,
        transactionRef: String,
        paymentMethod: String = "UPI_RAZORPAY"
    ): ExclusiveUnlockResult {
        if (buyerId.isBlank() || creatorId.isBlank() || contentId.isBlank()) {
            return ExclusiveUnlockResult.Failure("Missing user or content info.")
        }
        val settings = fetchMonetizationSettings()
        val split = computeExclusiveSplit(priceInr, settings.exclusiveCommissionPct)
        val deterministicId = "${buyerId}_${contentId}"
        val now = System.currentTimeMillis()
        val expiresAt = now + EXCLUSIVE_APPROVAL_WINDOW_MS

        return try {
            val purchaseRef = db.collection("exclusive_purchases").document(deterministicId)
            val existing = purchaseRef.get().await()
            val existingStatus = existing.getString("status")
            if (existing.exists() && isExclusiveStatusUnlocked(existingStatus)) {
                EntitlementRepository.markUnlockedLocally(buyerId, contentId)
                return ExclusiveUnlockResult.Success(deterministicId, resolveUnlockedMedia(contentId, true), alreadyOwned = true)
            }
            if (existing.exists() && isExclusiveStatusPending(existingStatus)) {
                val existingRec = existing.toObject(ExclusivePurchaseRecord::class.java)?.copy(id = existing.id)
                    ?: ExclusivePurchaseRecord(id = deterministicId, buyerId = buyerId, contentId = contentId, creatorId = creatorId, amount = priceInr, status = "pending_approval", expiresAt = expiresAt)
                EntitlementRepository.markPurchasePendingLocally(existingRec)
                return ExclusiveUnlockResult.PendingApproval(deterministicId, existingRec.expiresAt, existingRec)
            }

            val buyerSnap = try { db.collection("users").document(buyerId).get().await() } catch (_: Exception) { null }
            val buyerName = buyerSnap?.getString("username")?.takeIf { it.isNotBlank() } ?: "Someone"
            val buyerAvatar = buyerSnap?.getString("avatarUrl") ?: ""

            val entitlementRef = db.collection("content_entitlements").document(deterministicId)
            val creatorRef = db.collection("users").document(creatorId)
            val creatorWalletRef = db.collection("wallets").document(creatorId)

            db.runTransaction { txn ->
                val cSnap = txn.get(creatorRef)
                if (cSnap.exists()) {
                    val curPend = cSnap.getDouble("pendingBalance") ?: 0.0
                    txn.update(creatorRef, mapOf("pendingBalance" to (curPend + split.creatorNetAmount)))
                }
                txn.set(
                    creatorWalletRef,
                    mapOf(
                        "userId" to creatorId,
                        "pendingBalance" to FieldValue.increment(split.creatorNetAmount),
                        "currency" to "INR",
                        "updatedAt" to now
                    ),
                    SetOptions.merge()
                )

                txn.set(
                    purchaseRef,
                    mapOf(
                        "id" to deterministicId,
                        "buyerId" to buyerId,
                        "buyerUsername" to buyerName,
                        "buyerAvatar" to buyerAvatar,
                        "contentId" to contentId,
                        "contentType" to contentType.uppercase(),
                        "creatorId" to creatorId,
                        "creatorUsername" to creatorUsername,
                        "creatorAvatar" to creatorAvatar,
                        "title" to title,
                        "teaserCaption" to teaserCaption,
                        "blurredThumbnailUrl" to blurredThumbnailUrl,
                        "amount" to split.grossAmount,
                        "platformCommissionPct" to split.commissionPct,
                        "platformCommissionAmount" to split.commissionAmount,
                        "creatorNetAmount" to split.creatorNetAmount,
                        "status" to "pending_approval",
                        "paymentMethod" to paymentMethod,
                        "paymentVerified" to true,
                        "transactionRef" to transactionRef,
                        "expiresAt" to expiresAt,
                        "timestamp" to now
                    ),
                    SetOptions.merge()
                )

                txn.set(
                    entitlementRef,
                    mapOf(
                        "entitlementId" to deterministicId,
                        "userId" to buyerId,
                        "creatorId" to creatorId,
                        "contentId" to contentId,
                        "contentType" to contentType.uppercase(),
                        "entitlementType" to "EXCLUSIVE_UNLOCK",
                        "active" to false,
                        "amountPaid" to split.grossAmount,
                        "validUntil" to -1L,
                        "orderId" to transactionRef,
                        "grantedAt" to now
                    ),
                    SetOptions.merge()
                )

                val buyerTxnRef = db.collection("wallet_transactions").document("WTX_GW_BUY_${deterministicId}_$now")
                txn.set(
                    buyerTxnRef,
                    mapOf(
                        "id" to buyerTxnRef.id,
                        "userId" to buyerId,
                        "purchaseId" to deterministicId,
                        "contentId" to contentId,
                        "type" to "EXCLUSIVE_PAYMENT",
                        "direction" to "DEBIT",
                        "amount" to split.grossAmount,
                        "status" to "pending_approval",
                        "description" to "Paid ₹${split.grossAmount.toInt()} via $paymentMethod to unlock @$creatorUsername",
                        "timestamp" to now
                    )
                )

                val creatorTxnRef = db.collection("wallet_transactions").document("WTX_GW_CR_${deterministicId}_$now")
                txn.set(
                    creatorTxnRef,
                    mapOf(
                        "id" to creatorTxnRef.id,
                        "userId" to creatorId,
                        "purchaseId" to deterministicId,
                        "contentId" to contentId,
                        "type" to "EXCLUSIVE_PENDING",
                        "direction" to "CREDIT_PENDING",
                        "amount" to split.creatorNetAmount,
                        "grossAmount" to split.grossAmount,
                        "status" to "pending_approval",
                        "description" to "Pending approval from @$buyerName (₹${split.grossAmount.toInt()})",
                        "timestamp" to now
                    )
                )
                null
            }.await()

            val record = ExclusivePurchaseRecord(
                id = deterministicId,
                buyerId = buyerId,
                buyerUsername = buyerName,
                buyerAvatar = buyerAvatar,
                contentId = contentId,
                contentType = contentType.uppercase(),
                creatorId = creatorId,
                creatorUsername = creatorUsername,
                creatorAvatar = creatorAvatar,
                title = title,
                teaserCaption = teaserCaption,
                blurredThumbnailUrl = blurredThumbnailUrl,
                amount = split.grossAmount,
                platformCommissionPct = split.commissionPct,
                platformCommissionAmount = split.commissionAmount,
                creatorNetAmount = split.creatorNetAmount,
                status = "pending_approval",
                paymentMethod = paymentMethod,
                paymentVerified = true,
                transactionRef = transactionRef,
                expiresAt = expiresAt,
                timestamp = now
            )

            EntitlementRepository.markPurchasePendingLocally(record)
            dispatchPendingApprovalNotifications(
                buyerId = buyerId,
                buyerUsername = buyerName,
                creatorId = creatorId,
                creatorUsername = creatorUsername,
                contentId = contentId,
                contentType = contentType,
                amount = split.grossAmount
            )

            ExclusiveUnlockResult.PendingApproval(
                purchaseId = deterministicId,
                expiresAt = expiresAt,
                record = record
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed recording verified gateway unlock", e)
            ExclusiveUnlockResult.Failure(e.message ?: "Failed saving payment verification.")
        }
    }

    /**
     * Real-time stream of ALL exclusive requests directed to a creator (`exclusive_purchases` where `creatorId == uid`).
     */
    fun observeCreatorExclusiveRequests(creatorId: String): Flow<List<ExclusivePurchaseRecord>> = callbackFlow {
        if (creatorId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = db.collection("exclusive_purchases")
            .whereEqualTo("creatorId", creatorId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "observeCreatorExclusiveRequests error: ${err.message}")
                    return@addSnapshotListener
                }
                val items = snap?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(ExclusivePurchaseRecord::class.java)?.copy(id = doc.id)
                    } catch (_: Exception) { null }
                }?.sortedByDescending { it.timestamp } ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Real-time combined exclusive transaction history for a user (both Buyer purchases & Creator receipts).
     */
    fun observeUserExclusiveHistory(userId: String): Flow<List<ExclusivePurchaseRecord>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val buyerItems = mutableListOf<ExclusivePurchaseRecord>()
        val creatorItems = mutableListOf<ExclusivePurchaseRecord>()

        fun emitMerged() {
            val merged = (buyerItems + creatorItems)
                .distinctBy { "${it.id}_${it.buyerId}_${it.creatorId}" }
                .sortedByDescending { it.timestamp }
            trySend(merged)
        }

        val l1 = db.collection("exclusive_purchases")
            .whereEqualTo("buyerId", userId)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    buyerItems.clear()
                    snap.documents.mapNotNull {
                        try { it.toObject(ExclusivePurchaseRecord::class.java)?.copy(id = it.id) } catch (_: Exception) { null }
                    }.let { buyerItems.addAll(it) }
                    emitMerged()
                }
            }
        val l2 = db.collection("exclusive_purchases")
            .whereEqualTo("creatorId", userId)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    creatorItems.clear()
                    snap.documents.mapNotNull {
                        try { it.toObject(ExclusivePurchaseRecord::class.java)?.copy(id = it.id) } catch (_: Exception) { null }
                    }.let { creatorItems.addAll(it) }
                    emitMerged()
                }
            }
        awaitClose {
            l1.remove()
            l2.remove()
        }
    }

    /**
     * Creator approves a `pending_approval` request:
     * - Calls Cloud Function `approveExclusiveRequest` first; falls back to atomic Firestore transaction
     *   if Cloud Functions are unreachable in local dev/test.
     * - Sets `status = "approved"`, activates `content_entitlements/{purchaseId}`,
     *   moves net payout from Creator's `pendingBalance` to `availableBalance` / `walletBalance` / `earnings`,
     *   increments content stats, and notifies buyer.
     */
    suspend fun approveExclusiveRequest(purchaseId: String, creatorId: String): ExclusiveActionResult {
        if (purchaseId.isBlank() || creatorId.isBlank()) {
            return ExclusiveActionResult.Failure("Invalid request.")
        }
        val cloudRes = callAuthenticatedCloudFunction(
            "approveExclusiveRequest",
            JSONObject().put("purchaseId", purchaseId)
        )
        if (cloudRes.isSuccess) {
            return ExclusiveActionResult.Success("Request approved! Earnings moved to Available Balance.")
        }

        val now = System.currentTimeMillis()
        val pRef = db.collection("exclusive_purchases").document(purchaseId)
        val entRef = db.collection("content_entitlements").document(purchaseId)

        return try {
            var buyerIdToNotify = ""
            var creatorNameToNotify = "Creator"
            var contentIdToNotify = ""
            var contentTypeToNotify = "POST"
            var settledNet = 0.0

            db.runTransaction { txn ->
                val pSnap = txn.get(pRef)
                if (!pSnap.exists()) throw IllegalStateException("Request not found")
                val ownerId = pSnap.getString("creatorId") ?: ""
                if (ownerId != creatorId) throw IllegalStateException("Only the creator can approve")

                val curStatus = pSnap.getString("status")
                if (isExclusiveStatusUnlocked(curStatus)) {
                    return@runTransaction null
                }
                if (!isExclusiveStatusPending(curStatus)) {
                    throw IllegalStateException("Request is already $curStatus")
                }

                val expiresAt = pSnap.getLong("expiresAt") ?: 0L
                if (expiresAt > 0L && now >= expiresAt) {
                    throw IllegalStateException("Request expired after 48h and will be refunded")
                }

                buyerIdToNotify = pSnap.getString("buyerId") ?: ""
                contentIdToNotify = pSnap.getString("contentId") ?: ""
                contentTypeToNotify = (pSnap.getString("contentType") ?: "POST").uppercase()
                creatorNameToNotify = pSnap.getString("creatorUsername") ?: "Creator"
                val buyerName = pSnap.getString("buyerUsername") ?: "fan"
                val gross = pSnap.getDouble("amount") ?: 0.0
                val fee = pSnap.getDouble("platformCommissionAmount") ?: 0.0
                val pct = pSnap.getDouble("platformCommissionPct") ?: 15.0
                settledNet = pSnap.getDouble("creatorNetAmount") ?: (gross - fee).coerceAtLeast(0.0)

                val cUserRef = db.collection("users").document(creatorId)
                val cUserSnap = txn.get(cUserRef)
                val cWalletRef = db.collection("wallets").document(creatorId)
                val cWalletSnap = txn.get(cWalletRef)
                val contentCollection = if (contentTypeToNotify == "STORY") "stories" else "posts"
                val contentRef = if (contentIdToNotify.isNotBlank()) db.collection(contentCollection).document(contentIdToNotify) else null
                val contentSnap = contentRef?.let { txn.get(it) }

                val userTransition = computeApprovalBalanceTransition(
                    currentPendingBalance = cUserSnap.getDouble("pendingBalance") ?: 0.0,
                    currentAvailableBalance = cUserSnap.getDouble("walletBalance") ?: 0.0,
                    netAmount = settledNet
                )
                val curEarnings = cUserSnap.getDouble("earnings") ?: 0.0

                txn.update(
                    cUserRef,
                    mapOf(
                        "pendingBalance" to userTransition.newPendingBalance,
                        "walletBalance" to userTransition.newAvailableBalance,
                        "earnings" to (curEarnings + settledNet)
                    )
                )

                val wTransition = computeApprovalBalanceTransition(
                    currentPendingBalance = cWalletSnap.getDouble("pendingBalance") ?: settledNet,
                    currentAvailableBalance = cWalletSnap.getDouble("availableBalance") ?: (cUserSnap.getDouble("walletBalance") ?: 0.0),
                    netAmount = settledNet
                )
                txn.set(
                    cWalletRef,
                    mapOf(
                        "userId" to creatorId,
                        "pendingBalance" to wTransition.newPendingBalance,
                        "availableBalance" to wTransition.newAvailableBalance,
                        "currency" to "INR",
                        "updatedAt" to now
                    ),
                    SetOptions.merge()
                )

                txn.update(
                    pRef,
                    mapOf(
                        "status" to "approved",
                        "approvedAt" to now,
                        "approvedBy" to creatorId
                    )
                )

                txn.set(
                    entRef,
                    mapOf(
                        "entitlementId" to purchaseId,
                        "userId" to buyerIdToNotify,
                        "creatorId" to creatorId,
                        "contentId" to contentIdToNotify,
                        "contentType" to contentTypeToNotify,
                        "entitlementType" to "EXCLUSIVE_UNLOCK",
                        "active" to true,
                        "amountPaid" to gross,
                        "validUntil" to -1L,
                        "grantedAt" to now
                    ),
                    SetOptions.merge()
                )

                if (contentRef != null && contentSnap != null && contentSnap.exists()) {
                    val unlocks = (contentSnap.getLong("unlockCount") ?: 0L).toInt()
                    val rev = contentSnap.getDouble("exclusiveRevenue") ?: 0.0
                    txn.update(
                        contentRef,
                        mapOf(
                            "unlockCount" to (unlocks + 1),
                            "exclusiveRevenue" to (rev + settledNet)
                        )
                    )
                }

                val ledgerRef = db.collection("creator_earnings_ledger").document("LEDGER_APP_${purchaseId}_$now")
                txn.set(
                    ledgerRef,
                    mapOf(
                        "ledgerId" to ledgerRef.id,
                        "creatorId" to creatorId,
                        "buyerId" to buyerIdToNotify,
                        "orderId" to purchaseId,
                        "contentId" to contentIdToNotify,
                        "sourceType" to "EXCLUSIVE_UNLOCK",
                        "grossAmount" to gross,
                        "platformFee" to fee,
                        "platformCommissionPct" to pct,
                        "netAmount" to settledNet,
                        "status" to "CREDITED",
                        "createdAt" to now
                    )
                )

                val wTxRef = db.collection("wallet_transactions").document("WTX_APP_${purchaseId}_$now")
                txn.set(
                    wTxRef,
                    mapOf(
                        "id" to wTxRef.id,
                        "userId" to creatorId,
                        "purchaseId" to purchaseId,
                        "contentId" to contentIdToNotify,
                        "type" to "EXCLUSIVE_APPROVED",
                        "direction" to "CREDIT_AVAILABLE",
                        "amount" to settledNet,
                        "grossAmount" to gross,
                        "status" to "approved",
                        "description" to "Approved unlock by @$buyerName (Moved ₹${String.format(Locale.US, "%.2f", settledNet)} to Available)",
                        "timestamp" to now
                    )
                )

                null
            }.await()

            if (buyerIdToNotify.isNotBlank()) {
                dispatchBuyerApprovedNotification(
                    buyerId = buyerIdToNotify,
                    creatorId = creatorId,
                    creatorUsername = creatorNameToNotify,
                    contentId = contentIdToNotify,
                    contentType = contentTypeToNotify
                )
            }

            ExclusiveActionResult.Success("Approved! ₹${String.format(Locale.US, "%.2f", settledNet)} moved to Available Balance.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "approveExclusiveRequest failed for $purchaseId", e)
            ExclusiveActionResult.Failure(e.message ?: "Could not approve request.")
        }
    }

    /**
     * Creator rejects (or system/user settles expired 48h) an exclusive request ->
     * refunds buyer's available wallet, subtracts from creator's `pendingBalance`, marks `"rejected"` or `"refunded"`,
     * and notifies both parties.
     */
    suspend fun rejectExclusiveRequest(
        purchaseId: String,
        actorId: String,
        reason: String = "Creator declined request",
        newStatus: String = "rejected"
    ): ExclusiveActionResult {
        if (purchaseId.isBlank() || actorId.isBlank()) {
            return ExclusiveActionResult.Failure("Invalid request.")
        }
        if (newStatus == "rejected") {
            val cloudRes = callAuthenticatedCloudFunction(
                "rejectExclusiveRequest",
                JSONObject().put("purchaseId", purchaseId).put("reason", reason)
            )
            if (cloudRes.isSuccess) {
                return ExclusiveActionResult.Success("Request rejected and buyer refunded.")
            }
        }

        val now = System.currentTimeMillis()
        val pRef = db.collection("exclusive_purchases").document(purchaseId)
        val entRef = db.collection("content_entitlements").document(purchaseId)

        return try {
            var buyerId = ""
            var creatorId = ""
            var buyerName = "Buyer"
            var creatorName = "Creator"
            var contentId = ""
            var contentType = "POST"
            var refundAmount = 0.0

            db.runTransaction { txn ->
                val pSnap = txn.get(pRef)
                if (!pSnap.exists()) throw IllegalStateException("Request not found")
                val curStatus = pSnap.getString("status")
                if (curStatus == "refunded" || curStatus == "REFUNDED" || curStatus == "rejected") {
                    return@runTransaction null
                }
                if (!isExclusiveStatusPending(curStatus)) {
                    throw IllegalStateException("Cannot refund request in status: $curStatus")
                }

                buyerId = pSnap.getString("buyerId") ?: ""
                creatorId = pSnap.getString("creatorId") ?: ""
                buyerName = pSnap.getString("buyerUsername") ?: "fan"
                creatorName = pSnap.getString("creatorUsername") ?: "creator"
                contentId = pSnap.getString("contentId") ?: ""
                contentType = (pSnap.getString("contentType") ?: "POST").uppercase()
                refundAmount = pSnap.getDouble("amount") ?: 0.0
                val creatorNet = pSnap.getDouble("creatorNetAmount") ?: 0.0

                val buyerRef = if (buyerId.isNotBlank()) db.collection("users").document(buyerId) else null
                val buyerSnap = buyerRef?.let { txn.get(it) }
                val buyerWalletRef = if (buyerId.isNotBlank()) db.collection("wallets").document(buyerId) else null
                val buyerWalletSnap = buyerWalletRef?.let { txn.get(it) }

                val creatorRef = if (creatorId.isNotBlank()) db.collection("users").document(creatorId) else null
                val creatorSnap = creatorRef?.let { txn.get(it) }
                val creatorWalletRef = if (creatorId.isNotBlank()) db.collection("wallets").document(creatorId) else null
                val creatorWalletSnap = creatorWalletRef?.let { txn.get(it) }

                if (buyerRef != null && buyerSnap != null && buyerSnap.exists() && refundAmount > 0.0) {
                    val curBuyerBal = buyerSnap.getDouble("walletBalance") ?: 0.0
                    txn.update(buyerRef, mapOf("walletBalance" to (curBuyerBal + refundAmount)))
                }
                if (buyerWalletRef != null && refundAmount > 0.0) {
                    val curW = buyerWalletSnap?.getDouble("availableBalance") ?: (buyerSnap?.getDouble("walletBalance") ?: 0.0)
                    txn.set(
                        buyerWalletRef,
                        mapOf(
                            "userId" to buyerId,
                            "availableBalance" to (curW + refundAmount),
                            "currency" to "INR",
                            "updatedAt" to now
                        ),
                        SetOptions.merge()
                    )
                }

                if (creatorRef != null && creatorSnap != null && creatorSnap.exists() && creatorNet > 0.0) {
                    val curPend = creatorSnap.getDouble("pendingBalance") ?: 0.0
                    txn.update(creatorRef, mapOf("pendingBalance" to (curPend - creatorNet).coerceAtLeast(0.0)))
                }
                if (creatorWalletRef != null && creatorNet > 0.0) {
                    val curWPend = creatorWalletSnap?.getDouble("pendingBalance") ?: creatorNet
                    txn.set(
                        creatorWalletRef,
                        mapOf(
                            "userId" to creatorId,
                            "pendingBalance" to (curWPend - creatorNet).coerceAtLeast(0.0),
                            "currency" to "INR",
                            "updatedAt" to now
                        ),
                        SetOptions.merge()
                    )
                }

                txn.update(
                    pRef,
                    mapOf(
                        "status" to newStatus,
                        "refundedAt" to now,
                        "refundReason" to reason
                    )
                )
                txn.set(entRef, mapOf("active" to false), SetOptions.merge())

                if (buyerId.isNotBlank()) {
                    val wRef = db.collection("wallet_transactions").document("WTX_REF_${purchaseId}_$now")
                    txn.set(
                        wRef,
                        mapOf(
                            "id" to wRef.id,
                            "userId" to buyerId,
                            "purchaseId" to purchaseId,
                            "contentId" to contentId,
                            "type" to "EXCLUSIVE_REFUND",
                            "direction" to "CREDIT_AVAILABLE",
                            "amount" to refundAmount,
                            "status" to newStatus,
                            "description" to "Refunded ₹${refundAmount.toInt()} ($reason)",
                            "timestamp" to now
                        )
                    )
                }

                null
            }.await()

            dispatchRefundNotifications(
                buyerId = buyerId,
                buyerUsername = buyerName,
                creatorId = creatorId,
                creatorUsername = creatorName,
                contentId = contentId,
                contentType = contentType,
                amount = refundAmount,
                reason = reason,
                isAutoExpired = (newStatus == "refunded")
            )

            ExclusiveActionResult.Success("Refunded ₹${refundAmount.toInt()} to buyer's wallet.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "rejectExclusiveRequest failed for $purchaseId", e)
            ExclusiveActionResult.Failure(e.message ?: "Could not refund request.")
        }
    }

    /**
     * Sweeps any `pending_approval` requests involving `userId` (as buyer or creator) past their 48h `expiresAt`
     * and settles auto-refunds immediately even if the user opens the app before the hourly Cloud Function cron runs.
     */
    suspend fun processExpiredExclusiveRequests(userId: String): Int {
        if (userId.isBlank()) return 0
        var refundedCount = 0
        val now = System.currentTimeMillis()
        try {
            val asBuyer = db.collection("exclusive_purchases")
                .whereEqualTo("buyerId", userId)
                .whereEqualTo("status", "pending_approval")
                .get().await()
            val asCreator = db.collection("exclusive_purchases")
                .whereEqualTo("creatorId", userId)
                .whereEqualTo("status", "pending_approval")
                .get().await()

            val candidates = (asBuyer.documents + asCreator.documents).distinctBy { it.id }
            for (doc in candidates) {
                val expiresAt = doc.getLong("expiresAt") ?: 0L
                val status = doc.getString("status")
                if (isExclusiveRequestExpired(status, expiresAt, now)) {
                    val res = rejectExclusiveRequest(
                        purchaseId = doc.id,
                        actorId = userId,
                        reason = "Auto-refunded after 48 hours without creator approval",
                        newStatus = "refunded"
                    )
                    if (res is ExclusiveActionResult.Success) {
                        refundedCount++
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "processExpiredExclusiveRequests error: ${e.message}")
        }
        return refundedCount
    }

    private suspend fun dispatchPendingApprovalNotifications(
        buyerId: String,
        buyerUsername: String,
        creatorId: String,
        creatorUsername: String,
        contentId: String,
        contentType: String,
        amount: Double
    ) {
        val now = System.currentTimeMillis()
        val label = when (contentType.uppercase()) {
            "REEL", "VIDEO" -> "exclusive reel"
            "STORY" -> "exclusive story"
            else -> "exclusive post"
        }
        try {
            val cMsg = "@$buyerUsername paid ₹${amount.toInt()} to unlock your $label. Approve or reject within 48 hours."
            db.collection("users").document(creatorId)
                .collection("notifications")
                .add(
                    mapOf(
                        "recipientUid" to creatorId,
                        "type" to "exclusive_request",
                        "title" to "New Exclusive Unlock Request",
                        "message" to cMsg,
                        "text" to cMsg,
                        "actorUid" to buyerId,
                        "actorId" to buyerId,
                        "actorUsername" to buyerUsername,
                        "senderId" to buyerId,
                        "targetId" to contentId,
                        "targetType" to contentType.lowercase(),
                        "postId" to contentId,
                        "contentType" to contentType.uppercase(),
                        "deepLink" to "exclusive_requests",
                        "createdAt" to now,
                        "timestamp" to now,
                        "read" to false,
                        "isRead" to false
                    )
                ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Creator pending notif failed: ${e.message}")
        }

        try {
            val bMsg = "Payment done - waiting for creator approval (₹${amount.toInt()} for @$creatorUsername). Auto-refunded if not approved within 48h."
            db.collection("users").document(buyerId)
                .collection("notifications")
                .add(
                    mapOf(
                        "recipientUid" to buyerId,
                        "type" to "exclusive_pending",
                        "title" to "Payment done - waiting for creator approval",
                        "message" to bMsg,
                        "text" to bMsg,
                        "actorUid" to creatorId,
                        "actorId" to creatorId,
                        "actorUsername" to creatorUsername,
                        "senderId" to creatorId,
                        "targetId" to contentId,
                        "targetType" to contentType.lowercase(),
                        "postId" to contentId,
                        "contentType" to contentType.uppercase(),
                        "deepLink" to "wallet",
                        "createdAt" to now,
                        "timestamp" to now,
                        "read" to false,
                        "isRead" to false
                    )
                ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Buyer pending notif failed: ${e.message}")
        }
    }

    private suspend fun dispatchBuyerApprovedNotification(
        buyerId: String,
        creatorId: String,
        creatorUsername: String,
        contentId: String,
        contentType: String
    ) {
        val now = System.currentTimeMillis()
        try {
            val msg = "@$creatorUsername approved your unlock request! Tap to view your exclusive ${contentType.lowercase()}."
            db.collection("users").document(buyerId)
                .collection("notifications")
                .add(
                    mapOf(
                        "recipientUid" to buyerId,
                        "type" to "exclusive_approved",
                        "title" to "Exclusive Access Approved!",
                        "message" to msg,
                        "text" to msg,
                        "actorUid" to creatorId,
                        "actorId" to creatorId,
                        "actorUsername" to creatorUsername,
                        "senderId" to creatorId,
                        "targetId" to contentId,
                        "targetType" to contentType.lowercase(),
                        "postId" to contentId,
                        "contentType" to contentType.uppercase(),
                        "deepLink" to if (contentType.equals("REEL", true) || contentType.equals("VIDEO", true)) "reel/$contentId" else "post_detail/$contentId",
                        "createdAt" to now,
                        "timestamp" to now,
                        "read" to false,
                        "isRead" to false
                    )
                ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Buyer approval notif failed: ${e.message}")
        }
    }

    private suspend fun dispatchRefundNotifications(
        buyerId: String,
        buyerUsername: String,
        creatorId: String,
        creatorUsername: String,
        contentId: String,
        contentType: String,
        amount: Double,
        reason: String,
        isAutoExpired: Boolean
    ) {
        val now = System.currentTimeMillis()
        if (buyerId.isNotBlank()) {
            try {
                val msg = "₹${amount.toInt()} has been refunded to your wallet ($reason)."
                db.collection("users").document(buyerId)
                    .collection("notifications")
                    .add(
                        mapOf(
                            "recipientUid" to buyerId,
                            "type" to if (isAutoExpired) "exclusive_refunded" else "exclusive_rejected",
                            "title" to if (isAutoExpired) "Exclusive Request Expired — Refunded" else "Exclusive Request Declined — Refunded",
                            "message" to msg,
                            "text" to msg,
                            "actorUid" to creatorId,
                            "actorId" to creatorId,
                            "actorUsername" to creatorUsername,
                            "senderId" to creatorId,
                            "targetId" to contentId,
                            "targetType" to contentType.lowercase(),
                            "postId" to contentId,
                            "contentType" to contentType.uppercase(),
                            "deepLink" to "wallet",
                            "createdAt" to now,
                            "timestamp" to now,
                            "read" to false,
                            "isRead" to false
                        )
                    ).await()
            } catch (_: Exception) {}
        }
        if (creatorId.isNotBlank()) {
            try {
                val msg = "@$buyerUsername's ₹${amount.toInt()} exclusive request was refunded ($reason)."
                db.collection("users").document(creatorId)
                    .collection("notifications")
                    .add(
                        mapOf(
                            "recipientUid" to creatorId,
                            "type" to "exclusive_refunded",
                            "title" to if (isAutoExpired) "Exclusive Request Auto-Refunded (48h)" else "Exclusive Request Rejected",
                            "message" to msg,
                            "text" to msg,
                            "actorUid" to buyerId,
                            "actorId" to buyerId,
                            "actorUsername" to buyerUsername,
                            "senderId" to buyerId,
                            "targetId" to contentId,
                            "targetType" to contentType.lowercase(),
                            "postId" to contentId,
                            "contentType" to contentType.uppercase(),
                            "deepLink" to "exclusive_requests",
                            "createdAt" to now,
                            "timestamp" to now,
                            "read" to false,
                            "isRead" to false
                        )
                    ).await()
            } catch (_: Exception) {}
        }
    }

    /**
     * Real-time stream of approved exclusive purchases owned by a viewer (for Profile "Unlocked" tab).
     * Accepts both new `"approved"` and legacy `"COMPLETED"` statuses.
     */
    fun observeBuyerUnlockedItems(buyerId: String): Flow<List<ExclusivePurchaseRecord>> = callbackFlow {
        if (buyerId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = db.collection("exclusive_purchases")
            .whereEqualTo("buyerId", buyerId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "observeBuyerUnlockedItems error: ${err.message}")
                    return@addSnapshotListener
                }
                val items = snap?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(ExclusivePurchaseRecord::class.java)?.copy(id = doc.id)
                    } catch (_: Exception) { null }
                }?.filter { isExclusiveStatusUnlocked(it.status) }
                    ?.sortedByDescending { it.timestamp } ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Aggregates per-content & total exclusive revenue metrics for the Creator Dashboard.
     */
    suspend fun getCreatorExclusiveStats(creatorId: String): CreatorExclusiveDashboardStats {
        if (creatorId.isBlank()) return CreatorExclusiveDashboardStats()
        return try {
            val purchasesSnap = db.collection("exclusive_purchases")
                .whereEqualTo("creatorId", creatorId)
                .get().await()

            val allPurchases = purchasesSnap.documents.mapNotNull { doc ->
                try {
                    doc.toObject(ExclusivePurchaseRecord::class.java)?.copy(id = doc.id)
                } catch (_: Exception) { null }
            }.sortedByDescending { it.timestamp }

            val completed = allPurchases.filter { isExclusiveStatusUnlocked(it.status) }
            val pending = allPurchases.filter { isExclusiveStatusPending(it.status) }

            val totalGross = completed.sumOf { it.amount }
            val totalNet = completed.sumOf { it.creatorNetAmount }
            val totalCommission = completed.sumOf { it.platformCommissionAmount }
            val pendingBalFromRecords = pending.sumOf { it.creatorNetAmount }

            val userDoc = db.collection("users").document(creatorId).get().await()
            val availableWallet = userDoc.getDouble("walletBalance") ?: 0.0
            val userPending = userDoc.getDouble("pendingBalance") ?: pendingBalFromRecords

            val postsSnap = db.collection("posts")
                .whereEqualTo("userId", creatorId)
                .get().await()
            val storiesSnap = db.collection("stories")
                .whereEqualTo("userId", creatorId)
                .get().await()

            val purchasesByContent = completed.groupBy { it.contentId }
            val itemStats = mutableListOf<CreatorExclusiveItemStat>()

            postsSnap.documents.forEach { doc ->
                val isEx = (doc.getBoolean("isExclusive") == true) || (doc.getBoolean("isPaidPost") == true)
                val price = doc.getDouble("postUnlockPrice") ?: doc.getDouble("premiumPrice") ?: 0.0
                if (isEx && price >= 10.0) {
                    val cPurchases = purchasesByContent[doc.id].orEmpty()
                    val mType = (doc.getString("mediaType") ?: "POST").uppercase()
                    itemStats.add(
                        CreatorExclusiveItemStat(
                            contentId = doc.id,
                            contentType = if (mType == "VIDEO" || mType == "REEL") "REEL" else "POST",
                            title = doc.getString("caption")?.take(60)?.ifBlank { "Exclusive Post" } ?: "Exclusive Post",
                            teaserCaption = doc.getString("teaserCaption") ?: "",
                            thumbnailUrl = doc.getString("blurredThumbnailUrl")?.ifBlank { doc.getString("thumbnailUrl") ?: "" } ?: "",
                            priceInr = price,
                            unlockCount = maxOf(cPurchases.size, (doc.getLong("unlockCount") ?: 0L).toInt()),
                            grossRevenueInr = cPurchases.sumOf { it.amount },
                            netRevenueInr = maxOf(cPurchases.sumOf { it.creatorNetAmount }, doc.getDouble("exclusiveRevenue") ?: 0.0),
                            createdAt = doc.getLong("createdAtMillis") ?: doc.getDate("timestamp")?.time ?: 0L
                        )
                    )
                }
            }

            storiesSnap.documents.forEach { doc ->
                val isEx = doc.getBoolean("isExclusiveContent") == true
                val price = doc.getDouble("exclusivePrice") ?: 0.0
                if (isEx && price >= 10.0) {
                    val cPurchases = purchasesByContent[doc.id].orEmpty()
                    itemStats.add(
                        CreatorExclusiveItemStat(
                            contentId = doc.id,
                            contentType = "STORY",
                            title = doc.getString("teaserCaption")?.ifBlank { "Exclusive Story" } ?: "Exclusive Story",
                            teaserCaption = doc.getString("teaserCaption") ?: "",
                            thumbnailUrl = doc.getString("blurredThumbnailUrl") ?: "",
                            priceInr = price,
                            unlockCount = maxOf(cPurchases.size, (doc.getLong("unlockCount") ?: 0L).toInt()),
                            grossRevenueInr = cPurchases.sumOf { it.amount },
                            netRevenueInr = maxOf(cPurchases.sumOf { it.creatorNetAmount }, doc.getDouble("exclusiveRevenue") ?: 0.0),
                            createdAt = doc.getDate("timestamp")?.time ?: 0L
                        )
                    )
                }
            }

            CreatorExclusiveDashboardStats(
                totalGrossRevenue = totalGross,
                totalNetRevenue = totalNet,
                totalPlatformCommission = totalCommission,
                totalUnlocks = completed.size,
                pendingBalance = maxOf(pendingBalFromRecords, userPending),
                availableBalance = availableWallet,
                items = itemStats.sortedByDescending { it.netRevenueInr },
                recentPurchases = completed.take(25)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load creator exclusive stats", e)
            CreatorExclusiveDashboardStats()
        }
    }

    suspend fun updateExclusivePrice(
        contentId: String,
        contentType: String,
        newPriceInr: Double
    ): Boolean {
        val settings = fetchMonetizationSettings()
        val minPrice = settings.exclusiveMinPriceInr.coerceAtLeast(10.0)
        val maxPrice = settings.exclusiveMaxPriceInr.coerceAtLeast(minPrice)
        if (newPriceInr < minPrice || newPriceInr > maxPrice) return false

        return try {
            if (contentType.equals("STORY", ignoreCase = true)) {
                db.collection("stories").document(contentId).update(
                    mapOf("exclusivePrice" to newPriceInr, "premiumPrice" to newPriceInr)
                ).await()
            } else {
                db.collection("posts").document(contentId).update(
                    mapOf("postUnlockPrice" to newPriceInr, "premiumPrice" to newPriceInr)
                ).await()
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed updating exclusive price for $contentId", e)
            false
        }
    }

    suspend fun reportExclusiveContent(
        contentId: String,
        contentType: String,
        creatorId: String,
        creatorUsername: String,
        reason: String,
        details: String = ""
    ): Boolean {
        val reporterId = RepositoryManager.getCurrentUserId()
        if (reporterId.isBlank() || contentId.isBlank()) return false
        return try {
            val reportId = "EXR_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            db.collection("exclusive_reports").document(reportId).set(
                mapOf(
                    "id" to reportId,
                    "contentId" to contentId,
                    "contentType" to contentType.uppercase(),
                    "creatorId" to creatorId,
                    "creatorUsername" to creatorUsername,
                    "reporterId" to reporterId,
                    "reason" to reason,
                    "details" to details,
                    "status" to "OPEN",
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed reporting exclusive content $contentId", e)
            false
        }
    }

    suspend fun getAllExclusiveReports(): List<ExclusiveReportRecord> {
        return try {
            val snap = db.collection("exclusive_reports")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .get().await()
            snap.documents.mapNotNull { doc ->
                try {
                    doc.toObject(ExclusiveReportRecord::class.java)?.copy(id = doc.id)
                } catch (_: Exception) { null }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed fetching exclusive reports", e)
            emptyList()
        }
    }

    suspend fun adminRemoveExclusiveContent(
        contentId: String,
        contentType: String,
        reportId: String
    ): Boolean {
        return try {
            val collection = if (contentType.equals("STORY", ignoreCase = true)) "stories" else "posts"
            try {
                db.collection(collection).document(contentId).update(
                    mapOf("isDeleted" to true, "isRemovedByAdmin" to true)
                ).await()
            } catch (_: Exception) {}
            try {
                db.collection("exclusive_media_vault").document(contentId).delete().await()
            } catch (_: Exception) {}
            if (reportId.isNotBlank()) {
                db.collection("exclusive_reports").document(reportId).update(
                    mapOf("status" to "REMOVED")
                ).await()
            }
            vaultCache.remove(contentId)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Admin removal failed for $contentId", e)
            false
        }
    }

    suspend fun adminDismissExclusiveReport(reportId: String): Boolean {
        return try {
            db.collection("exclusive_reports").document(reportId).update(
                mapOf("status" to "DISMISSED")
            ).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Admin: Atomically refunds a purchase (whether pending_approval OR approved/COMPLETED) back to buyer's wallet,
     * settles creator's pendingBalance or walletBalance appropriately, revokes entitlement, and marks `refunded`.
     */
    suspend fun adminRefundPurchase(purchaseId: String, reason: String = "Admin refund"): Boolean {
        if (purchaseId.isBlank()) return false
        return try {
            val purchaseRef = db.collection("exclusive_purchases").document(purchaseId)
            val entitlementRef = db.collection("content_entitlements").document(purchaseId)
            val now = System.currentTimeMillis()

            db.runTransaction { txn ->
                val pSnap = txn.get(purchaseRef)
                if (!pSnap.exists()) return@runTransaction null
                val curStatus = pSnap.getString("status")
                if (curStatus.equals("refunded", ignoreCase = true)) return@runTransaction null

                val buyerId = pSnap.getString("buyerId") ?: return@runTransaction null
                val creatorId = pSnap.getString("creatorId") ?: ""
                val amount = pSnap.getDouble("amount") ?: 0.0
                val creatorNet = pSnap.getDouble("creatorNetAmount") ?: 0.0

                val buyerRef = db.collection("users").document(buyerId)
                val buyerSnap = txn.get(buyerRef)
                val creatorRef = if (creatorId.isNotBlank()) db.collection("users").document(creatorId) else null
                val creatorSnap = creatorRef?.let { txn.get(it) }

                if (buyerSnap.exists() && amount > 0) {
                    val curBuyerBal = buyerSnap.getDouble("walletBalance") ?: 0.0
                    txn.update(buyerRef, mapOf("walletBalance" to (curBuyerBal + amount)))
                }
                if (creatorRef != null && creatorSnap != null && creatorSnap.exists() && creatorNet > 0) {
                    if (isExclusiveStatusPending(curStatus)) {
                        val curPend = creatorSnap.getDouble("pendingBalance") ?: 0.0
                        txn.update(creatorRef, mapOf("pendingBalance" to (curPend - creatorNet).coerceAtLeast(0.0)))
                    } else if (isExclusiveStatusUnlocked(curStatus)) {
                        val curCreatorBal = creatorSnap.getDouble("walletBalance") ?: 0.0
                        txn.update(creatorRef, mapOf("walletBalance" to (curCreatorBal - creatorNet).coerceAtLeast(0.0)))
                    }
                }

                txn.update(
                    purchaseRef,
                    mapOf("status" to "refunded", "refundReason" to reason, "refundedAt" to now)
                )
                txn.set(entitlementRef, mapOf("active" to false), SetOptions.merge())
                null
            }.await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Admin refund failed for $purchaseId", e)
            false
        }
    }
}
