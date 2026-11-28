package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ReelsPreferencesManager {
    private const val PREFS_NAME = "reels_playback_prefs"
    private const val KEY_SPEED = "playback_speed"
    private const val KEY_AUTO_SCROLL = "auto_scroll"
    private const val KEY_LOOP = "loop_video"
    private const val KEY_CAPTIONS = "captions_enabled"
    private const val KEY_DATA_SAVER = "data_saver"
    private const val KEY_MUTED = "is_muted"
    private const val KEY_SHOW_EARN_BUTTON = "show_earn_button"

    private lateinit var prefs: SharedPreferences

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _autoScrollEnabled = MutableStateFlow(false)
    val autoScrollEnabled: StateFlow<Boolean> = _autoScrollEnabled.asStateFlow()

    private val _loopVideo = MutableStateFlow(true)
    val loopVideo: StateFlow<Boolean> = _loopVideo.asStateFlow()

    private val _captionsEnabled = MutableStateFlow(false)
    val captionsEnabled: StateFlow<Boolean> = _captionsEnabled.asStateFlow()

    private val _dataSaverEnabled = MutableStateFlow(false)
    val dataSaverEnabled: StateFlow<Boolean> = _dataSaverEnabled.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isFullScreen = MutableStateFlow(false)
    val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    private val _showEarnButton = MutableStateFlow(true)
    val showEarnButton: StateFlow<Boolean> = _showEarnButton.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _playbackSpeed.value = prefs.getFloat(KEY_SPEED, 1.0f)
        _autoScrollEnabled.value = prefs.getBoolean(KEY_AUTO_SCROLL, false)
        _loopVideo.value = prefs.getBoolean(KEY_LOOP, true)
        _captionsEnabled.value = prefs.getBoolean(KEY_CAPTIONS, false)
        _dataSaverEnabled.value = prefs.getBoolean(KEY_DATA_SAVER, false)
        _isMuted.value = prefs.getBoolean(KEY_MUTED, false)
        _showEarnButton.value = prefs.getBoolean(KEY_SHOW_EARN_BUTTON, true)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        if (::prefs.isInitialized) {
            prefs.edit().putFloat(KEY_SPEED, speed).apply()
        }
    }

    fun toggleAutoScroll(): Boolean {
        val newValue = !_autoScrollEnabled.value
        _autoScrollEnabled.value = newValue
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_AUTO_SCROLL, newValue).apply()
        }
        return newValue
    }

    fun setAutoScroll(enabled: Boolean) {
        _autoScrollEnabled.value = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_AUTO_SCROLL, enabled).apply()
        }
    }

    fun toggleLoopVideo(): Boolean {
        val newValue = !_loopVideo.value
        _loopVideo.value = newValue
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_LOOP, newValue).apply()
        }
        return newValue
    }

    fun toggleCaptions(): Boolean {
        val newValue = !_captionsEnabled.value
        _captionsEnabled.value = newValue
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_CAPTIONS, newValue).apply()
        }
        return newValue
    }

    fun toggleDataSaver(): Boolean {
        val newValue = !_dataSaverEnabled.value
        _dataSaverEnabled.value = newValue
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_DATA_SAVER, newValue).apply()
        }
        return newValue
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_MUTED, muted).apply()
        }
    }

    fun toggleMute(): Boolean {
        val newValue = !_isMuted.value
        setMuted(newValue)
        return newValue
    }

    fun toggleFullScreen(): Boolean {
        val newValue = !_isFullScreen.value
        _isFullScreen.value = newValue
        return newValue
    }

    fun setFullScreen(fullScreen: Boolean) {
        _isFullScreen.value = fullScreen
    }

    fun setShowEarnButton(show: Boolean) {
        _showEarnButton.value = show
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_SHOW_EARN_BUTTON, show).apply()
        }
    }

    fun toggleShowEarnButton(): Boolean {
        val newValue = !_showEarnButton.value
        setShowEarnButton(newValue)
        return newValue
    }
}
