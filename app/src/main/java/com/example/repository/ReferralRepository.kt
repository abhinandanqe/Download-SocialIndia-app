package com.example.repository

import android.util.Log
import com.example.model.RepositoryManager
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
import java.util.Locale

data class ReferralConfig(
    val isEnabled: Boolean = true,
    val referrerRewardCoins: Long = 100L,
    val refereeBonusCoins: Long = 50L,
    val dailyReferralLimit: Int = 20,
    val bannerTitle: String = "Invite Friends, Earn Social Coins",
    val bannerSubtitle: String = "Share your unique code and both of you earn instant coin rewards!",
    val updatedAt: Long = System.currentTimeMillis()
)

data class ReferralClaim(
    val id: String = "",
    val referrerId: String = "",
    val referrerUsername: String = "",
    val refereeId: String = "",
    val refereeUsername: String = "",
    val refereeAvatarUrl: String = "",
    val referralCode: String = "",
    val referrerCoins: Long = 0L,
    val refereeCoins: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

data class UserReferralStats(
    val userId: String = "",
    val referralCode: String = "",
    val referredByCode: String = "",
    val totalReferrals: Int = 0,
    val totalCoinsEarned: Long = 0L,
    val claimedMilestones: List<Int> = emptyList(),
    val currentCoins: Long = 0L
)

data class ReferralLeaderboardEntry(
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val totalReferrals: Int = 0,
    val totalCoinsEarned: Long = 0L
)

data class ReferralMilestone(
    val requiredInvites: Int,
    val bonusCoins: Long,
    val tierTitle: String,
    val badgeColorHex: Long
)

object ReferralRepository {
    private const val SETTINGS_COLLECTION = "admin_settings"
    private const val SETTINGS_DOC = "referral_config"
    private const val CODES_COLLECTION = "referral_codes"
    private const val CLAIMS_COLLECTION = "referral_claims"

    val defaultMilestones = listOf(
        ReferralMilestone(3, 150L, "Bronze Promoter", 0xFFCD7F32),
        ReferralMilestone(10, 600L, "Silver Connector", 0xFFC0C0C0),
        ReferralMilestone(25, 2000L, "Gold Creator", 0xFFFFD700),
        ReferralMilestone(50, 5000L, "India Ambassador", 0xFF00E5FF)
    )

    fun listenReferralConfig(): Flow<ReferralConfig> = callbackFlow {
        val listener = RepositoryManager.firestore.collection(SETTINGS_COLLECTION)
            .document(SETTINGS_DOC)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                if (snap != null && snap.exists()) {
                    trySend(
                        ReferralConfig(
                            isEnabled = snap.getBoolean("isEnabled") ?: true,
                            referrerRewardCoins = snap.getLong("referrerRewardCoins") ?: 100L,
                            refereeBonusCoins = snap.getLong("refereeBonusCoins") ?: 50L,
                            dailyReferralLimit = (snap.getLong("dailyReferralLimit") ?: 20L).toInt(),
                            bannerTitle = snap.getString("bannerTitle") ?: "Invite Friends, Earn Social Coins",
                            bannerSubtitle = snap.getString("bannerSubtitle") ?: "Share your unique code and both of you earn instant coin rewards!",
                            updatedAt = snap.getLong("updatedAt") ?: System.currentTimeMillis()
                        )
                    )
                } else {
                    trySend(ReferralConfig())
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveReferralConfig(config: ReferralConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val data = mapOf(
                "isEnabled" to config.isEnabled,
                "referrerRewardCoins" to config.referrerRewardCoins.coerceIn(0L, 100_000L),
                "refereeBonusCoins" to config.refereeBonusCoins.coerceIn(0L, 100_000L),
                "dailyReferralLimit" to config.dailyReferralLimit.coerceIn(1, 500),
                "bannerTitle" to config.bannerTitle.trim().ifEmpty { "Invite Friends, Earn Social Coins" },
                "bannerSubtitle" to config.bannerSubtitle.trim().ifEmpty { "Share your unique code and earn instant coin rewards!" },
                "updatedAt" to System.currentTimeMillis()
            )
            RepositoryManager.firestore.collection(SETTINGS_COLLECTION)
                .document(SETTINGS_DOC)
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ReferralRepo", "Failed to save referral config", e)
            Result.failure(e)
        }
    }

    suspend fun getOrCreateUserReferralStats(userId: String): UserReferralStats = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext UserReferralStats()
        try {
            val db = RepositoryManager.firestore
            val userRef = db.collection("users").document(userId)
            val snap = userRef.get().await()

            val username = snap.getString("username").orEmpty().ifBlank { "USER" }
            var code = snap.getString("referralCode").orEmpty().trim().uppercase(Locale.US)
            if (code.isBlank()) {
                val prefix = username.filter { it.isLetterOrDigit() }.take(4).uppercase(Locale.US).ifBlank { "USER" }
                val suffix = userId.filter { it.isLetterOrDigit() }.takeLast(4).uppercase(Locale.US)
                code = "SI-$prefix$suffix"
                userRef.set(mapOf("referralCode" to code), SetOptions.merge()).await()
                db.collection(CODES_COLLECTION).document(code).set(
                    mapOf(
                        "code" to code,
                        "userId" to userId,
                        "username" to username,
                        "createdAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
            } else {
                // Ensure index document exists
                db.collection(CODES_COLLECTION).document(code).set(
                    mapOf("code" to code, "userId" to userId, "username" to username),
                    SetOptions.merge()
                ).await()
            }

            val claimed = (snap.get("claimedReferralMilestones") as? List<*>)
                ?.mapNotNull { (it as? Number)?.toInt() }
                ?: emptyList()

            UserReferralStats(
                userId = userId,
                referralCode = code,
                referredByCode = snap.getString("referredByCode").orEmpty(),
                totalReferrals = (snap.getLong("totalReferrals") ?: 0L).toInt(),
                totalCoinsEarned = snap.getLong("referralCoinsEarned") ?: 0L,
                claimedMilestones = claimed,
                currentCoins = snap.getLong("coins") ?: 0L
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ReferralRepo", "Error getting user referral stats", e)
            UserReferralStats(userId = userId)
        }
    }

    suspend fun redeemReferralCode(
        currentUserId: String,
        rawCode: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val code = rawCode.trim().uppercase(Locale.US)
            if (currentUserId.isBlank()) return@withContext Result.failure(IllegalStateException("Please sign in first"))
            if (code.length < 4) return@withContext Result.failure(IllegalArgumentException("Enter a valid referral code"))

            val db = RepositoryManager.firestore
            val configSnap = db.collection(SETTINGS_COLLECTION).document(SETTINGS_DOC).get().await()
            val isEnabled = configSnap.getBoolean("isEnabled") ?: true
            if (!isEnabled) {
                return@withContext Result.failure(IllegalStateException("Referral rewards program is currently paused by Admin"))
            }
            val referrerReward = configSnap.getLong("referrerRewardCoins") ?: 100L
            val refereeBonus = configSnap.getLong("refereeBonusCoins") ?: 50L
            val dailyLimit = (configSnap.getLong("dailyReferralLimit") ?: 20L).toInt()

            val codeSnap = db.collection(CODES_COLLECTION).document(code).get().await()
            if (!codeSnap.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Referral code \"$code\" not found"))
            }
            val referrerId = codeSnap.getString("userId").orEmpty()
            val referrerUsername = codeSnap.getString("username").orEmpty().ifBlank { "Creator" }
            if (referrerId.isBlank() || referrerId == currentUserId) {
                return@withContext Result.failure(IllegalArgumentException("You cannot redeem your own referral code"))
            }

            // Check daily referral cap for referrer
            val dayAgo = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
            val recentClaims = db.collection(CLAIMS_COLLECTION)
                .whereEqualTo("referrerId", referrerId)
                .limit(500)
                .get()
                .await()
            val todayCount = recentClaims.documents.count { (it.getLong("timestamp") ?: 0L) >= dayAgo }
            if (todayCount >= dailyLimit) {
                return@withContext Result.failure(IllegalStateException("This creator has reached today's referral limit"))
            }

            val currentUserRef = db.collection("users").document(currentUserId)
            val referrerRef = db.collection("users").document(referrerId)
            val claimRef = db.collection(CLAIMS_COLLECTION).document("claim_$currentUserId")

            db.runTransaction { tx ->
                val meSnap = tx.get(currentUserRef)
                val existingCode = meSnap.getString("referredByCode").orEmpty()
                val existingClaim = tx.get(claimRef)
                if (existingCode.isNotBlank() || existingClaim.exists()) {
                    throw IllegalStateException("You have already redeemed a referral code")
                }

                val myUsername = meSnap.getString("username").orEmpty().ifBlank { "User" }
                val myAvatar = meSnap.getString("profilePicUrl") ?: meSnap.getString("avatar_url") ?: ""

                tx.set(
                    currentUserRef,
                    mapOf(
                        "referredByCode" to code,
                        "referredByUserId" to referrerId,
                        "coins" to FieldValue.increment(refereeBonus)
                    ),
                    SetOptions.merge()
                )
                tx.set(
                    referrerRef,
                    mapOf(
                        "coins" to FieldValue.increment(referrerReward),
                        "totalReferrals" to FieldValue.increment(1L),
                        "referralCoinsEarned" to FieldValue.increment(referrerReward)
                    ),
                    SetOptions.merge()
                )
                tx.set(
                    claimRef,
                    mapOf(
                        "referrerId" to referrerId,
                        "referrerUsername" to referrerUsername,
                        "refereeId" to currentUserId,
                        "refereeUsername" to myUsername,
                        "refereeAvatarUrl" to myAvatar,
                        "referralCode" to code,
                        "referrerCoins" to referrerReward,
                        "refereeCoins" to refereeBonus,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            }.await()

            Result.success("Congrats! +$refereeBonus coins added to your wallet & +$referrerReward coins awarded to @$referrerUsername!")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ReferralRepo", "Redeem failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun claimMilestoneBonus(userId: String, milestone: ReferralMilestone): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val db = RepositoryManager.firestore
            val userRef = db.collection("users").document(userId)
            db.runTransaction { tx ->
                val snap = tx.get(userRef)
                val invites = (snap.getLong("totalReferrals") ?: 0L).toInt()
                val claimed = (snap.get("claimedReferralMilestones") as? List<*>)
                    ?.mapNotNull { (it as? Number)?.toInt() }
                    ?.toMutableList()
                    ?: mutableListOf()
                if (invites < milestone.requiredInvites) {
                    throw IllegalStateException("Invite ${milestone.requiredInvites - invites} more friends to unlock this tier")
                }
                if (claimed.contains(milestone.requiredInvites)) {
                    throw IllegalStateException("Milestone already claimed")
                }
                claimed.add(milestone.requiredInvites)
                tx.set(
                    userRef,
                    mapOf(
                        "claimedReferralMilestones" to claimed,
                        "coins" to FieldValue.increment(milestone.bonusCoins),
                        "referralCoinsEarned" to FieldValue.increment(milestone.bonusCoins)
                    ),
                    SetOptions.merge()
                )
            }.await()
            Result.success(milestone.bonusCoins)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listenUserReferralClaims(referrerId: String): Flow<List<ReferralClaim>> = callbackFlow {
        if (referrerId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = RepositoryManager.firestore.collection(CLAIMS_COLLECTION)
            .whereEqualTo("referrerId", referrerId)
            .limit(40)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                if (snap != null) {
                    val claims = snap.documents.map { doc ->
                        ReferralClaim(
                            id = doc.id,
                            referrerId = doc.getString("referrerId").orEmpty(),
                            referrerUsername = doc.getString("referrerUsername").orEmpty(),
                            refereeId = doc.getString("refereeId").orEmpty(),
                            refereeUsername = doc.getString("refereeUsername").orEmpty().ifBlank { "Friend" },
                            refereeAvatarUrl = doc.getString("refereeAvatarUrl").orEmpty(),
                            referralCode = doc.getString("referralCode").orEmpty(),
                            referrerCoins = doc.getLong("referrerCoins") ?: 0L,
                            refereeCoins = doc.getLong("refereeCoins") ?: 0L,
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    }.sortedByDescending { it.timestamp }
                    trySend(claims)
                }
            }
        awaitClose { listener.remove() }
    }

    fun listenAllReferralClaimsForAdmin(limit: Long = 50L): Flow<List<ReferralClaim>> = callbackFlow {
        val listener = RepositoryManager.firestore.collection(CLAIMS_COLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                if (snap != null) {
                    val claims = snap.documents.map { doc ->
                        ReferralClaim(
                            id = doc.id,
                            referrerId = doc.getString("referrerId").orEmpty(),
                            referrerUsername = doc.getString("referrerUsername").orEmpty().ifBlank { "Creator" },
                            refereeId = doc.getString("refereeId").orEmpty(),
                            refereeUsername = doc.getString("refereeUsername").orEmpty().ifBlank { "User" },
                            refereeAvatarUrl = doc.getString("refereeAvatarUrl").orEmpty(),
                            referralCode = doc.getString("referralCode").orEmpty(),
                            referrerCoins = doc.getLong("referrerCoins") ?: 0L,
                            refereeCoins = doc.getLong("refereeCoins") ?: 0L,
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    }
                    trySend(claims)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun fetchTopReferrers(limit: Long = 10L): List<ReferralLeaderboardEntry> = withContext(Dispatchers.IO) {
        try {
            val snap = RepositoryManager.firestore.collection("users")
                .whereGreaterThan("totalReferrals", 0)
                .orderBy("totalReferrals", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
            snap.documents.map { doc ->
                ReferralLeaderboardEntry(
                    userId = doc.id,
                    username = doc.getString("username").orEmpty().ifBlank { "Creator" },
                    avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatar_url") ?: "",
                    totalReferrals = (doc.getLong("totalReferrals") ?: 0L).toInt(),
                    totalCoinsEarned = doc.getLong("referralCoinsEarned") ?: 0L
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ReferralRepo", "Top referrers fetch failed: ${e.message}")
            emptyList()
        }
    }
}
