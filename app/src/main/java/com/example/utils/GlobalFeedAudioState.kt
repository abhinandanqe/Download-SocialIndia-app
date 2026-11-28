package com.example.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized Global Feed Audio State manager.
 * Preserves user's manual sound preference (Muted vs Unmuted) across the entire Home Feed session,
 * survives item recycling, LazyColumn scrolling, screen recomposition, and navigation between screens.
 */
object GlobalFeedAudioState {
    // True if user has sound enabled on Home Feed. Default is true (sound ON).
    // User can tap speaker or video to toggle Mute/Unmute.
    private val _isSoundEnabled = MutableStateFlow(true)
    val isSoundEnabled: StateFlow<Boolean> = _isSoundEnabled.asStateFlow()

    // Currently active playing video ID in Home Feed (ensures exactly 1 active audio stream)
    private val _currentPlayingVideoId = MutableStateFlow<String?>(null)
    val currentPlayingVideoId: StateFlow<String?> = _currentPlayingVideoId.asStateFlow()

    fun isSoundOn(): Boolean = _isSoundEnabled.value

    fun isMuted(): Boolean = !_isSoundEnabled.value

    fun setSoundEnabled(enabled: Boolean) {
        _isSoundEnabled.value = enabled
    }

    fun toggleSound(): Boolean {
        val newState = !_isSoundEnabled.value
        _isSoundEnabled.value = newState
        return newState
    }

    fun setActivePlayingVideoId(videoId: String?) {
        _currentPlayingVideoId.value = videoId
    }
}
