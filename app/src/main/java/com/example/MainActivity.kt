package com.example
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import com.example.ui.ShortsApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : androidx.fragment.app.FragmentActivity() {

  private val requestPermissionLauncher = registerForActivityResult(
      androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
  ) { _ -> }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Hardware-accelerated rendering & GPU utilization
    try {
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
    } catch (_: Exception) {}

    // Enable Peak Display Refresh Rate (90Hz / 120Hz / 144Hz) for Butter-Smooth 60/120fps UI
    try {
        val currentDisplay = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        val maxMode = currentDisplay?.supportedModes?.maxByOrNull { it.refreshRate }
        if (maxMode != null && maxMode.refreshRate > 60f) {
            val params = window.attributes
            params.preferredDisplayModeId = maxMode.modeId
            window.attributes = params
        }
    } catch (_: Exception) {}

    // RepositoryManager, SoundManager, and ThemePreference are already initialized in MyApplication
    androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_ERROR)
    
    try {
        com.example.notification.SocialIndiaNotificationManager.setupNotificationChannels(this)
        val uid = com.example.model.RepositoryManager.getCurrentUserId()
        if (uid.isNotEmpty()) {
            com.example.model.RepositoryManager.attachCurrentUserRealtimeListener(uid)
            com.example.notification.SocialIndiaNotificationManager.attachUserListener(this, uid)
        }
    } catch(e: Exception) {
        android.util.Log.w("MainActivity", "Notification setup error: ${e.message}")
    }
    
    // Handle initial intent if app is cold started from notification action click
    intent?.let { handleNotificationIntent(it) }
    
    // Dynamic request for notifications on Android 13+
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            try { 
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    
    enableEdgeToEdge(
        statusBarStyle = androidx.activity.SystemBarStyle.auto(
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT
        ),
        navigationBarStyle = androidx.activity.SystemBarStyle.auto(
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT
        )
    )
    setContent {
      val isDarkTheme by com.example.utils.ThemePreference.isDarkTheme.collectAsStateWithLifecycle()
      
      val view = androidx.compose.ui.platform.LocalView.current
      if (!view.isInEditMode) {
          androidx.compose.runtime.SideEffect {
              try {
                  val win = this@MainActivity.window
                  androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
                  win.statusBarColor = android.graphics.Color.TRANSPARENT
                  win.navigationBarColor = android.graphics.Color.TRANSPARENT
                  val insetsController = androidx.core.view.WindowCompat.getInsetsController(win, view)
                  insetsController.isAppearanceLightStatusBars = !isDarkTheme
                  insetsController.isAppearanceLightNavigationBars = !isDarkTheme
              } catch (_: Exception) {}
          }
      }

      MyApplicationTheme(darkTheme = isDarkTheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            var showSplash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
            if (showSplash) {
                com.example.ui.SplashScreen(onSplashFinished = { showSplash = false })
            } else {
                ShortsApp()
            }
        }
      }
    }
  }
  override fun onResume() {
    super.onResume()
    val uid = com.example.model.RepositoryManager.getCurrentUserId()
    com.example.security.FaceLockManager.onAppResumed(this, uid)
  }

  override fun onPause() {
    super.onPause()
    com.example.security.FaceLockManager.onAppPaused()
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleNotificationIntent(intent)
  }
  private fun handleNotificationIntent(intent: android.content.Intent) {
    val deepLinkRoute = intent.getStringExtra("deep_link_route")
    if (!deepLinkRoute.isNullOrEmpty()) {
        com.example.ui.GlobalState.pendingDeepLinkRoute.value = deepLinkRoute
    }
    val action = intent.getStringExtra("notification_action")
    val type = intent.getStringExtra("notification_type")
    val senderId = intent.getStringExtra("sender_id") ?: intent.getStringExtra("chat_user_id")
    val targetId = intent.getStringExtra("target_id")
    val senderUsername = intent.getStringExtra("sender_username") ?: "User"

    if (action != null) {
        when (action) {
            "view_analytics" -> {
                com.example.ui.GlobalState.pendingDeepLinkRoute.value = "professional_dashboard"
                android.widget.Toast.makeText(this, "📊 Opening Reel Analytics...", android.widget.Toast.LENGTH_SHORT).show()
            }
            "like_back" -> {
                if (!targetId.isNullOrEmpty()) {
                    com.example.ui.GlobalState.pendingDeepLinkRoute.value = "post_detail/$targetId"
                } else {
                    com.example.ui.GlobalState.pendingDeepLinkRoute.value = "inbox/notifications"
                }
                android.widget.Toast.makeText(this, "❤️ Liked video back!", android.widget.Toast.LENGTH_SHORT).show()
            }
            "reply_now" -> {
                com.example.ui.GlobalState.pendingDeepLinkRoute.value = if (!senderId.isNullOrEmpty()) "inbox_chat/$senderId" else "inbox/messages"
            }
            "accept_call" -> {
                com.example.ui.GlobalState.pendingDeepLinkRoute.value = if (!senderId.isNullOrEmpty()) "inbox_chat/$senderId" else "inbox/messages"
            }
            "decline_call" -> {
                com.example.ui.GlobalCallManager.declineCall()
                android.widget.Toast.makeText(this, "Call declined", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    } else if (type != null) {
        when (type) {
            "photo", "video", "voice", "chat" -> {
                if (deepLinkRoute.isNullOrEmpty()) {
                    com.example.ui.GlobalState.pendingDeepLinkRoute.value = if (!senderId.isNullOrEmpty()) "inbox_chat/$senderId" else "inbox/messages"
                }
            }
            "reel" -> {
                if (deepLinkRoute.isNullOrEmpty()) {
                    if (!targetId.isNullOrEmpty()) {
                        com.example.viewmodel.VideoViewModel.selectedVideoIdToPlay = targetId
                    }
                    com.example.ui.GlobalState.pendingDeepLinkRoute.value = "reels"
                }
            }
            "post_like", "post_comment" -> {
                if (deepLinkRoute.isNullOrEmpty()) {
                    com.example.ui.GlobalState.pendingDeepLinkRoute.value = if (!targetId.isNullOrEmpty()) "post_detail/$targetId" else "inbox/notifications"
                }
            }
            "story_like" -> {
                if (deepLinkRoute.isNullOrEmpty()) {
                    if (!targetId.isNullOrEmpty()) {
                        com.example.ui.GlobalState.targetStoryId.value = targetId
                    }
                    com.example.ui.GlobalState.pendingDeepLinkRoute.value = "home"
                }
            }
            "live_started" -> {
                if (deepLinkRoute.isNullOrEmpty()) {
                    com.example.ui.GlobalState.pendingDeepLinkRoute.value = "home"
                }
            }
            "incoming_call" -> {
                if (deepLinkRoute.isNullOrEmpty()) {
                    com.example.ui.GlobalState.pendingDeepLinkRoute.value = if (!senderId.isNullOrEmpty()) "inbox_chat/$senderId" else "inbox/messages"
                }
            }
        }
    }
  }
  override fun onStart() {
    super.onStart()
    updateUserStatus("online")
  }
  override fun onStop() {
    super.onStop()
    updateUserStatus("offline")
  }
  private fun updateUserStatus(status: String) {
    try {
      val userId = com.example.model.RepositoryManager.getCurrentUserId()
      if (userId.isNotEmpty()) {
          com.example.model.RepositoryManager.firestore.collection("users").document(userId)
              .set(
                  mapOf("status" to status, "last_seen" to System.currentTimeMillis()),
                  com.google.firebase.firestore.SetOptions.merge()
              )
              .addOnFailureListener { }
      }
    } catch (e: Exception) {
      // Ignore network/status update errors
    }
  }
}
