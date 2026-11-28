package com.example.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.model.StickerAdminStats
import com.example.model.StickerItem
import com.example.model.StickerPack
import com.example.model.StickerReport
import com.example.model.RepositoryManager
import com.example.utils.GoogleDriveManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory and disk caching layer for Social India stickers and packs.
 */
object SocialIndiaStickerCache {
    val packCache = ConcurrentHashMap<String, StickerPack>()
    val stickerListCache = ConcurrentHashMap<String, List<StickerItem>>()
    val recentStickersCache = ConcurrentHashMap<String, List<StickerItem>>()
    val favoriteStickersCache = ConcurrentHashMap<String, Set<String>>()

    fun clear() {
        packCache.clear()
        stickerListCache.clear()
    }

    fun invalidatePack(packId: String) {
        packCache.remove(packId)
        stickerListCache.remove(packId)
    }
}

class StickerRepository(private val firestore: FirebaseFirestore) {
    private val TAG = "StickerRepository"

    /**
     * Observes all published sticker packs for users in real-time.
     */
    fun observePublishedPacks(): Flow<List<StickerPack>> = callbackFlow {
        val listener = firestore.collection("stickerPacks")
            .whereEqualTo("status", "PUBLISHED")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error observing published sticker packs", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try { doc.toObject(StickerPack::class.java)?.copy(packId = doc.id) } catch (_: Exception) { null }
                }?.sortedBy { it.sortOrder } ?: emptyList()

                list.forEach { SocialIndiaStickerCache.packCache[it.packId] = it }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Observes all sticker packs for Admin (including Draft & Archived).
     */
    fun observeAllPacksForAdmin(): Flow<List<StickerPack>> = callbackFlow {
        val listener = firestore.collection("stickerPacks")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error observing all sticker packs for admin", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try { doc.toObject(StickerPack::class.java)?.copy(packId = doc.id) } catch (_: Exception) { null }
                }?.sortedByDescending { it.updatedAt } ?: emptyList()

                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Observes stickers inside a specific pack.
     */
    fun observePackStickers(packId: String): Flow<List<StickerItem>> = callbackFlow {
        val listener = firestore.collection("stickers")
            .whereEqualTo("packId", packId)
            .whereEqualTo("status", "PUBLISHED")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error observing pack stickers: $packId", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try { doc.toObject(StickerItem::class.java)?.copy(stickerId = doc.id) } catch (_: Exception) { null }
                }?.sortedBy { it.sortOrder } ?: emptyList()

                SocialIndiaStickerCache.stickerListCache[packId] = list
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Observes all stickers in a pack for Admin (including drafts).
     */
    fun observeAllPackStickersForAdmin(packId: String): Flow<List<StickerItem>> = callbackFlow {
        val listener = firestore.collection("stickers")
            .whereEqualTo("packId", packId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error observing admin pack stickers: $packId", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try { doc.toObject(StickerItem::class.java)?.copy(stickerId = doc.id) } catch (_: Exception) { null }
                }?.sortedBy { it.sortOrder } ?: emptyList()

                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Search published stickers by name, pack, or tag.
     */
    suspend fun searchStickers(query: String): List<StickerItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = query.trim().lowercase()
        try {
            val snapshot = firestore.collection("stickers")
                .whereEqualTo("status", "PUBLISHED")
                .limit(100)
                .get()
                .await()

            return@withContext snapshot.documents.mapNotNull { doc ->
                try { doc.toObject(StickerItem::class.java)?.copy(stickerId = doc.id) } catch (_: Exception) { null }
            }.filter { sticker ->
                sticker.name.lowercase().contains(q) ||
                sticker.description.lowercase().contains(q) ||
                sticker.tags.any { it.lowercase().contains(q) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search stickers", e)
            return@withContext emptyList()
        }
    }

    /**
     * Observes recent stickers for the active user account.
     */
    fun getRecentStickers(userId: String): Flow<List<StickerItem>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users")
            .document(userId)
            .collection("recentStickers")
            .orderBy("lastUsedAt", Query.Direction.DESCENDING)
            .limit(24)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try { doc.toObject(StickerItem::class.java)?.copy(stickerId = doc.id) } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Records sticker usage for recent history and global analytics.
     */
    fun recordStickerUsage(userId: String, sticker: StickerItem) {
        if (userId.isEmpty() || sticker.stickerId.isEmpty()) return
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                // Save in user recent stickers
                val recentData = hashMapOf(
                    "stickerId" to sticker.stickerId,
                    "packId" to sticker.packId,
                    "name" to sticker.name,
                    "type" to sticker.type,
                    "driveFileId" to sticker.driveFileId,
                    "mediaUrl" to sticker.mediaUrl,
                    "thumbnailUrl" to sticker.thumbnailUrl,
                    "mimeType" to sticker.mimeType,
                    "isAnimated" to sticker.isAnimated,
                    "lastUsedAt" to System.currentTimeMillis()
                )
                firestore.collection("users")
                    .document(userId)
                    .collection("recentStickers")
                    .document(sticker.stickerId)
                    .set(recentData, SetOptions.merge())

                // Increment usage count in global sticker metadata
                firestore.collection("stickers")
                    .document(sticker.stickerId)
                    .update("usageCount", FieldValue.increment(1))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record sticker usage", e)
            }
        }
    }

