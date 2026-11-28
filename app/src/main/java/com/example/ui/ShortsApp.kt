package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.RepositoryManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import com.example.auth.MultiAccountManager
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.utils.bounceClick
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import coil.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.tasks.await
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.StoryUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class CallState(
    val isVideo: Boolean,
    val peerId: String,
    val peerUsername: String,
    val peerDisplayName: String = "",
    val peerAvatarUrl: String = "",
    val isOutgoing: Boolean,
    val status: String, // "calling", "ringing", "connected", "declined", "ended"
    val sdpOffer: String? = null,
    val sdpAnswer: String? = null,
    val candidates: List<String> = emptyList()
)

object GlobalCallManager {
    val activeCallState = MutableStateFlow<CallState?>(null)
    val heartEventFlow = kotlinx.coroutines.flow.MutableSharedFlow<Long>(extraBufferCapacity = 16)
    
    fun sendHeart() {
        val current = activeCallState.value ?: return
        com.example.model.RepositoryManager.socialRepository.sendCallSignaling(
            receiverId = current.peerId,
            type = "heart",
            data = mapOf("timestamp" to System.currentTimeMillis())
        )
        heartEventFlow.tryEmit(System.currentTimeMillis())
    }
    
    fun initiateCall(
        context: android.content.Context,
        peerId: String,
        peerUsername: String,
        peerDisplayName: String = "",
        peerAvatarUrl: String = "",
        isVideo: Boolean = true
    ) {
        val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
        val sanitizedCurrent = currentUserId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val sanitizedPeer = peerId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val roomID = if (sanitizedCurrent < sanitizedPeer) "${sanitizedCurrent}_${sanitizedPeer}" else "${sanitizedPeer}_${sanitizedCurrent}"
        
        val activeAcc = com.example.auth.MultiAccountManager.activeAccount.value
        val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val myUsername: String = activeAcc?.username?.ifEmpty { null }
            ?: activeAcc?.displayName?.ifEmpty { null }
            ?: fbUser?.displayName?.ifEmpty { null }
            ?: "User"
        val myDisplayName: String = activeAcc?.displayName?.ifEmpty { null } ?: myUsername
        val myAvatarUrl: String = activeAcc?.avatarUrl ?: fbUser?.photoUrl?.toString() ?: ""

        val state = CallState(
            isVideo = isVideo,
            peerId = peerId,
            peerUsername = peerUsername,
            peerDisplayName = peerDisplayName.ifEmpty { peerUsername },
            peerAvatarUrl = peerAvatarUrl,
            isOutgoing = true,
            status = "calling",
            sdpOffer = roomID
        )
        activeCallState.value = state
        
        // Start outgoing ringtone
        com.example.utils.SoundManager.startRingtone(context, isOutgoing = true)
        
        // Send signal via Firebase
        com.example.model.RepositoryManager.socialRepository.sendCallSignaling(
            receiverId = peerId,
            type = "offer",
            data = mapOf(
                "mediaType" to if (isVideo) "video" else "audio",
                "senderUsername" to myUsername,
                "senderDisplayName" to myDisplayName,
                "senderAvatarUrl" to myAvatarUrl,
                "roomID" to roomID,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }

    fun acceptCall(context: android.content.Context) {
        val current = activeCallState.value ?: return
        com.example.utils.SoundManager.stopRingtone()
        activeCallState.value = current.copy(status = "connected")
        
        val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
        val activeAcc = com.example.auth.MultiAccountManager.activeAccount.value
        val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val myUsername: String = activeAcc?.username?.ifEmpty { null }
            ?: activeAcc?.displayName?.ifEmpty { null }
            ?: fbUser?.displayName?.ifEmpty { null }
            ?: "User"
        val roomID = current.sdpOffer ?: (if (currentUserId < current.peerId) "${currentUserId}_${current.peerId}" else "${current.peerId}_${currentUserId}").replace(Regex("[^a-zA-Z0-9_]"), "_")
        
        // Signal caller that call was accepted
        com.example.model.RepositoryManager.socialRepository.sendCallSignaling(
            receiverId = current.peerId,
            type = "answer",
            data = mapOf(
                "roomID" to roomID,
                "timestamp" to System.currentTimeMillis()
            )
        )
        
        // Launch Native Social India CallActivity safely
        try {
            val intent = android.content.Intent(context, CallActivity::class.java).apply {
                putExtra("userID", currentUserId.ifBlank { "user_${System.currentTimeMillis()}" })
                putExtra("userName", myUsername)
                putExtra("callID", roomID)
                putExtra("isVideoCall", current.isVideo)
                putExtra("peerId", current.peerId)
                putExtra("peerName", current.peerDisplayName.ifEmpty { current.peerUsername })
                putExtra("peerAvatar", current.peerAvatarUrl)
                putExtra("isOutgoing", false)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("GlobalCallManager", "Failed to launch CallActivity", e)
        }
        
        activeCallState.value = null
    }

    fun declineCall() {
        val current = activeCallState.value ?: return
        com.example.utils.SoundManager.stopRingtone()
        activeCallState.value = current.copy(status = "declined")
        
        com.example.model.RepositoryManager.socialRepository.sendCallSignaling(
            receiverId = current.peerId,
            type = "decline",
            data = mapOf("timestamp" to System.currentTimeMillis())
        )
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            activeCallState.value = null
            val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
            com.example.model.RepositoryManager.socialRepository.clearCallSignaling(currentUserId)
        }, 1200)
    }

    fun endCall() {
        val current = activeCallState.value ?: return
        com.example.utils.SoundManager.stopRingtone()
        activeCallState.value = current.copy(status = "ended")
        
        com.example.model.RepositoryManager.socialRepository.sendCallSignaling(
            receiverId = current.peerId,
            type = "end",
            data = mapOf("timestamp" to System.currentTimeMillis())
        )
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            activeCallState.value = null
            val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
            com.example.model.RepositoryManager.socialRepository.clearCallSignaling(currentUserId)
        }, 1000)
    }
}

private fun androidx.navigation.NavHostController.safeNavigate(
    route: String,
    builder: androidx.navigation.NavOptionsBuilder.() -> Unit = {}
) {
    if (route.isBlank()) return
    try {
        navigate(route, builder)
    } catch (e: Exception) {
        android.util.Log.e("ShortsApp", "Safe navigation blocked for '$route'", e)
    }
}

private fun androidx.navigation.NavHostController.safePopBackStack() {
    try {
        popBackStack()
    } catch (e: Exception) {
        android.util.Log.e("ShortsApp", "Safe popBackStack failed", e)
    }
}

