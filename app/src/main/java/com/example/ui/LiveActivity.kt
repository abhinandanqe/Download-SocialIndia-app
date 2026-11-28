package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.model.LivePresenceManager
import com.example.model.LiveSessionInfo
import com.example.model.RepositoryManager
import com.example.repository.LiveGiftModel
import com.example.ui.components.RealtimeLiveGiftOverlay
import com.example.utils.GlobalMediaSessionManager
import com.example.utils.MediaSource
import com.example.utils.NumberFormatUtils
import com.example.zego.DynamicZegoLiveConfig
import com.example.zego.LiveEligibilityResult
import com.example.zego.SocialIndiaZegoConfig
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.zegocloud.uikit.prebuilt.livestreaming.ZegoUIKitPrebuiltLiveStreamingConfig
import com.zegocloud.uikit.prebuilt.livestreaming.ZegoUIKitPrebuiltLiveStreamingFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.random.Random

private enum class LiveActivityStage {
    LOADING,
    COMING_SOON_PAUSED,
    ELIGIBILITY_LOCKED,
    HOST_PRE_LIVE_STUDIO,
    LIVE_BROADCASTING
}

class LiveActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LiveActivity"
    }

    private val zegoContainerId: Int by lazy { View.generateViewId() }
    private lateinit var zegoContainer: FrameLayout

    private var liveId: String = ""
    private var isHost: Boolean = false
    private var rawUserId: String = ""
    private var userName: String = "Creator"
    private var hostUid: String = ""
    private var hostUsername: String = "Live Creator"
    private var hostAvatar: String = ""

    private var stageState = mutableStateOf(LiveActivityStage.LOADING)
    private var configState = mutableStateOf(SocialIndiaZegoConfig.liveConfig.value)
    private var eligibilityState = mutableStateOf<LiveEligibilityResult?>(null)
    private var activeLiveTitleState = mutableStateOf("Live Stream 🔴")
    private var activeCategoryState = mutableStateOf("Just Chatting")
    private var liveStartedAtMsState = mutableStateOf(0L)
    private var liveViewersCountState = mutableStateOf(1)
    private var liveLikesCountState = mutableStateOf(0L)
    private var hostIsVerifiedState = mutableStateOf(false)

    private var heartbeatJob: Job? = null
    private var sessionListener: ListenerRegistration? = null
    private var hasBroadcastStarted = false
    private var hasEndedCleanly = false
    private var hasIncrementedViewer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!RepositoryManager.isInitialized()) {
            RepositoryManager.init(applicationContext)
        }
        SocialIndiaZegoConfig.startObserving()
        GlobalMediaSessionManager.requestFocus(MediaSource.LIVE, this)

        val currentUid = RepositoryManager.getCurrentUserId()
        isHost = intent.getBooleanExtra("isHost", false)
        rawUserId = intent.getStringExtra("userID")?.ifBlank { currentUid } ?: currentUid
        if (rawUserId.isBlank()) rawUserId = "guest_${System.currentTimeMillis() % 100000}"

        userName = intent.getStringExtra("userName")?.ifBlank {
            RepositoryManager.currentUserState.value?.username?.ifBlank { "Creator" } ?: "Creator"
        } ?: "Creator"

        hostUid = intent.getStringExtra("hostUid")?.ifBlank { if (isHost) currentUid else "" } ?: ""
        hostUsername = intent.getStringExtra("hostUsername")?.ifBlank { if (isHost) userName else "Live Creator" } ?: "Live Creator"
        hostAvatar = intent.getStringExtra("hostAvatar") ?: ""
        liveId = intent.getStringExtra("liveID")?.ifBlank {
            "live_${currentUid.ifBlank { "host" }}_${System.currentTimeMillis()}"
        } ?: "live_${System.currentTimeMillis()}"

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        zegoContainer = FrameLayout(this).apply {
            id = zegoContainerId
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(zegoContainer)

        val composeOverlay = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setContent {
                MaterialTheme {
                    LiveActivityComposeRoot(
                        stage = stageState.value,
                        config = configState.value,
                        eligibility = eligibilityState.value,
                        isHost = isHost,
                        liveId = liveId,
                        hostUid = hostUid,
                        hostUsername = hostUsername,
                        hostAvatar = hostAvatar,
                        hostIsVerified = hostIsVerifiedState.value,
                        liveTitle = activeLiveTitleState.value,
                        liveCategory = activeCategoryState.value,
                        liveStartedAtMs = liveStartedAtMsState.value,
                        viewersCount = liveViewersCountState.value,
                        likesCount = liveLikesCountState.value,
                        onBack = { endSessionAndFinish() },
                        onGoLiveConfirmed = { title, category ->
                            startRealPublicBroadcast(title, category)
                        },
                        onSendReaction = { incrementLiveLikes() }
                    )
                }
            }
        }
        root.addView(composeOverlay)
        setContentView(root)

        bootstrapLiveSession()
    }

    private fun bootstrapLiveSession() {
        lifecycleScope.launch {
            try {
                val cfg = SocialIndiaZegoConfig.fetchLatestConfig()
                configState.value = cfg

                // Enrich host metadata if missing
                val targetHostUid = hostUid.ifBlank { if (isHost) RepositoryManager.getCurrentUserId() else "" }
                if (targetHostUid.isNotBlank()) {
                    try {
                        val hostDoc = RepositoryManager.firestore.collection("users").document(targetHostUid).get().await()
                        if (hostDoc.exists()) {
                            if (hostUsername.isBlank() || hostUsername == "Live Creator") {
                                hostUsername = hostDoc.getString("username") ?: hostUsername
                            }
                            if (hostAvatar.isBlank()) {
                                hostAvatar = hostDoc.getString("avatarUrl") ?: hostDoc.getString("profilePicUrl") ?: ""
                            }
                            val vStatus = hostDoc.getString("verificationStatus") ?: ""
                            val vSuspended = hostDoc.getBoolean("verificationSuspended") ?: false
                            hostIsVerifiedState.value = !vSuspended && (
                                (hostDoc.getBoolean("isVerified") ?: false) ||
                                    vStatus == "approved" ||
                                    vStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE
                            )
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Exception) {}
                }

                if (!cfg.isLiveEnabled) {
                    stageState.value = LiveActivityStage.COMING_SOON_PAUSED
                    return@launch
                }

                if (isHost) {
                    val uid = RepositoryManager.getCurrentUserId()
                    val eligibility = SocialIndiaZegoConfig.checkUserLiveEligibility(uid)
                    eligibilityState.value = eligibility
                    if (!eligibility.isAllowed) {
                        stageState.value = LiveActivityStage.ELIGIBILITY_LOCKED
                    } else {
                        stageState.value = LiveActivityStage.HOST_PRE_LIVE_STUDIO
                    }
                } else {
                    // Audience joins existing real public ZegoCloud live room immediately
                    joinRealPublicBroadcastAsViewer(cfg)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "bootstrapLiveSession error", e)
                Toast.makeText(this@LiveActivity, "Live Studio error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startRealPublicBroadcast(title: String, category: String) {
        if (hasBroadcastStarted) return
        val cfg = configState.value
        val currentUid = RepositoryManager.getCurrentUserId()
        if (currentUid.isBlank()) {
            Toast.makeText(this, "Please sign in to Go Live", Toast.LENGTH_SHORT).show()
            return
        }

        hasBroadcastStarted = true
        val cleanedTitle = title.trim().ifBlank { "$hostUsername is Live 🔴" }
        activeLiveTitleState.value = cleanedTitle
        activeCategoryState.value = category
        val nowMs = System.currentTimeMillis()
        liveStartedAtMsState.value = nowMs
        liveViewersCountState.value = 1

        // 1. Mount real ZEGO Cloud Live Streaming Fragment using Admin's dynamic AppID & AppSign
        mountZegoLiveFragment(cfg = cfg, asHost = true)
        stageState.value = LiveActivityStage.LIVE_BROADCASTING

        // 2. Publish real-time public liveSession in Firestore + register presence + notify followers ONLY NOW
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = RepositoryManager.firestore
                val sessionPayload = hashMapOf(
                    "liveId" to liveId,
                    "hostUid" to currentUid,
                    "userId" to currentUid,
                    "hostUsername" to hostUsername,
                    "username" to hostUsername,
                    "hostAvatar" to hostAvatar,
                    "avatarUrl" to hostAvatar,
                    "title" to cleanedTitle,
                    "category" to category,
                    "status" to "LIVE",
                    "viewerCount" to 1L,
                    "peakViewers" to 1L,
                    "likesCount" to 0L,
                    "isVerified" to hostIsVerifiedState.value,
                    "streamQuality" to cfg.streamQuality,
                    "startedAt" to FieldValue.serverTimestamp(),
                    "startedAtMs" to nowMs,
                    "lastHeartbeatMs" to nowMs
                )
                db.collection("liveSessions").document(liveId).set(sessionPayload, SetOptions.merge()).await()
                db.collection("users").document(currentUid)
                    .set(mapOf("isLive" to true, "currentLiveId" to liveId), SetOptions.merge())
                    .await()

                LivePresenceManager.registerActiveLive(
                    LiveSessionInfo(
                        storyId = liveId,
                        userId = currentUid,
                        username = hostUsername,
                        avatarUrl = hostAvatar,
                        title = cleanedTitle,
                        category = category,
                        viewersCount = 1,
                        peakViewers = 1,
                        isVerified = hostIsVerifiedState.value,
                        isHost = true,
                        startTime = nowMs,
                        lastHeartbeatMs = nowMs
                    )
                )

                // Notify followers ONLY when real broadcast actually begins
                notifyFollowersOfPublicLive(currentUid, hostUsername, hostAvatar, liveId, cleanedTitle)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Failed publishing liveSession", e)
            }
        }

        // 3. Observe live session metadata + start 25-second Host Heartbeat
        attachLiveSessionObserver()
        startHostHeartbeat()
    }

    private fun joinRealPublicBroadcastAsViewer(cfg: DynamicZegoLiveConfig) {
        liveStartedAtMsState.value = System.currentTimeMillis()
        mountZegoLiveFragment(cfg = cfg, asHost = false)
        stageState.value = LiveActivityStage.LIVE_BROADCASTING
        attachLiveSessionObserver()

        if (!hasIncrementedViewer && liveId.isNotBlank()) {
            hasIncrementedViewer = true
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    RepositoryManager.firestore.collection("liveSessions")
                        .document(liveId)
                        .update("viewerCount", FieldValue.increment(1))
                        .await()
                } catch (_: Exception) {}
            }
        }
    }

    private fun mountZegoLiveFragment(cfg: DynamicZegoLiveConfig, asHost: Boolean) {
        try {
            val zegoConfig = if (asHost) {
                ZegoUIKitPrebuiltLiveStreamingConfig.host(cfg.allowCoHosting).apply {
                    turnOnCameraWhenJoining = true
                    turnOnMicrophoneWhenJoining = true
                    useSpeakerWhenJoining = true
                }
            } else {
                ZegoUIKitPrebuiltLiveStreamingConfig.audience(cfg.allowCoHosting).apply {
                    useSpeakerWhenJoining = true
                }
            }

            val zegoUid = SocialIndiaZegoConfig.mapToZegoUserId(rawUserId)
            val fragment = ZegoUIKitPrebuiltLiveStreamingFragment.newInstance(
                cfg.appId,
                cfg.appSign,
                zegoUid,
                userName.ifBlank { "User" },
                liveId,
                zegoConfig
            )

            supportFragmentManager.beginTransaction()
                .replace(zegoContainerId, fragment, "zego_live_fragment")
                .commitAllowingStateLoss()
        } catch (e: Exception) {
            Log.e(TAG, "Failed mounting ZegoUIKitPrebuiltLiveStreamingFragment", e)
            Toast.makeText(
                this,
                "Could not initialize ZEGO Live (AppID ${cfg.appId}): ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun attachLiveSessionObserver() {
        sessionListener?.remove()
        if (liveId.isBlank()) return
        sessionListener = RepositoryManager.firestore.collection("liveSessions")
            .document(liveId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
                val status = snap.getString("status") ?: "LIVE"
                val viewers = (snap.getLong("viewerCount") ?: 1L).toInt().coerceAtLeast(1)
                val likes = snap.getLong("likesCount") ?: 0L
                val title = snap.getString("title")
                val category = snap.getString("category")
                val startedMs = snap.getLong("startedAtMs") ?: liveStartedAtMsState.value
                val hUid = snap.getString("hostUid")
                val hName = snap.getString("hostUsername")
                val hAv = snap.getString("hostAvatar")

                liveViewersCountState.value = viewers
                liveLikesCountState.value = likes
                if (!title.isNullOrBlank()) activeLiveTitleState.value = title
                if (!category.isNullOrBlank()) activeCategoryState.value = category
                if (startedMs > 0L) liveStartedAtMsState.value = startedMs
                if (!hUid.isNullOrBlank() && hostUid.isBlank()) hostUid = hUid
                if (!hName.isNullOrBlank()) hostUsername = hName
                if (!hAv.isNullOrBlank()) hostAvatar = hAv

                if (!isHost && status == "ENDED") {
                    Toast.makeText(this, "@$hostUsername ended the Live stream", Toast.LENGTH_SHORT).show()
                    endSessionAndFinish()
                }
            }
    }

    private fun startHostHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive && hasBroadcastStarted && !hasEndedCleanly) {
                delay(25_000L)
                try {
                    RepositoryManager.firestore.collection("liveSessions")
                        .document(liveId)
                        .set(
                            mapOf(
                                "lastHeartbeatMs" to System.currentTimeMillis(),
                                "status" to "LIVE"
                            ),
                            SetOptions.merge()
                        )
                        .await()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {}
            }
        }
    }

    private fun incrementLiveLikes() {
        if (liveId.isBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                RepositoryManager.firestore.collection("liveSessions")
                    .document(liveId)
                    .update("likesCount", FieldValue.increment(1))
            } catch (_: Exception) {}
        }
    }

    private suspend fun notifyFollowersOfPublicLive(
        hostId: String,
        hostName: String,
        hostPic: String,
        sessionLiveId: String,
        streamTitle: String
    ) {
        try {
            val db = RepositoryManager.firestore
            val followersSnap = db.collection("users").document(hostId)
                .collection("followers")
                .limit(100)
                .get()
                .await()
            val batch = db.batch()
            var count = 0
            for (doc in followersSnap.documents) {
                val followerId = doc.id
                if (followerId == hostId || followerId.isBlank()) continue
                val notifRef = db.collection("notifications").document()
                batch.set(
                    notifRef,
                    mapOf(
                        "userId" to followerId,
                        "senderId" to hostId,
                        "senderUsername" to hostName,
                        "senderAvatar" to hostPic,
                        "type" to "live_started",
                        "message" to "is LIVE now: $streamTitle 🔴",
                        "relatedId" to sessionLiveId,
                        "isRead" to false,
                        "timestamp" to FieldValue.serverTimestamp()
                    )
                )
                count++
            }
            if (count > 0) batch.commit().await()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "notifyFollowersOfPublicLive skipped: ${e.message}")
        }
    }

    private fun performTeardownCleanup() {
        if (hasEndedCleanly) return
        hasEndedCleanly = true
        heartbeatJob?.cancel()
        sessionListener?.remove()
        sessionListener = null

        val elapsedMinutes = if (liveStartedAtMsState.value > 0L) {
            ((System.currentTimeMillis() - liveStartedAtMsState.value) / 60_000.0).coerceAtLeast(0.0)
        } else 0.0

        if (isHost && hasBroadcastStarted) {
            val uid = RepositoryManager.getCurrentUserId().ifBlank { hostUid }
            LivePresenceManager.endLive(liveId, uid)
            if (elapsedMinutes >= 0.1) {
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    SocialIndiaZegoConfig.recordLiveSessionMinutes(elapsedMinutes)
                }
            }
        } else if (!isHost && hasIncrementedViewer && liveId.isNotBlank()) {
            hasIncrementedViewer = false
            RepositoryManager.firestore.collection("liveSessions")
                .document(liveId)
                .update("viewerCount", FieldValue.increment(-1))
        }

        GlobalMediaSessionManager.releaseFocus(MediaSource.LIVE)
    }

    private fun endSessionAndFinish() {
        performTeardownCleanup()
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        performTeardownCleanup()
        super.onDestroy()
    }
}

