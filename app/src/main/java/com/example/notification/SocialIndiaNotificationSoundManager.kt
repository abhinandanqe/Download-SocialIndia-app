package com.example.notification

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.R

/**
 * SocialIndiaNotificationSoundManager
 * 
 * Manages original Social India notification sounds and haptics.
 * Uses res/raw/social_india_notification.wav for a signature, pleasant chime.
 */
object SocialIndiaNotificationSoundManager {
    private const val TAG = "SoundManager"
    private const val PREFS_NAME = "social_india_sound_settings"
    private const val KEY_SOUND_ENABLED = "key_social_india_sound_enabled"
    private const val KEY_VIBRATE_ENABLED = "key_vibrate_enabled"
    private const val KEY_MESSAGE_SOUND = "key_message_sound"
    private const val KEY_CREATOR_SOUND = "key_creator_sound"
    private const val KEY_AI_SOUND = "key_ai_sound"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isSoundEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    fun isVibrateEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VIBRATE_ENABLED, true)
    }

    fun setVibrateEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VIBRATE_ENABLED, enabled).apply()
    }

    /**
     * Returns the Uri for the original Social India notification sound resource.
     */
    fun getNotificationSoundUri(context: Context): Uri {
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath("${R.raw.social_india_notification}")
            .build()
    }

    /**
     * Plays the signature Social India chime in-app (e.g. on foreground notification).
     */
    fun playInAppNotificationSound(context: Context) {
        if (!isSoundEnabled(context)) return

        try {
            val mp = MediaPlayer.create(context, R.raw.social_india_notification)
            mp?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
                setOnCompletionListener { player ->
                    player.release()
                }
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing notification sound: ${e.message}")
        }

        if (isVibrateEnabled(context)) {
            triggerNotificationHaptic(context)
        }
    }

    /**
     * Performs a gentle, pleasant notification haptic feedback.
     */
    private fun triggerNotificationHaptic(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 40, 60, 40)
                    val amplitudes = intArrayOf(0, 180, 0, 120)
                    it.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(50)
                }
            }
        } catch (e: Exception) {
            // non-fatal
        }
    }
}
