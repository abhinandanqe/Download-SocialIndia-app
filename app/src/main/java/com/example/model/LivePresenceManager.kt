package com.example.model

import android.util.Log
import com.example.zego.SocialIndiaZegoConfig
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

data class LiveSessionInfo(
    val storyId: String,
    val userId: String,
    val username: String = "",
    val avatarUrl: String = "",
    val title: String = "Live Video",
    val category: String = "Live",
    val viewersCount: Int = 1,
    val peakViewers: Int = 1,
    val isVerified: Boolean = false,
    val isHost: Boolean = false,
    val startTime: Long = System.currentTimeMillis(),
    val lastHeartbeatMs: Long = System.currentTimeMillis()
)

/**
 * Real-Time Public Live Session Presence Engine.
 * Observes `liveSessions` where status == "LIVE" with strict heartbeat freshness (<= 3 minutes),
 * so ended/crashed/zombie streams NEVER hijack a user's Profile or Story ring!
 */
object LivePresenceManager {
    private const val TAG = "LivePresenceManager"
    private const val STALE_HEARTBEAT_THRESHOLD_MS = 180_000L // 3 minutes without host heartbeat -> stale

    private val _activeLives = MutableStateFlow<Map<String, LiveSessionInfo>>(emptyMap())
    val activeLives: StateFlow<Map<String, LiveSessionInfo>> = _activeLives.asStateFlow()

    private var liveSessionsListener: ListenerRegistration? = null
    private var isInitialized = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init() {
        if (isInitialized) return
        isInitialized = true
        SocialIndiaZegoConfig.startObserving()
        startObserving()
        cleanupZombieLiveStoriesForCurrentUser()
    }