@Composable
private fun LiveActivityComposeRoot(
    stage: LiveActivityStage,
    config: DynamicZegoLiveConfig,
    eligibility: LiveEligibilityResult?,
    isHost: Boolean,
    liveId: String,
    hostUid: String,
    hostUsername: String,
    hostAvatar: String,
    hostIsVerified: Boolean,
    liveTitle: String,
    liveCategory: String,
    liveStartedAtMs: Long,
    viewersCount: Int,
    likesCount: Long,
    onBack: () -> Unit,
    onGoLiveConfirmed: (String, String) -> Unit,
    onSendReaction: () -> Unit
) {
    when (stage) {
        LiveActivityStage.LOADING -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A14)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(color = Color(0xFFFF2A6D))
                    Text(
                        text = "Connecting to Social India Live Studio...",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        LiveActivityStage.COMING_SOON_PAUSED -> {
            LiveStreamingComingSoonScreen(
                config = config,
                onBack = onBack
            )
        }

        LiveActivityStage.ELIGIBILITY_LOCKED -> {
            LiveEligibilityLockedScreen(
                eligibility = eligibility ?: LiveEligibilityResult(
                    isAllowed = false,
                    isLiveSystemEnabled = true,
                    currentFollowers = 0,
                    requiredFollowers = config.minFollowersToGoLive
                ),
                config = config,
                onBack = onBack
            )
        }

        LiveActivityStage.HOST_PRE_LIVE_STUDIO -> {
            HostPreLiveStudioScreen(
                config = config,
                hostUsername = hostUsername,
                hostAvatar = hostAvatar,
                onBack = onBack,
                onStartBroadcast = onGoLiveConfirmed
            )
        }

        LiveActivityStage.LIVE_BROADCASTING -> {
            ActiveLiveStreamingOverlay(
                config = config,
                isHost = isHost,
                liveId = liveId,
                hostUid = hostUid,
                hostUsername = hostUsername,
                hostAvatar = hostAvatar,
                hostIsVerified = hostIsVerified,
                liveTitle = liveTitle,
                liveCategory = liveCategory,
                liveStartedAtMs = liveStartedAtMs,
                viewersCount = viewersCount,
                likesCount = likesCount,
                onEndOrLeave = onBack,
                onSendReaction = onSendReaction
            )
        }
    }
}

