package com.example.zego

import android.util.Log
import com.example.BuildConfig
import com.example.model.RepositoryManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class ZegoEnvironment {
    PRODUCTION,
    TEST
}

data class ZegoKeyProfile(
    val id: String = "primary",
    val label: String = "Primary Free Plan (10K mins)",
    val appId: Long = SocialIndiaZegoConfig.DEFAULT_APP_ID,
    val appSign: String = SocialIndiaZegoConfig.DEFAULT_APP_SIGN,
    val monthlyQuotaMinutes: Int = 10000,
    val usedMinutes: Double = 0.0
)

data class DynamicZegoLiveConfig(
    val activeProfileId: String = "primary",
    val appId: Long = SocialIndiaZegoConfig.DEFAULT_APP_ID,
    val appSign: String = SocialIndiaZegoConfig.DEFAULT_APP_SIGN,
    val keyProfiles: List<ZegoKeyProfile> = listOf(ZegoKeyProfile()),
    val isLiveEnabled: Boolean = true,
    val minFollowersToGoLive: Int = 100,
    val verifiedBypassFollowerGate: Boolean = true,
    val allowCoHosting: Boolean = true,
    val allowLiveGifts: Boolean = true,
    val allowLiveComments: Boolean = true,
    val allowBeautyFilters: Boolean = true,
    val streamQuality: String = "HD_720P",
    val previewBannerTitle: String = "Social India Live Studio",
    val previewBannerSubtitle: String = "Go Live in Real-Time HD with Fans, 3D Gifts & Co-Hosts",
    val previewBannerImageUrl: String = "",
    val monthlyFreeQuotaMinutes: Int = 10000,
    val usedMinutesThisMonth: Double = 0.0,
    val updatedAt: Long = 0L
)

data class LiveEligibilityResult(
    val isAllowed: Boolean,
    val isLiveSystemEnabled: Boolean,
    val currentFollowers: Int,
    val requiredFollowers: Int,
    val isAdminBypass: Boolean = false,
    val isVerifiedBypass: Boolean = false,
    val isWhitelisted: Boolean = false,
    val blockReason: String = ""
)

/**
 * Social India Dynamic ZEGO Cloud & Live Streaming Configuration Layer.
 * Syncs in real time from Firestore `app_config/zego_live_config` so Admin can hot-swap
 * AppID/AppSign keys (for 10,000-min free plan rotation), customize Live Preview banners,
 * toggle Public Live Streaming, and enforce Minimum Followers eligibility without rebuilding APK.
 */
object SocialIndiaZegoConfig {
    private const val TAG = "SocialIndiaZegoConfig"
    private const val CONFIG_COLLECTION = "app_config"
    private const val CONFIG_DOCUMENT = "zego_live_config"

    const val DEFAULT_APP_ID: Long = 0L
    const val DEFAULT_APP_SIGN: String = ""

    private val _liveConfig = MutableStateFlow(DynamicZegoLiveConfig(appId = fallbackAppId(), appSign = fallbackAppSign()))
    val liveConfig: StateFlow<DynamicZegoLiveConfig> = _liveConfig.asStateFlow()

    private var configListener: ListenerRegistration? = null

    private fun fallbackAppId(): Long {
        return try {
            val configured = BuildConfig.ZEGO_APP_ID.trim()
            configured.toLongOrNull() ?: DEFAULT_APP_ID
        } catch (_: Exception) {
            DEFAULT_APP_ID
        }
    }

    private fun fallbackAppSign(): String {
        return try {
            val configured = BuildConfig.ZEGO_APP_SIGN.trim()
            if (configured.length >= 32) configured else DEFAULT_APP_SIGN
        } catch (_: Exception) {
            DEFAULT_APP_SIGN
        }
    }

    val appId: Long
        get() = _liveConfig.value.appId.takeIf { it > 0L } ?: fallbackAppId()

    val appSign: String
        get() = _liveConfig.value.appSign.takeIf { it.length >= 32 } ?: fallbackAppSign()

    val isLiveEnabled: Boolean
        get() = _liveConfig.value.isLiveEnabled

    val minFollowersToGoLive: Int
        get() = _liveConfig.value.minFollowersToGoLive.coerceAtLeast(0)

    val environment: ZegoEnvironment = ZegoEnvironment.PRODUCTION

