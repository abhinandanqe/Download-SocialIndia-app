package com.example.ui.templates

import android.content.Context
import android.util.Log
import com.example.model.RepositoryManager
import com.example.repository.ExclusiveContentRepository
import com.example.repository.ExclusiveUnlockResult
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Unified Repository for the Social India Templates System.
 *
 * Firestore Collections:
 * - `templates`      : Public & creator-published template JSON documents
 * - `savedTemplates` : Per-user favorited templates (docId: "{userId}_{templateId}")
 * - `templateUsage`  : Usage logs, Recently Used history, Reel template counts & Quick Story chain counters
 *
 * Offline-First Guarantee:
 * - Saved templates, recently used IDs, unlocked premium template IDs, and the user's Brand Kit
 *   are mirrored locally inside `filesDir/offline_templates/` so Saved Templates ALWAYS open offline!
 */
object TemplateRepository {
    private const val TAG = "TemplateRepository"
    private const val COL_TEMPLATES = "templates"
    private const val COL_SAVED = "savedTemplates"
    private const val COL_USAGE = "templateUsage"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private fun firestore(): FirebaseFirestore = RepositoryManager.firestore

    private fun cacheDir(context: Context): File =
        File(context.filesDir, "offline_templates").apply { mkdirs() }

    /**
     * Fetches all templates from Firestore `templates`, merges seamlessly with the 10 built-in
     * India-focused sample templates, and caches locally for offline access.
     */
    suspend fun fetchAllTemplates(context: Context): Pair<List<ContentTemplate>, Boolean> = withContext(Dispatchers.IO) {
        val sampleMap = SAMPLE_SOCIAL_INDIA_TEMPLATES.associateBy { it.id }.toMutableMap()
        val cachedSaved = loadOfflineSavedTemplates(context)
        cachedSaved.forEach { sampleMap[it.id] = it }

        var isOfflineFallback = false
        try {
            val snap = firestore().collection(COL_TEMPLATES)
                .limit(120)
                .get()
                .await()

            for (doc in snap.documents) {
                val rawJson = doc.getString("templateJson")
                val parsed = if (!rawJson.isNullOrBlank()) {
                    try {
                        json.decodeFromString<ContentTemplate>(rawJson).copy(
                            id = doc.id,
                            usageCount = doc.getLong("usageCount") ?: 0L
                        )
                    } catch (_: Exception) {
                        null
                    }
                } else null
                if (parsed != null) {
                    sampleMap[parsed.id] = parsed
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Offline or Firestore read fallback for templates: ${e.message}")
            isOfflineFallback = true
        }

        // Apply any cached local usage bumps so counters stay instant
        val localCounts = loadLocalUsageCounts(context)
        val merged = sampleMap.values.map { tpl ->
            val extra = localCounts[tpl.id] ?: 0L
            if (extra > tpl.usageCount) tpl.copy(usageCount = extra) else tpl
        }.sortedByDescending { it.usageCount }

        merged to isOfflineFallback
    }

    /**
     * Publishes a creator's own edit or Reel as a reusable Template in Firestore `templates`.
     */
    suspend fun publishCreatorTemplate(
        context: Context,
        template: ContentTemplate
    ): Result<ContentTemplate> = withContext(Dispatchers.IO) {
        try {
            val uid = RepositoryManager.getCurrentUserId().ifBlank { "creator_local" }
            val user = RepositoryManager.currentUserState.value
            val finalTemplate = template.copy(
                id = template.id.ifBlank { "tpl_${UUID.randomUUID().toString().take(10)}" },
                creatorId = uid,
                creatorName = user?.username?.ifBlank { "Creator" } ?: template.creatorName,
                createdAtMs = System.currentTimeMillis()
            )
            val payload = hashMapOf(
                "id" to finalTemplate.id,
                "type" to finalTemplate.type,
                "title" to finalTemplate.title,
                "category" to finalTemplate.category,
                "aspectRatio" to finalTemplate.aspectRatio,
                "duration" to finalTemplate.duration,
                "creatorId" to finalTemplate.creatorId,
                "creatorName" to finalTemplate.creatorName,
                "usageCount" to finalTemplate.usageCount,
                "isPremium" to finalTemplate.isPremium,
                "price" to finalTemplate.price,
                "promptText" to finalTemplate.promptText,
                "originTemplateId" to finalTemplate.originTemplateId,
                "templateJson" to json.encodeToString(finalTemplate),
                "createdAtMs" to finalTemplate.createdAtMs
            )
            firestore().collection(COL_TEMPLATES)
                .document(finalTemplate.id)
                .set(payload, SetOptions.merge())
                .await()

            // Mirror locally so creator can use immediately offline too
            saveOfflineTemplateFile(context, finalTemplate)
            Result.success(finalTemplate)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish template", e)
            Result.failure(e)
        }
    }

    // ================= SAVED / FAVORITE TEMPLATES (ONLINE + OFFLINE) =================

    suspend fun getSavedTemplateIds(context: Context): Set<String> = withContext(Dispatchers.IO) {
        val localIds = loadOfflineSavedTemplates(context).map { it.id }.toMutableSet()
        val uid = RepositoryManager.getCurrentUserId()
        if (uid.isNotBlank()) {
            try {
                val snap = firestore().collection(COL_SAVED)
                    .whereEqualTo("userId", uid)
                    .limit(100)
                    .get()
                    .await()
                for (doc in snap.documents) {
                    val tid = doc.getString("templateId")
                    if (!tid.isNullOrBlank()) localIds.add(tid)
                    val raw = doc.getString("templateJson")
                    if (!raw.isNullOrBlank()) {
                        try {
                            val tpl = json.decodeFromString<ContentTemplate>(raw)
                            saveOfflineTemplateFile(context, tpl)
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Offline fallback for savedTemplates: ${e.message}")
            }
        }
        localIds
    }

    suspend fun toggleSaveTemplate(
        context: Context,
        template: ContentTemplate,
        currentlySaved: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val newSaved = !currentlySaved
        val uid = RepositoryManager.getCurrentUserId()
        // 1. Always mirror offline first so Saved Templates work with zero connectivity
        if (newSaved) {
            saveOfflineTemplateFile(context, template)
        } else {
            deleteOfflineTemplateFile(context, template.id)
        }

        // 2. Sync to Firestore `savedTemplates`
        if (uid.isNotBlank()) {
            try {
                val docId = "${uid}_${template.id}"
                if (newSaved) {
                    firestore().collection(COL_SAVED).document(docId).set(
                        hashMapOf(
                            "userId" to uid,
                            "templateId" to template.id,
                            "savedAtMs" to System.currentTimeMillis(),
                            "templateJson" to json.encodeToString(template)
                        ),
                        SetOptions.merge()
                    ).await()
                } else {
                    firestore().collection(COL_SAVED).document(docId).delete().await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Saved template offline-only sync queued: ${e.message}")
            }
        }
        newSaved
    }

    fun loadOfflineSavedTemplates(context: Context): List<ContentTemplate> {
        return try {
            val files = cacheDir(context).listFiles { f -> f.name.startsWith("saved_") && f.extension == "json" }
                ?: return emptyList()
            files.mapNotNull { file ->
                try {
                    json.decodeFromString<ContentTemplate>(file.readText())
                } catch (_: Exception) {
                    null
                }
            }.sortedByDescending { it.createdAtMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveOfflineTemplateFile(context: Context, template: ContentTemplate) {
        try {
            val f = File(cacheDir(context), "saved_${template.id}.json")
            f.writeText(json.encodeToString(template))
        } catch (_: Exception) {}
    }

    private fun deleteOfflineTemplateFile(context: Context, templateId: String) {
        try {
            File(cacheDir(context), "saved_${templateId}.json").delete()
        } catch (_: Exception) {}
    }

    // ================= TEMPLATE USAGE, RECENTLY USED & CHAIN COUNTERS =================

    suspend fun recordTemplateUsage(
        context: Context,
        template: ContentTemplate,
        sourcePostId: String? = null
    ) = withContext(Dispatchers.IO) {
        val uid = RepositoryManager.getCurrentUserId().ifBlank { "guest" }
        val now = System.currentTimeMillis()
        // Update local recently used list & local count immediately (works offline!)
        recordRecentLocally(context, template.id)
        bumpLocalUsageCount(context, template.id, template.usageCount + 1L)

        try {
            val usageDoc = hashMapOf(
                "templateId" to template.id,
                "originTemplateId" to template.originTemplateId.ifBlank { template.id },
                "type" to template.type,
                "userId" to uid,
                "sourcePostId" to (sourcePostId ?: ""),
                "createdAtMs" to now
            )
            firestore().collection(COL_USAGE).add(usageDoc).await()

            // Increment usageCount on template doc if it exists
            firestore().collection(COL_TEMPLATES).document(template.id)
                .set(
                    hashMapOf(
                        "id" to template.id,
                        "usageCount" to FieldValue.increment(1L),
                        "templateJson" to json.encodeToString(template.copy(usageCount = template.usageCount + 1L))
                    ),
                    SetOptions.merge()
                ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Template usage cached locally (offline): ${e.message}")
        }
    }

    suspend fun getReelTemplateUsageCount(postId: String): Long = withContext(Dispatchers.IO) {
        if (postId.isBlank()) return@withContext 0L
        try {
            val snap = firestore().collection(COL_USAGE)
                .whereEqualTo("sourcePostId", postId)
                .limit(200)
                .get()
                .await()
            snap.size().toLong()
        } catch (_: Exception) {
            0L
        }
    }

    fun getRecentlyUsedIds(context: Context): List<String> {
        return try {
            val prefs = context.getSharedPreferences("social_india_templates", Context.MODE_PRIVATE)
            val raw = prefs.getString("recent_ids", "") ?: ""
            raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun recordRecentLocally(context: Context, templateId: String) {
        try {
            val prefs = context.getSharedPreferences("social_india_templates", Context.MODE_PRIVATE)
            val existing = getRecentlyUsedIds(context).filterNot { it == templateId }
            val updated = (listOf(templateId) + existing).take(12)
            prefs.edit().putString("recent_ids", updated.joinToString(",")).apply()
        } catch (_: Exception) {}
    }

    private fun bumpLocalUsageCount(context: Context, templateId: String, minCount: Long) {
        try {
            val prefs = context.getSharedPreferences("social_india_templates", Context.MODE_PRIVATE)
            val prev = prefs.getLong("usage_$templateId", 0L)
            prefs.edit().putLong("usage_$templateId", maxOf(prev + 1L, minCount)).apply()
        } catch (_: Exception) {}
    }

    private fun loadLocalUsageCounts(context: Context): Map<String, Long> {
        return try {
            val prefs = context.getSharedPreferences("social_india_templates", Context.MODE_PRIVATE)
            prefs.all.mapNotNull { (k, v) ->
                if (k.startsWith("usage_") && v is Long) k.removePrefix("usage_") to v else null
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // ================= PREMIUM TEMPLATE UNLOCK (REUSES EXISTING PAYMENT PIPELINE) =================

    suspend fun getUnlockedTemplateIds(context: Context): Set<String> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("social_india_templates", Context.MODE_PRIVATE)
        val local = (prefs.getStringSet("unlocked_templates", emptySet()) ?: emptySet()).toMutableSet()
        val uid = RepositoryManager.getCurrentUserId()
        if (uid.isNotBlank()) {
            try {
                val snap = firestore().collection("content_entitlements")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("contentType", "TEMPLATE")
                    .whereEqualTo("active", true)
                    .get()
                    .await()
                for (doc in snap.documents) {
                    doc.getString("contentId")?.let { local.add(it) }
                }
                prefs.edit().putStringSet("unlocked_templates", local).apply()
            } catch (_: Exception) {}
        }
        local
    }

    suspend fun unlockPremiumTemplateWithWallet(
        context: Context,
        template: ContentTemplate
    ): ExclusiveUnlockResult {
        val uid = RepositoryManager.getCurrentUserId()
        if (uid.isBlank()) return ExclusiveUnlockResult.Failure("Please sign in to unlock premium templates.")
        val result = ExclusiveContentRepository.unlockWithWallet(
            buyerId = uid,
            creatorId = template.creatorId,
            contentId = template.id,
            contentType = "TEMPLATE",
            title = template.title,
            teaserCaption = "${template.typeLabel} Template (${template.slots.size} clips)",
            blurredThumbnailUrl = template.previewThumbUrl,
            creatorUsername = template.creatorName,
            creatorAvatar = "",
            priceInr = template.price.coerceAtLeast(10.0)
        )
        if (result is ExclusiveUnlockResult.Success) {
            markTemplateUnlockedLocally(context, template.id)
        }
        return result
    }

    suspend fun recordGatewayTemplateUnlock(
        context: Context,
        template: ContentTemplate,
        utr: String
    ): Boolean {
        val uid = RepositoryManager.getCurrentUserId()
        if (uid.isBlank()) return false
        ExclusiveContentRepository.recordVerifiedGatewayUnlock(
            buyerId = uid,
            creatorId = template.creatorId,
            contentId = template.id,
            contentType = "TEMPLATE",
            title = template.title,
            teaserCaption = "${template.typeLabel} Template (${template.slots.size} clips)",
            blurredThumbnailUrl = template.previewThumbUrl,
            creatorUsername = template.creatorName,
            creatorAvatar = "",
            priceInr = template.price.coerceAtLeast(10.0),
            transactionRef = utr
        )
        markTemplateUnlockedLocally(context, template.id)
        return true
    }

    private fun markTemplateUnlockedLocally(context: Context, templateId: String) {
        try {
            val prefs = context.getSharedPreferences("social_india_templates", Context.MODE_PRIVATE)
            val set = (prefs.getStringSet("unlocked_templates", emptySet()) ?: emptySet()).toMutableSet()
            set.add(templateId)
            prefs.edit().putStringSet("unlocked_templates", set).apply()
        } catch (_: Exception) {}
    }

    // ================= BRAND KIT PERSISTENCE (OFFLINE + FIRESTORE) =================

    fun loadBrandKitLocally(context: Context): BrandKit {
        return try {
            val f = File(cacheDir(context), "brand_kit.json")
            if (!f.exists()) BrandKit()
            else json.decodeFromString<BrandKit>(f.readText())
        } catch (_: Exception) {
            BrandKit()
        }
    }

    suspend fun saveBrandKit(context: Context, kit: BrandKit) = withContext(Dispatchers.IO) {
        try {
            File(cacheDir(context), "brand_kit.json").writeText(json.encodeToString(kit))
            val uid = RepositoryManager.getCurrentUserId()
            if (uid.isNotBlank()) {
                firestore().collection("users")
                    .document(uid)
                    .collection("brand_kit")
                    .document("default")
                    .set(hashMapOf("brandKitJson" to json.encodeToString(kit), "updatedAtMs" to System.currentTimeMillis()), SetOptions.merge())
                    .await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Brand kit saved locally (offline): ${e.message}")
        }
    }
}