@Composable
private fun LiveEligibilityLockedScreen(
    eligibility: LiveEligibilityResult,
    config: DynamicZegoLiveConfig,
    onBack: () -> Unit
) {
    val required = eligibility.requiredFollowers.coerceAtLeast(1)
    val current = eligibility.currentFollowers.coerceAtLeast(0)
    val remaining = (required - current).coerceAtLeast(0)
    val targetProgress = (current.toFloat() / required.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "eligibility_progress"
    )
    val isAdminRestricted = eligibility.blockReason.contains("restricted", ignoreCase = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A0826), Color(0xFF0A0B14), Color(0xFF0F172A))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .testTag("live_locked_close_button")
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Glowing Lock + Crown Emblem
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF2A6D), Color(0xFFFF9100), Color(0xFF7B2FFF))
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAdminRestricted) Icons.Default.Block else Icons.Default.Lock,
                    contentDescription = "Live Locked",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFFFF2A6D).copy(alpha = 0.18f),
                border = BorderStroke(1.dp, Color(0xFFFF2A6D).copy(alpha = 0.6f))
            ) {
                Text(
                    text = if (isAdminRestricted) "LIVE ACCESS RESTRICTED" else "CREATOR MILESTONE LOCKED",
                    color = Color(0xFFFF80AB),
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Text(
                text = if (isAdminRestricted)
                    "Live Access Paused for Your Account"
                else
                    "Unlock ${config.previewBannerTitle}",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 23.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isAdminRestricted) {
                    eligibility.blockReason
                } else {
                    "Reach $required followers to Go Live publicly in HD, earn real-time 3D Gifts & host multi-guest sessions!"
                },
                color = Color(0xFFB8C0D8),
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )

            if (!isAdminRestricted) {
                // Progress Card
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF15182B),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Your Follower Progress",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${NumberFormatUtils.formatCount(current)} / ${NumberFormatUtils.formatCount(required)}",
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(50)),
                            color = Color(0xFFFF2A6D),
                            trackColor = Color.White.copy(alpha = 0.12f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${(targetProgress * 100).toInt()}% unlocked",
                                color = Color(0xFFFF80AB),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Need $remaining more followers",
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Creator Growth Tips Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF121524),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "How to Reach $required Followers Faster",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        listOf(
                            "🎬 Post trending Reels using Beat Templates & real music",
                            "📸 Share daily Stories to stay on top of Home Feed",
                            "💬 Reply to comments & collaborate with creators"
                        ).forEach { tip ->
                            Text(
                                text = tip,
                                color = Color(0xFFD2D8EA),
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onBack,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A6D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("live_locked_got_it_button")
            ) {
                Text(
                    text = "Keep Growing & Come Back",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.5.sp
                )
            }
        }
    }
}