    private fun startObserving() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            liveSessionsListener?.remove()
            liveSessionsListener = firestore.collection("liveSessions")
                .whereEqualTo("status", "LIVE")
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Error observing active liveSessions", error)
                        return@addSnapshotListener
                    }

                    if (!SocialIndiaZegoConfig.isLiveEnabled) {
                        _activeLives.value = emptyMap()
                        return@addSnapshotListener
                    }

                    val now = System.currentTimeMillis()
                    val updatedMap = ConcurrentHashMap<String, LiveSessionInfo>()

                    snapshot?.documents?.forEach { doc ->
                        val liveId = doc.id
                        val hostUid = doc.getString("hostUid")
                            ?: doc.getString("userId")
                            ?: ""
                        if (hostUid.isBlank()) return@forEach

                        val lastHeartbeat = doc.getLong("lastHeartbeatMs")
                            ?: doc.getTimestamp("startedAt")?.toDate()?.time
                            ?: 0L

                        // Strictly ignore stale/zombie streams that stopped sending heartbeats
                        if (lastHeartbeat <= 0L || (now - lastHeartbeat) > STALE_HEARTBEAT_THRESHOLD_MS) {
                            return@forEach
                        }

                        val username = doc.getString("hostUsername")
                            ?: doc.getString("username")
                            ?: "Live Creator"
                        val avatarUrl = doc.getString("hostAvatar")
                            ?: doc.getString("avatarUrl")
                            ?: ""
                        val title = doc.getString("title") ?: "Live Stream 🔴"
                        val category = doc.getString("category") ?: "Live"
                        val viewersCount = (doc.getLong("viewerCount") ?: 1L).toInt().coerceAtLeast(1)
                        val peakViewers = (doc.getLong("peakViewers") ?: viewersCount.toLong()).toInt().coerceAtLeast(viewersCount)
                        val isVerified = doc.getBoolean("isVerified") ?: false
                        val startTime = doc.getLong("startedAtMs")
                            ?: doc.getTimestamp("startedAt")?.toDate()?.time
                            ?: now

                        updatedMap[hostUid] = LiveSessionInfo(
                            storyId = liveId,
                            userId = hostUid,
                            username = username,
                            avatarUrl = avatarUrl,
                            title = title,
                            category = category,
                            viewersCount = viewersCount,
                            peakViewers = peakViewers,
                            isVerified = isVerified,
                            startTime = startTime,
                            lastHeartbeatMs = lastHeartbeat
                        )
                    }

                    _activeLives.value = updatedMap
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start LivePresenceManager observation", e)
        }
    }

    /**
     * Automatically cleans up any leftover legacy `stories` docs with isLive=true
     * from earlier sessions so user profiles & stories are never stuck in LIVE state.
     */
    fun cleanupZombieLiveStoriesForCurrentUser() {
        scope.launch {
            try {
                val uid = if (RepositoryManager.isInitialized()) RepositoryManager.getCurrentUserId() else ""
                if (uid.isBlank()) return@launch
                val db = FirebaseFirestore.getInstance()
                val staleStories = db.collection("stories")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("isLive", true)
                    .get()
                    .await()
                for (doc in staleStories.documents) {
                    try {
                        doc.reference.update("isLive", false).await()
                    } catch (_: Exception) {}
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {}
        }
    }

    /**
     * Admin emergency action: terminates all active/zombie liveSessions and legacy live stories.
     */
    suspend fun forceEndAllActiveLives(): Int {
        var cleared = 0
        try {
            val db = FirebaseFirestore.getInstance()
            val liveDocs = db.collection("liveSessions")
                .whereEqualTo("status", "LIVE")
                .get()
                .await()
            for (doc in liveDocs.documents) {
                val hostUid = doc.getString("hostUid") ?: ""
                doc.reference.set(
                    mapOf("status" to "ENDED", "endedAt" to FieldValue.serverTimestamp()),
                    SetOptions.merge()
                ).await()
                if (hostUid.isNotBlank()) {
                    try {
                        db.collection("users").document(hostUid)
                            .set(mapOf("isLive" to false, "currentLiveId" to ""), SetOptions.merge())
                            .await()
                    } catch (_: Exception) {}
                }
                cleared++
            }
            val legacyLiveStories = db.collection("stories")
                .whereEqualTo("isLive", true)
                .get()
                .await()
            for (doc in legacyLiveStories.documents) {
                doc.reference.update("isLive", false).await()
                cleared++
            }
            _activeLives.value = emptyMap()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "forceEndAllActiveLives failed", e)
        }
        return cleared
    }

    fun isUserLive(userId: String?): Boolean {
        if (userId.isNullOrEmpty() || !SocialIndiaZegoConfig.isLiveEnabled) return false
        val session = _activeLives.value[userId] ?: return false
        return (System.currentTimeMillis() - session.lastHeartbeatMs) <= STALE_HEARTBEAT_THRESHOLD_MS
    }

    fun getLiveSession(userId: String?): LiveSessionInfo? {
        if (userId.isNullOrEmpty() || !SocialIndiaZegoConfig.isLiveEnabled) return null
        val session = _activeLives.value[userId] ?: return null
        return if ((System.currentTimeMillis() - session.lastHeartbeatMs) <= STALE_HEARTBEAT_THRESHOLD_MS) session else null
    }

    fun getLiveStoryId(userId: String?): String? = getLiveSession(userId)?.storyId

    fun registerActiveLive(session: LiveSessionInfo) {
        val updated = _activeLives.value.toMutableMap()
        updated[session.userId] = session
        _activeLives.value = updated
    }

    fun endLive(liveId: String, userId: String) {
        val updated = _activeLives.value.toMutableMap()
        updated.remove(userId)
        _activeLives.value = updated

        scope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                if (liveId.isNotBlank()) {
                    db.collection("liveSessions").document(liveId).set(
                        mapOf(
                            "status" to "ENDED",
                            "endedAt" to FieldValue.serverTimestamp(),
                            "lastHeartbeatMs" to 0L
                        ),
                        SetOptions.merge()
                    ).await()
                    try {
                        RepositoryManager.postRepository.endLiveStory(liveId)
                    } catch (_: Exception) {}
                }
                if (userId.isNotBlank()) {
                    db.collection("users").document(userId).set(
                        mapOf("isLive" to false, "currentLiveId" to ""),
                        SetOptions.merge()
                    ).await()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Error ending live session cleanly", e)
            }
        }
    }
}