    fun startObserving() {
        if (configListener != null) return
        try {
            val db = if (RepositoryManager.isInitialized()) RepositoryManager.firestore else FirebaseFirestore.getInstance()
            configListener = db.collection(CONFIG_COLLECTION)
                .document(CONFIG_DOCUMENT)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Failed to observe dynamic ZEGO live config: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        _liveConfig.value = parseConfig(snapshot.data)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching ZEGO live config observer", e)
        }
    }

    fun stopObserving() {
        configListener?.remove()
        configListener = null
    }

    suspend fun fetchLatestConfig(): DynamicZegoLiveConfig = withContext(Dispatchers.IO) {
        try {
            val db = if (RepositoryManager.isInitialized()) RepositoryManager.firestore else FirebaseFirestore.getInstance()
            val doc = db.collection(CONFIG_COLLECTION).document(CONFIG_DOCUMENT).get().await()
            if (doc.exists()) {
                val parsed = parseConfig(doc.data)
                _liveConfig.value = parsed
                return@withContext parsed
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "fetchLatestConfig fallback to cached: ${e.message}")
        }
        _liveConfig.value
    }

    suspend fun saveConfigToFirestore(config: DynamicZegoLiveConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = if (RepositoryManager.isInitialized()) RepositoryManager.firestore else FirebaseFirestore.getInstance()
            val profilesList = config.keyProfiles.map { p ->
                mapOf(
                    "id" to p.id,
                    "label" to p.label,
                    "appId" to p.appId,
                    "appSign" to p.appSign,
                    "monthlyQuotaMinutes" to p.monthlyQuotaMinutes,
                    "usedMinutes" to p.usedMinutes
                )
            }
            val payload = hashMapOf(
                "activeProfileId" to config.activeProfileId,
                "appId" to config.appId,
                "appSign" to config.appSign,
                "keyProfiles" to profilesList,
                "isLiveEnabled" to config.isLiveEnabled,
                "minFollowersToGoLive" to config.minFollowersToGoLive.coerceAtLeast(0),
                "verifiedBypassFollowerGate" to config.verifiedBypassFollowerGate,
                "allowCoHosting" to config.allowCoHosting,
                "allowLiveGifts" to config.allowLiveGifts,
                "allowLiveComments" to config.allowLiveComments,
                "allowBeautyFilters" to config.allowBeautyFilters,
                "streamQuality" to config.streamQuality,
                "previewBannerTitle" to config.previewBannerTitle,
                "previewBannerSubtitle" to config.previewBannerSubtitle,
                "previewBannerImageUrl" to config.previewBannerImageUrl,
                "monthlyFreeQuotaMinutes" to config.monthlyFreeQuotaMinutes.coerceAtLeast(100),
                "usedMinutesThisMonth" to config.usedMinutesThisMonth.coerceAtLeast(0.0),
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection(CONFIG_COLLECTION)
                .document(CONFIG_DOCUMENT)
                .set(payload, SetOptions.merge())
                .await()
            _liveConfig.value = config.copy(updatedAt = System.currentTimeMillis())
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving dynamic ZEGO live config", e)
            Result.failure(e)
        }
    }