    /**
     * Observes favorite stickers for the active user account.
     */
    fun getFavoriteStickers(userId: String): Flow<List<StickerItem>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users")
            .document(userId)
            .collection("favoriteStickers")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try { doc.toObject(StickerItem::class.java)?.copy(stickerId = doc.id) } catch (_: Exception) { null }
                } ?: emptyList()
                SocialIndiaStickerCache.favoriteStickersCache[userId] = list.map { it.stickerId }.toSet()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Toggles a sticker as favorite for a user.
     */
    suspend fun toggleFavorite(userId: String, sticker: StickerItem): Boolean = withContext(Dispatchers.IO) {
        if (userId.isEmpty() || sticker.stickerId.isEmpty()) return@withContext false
        try {
            val favDoc = firestore.collection("users")
                .document(userId)
                .collection("favoriteStickers")
                .document(sticker.stickerId)

            val existing = favDoc.get().await()
            if (existing.exists()) {
                favDoc.delete().await()
                return@withContext false
            } else {
                val data = hashMapOf(
                    "stickerId" to sticker.stickerId,
                    "packId" to sticker.packId,
                    "name" to sticker.name,
                    "type" to sticker.type,
                    "driveFileId" to sticker.driveFileId,
                    "mediaUrl" to sticker.mediaUrl,
                    "thumbnailUrl" to sticker.thumbnailUrl,
                    "mimeType" to sticker.mimeType,
                    "isAnimated" to sticker.isAnimated,
                    "createdAt" to System.currentTimeMillis()
                )
                favDoc.set(data).await()
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling favorite sticker", e)
            return@withContext false
        }
    }

    // --- ADMIN METHODS ---

    /**
     * Creates a new sticker pack with pack icon uploaded to Google Drive.
     */
    suspend fun createPack(
        name: String,
        description: String,
        category: String,
        language: String,
        iconUri: Uri?,
        context: Context
    ): StickerPack? = withContext(Dispatchers.IO) {
        val packId = "pack_${UUID.randomUUID().toString().take(10)}"
        var driveFileId = ""
        var iconUrl = ""

        if (iconUri != null) {
            try {
                val mime = context.contentResolver.getType(iconUri) ?: "image/png"
                val driveResult = try {
                    GoogleDriveManager.uploadStickerMedia(
                        context = context,
                        uri = iconUri,
                        packId = packId,
                        suggestedName = "icon_${System.currentTimeMillis()}",
                        mimeType = mime
                    )
                } catch (_: Exception) { null }
                if (driveResult != null) {
                    driveFileId = driveResult.fileId
                    iconUrl = driveResult.mediaUrl
                } else {
                    val cloudUrl = RepositoryManager.mediaRepository.uploadMedia(iconUri)
                    if (!cloudUrl.isNullOrEmpty()) {
                        iconUrl = cloudUrl
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload pack icon", e)
            }
        }

        val pack = StickerPack(
            packId = packId,
            name = name.trim(),
            description = description.trim(),
            iconDriveFileId = driveFileId,
            iconUrl = iconUrl,
            category = category,
            language = language,
            status = "PUBLISHED",
            sortOrder = 0,
            stickersCount = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        try {
            firestore.collection("stickerPacks").document(packId).set(pack).await()
            SocialIndiaStickerCache.invalidatePack(packId)
            return@withContext pack
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create sticker pack in Firestore", e)
            return@withContext null
        }
    }

    /**
     * Uploads sticker file directly to Google Drive or Cloud CDN and creates Firestore record.
     */
    suspend fun uploadSticker(
        packId: String,
        name: String,
        tags: List<String>,
        mediaUri: Uri,
        context: Context
    ): StickerItem? = withContext(Dispatchers.IO) {
        val stickerId = "stk_${UUID.randomUUID().toString().take(12)}"
        val mime = context.contentResolver.getType(mediaUri) ?: "image/png"
        val isGif = mime.contains("gif", ignoreCase = true) || mediaUri.toString().contains(".gif", ignoreCase = true)
        val isWebp = mime.contains("webp", ignoreCase = true) || mediaUri.toString().contains(".webp", ignoreCase = true)
        val isAnimated = isGif || (isWebp && name.contains("anim", ignoreCase = true))

        val (finalMediaUrl, finalFileId) = try {
            val driveResult = GoogleDriveManager.uploadStickerMedia(
                context = context,
                uri = mediaUri,
                packId = packId,
                suggestedName = "sticker_${System.currentTimeMillis()}",
                mimeType = mime
            )
            if (driveResult != null) {
                Pair(driveResult.mediaUrl, driveResult.fileId)
            } else {
                val cloudUrl = RepositoryManager.mediaRepository.uploadMedia(mediaUri) ?: return@withContext null
                Pair(cloudUrl, "")
            }
        } catch (_: Exception) {
            val cloudUrl = RepositoryManager.mediaRepository.uploadMedia(mediaUri) ?: return@withContext null
            Pair(cloudUrl, "")
        }

        val sticker = StickerItem(
            stickerId = stickerId,
            packId = packId,
            name = name.ifEmpty { "Sticker" },
            description = "Social India Sticker",
            type = if (isAnimated) "ANIMATED" else "STATIC",
            driveFileId = finalFileId,
            mediaUrl = finalMediaUrl,
            thumbnailDriveFileId = finalFileId,
            thumbnailUrl = finalMediaUrl,
            mimeType = mime,
            width = 512,
            height = 512,
            tags = tags,
            status = "PUBLISHED",
            usageCount = 0L,
            isAnimated = isAnimated,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        try {
            firestore.collection("stickers").document(stickerId).set(sticker).await()
            firestore.collection("stickerPacks").document(packId).update(
                mapOf(
                    "stickersCount" to FieldValue.increment(1),
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            SocialIndiaStickerCache.invalidatePack(packId)
            return@withContext sticker
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write sticker metadata to Firestore", e)
            return@withContext null
        }
    }

    /**
     * Publishes a draft sticker pack.
     */
    suspend fun publishPack(packId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("stickerPacks").document(packId).update(
                mapOf("status" to "PUBLISHED", "updatedAt" to System.currentTimeMillis())
            ).await()
            SocialIndiaStickerCache.invalidatePack(packId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Archives a sticker pack.
     */
    suspend fun archivePack(packId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("stickerPacks").document(packId).update(
                mapOf("status" to "ARCHIVED", "updatedAt" to System.currentTimeMillis())
            ).await()
            SocialIndiaStickerCache.invalidatePack(packId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Deletes a sticker metadata from Firestore.
     */
    suspend fun deleteSticker(stickerId: String, packId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            firestore.collection("stickers").document(stickerId).delete().await()
            firestore.collection("stickerPacks").document(packId).update(
                mapOf(
                    "stickersCount" to FieldValue.increment(-1),
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            SocialIndiaStickerCache.invalidatePack(packId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reports a sticker.
     */
    suspend fun reportSticker(stickerId: String, reporterUid: String, reason: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val reportId = "rep_${UUID.randomUUID().toString().take(10)}"
            val report = StickerReport(
                reportId = reportId,
                stickerId = stickerId,
                reporterUid = reporterUid,
                reason = reason,
                status = "PENDING",
                createdAt = System.currentTimeMillis()
            )
            firestore.collection("stickerReports").document(reportId).set(report).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetches real aggregate statistics for the Admin Sticker dashboard.
     */
    suspend fun getAdminStats(): StickerAdminStats = withContext(Dispatchers.IO) {
        try {
            val packsSnap = firestore.collection("stickerPacks").get().await()
            val totalPacks = packsSnap.size()
            var publishedPacks = 0
            var draftPacks = 0
            packsSnap.documents.forEach { doc ->
                val status = doc.getString("status") ?: "PUBLISHED"
                if (status == "PUBLISHED") publishedPacks++ else if (status == "DRAFT") draftPacks++
            }

            val stickersSnap = firestore.collection("stickers").get().await()
            var animatedCount = 0
            var staticCount = 0
            var totalUsage = 0L

            stickersSnap.documents.forEach { doc ->
                val isAnim = doc.getBoolean("isAnimated") ?: false
                if (isAnim) animatedCount++ else staticCount++
                totalUsage += (doc.getLong("usageCount") ?: 0L)
            }

            return@withContext StickerAdminStats(
                totalPacks = totalPacks,
                publishedPacks = publishedPacks,
                draftPacks = draftPacks,
                animatedStickers = animatedCount,
                staticStickers = staticCount,
                totalUsageCount = totalUsage
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching sticker admin stats", e)
            return@withContext StickerAdminStats()
        }
    }

    /**
     * Seeds initial high-quality sticker packs (Indian, Reactions, Celebration, Love)
     * if the database is currently empty.
     */
    suspend fun seedInitialStickerPacksIfEmpty() = withContext(Dispatchers.IO) {
        try {
            val existing = firestore.collection("stickerPacks").limit(1).get().await()
            if (!existing.isEmpty) return@withContext

            Log.d(TAG, "Seeding initial Social India sticker packs...")

            // 1. Indian & Festival Pack
            val pack1Id = "pack_indian_vibes"
            val pack1 = StickerPack(
                packId = pack1Id,
                name = "Desi Swag & Festivals",
                description = "Authentic Indian expressions, greetings and festival celebrations",
                iconUrl = "https://images.unsplash.com/photo-1596461404969-9ae70f2830c1?w=150",
                category = "Indian",
                language = "Hindi/English",
                status = "PUBLISHED",
                sortOrder = 1,
                stickersCount = 6
            )
            firestore.collection("stickerPacks").document(pack1Id).set(pack1).await()

            val pack1Stickers = listOf(
                StickerItem("stk_namaste", pack1Id, "Namaste 🙏", "Traditional Indian greeting", "STATIC", "", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=300", "", "", "image/png", 512, 512, 0, 0, 1, listOf("namaste", "greeting", "indian", "respect"), 1, "PUBLISHED", 120, false),
                StickerItem("stk_bhangra", pack1Id, "Bhangra Energy", "Punjabi dance celebrations", "ANIMATED", "", "https://media.giphy.com/media/l2YWvI3Zl4uCqB46c/giphy.gif", "", "", "image/gif", 512, 512, 0, 0, 20, listOf("bhangra", "dance", "punjabi", "celebration"), 2, "PUBLISHED", 89, true),
                StickerItem("stk_diwali", pack1Id, "Shubh Diwali 🪔", "Diwali diya of lights", "STATIC", "", "https://images.unsplash.com/photo-1605379399642-870262d3d051?w=300", "", "", "image/png", 512, 512, 0, 0, 1, listOf("diwali", "festival", "light", "shubh"), 3, "PUBLISHED", 145, false),
                StickerItem("stk_chai", pack1Id, "Chai Peelo ☕", "Warm cup of Indian cutting chai", "STATIC", "", "https://images.unsplash.com/photo-1576092768241-dec231879fc3?w=300", "", "", "image/png", 512, 512, 0, 0, 1, listOf("chai", "tea", "morning", "desi"), 4, "PUBLISHED", 210, false),
                StickerItem("stk_jhakaas", pack1Id, "Ekdum Jhakaas! ⭐", "Classic Bollywood style appreciation", "STATIC", "", "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=300", "", "", "image/png", 512, 512, 0, 0, 1, listOf("jhakaas", "bollywood", "superb", "great"), 5, "PUBLISHED", 95, false),
                StickerItem("stk_dhol", pack1Id, "Dhol Beats 🥁", "Festive rhythmic beats", "ANIMATED", "", "https://media.giphy.com/media/DhstvI455Y0sE/giphy.gif", "", "", "image/gif", 512, 512, 0, 0, 24, listOf("dhol", "music", "party", "bhangra"), 6, "PUBLISHED", 78, true)
            )
            pack1Stickers.forEach { firestore.collection("stickers").document(it.stickerId).set(it).await() }

            // 2. Love & Emotions Pack
            val pack2Id = "pack_love_hearts"
            val pack2 = StickerPack(
                packId = pack2Id,
                name = "Love & Affection ❤️",
                description = "Hearts, cuddles, and warm expressive stickers",
                iconUrl = "https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=150",
                category = "Love",
                language = "Global",
                status = "PUBLISHED",
                sortOrder = 2,
                stickersCount = 4
            )
            firestore.collection("stickerPacks").document(pack2Id).set(pack2).await()

            val pack2Stickers = listOf(
                StickerItem("stk_heart_sparkle", pack2Id, "Heart Sparkle 💖", "Glowing pink heart", "ANIMATED", "", "https://media.giphy.com/media/l4pTdcifPZLpDjL1e/giphy.gif", "", "", "image/gif", 512, 512, 0, 0, 16, listOf("heart", "love", "sparkle"), 1, "PUBLISHED", 312, true),
                StickerItem("stk_flying_kiss", pack2Id, "Flying Kisses 😘", "Romantic sweet kiss", "ANIMATED", "", "https://media.giphy.com/media/3o7TKoWXm3okO1kgHC/giphy.gif", "", "", "image/gif", 512, 512, 0, 0, 18, listOf("kiss", "love", "sweet"), 2, "PUBLISHED", 198, true),
                StickerItem("stk_roses", pack2Id, "Red Roses 🌹", "Bouquet of beautiful red roses", "STATIC", "", "https://images.unsplash.com/photo-1518895949257-7621c3c786d7?w=300", "", "", "image/png", 512, 512, 0, 0, 1, listOf("rose", "flowers", "romantic"), 3, "PUBLISHED", 164, false),
                StickerItem("stk_cuddle_bear", pack2Id, "Cuddle Hug 🐻", "Warm cute bear hug", "ANIMATED", "", "https://media.giphy.com/media/MDJ9IbxxvDUQM/giphy.gif", "", "", "image/gif", 512, 512, 0, 0, 22, listOf("hug", "cuddle", "cute"), 4, "PUBLISHED", 220, true)
            )
            pack2Stickers.forEach { firestore.collection("stickers").document(it.stickerId).set(it).await() }

            // 3. Reactions & Memes Pack
            val pack3Id = "pack_reactions_hype"
            val pack3 = StickerPack(
                packId = pack3Id,
                name = "Hype & Reactions 🔥",
                description = "Express yourself with fire reactions and funny moments",
                iconUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=150",
                category = "Reactions",
                language = "Global",
                status = "PUBLISHED",
                sortOrder = 3,
                stickersCount = 4
            )
            firestore.collection("stickerPacks").document(pack3Id).set(pack3).await()

            val pack3Stickers = listOf(
                StickerItem("stk_mind_blown", pack3Id, "Mind Blown 🤯", "Exploding reaction", "ANIMATED", "", "https://media.giphy.com/media/26ufdipQqU2lhNA4g/giphy.gif", "", "", "image/gif", 512, 512, 0, 0, 15, listOf("mindblown", "shock", "wow"), 1, "PUBLISHED", 270, true),
                StickerItem("stk_fire_lit", pack3Id, "Pure Fire 🔥", "This is lit!", "ANIMATED", "", "https://media.giphy.com/media/nrXif9YExO9EI/giphy.gif", "", "", "image/gif", 512, 512, 0, 0, 16, listOf("fire", "lit", "hot", "trending"), 2, "PUBLISHED", 410, true),
                StickerItem("stk_thumbs_up", pack3Id, "Super Cool 👍", "Approved and great job", "ANIMATED", "", "https://media.giphy.com/media/111ebonMs90YLu/giphy.gif", "", "", "image/gif", 512, 512, 0, 0, 14, listOf("thumbsup", "cool", "agree"), 3, "PUBLISHED", 185, true),
                StickerItem("stk_lol_cat", pack3Id, "Cat Vibing 🐱", "Vibing to the tune", "ANIMATED", "", "https://media.giphy.com/media/jpbnoe3UIa8TU8LM13/giphy.gif", "", "", "image/gif", 512, 512, 0, 0, 20, listOf("cat", "vibe", "funny"), 4, "PUBLISHED", 305, true)
            )
            pack3Stickers.forEach { firestore.collection("stickers").document(it.stickerId).set(it).await() }

            Log.d(TAG, "Successfully seeded default Social India sticker packs!")
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding initial sticker packs", e)
        }
    }
}
