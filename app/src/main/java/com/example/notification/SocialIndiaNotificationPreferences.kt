package com.example.notification

import android.content.Context
import android.content.SharedPreferences
import com.example.model.RepositoryManager

/**
 * Persists and retrieves Notification Preferences scoped cleanly by Firebase UID.
 */
object SocialIndiaNotificationPreferences {

    private const val PREFS_PREFIX = "si_notification_prefs_"

    private fun getPrefs(context: Context): SharedPreferences {
        val uid = RepositoryManager.getCurrentUserId().ifEmpty { "anonymous" }
        return context.getSharedPreferences("$PREFS_PREFIX$uid", Context.MODE_PRIVATE)
    }

    // Master toggles
    fun isPushEnabled(context: Context): Boolean = getPrefs(context).getBoolean("push_enabled", true)
    fun setPushEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean("push_enabled", enabled).apply()

    fun isInAppEnabled(context: Context): Boolean = getPrefs(context).getBoolean("in_app_enabled", true)
    fun setInAppEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean("in_app_enabled", enabled).apply()

    // Sound: "social_india", "default", "silent"
    fun getSoundMode(context: Context): String = getPrefs(context).getString("sound_mode", "social_india") ?: "social_india"
    fun setSoundMode(context: Context, mode: String) = getPrefs(context).edit().putString("sound_mode", mode).apply()

    fun isVibrationEnabled(context: Context): Boolean = getPrefs(context).getBoolean("vibration_enabled", true)
    fun setVibrationEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean("vibration_enabled", enabled).apply()

    fun isMessagePreviewEnabled(context: Context): Boolean = getPrefs(context).getBoolean("message_preview_enabled", true)
    fun setMessagePreviewEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean("message_preview_enabled", enabled).apply()

    fun isBadgeEnabled(context: Context): Boolean = getPrefs(context).getBoolean("badge_enabled", true)
    fun setBadgeEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean("badge_enabled", enabled).apply()

    // Category granular toggles
    fun isCategoryEnabled(context: Context, categoryKey: String): Boolean {
        return getPrefs(context).getBoolean("cat_$categoryKey", true)
    }

    fun setCategoryEnabled(context: Context, categoryKey: String, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("cat_$categoryKey", enabled).apply()
    }
}
