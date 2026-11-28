package com.example.zego

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.model.RepositoryManager
import com.example.ui.CallActivity
import com.example.ui.LiveActivity
import com.example.ui.LiveAudioRoomActivity
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class ZegoCallState {
    IDLE,
    OUTGOING,
    RINGING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ENDED,
    REJECTED,
    MISSED,
    FAILED
}

data class ActiveCallInfo(
    val callId: String,
    val callerUid: String,
    val receiverUid: String,
    val peerUsername: String,
    val peerPhotoUrl: String = "",
    val isVideo: Boolean,
    val isOutgoing: Boolean,
    val status: ZegoCallState = ZegoCallState.IDLE,
    val startedAt: Long = System.currentTimeMillis(),
    val connectedAt: Long = 0L,
    val durationSeconds: Long = 0L
)

data class CallHistoryItem(
    val callId: String = "",
    val peerUid: String = "",
    val peerUsername: String = "User",
    val peerPhotoUrl: String = "",
    val isVideo: Boolean = true,
    val isOutgoing: Boolean = true,
    val status: String = "ended", // "ended", "missed", "rejected"
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Long = 0L
)

object SocialIndiaZegoManager {
    private const val TAG = "SocialIndiaZegoManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _callState = MutableStateFlow<ActiveCallInfo?>(null)
    val callState: StateFlow<ActiveCallInfo?> = _callState.asStateFlow()

    private val _isMicrophoneMuted = MutableStateFlow(false)
    val isMicrophoneMuted: StateFlow<Boolean> = _isMicrophoneMuted.asStateFlow()

    private val _isCameraEnabled = MutableStateFlow(true)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private var currentZegoUserId: String = ""
    private var currentUserName: String = ""

    /**
     * Initializes the ZEGO communication subsystem.
     */
    fun initialize(context: Context) {
        if (_isInitialized.value) return
        try {
            val appId = SocialIndiaZegoConfig.appId
            Log.d(TAG, "Initializing ZEGO with AppID: $appId (Environment: ${SocialIndiaZegoConfig.environment})")
            _isInitialized.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ZEGO subsystem", e)
        }
    }

    /**
     * Logs in the active user using deterministic Social India UID mapping.
     */
    fun loginUser(firebaseUid: String, userName: String) {
        currentZegoUserId = SocialIndiaZegoConfig.mapToZegoUserId(firebaseUid)
        currentUserName = userName.ifEmpty { "User_${firebaseUid.take(5)}" }
        Log.d(TAG, "User logged in to ZEGO: $currentZegoUserId ($currentUserName)")
    }

    fun logoutUser() {
        Log.d(TAG, "Logging out user from ZEGO: $currentZegoUserId")
        cleanup()
        currentZegoUserId = ""
        currentUserName = ""
    }

    fun generateRoomId(prefix: String = "room"): String {
        return "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
    }

    // --- LIVE STREAMING ---