    /**
     * Tracks minutes consumed against the active 10,000-minute ZEGO free plan quota.
     */
    suspend fun recordLiveSessionMinutes(durationMinutes: Double) = withContext(Dispatchers.IO) {
        if (durationMinutes <= 0.05) return@withContext
        try {
            val current = fetchLatestConfig()
            val updatedProfiles = current.keyProfiles.map { p ->
                if (p.id == current.activeProfileId) {
                    p.copy(usedMinutes = p.usedMinutes + durationMinutes)
                } else p
            }
            val nextTotal = current.usedMinutesThisMonth + durationMinutes
            saveConfigToFirestore(
                current.copy(
                    keyProfiles = updatedProfiles,
                    usedMinutesThisMonth = nextTotal
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Unable to record live session minutes: ${e.message}")
        }
    }

    /**
     * Checks whether a user is allowed to broadcast Live based on Admin's real-time rules:
     * Master Live Switch, Admin/VIP whitelist override, Verified bypass, and Minimum Followers Gate.
     */
    suspend fun checkUserLiveEligibility(userId: String): LiveEligibilityResult = withContext(Dispatchers.IO) {
        val cfg = fetchLatestConfig()
        if (!cfg.isLiveEnabled) {
            return@withContext LiveEligibilityResult(
                isAllowed = false,
                isLiveSystemEnabled = false,
                currentFollowers = 0,
                requiredFollowers = cfg.minFollowersToGoLive,
                blockReason = "Live Streaming is currently paused by Admin."
            )
        }
        if (userId.isBlank()) {
            return@withContext LiveEligibilityResult(
                isAllowed = false,
                isLiveSystemEnabled = true,
                currentFollowers = 0,
                requiredFollowers = cfg.minFollowersToGoLive,
                blockReason = "Please sign in to start a Live Stream."
            )
        }
        try {
            val db = if (RepositoryManager.isInitialized()) RepositoryManager.firestore else FirebaseFirestore.getInstance()
            val userDoc = db.collection("users").document(userId).get().await()
            val isAdmin = userDoc.getBoolean("isAdmin") ?: false
            val liveOverride = userDoc.getBoolean("liveAccessOverride")
            val vStatus = userDoc.getString("verificationStatus") ?: ""
            val vSuspended = userDoc.getBoolean("verificationSuspended") ?: false
            val isVerified = !vSuspended && (
                (userDoc.getBoolean("isVerified") ?: false) ||
                vStatus == "approved" ||
                vStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE
            )
            val followerCount = (
                userDoc.getLong("followerCount")
                    ?: userDoc.getLong("followersCount")
                    ?: (userDoc.get("followers") as? List<*>)?.size?.toLong()
                    ?: 0L
            ).toInt().coerceAtLeast(0)

            // Explicit Admin block overrides everything except isAdmin
            if (liveOverride == false && !isAdmin) {
                return@withContext LiveEligibilityResult(
                    isAllowed = false,
                    isLiveSystemEnabled = true,
                    currentFollowers = followerCount,
                    requiredFollowers = cfg.minFollowersToGoLive,
                    blockReason = "Your Live access has been restricted by Admin."
                )
            }
            if (isAdmin) {
                return@withContext LiveEligibilityResult(
                    isAllowed = true,
                    isLiveSystemEnabled = true,
                    currentFollowers = followerCount,
                    requiredFollowers = cfg.minFollowersToGoLive,
                    isAdminBypass = true
                )
            }
            if (liveOverride == true) {
                return@withContext LiveEligibilityResult(
                    isAllowed = true,
                    isLiveSystemEnabled = true,
                    currentFollowers = followerCount,
                    requiredFollowers = cfg.minFollowersToGoLive,
                    isWhitelisted = true
                )
            }
            if (cfg.verifiedBypassFollowerGate && isVerified) {
                return@withContext LiveEligibilityResult(
                    isAllowed = true,
                    isLiveSystemEnabled = true,
                    currentFollowers = followerCount,
                    requiredFollowers = cfg.minFollowersToGoLive,
                    isVerifiedBypass = true
                )
            }
            val eligible = followerCount >= cfg.minFollowersToGoLive
            LiveEligibilityResult(
                isAllowed = eligible,
                isLiveSystemEnabled = true,
                currentFollowers = followerCount,
                requiredFollowers = cfg.minFollowersToGoLive,
                blockReason = if (eligible) "" else "You need at least ${cfg.minFollowersToGoLive} followers to Go Live."
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Eligibility check fallback: ${e.message}")
            LiveEligibilityResult(
                isAllowed = cfg.minFollowersToGoLive == 0,
                isLiveSystemEnabled = cfg.isLiveEnabled,
                currentFollowers = 0,
                requiredFollowers = cfg.minFollowersToGoLive,
                blockReason = "Could not verify follower count. Please check your connection."
            )
        }
    }

    private fun parseConfig(data: Map<String, Any>?): DynamicZegoLiveConfig {
        if (data == null) return _liveConfig.value
        val activeAppId = (data["appId"] as? Number)?.toLong()
            ?: (data["appId"] as? String)?.toLongOrNull()
            ?: fallbackAppId()
        val activeAppSign = (data["appSign"] as? String)?.takeIf { it.length >= 32 } ?: fallbackAppSign()
        val activeProfileId = (data["activeProfileId"] as? String)?.ifBlank { "primary" } ?: "primary"
        val rawProfiles = data["keyProfiles"] as? List<*>
        val parsedProfiles = rawProfiles?.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val id = m["id"] as? String ?: return@mapNotNull null
            val appIdNum = (m["appId"] as? Number)?.toLong()
                ?: (m["appId"] as? String)?.toLongOrNull()
                ?: return@mapNotNull null
            val signStr = (m["appSign"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ZegoKeyProfile(
                id = id,
                label = (m["label"] as? String)?.ifBlank { "ZEGO Account" } ?: "ZEGO Account",
                appId = appIdNum,
                appSign = signStr,
                monthlyQuotaMinutes = (m["monthlyQuotaMinutes"] as? Number)?.toInt() ?: 10000,
                usedMinutes = (m["usedMinutes"] as? Number)?.toDouble() ?: 0.0
            )
        }.orEmpty().ifEmpty {
            listOf(
                ZegoKeyProfile(
                    id = activeProfileId,
                    label = "Primary Free Plan (10K mins)",
                    appId = activeAppId,
                    appSign = activeAppSign,
                    monthlyQuotaMinutes = (data["monthlyFreeQuotaMinutes"] as? Number)?.toInt() ?: 10000,
                    usedMinutes = (data["usedMinutesThisMonth"] as? Number)?.toDouble() ?: 0.0
                )
            )
        }
        return DynamicZegoLiveConfig(
            activeProfileId = activeProfileId,
            appId = activeAppId,
            appSign = activeAppSign,
            keyProfiles = parsedProfiles,
            isLiveEnabled = (data["isLiveEnabled"] as? Boolean) ?: true,
            minFollowersToGoLive = ((data["minFollowersToGoLive"] as? Number)?.toInt() ?: 100).coerceAtLeast(0),
            verifiedBypassFollowerGate = (data["verifiedBypassFollowerGate"] as? Boolean) ?: true,
            allowCoHosting = (data["allowCoHosting"] as? Boolean) ?: true,
            allowLiveGifts = (data["allowLiveGifts"] as? Boolean) ?: true,
            allowLiveComments = (data["allowLiveComments"] as? Boolean) ?: true,
            allowBeautyFilters = (data["allowBeautyFilters"] as? Boolean) ?: true,
            streamQuality = (data["streamQuality"] as? String)?.ifBlank { "HD_720P" } ?: "HD_720P",
            previewBannerTitle = (data["previewBannerTitle"] as? String)?.ifBlank { "Social India Live Studio" } ?: "Social India Live Studio",
            previewBannerSubtitle = (data["previewBannerSubtitle"] as? String)?.ifBlank { "Go Live in Real-Time HD with Fans, 3D Gifts & Co-Hosts" }
                ?: "Go Live in Real-Time HD with Fans, 3D Gifts & Co-Hosts",
            previewBannerImageUrl = (data["previewBannerImageUrl"] as? String) ?: "",
            monthlyFreeQuotaMinutes = ((data["monthlyFreeQuotaMinutes"] as? Number)?.toInt() ?: 10000).coerceAtLeast(100),
            usedMinutesThisMonth = ((data["usedMinutesThisMonth"] as? Number)?.toDouble() ?: 0.0).coerceAtLeast(0.0),
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }

    /**
     * Map Firebase UID to deterministic safe ZEGO User ID:
     * social_{firebaseUid}
     */
    fun mapToZegoUserId(firebaseUid: String): String {
        val sanitized = firebaseUid.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return if (sanitized.startsWith("social_")) sanitized else "social_$sanitized"
    }

    /**
     * Strip social_ prefix to recover Firebase UID if needed.
     */
    fun mapToFirebaseUid(zegoUserId: String): String {
        return if (zegoUserId.startsWith("social_")) zegoUserId.removePrefix("social_") else zegoUserId
    }
}

interface ZegoCredentialProvider {
    fun getAppId(): Long
    fun getAppSign(): String
}

class DefaultZegoCredentialProvider : ZegoCredentialProvider {
    override fun getAppId(): Long = SocialIndiaZegoConfig.appId
    override fun getAppSign(): String = SocialIndiaZegoConfig.appSign
}

interface ZegoTokenProvider {
    suspend fun getToken(roomId: String, userId: String): String?
}