@Composable
private fun HostPreLiveStudioScreen(
    config: DynamicZegoLiveConfig,
    hostUsername: String,
    hostAvatar: String,
    onBack: () -> Unit,
    onStartBroadcast: (String, String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermissions = (result[Manifest.permission.CAMERA] == true) &&
            (result[Manifest.permission.RECORD_AUDIO] == true)
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    var useFrontCamera by remember { mutableStateOf(true) }
    var liveTitle by remember { mutableStateOf("") }
    val categories = remember {
        listOf("Just Chatting", "Music 🎵", "Gaming 🎮", "Comedy 😂", "Dance 💃", "Q&A 💬")
    }
    var selectedCategory by remember { mutableStateOf(categories.first()) }
    var isStarting by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Real CameraX Backdrop Preview
        if (hasPermissions) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(context))
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF1B0A2E), Color(0xFF0A0D1A)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    Text("Camera & Mic permission required to Go Live", color = Color.White, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = { permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }
                    ) {
                        Text("Grant Camera & Mic")
                    }
                }
            }
        }

        // Cinema Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Top Bar: Close + Admin Preview Banner Badge + Flip Camera
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Live Studio", tint = Color.White)
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, Color(0xFFFF2A6D).copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF2A6D)))
                        Text(
                            text = "${config.streamQuality.replace("_", " ")} • LIVE STUDIO",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = { useFrontCamera = !useFrontCamera },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White)
                }
            }

            // Admin Custom Live Preview Banner Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFFFF2A6D), Color(0xFF7B2FFF)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val thumb = config.previewBannerImageUrl.ifBlank { hostAvatar }
                        if (thumb.isNotBlank()) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = "Live Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = config.previewBannerTitle.ifBlank { "Social India Live Studio" },
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = config.previewBannerSubtitle,
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 11.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Bottom Setup Sheet: Title + Category + Feature Badges + GO LIVE CTA
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = liveTitle,
                onValueChange = { liveTitle = it },
                placeholder = { Text("Add a catchy Live title...", color = Color.White.copy(alpha = 0.65f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Black.copy(alpha = 0.55f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.45f),
                    focusedBorderColor = Color(0xFFFF2A6D),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pre_live_title_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val selected = cat == selectedCategory
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) Color(0xFFFF2A6D) else Color.Black.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.22f)),
                        modifier = Modifier.clickable { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (config.allowLiveGifts) {
                    StudioCapabilityChip("🎁 3D Gifts ON")
                }
                if (config.allowCoHosting) {
                    StudioCapabilityChip("🎙️ Co-Host Ready")
                }
                StudioCapabilityChip("🌍 Public Broadcast")
            }

            Button(
                onClick = {
                    if (!hasPermissions) {
                        permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                        return@Button
                    }
                    if (isStarting) return@Button
                    isStarting = true
                    onStartBroadcast(liveTitle, selectedCategory)
                },
                enabled = !isStarting,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("go_live_publicly_button")
            ) {
                if (isStarting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Sensors, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GO LIVE PUBLICLY",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioCapabilityChip(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ActiveLiveStreamingOverlay(
    config: DynamicZegoLiveConfig,
    isHost: Boolean,
    liveId: String,
    hostUid: String,
    hostUsername: String,
    hostAvatar: String,
    hostIsVerified: Boolean,
    liveTitle: String,
    liveCategory: String,
    liveStartedAtMs: Long,
    viewersCount: Int,
    likesCount: Long,
    onEndOrLeave: () -> Unit,
    onSendReaction: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUid = remember { RepositoryManager.getCurrentUserId() }

    var elapsedSeconds by remember { mutableStateOf(0L) }
    var isFollowingHost by remember { mutableStateOf(false) }
    var showGiftSheet by remember { mutableStateOf(false) }
    var userCoinsBalance by remember { mutableStateOf(0.0) }
    val particles = remember { mutableStateListOf<FloatingParticle>() }
    var showEndConfirmDialog by remember { mutableStateOf(false) }

    // Duration clock
    LaunchedEffect(liveStartedAtMs) {
        while (true) {
            val base = if (liveStartedAtMs > 0L) liveStartedAtMs else System.currentTimeMillis()
            elapsedSeconds = ((System.currentTimeMillis() - base) / 1000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }

    // Load wallet balance & follow status
    LaunchedEffect(currentUid, hostUid) {
        if (currentUid.isNotBlank()) {
            try {
                val uDoc = RepositoryManager.firestore.collection("users").document(currentUid).get().await()
                val coins = uDoc.getLong("coins") ?: 0L
                val cash = uDoc.getDouble("walletBalance") ?: 0.0
                userCoinsBalance = coins.toDouble() + cash
                if (!isHost && hostUid.isNotBlank() && hostUid != currentUid) {
                    val fDoc = RepositoryManager.firestore.collection("users").document(currentUid)
                        .collection("following").document(hostUid).get().await()
                    isFollowingHost = fDoc.exists()
                }
            } catch (_: Exception) {}
        }
    }

    fun emitParticle(emoji: String) {
        val p = FloatingParticle(
            id = System.nanoTime() + Random.nextLong(1000),
            emoji = emoji,
            startXOffset = Random.nextFloat() * 48f - 24f,
            swayAmount = Random.nextFloat() * 24f + 12f,
            targetScale = 1.25f,
            durationMs = 2100
        )
        particles.add(p)
        if (particles.size > 28) particles.removeAt(0)
        onSendReaction()
    }

    val pulse = rememberInfiniteTransition(label = "live_dot_pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "live_dot_alpha"
    )

    val mins = elapsedSeconds / 60
    val secs = elapsedSeconds % 60
    val formattedTimer = String.format("%02d:%02d", mins, secs)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Floating reactions layer (non-blocking)
        LiveFloatingReactionsEngine(
            particles = particles,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 120.dp)
                .size(width = 140.dp, height = 380.dp)
        )

        // Real-time 3D Gift celebration overlay
        if (config.allowLiveGifts) {
            RealtimeLiveGiftOverlay(liveStoryId = liveId)
        }

        // Top HUD: Host Pill + Live Timer + Viewer Count + End/Leave Button
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Host Info Glass Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier.padding(start = 5.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFFFF2A6D), CircleShape)
                            .background(Color(0xFF22243A)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hostAvatar.isNotBlank()) {
                            AsyncImage(
                                model = hostAvatar,
                                contentDescription = hostUsername,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = hostUsername,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp)
                            )
                            if (hostIsVerified) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF1744).copy(alpha = dotAlpha))
                            )
                            Text(
                                text = "LIVE $formattedTimer • $liveCategory",
                                color = Color(0xFFFFD1DC),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    if (!isHost && hostUid.isNotBlank() && hostUid != currentUid && !isFollowingHost) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFFF2A6D),
                            modifier = Modifier.clickable {
                                isFollowingHost = true
                                scope.launch {
                                    try {
                                        RepositoryManager.socialRepository.followUser(currentUid, hostUid)
                                        Toast.makeText(context, "Followed @$hostUsername!", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {}
                                }
                            }
                        ) {
                            Text(
                                text = "+ Follow",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Right HUD: Viewer Count + Likes + Exit
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "Viewers", tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(
                            text = NumberFormatUtils.formatCount(viewersCount),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (isHost) showEndConfirmDialog = true else onEndOrLeave()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF1744).copy(alpha = 0.85f))
                        .testTag("live_overlay_exit_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "End Live", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Right-Edge Quick Reactions & 3D Gifts Rail
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.48f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier
                    .size(46.dp)
                    .clickable { emitParticle("❤️") }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("❤️", fontSize = 18.sp)
                    if (likesCount > 0) {
                        Text(
                            text = NumberFormatUtils.formatCount(likesCount.toInt()),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.48f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier
                    .size(44.dp)
                    .clickable { emitParticle("🔥") }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🔥", fontSize = 19.sp)
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.48f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier
                    .size(44.dp)
                    .clickable { emitParticle("👏") }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("👏", fontSize = 19.sp)
                }
            }

            if (config.allowLiveGifts && !isHost) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFF2A6D),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD700)),
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { showGiftSheet = true }
                        .testTag("live_send_gift_fab")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = "Send Live Gift", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }

    if (showGiftSheet) {
        LiveGiftBottomSheet(
            walletBalance = userCoinsBalance,
            onDismiss = { showGiftSheet = false },
            onGiftSelected = { selectedGift ->
                showGiftSheet = false
                scope.launch {
                    val user = RepositoryManager.currentUserState.value
                    val giftModel = LiveGiftModel(
                        id = selectedGift.id,
                        name = selectedGift.name,
                        emoji = selectedGift.emoji,
                        coins = selectedGift.coins,
                        sticker3dUrl = selectedGift.sticker3dUrl
                    )
                    val res = RepositoryManager.giftRepository.sendGift(
                        senderId = currentUid,
                        senderUsername = user?.username ?: "Fan",
                        senderAvatar = user?.avatarUrl ?: "",
                        isSenderVerified = user?.isVerified ?: false,
                        streamerId = hostUid,
                        liveStoryId = liveId,
                        gift = giftModel
                    )
                    if (res.isSuccess) {
                        userCoinsBalance = (userCoinsBalance - selectedGift.coins).coerceAtLeast(0.0)
                        emitParticle(selectedGift.emoji)
                        Toast.makeText(context, "Sent ${selectedGift.name} ${selectedGift.emoji}!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, res.exceptionOrNull()?.message ?: "Insufficient coins", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showEndConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEndConfirmDialog = false },
            title = { Text("End Live Broadcast?", fontWeight = FontWeight.Bold) },
            text = { Text("Your live stream will end for all $viewersCount viewers and your session minutes will be saved.") },
            confirmButton = {
                Button(
                    onClick = {
                        showEndConfirmDialog = false
                        onEndOrLeave()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744))
                ) {
                    Text("End Live")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirmDialog = false }) {
                    Text("Keep Streaming")
                }
            }
        )
    }
}

@Composable
fun LiveStreamingComingSoonScreen(
    config: DynamicZegoLiveConfig = SocialIndiaZegoConfig.liveConfig.value,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }
    var isNotifyEnabled by remember { mutableStateOf(false) }
    var isSavingNotify by remember { mutableStateOf(false) }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            try {
                val doc = RepositoryManager.firestore
                    .collection("users")
                    .document(currentUserId)
                    .get()
                    .await()
                isNotifyEnabled = doc.getBoolean("notifyLiveLaunch") ?: false
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {}
        }
    }

    val pulse = rememberInfiniteTransition(label = "live_soon_pulse")
    val badgeAlpha by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "live_soon_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF140822), Color(0xFF090912), Color(0xFF0D1426))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .testTag("live_coming_soon_close")
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close Live Coming Soon", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFFFF2A6D).copy(alpha = badgeAlpha * 0.22f),
                border = BorderStroke(1.5.dp, Color(0xFFFF2A6D))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF2A6D)))
                    Text(
                        text = "LIVE STUDIO • PAUSED BY ADMIN",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.5.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            if (config.previewBannerImageUrl.isNotBlank()) {
                AsyncImage(
                    model = config.previewBannerImageUrl,
                    contentDescription = "Live Preview Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFF2A6D), Color(0xFF7B2FFF), Color(0xFF00F0FF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = "Live Streaming",
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Text(
                text = config.previewBannerTitle.ifBlank { "Social India Live Studio" },
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 23.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = config.previewBannerSubtitle,
                color = Color(0xFFB8B8D0),
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = {
                    if (isSavingNotify) return@Button
                    isSavingNotify = true
                    val nextVal = !isNotifyEnabled
                    scope.launch {
                        try {
                            if (currentUserId.isNotBlank()) {
                                RepositoryManager.firestore.collection("users")
                                    .document(currentUserId)
                                    .set(mapOf("notifyLiveLaunch" to nextVal), SetOptions.merge())
                                    .await()
                            }
                            isNotifyEnabled = nextVal
                            Toast.makeText(
                                context,
                                if (nextVal) "🔔 Reminder enabled! We'll alert you when Live resumes." else "Reminder turned off",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            isNotifyEnabled = nextVal
                        } finally {
                            isSavingNotify = false
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isNotifyEnabled) Color(0xFF10B981) else Color(0xFFFF2A6D)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("live_notify_button")
            ) {
                Icon(
                    imageVector = if (isNotifyEnabled) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isNotifyEnabled) "Personal Reminder Enabled ✓" else "Notify Me When Live Resumes",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }

            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("live_back_button")
            ) {
                Text("Back to Social India", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