    fun startLive(context: Context, liveId: String, isHost: Boolean, userName: String) {
        if (liveId.isBlank()) return
        val currentUid = RepositoryManager.getCurrentUserId()
        val mappedUid = SocialIndiaZegoConfig.mapToZegoUserId(currentUid)
        val displayName = userName.ifEmpty { "Creator" }

        try {
            val intent = Intent(context, LiveActivity::class.java).apply {
                putExtra("liveID", liveId)
                putExtra("isHost", isHost)
                putExtra("userID", mappedUid)
                putExtra("userName", displayName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start LiveActivity", e)
        }
    }

    fun joinLive(context: Context, liveId: String, userName: String) {
        startLive(context, liveId, isHost = false, userName = userName)
    }

    fun stopLive(liveId: String) {
        if (liveId.isBlank()) return
        val currentUid = RepositoryManager.getCurrentUserId()
        com.example.model.LivePresenceManager.endLive(liveId, currentUid)
    }

    // --- LIVE AUDIO ROOM ---

    fun startAudioRoom(context: Context, roomId: String, roomTitle: String, isHost: Boolean, userName: String) {
        if (roomId.isBlank()) return
        val currentUid = RepositoryManager.getCurrentUserId()
        val mappedUid = SocialIndiaZegoConfig.mapToZegoUserId(currentUid)
        val displayName = userName.ifEmpty { "Speaker" }

        try {
            val intent = Intent(context, LiveAudioRoomActivity::class.java).apply {
                putExtra("roomID", roomId)
                putExtra("roomTitle", roomTitle)
                putExtra("isHost", isHost)
                putExtra("userID", mappedUid)
                putExtra("userName", displayName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start LiveAudioRoomActivity", e)
        }
    }

    // --- ONE-TO-ONE VOICE & VIDEO CALLS ---

    fun startVoiceCall(context: Context, peerId: String, peerName: String, peerPhotoUrl: String = "") {
        com.example.ui.GlobalCallManager.initiateCall(
            context = context,
            peerId = peerId,
            peerUsername = peerName,
            peerDisplayName = peerName,
            peerAvatarUrl = peerPhotoUrl,
            isVideo = false
        )
    }

    fun startVideoCall(context: Context, peerId: String, peerName: String, peerPhotoUrl: String = "") {
        com.example.ui.GlobalCallManager.initiateCall(
            context = context,
            peerId = peerId,
            peerUsername = peerName,
            peerDisplayName = peerName,
            peerAvatarUrl = peerPhotoUrl,
            isVideo = true
        )
    }

    private fun initiateCallInternal(
        context: Context,
        peerId: String,
        peerName: String,
        peerPhotoUrl: String,
        isVideo: Boolean
    ) {
        val currentUserId = RepositoryManager.getCurrentUserId()
        val roomID = if (currentUserId < peerId) "${currentUserId}_${peerId}" else "${peerId}_${currentUserId}"
        val callId = "call_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

        val callInfo = ActiveCallInfo(
            callId = callId,
            callerUid = currentUserId,
            receiverUid = peerId,
            peerUsername = peerName,
            peerPhotoUrl = peerPhotoUrl,
            isVideo = isVideo,
            isOutgoing = true,
            status = ZegoCallState.OUTGOING,
            startedAt = System.currentTimeMillis()
        )
        _callState.value = callInfo

        // Record in Firebase calls collection
        scope.launch {
            recordCallInFirestore(callInfo, roomID)
        }

        // Send real-time signaling offer to receiver
        RepositoryManager.socialRepository.sendCallSignaling(
            receiverId = peerId,
            type = "offer",
            data = mapOf(
                "callId" to callId,
                "mediaType" to if (isVideo) "video" else "audio",
                "senderUsername" to currentUserName,
                "roomID" to roomID,
                "timestamp" to System.currentTimeMillis()
            )
        )

        // Launch Zego CallActivity
        val mappedUid = SocialIndiaZegoConfig.mapToZegoUserId(currentUserId)
        try {
            val intent = Intent(context, CallActivity::class.java).apply {
                putExtra("userID", mappedUid)
                putExtra("userName", currentUserName)
                putExtra("callID", roomID)
                putExtra("isVideoCall", isVideo)
                putExtra("peerId", peerId)
                putExtra("peerName", peerName)
                putExtra("peerAvatar", peerPhotoUrl)
                putExtra("isOutgoing", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch outgoing CallActivity", e)
        }
    }

    fun acceptCall(context: Context, callId: String, roomID: String, isVideo: Boolean) {
        if (callId.isBlank() || roomID.isBlank()) return
        val current = _callState.value
        val currentUserId = RepositoryManager.getCurrentUserId()
        val mappedUid = SocialIndiaZegoConfig.mapToZegoUserId(currentUserId)

        _callState.value = current?.copy(
            status = ZegoCallState.CONNECTED,
            connectedAt = System.currentTimeMillis()
        )

        // Update Firestore
        scope.launch {
            try {
                RepositoryManager.firestore.collection("calls").document(callId).update(
                    mapOf(
                        "status" to "connected",
                        "connectedAt" to FieldValue.serverTimestamp()
                    )
                )
            } catch (ignored: Exception) {}
        }

        // Launch CallActivity
        try {
            val intent = Intent(context, CallActivity::class.java).apply {
                putExtra("userID", mappedUid)
                putExtra("userName", currentUserName)
                putExtra("callID", roomID)
                putExtra("isVideoCall", isVideo)
                putExtra("peerId", current?.callerUid ?: "")
                putExtra("peerName", current?.peerUsername ?: "User")
                putExtra("peerAvatar", current?.peerPhotoUrl ?: "")
                putExtra("isOutgoing", false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch accepted CallActivity", e)
        }
    }

    fun rejectCall(callId: String, peerId: String) {
        val current = _callState.value
        _callState.value = current?.copy(status = ZegoCallState.REJECTED)

        RepositoryManager.socialRepository.sendCallSignaling(
            receiverId = peerId,
            type = "decline",
            data = mapOf(
                "callId" to callId,
                "timestamp" to System.currentTimeMillis()
            )
        )

        scope.launch {
            try {
                RepositoryManager.firestore.collection("calls").document(callId).update(
                    mapOf(
                        "status" to "rejected",
                        "endedAt" to FieldValue.serverTimestamp()
                    )
                )
            } catch (ignored: Exception) {}
        }

        cleanup()
    }

    fun endCall(callId: String, peerId: String) {
        val current = _callState.value
        val now = System.currentTimeMillis()
        val duration = if (current != null && current.connectedAt > 0) {
            (now - current.connectedAt) / 1000
        } else 0L

        _callState.value = current?.copy(
            status = ZegoCallState.ENDED,
            durationSeconds = duration
        )

        RepositoryManager.socialRepository.sendCallSignaling(
            receiverId = peerId,
            type = "end",
            data = mapOf(
                "callId" to callId,
                "duration" to duration,
                "timestamp" to now
            )
        )

        scope.launch {
            try {
                RepositoryManager.firestore.collection("calls").document(callId).update(
                    mapOf(
                        "status" to "ended",
                        "endedAt" to FieldValue.serverTimestamp(),
                        "duration" to duration
                    )
                )
            } catch (ignored: Exception) {}
        }

        cleanup()
    }

    private suspend fun recordCallInFirestore(call: ActiveCallInfo, zegoRoomId: String) {
        try {
            val record = hashMapOf(
                "callId" to call.callId,
                "callerUid" to call.callerUid,
                "receiverUid" to call.receiverUid,
                "callType" to if (call.isVideo) "VIDEO" else "VOICE",
                "zegoRoomId" to zegoRoomId,
                "status" to "calling",
                "startedAt" to FieldValue.serverTimestamp(),
                "connectedAt" to null,
                "endedAt" to null,
                "duration" to 0L,
                "createdAt" to FieldValue.serverTimestamp()
            )
            RepositoryManager.firestore.collection("calls").document(call.callId).set(record)

            // Save in user call history
            val callerHistory = hashMapOf(
                "callId" to call.callId,
                "peerUid" to call.receiverUid,
                "peerUsername" to call.peerUsername,
                "peerPhotoUrl" to call.peerPhotoUrl,
                "isVideo" to call.isVideo,
                "isOutgoing" to true,
                "status" to "calling",
                "timestamp" to System.currentTimeMillis(),
                "duration" to 0L
            )
            RepositoryManager.firestore.collection("users")
                .document(call.callerUid)
                .collection("call_history")
                .document(call.callId)
                .set(callerHistory)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record call in Firestore", e)
        }
    }

    // --- CONTROLS ---

    fun muteMicrophone(muted: Boolean) {
        _isMicrophoneMuted.value = muted
    }

    fun unmuteMicrophone() {
        _isMicrophoneMuted.value = false
    }

    fun enableCamera(enabled: Boolean) {
        _isCameraEnabled.value = enabled
    }

    fun disableCamera() {
        _isCameraEnabled.value = false
    }

    fun enableSpeaker(enabled: Boolean) {
        _isSpeakerOn.value = enabled
    }

    fun disableSpeaker() {
        _isSpeakerOn.value = false
    }

    fun cleanup() {
        _callState.value = null
        _isMicrophoneMuted.value = false
        _isCameraEnabled.value = true
        _isSpeakerOn.value = true
    }

    fun destroy() {
        cleanup()
        _isInitialized.value = false
    }
}
