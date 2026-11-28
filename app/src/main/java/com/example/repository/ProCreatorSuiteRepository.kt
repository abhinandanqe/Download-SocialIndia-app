package com.example.repository

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.example.model.AutoCaptionStyle
import com.example.model.ChatUpgradePrefs
import com.example.model.CollabBooking
import com.example.model.CosmeticItem
import com.example.model.CreatorRateCard
import com.example.model.DailyRewardsState
import com.example.model.LeaderboardEntry
import com.example.model.LuckyCoinBox
import com.example.model.RepositoryManager
import com.example.model.SmartReelTemplate
import com.example.model.User
import com.example.model.VerificationStatus
import com.example.model.Video
import com.example.model.WatchPartyChatMessage
import com.example.model.WatchPartySession
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object ProCreatorSuiteRepository {

    private val db: FirebaseFirestore
        get() = if (RepositoryManager.isInitialized()) RepositoryManager.firestore else FirebaseFirestore.getInstance()

    private const val CACHE_TTL_MS = 30_000L
    private val leaderboardCache = ConcurrentHashMap<String, Pair<Long, List<LeaderboardEntry>>>()
    @Volatile
    private var rateCardsCache: Pair<Long, List<CreatorRateCard>>? = null

    val DAILY_STREAK_REWARDS = listOf(2, 3, 5, 5, 8, 10, 15)

    val SPIN_WHEEL_SEGMENTS = listOf(
        1 to Color(0xFF3B82F6),
        2 to Color(0xFF8B5CF6),
        3 to Color(0xFFEC4899),
        5 to Color(0xFFF59E0B),
        2 to Color(0xFF10B981),
        8 to Color(0xFF06B6D4),
        10 to Color(0xFFF97316),
        20 to Color(0xFFEAB308)
    )

    val COSMETIC_CATALOG: List<CosmeticItem> = listOf(
        // Animated Avatar Frames
        CosmeticItem(
            id = "frame_royal_gold",
            type = "FRAME",
            name = "Royal Gold Aura",
            description = "Spinning 24K golden halo around your profile picture",
            priceCoins = 120,
            primaryColor = Color(0xFFFFD700),
            secondaryColor = Color(0xFFFF8C00),
            accentColor = Color(0xFFFFF3B0),
            emoji = "👑",
            badgeLabel = "VIP"
        ),
        CosmeticItem(
            id = "frame_cyber_neon",
            type = "FRAME",
            name = "Cyber Neon Pulse",
            description = "Electric cyan & magenta rotating cyber ring",
            priceCoins = 150,
            primaryColor = Color(0xFF00F0FF),
            secondaryColor = Color(0xFFFF00C8),
            accentColor = Color(0xFF7B2FFF),
            emoji = "⚡",
            badgeLabel = "HOT"
        ),
        CosmeticItem(
            id = "frame_crimson_flame",
            type = "FRAME",
            name = "Inferno Flame Ring",
            description = "Blazing ember ring for top creators & streamers",
            priceCoins = 180,
            primaryColor = Color(0xFFFF2A54),
            secondaryColor = Color(0xFFFF9100),
            accentColor = Color(0xFFFFEA00),
            emoji = "🔥",
            badgeLabel = "PRO"
        ),
        CosmeticItem(
            id = "frame_diamond_halo",
            type = "FRAME",
            name = "Diamond Crystal Halo",
            description = "Icy prism sparkle ring with luxury glow",
            priceCoins = 300,
            primaryColor = Color(0xFF60EFFF),
            secondaryColor = Color(0xFF0061FF),
            accentColor = Color(0xFFE0F7FA),
            emoji = "💎",
            badgeLabel = "LUXURY"
        ),
        CosmeticItem(
            id = "frame_emerald_crown",
            type = "FRAME",
            name = "Emerald Prestige",
            description = "Rich emerald & gold aurora border",
            priceCoins = 200,
            primaryColor = Color(0xFF10B981),
            secondaryColor = Color(0xFF34D399),
            accentColor = Color(0xFFFDE047),
            emoji = "🌿",
            badgeLabel = "RARE"
        ),
        // Profile Themes
        CosmeticItem(
            id = "theme_obsidian_gold",
            type = "THEME",
            name = "Obsidian Gold",
            description = "Luxury velvet black infused with warm golden light",
            priceCoins = 100,
            primaryColor = Color(0xFF1A1408),
            secondaryColor = Color(0xFF2D2109),
            accentColor = Color(0xFFFFD700),
            emoji = "✨",
            badgeLabel = "POPULAR"
        ),
        CosmeticItem(
            id = "theme_midnight_aurora",
            type = "THEME",
            name = "Midnight Aurora",
            description = "Northern lights teal & indigo atmospheric profile backdrop",
            priceCoins = 120,
            primaryColor = Color(0xFF091B2A),
            secondaryColor = Color(0xFF132D46),
            accentColor = Color(0xFF00F5D4),
            emoji = "🌌",
            badgeLabel = "NEW"
        ),
        CosmeticItem(
            id = "theme_sunset_velvet",
            type = "THEME",
            name = "Sunset Velvet",
            description = "Warm rose-gold & deep plum creator studio vibe",
            priceCoins = 110,
            primaryColor = Color(0xFF260D1D),
            secondaryColor = Color(0xFF3D132B),
            accentColor = Color(0xFFFF4D8D),
            emoji = "🌅",
            badgeLabel = "TRENDING"
        ),
        CosmeticItem(
            id = "theme_cyber_synth",
            type = "THEME",
            name = "Cyber Synthwave",
            description = "Futuristic ultraviolet & neon blue glow theme",
            priceCoins = 160,
            primaryColor = Color(0xFF120929),
            secondaryColor = Color(0xFF1F1147),
            accentColor = Color(0xFF00E5FF),
            emoji = "🪩",
            badgeLabel = "VIP"
        ),
        // VIP Comment Entrance Badges
        CosmeticItem(
            id = "entrance_vip_crown",
            type = "ENTRANCE",
            name = "VIP Crown Tag",
            description = "Golden VIP Crown badge shown beside your comments & profile",
            priceCoins = 90,
            primaryColor = Color(0xFFFFD700),
            secondaryColor = Color(0xFFFF8F00),
            accentColor = Color(0xFFFFFFFF),
            emoji = "👑",
            badgeLabel = "VIP"
        ),
        CosmeticItem(
            id = "entrance_diamond_fan",
            type = "ENTRANCE",
            name = "Diamond Supporter",
            description = "Crystal badge that highlights your replies in Reels & Live",
            priceCoins = 140,
            primaryColor = Color(0xFF38BDF8),
            secondaryColor = Color(0xFF2563EB),
            accentColor = Color(0xFFFFFFFF),
            emoji = "💎",
            badgeLabel = "SUPPORTER"
        ),
        CosmeticItem(
            id = "entrance_fire_legend",
            type = "ENTRANCE",
            name = "Fire Legend Aura",
            description = "Fiery legend tag with blazing accent highlight",
            priceCoins = 180,
            primaryColor = Color(0xFFFF4D4D),
            secondaryColor = Color(0xFFFF9500),
            accentColor = Color(0xFFFFFFFF),
            emoji = "🔥",
            badgeLabel = "LEGEND"
        )
    )

    val SMART_TEMPLATES: List<SmartReelTemplate> = listOf(
        SmartReelTemplate(
            id = "tpl_beat_drop",
            name = "Beat Drop Montage",
            subtitle = "Fast 1.4s bass-synced photo/clip cuts with zoom punch",
            bpm = 128,
            clipDurationMs = 1400L,
            musicTitle = "Midnight Pulse (Beat Mix)",
            musicArtist = "Studio DJ",
            musicUrl = "",
            primaryColor = Color(0xFFFF007F),
            secondaryColor = Color(0xFF7B2FFF),
            transitionStyle = "ZOOM_PULSE",
            defaultCaptionStyle = "neon_pop"
        ),
        SmartReelTemplate(
            id = "tpl_golden_cinema",
            name = "Golden Hour Cinema",
            subtitle = "Dreamy 2.4s warm crossfade for travel & lifestyle",
            bpm = 96,
            clipDurationMs = 2400L,
            musicTitle = "Golden Horizon",
            musicArtist = "Acoustic Waves",
            musicUrl = "",
            primaryColor = Color(0xFFFFA726),
            secondaryColor = Color(0xFFE65100),
            transitionStyle = "CINEMATIC_FADE",
            defaultCaptionStyle = "minimal_sub"
        ),
        SmartReelTemplate(
            id = "tpl_cyber_flash",
            name = "Cyber Flash Cut",
            subtitle = "High-energy 1.1s neon slide flash transitions",
            bpm = 140,
            clipDurationMs = 1100L,
            musicTitle = "Neon Velocity",
            musicArtist = "Synthwave Lab",
            musicUrl = "",
            primaryColor = Color(0xFF00E5FF),
            secondaryColor = Color(0xFF2979FF),
            transitionStyle = "SLIDE_FLASH",
            defaultCaptionStyle = "karaoke"
        ),
        SmartReelTemplate(
            id = "tpl_polaroid_story",
            name = "Polaroid Memories",
            subtitle = "Scrapbook 2.0s frame flip with lyric subtitles",
            bpm = 104,
            clipDurationMs = 2000L,
            musicTitle = "Lo-Fi Sunset Tape",
            musicArtist = "Chillhop Beats",
            musicUrl = "",
            primaryColor = Color(0xFFEC4899),
            secondaryColor = Color(0xFFF43F5E),
            transitionStyle = "POLAROID_FLIP",
            defaultCaptionStyle = "bold_box"
        )
    )

    val AUTO_CAPTION_STYLES: List<AutoCaptionStyle> = listOf(
        AutoCaptionStyle("neon_pop", "Neon Pop", Color.White, Color(0xCC0D0D1A), Color(0xFF00F0FF)),
        AutoCaptionStyle("karaoke", "Karaoke Gold", Color.White, Color(0xB8000000), Color(0xFFFFD700)),
        AutoCaptionStyle("bold_box", "Bold Box", Color.Black, Color(0xF2FFFFFF), Color(0xFFFF2A6D)),
        AutoCaptionStyle("minimal_sub", "Cinema Sub", Color(0xFFF8FAFC), Color(0x88000000), Color(0xFFFFB74D))
    )

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun isDocVerified(doc: com.google.firebase.firestore.DocumentSnapshot): Boolean {
        val suspended = doc.getBoolean("verificationSuspended") ?: false
        if (suspended) return false
        val status = doc.getString("verificationStatus") ?: ""
        return (doc.getBoolean("isVerified") ?: doc.getBoolean("verified") ?: false) ||
            status == "approved" || status == VerificationStatus.VERIFIED_ACTIVE
    }

    // ==================== 1. CREATOR LEADERBOARD & WEEKLY REWARDS ====================

    suspend fun fetchLeaderboard(
        category: String,
        period: String,
        forceRefresh: Boolean = false
    ): List<LeaderboardEntry> = withContext(Dispatchers.IO) {
        val cacheKey = "${category}_${period}"
        if (!forceRefresh) {
            leaderboardCache[cacheKey]?.let { (ts, cached) ->
                if (System.currentTimeMillis() - ts < CACHE_TTL_MS && cached.isNotEmpty()) {
                    return@withContext cached
                }
            }
        }
        try {
            if (category == "SUPPORTERS") {
                val cutoffMs = if (period == "WEEKLY") {
                    System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000L
                } else {
                    System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L
                }
                val giftsSnap = db.collection("gifts").limit(300).get().await()
                val totalsByUser = mutableMapOf<String, Long>()
                val nameByUser = mutableMapOf<String, String>()
                val avatarByUser = mutableMapOf<String, String>()
                for (doc in giftsSnap.documents) {
                    val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    if (ts < cutoffMs) continue
                    val senderId = doc.getString("senderId") ?: continue
                    if (senderId.isBlank()) continue
                    val coins = doc.getLong("coins") ?: 0L
                    totalsByUser[senderId] = (totalsByUser[senderId] ?: 0L) + coins
                    val uname = doc.getString("senderUsername") ?: ""
                    if (uname.isNotBlank()) nameByUser[senderId] = uname
                    val av = doc.getString("senderAvatar") ?: ""
                    if (av.isNotBlank()) avatarByUser[senderId] = av
                }
                if (totalsByUser.isNotEmpty()) {
                    val sortedIds = totalsByUser.entries.sortedByDescending { it.value }.take(30)
                    val userDocs = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
                    sortedIds.map { it.key }.chunked(10).forEach { chunk ->
                        try {
                            val snap = db.collection("users")
                                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                                .get().await()
                            snap.documents.forEach { userDocs[it.id] = it }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {}
                    }
                    val supporterEntries = sortedIds.mapIndexed { index, (uid, totalCoins) ->
                        val doc = userDocs[uid]
                        LeaderboardEntry(
                            userId = uid,
                            username = doc?.getString("username")?.ifBlank { null } ?: nameByUser[uid] ?: "Supporter",
                            displayName = doc?.getString("displayName") ?: doc?.getString("name") ?: "",
                            avatarUrl = doc?.getString("avatar_url") ?: doc?.getString("profilePicUrl") ?: avatarByUser[uid] ?: "",
                            isVerified = doc?.let { isDocVerified(it) } ?: false,
                            verificationBadgeUrl = doc?.getString("verificationBadgeUrl") ?: "",
                            score = totalCoins,
                            metricLabel = "$totalCoins Coins Gifted",
                            coins = totalCoins,
                            rank = index + 1,
                            equippedFrameId = doc?.getString("equippedFrameId") ?: "",
                            supporterBadge = when (index) {
                                0 -> "CROWN_1"
                                1 -> "CROWN_2"
                                2 -> "CROWN_3"
                                else -> "TOP_SUPPORTER"
                            }
                        )
                    }
                    leaderboardCache[cacheKey] = System.currentTimeMillis() to supporterEntries
                    return@withContext supporterEntries
                }
            }

            // EARNERS or TRENDING (or fallback if no gifts yet)
            val usersSnap = db.collection("users").limit(80).get().await()
            val mapped = usersSnap.documents.mapNotNull { doc ->
                val uname = doc.getString("username")?.takeIf { it.isNotBlank() }
                    ?: doc.getString("displayName")?.takeIf { it.isNotBlank() }
                    ?: doc.getString("name")?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val earnings = doc.getDouble("earnings") ?: 0.0
                val coins = doc.getLong("coins") ?: 0L
                val followers = (doc.getLong("followersCount") ?: doc.getLong("followerCount") ?: 0L).toInt()
                val weeklyScore = doc.getLong("weeklyScore") ?: 0L
                val periodFactor = if (period == "MONTHLY") 1.35 else 1.0

                val score = when (category) {
                    "EARNERS" -> ((earnings * 10.0) + coins).toLong()
                    "SUPPORTERS" -> coins
                    else -> ((followers * 12L) + weeklyScore + (coins / 2L))
                }.let { (it * periodFactor).toLong() }

                val label = when (category) {
                    "EARNERS" -> "₹${earnings.toInt()} • $coins coins"
                    "SUPPORTERS" -> "$coins Coins"
                    else -> "$followers Followers"
                }

                LeaderboardEntry(
                    userId = doc.id,
                    username = uname,
                    displayName = doc.getString("displayName") ?: doc.getString("name") ?: "",
                    avatarUrl = doc.getString("avatar_url") ?: doc.getString("profilePicUrl") ?: "",
                    isVerified = isDocVerified(doc),
                    verificationBadgeUrl = doc.getString("verificationBadgeUrl") ?: "",
                    score = score,
                    metricLabel = label,
                    coins = coins,
                    earnings = earnings,
                    followersCount = followers,
                    equippedFrameId = doc.getString("equippedFrameId") ?: ""
                )
            }.sortedByDescending { it.score }
                .take(30)
                .mapIndexed { idx, entry ->
                    entry.copy(
                        rank = idx + 1,
                        supporterBadge = when (idx) {
                            0 -> "CROWN_1"
                            1 -> "CROWN_2"
                            2 -> "CROWN_3"
                            else -> if (idx < 10) "TOP_SUPPORTER" else ""
                        }
                    )
                }
            leaderboardCache[cacheKey] = System.currentTimeMillis() to mapped
            mapped
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ProCreatorSuiteRepo", "fetchLeaderboard failed", e)
            leaderboardCache[cacheKey]?.second ?: emptyList()
        }
    }

    suspend fun claimWeeklyLeaderboardBonus(userId: String, rank: Int): Result<Int> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext Result.failure(Exception("Sign in required"))
        val weekKey = SimpleDateFormat("yyyy-'W'ww", Locale.US).format(Date())
        val bonus = when (rank) {
            1 -> 50
            2 -> 30
            3 -> 20
            in 4..10 -> 10
            else -> 5
        }
        try {
            db.runTransaction { tx ->
                val userRef = db.collection("users").document(userId)
                val snap = tx.get(userRef)
                val lastClaim = snap.getString("lastLeaderboardRewardWeek") ?: ""
                if (lastClaim == weekKey) {
                    throw IllegalStateException("Already claimed this week's leaderboard reward!")
                }
                val currentCoins = snap.getLong("coins") ?: 0L
                val tier = when (rank) {
                    1 -> "GOLD"
                    2 -> "SILVER"
                    3 -> "BRONZE"
                    else -> snap.getString("creatorBadgeTier") ?: ""
                }
                tx.set(
                    userRef,
                    mapOf(
                        "coins" to (currentCoins + bonus),
                        "lastLeaderboardRewardWeek" to weekKey,
                        "creatorBadgeTier" to tier,
                        "weeklyScore" to ((snap.getLong("weeklyScore") ?: 0L) + 25L)
                    ),
                    SetOptions.merge()
                )
            }.await()
            leaderboardCache.clear()
            Result.success(bonus)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== 3. DAILY CHECK-IN STREAK, SPIN WHEEL & LUCKY BOX ====================

    fun observeDailyRewardsState(userId: String): Flow<DailyRewardsState> = callbackFlow {
        if (userId.isBlank()) {
            trySend(DailyRewardsState())
            awaitClose {}
            return@callbackFlow
        }
        val reg = db.collection("users").document(userId).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            val today = todayKey()
            val lastCheckIn = snap.getString("lastCheckInDate") ?: ""
            val lastSpin = snap.getString("lastSpinDate") ?: ""
            val streak = (snap.getLong("streakDay") ?: 0L).toInt().coerceIn(0, 7)
            val totalClaims = (snap.getLong("totalStreakClaims") ?: 0L).toInt()
            trySend(
                DailyRewardsState(
                    coins = snap.getLong("coins") ?: 0L,
                    streakDay = streak,
                    lastCheckInDate = lastCheckIn,
                    lastSpinDate = lastSpin,
                    canClaimCheckInToday = lastCheckIn != today,
                    canSpinToday = lastSpin != today,
                    totalStreakClaims = totalClaims
                )
            )
        }
        awaitClose { reg.remove() }
    }

    suspend fun claimDailyCheckIn(userId: String): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext Result.failure(Exception("Sign in required"))
        val today = todayKey()
        try {
            var awardedCoins = 15
            var nextDay = 1
            db.runTransaction { tx ->
                val ref = db.collection("users").document(userId)
                val doc = tx.get(ref)
                val lastDate = doc.getString("lastCheckInDate") ?: ""
                if (lastDate == today) {
                    throw IllegalStateException("Already checked in today! Come back tomorrow.")
                }
                val currentStreak = (doc.getLong("streakDay") ?: 0L).toInt()
                nextDay = if (currentStreak >= 7) 1 else (currentStreak + 1)
                awardedCoins = DAILY_STREAK_REWARDS[(nextDay - 1).coerceIn(0, DAILY_STREAK_REWARDS.lastIndex)]
                val coins = doc.getLong("coins") ?: 0L
                val totalClaims = (doc.getLong("totalStreakClaims") ?: 0L) + 1L
                tx.set(
                    ref,
                    mapOf(
                        "coins" to (coins + awardedCoins),
                        "streakDay" to nextDay,
                        "lastCheckInDate" to today,
                        "totalStreakClaims" to totalClaims,
                        "weeklyScore" to ((doc.getLong("weeklyScore") ?: 0L) + 15L)
                    ),
                    SetOptions.merge()
                )
            }.await()
            Result.success(nextDay to awardedCoins)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun claimSpinWheelReward(userId: String, wonCoins: Int): Result<Int> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext Result.failure(Exception("Sign in required"))
        val today = todayKey()
        try {
            db.runTransaction { tx ->
                val ref = db.collection("users").document(userId)
                val doc = tx.get(ref)
                val lastSpin = doc.getString("lastSpinDate") ?: ""
                if (lastSpin == today) {
                    throw IllegalStateException("You already used today's free spin!")
                }
                val coins = doc.getLong("coins") ?: 0L
                tx.set(
                    ref,
                    mapOf(
                        "coins" to (coins + wonCoins),
                        "lastSpinDate" to today,
                        "weeklyScore" to ((doc.getLong("weeklyScore") ?: 0L) + 20L)
                    ),
                    SetOptions.merge()
                )
            }.await()
            Result.success(wonCoins)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeActiveLuckyBoxes(): Flow<List<LuckyCoinBox>> = callbackFlow {
        val reg = db.collection("lucky_boxes")
            .limit(35)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                val boxes = snap.documents.mapNotNull { doc ->
                    val remaining = (doc.getLong("remainingCoins") ?: 0L).toInt()
                    val expiresAt = doc.getLong("expiresAt") ?: (now + 3600_000L)
                    if (remaining <= 0 || expiresAt < now) return@mapNotNull null
                    val rawClaimed = doc.get("claimedBy") as? Map<*, *>
                    val claimedMap = rawClaimed?.mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val amt = (v as? Number)?.toInt() ?: 0
                        key to amt
                    }?.toMap() ?: emptyMap()
                    LuckyCoinBox(
                        id = doc.id,
                        creatorId = doc.getString("creatorId") ?: "",
                        creatorUsername = doc.getString("creatorUsername") ?: "Creator",
                        creatorAvatar = doc.getString("creatorAvatar") ?: "",
                        isCreatorVerified = doc.getBoolean("isCreatorVerified") ?: false,
                        title = doc.getString("title") ?: "Lucky Treasure Box",
                        totalCoins = (doc.getLong("totalCoins") ?: 100L).toInt(),
                        remainingCoins = remaining,
                        maxClaimants = (doc.getLong("maxClaimants") ?: 10L).toInt(),
                        claimedBy = claimedMap,
                        createdAt = doc.getLong("createdAt") ?: now,
                        expiresAt = expiresAt
                    )
                }.sortedByDescending { it.createdAt }
                trySend(boxes)
            }
        awaitClose { reg.remove() }
    }

    suspend fun dropLuckyCoinBox(
        creatorId: String,
        creatorUsername: String,
        creatorAvatar: String,
        isVerified: Boolean,
        title: String,
        totalCoins: Int,
        maxClaimants: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        if (creatorId.isBlank()) return@withContext Result.failure(Exception("Sign in required"))
        if (totalCoins < 20) return@withContext Result.failure(Exception("Minimum 20 coins required"))
        try {
            val boxId = "box_${UUID.randomUUID().toString().take(10)}"
            val now = System.currentTimeMillis()
            db.runTransaction { tx ->
                val userRef = db.collection("users").document(creatorId)
                val userDoc = tx.get(userRef)
                val currentCoins = userDoc.getLong("coins") ?: 0L
                if (currentCoins < totalCoins) {
                    throw IllegalStateException("Need $totalCoins coins (you have $currentCoins). Claim Daily Streak or Spin first!")
                }
                tx.set(userRef, mapOf("coins" to (currentCoins - totalCoins)), SetOptions.merge())
                val boxRef = db.collection("lucky_boxes").document(boxId)
                tx.set(
                    boxRef,
                    mapOf(
                        "creatorId" to creatorId,
                        "creatorUsername" to creatorUsername,
                        "creatorAvatar" to creatorAvatar,
                        "isCreatorVerified" to isVerified,
                        "title" to title.ifBlank { "Lucky Coin Drop 🎁" },
                        "totalCoins" to totalCoins,
                        "remainingCoins" to totalCoins,
                        "maxClaimants" to maxClaimants.coerceIn(2, 50),
                        "claimedBy" to emptyMap<String, Int>(),
                        "createdAt" to now,
                        "expiresAt" to (now + 24L * 60 * 60 * 1000L)
                    )
                )
            }.await()
            Result.success(boxId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun claimLuckyCoinBox(boxId: String, userId: String): Result<Int> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext Result.failure(Exception("Sign in required"))
        try {
            var claimedAmount = 0
            db.runTransaction { tx ->
                val boxRef = db.collection("lucky_boxes").document(boxId)
                val userRef = db.collection("users").document(userId)
                val boxDoc = tx.get(boxRef)
                val userDoc = tx.get(userRef)
                if (!boxDoc.exists()) throw IllegalStateException("Lucky box expired")

                val remaining = (boxDoc.getLong("remainingCoins") ?: 0L).toInt()
                val maxClaimants = (boxDoc.getLong("maxClaimants") ?: 10L).toInt()
                val rawClaimed = (boxDoc.get("claimedBy") as? Map<*, *>) ?: emptyMap<String, Any>()
                if (rawClaimed.containsKey(userId)) {
                    throw IllegalStateException("You already claimed from this Lucky Box!")
                }
                if (remaining <= 0 || rawClaimed.size >= maxClaimants) {
                    throw IllegalStateException("All coins in this Lucky Box have been claimed!")
                }
                val slotsLeft = (maxClaimants - rawClaimed.size).coerceAtLeast(1)
                claimedAmount = if (slotsLeft == 1) {
                    remaining
                } else {
                    val avg = (remaining / slotsLeft).coerceAtLeast(1)
                    val maxSlice = (avg * 2).coerceAtMost(remaining - (slotsLeft - 1)).coerceAtLeast(1)
                    Random.nextInt(1, maxSlice + 1)
                }

                val updatedClaimed = rawClaimed.entries.associate { (k, v) ->
                    k.toString() to ((v as? Number)?.toInt() ?: 0)
                }.toMutableMap()
                updatedClaimed[userId] = claimedAmount

                tx.update(
                    boxRef,
                    mapOf(
                        "remainingCoins" to (remaining - claimedAmount).coerceAtLeast(0),
                        "claimedBy" to updatedClaimed
                    )
                )
                val userCoins = userDoc.getLong("coins") ?: 0L
                tx.set(userRef, mapOf("coins" to (userCoins + claimedAmount)), SetOptions.merge())
            }.await()
            Result.success(claimedAmount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== 5. COSMETICS: PROFILE THEMES, AVATAR FRAMES, VIP BADGES ====================

    fun observeUserCosmetics(userId: String): Flow<com.example.model.UserCosmeticsState> = callbackFlow {
        if (userId.isBlank()) {
            trySend(com.example.model.UserCosmeticsState())
            awaitClose {}
            return@callbackFlow
        }
        val reg = try {
            db.collection("users").document(userId).addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                try {
                    val owned = (snap.get("ownedCosmetics") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
                    val coinsVal = when (val c = snap.get("coins")) {
                        is Number -> c.toLong()
                        is String -> c.toLongOrNull() ?: c.toDoubleOrNull()?.toLong() ?: 0L
                        else -> 0L
                    }
                    trySend(
                        com.example.model.UserCosmeticsState(
                            coins = coinsVal,
                            ownedIds = owned,
                            equippedFrameId = (snap.get("equippedFrameId") as? String)?.ifBlank { "frame_none" } ?: "frame_none",
                            equippedThemeId = (snap.get("profileTheme") as? String)?.ifBlank { "theme_default" } ?: "theme_default",
                            equippedEntranceId = (snap.get("equippedEntranceId") as? String)?.ifBlank { "entrance_none" } ?: "entrance_none"
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e("ProCreatorSuiteRepo", "Safe cosmetics parse failed for $userId", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ProCreatorSuiteRepo", "Failed to attach cosmetics listener", e)
            null
        }
        awaitClose { reg?.remove() }
    }

    suspend fun purchaseOrEquipCosmetic(userId: String, item: CosmeticItem): Result<String> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext Result.failure(Exception("Sign in required"))
        val equipField = when (item.type) {
            "FRAME" -> "equippedFrameId"
            "THEME" -> "profileTheme"
            else -> "equippedEntranceId"
        }
        try {
            var actionMsg = "Equipped ${item.name}!"
            db.runTransaction { tx ->
                val ref = db.collection("users").document(userId)
                val doc = tx.get(ref)
                val owned = (doc.get("ownedCosmetics") as? List<*>)?.filterIsInstance<String>()?.toMutableSet() ?: mutableSetOf()
                val alreadyOwned = owned.contains(item.id)
                if (alreadyOwned) {
                    val currentEquipped = doc.getString(equipField) ?: ""
                    val nextValue = if (currentEquipped == item.id) {
                        actionMsg = "Unequipped ${item.name}"
                        when (item.type) {
                            "FRAME" -> "frame_none"
                            "THEME" -> "theme_default"
                            else -> "entrance_none"
                        }
                    } else {
                        actionMsg = "Equipped ${item.name}!"
                        item.id
                    }
                    tx.set(ref, mapOf(equipField to nextValue), SetOptions.merge())
                } else {
                    val coins = doc.getLong("coins") ?: 0L
                    if (coins < item.priceCoins) {
                        throw IllegalStateException("Need ${item.priceCoins} coins (you have $coins). Spin daily wheel to earn free coins!")
                    }
                    owned.add(item.id)
                    actionMsg = "Unlocked & Equipped ${item.name}!"
                    tx.set(
                        ref,
                        mapOf(
                            "coins" to (coins - item.priceCoins),
                            "ownedCosmetics" to owned.toList(),
                            equipField to item.id
                        ),
                        SetOptions.merge()
                    )
                }
            }.await()
            Result.success(actionMsg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== 7. BRAND COLLABS & PAID SHOUTOUT MARKETPLACE ====================

    private fun mapDocToRateCard(doc: com.google.firebase.firestore.DocumentSnapshot): CreatorRateCard? {
        val uname = doc.getString("username")?.takeIf { it.isNotBlank() }
            ?: doc.getString("displayName")?.takeIf { it.isNotBlank() }
            ?: doc.getString("name")?.takeIf { it.isNotBlank() }
            ?: return null
        val explicitEnabled = doc.getBoolean("collabCardEnabled")
        if (explicitEnabled == false) return null
        val followers = (doc.getLong("followerCount") ?: doc.getLong("followersCount") ?: 0L).toInt().coerceAtLeast(0)
        return CreatorRateCard(
            userId = doc.id,
            username = uname,
            displayName = doc.getString("displayName")?.ifBlank { null } ?: doc.getString("name")?.ifBlank { null } ?: uname,
            avatarUrl = doc.getString("avatar_url")?.ifBlank { null }
                ?: doc.getString("avatarUrl")?.ifBlank { null }
                ?: doc.getString("profilePicUrl")?.ifBlank { null }
                ?: "",
            isVerified = isDocVerified(doc),
            verificationBadgeUrl = doc.getString("verificationBadgeUrl") ?: "",
            followersCount = followers,
            enabled = explicitEnabled ?: true,
            niche = doc.getString("collabNiche")?.ifBlank { null }
                ?: doc.getString("category")?.ifBlank { null }
                ?: "Reels & Lifestyle",
            bioNote = doc.getString("collabBioNote")?.ifBlank { null }
                ?: doc.getString("bio")?.takeIf { it.isNotBlank() }?.take(100)
                ?: "Story shoutouts, Reel promos & paid brand collaborations",
            storyShoutoutCoins = (doc.getLong("collabStoryPrice") ?: 150L).toInt().coerceAtLeast(25),
            reelPromoCoins = (doc.getLong("collabReelPrice") ?: 400L).toInt().coerceAtLeast(50),
            collabPostCoins = (doc.getLong("collabPostPrice") ?: 250L).toInt().coerceAtLeast(50),
            turnaroundDays = (doc.getLong("collabTurnaroundDays") ?: 2L).toInt().coerceIn(1, 14),
            completedDeals = (doc.getLong("collabCompletedDeals") ?: 0L).toInt()
        )
    }

    fun observeCreatorRateCards(): Flow<List<CreatorRateCard>> = callbackFlow {
        val reg = db.collection("users").limit(80).addSnapshotListener { snap, err ->
            if (err != null || snap == null) {
                trySend(rateCardsCache?.second ?: emptyList())
                return@addSnapshotListener
            }
            val cards = snap.documents
                .mapNotNull { mapDocToRateCard(it) }
                .sortedByDescending { (if (it.isVerified) 10_000 else 0) + it.followersCount + it.completedDeals * 50 }
            rateCardsCache = System.currentTimeMillis() to cards
            trySend(cards)
        }
        awaitClose { reg.remove() }
    }

    suspend fun fetchCreatorRateCards(forceRefresh: Boolean = false): List<CreatorRateCard> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            rateCardsCache?.let { (ts, cached) ->
                if (System.currentTimeMillis() - ts < CACHE_TTL_MS && cached.isNotEmpty()) {
                    return@withContext cached
                }
            }
        }
        try {
            val snap = db.collection("users").limit(80).get().await()
            val cards = snap.documents
                .mapNotNull { mapDocToRateCard(it) }
                .sortedByDescending { (if (it.isVerified) 10_000 else 0) + it.followersCount + it.completedDeals * 50 }
            rateCardsCache = System.currentTimeMillis() to cards
            cards
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ProCreatorSuiteRepo", "fetchCreatorRateCards failed", e)
            rateCardsCache?.second ?: emptyList()
        }
    }

    suspend fun sendDirectCollabInquiry(
        brandUserId: String,
        brandUsername: String,
        brandAvatar: String,
        creator: CreatorRateCard,
        packageType: String,
        brief: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (brandUserId.isBlank()) return@withContext Result.failure(Exception("Sign in required"))
        if (brandUserId == creator.userId) return@withContext Result.failure(Exception("You cannot send an inquiry to yourself"))
        if (brief.isBlank()) return@withContext Result.failure(Exception("Please write your campaign brief"))
        try {
            val bookingId = "inquiry_${UUID.randomUUID().toString().take(12)}"
            val now = System.currentTimeMillis()
            db.collection("brand_collabs").document(bookingId).set(
                mapOf(
                    "creatorId" to creator.userId,
                    "creatorUsername" to creator.username,
                    "creatorAvatar" to creator.avatarUrl,
                    "brandUserId" to brandUserId,
                    "brandUsername" to brandUsername,
                    "brandAvatar" to brandAvatar,
                    "packageType" to packageType,
                    "brief" to brief.trim(),
                    "amountCoins" to 0,
                    "status" to "INQUIRY_SENT",
                    "deliverableNote" to "",
                    "createdAt" to now
                )
            ).await()
            try {
                db.collection("users").document(creator.userId).collection("notifications").add(
                    mapOf(
                        "type" to "BRAND_COLLAB",
                        "actorId" to brandUserId,
                        "actorUsername" to brandUsername,
                        "actorAvatar" to brandAvatar,
                        "message" to "sent you a Brand Collab inquiry for $packageType!",
                        "timestamp" to FieldValue.serverTimestamp(),
                        "isRead" to false
                    )
                )
            } catch (_: Exception) {}
            Result.success(bookingId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveMyRateCard(
        userId: String,
        enabled: Boolean,
        niche: String,
        bioNote: String,
        storyCoins: Int,
        reelCoins: Int,
        postCoins: Int,
        turnaroundDays: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext Result.failure(Exception("Sign in required"))
        try {
            db.collection("users").document(userId).set(
                mapOf(
                    "collabCardEnabled" to enabled,
                    "collabNiche" to niche.trim().ifBlank { "Creator & Reels" },
                    "collabBioNote" to bioNote.trim(),
                    "collabStoryPrice" to storyCoins.coerceIn(25, 100_000),
                    "collabReelPrice" to reelCoins.coerceIn(50, 100_000),
                    "collabPostPrice" to postCoins.coerceIn(50, 100_000),
                    "collabTurnaroundDays" to turnaroundDays.coerceIn(1, 14)
                ),
                SetOptions.merge()
            ).await()
            rateCardsCache = null
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createEscrowCollabBooking(
        brandUserId: String,
        brandUsername: String,
        brandAvatar: String,
        creator: CreatorRateCard,
        packageType: String,
        brief: String,
        amountCoins: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        if (brandUserId.isBlank()) return@withContext Result.failure(Exception("Sign in required"))
        if (brandUserId == creator.userId) return@withContext Result.failure(Exception("You cannot book your own rate card"))
        if (brief.isBlank()) return@withContext Result.failure(Exception("Please add campaign details"))
        try {
            val bookingId = "collab_${UUID.randomUUID().toString().take(12)}"
            val now = System.currentTimeMillis()
            db.runTransaction { tx ->
                val buyerRef = db.collection("users").document(brandUserId)
                val buyerDoc = tx.get(buyerRef)
                val coins = buyerDoc.getLong("coins") ?: 0L
                val wallet = buyerDoc.getDouble("walletBalance") ?: 0.0
                val totalAvail = coins + wallet.toLong()
                if (totalAvail < amountCoins) {
                    throw IllegalStateException("Need $amountCoins coins in escrow (available: $totalAvail)")
                }
                if (coins >= amountCoins) {
                    tx.set(buyerRef, mapOf("coins" to (coins - amountCoins)), SetOptions.merge())
                } else {
                    val shortfall = amountCoins - coins
                    tx.set(
                        buyerRef,
                        mapOf("coins" to 0L, "walletBalance" to (wallet - shortfall).coerceAtLeast(0.0)),
                        SetOptions.merge()
                    )
                }
                tx.set(
                    db.collection("brand_collabs").document(bookingId),
                    mapOf(
                        "creatorId" to creator.userId,
                        "creatorUsername" to creator.username,
                        "creatorAvatar" to creator.avatarUrl,
                        "brandUserId" to brandUserId,
                        "brandUsername" to brandUsername,
                        "brandAvatar" to brandAvatar,
                        "packageType" to packageType,
                        "brief" to brief.trim(),
                        "amountCoins" to amountCoins,
                        "status" to "ESCROW_HELD",
                        "deliverableNote" to "",
                        "createdAt" to now
                    )
                )
            }.await()

            try {
                db.collection("users").document(creator.userId).collection("notifications").add(
                    mapOf(
                        "type" to "BRAND_COLLAB",
                        "actorId" to brandUserId,
                        "actorUsername" to brandUsername,
                        "actorAvatar" to brandAvatar,
                        "message" to "booked a $packageType ($amountCoins coins in escrow)!",
                        "timestamp" to FieldValue.serverTimestamp(),
                        "isRead" to false
                    )
                )
            } catch (_: Exception) {}

            Result.success(bookingId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeMyCollabBookings(userId: String): Flow<List<CollabBooking>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val reg = db.collection("brand_collabs")
            .limit(60)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap.documents.mapNotNull { doc ->
                    val cId = doc.getString("creatorId") ?: ""
                    val bId = doc.getString("brandUserId") ?: ""
                    if (cId != userId && bId != userId) return@mapNotNull null
                    CollabBooking(
                        id = doc.id,
                        creatorId = cId,
                        creatorUsername = doc.getString("creatorUsername") ?: "Creator",
                        creatorAvatar = doc.getString("creatorAvatar") ?: "",
                        brandUserId = bId,
                        brandUsername = doc.getString("brandUsername") ?: "Brand",
                        brandAvatar = doc.getString("brandAvatar") ?: "",
                        packageType = doc.getString("packageType") ?: "STORY_SHOUTOUT",
                        brief = doc.getString("brief") ?: "",
                        amountCoins = (doc.getLong("amountCoins") ?: 0L).toInt(),
                        status = doc.getString("status") ?: "ESCROW_HELD",
                        deliverableNote = doc.getString("deliverableNote") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0L
                    )
                }.sortedByDescending { it.createdAt }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun updateCollabBookingStatus(
        bookingId: String,
        newStatus: String,
        deliverableNote: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            db.runTransaction { tx ->
                val bookingRef = db.collection("brand_collabs").document(bookingId)
                val bDoc = tx.get(bookingRef)
                if (!bDoc.exists()) throw IllegalStateException("Booking not found")
                val currentStatus = bDoc.getString("status") ?: ""
                val creatorId = bDoc.getString("creatorId") ?: ""
                val brandUserId = bDoc.getString("brandUserId") ?: ""
                val amount = (bDoc.getLong("amountCoins") ?: 0L).toInt()

                if (currentStatus == "COMPLETED" || currentStatus == "DECLINED") {
                    throw IllegalStateException("Booking is already finalized")
                }

                when (newStatus) {
                    "COMPLETED" -> {
                        val creatorRef = db.collection("users").document(creatorId)
                        val cDoc = tx.get(creatorRef)
                        val netCoins = if (amount > 0) (amount * 0.90).toLong().coerceAtLeast(1L) else 0L
                        val netRupees = if (amount > 0) amount * 0.90 else 0.0
                        val cCoins = cDoc.getLong("coins") ?: 0L
                        val cEarn = cDoc.getDouble("earnings") ?: 0.0
                        val cWallet = cDoc.getDouble("walletBalance") ?: 0.0
                        val deals = (cDoc.getLong("collabCompletedDeals") ?: 0L) + 1L
                        tx.set(
                            creatorRef,
                            mapOf(
                                "coins" to (cCoins + netCoins),
                                "earnings" to (cEarn + netRupees),
                                "walletBalance" to (cWallet + netRupees),
                                "collabCompletedDeals" to deals
                            ),
                            SetOptions.merge()
                        )
                        if (amount > 0) {
                            val ledgerId = "collab_pay_${UUID.randomUUID().toString().take(10)}"
                            tx.set(
                                db.collection("creator_earnings_ledger").document(ledgerId),
                                mapOf(
                                    "id" to ledgerId,
                                    "creatorUid" to creatorId,
                                    "payerUid" to brandUserId,
                                    "payerUsername" to (bDoc.getString("brandUsername") ?: "Brand"),
                                    "sourceType" to "BRAND_COLLAB",
                                    "contentId" to bookingId,
                                    "description" to "Paid Shoutout/Collab (${bDoc.getString("packageType")})",
                                    "grossAmount" to amount.toDouble(),
                                    "platformFee" to (amount * 0.10),
                                    "netCreatorAmount" to netRupees,
                                    "status" to "CREDITED",
                                    "timestamp" to System.currentTimeMillis()
                                )
                            )
                        }
                        tx.update(bookingRef, "status", "COMPLETED")
                    }
                    "DECLINED" -> {
                        val buyerRef = db.collection("users").document(brandUserId)
                        val buyerDoc = tx.get(buyerRef)
                        val buyerCoins = buyerDoc.getLong("coins") ?: 0L
                        tx.set(buyerRef, mapOf("coins" to (buyerCoins + amount)), SetOptions.merge())
                        tx.update(bookingRef, "status", "DECLINED")
                    }
                    "DELIVERED" -> {
                        tx.update(
                            bookingRef,
                            mapOf(
                                "status" to "DELIVERED",
                                "deliverableNote" to deliverableNote.ifBlank { "Campaign published & live!" }
                            )
                        )
                    }
                    else -> {
                        tx.update(bookingRef, "status", newStatus)
                    }
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== 4. INTERACTIVE WATCH PARTY ====================

    suspend fun createOrJoinWatchParty(
        roomId: String?,
        hostId: String,
        hostUsername: String,
        chatPartnerId: String = "",
        initialVideoId: String = "",
        initialVideoUrl: String = "",
        initialVideoTitle: String = "Watch Party Reel",
        initialThumb: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        if (hostId.isBlank()) return@withContext Result.failure(Exception("Sign in required"))
        try {
            val resolvedRoomId = roomId?.takeIf { it.isNotBlank() } ?: "wp_${UUID.randomUUID().toString().take(8)}"
            val roomRef = db.collection("watch_parties").document(resolvedRoomId)
            val existing = roomRef.get().await()
            if (existing.exists()) {
                roomRef.set(mapOf("participants" to FieldValue.arrayUnion(hostId)), SetOptions.merge()).await()
            } else {
                val roomCode = resolvedRoomId.takeLast(6).uppercase(Locale.US)
                roomRef.set(
                    mapOf(
                        "roomCode" to roomCode,
                        "hostId" to hostId,
                        "hostUsername" to hostUsername.ifBlank { "Host" },
                        "chatPartnerId" to chatPartnerId,
                        "videoId" to initialVideoId,
                        "videoUrl" to initialVideoUrl,
                        "videoTitle" to initialVideoTitle,
                        "videoThumb" to initialThumb,
                        "isPlaying" to true,
                        "positionMs" to 0L,
                        "updatedAt" to System.currentTimeMillis(),
                        "participants" to listOfNotNull(hostId, chatPartnerId.takeIf { it.isNotBlank() }).distinct(),
                        "lastEmoji" to "",
                        "lastEmojiSender" to "",
                        "lastEmojiTs" to 0L
                    )
                ).await()
            }
            Result.success(resolvedRoomId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRecentVideoReelsForWatchParty(): List<Video> = withContext(Dispatchers.IO) {
        fun mapVideoDoc(doc: com.google.firebase.firestore.DocumentSnapshot): Video? {
            val rawUrl = doc.getString("postUrl")?.takeIf { it.isNotBlank() }
                ?: doc.getString("videoUrl")?.takeIf { it.isNotBlank() }
                ?: doc.getString("video_url")?.takeIf { it.isNotBlank() }
                ?: doc.getString("mediaUrl")?.takeIf { it.isNotBlank() }
                ?: return null
            if (com.example.utils.MediaResolver.isDummyOrSampleUrl(rawUrl)) return null
            val mediaType = doc.getString("mediaType") ?: ""
            val isImageUrl = com.example.utils.MediaResolver.isImage(rawUrl)
            val isVideoDoc = !isImageUrl && (
                mediaType.equals("video", ignoreCase = true) ||
                    mediaType.equals("reel", ignoreCase = true) ||
                    mediaType.isBlank()
            )
            if (!isVideoDoc) return null
            val uid = doc.getString("userId") ?: doc.getString("user_id") ?: ""
            val uname = doc.getString("username")?.ifBlank { "Creator" } ?: "Creator"
            val avatar = doc.getString("userAvatarUrl")?.ifBlank { null }
                ?: doc.getString("avatar_url")?.ifBlank { null }
                ?: doc.getString("profilePicUrl")?.ifBlank { null }
                ?: ""
            val caption = doc.getString("caption")?.ifBlank { null } ?: doc.getString("description") ?: ""
            val thumb = doc.getString("thumbnailUrl")?.ifBlank { null }
                ?: doc.getString("postThumbnail")?.ifBlank { null }
                ?: ""
            val ts = doc.getLong("createdAtMillis")
                ?: doc.getTimestamp("timestamp")?.toDate()?.time
                ?: 0L
            return Video(
                id = doc.id,
                userId = uid,
                user = User(
                    id = uid,
                    username = uname,
                    avatarUrl = avatar,
                    isVerified = doc.getBoolean("isUserVerified") ?: false
                ),
                videoUrl = rawUrl,
                thumbnailUrl = thumb,
                description = caption,
                caption = caption,
                mediaType = "video",
                timestamp = ts
            )
        }

        try {
            val results = mutableListOf<Video>()
            val postsSnap = db.collection("posts").limit(50).get().await()
            results.addAll(postsSnap.documents.mapNotNull { mapVideoDoc(it) })
            if (results.size < 5) {
                try {
                    val videosSnap = db.collection("videos").limit(30).get().await()
                    results.addAll(videosSnap.documents.mapNotNull { mapVideoDoc(it) })
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
            results.distinctBy { it.id }.sortedByDescending { it.timestamp }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ProCreatorSuiteRepo", "fetchRecentVideoReelsForWatchParty failed", e)
            emptyList()
        }
    }

    fun observeWatchParty(roomId: String): Flow<WatchPartySession?> = callbackFlow {
        if (roomId.isBlank()) {
            trySend(null)
            awaitClose {}
            return@callbackFlow
        }
        val reg = db.collection("watch_parties").document(roomId).addSnapshotListener { doc, err ->
            if (err != null || doc == null || !doc.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            trySend(
                WatchPartySession(
                    id = doc.id,
                    roomCode = doc.getString("roomCode") ?: doc.id.takeLast(6).uppercase(Locale.US),
                    hostId = doc.getString("hostId") ?: "",
                    hostUsername = doc.getString("hostUsername") ?: "Host",
                    chatPartnerId = doc.getString("chatPartnerId") ?: "",
                    videoId = doc.getString("videoId") ?: "",
                    videoUrl = doc.getString("videoUrl") ?: "",
                    videoTitle = doc.getString("videoTitle") ?: "Watch Party Reel",
                    videoThumb = doc.getString("videoThumb") ?: "",
                    isPlaying = doc.getBoolean("isPlaying") ?: true,
                    positionMs = doc.getLong("positionMs") ?: 0L,
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                    participants = (doc.get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    lastEmoji = doc.getString("lastEmoji") ?: "",
                    lastEmojiSender = doc.getString("lastEmojiSender") ?: "",
                    lastEmojiTs = doc.getLong("lastEmojiTs") ?: 0L
                )
            )
        }
        awaitClose { reg.remove() }
    }

    fun observeWatchPartyMessages(roomId: String): Flow<List<WatchPartyChatMessage>> = callbackFlow {
        if (roomId.isBlank()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val reg = db.collection("watch_parties").document(roomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(60)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val msgs = snap.documents.map { d ->
                    WatchPartyChatMessage(
                        id = d.id,
                        senderId = d.getString("senderId") ?: "",
                        senderUsername = d.getString("senderUsername") ?: "Viewer",
                        senderAvatar = d.getString("senderAvatar") ?: "",
                        text = d.getString("text") ?: "",
                        timestamp = d.getLong("timestamp") ?: 0L
                    )
                }
                trySend(msgs)
            }
        awaitClose { reg.remove() }
    }

    suspend fun syncWatchPartyPlayback(
        roomId: String,
        isPlaying: Boolean,
        positionMs: Long,
        videoId: String? = null,
        videoUrl: String? = null,
        videoTitle: String? = null,
        videoThumb: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val updates = mutableMapOf<String, Any>(
                "isPlaying" to isPlaying,
                "positionMs" to positionMs.coerceAtLeast(0L),
                "updatedAt" to System.currentTimeMillis()
            )
            if (videoId != null) updates["videoId"] = videoId
            if (videoUrl != null) updates["videoUrl"] = videoUrl
            if (videoTitle != null) updates["videoTitle"] = videoTitle
            if (videoThumb != null) updates["videoThumb"] = videoThumb
            db.collection("watch_parties").document(roomId).set(updates, SetOptions.merge()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    suspend fun sendWatchPartyEmoji(roomId: String, emoji: String, senderUsername: String) = withContext(Dispatchers.IO) {
        try {
            db.collection("watch_parties").document(roomId).set(
                mapOf(
                    "lastEmoji" to emoji,
                    "lastEmojiSender" to senderUsername,
                    "lastEmojiTs" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    suspend fun sendWatchPartyMessage(
        roomId: String,
        senderId: String,
        senderUsername: String,
        senderAvatar: String,
        text: String
    ) = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext
        try {
            db.collection("watch_parties").document(roomId).collection("messages").add(
                mapOf(
                    "senderId" to senderId,
                    "senderUsername" to senderUsername,
                    "senderAvatar" to senderAvatar,
                    "text" to text.trim(),
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    // ==================== 8. CHAT UPGRADES: THEMES, VANISH MODE, VOICE FILTERS ====================

    fun chatPairKey(uid1: String, uid2: String): String =
        if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"

    fun observeChatUpgradePrefs(uid1: String, uid2: String): Flow<ChatUpgradePrefs> = callbackFlow {
        if (uid1.isBlank() || uid2.isBlank()) {
            trySend(ChatUpgradePrefs())
            awaitClose {}
            return@callbackFlow
        }
        val key = chatPairKey(uid1, uid2)
        val reg = db.collection("chat_preferences").document(key).addSnapshotListener { doc, err ->
            if (err != null || doc == null || !doc.exists()) {
                trySend(ChatUpgradePrefs())
                return@addSnapshotListener
            }
            trySend(
                ChatUpgradePrefs(
                    themeId = doc.getString("themeId") ?: "default",
                    vanishModeEnabled = doc.getBoolean("vanishModeEnabled") ?: false,
                    voiceFilterId = doc.getString("voiceFilterId") ?: "original"
                )
            )
        }
        awaitClose { reg.remove() }
    }

    suspend fun updateChatUpgradePrefs(
        uid1: String,
        uid2: String,
        themeId: String? = null,
        vanishModeEnabled: Boolean? = null,
        voiceFilterId: String? = null
    ) = withContext(Dispatchers.IO) {
        if (uid1.isBlank() || uid2.isBlank()) return@withContext
        try {
            val key = chatPairKey(uid1, uid2)
            val data = mutableMapOf<String, Any>("updatedAt" to System.currentTimeMillis())
            if (themeId != null) data["themeId"] = themeId
            if (vanishModeEnabled != null) data["vanishModeEnabled"] = vanishModeEnabled
            if (voiceFilterId != null) data["voiceFilterId"] = voiceFilterId
            db.collection("chat_preferences").document(key).set(data, SetOptions.merge()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    suspend fun purgeVanishMessagesForChat(uid1: String, uid2: String) = withContext(Dispatchers.IO) {
        if (uid1.isBlank() || uid2.isBlank()) return@withContext
        try {
            val key = chatPairKey(uid1, uid2)
            val snap = db.collection("chats").document(key).collection("messages")
                .limit(100)
                .get()
                .await()
            val batch = db.batch()
            var count = 0
            for (doc in snap.documents) {
                val text = doc.getString("text") ?: ""
                if (text.startsWith("[VANISH]")) {
                    batch.delete(doc.reference)
                    count++
                }
            }
            if (count > 0) batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }
}
