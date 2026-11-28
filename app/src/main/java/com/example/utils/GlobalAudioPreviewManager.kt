package com.example.utils

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global single-instance audio preview player for Music Pickers, User Notes audio playback,
 * and audio previews across the application.
 * Ensures only one preview plays at any time, prevents memory leaks, and handles lifecycle gracefully.
 */
object GlobalAudioPreviewManager {
    private const val TAG = "AudioPreviewManager"
    
    private var exoPlayer: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    private var activeCallback: ((isPlaying: Boolean, isLoading: Boolean, isError: Boolean) -> Unit)? = null

    private fun getOrCreatePlayer(context: Context): ExoPlayer {
        val existing = exoPlayer
        if (existing != null) return existing

        val player = ExoPlayerUtils.createExoPlayer(context.applicationContext, isMusic = true)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _isLoading.value = true
                        _isError.value = false
                        notifyState()
                    }
                    Player.STATE_READY -> {
                        _isLoading.value = false
                        _isError.value = false
                        _isPlaying.value = player.isPlaying
                        notifyState()
                    }
                    Player.STATE_ENDED -> {
                        _isLoading.value = false
                        _isPlaying.value = false
                        _isError.value = false
                        notifyState()
                    }
                    Player.STATE_IDLE -> {
                        _isLoading.value = false
                        notifyState()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                notifyState()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Audio preview playback error: ${error.message}", error)
                _isLoading.value = false
                _isPlaying.value = false
                _isError.value = true
                notifyState()
            }
        })

        exoPlayer = player
        return player
    }

    private fun notifyState() {
        activeCallback?.invoke(_isPlaying.value, _isLoading.value, _isError.value)
    }

    fun playOrToggle(
        context: Context,
        url: String,
        onStateChanged: ((isPlaying: Boolean, isLoading: Boolean, isError: Boolean) -> Unit)? = null
    ) {
        if (url.isBlank()) return

        activeCallback = onStateChanged

        val player = getOrCreatePlayer(context)

        if (_currentUrl.value == url) {
            // Same track clicked -> toggle play / pause
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
            } else {
                if (_isError.value) {
                    // Retry playback
                    playFresh(context, url)
                } else {
                    player.play()
                    _isPlaying.value = true
                }
            }
            notifyState()
            return
        }

        // New track requested
        playFresh(context, url)
    }

    private fun playFresh(context: Context, url: String) {
        val player = getOrCreatePlayer(context)
        _currentUrl.value = url
        _isLoading.value = true
        _isError.value = false
        _isPlaying.value = false
        notifyState()

        try {
            player.stop()
            player.clearMediaItems()
            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio playback for $url", e)
            _isLoading.value = false
            _isPlaying.value = false
            _isError.value = true
            notifyState()
        }
    }

    fun pause() {
        exoPlayer?.pause()
        _isPlaying.value = false
        notifyState()
    }

    fun resume() {
        exoPlayer?.play()
        _isPlaying.value = true
        notifyState()
    }

    fun stop() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _currentUrl.value = null
        _isPlaying.value = false
        _isLoading.value = false
        _isError.value = false
        activeCallback = null
    }

    fun release() {
        stop()
        exoPlayer?.release()
        exoPlayer = null
    }
}
