package com.example.utils

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.Ringtone

object SoundManager {
    private var appContext: Context? = null
    private var audioManager: AudioManager? = null
    private var ringtone: Ringtone? = null

    fun init(context: Context) {
        try {
            appContext = context.applicationContext
            audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        } catch (_: Throwable) {
            // Ignore init errors safely
        }
    }

    fun playClickSound() {
        try {
            audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.5f)
        } catch (_: Throwable) {}
    }

    fun playMessageSendSound(context: Context) {
        try {
            val ctx = appContext ?: context.applicationContext
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(ctx, uri)
            r?.play()
        } catch (_: Throwable) {}
    }

    fun playSplashSound(context: Context) {
        try {
            val ctx = appContext ?: context.applicationContext
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(ctx, uri)
            r?.play()
        } catch (_: Throwable) {}
    }

    fun startRingtone(context: Context, isOutgoing: Boolean) {
        try {
            stopRingtone()
            val ctx = appContext ?: context.applicationContext
            val uri = if (isOutgoing) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            ringtone = RingtoneManager.getRingtone(ctx, uri)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            ringtone?.play()
        } catch (_: Throwable) {}
    }

    fun stopRingtone() {
        try {
            ringtone?.stop()
            ringtone = null
        } catch (_: Throwable) {}
    }

    fun release() {
        try {
            stopRingtone()
            audioManager = null
            appContext = null
        } catch (_: Throwable) {}
    }
}