private fun androidx.navigation.NavHostController.safeStartDestinationId(): Int? {
    return try {
        graph.findNode("home")?.id ?: graph.startDestinationId.takeIf { it != 0 }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun ShortsApp() {
    val appContext = androidx.compose.ui.platform.LocalContext.current
    var latestUpdateConfig by remember { mutableStateOf<com.example.model.UpdateInfo?>(null) }
    var githubUpdateConfig by remember { mutableStateOf<com.example.model.UpdateInfo?>(null) }
    var dismissedForCurrentSessionVersion by remember { mutableIntStateOf(-1) }
    var installedVersionCode by remember { mutableIntStateOf(com.example.utils.AppUpdateManager.getInstalledVersionCode(appContext)) }
    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    
    DisposableEffect(Unit) {
        val reg = try {
            firestore.collection("app_config").document("update_info")
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error == null && snapshot != null && snapshot.exists()) {
                            val info = com.example.utils.AppUpdateManager.parseUpdateInfo(snapshot)
                            latestUpdateConfig = info
                        } else if (snapshot != null && !snapshot.exists()) {
                            latestUpdateConfig = null
                        }
                    } catch (_: Exception) {}
                }
        } catch (_: Exception) {
            null
        }
        onDispose {
            reg?.remove()
        }
    }

    val effectiveGitHubRepo = remember(latestUpdateConfig?.githubRepo) {
        latestUpdateConfig?.githubRepo?.ifBlank {
            com.example.utils.AppUpdateManager.getDefaultGitHubRepo()
        } ?: com.example.utils.AppUpdateManager.getDefaultGitHubRepo()
    }
    val isGitHubAutoSyncEnabled = latestUpdateConfig?.githubAutoSyncEnabled ?: true
    LaunchedEffect(effectiveGitHubRepo, isGitHubAutoSyncEnabled) {
        if (isGitHubAutoSyncEnabled && effectiveGitHubRepo.isNotBlank()) {
            val res = com.example.utils.AppUpdateManager.fetchLatestGitHubRelease(
                repoInput = effectiveGitHubRepo,
                baseConfig = latestUpdateConfig
            )
            res.getOrNull()?.let { releaseInfo ->
                githubUpdateConfig = releaseInfo
            }
        } else if (!isGitHubAutoSyncEnabled) {
            githubUpdateConfig = null
        }
    }

    val effectiveUpdateConfig = remember(latestUpdateConfig, githubUpdateConfig, installedVersionCode) {
        com.example.utils.AppUpdateManager.resolveEffectiveUpdate(
            firebaseConfig = latestUpdateConfig,
            githubConfig = githubUpdateConfig,
            installedVersionCode = installedVersionCode
        )
    }

    val switchKey by MultiAccountManager.switchCounter.collectAsStateWithLifecycle()
    key(switchKey) {
    val navController = rememberNavController()
    var userAvatarUrl by remember { mutableStateOf("") }
    
    val activeSavedAccount by MultiAccountManager.activeAccount.collectAsStateWithLifecycle()
    var showAccountSwitcher by remember { mutableStateOf(false) }
    val globalAccountSwitcherTrigger by GlobalState.showAccountSwitcher.collectAsStateWithLifecycle()
    LaunchedEffect(globalAccountSwitcherTrigger) {
        if (globalAccountSwitcherTrigger) {
            showAccountSwitcher = true
            GlobalState.showAccountSwitcher.value = false
        }
    }

    val isFaceUnlocked by com.example.security.FaceLockManager.isUnlocked.collectAsStateWithLifecycle()
    val isAccountSuspended by com.example.security.AccountSuspensionManager.isSuspended.collectAsStateWithLifecycle()

    val currentUserRealtime by com.example.model.RepositoryManager.currentUserState.collectAsStateWithLifecycle()
    LaunchedEffect(effectiveUpdateConfig, currentUserRealtime?.id, switchKey) {
        installedVersionCode = com.example.utils.AppUpdateManager.getInstalledVersionCode(appContext)
        com.example.utils.AppUpdateManager.syncUserUpdateStatusOnLaunch(appContext, effectiveUpdateConfig)
    }

    LaunchedEffect(currentUserRealtime?.avatarUrl, activeSavedAccount) {
        val realUrl = currentUserRealtime?.avatarUrl
        if (!realUrl.isNullOrEmpty()) {
            userAvatarUrl = realUrl
        } else if (activeSavedAccount?.avatarUrl?.isNotEmpty() == true) {
            userAvatarUrl = activeSavedAccount?.avatarUrl ?: ""
        }
    }
    
    val activeCallState by GlobalCallManager.activeCallState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val isOnline by com.example.utils.NetworkMonitor.isOnline.collectAsStateWithLifecycle()
    
    val pendingRoute = GlobalState.pendingDeepLinkRoute.value
    LaunchedEffect(pendingRoute) {
        if (!pendingRoute.isNullOrEmpty()) {
            try {
                navController.safeNavigate(pendingRoute) {
                    launchSingleTop = true
                }
            } catch (e: Exception) {
                android.util.Log.e("ShortsApp", "DeepLink navigation failed for $pendingRoute", e)
            } finally {
                GlobalState.pendingDeepLinkRoute.value = null
            }
        }
    }

    LaunchedEffect(activeCallState?.status) {
        val status = activeCallState?.status
        val isOutgoing = activeCallState?.isOutgoing == true
        if (status == "calling") {
            com.example.utils.SoundManager.startRingtone(context, isOutgoing = true)
        } else if (status == "ringing") {
            com.example.utils.SoundManager.startRingtone(context, isOutgoing = false)
        } else {
            com.example.utils.SoundManager.stopRingtone()
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            com.example.utils.SoundManager.stopRingtone()
        }
    }
    
    // Real-time listen to call signaling in Firebase Realtime Database
    val currentUserId = if (RepositoryManager.isRealUserLoggedIn()) RepositoryManager.getCurrentUserId() else ""

    LaunchedEffect(currentUserId, switchKey) {
        if (currentUserId.isEmpty()) {
            userAvatarUrl = ""
            return@LaunchedEffect
        }
        try {
            val doc = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                RepositoryManager.getDocument(
                    databaseId = RepositoryManager.DATABASE_ID,
                    collectionId = RepositoryManager.USERS_COLLECTION_ID,
                    documentId = currentUserId
                ).await()
            }
            val isBanned = doc.data?.get("isBanned") as? Boolean ?: false
            if (isBanned) {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                navController.safeNavigate("auth") {
                    popUpTo(0) { inclusive = true }
                }
            } else {
                val fetchedAvatar = doc.data?.get("avatar_url")?.toString()
                    ?: doc.data?.get("profilePicUrl")?.toString()
                    ?: ""
                if (fetchedAvatar.isNotEmpty()) {
                    userAvatarUrl = fetchedAvatar
                }
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            android.util.Log.w("ShortsApp", "User status check failed: ${e.message}")
        }
    }
    
    DisposableEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            scope.launch {
                RepositoryManager.socialRepository.setUserStatus(currentUserId, true)
            }
        }
        onDispose {
            if (currentUserId.isNotEmpty()) {
                @kotlin.OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                kotlinx.coroutines.GlobalScope.launch {
                    RepositoryManager.socialRepository.setUserStatus(currentUserId, false)
                }
            }
        }
    }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            try {
                com.example.repository.FlagshipFeaturesRepository.autoPublishDueScheduledPosts(currentUserId)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            com.example.security.AccountSuspensionManager.observeUserAccountStatus(currentUserId)
            RepositoryManager.socialRepository.observeCallSignalingRtdb(currentUserId).collect { signalingMap ->
                if (signalingMap != null) {
                    val type = signalingMap["type"] as? String ?: ""
                    val senderId = signalingMap["senderId"] as? String ?: ""
                    val data = signalingMap["data"] as? Map<String, Any> ?: emptyMap()
                    val timestamp = (signalingMap["timestampMillis"] as? Long)
                        ?: (signalingMap["timestamp"] as? Long)
                        ?: (signalingMap["timestamp"] as? com.google.firebase.Timestamp)?.toDate()?.time
                        ?: System.currentTimeMillis()
                    
                    // Respond only to signaling generated in the last 60 seconds
                    if (System.currentTimeMillis() - timestamp < 60000) {
                        when (type) {
                            "offer" -> {
                                val mediaType = data["mediaType"] as? String ?: "audio"
                                val senderUsername = data["senderUsername"] as? String ?: "User"
                                val senderDisplayName = data["senderDisplayName"] as? String ?: senderUsername
                                val senderAvatarUrl = data["senderAvatarUrl"] as? String ?: ""
                                val roomID = data["roomID"] as? String ?: data["sdp"] as? String ?: ""
                                if (GlobalCallManager.activeCallState.value == null) {
                                    GlobalCallManager.activeCallState.value = CallState(
                                        isVideo = mediaType == "video",
                                        peerId = senderId,
                                        peerUsername = senderUsername,
                                        peerDisplayName = senderDisplayName,
                                        peerAvatarUrl = senderAvatarUrl,
                                        isOutgoing = false,
                                        status = "ringing",
                                        sdpOffer = roomID
                                    )
                                }
                            }
                            "answer" -> {
                                val sdp = data["sdp"] as? String ?: ""
                                val roomID = data["roomID"] as? String ?: ""
                                val current = GlobalCallManager.activeCallState.value
                                if (current != null && current.isOutgoing && current.peerId == senderId) {
                                    GlobalCallManager.activeCallState.value = current.copy(
                                        status = "connected",
                                        sdpAnswer = sdp
                                    )
                                    com.example.utils.SoundManager.stopRingtone()
                                    val activeAcc = com.example.auth.MultiAccountManager.activeAccount.value
                                    val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                                    val myUsername: String = activeAcc?.username?.ifEmpty { null }
                                        ?: activeAcc?.displayName?.ifEmpty { null }
                                        ?: fbUser?.displayName?.ifEmpty { null }
                                        ?: "User"
                                    val targetRoom = if (roomID.isNotEmpty()) roomID else (current.sdpOffer ?: "call_${System.currentTimeMillis()}")
                                    val intent = android.content.Intent(context, CallActivity::class.java).apply {
                                        putExtra("userID", currentUserId)
                                        putExtra("userName", myUsername)
                                        putExtra("callID", targetRoom)
                                        putExtra("isVideoCall", current.isVideo)
                                        putExtra("peerId", current.peerId)
                                        putExtra("peerName", current.peerDisplayName.ifEmpty { current.peerUsername })
                                        putExtra("peerAvatar", current.peerAvatarUrl)
                                        putExtra("isOutgoing", true)
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.util.Log.e("ShortsApp", "Failed to start CallActivity", e)
                                    }
                                    GlobalCallManager.activeCallState.value = null
                                }
                            }
                            "decline" -> {
                                val current = GlobalCallManager.activeCallState.value
                                if (current != null && current.peerId == senderId) {
                                    com.example.utils.SoundManager.stopRingtone()
                                    GlobalCallManager.activeCallState.value = current.copy(status = "declined")
                                    delay(1500)
                                    GlobalCallManager.activeCallState.value = null
                                    RepositoryManager.socialRepository.clearCallSignaling(currentUserId)
                                }
                            }
                            "end" -> {
                                val current = GlobalCallManager.activeCallState.value
                                if (current != null && current.peerId == senderId) {
                                    com.example.utils.SoundManager.stopRingtone()
                                    GlobalCallManager.activeCallState.value = current.copy(status = "ended")
                                    delay(1500)
                                    GlobalCallManager.activeCallState.value = null
                                    RepositoryManager.socialRepository.clearCallSignaling(currentUserId)
                                }
                            }
                            "candidate" -> {
                                val sdpMid = data["sdpMid"] as? String ?: return@collect
                                val sdpMLineIndex = (data["sdpMLineIndex"] as? Long)?.toInt() ?: return@collect
                                val sdp = data["sdp"] as? String ?: return@collect
                                
                                val current = GlobalCallManager.activeCallState.value
                                if (current != null && current.peerId == senderId) {
                                }
                            }
                            "heart" -> {
                                GlobalCallManager.heartEventFlow.tryEmit(System.currentTimeMillis())
                            }
                        }
                    }
                }
            }
        }
    }
    
    var showSuspensionKyc by remember { mutableStateOf(false) }

    if (!isFaceUnlocked) {
        com.example.ui.security.FaceLockScreen(
            onUnlocked = {
                com.example.security.FaceLockManager.unlock()
            },
            onOpenAccountSwitcher = {
                showAccountSwitcher = true
            }
        )
    } else if (isAccountSuspended) {
        if (showSuspensionKyc) {
            com.example.ui.security.FaceKycScreen(
                onBack = { showSuspensionKyc = false }
            )
        } else {
            com.example.ui.security.SuspendedAccountScreen(
                onNavigateToKyc = {
                    showSuspensionKyc = true
                },
                onOpenAccountSwitcher = {
                    showAccountSwitcher = true
                },
                onLogout = {
                    showSuspensionKyc = false
                    com.example.auth.MultiAccountManager.logoutCurrentAccount(context)
                    navController.safeNavigate("auth") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                val hideBottomBarRoutes = listOf(
                    "upload", "professional_dashboard", "monetization", "auth", "edit_profile", "settings",
                    "wallet", "admin_panel", "business_dashboard", "business_setup", "create_advertisement",
                    "admin_ad_manager", "identity_verification", "account_security", "store", "referrals"
                )
            if (currentRoute != null && currentRoute !in hideBottomBarRoutes && !currentRoute.startsWith("webview/")) {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    userAvatarUrl = userAvatarUrl,
                    onNavigate = { route ->
                        val protectedRoutes = listOf("upload", "inbox", "profile")
                        if (route in protectedRoutes && com.example.model.RepositoryManager.getCurrentUserId().isEmpty()) {
                            navController.safeNavigate("auth")
                        } else {
                            val startDestId = navController.safeStartDestinationId()
                            navController.safeNavigate(route) {
                                if (startDestId != null) {
                                    popUpTo(startDestId) { saveState = true }
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onOpenAccountSwitcher = { showAccountSwitcher = true }
                )
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        Box(modifier = Modifier.fillMaxSize()) {
            val isSpreadUnderRoute = currentRoute == "home" || currentRoute == "reels"
            val bottomPadding = if (isSpreadUnderRoute) 0.dp else innerPadding.calculateBottomPadding()
            val initialStartDestination = remember(switchKey) {
                if (com.example.model.RepositoryManager.isLoggedIn()) "home" else "auth"
            }
            val reducedMotion = com.example.ui.motion.rememberReducedMotionEnabled()
            NavHost(
                navController = navController,
                startDestination = initialStartDestination,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPadding)
                        .background(if (currentRoute == "reels") Color.Black else MaterialTheme.colorScheme.background),
                    enterTransition = {
                        val topTabs = setOf("home", "discover", "reels", "profile", "inbox")
                        if (reducedMotion) {
                            fadeIn(animationSpec = tween(1))
                        } else if (targetState.destination.route in topTabs && initialState.destination.route in topTabs) {
                            fadeIn(animationSpec = com.example.ui.motion.SocialMotion.floatSpec(com.example.ui.motion.SocialMotion.DurationTabFade))
                        } else {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = com.example.ui.motion.SocialMotion.screenSlideSpec()
                            ) + fadeIn(animationSpec = com.example.ui.motion.SocialMotion.floatSpec(com.example.ui.motion.SocialMotion.DurationFast))
                        }
                    },
                    exitTransition = {
                        val topTabs = setOf("home", "discover", "reels", "profile", "inbox")
                        if (reducedMotion) {
                            fadeOut(animationSpec = tween(1))
                        } else if (targetState.destination.route in topTabs && initialState.destination.route in topTabs) {
                            fadeOut(animationSpec = com.example.ui.motion.SocialMotion.floatSpec(com.example.ui.motion.SocialMotion.DurationTabFade))
                        } else {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = com.example.ui.motion.SocialMotion.screenSlideSpec()
                            ) + fadeOut(animationSpec = com.example.ui.motion.SocialMotion.floatSpec(com.example.ui.motion.SocialMotion.DurationFast))
                        }
                    },
                    popEnterTransition = {
                        val topTabs = setOf("home", "discover", "reels", "profile", "inbox")
                        if (reducedMotion) {
                            fadeIn(animationSpec = tween(1))
                        } else if (targetState.destination.route in topTabs && initialState.destination.route in topTabs) {
                            fadeIn(animationSpec = com.example.ui.motion.SocialMotion.floatSpec(com.example.ui.motion.SocialMotion.DurationTabFade))
                        } else {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = com.example.ui.motion.SocialMotion.screenSlideSpec()
                            ) + fadeIn(animationSpec = com.example.ui.motion.SocialMotion.floatSpec(com.example.ui.motion.SocialMotion.DurationFast))
                        }
                    },
                    popExitTransition = {
                        val topTabs = setOf("home", "discover", "reels", "profile", "inbox")
                        if (reducedMotion) {
                            fadeOut(animationSpec = tween(1))
                        } else if (targetState.destination.route in topTabs && initialState.destination.route in topTabs) {
                            fadeOut(animationSpec = com.example.ui.motion.SocialMotion.floatSpec(com.example.ui.motion.SocialMotion.DurationTabFade))
                        } else {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = com.example.ui.motion.SocialMotion.screenSlideSpec()
                            ) + fadeOut(animationSpec = com.example.ui.motion.SocialMotion.floatSpec(com.example.ui.motion.SocialMotion.DurationFast))
                        }
                    }
                ) {
                composable(
                    "home",
                    deepLinks = listOf(
                        androidx.navigation.navDeepLink { uriPattern = "https://upload.app/socialindia/story/{storyId}" }
                    )
                ) { backStackEntry ->
                    val storyId = backStackEntry.arguments?.getString("storyId")
                    if (storyId != null) {
                        GlobalState.targetStoryId.value = storyId
                    }
                    HomeFeedScreen(
                        viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                        onNavigate = { route -> navController.safeNavigate(route) }
                    )
                }
                
composable("audio_details/{trackId}/{trackName}/{artistName}/{trackImageUrl}") { backStackEntry ->
                    val trackId = backStackEntry.arguments?.getString("trackId") ?: ""
                    val trackName = backStackEntry.arguments?.getString("trackName") ?: ""
                    val artistName = backStackEntry.arguments?.getString("artistName") ?: ""
                    val trackImageUrl = backStackEntry.arguments?.getString("trackImageUrl") ?: ""
                    
                    AudioDetailsScreen(
                        trackId = trackId,
                        trackName = trackName,
                        artistName = artistName,
                        trackImageUrl = trackImageUrl,
                        onBack = { navController.safePopBackStack() },
                        onUseAudio = { sId, sTitle, sArtist, sCover, sAudioUrl ->
                            val tId = sId.toLongOrNull() ?: System.currentTimeMillis()
                            com.example.ui.GlobalState.preselectedMusicTrack.value = com.example.api.ITunesTrack(
                                id = tId,
                                name = sTitle,
                                artistName = sArtist,
                                artworkUrl = sCover,
                                previewUrl = sAudioUrl
                            )
                            if (!com.example.model.RepositoryManager.isRealUserLoggedIn()) {
                                android.widget.Toast.makeText(context, "Please sign in to create with this audio", android.widget.Toast.LENGTH_SHORT).show()
                                navController.safeNavigate("auth")
                            } else {
                                navController.safeNavigate("upload")
                            }
                        },
                        onNavigateToVideo = { videoId ->
                            if (videoId.isNotBlank()) {
                                GlobalState.targetVideoId.value = videoId
                                val startDestId = navController.safeStartDestinationId()
                                navController.safeNavigate("home") {
                                    if (startDestId != null) {
                                        popUpTo(startDestId)
                                    }
                                    launchSingleTop = true
                                }
                            }
                        },
                        onNavigateToProfile = { userId ->
                            if (userId.isNotBlank()) navController.safeNavigate("profile/$userId")
                        }
                    )
                }
                composable("discover") {
                    DiscoverScreen(
                        onNavigateToInbox = { navController.safeNavigate("inbox/0") },
                        onNavigateToHome = { videoId -> 
                            if (videoId.isNotBlank()) navController.safeNavigate("post_detail/$videoId")
                        },
                        onNavigateToProfile = { userId ->
                            if (userId.isNotBlank()) navController.safeNavigate("profile/$userId")
                        }
                    )
                }
                composable("search") {
                    FlagshipSearchScreen(
                        onNavigateToProfile = { userId ->
                            if (userId.isNotBlank()) navController.safeNavigate("profile/$userId")
                        },
                        onVideoClick = { videoId ->
                            if (videoId.isNotBlank()) navController.safeNavigate("post_detail/$videoId")
                        },
                        onBack = { navController.safePopBackStack() },
                        onAudioClick = { sId, sTitle, sArtist, sCover ->
                            val eTrackId = java.net.URLEncoder.encode(if (sId.isEmpty()) "unknown" else sId, "UTF-8")
                            val eTrackName = java.net.URLEncoder.encode(if (sTitle.isEmpty()) "Original Audio" else sTitle, "UTF-8")
                            val eArtistName = java.net.URLEncoder.encode(if (sArtist.isEmpty()) "Original Creator" else sArtist, "UTF-8")
                            val eTrackImage = java.net.URLEncoder.encode(if (sCover.isEmpty()) "empty" else sCover, "UTF-8")
                            navController.safeNavigate("audio_details/$eTrackId/$eTrackName/$eArtistName/$eTrackImage")
                        },
                        onUseAudio = { sId, sTitle, sArtist, sCover ->
                            val tId = sId.toLongOrNull() ?: System.currentTimeMillis()
                            com.example.ui.GlobalState.preselectedMusicTrack.value = com.example.api.ITunesTrack(
                                id = tId,
                                name = sTitle,
                                artistName = sArtist,
                                artworkUrl = sCover,
                                previewUrl = ""
                            )
                            if (!com.example.model.RepositoryManager.isRealUserLoggedIn()) {
                                android.widget.Toast.makeText(context, "Please sign in to create with this audio", android.widget.Toast.LENGTH_SHORT).show()
                                navController.safeNavigate("auth")
                            } else {
                                navController.safeNavigate("upload")
                            }
                        }
                    )
                }
                composable("discover_people") {
                    DiscoverPeopleScreen(
                        onNavigateToProfile = { userId ->
                            if (userId.isNotBlank()) navController.safeNavigate("profile/$userId")
                        },
                        onBack = { navController.safePopBackStack() }
                    )
                }
                composable("reel/{reelId}") { backStackEntry ->
                    val reelId = backStackEntry.arguments?.getString("reelId") ?: ""
                    LaunchedEffect(reelId) {
                        if (reelId.isNotBlank()) {
                            com.example.viewmodel.VideoViewModel.selectedVideoIdToPlay = reelId
                        }
                    }
                    VideoFeedScreen(
                        onNavigateToSearch = { navController.safeNavigate("search") },
                        onCameraClick = {
                            if (!com.example.model.RepositoryManager.isRealUserLoggedIn()) {
                                android.widget.Toast.makeText(context, "Please log in to create a post or reel", android.widget.Toast.LENGTH_SHORT).show()
                                navController.safeNavigate("auth")
                            } else {
                                navController.safeNavigate("upload")
                            }
                        },
                        onNavigateToWebView = { url ->
                            val encodedUrl = java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8.toString())
                            navController.safeNavigate("webview/$encodedUrl")
                        },
                        onNavigateToInbox = { tab ->
                            if (!com.example.model.RepositoryManager.isRealUserLoggedIn()) {
                                navController.safeNavigate("auth")
                            } else {
                                navController.safeNavigate("inbox/$tab")
                            }
                        },
                        onNavigateToProfile = { userId ->
                            if (userId.isNotBlank()) navController.safeNavigate("profile/$userId")
                        },
                        onNavigateToMusic = { trackId, trackName, artistName, trackImageUrl ->
                            val eTrackId = java.net.URLEncoder.encode(if (trackId.isEmpty()) "unknown" else trackId, "UTF-8")
                            val eTrackName = java.net.URLEncoder.encode(if (trackName.isEmpty()) "Original Audio" else trackName, "UTF-8")
                            val eArtistName = java.net.URLEncoder.encode(if (artistName.isEmpty()) "Original Creator" else artistName, "UTF-8")
                            val eTrackImage = java.net.URLEncoder.encode(if (trackImageUrl.isEmpty()) "empty" else trackImageUrl, "UTF-8")
                            navController.safeNavigate("audio_details/$eTrackId/$eTrackName/$eArtistName/$eTrackImage")
                        },
                        onNavigateToMonetization = {
                            if (!com.example.model.RepositoryManager.isRealUserLoggedIn()) {
                                navController.safeNavigate("auth")
                            } else {
                                navController.safeNavigate("monetization")
                            }
                        },
                        onNavigateToTemplates = {
                            navController.safeNavigate("templates")
                        }
                    )
                }
                composable("reels") {
                    VideoFeedScreen(
                        onNavigateToSearch = { navController.safeNavigate("search") },
                        onCameraClick = { 
                            if (!com.example.model.RepositoryManager.isRealUserLoggedIn()) {
                                android.widget.Toast.makeText(context, "Please log in to create a post or reel", android.widget.Toast.LENGTH_SHORT).show()
                                navController.safeNavigate("auth")
                            } else {
                                navController.safeNavigate("upload") 
                            }
                        },
                        onNavigateToWebView = { url -> 
                            val encodedUrl = java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8.toString())
                            navController.safeNavigate("webview/$encodedUrl")
                        },
                        onNavigateToInbox = { tab ->
                            if (!com.example.model.RepositoryManager.isRealUserLoggedIn()) {
                                navController.safeNavigate("auth")
                            } else {
                                navController.safeNavigate("inbox/$tab") 
                            }
                        },
                        onNavigateToProfile = { userId ->
                            if (userId.isNotBlank()) navController.safeNavigate("profile/$userId")
                        },
                        onNavigateToMusic = { trackId, trackName, artistName, trackImageUrl ->
                            val eTrackId = java.net.URLEncoder.encode(if (trackId.isEmpty()) "unknown" else trackId, "UTF-8")
                            val eTrackName = java.net.URLEncoder.encode(if (trackName.isEmpty()) "Original Audio" else trackName, "UTF-8")
                            val eArtistName = java.net.URLEncoder.encode(if (artistName.isEmpty()) "Original Creator" else artistName, "UTF-8")
                            val eTrackImage = java.net.URLEncoder.encode(if (trackImageUrl.isEmpty()) "empty" else trackImageUrl, "UTF-8")
                            navController.safeNavigate("audio_details/$eTrackId/$eTrackName/$eArtistName/$eTrackImage")
                        },
                        onNavigateToMonetization = {
                            if (!com.example.model.RepositoryManager.isRealUserLoggedIn()) {
                                navController.safeNavigate("auth")
                            } else {
                                navController.safeNavigate("monetization")
                            }
                        },
                        onNavigateToTemplates = {
                            navController.safeNavigate("templates")
                        }
                    )
                }
                composable("auth") {
                    LoginScreen(
                        onLoginSuccess = {
                            if (navController.previousBackStackEntry != null) {
                                navController.safePopBackStack()
                            } else {
                                navController.safeNavigate("home") {
                                    popUpTo(0) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                        onBack = {
                            if (navController.previousBackStackEntry != null) {
                                navController.safePopBackStack()
                            } else {
                                navController.safeNavigate("home") {
                                    popUpTo(0) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }
                composable("upload") {
                    if (!com.example.model.RepositoryManager.isRealUserLoggedIn()) {
                        LaunchedEffect(Unit) {
                            android.widget.Toast.makeText(context, "Please sign in to post photos or videos", android.widget.Toast.LENGTH_SHORT).show()
                            navController.safeNavigate("auth") {
                                popUpTo("upload") { inclusive = true }
                            }
                        }
                    } else {
                        @OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
                        UploadScreen(
                            onNavigateToHome = { navController.safePopBackStack() },
                            onNavigateToProfile = {
                                val startDestId = navController.safeStartDestinationId()
                                navController.safeNavigate("profile") {
                                    if (startDestId != null) {
                                        popUpTo(startDestId) { saveState = true }
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onNavigate = { route ->
                                navController.safeNavigate(route)
                            }
                        )
                    }
                }
                composable("inbox/{tab}") { backStackEntry ->
                    val tab = backStackEntry.arguments?.getString("tab") ?: "messages"
                    InboxScreen(
                        initialTab = tab,
                        onNavigateToHome = {
                            navController.safeNavigate("home") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onNavigate = { route ->
                            navController.safeNavigate(route)
                        }
                    )
                }
                composable("inbox") {
                    InboxScreen(
                        initialTab = "messages",
                        onNavigateToHome = {
                            navController.safeNavigate("home") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onNavigate = { route ->
                            navController.safeNavigate(route)
                        }
                    )
                }
                
                composable("store") {
                    StoreScreen(
                        onNavigateBack = { navController.safePopBackStack() }
                    )
                }
                
                composable("follows/{userId}/{tab}") { backStackEntry ->
                    val targetUserId = backStackEntry.arguments?.getString("userId") ?: ""
                    val tab = backStackEntry.arguments?.getString("tab") ?: "following"
                    FollowsScreen(
                        targetUserId = targetUserId,
                        initialTab = tab,
                        onNavigateToProfile = { uid -> if (uid.isNotBlank()) navController.safeNavigate("profile/$uid") },
                        onBack = { navController.safePopBackStack() }
                    )
                }

                composable("profile/{userId}") { backStackEntry ->
                    val rawTargetUserId = backStackEntry.arguments?.getString("userId")
                    val targetUserId = rawTargetUserId?.let { android.net.Uri.decode(it).trim() }?.takeIf { it.isNotEmpty() }
                    ProfileScreen(
                        profileUserId = targetUserId,
                        onNavigateToEditProfile = { navController.safeNavigate("edit_profile") },
                        onNavigateToSettings = { navController.safeNavigate("settings") },
                        onNavigateToReel = { reelId ->
                            com.example.viewmodel.VideoViewModel.selectedVideoIdToPlay = reelId
                            navController.safeNavigate("reels")
                        },
                        onNavigateToPostDetail = { postId, authorId ->
                            if (postId.isNotBlank()) {
                                val dest = "post_detail/$postId" + (if (!authorId.isNullOrEmpty()) "?userId=$authorId" else "")
                                navController.safeNavigate(dest)
                            }
                        },
                        onNavigateToChat = { uid ->
                            if (uid.isNotBlank()) navController.safeNavigate("inbox_chat/$uid")
                        },
                        onNavigateToFollows = { uid, tab ->
                            if (uid.isNotBlank()) navController.safeNavigate("follows/$uid/${tab.ifBlank { "following" }}")
                        },
                        onNavigateToDiscoverPeople = { navController.safeNavigate("discover_people") },
                        onNavigateToSaved = { navController.safeNavigate("saved_posts") },
                        onBack = { navController.safePopBackStack() },
                        onNavigateToChannels = { navController.safeNavigate("broadcast_channels") },
                        onNavigateToBusinessDashboard = { navController.safeNavigate("business_dashboard") },
                        onNavigateToCreateAd = { postId ->
                            if (!postId.isNullOrBlank()) navController.safeNavigate("create_advertisement/$postId")
                            else navController.safeNavigate("create_advertisement")
                        },
                        onNavigate = { route ->
                            navController.safeNavigate(route)
                        }
                    )
                }
                composable("profile") {
                    ProfileScreen(
                        profileUserId = null,
                        onNavigateToEditProfile = { navController.safeNavigate("edit_profile") },
                        onNavigateToSettings = { navController.safeNavigate("settings") },
                        onNavigateToReel = { reelId ->
                            com.example.viewmodel.VideoViewModel.selectedVideoIdToPlay = reelId
                            navController.safeNavigate("reels")
                        },
                        onNavigateToPostDetail = { postId, authorId ->
                            if (postId.isNotBlank()) {
                                val dest = "post_detail/$postId" + (if (!authorId.isNullOrEmpty()) "?userId=$authorId" else "")
                                navController.safeNavigate(dest)
                            }
                        },
                        onNavigateToChat = { uid ->
                            if (uid.isNotBlank()) navController.safeNavigate("inbox_chat/$uid")
                        },
                        onNavigateToFollows = { uid, tab ->
                            if (uid.isNotBlank()) navController.safeNavigate("follows/$uid/${tab.ifBlank { "following" }}")
                        },
                        onNavigateToDiscoverPeople = { navController.safeNavigate("discover_people") },
                        onNavigateToSaved = { navController.safeNavigate("saved_posts") },
                        onBack = { navController.safePopBackStack() },
                        onOpenAccountSwitcher = { showAccountSwitcher = true },
                        onNavigateToChannels = { navController.safeNavigate("broadcast_channels") },
                        onNavigateToBusinessDashboard = { navController.safeNavigate("business_dashboard") },
                        onNavigateToCreateAd = { postId ->
                            if (!postId.isNullOrBlank()) navController.safeNavigate("create_advertisement/$postId")
                            else navController.safeNavigate("create_advertisement")
                        },
                        onNavigate = { route ->
                            navController.safeNavigate(route)
                        }
                    )
                }
                composable("inbox_chat/{userId}") { backStackEntry ->
                    val targetUserId = backStackEntry.arguments?.getString("userId") ?: ""
                    InboxScreen(
                        initialTab = "messages",
                        initialTargetUserId = targetUserId,
                        onNavigateToHome = {
                            navController.safeNavigate("home") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onNavigate = { route ->
                            navController.safeNavigate(route)
                        }
                    )
                }
                composable("edit_profile") {
                    EditProfileScreen(
                        onBack = { navController.safePopBackStack() }
                    )
                }
                composable("settings") {
                    var isAdmin by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    var isVerified by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        try {
                            val userId = com.example.model.RepositoryManager.getCurrentUserId()
                            if (userId.isEmpty()) return@LaunchedEffect
                            val doc = com.example.model.RepositoryManager.firestore.collection("users").document(userId).get().await()
                            val authEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""
                            val userEmail = doc.getString("email") ?: ""
                            val isMasterEmail = authEmail.equals("abhinandankumar7867@gmail.com", ignoreCase = true) || 
                                                userEmail.equals("abhinandankumar7867@gmail.com", ignoreCase = true)
                            isAdmin = (doc.getBoolean("isAdmin") ?: false) || isMasterEmail
                            isVerified = (doc.getBoolean("isVerified") ?: false) || isMasterEmail
                            if (isMasterEmail && (doc.getBoolean("isAdmin") != true)) {
                                try {
                                    com.example.model.RepositoryManager.firestore.collection("users").document(userId)
                                        .update(mapOf("isAdmin" to true, "isVerified" to true))
                                } catch (_: Exception) {}
                            }
                        } catch (_: Exception) {}
                    }
                    SettingsScreen(
                        onBack = { navController.safePopBackStack() },
                        onLogout = {
                            com.example.auth.MultiAccountManager.logoutCurrentAccount(context)
                            navController.safeNavigate("home") { popUpTo(0) }
                        },
                        isAdmin = isAdmin,
                        isVerified = isVerified,
                        onAdminClick = { navController.safeNavigate("admin_panel") },
                        onDashboardClick = { navController.safeNavigate("professional_dashboard") },
                        onMonetizationClick = { navController.safeNavigate("monetization") },
                        onWalletClick = { navController.safeNavigate("wallet") },
                        onNavigateToEditProfile = { navController.safeNavigate("edit_profile") },
                        onLogin = { navController.safeNavigate("auth") },
                        onRequestVerification = { navController.safeNavigate("verification") },
                        onReviewVerifications = { navController.safeNavigate("verification_admin") },
                        onPrivacyClick = { navController.safeNavigate("privacy_settings") },
                        onNavigateToSaved = { navController.safeNavigate("saved_posts") },
                        onNavigateToCloseFriends = { navController.safeNavigate("close_friends") },
                        onNavigateToArchive = { navController.safeNavigate("archive_deleted") },
                        onNavigateToScheduled = { navController.safeNavigate("scheduled_content") },
                        onNavigateToChannels = { navController.safeNavigate("broadcast_channels") },
                        onOpenAccountSwitcher = { showAccountSwitcher = true },
                        onNavigateToBusinessDashboard = { navController.safeNavigate("business_dashboard") },
                        onNavigateToBusinessSetup = { navController.safeNavigate("business_setup") },
                        onNavigateToCreateAd = { navController.safeNavigate("create_advertisement") },
                        onNavigateToAdminAds = { navController.safeNavigate("admin_ad_manager") },
                        onNavigateToAccountSecurity = { navController.safeNavigate("account_security") },
                        onNavigateToAdminSecurity = { navController.safeNavigate("admin_security") },
                        onKycClick = { navController.safeNavigate("identity_verification") },
                        onAiHubClick = { navController.safeNavigate("ai_hub") },
                        onNavigateToReferrals = { navController.safeNavigate("referrals") },
                        onNavigateToDrafts = { navController.safeNavigate("drafts") }
                    )
                }

                composable("referrals") {
                    ReferralRewardsScreen(
                        currentUserId = currentUserId,
                        onBack = { navController.safePopBackStack() },
                        onNavigateToWallet = { navController.safeNavigate("wallet") }
                    )
                }

                composable("identity_verification") {
                    com.example.ui.security.FaceKycScreen(
                        onBack = { navController.safePopBackStack() }
                    )
                }

                composable("account_security") {
                    com.example.ui.security.AccountSecurityScreen(
                        onBack = { navController.safePopBackStack() },
                        onNavigateToAdminSecurity = { navController.safeNavigate("admin_security") }
                    )
                }

                composable("admin_security") {
                    com.example.ui.security.AdminSecurityScreen(
                        onBack = { navController.safePopBackStack() }
                    )
                }
                
                composable("business_dashboard") {
                    BusinessDashboardScreen(
                        onBack = { navController.safePopBackStack() },
                        onNavigateToCreateAd = { postId ->
                            if (!postId.isNullOrBlank()) navController.safeNavigate("create_advertisement/$postId")
                            else navController.safeNavigate("create_advertisement")
                        },
                        onNavigateToSettings = { navController.safeNavigate("business_setup") }
                    )
                }

                composable("business_setup") {
                    BusinessSetupScreen(
                        onBack = { navController.safePopBackStack() },
                        onSetupComplete = {
                            navController.safeNavigate("business_dashboard") {
                                popUpTo("business_setup") { inclusive = true }
                            }
                        },
                        onNavigateToVerification = {
                            navController.safeNavigate("verification")
                        }
                    )
                }

                composable("create_advertisement") {
                    CreateAdvertisementScreen(
                        onBack = { navController.safePopBackStack() },
                        onAdCreated = { _ ->
                            navController.safeNavigate("business_dashboard") {
                                popUpTo("create_advertisement") { inclusive = true }
                            }
                        }
                    )
                }

                composable("create_advertisement/{postId}") { backStackEntry ->
                    val postId = backStackEntry.arguments?.getString("postId")
                    CreateAdvertisementScreen(
                        onBack = { navController.safePopBackStack() },
                        onAdCreated = { _ ->
                            navController.safeNavigate("business_dashboard") {
                                popUpTo("create_advertisement") { inclusive = true }
                            }
                        },
                        preselectedPostId = postId
                    )
                }

                composable("admin_ad_manager") {
                    AdminAdManagerScreen(
                        onBack = { navController.safePopBackStack() }
                    )
                }
                
                composable("verification") {
                    VerificationScreen(onBack = { navController.safePopBackStack() })
                }
                
                composable("verification_admin") {
                    VerificationAdminScreen(onNavigateBack = { navController.safePopBackStack() })
                }
                
                composable("privacy_settings") {
                    PrivacySettingsScreen(onBack = { navController.safePopBackStack() })
                }

                composable("saved_posts") {
                    SavedCollectionsScreen(
                        currentUserId = currentUserId,
                        onNavigateBack = { navController.safePopBackStack() },
                        onOpenPost = { postId ->
                            if (postId.isNotBlank()) navController.safeNavigate("post_detail/$postId")
                        }
                    )
                }

                composable("close_friends") {
                    CloseFriendsScreen(
                        currentUserId = currentUserId,
                        onNavigateBack = { navController.safePopBackStack() }
                    )
                }

                composable("archive_deleted") {
                    ArchiveAndDeletedScreen(
                        currentUserId = currentUserId,
                        onNavigateBack = { navController.safePopBackStack() },
                        onOpenPost = { postId ->
                            if (postId.isNotBlank()) navController.safeNavigate("post_detail/$postId")
                        }
                    )
                }

                composable("scheduled_content") {
                    ScheduledContentScreen(
                        currentUserId = currentUserId,
                        onNavigateBack = { navController.safePopBackStack() }
                    )
                }

                composable("broadcast_channels") {
                    val currentUserName = com.example.auth.MultiAccountManager.activeAccount.value?.username?.ifEmpty { null }
                        ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName?.ifEmpty { null }
                        ?: "You"
                    BroadcastChannelsScreen(
                        currentUserId = currentUserId,
                        currentUsername = currentUserName,
                        currentUserAvatar = userAvatarUrl,
                        onNavigateBack = { navController.safePopBackStack() },
                        onOpenChannel = { channelId ->
                            if (channelId.isNotBlank()) navController.safeNavigate("broadcast_chat/$channelId")
                        }
                    )
                }

                composable("broadcast_chat/{channelId}") { backStackEntry ->
                    val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                    val currentUserName = com.example.auth.MultiAccountManager.activeAccount.value?.username?.ifEmpty { null }
                        ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName?.ifEmpty { null }
                        ?: "You"
                    BroadcastChannelChatScreen(
                        channelId = channelId,
                        currentUserId = currentUserId,
                        currentUsername = currentUserName,
                        currentUserAvatar = userAvatarUrl,
                        onNavigateBack = { navController.safePopBackStack() },
                        onOpenPost = { postId ->
                            if (postId.isNotBlank()) navController.safeNavigate("post_detail/$postId")
                        }
                    )
                }
                
                composable("professional_dashboard") {
                    var currentUser by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.example.model.User?>(null) }
                    var totalViews by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
                    var totalLikes by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
                    var reachCount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("0") }
                    var dashLoadDone by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        try {
                            val userId = com.example.model.RepositoryManager.getCurrentUserId()
                            if (userId.isEmpty()) {
                                dashLoadDone = true
                                return@LaunchedEffect
                            }
                            val doc = com.example.model.RepositoryManager.firestore.collection("users").document(userId).get().await()
                            currentUser = com.example.model.User(
                                id = doc.id,
                                username = doc.getString("username") ?: "",
                                avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatar_url") ?: "",
                                isVerified = doc.getBoolean("isVerified") ?: false,
                                followerCount = doc.getLong("followerCount")?.toInt() ?: 0,
                                isMonetized = doc.getBoolean("isMonetized") ?: false,
                                monetizationRequested = doc.getBoolean("monetizationRequested") ?: false,
                                verificationRequested = doc.getBoolean("verificationRequested") ?: false,
                                earnings = doc.getDouble("earnings") ?: 0.0
                            )
                            
                            val videosSnap = com.example.model.RepositoryManager.firestore.collection("posts").whereEqualTo("userId", userId).get().await()
                            var views = 0
                            var likes = 0
                            for (v in videosSnap.documents) {
                                views += (v.getLong("views") ?: 0L).toInt()
                                likes += (v.getLong("likesCount") ?: 0L).toInt()
                            }
                            totalViews = views
                            totalLikes = likes
                            reachCount = com.example.utils.NumberFormatUtils.formatCount(views + likes)
                        } catch (_: Exception) {
                        } finally {
                            dashLoadDone = true
                        }
                    }
                    val dashUser = currentUser
                    if (dashUser != null) {
                        ProfessionalDashboardScreen(
                            user = dashUser,
                            onBack = { navController.safePopBackStack() },
                            onNavigateToMonetization = { navController.safeNavigate("monetization") },
                            onNavigateToWallet = { navController.safeNavigate("wallet") },
                            onNavigateToDrafts = { navController.safeNavigate("drafts") },
                            onNavigateToCalendar = { navController.safeNavigate("scheduled_content") },
                            onNavigateToReferrals = { navController.safeNavigate("referrals") },
                            onOpenPost = { postId -> if (postId.isNotBlank()) navController.safeNavigate("post_detail/$postId") }
                        )
                    } else if (dashLoadDone) {
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                androidx.compose.material3.Text("Please sign in to view your dashboard")
                                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(12.dp))
                                androidx.compose.material3.Button(onClick = { navController.safePopBackStack() }) {
                                    androidx.compose.material3.Text("Go Back")
                                }
                            }
                        }
                    } else {
                        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                }
                composable("webview/{url}") { backStackEntry ->
                    val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                    val url = try {
                        java.net.URLDecoder.decode(encodedUrl, java.nio.charset.StandardCharsets.UTF_8.toString())
                    } catch (_: Exception) {
                        encodedUrl
                    }
                    WebViewScreen(url = url, onBack = { navController.safePopBackStack() })
                }
                composable("drafts") {
                    DraftsScreen(
                        onBack = { navController.safePopBackStack() },
                        onNavigateToScheduled = { navController.safeNavigate("scheduled_content") }
                    )
                }
                composable(
                    route = "post_detail/{postId}?userId={userId}",
                    arguments = listOf(
                        androidx.navigation.navArgument("postId") { type = androidx.navigation.NavType.StringType },
                        androidx.navigation.navArgument("userId") {
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val postId = backStackEntry.arguments?.getString("postId") ?: ""
                    val authorUserId = backStackEntry.arguments?.getString("userId")
                    PostDetailScreen(
                        initialPostId = postId,
                        userId = authorUserId,
                        onBack = { navController.safePopBackStack() },
                        onNavigateToProfile = { uid ->
                            if (uid.isNotBlank()) navController.safeNavigate("profile/$uid")
                        },
                        onNavigateToReel = { reelId ->
                            com.example.viewmodel.VideoViewModel.selectedVideoIdToPlay = reelId
                            navController.safeNavigate("reels")
                        },
                        onAudioClick = { trackId, trackTitle, trackArtist, trackCover ->
                            val eTrackId = java.net.URLEncoder.encode(if (trackId.isEmpty()) "unknown" else trackId, "UTF-8")
                            val eTrackName = java.net.URLEncoder.encode(if (trackTitle.isEmpty()) "Original Audio" else trackTitle, "UTF-8")
                            val eTrackArtist = java.net.URLEncoder.encode(if (trackArtist.isEmpty()) "Artist" else trackArtist, "UTF-8")
                            val eTrackCover = java.net.URLEncoder.encode(if (trackCover.isEmpty()) "default" else trackCover, "UTF-8")
                            navController.safeNavigate("audio_details/$eTrackId/$eTrackName/$eTrackArtist/$eTrackCover")
                        }
                    )
                }
                composable("post_detail/{postId}") { backStackEntry ->
                    val postId = backStackEntry.arguments?.getString("postId") ?: ""
                    PostDetailScreen(
                        initialPostId = postId,
                        userId = null,
                        onBack = { navController.safePopBackStack() },
                        onNavigateToProfile = { uid ->
                            if (uid.isNotBlank()) navController.safeNavigate("profile/$uid")
                        },
                        onNavigateToReel = { reelId ->
                            com.example.viewmodel.VideoViewModel.selectedVideoIdToPlay = reelId
                            navController.safeNavigate("reels")
                        },
                        onAudioClick = { trackId, trackTitle, trackArtist, trackCover ->
                            val eTrackId = java.net.URLEncoder.encode(if (trackId.isEmpty()) "unknown" else trackId, "UTF-8")
                            val eTrackName = java.net.URLEncoder.encode(if (trackTitle.isEmpty()) "Original Audio" else trackTitle, "UTF-8")
                            val eTrackArtist = java.net.URLEncoder.encode(if (trackArtist.isEmpty()) "Artist" else trackArtist, "UTF-8")
                            val eTrackCover = java.net.URLEncoder.encode(if (trackCover.isEmpty()) "default" else trackCover, "UTF-8")
                            navController.safeNavigate("audio_details/$eTrackId/$eTrackName/$eTrackArtist/$eTrackCover")
                        }
                    )
                }
                composable("calendar") {
                    CalendarScreen(onBack = { navController.safePopBackStack() })
                }
                composable("monetization") {
                    MonetizationScreen(
                        onBack = { navController.safePopBackStack() },
                        onNavigateToWallet = {
                            navController.safeNavigate("wallet") { launchSingleTop = true }
                        }
                    )
                }
                composable("ai_hub") {
                    com.example.ui.AiHubScreen(
                        onNavigateBack = { navController.safePopBackStack() },
                        onNavigateToReel = { reelId ->
                            com.example.viewmodel.VideoViewModel.selectedVideoIdToPlay = reelId
                            navController.safeNavigate("reels") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToAiTool = { aiId ->
                            if (aiId.isNotBlank()) navController.safeNavigate("ai_tool/$aiId")
                        }
                    )
                }
                composable("ai_tool/{aiId}") { backStackEntry ->
                    val aiId = backStackEntry.arguments?.getString("aiId") ?: ""
                    com.example.ui.AiExecutionScreen(
                        aiId = aiId,
                        onNavigateBack = { navController.safePopBackStack() }
                    )
                }
                composable("admin_ai_manager") {
                    com.example.ui.AdminAiManagerScreen(
                        onNavigateBack = { navController.safePopBackStack() }
                    )
                }
                composable("admin_panel") {
                    AdminPanelScreen(
                        onBack = { navController.safePopBackStack() },
                        onNavigateToAiManager = { navController.safeNavigate("admin_ai_manager") },
                        onNavigateToAdsManager = { navController.safeNavigate("admin_ad_manager") }
                    )
                }
                composable("wallet") {
                    WalletScreen(
                        onBack = { navController.safePopBackStack() },
                        onNavigateToMonetization = {
                            navController.safeNavigate("monetization") { launchSingleTop = true }
                        },
                        onNavigateToExclusiveRequests = {
                            navController.safeNavigate("exclusive_requests") { launchSingleTop = true }
                        }
                    )
                }
                composable("exclusive_requests") {
                    ExclusiveRequestsScreen(
                        onBack = { navController.safePopBackStack() },
                        onNavigateToPost = { contentId, contentType ->
                            if (contentId.isNotBlank()) {
                                if (contentType.equals("REEL", true) || contentType.equals("VIDEO", true)) {
                                    com.example.viewmodel.VideoViewModel.selectedVideoIdToPlay = contentId
                                    navController.safeNavigate("reels")
                                } else {
                                    navController.safeNavigate("post_detail/$contentId")
                                }
                            }
                        }
                    )
                }
                composable("call_history") {
                    CallHistoryScreen(
                        onNavigateBack = { navController.safePopBackStack() }
                    )
                }
                composable("audio_room_setup") {
                    AudioRoomSetupScreen(
                        onNavigateBack = { navController.safePopBackStack() }
                    )
                }
                composable("zego_diagnostics") {
                    ZegoDiagnosticsScreen(
                        onNavigateBack = { navController.safePopBackStack() }
                    )
                }
                composable("leaderboard") {
                    CreatorLeaderboardScreen(
                        onBack = { navController.safePopBackStack() },
                        onNavigateToProfile = { uid ->
                            if (uid.isNotBlank()) navController.safeNavigate("profile/$uid")
                        },
                        onNavigateToDailyRewards = { navController.safeNavigate("daily_rewards") }
                    )
                }
                composable("daily_rewards") {
                    DailyRewardsAndLuckyBoxScreen(
                        onBack = { navController.safePopBackStack() },
                        onNavigateToCosmetics = { navController.safeNavigate("cosmetics_store") }
                    )
                }
                composable("cosmetics_store") {
                    CosmeticsAndThemesStoreScreen(
                        onBack = { navController.safePopBackStack() },
                        onNavigateToDailyRewards = { navController.safeNavigate("daily_rewards") }
                    )
                }
                composable("brand_collabs") {
                    BrandCollabsMarketplaceScreen(
                        onBack = { navController.safePopBackStack() },
                        onNavigateToProfile = { uid ->
                            if (uid.isNotBlank()) navController.safeNavigate("profile/$uid")
                        }
                    )
                }
                composable("watch_party/{roomId}") { backStackEntry ->
                    val roomId = backStackEntry.arguments?.getString("roomId") ?: "new"
                    WatchPartyScreen(
                        initialRoomId = roomId,
                        onBack = { navController.safePopBackStack() }
                    )
                }
                composable("templates") {
                    com.example.ui.templates.TemplateBrowserScreen(
                        initialTemplateId = null,
                        onClose = { navController.safePopBackStack() },
                        onTemplateReadyForEditor = { snapshot, uris, template ->
                            if (!com.example.model.RepositoryManager.isRealUserLoggedIn()) {
                                android.widget.Toast.makeText(context, "Please log in to create with templates", android.widget.Toast.LENGTH_SHORT).show()
                                navController.safeNavigate("auth")
                            } else {
                                GlobalState.pendingReadyTemplateSnapshot.value = snapshot
                                GlobalState.pendingReadyTemplateUris.value = uris
                                GlobalState.pendingReadyTemplateInfo.value = template
                                navController.safeNavigate("upload")
                            }
                        }
                    )
                }
                composable("templates/{templateId}") { backStackEntry ->
                    val tplId = backStackEntry.arguments?.getString("templateId")
                    com.example.ui.templates.TemplateBrowserScreen(
                        initialTemplateId = tplId,
                        onClose = { navController.safePopBackStack() },
                        onTemplateReadyForEditor = { snapshot, uris, template ->
                            if (!com.example.model.RepositoryManager.isRealUserLoggedIn()) {
                                android.widget.Toast.makeText(context, "Please log in to create with templates", android.widget.Toast.LENGTH_SHORT).show()
                                navController.safeNavigate("auth")
                            } else {
                                GlobalState.pendingReadyTemplateSnapshot.value = snapshot
                                GlobalState.pendingReadyTemplateUris.value = uris
                                GlobalState.pendingReadyTemplateInfo.value = template
                                navController.safeNavigate("upload")
                            }
                        }
                    )
                }
                composable("smart_templates") {
                    com.example.ui.templates.TemplateBrowserScreen(
                        initialTemplateId = null,
                        onClose = { navController.safePopBackStack() },
                        onTemplateReadyForEditor = { snapshot, uris, template ->
                            if (!com.example.model.RepositoryManager.isRealUserLoggedIn()) {
                                android.widget.Toast.makeText(context, "Please log in to create with templates", android.widget.Toast.LENGTH_SHORT).show()
                                navController.safeNavigate("auth")
                            } else {
                                GlobalState.pendingReadyTemplateSnapshot.value = snapshot
                                GlobalState.pendingReadyTemplateUris.value = uris
                                GlobalState.pendingReadyTemplateInfo.value = template
                                navController.safeNavigate("upload")
                            }
                        }
                    )
                }
                composable("smart_templates/{templateId}") { backStackEntry ->
                    val tplId = backStackEntry.arguments?.getString("templateId")
                    com.example.ui.templates.TemplateBrowserScreen(
                        initialTemplateId = tplId,
                        onClose = { navController.safePopBackStack() },
                        onTemplateReadyForEditor = { snapshot, uris, template ->
                            if (!com.example.model.RepositoryManager.isRealUserLoggedIn()) {
                                android.widget.Toast.makeText(context, "Please log in to create with templates", android.widget.Toast.LENGTH_SHORT).show()
                                navController.safeNavigate("auth")
                            } else {
                                GlobalState.pendingReadyTemplateSnapshot.value = snapshot
                                GlobalState.pendingReadyTemplateUris.value = uris
                                GlobalState.pendingReadyTemplateInfo.value = template
                                navController.safeNavigate("upload")
                            }
                        }
                    )
                }
            }
            
            InstagramUploadBanner(
                modifier = Modifier.align(Alignment.TopCenter)
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = !isOnline,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 52.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xEE1F1F24),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                val backOnline = com.example.utils.NetworkMonitor.recheck(context)
                                GlobalState.homeFeedRefreshTrigger.value = System.currentTimeMillis()
                                if (backOnline) {
                                    android.widget.Toast.makeText(context, "Connected! Refreshing feed...", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "Still offline. Showing cached content.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = "Offline",
                            tint = Color(0xFFFF453A),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "No internet connection • Tap to retry",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
            
        val activeUpdate = effectiveUpdateConfig
        if (activeUpdate != null &&
            activeUpdate.popupEnabled &&
            activeUpdate.url.isNotBlank() &&
            activeUpdate.versionCode > installedVersionCode &&
            dismissedForCurrentSessionVersion != activeUpdate.versionCode
        ) {
            com.example.ui.UpdateDialog(
                updateInfo = activeUpdate,
                onDismissRequest = {
                    dismissedForCurrentSessionVersion = activeUpdate.versionCode
                },
                onUpdateVerifiedInstalled = {
                    installedVersionCode = com.example.utils.AppUpdateManager.getInstalledVersionCode(appContext)
                    dismissedForCurrentSessionVersion = activeUpdate.versionCode
                }
            )
        }
            
        val globalUploadState by com.example.viewmodel.UploadViewModel.globalUploadState.collectAsStateWithLifecycle()
            
        val driveAuthLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                android.widget.Toast.makeText(context, "Google Drive Authorized! Please try uploading again.", android.widget.Toast.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(context, "Google Drive permission denied. Cannot upload.", android.widget.Toast.LENGTH_SHORT).show()
            }
            com.example.viewmodel.UploadViewModel.globalUploadState.value = com.example.viewmodel.UploadState.Idle
        }
            
        LaunchedEffect(globalUploadState) {
            if (globalUploadState is com.example.viewmodel.UploadState.Success || globalUploadState is com.example.viewmodel.UploadState.SuccessWithMessage || globalUploadState is com.example.viewmodel.UploadState.Error) {
                kotlinx.coroutines.delay(3000)
                com.example.viewmodel.UploadViewModel.globalUploadState.value = com.example.viewmodel.UploadState.Idle
            } else if (globalUploadState is com.example.viewmodel.UploadState.NeedsDriveAuth) {
                val intent = (globalUploadState as com.example.viewmodel.UploadState.NeedsDriveAuth).intent
                if (intent != null) {
                    try {
                        driveAuthLauncher.launch(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Unable to launch Drive authorization", android.widget.Toast.LENGTH_SHORT).show()
                        com.example.viewmodel.UploadViewModel.globalUploadState.value = com.example.viewmodel.UploadState.Idle
                    }
                } else {
                    android.widget.Toast.makeText(context, "Drive Auth Intent is null", android.widget.Toast.LENGTH_SHORT).show()
                    com.example.viewmodel.UploadViewModel.globalUploadState.value = com.example.viewmodel.UploadState.Idle
                }
            }
        }
    }
    }

    if (showAccountSwitcher) {
        AccountSwitcherBottomSheet(
            onDismissRequest = { showAccountSwitcher = false },
            onAddAccount = {
                showAccountSwitcher = false
                navController.safeNavigate("auth")
            },
            onAccountSwitched = { _ ->
                showAccountSwitcher = false
                navController.safeNavigate("home") {
                    popUpTo(0) { inclusive = false }
                    launchSingleTop = true
                }
            }
        )
    }

    var isCallMinimizedToPip by remember { mutableStateOf(false) }
    LaunchedEffect(activeCallState?.peerId, activeCallState?.status) {
        if (activeCallState == null) {
            isCallMinimizedToPip = false
        }
    }

    // 100% Full Screen Immersive Instagram Calling Overlay on top of EVERYTHING (unless minimized to PiP)
    activeCallState?.let { call ->
        if (!isCallMinimizedToPip) {
            CallOverlayScreen(
                call = call,
                onMinimizeToPip = {
                    isCallMinimizedToPip = true
                    GlobalPipController.startCallPip(
                        callId = call.peerId,
                        peerName = call.peerDisplayName.ifEmpty { call.peerUsername },
                        peerAvatar = call.peerAvatarUrl,
                        isAudio = !call.isVideo
                    )
                }
            )
        }
    }

    // Floating Picture-in-Picture (PiP) Mini-Player Overlay (Videos, Reels, Live, Calls)
    FloatingPipMiniPlayerOverlay(
        onExpandVideo = { pipItem ->
            if (pipItem.id.isNotBlank()) navController.safeNavigate("post_detail/${pipItem.id}")
        },
        onExpandCall = { _ ->
            isCallMinimizedToPip = false
        },
        onEndCallFromPip = { _ ->
            isCallMinimizedToPip = false
            GlobalCallManager.endCall()
        }
    )
    }

    // Screen-wide Super Hype Particle Burst & Heart Rain Overlay
    SuperHypeRainOverlay()
}

@Composable
fun CallOverlayScreen(
    call: CallState,
    onMinimizeToPip: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var callDurationSeconds by remember { mutableStateOf(0) }
    var isMicMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }

    BackHandler {
        onMinimizeToPip()
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(call.peerId, call.isVideo) {
        val permissions = mutableListOf<String>()
        if (call.isVideo) {
            permissions.add(android.Manifest.permission.CAMERA)
        }
        permissions.add(android.Manifest.permission.RECORD_AUDIO)
        try {
            permissionLauncher.launch(permissions.toTypedArray())
        } catch (_: Exception) {}
    }

    LaunchedEffect(call.status) {
        if (call.status == "connected") {
            while (true) {
                delay(1000L)
                callDurationSeconds++
            }
        }
    }

    val formatTime = { sec: Int ->
        val mins = sec / 60
        val secs = sec % 60
        String.format("%02d:%02d", mins, secs)
    }

    // Concentric pulsating wave animations for Instagram-style incoming/outgoing ringing
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha1"
    )
    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )
    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1E0A2A),
                        Color(0xFF12061A),
                        Color(0xFF0A0310),
                        Color(0xFF000000)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        FloatingHeartsContainer(modifier = Modifier.fillMaxSize())

        // Minimize to Floating PiP Mini-Player Button (Top-Left)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.14f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 22.dp, start = 16.dp)
                .clickable { onMinimizeToPip() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PictureInPictureAlt,
                    contentDescription = "Minimize Call to PiP",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PiP",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Top Status Bar: Call Type Badge & Security Notice
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Icon(
                    imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = null,
                    tint = if (call.isVideo) Color(0xFF69C9D0) else Color(0xFF43D854),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (call.isVideo) "Social India Video Call" else "Social India Audio Call",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "End-to-end encrypted",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp
                )
            }
        }

        // Center Content: Pulsing Avatar & Peer Details
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 60.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(220.dp)
            ) {
                if (call.status == "ringing" || call.status == "calling") {
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .graphicsLayer {
                                scaleX = pulseScale2
                                scaleY = pulseScale2
                                alpha = pulseAlpha2
                            }
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFFEE1D52).copy(alpha = 0.45f), Color.Transparent)
                                ),
                                CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .graphicsLayer {
                                scaleX = pulseScale1
                                scaleY = pulseScale1
                                alpha = pulseAlpha1
                            }
                            .border(2.5.dp, Color(0xFF69C9D0).copy(alpha = pulseAlpha1), CircleShape)
                    )
                }

                // Main Caller/Callee Profile Picture
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .border(
                            3.5.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFF58529),
                                    Color(0xFFDD2A7B),
                                    Color(0xFF8134AF)
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val avatarUrl = call.peerAvatarUrl
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = call.peerUsername,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = call.peerDisplayName.ifEmpty { call.peerUsername },
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "@${call.peerUsername}",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(14.dp))

            val statusText = when (call.status) {
                "ringing" -> if (call.isOutgoing) "Ringing..." else "Incoming call..."
                "calling" -> "Calling..."
                "connecting" -> "Connecting..."
                "connected" -> formatTime(callDurationSeconds)
                "declined" -> "Call Declined"
                "ended" -> "Call Ended"
                else -> ""
            }

            Text(
                text = statusText,
                color = if (call.status == "declined" || call.status == "ended") Color(0xFFFF5252) else Color(0xFF69C9D0),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Bottom Controls - Instagram Style
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp, start = 32.dp, end = 32.dp)
        ) {
            if (call.status == "ringing" && !call.isOutgoing) {
                // Incoming Call: Big Red Decline + Big Green Accept
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                com.example.utils.SoundManager.playClickSound()
                                GlobalCallManager.declineCall()
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFFEE1D52), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Decline",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Decline", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                com.example.utils.SoundManager.playClickSound()
                                GlobalCallManager.acceptCall(context)
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFF43D854), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = "Accept",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Accept", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    }
                }
            } else {
                // Outgoing Calling or In-Call: End Call Button + Quick Controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mute Mic Toggle
                        IconButton(
                            onClick = {
                                isMicMuted = !isMicMuted
                                com.example.utils.SoundManager.playClickSound()
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(if (isMicMuted) Color.White else Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                tint = if (isMicMuted) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Speakerphone Toggle
                        IconButton(
                            onClick = {
                                isSpeakerOn = !isSpeakerOn
                                com.example.utils.SoundManager.playClickSound()
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(if (isSpeakerOn) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                contentDescription = "Speaker",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Heart send button
                        IconButton(
                            onClick = {
                                GlobalCallManager.sendHeart()
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color(0xFFEE1D52).copy(alpha = 0.25f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Heart",
                                tint = Color(0xFFEE1D52),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Large Red End Call Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                com.example.utils.SoundManager.playClickSound()
                                GlobalCallManager.endCall()
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFFEE1D52), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (call.status == "calling") "Cancel" else "End",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
data class FloatingHeartState(
    val id: Long,
    val xOffset: Float,
    val scale: Float,
    val color: Color
)

@Composable
fun FloatingHeartsContainer(modifier: Modifier = Modifier) {
    val hearts = remember { mutableStateListOf<FloatingHeartState>() }
    
    LaunchedEffect(Unit) {
        GlobalCallManager.heartEventFlow.collect { timestamp ->
            val count = (2..4).random()
            for (i in 0 until count) {
                val randomX = (-120..120).random()
                val randomScale = 0.7f + (kotlin.random.Random.nextFloat() * 0.6f)
                val randomColor = listOf(
                    Color(0xFFEE1D52),
                    Color(0xFFFF2D55),
                    Color(0xFFFF9500),
                    Color(0xFFFFCC00),
                    Color(0xFF8E44AD),
                    Color(0xFF3498DB)
                ).random()
                hearts.add(
                    FloatingHeartState(
                        id = timestamp + i + (0..10000).random(),
                        xOffset = randomX.toFloat(),
                        scale = randomScale,
                        color = randomColor
                    )
                )
                delay(100)
            }
        }
    }
    
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        hearts.forEach { heart ->
            key(heart.id) {
                var yOffset by remember { mutableStateOf(0f) }
                var alpha by remember { mutableStateOf(1f) }
                val wobbleSpeed = remember { (1..3).random() }
                val wobbleAmount = remember { (10..30).random() }
                
                LaunchedEffect(Unit) {
                    val duration = 3000
                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < duration) {
                        val elapsed = System.currentTimeMillis() - startTime
                        val progress = elapsed.toFloat() / duration
                        yOffset = -progress * 650f
                        alpha = 1f - (progress * 1.2f).coerceAtMost(1f)
                        delay(16)
                    }
                    hearts.removeAll { it.id == heart.id }
                }
                
                val wobble = kotlin.math.sin(yOffset / 50f * wobbleSpeed) * wobbleAmount
                
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = heart.color.copy(alpha = alpha),
                    modifier = Modifier
                        .offset(x = (heart.xOffset + wobble).dp, y = yOffset.dp)
                        .size((28 * heart.scale).dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomNavigationBar(
    currentRoute: String?,
    userAvatarUrl: String,
    onNavigate: (String) -> Unit,
    onOpenAccountSwitcher: () -> Unit = {}
) {
    val isReels = currentRoute == "reels"
    val isAppDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isDark = isAppDark || isReels

    // iPhone style translucent liquid frosted glass colors (sheer acrylic / smoked crystal)
    val glassBrush = if (isReels) {
        Brush.verticalGradient(
            listOf(
                Color(0x80121218),
                Color(0xA6121218),
                Color(0xCC121218)
            )
        )
    } else if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color(0xB3121218),
                Color(0xCC121218),
                Color(0xE6121218)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xCCFFFFFF),
                Color(0xD9FFFFFF),
                Color(0xEEFFFFFF)
            )
        )
    }

    val contentColor = if (isDark) Color.White else Color(0xFF141416)
    val inactiveColor = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.45f)

    // iPhone top specular highlight edge
    val topBorderBrush = if (isDark) {
        Brush.horizontalGradient(
            listOf(
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.06f)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color.Black.copy(alpha = 0.03f),
                Color.Black.copy(alpha = 0.09f),
                Color.Black.copy(alpha = 0.03f)
            )
        )
    }

    com.example.ui.components.LiquidGlassDock(
        isDark = isDark
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .height(52.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
                // 1. Home
                var lastHomeClickTime by remember { mutableStateOf(0L) }
                IosNavIcon(
                    icon = if (currentRoute == "home") Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Home",
                    isSelected = currentRoute == "home",
                    contentColor = contentColor,
                    inactiveColor = inactiveColor,
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (currentRoute == "home") {
                            GlobalState.homeFeedRefreshTrigger.value = now
                        } else {
                            if (now - lastHomeClickTime < 500L) {
                                GlobalState.homeFeedRefreshTrigger.value = now
                            }
                            lastHomeClickTime = now
                            onNavigate("home")
                        }
                    }
                )

                // 2. Discover / Search
                IosNavIcon(
                    icon = if (currentRoute == "discover") Icons.Default.Search else Icons.Outlined.Search,
                    contentDescription = "Discover",
                    isSelected = currentRoute == "discover",
                    contentColor = contentColor,
                    inactiveColor = inactiveColor,
                    onClick = { onNavigate("discover") }
                )

                // 3. Upload / Create (iOS Instagram squircle AddBox icon)
                IosCreateNavButton(
                    isSelected = currentRoute == "upload",
                    contentColor = contentColor,
                    inactiveColor = inactiveColor,
                    isDark = isDark,
                    onClick = { onNavigate("upload") }
                )

                // 4. Reels (iOS Instagram Video clapper / SmartDisplay)
                IosNavIcon(
                    icon = if (currentRoute == "reels") Icons.Filled.SmartDisplay else Icons.Outlined.SmartDisplay,
                    contentDescription = "Reels",
                    isSelected = currentRoute == "reels",
                    contentColor = contentColor,
                    inactiveColor = inactiveColor,
                    onClick = { onNavigate("reels") }
                )

                // 5. Profile with Instagram Selection Pop Bounce & Gradient Ring
                val isProfileSelected = currentRoute == "profile"
                val reducedMotion = com.example.ui.motion.rememberReducedMotionEnabled()
                val profileBounce = remember { androidx.compose.animation.core.Animatable(1f) }
                LaunchedEffect(isProfileSelected, reducedMotion) {
                    if (isProfileSelected && !reducedMotion) {
                        profileBounce.snapTo(0.92f)
                        profileBounce.animateTo(1.18f, com.example.ui.motion.SocialMotion.floatSpec(com.example.ui.motion.SocialMotion.DurationQuick))
                        profileBounce.animateTo(1.08f, com.example.ui.motion.SocialMotion.springPop())
                    } else {
                        profileBounce.animateTo(
                            if (isProfileSelected) 1.08f else 1.0f,
                            com.example.ui.motion.SocialMotion.springSnappy(reducedMotion)
                        )
                    }
                }
                val profileScale = profileBounce.value
                val dotAlpha by animateFloatAsState(
                    targetValue = if (isProfileSelected) 1.0f else 0.0f,
                    animationSpec = com.example.ui.motion.SocialMotion.floatSpec(com.example.ui.motion.SocialMotion.DurationFast, reducedMotion),
                    label = "profile_dot"
                )

                val instagramRing = Brush.sweepGradient(
                    listOf(
                        Color(0xFFFBAA47),
                        Color(0xFFD91A46),
                        Color(0xFFA60F93),
                        Color(0xFFFBAA47)
                    )
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .bounceClick()
                        .combinedClickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                com.example.utils.SoundManager.playClickSound()
                                onNavigate("profile")
                            },
                            onLongClick = {
                                com.example.utils.SoundManager.playClickSound()
                                onOpenAccountSwitcher()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = profileScale
                                    scaleY = profileScale
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (userAvatarUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = userAvatarUrl,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier
                                        .size(27.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (isProfileSelected) 2.2.dp else 1.dp,
                                            brush = if (isProfileSelected) instagramRing else Brush.linearGradient(listOf(inactiveColor.copy(alpha = 0.4f), inactiveColor.copy(alpha = 0.4f))),
                                            shape = CircleShape
                                        ),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = if (isProfileSelected) Icons.Filled.Person else Icons.Outlined.Person,
                                    contentDescription = "Profile",
                                    tint = if (isProfileSelected) contentColor else inactiveColor,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        // Tiny iOS active indicator dot
                        Box(
                            modifier = Modifier
                                .size(3.5.dp)
                                .graphicsLayer { alpha = dotAlpha }
                                .clip(CircleShape)
                                .background(contentColor)
                        )
                    }
                }
            }

            // Authentic iPhone Home Indicator Swipe Bar
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .width(134.dp)
                    .height(4.5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background((if (isDark) Color.White else Color.Black).copy(alpha = 0.30f))
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
}

@Composable
fun RowScope.IosCreateNavButton(
    isSelected: Boolean,
    contentColor: Color,
    inactiveColor: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val reducedMotion = com.example.ui.motion.rememberReducedMotionEnabled()
    val bounceScale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(isSelected, reducedMotion) {
        if (isSelected && !reducedMotion) {
            bounceScale.snapTo(0.92f)
            bounceScale.animateTo(1.18f, com.example.ui.motion.SocialMotion.floatSpec(com.example.ui.motion.SocialMotion.DurationQuick))
            bounceScale.animateTo(1.06f, com.example.ui.motion.SocialMotion.springPop())
        } else {
            bounceScale.animateTo(
                if (isSelected) 1.06f else 1.0f,
                com.example.ui.motion.SocialMotion.springSnappy(reducedMotion)
            )
        }
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .bounceClick()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                com.example.utils.SoundManager.playClickSound()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = bounceScale.value
                    scaleY = bounceScale.value
                }
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.8.dp,
                    color = if (isSelected) contentColor else inactiveColor,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create",
                tint = if (isSelected) contentColor else inactiveColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun RowScope.IosNavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    contentColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit
) {
    val reducedMotion = com.example.ui.motion.rememberReducedMotionEnabled()
    val bounceScale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(isSelected, reducedMotion) {
        if (isSelected && !reducedMotion) {
            bounceScale.snapTo(0.92f)
            bounceScale.animateTo(1.18f, com.example.ui.motion.SocialMotion.floatSpec(com.example.ui.motion.SocialMotion.DurationQuick))
            bounceScale.animateTo(1.06f, com.example.ui.motion.SocialMotion.springPop())
        } else {
            bounceScale.animateTo(
                if (isSelected) 1.06f else 1.0f,
                com.example.ui.motion.SocialMotion.springSnappy(reducedMotion)
            )
        }
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .bounceClick()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                com.example.utils.SoundManager.playClickSound()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) contentColor else inactiveColor,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = bounceScale.value
                    scaleY = bounceScale.value
                }
                .size(26.dp)
        )
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(text = title, color = Color.White, style = MaterialTheme.typography.headlineMedium)
    }
}
@androidx.compose.runtime.Composable
fun LocalCameraPreview(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    isFrontCamera: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val previewView = androidx.compose.runtime.remember { androidx.camera.view.PreviewView(context).apply { scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                    implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE } }
    
    androidx.compose.runtime.LaunchedEffect(isFrontCamera) {
        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val cameraSelector = if (isFrontCamera) androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA else androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
            try {
                if (cameraProvider.hasCamera(cameraSelector)) {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                } else {
                    // Try fallback
                    val fallback = if (cameraProvider.hasCamera(androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA)) {
                        androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
                    } else if (cameraProvider.hasCamera(androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA
                    } else null
                    
                    if (fallback != null) {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, fallback, preview)
                    }
                }
            } catch (exc: Exception) {
                android.util.Log.e("CameraPreview", "Use case binding failed", exc)
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }
    
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            try {
                val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                }
            } catch (_: Exception) {}
        }
    }
    
    androidx.compose.ui.viewinterop.AndroidView({ previewView }, modifier = modifier)
}



