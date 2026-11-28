package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.fragment.app.FragmentActivity
import com.example.model.RepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object FaceLockManager {
    private const val PREFS_NAME = "social_india_face_lock_prefs"
    private const val KEY_PREFIX_FACE_LOCK = "face_lock_enabled_"

    private val scope = CoroutineScope(Dispatchers.IO)
    private var prefs: SharedPreferences? = null

    // App unlock state (in-memory, reset when locked or app goes to background)
    private val _isUnlocked = MutableStateFlow(true)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    // Timestamp when app went to background
    private var backgroundTimestamp: Long = 0L

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return prefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
    }

    /**
     * Checks if Face Lock is enabled for the specific Firebase UID.
     * Each account maintains its preference independently.
     */
    fun isFaceLockEnabled(context: Context, uid: String): Boolean {
        if (uid.isEmpty()) return false
        return getPrefs(context).getBoolean(KEY_PREFIX_FACE_LOCK + uid, false)
    }

    /**
     * Enables or disables Face Lock for the specified Firebase UID.
     * Updates SharedPreferences and syncs to Firestore user document.
     */
    fun setFaceLockEnabled(context: Context, uid: String, enabled: Boolean) {
        if (uid.isEmpty()) return
        getPrefs(context).edit().putBoolean(KEY_PREFIX_FACE_LOCK + uid, enabled).apply()
        
        // Sync to Firestore asynchronously
        scope.launch {
            try {
                RepositoryManager.firestore.collection("users").document(uid).update("faceLockEnabled", enabled)
            } catch (_: Exception) {}
        }

        // If enabled on current active user, app remains unlocked for current session
        if (enabled) {
            _isUnlocked.value = true
        }
    }

    /**
     * Called when the active user switches in MultiAccountManager.
     * Checks the newly active account's Face Lock preference.
     */
    fun onAccountSwitched(context: Context, newUid: String) {
        if (newUid.isEmpty()) {
            _isUnlocked.value = true
            return
        }
        val isEnabled = isFaceLockEnabled(context, newUid)
        if (isEnabled) {
            _isUnlocked.value = false
        } else {
            _isUnlocked.value = true
        }
    }

    /**
     * Called when user logs out. Invalidates biometric unlock session.
     */
    fun onLogout() {
        _isUnlocked.value = true
        backgroundTimestamp = 0L
    }

    /**
     * Called when app goes into background.
     */
    fun onAppPaused() {
        backgroundTimestamp = System.currentTimeMillis()
    }

    /**
     * Called when app comes to foreground.
     * If Face Lock is enabled for active user and background timeout passed, locks the app.
     */
    fun onAppResumed(context: Context, activeUid: String) {
        if (activeUid.isEmpty()) return
        val isEnabled = isFaceLockEnabled(context, activeUid)
        if (isEnabled) {
            // If backgrounded for more than 1 second, require authentication
            if (System.currentTimeMillis() - backgroundTimestamp > 1000) {
                _isUnlocked.value = false
            }
        } else {
            _isUnlocked.value = true
        }
    }

    fun unlock() {
        _isUnlocked.value = true
    }

    fun lock() {
        _isUnlocked.value = false
    }

    /**
     * Triggers the secure Android BiometricPrompt system.
     * Raw face/biometric data is handled entirely by Android OS hardware and never stored.
     * Supports device PIN/password fallback.
     */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        SecurityBiometricHelper.promptBiometric(
            activity = activity,
            title = "Unlock Social India",
            subtitle = "Verify your face or biometric to continue",
            negativeButtonText = "Use PIN or Password",
            allowDeviceCredentialFallback = true,
            onSuccess = {
                _isUnlocked.value = true
                onSuccess()
            },
            onError = { code, err ->
                onError(err.toString())
            },
            onFailed = {
                onError("Biometric authentication not recognized. Try again or use PIN.")
            }
        )
    }
}
