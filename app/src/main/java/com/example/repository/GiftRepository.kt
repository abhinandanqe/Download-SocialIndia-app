package com.example.repository

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.example.model.Comment
import com.example.model.RepositoryManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

data class LiveGiftModel(
    val id: String = "",
    val name: String = "",
    val emoji: String = "🎁",
    val coins: Int = 1,
    val category: String = "Popular",
    val order: Int = 0,
    val enabled: Boolean = true,
    val hexColor: String = "#FF2A6D",
    val sticker3dUrl: String = ""
) {
    val composeColor: Color
        get() = try {
            Color(android.graphics.Color.parseColor(hexColor))
        } catch (e: Exception) {
            Color(0xFFFF2A6D)
        }
}

data class RealtimeLiveGiftEvent(
    val id: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val senderAvatar: String = "",
    val receiverId: String = "",
    val giftId: String = "",
    val giftName: String = "",
    val emoji: String = "🎁",
    val sticker3dUrl: String = "",
    val coins: Int = 1,
    val liveId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class GiftRepository(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    companion object {
        val DEFAULT_GIFTS = listOf(
            LiveGiftModel("rose", "Rose", "🌹", 1, "Popular", 1, true, "#FF2A6D", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f339/512.gif"),
            LiveGiftModel("heart", "Sparkle Heart", "💖", 5, "Popular", 2, true, "#FF007F", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f496/512.gif"),
            LiveGiftModel("applause", "Clap & Cheer", "👏", 10, "Fun", 3, true, "#FFD700", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f44f/512.gif"),
            LiveGiftModel("party", "Party Popper", "🎉", 25, "Fun", 4, true, "#FF6B6B", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f389/512.gif"),
            LiveGiftModel("diamond", "3D Diamond", "💎", 50, "Luxury", 5, true, "#00F0FF", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f48e/512.gif"),
            LiveGiftModel("crown", "Royal Crown", "👑", 100, "Luxury", 6, true, "#FFC107", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f451/512.gif"),
            LiveGiftModel("rocket", "Galaxy Rocket", "🚀", 500, "Special", 7, true, "#9D00FF", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f680/512.gif"),
            LiveGiftModel("fire", "Super Fire", "🔥", 250, "Popular", 8, true, "#FF3D00", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f525/512.gif"),
            LiveGiftModel("sports_car", "Speed Car", "🏎️", 1000, "Luxury", 9, true, "#FF1744", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f3ce_fe0f/512.gif"),
            LiveGiftModel("galaxy", "Cosmic Star", "🌟", 2000, "Special", 10, true, "#00E5FF", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f31f/512.gif")
        )
    }

    /**
     * Real-time stream of incoming gifts for a specific live stream.
     * Emits newly added gift events so host and all viewers see real-time animated overlays.
     */
    fun observeLiveStreamGifts(liveStoryId: String): Flow<RealtimeLiveGiftEvent> = callbackFlow {
        if (liveStoryId.isBlank()) {
            awaitClose {}
            return@callbackFlow
        }
        val listenStartTime = System.currentTimeMillis() - 20000L
        val seenGiftIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val listener = firestore.collection("gifts")
            .whereEqualTo("liveId", liveStoryId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                for (change in snapshot.documentChanges) {
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val docId = doc.id
                        if (!seenGiftIds.add(docId)) continue

                        val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        if (ts >= listenStartTime) {
                            val event = RealtimeLiveGiftEvent(
                                id = docId,
                                senderId = doc.getString("senderId") ?: "",
                                senderUsername = doc.getString("senderUsername") ?: "Fan",
                                senderAvatar = doc.getString("senderAvatar") ?: "",
                                receiverId = doc.getString("receiverId") ?: "",
                                giftId = doc.getString("giftId") ?: "",
                                giftName = doc.getString("giftName") ?: "Gift",
                                emoji = doc.getString("emoji") ?: "🎁",
                                sticker3dUrl = doc.getString("sticker3dUrl") ?: "",
                                coins = doc.getLong("coins")?.toInt() ?: 1,
                                liveId = liveStoryId,
                                timestamp = ts
                            )
                            trySend(event)
                        }
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Observe enabled gifts for viewers in Live Stream.
     */
    fun observeActiveGifts(): Flow<List<LiveGiftModel>> = callbackFlow {
        val listener = firestore.collection("admin_gifts")
            .whereEqualTo("enabled", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("GiftRepository", "Error observing admin_gifts: ${error.message}")
                    trySend(DEFAULT_GIFTS)
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) {
                    trySend(DEFAULT_GIFTS)
                } else {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(LiveGiftModel::class.java)?.copy(id = doc.id)
                    }.sortedBy { it.order }
                    trySend(if (list.isEmpty()) DEFAULT_GIFTS else list)
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Observe all gifts for Admin Gift Manager.
     */
    fun observeAllGiftsAdmin(): Flow<List<LiveGiftModel>> = callbackFlow {
        val listener = firestore.collection("admin_gifts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(DEFAULT_GIFTS)
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) {
                    trySend(DEFAULT_GIFTS)
                } else {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(LiveGiftModel::class.java)?.copy(id = doc.id)
                    }.sortedBy { it.order }
                    trySend(if (list.isEmpty()) DEFAULT_GIFTS else list)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveGift(gift: LiveGiftModel): Boolean = withContext(Dispatchers.IO) {
        try {
            val giftId = if (gift.id.isBlank()) "gift_${UUID.randomUUID().toString().take(8)}" else gift.id
            val data = hashMapOf(
                "id" to giftId,
                "name" to gift.name,
                "emoji" to gift.emoji,
                "coins" to gift.coins,
                "category" to gift.category,
                "order" to gift.order,
                "enabled" to gift.enabled,
                "hexColor" to gift.hexColor,
                "sticker3dUrl" to gift.sticker3dUrl
            )
            firestore.collection("admin_gifts").document(giftId).set(data, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e("GiftRepository", "Error saving gift", e)
            false
        }
    }

    suspend fun toggleGiftEnabled(giftId: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("admin_gifts").document(giftId).update("enabled", enabled).await()
            true
        } catch (e: Exception) {
            Log.e("GiftRepository", "Error toggling gift status", e)
            false
        }
    }

    suspend fun deleteGift(giftId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("admin_gifts").document(giftId).delete().await()
            true
        } catch (e: Exception) {
            Log.e("GiftRepository", "Error deleting gift", e)
            false
        }
    }

    suspend fun seedDefaultGiftsIfEmpty() = withContext(Dispatchers.IO) {
        try {
            val snap = firestore.collection("admin_gifts").limit(1).get().await()
            if (snap.isEmpty) {
                DEFAULT_GIFTS.forEach { gift ->
                    saveGift(gift)
                }
            }
        } catch (e: Exception) {
            Log.e("GiftRepository", "Error seeding default gifts", e)
        }
    }

    /**
     * Securely execute a coin deduction & gift sending transaction.
     */
    suspend fun sendGift(
        senderId: String,
        senderUsername: String,
        senderAvatar: String,
        isSenderVerified: Boolean,
        streamerId: String,
        liveStoryId: String,
        gift: LiveGiftModel
    ): Result<Double> = withContext(Dispatchers.IO) {
        if (senderId.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
        if (streamerId.isEmpty()) return@withContext Result.failure(Exception("Streamer not found"))

        try {
            var newBalance = 0.0
            firestore.runTransaction { transaction ->
                val senderRef = firestore.collection("users").document(senderId)
                val streamerRef = firestore.collection("users").document(streamerId)

                val senderDoc = transaction.get(senderRef)
                val streamerDoc = transaction.get(streamerRef)

                val senderCoins = senderDoc.getLong("coins") ?: 0L
                val senderCash = senderDoc.getDouble("walletBalance")
                    ?: (senderDoc.getDouble("earnings") ?: 0.0)
                val totalAvailable = senderCoins + senderCash.toLong()

                if (totalAvailable < gift.coins) {
                    throw IllegalStateException("Insufficient coins: need ${gift.coins}, have ${totalAvailable.toInt()}")
                }

                if (senderCoins >= gift.coins) {
                    transaction.update(senderRef, "coins", senderCoins - gift.coins)
                    newBalance = senderCash
                } else {
                    val remaining = gift.coins - senderCoins
                    transaction.update(senderRef, "coins", 0L)
                    newBalance = (senderCash - remaining).coerceAtLeast(0.0)
                    transaction.update(senderRef, "walletBalance", newBalance)
                }

                val streamerCoins = streamerDoc.getLong("coins") ?: 0L
                transaction.update(streamerRef, "coins", streamerCoins + gift.coins)
                val streamerEarnings = streamerDoc.getDouble("earnings") ?: 0.0
                transaction.update(streamerRef, "earnings", streamerEarnings + gift.coins)

                // Record gift in "gifts" collection
                val giftRecord = hashMapOf(
                    "id" to UUID.randomUUID().toString(),
                    "senderId" to senderId,
                    "senderUsername" to senderUsername,
                    "senderAvatar" to senderAvatar,
                    "receiverId" to streamerId,
                    "giftId" to gift.id,
                    "giftName" to gift.name,
                    "emoji" to gift.emoji,
                    "sticker3dUrl" to gift.sticker3dUrl,
                    "coins" to gift.coins,
                    "liveId" to liveStoryId,
                    "timestamp" to System.currentTimeMillis()
                )
                transaction.set(firestore.collection("gifts").document(), giftRecord)
            }.await()

            // Post announcement comment in Live Chat
            try {
                val giftComment = Comment(
                    id = UUID.randomUUID().toString(),
                    userId = senderId,
                    username = senderUsername,
                    avatarUrl = senderAvatar,
                    isVerified = isSenderVerified,
                    text = "🎁 Sent a ${gift.name} ${gift.emoji} (${gift.coins} Coins)"
                )
                RepositoryManager.postRepository.addStoryComment(liveStoryId, giftComment)
            } catch (e: Exception) {
                Log.w("GiftRepository", "Failed to add story comment: ${e.message}")
            }

            // Generate notification for streamer
            try {
                val notifData = hashMapOf(
                    "type" to "LIVE_GIFT",
                    "actorId" to senderId,
                    "actorUsername" to senderUsername,
                    "actorAvatar" to senderAvatar,
                    "message" to "sent you a ${gift.name} ${gift.emoji} (${gift.coins} Coins) during your live!",
                    "coins" to gift.coins,
                    "giftId" to gift.id,
                    "liveId" to liveStoryId,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "isRead" to false
                )
                firestore.collection("users").document(streamerId).collection("notifications").add(notifData)
            } catch (e: Exception) {
                Log.w("GiftRepository", "Failed to send notification: ${e.message}")
            }

            Result.success(newBalance)
        } catch (e: Exception) {
            Log.e("GiftRepository", "sendGift failed", e)
            Result.failure(e)
        }
    }

    /**
     * Transactional Super Gift / Creator Tip for Reels, Posts, and Stories.
     * Credits creator's coins, earnings, walletBalance, writes to gifts & creator_earnings_ledger,
     * and dispatches an instant notification.
     */
    suspend fun sendContentGift(
        senderId: String,
        senderUsername: String,
        senderAvatar: String,
        creatorId: String,
        contentId: String,
        contentType: String, // "REEL", "POST", "STORY"
        gift: LiveGiftModel
    ): Result<Double> = withContext(Dispatchers.IO) {
        if (senderId.isEmpty()) return@withContext Result.failure(Exception("Please sign in first"))
        if (creatorId.isEmpty()) return@withContext Result.failure(Exception("Creator not found"))
        if (senderId == creatorId) return@withContext Result.failure(Exception("Cannot gift your own content"))

        try {
            var remainingWallet = 0.0
            val ledgerId = "gift_${UUID.randomUUID().toString().take(12)}"
            val netCreatorRupees = gift.coins * 0.90
            val platformFeeRupees = gift.coins * 0.10

            firestore.runTransaction { tx ->
                val senderRef = firestore.collection("users").document(senderId)
                val creatorRef = firestore.collection("users").document(creatorId)

                val senderDoc = tx.get(senderRef)
                val creatorDoc = tx.get(creatorRef)

                val senderCoins = senderDoc.getLong("coins") ?: 0L
                val senderWallet = senderDoc.getDouble("walletBalance") ?: 0.0
                val totalAvailable = senderCoins + senderWallet.toLong()

                if (totalAvailable < gift.coins) {
                    throw IllegalStateException("Need ${gift.coins} coins (you have ${totalAvailable.toInt()}). Claim daily free coins!")
                }

                if (senderCoins >= gift.coins) {
                    tx.update(senderRef, "coins", senderCoins - gift.coins)
                    remainingWallet = senderWallet
                } else {
                    val shortfall = gift.coins - senderCoins
                    tx.update(senderRef, "coins", 0L)
                    remainingWallet = (senderWallet - shortfall).coerceAtLeast(0.0)
                    tx.update(senderRef, "walletBalance", remainingWallet)
                }

                val creatorCoins = creatorDoc.getLong("coins") ?: 0L
                val creatorEarnings = creatorDoc.getDouble("earnings") ?: 0.0
                val creatorWallet = creatorDoc.getDouble("walletBalance") ?: 0.0

                tx.update(
                    creatorRef,
                    mapOf(
                        "coins" to (creatorCoins + gift.coins),
                        "earnings" to (creatorEarnings + netCreatorRupees),
                        "walletBalance" to (creatorWallet + netCreatorRupees)
                    )
                )

                val nowMs = System.currentTimeMillis()
                val giftRecord = hashMapOf(
                    "id" to ledgerId,
                    "senderId" to senderId,
                    "senderUsername" to senderUsername,
                    "senderAvatar" to senderAvatar,
                    "receiverId" to creatorId,
                    "giftId" to gift.id,
                    "giftName" to gift.name,
                    "emoji" to gift.emoji,
                    "coins" to gift.coins,
                    "contentId" to contentId,
                    "contentType" to contentType,
                    "timestamp" to nowMs
                )
                tx.set(firestore.collection("gifts").document(ledgerId), giftRecord)

                val ledgerEntry = hashMapOf(
                    "id" to ledgerId,
                    "creatorUid" to creatorId,
                    "payerUid" to senderId,
                    "payerUsername" to senderUsername,
                    "sourceType" to "${contentType}_GIFT",
                    "contentId" to contentId,
                    "description" to "${gift.emoji} ${gift.name} on $contentType from @$senderUsername",
                    "grossAmount" to gift.coins.toDouble(),
                    "platformFee" to platformFeeRupees,
                    "netCreatorAmount" to netCreatorRupees,
                    "status" to "CREDITED",
                    "timestamp" to nowMs
                )
                tx.set(firestore.collection("creator_earnings_ledger").document(ledgerId), ledgerEntry)
            }.await()

            try {
                val label = when (contentType) {
                    "REEL" -> "Reel"
                    "STORY" -> "Story"
                    else -> "Post"
                }
                val notifData = hashMapOf(
                    "type" to "${contentType}_GIFT",
                    "actorId" to senderId,
                    "actorUsername" to senderUsername,
                    "actorAvatar" to senderAvatar,
                    "content" to "sent you ${gift.emoji} ${gift.name} (+₹${netCreatorRupees.toInt()}) on your $label!",
                    "message" to "sent you ${gift.emoji} ${gift.name} (+₹${netCreatorRupees.toInt()}) on your $label!",
                    "postId" to contentId,
                    "coins" to gift.coins,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "isRead" to false
                )
                firestore.collection("users").document(creatorId).collection("notifications").add(notifData)
            } catch (e: Exception) {
                Log.w("GiftRepository", "Failed to send content gift notification: ${e.message}")
            }

            Result.success(remainingWallet)
        } catch (e: Exception) {
            Log.e("GiftRepository", "sendContentGift failed", e)
            Result.failure(e)
        }
    }
}
