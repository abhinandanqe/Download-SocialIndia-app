package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.R
import com.example.model.RepositoryManager
import java.util.Collections
import java.util.LinkedHashMap

/**
 * High-performance, idempotent Messenger Sound & Notification Engine.
 * Ensures:
 * 1. Sent sound plays immediately upon verified Firestore message save.
 * 2. Incoming sound plays on real-time arrival without duplicating.
 * 3. Strict anti-duplication tracking by message ID.
 * 4. Respects per-chat mute and global sound/vibration toggles.
 * 5. Scoped cleanly to current active Firebase UID.
 */
object MessengerSoundManager {
    private const val TAG = "MessengerSoundManager"
    private const val PREFS_PREFIX = "si_messenger_sound_"

    // In-memory LRU cache of recently processed message IDs (max 500)
    private val processedMessageIds: MutableSet<String> = Collections.synchronizedSet(
        Collections.newSetFromMap(
            object : LinkedHashMap<String, Boolean>(200, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                    return size > 500
                }
            }
        )
    )

    // IDs sent locally so we never play an incoming chime for our own messages
    private val locallySentMessageIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf<String>())

    private fun getPrefs(context: Context): SharedPreferences {
        val uid = RepositoryManager.getCurrentUserId().ifEmpty { "global" }
        return context.getSharedPreferences("$PREFS_PREFIX$uid", Context.MODE_PRIVATE)
    }

    // --- Sound Settings ---
    fun isMessageSoundsEnabled(context: Context): Boolean = getPrefs(context).getBoolean("msg_sounds_enabled", true)
    fun setMessageSoundsEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean("msg_sounds_enabled", enabled).apply()

    fun isNotificationSoundsEnabled(context: Context): Boolean = getPrefs(context).getBoolean("notif_sounds_enabled", true)
    fun setNotificationSoundsEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean("notif_sounds_enabled", enabled).apply()

    fun isVibrationEnabled(context: Context): Boolean = getPrefs(context).getBoolean("vibration_enabled", true)
    fun setVibrationEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean("vibration_enabled", enabled).apply()

    fun isGlobalMute(context: Context): Boolean = getPrefs(context).getBoolean("global_mute", false)
    fun setGlobalMute(context: Context, muted: Boolean) = getPrefs(context).edit().putBoolean("global_mute", muted).apply()

    fun isChatMuted(context: Context, otherUserId: String): Boolean {
        if (otherUserId.isEmpty()) return false
        return getPrefs(context).getBoolean("mute_chat_$otherUserId", false)
    }

    fun setChatMuted(context: Context, otherUserId: String, muted: Boolean) {
        if (otherUserId.isEmpty()) return
        getPrefs(context).edit().putBoolean("mute_chat_$otherUserId", muted).apply()
    }

    fun isPreviewEnabled(context: Context): Boolean = getPrefs(context).getBoolean("preview_enabled", true)
    fun setPreviewEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean("preview_enabled", enabled).apply()

    /**
     * Records a message as locally sent so it is never processed as an incoming message.
     */
    fun markLocallySent(messageId: String) {
        if (messageId.isNotEmpty()) {
            locallySentMessageIds.add(messageId)
            processedMessageIds.add(messageId)
        }
    }

    /**
     * Plays the subtle outgoing sent sound immediately upon verified server confirmation.
     */
    fun playMessageSentSound(context: Context) {
        if (isGlobalMute(context) || !isMessageSoundsEnabled(context)) return

        try {
            val mp = MediaPlayer.create(context.applicationContext, R.raw.click_sound)
            mp?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build()
                )
                setVolume(0.45f, 0.45f) // Subtle outgoing click
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sent sound playback: ${e.message}")
        }
    }

    /**
     * Processes an incoming message event.
     * Returns true if sound/vibration was played, false if duplicate or muted.
     */
    fun onIncomingMessageReceived(
        context: Context,
        messageId: String,
        senderId: String,
        isCurrentlyInChat: Boolean
    ): Boolean {
        val currentUserId = RepositoryManager.getCurrentUserId()
        if (senderId.isEmpty() || senderId == currentUserId) return false
        if (messageId.isNotEmpty() && (locallySentMessageIds.contains(messageId) || processedMessageIds.contains(messageId))) {
            return false // Deduplicated
        }
        if (messageId.isNotEmpty()) {
            processedMessageIds.add(messageId)
        }

        // Check mute rules
        if (isGlobalMute(context)) return false
        if (isChatMuted(context, senderId)) return false
        if (!isMessageSoundsEnabled(context)) return false

        // Play incoming chime
        try {
            val mp = MediaPlayer.create(context.applicationContext, R.raw.social_india_notification)
            mp?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(if (isCurrentlyInChat) AudioAttributes.USAGE_ASSISTANCE_SONIFICATION else AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
                val vol = if (isCurrentlyInChat) 0.6f else 0.85f
                setVolume(vol, vol)
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Incoming sound playback: ${e.message}")
        }

        // Haptic feedback if enabled
        if (isVibrationEnabled(context)) {
            triggerSubtleHaptic(context)
        }

        return true
    }

    private fun triggerSubtleHaptic(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30), intArrayOf(0, 80, 0, 120), -1)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Haptic trigger: ${e.message}")
        }
    }
}
