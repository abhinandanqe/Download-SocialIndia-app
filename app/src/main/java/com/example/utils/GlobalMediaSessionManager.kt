package com.example.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GlobalMediaSessionManager
 * 
 * Enforces single primary media playback focus across the entire application.
 * Solves audio overlap, ghost playback, and background leaks between Home Feed,
 * Reels, Story Viewer, Live streams, and audio previews.
 *
 * Primary Media Context Priority:
 * 1. Story Viewer (highest exclusive priority)
 * 2. Reels
 * 3. Home Feed
 * 4. Other Preview Media
 */
enum class MediaSource {
    HOME_FEED,
    REELS,
    STORY,
    VIDEO_EDITOR,
    LIVE,
    CHAT_VOICE,
    OTHER
}

object GlobalMediaSessionManager {
    private const val TAG = "GlobalMediaSessionMgr"

    private val _activeMediaSource = MutableStateFlow<MediaSource?>(null)
    val activeMediaSource: StateFlow<MediaSource?> = _activeMediaSource.asStateFlow()

    // Gatekeeper flag for Home Feed videos & audio
    private val _isHomeFeedPlaybackAllowed = MutableStateFlow(true)
    val isHomeFeedPlaybackAllowed: StateFlow<Boolean> = _isHomeFeedPlaybackAllowed.asStateFlow()

    // Gatekeeper flag for Reels video & audio
    private val _isReelsPlaybackAllowed = MutableStateFlow(true)
    val isReelsPlaybackAllowed: StateFlow<Boolean> = _isReelsPlaybackAllowed.asStateFlow()

    // Reactive Story Viewer presence flag
    private val _isStoryActive = MutableStateFlow(false)
    val isStoryActive: StateFlow<Boolean> = _isStoryActive.asStateFlow()

    // Registered emergency pause callbacks
    private val pauseCallbacks = mutableSetOf<() -> Unit>()

    // Android AudioManager audio focus tracking
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isAudioFocusHeld = false

    fun registerPauseCallback(callback: () -> Unit) {
        synchronized(pauseCallbacks) {
            pauseCallbacks.add(callback)
        }
    }

    fun unregisterPauseCallback(callback: () -> Unit) {
        synchronized(pauseCallbacks) {
            pauseCallbacks.remove(callback)
        }
    }

    /**
     * Request exclusive media focus.
     * When Story requests focus, immediately mutes and pauses all active Home Feed/Reels players.
     */
    fun requestFocus(source: MediaSource, context: Context? = null): Boolean {
        Log.d(TAG, "requestFocus for source=$source (active=${_activeMediaSource.value}, storyActive=${_isStoryActive.value})")

        when (source) {
            MediaSource.STORY -> {
                // Immediate global media pause
                _isStoryActive.value = true
                _isHomeFeedPlaybackAllowed.value = false
                _isReelsPlaybackAllowed.value = false
                _activeMediaSource.value = MediaSource.STORY

                // Trigger all registered pause callbacks instantly
                synchronized(pauseCallbacks) {
                    val callbacksSnapshot = pauseCallbacks.toList()
                    for (cb in callbacksSnapshot) {
                        try {
                            cb.invoke()
                        } catch (e: Throwable) {
                            Log.e(TAG, "Error executing pause callback", e)
                        }
                    }
                }

                requestAndroidAudioFocus(context)
                return true
            }

            MediaSource.REELS -> {
                if (_isStoryActive.value) {
                    Log.d(TAG, "Reels denied focus because Story is active")
                    return false
                }
                _isHomeFeedPlaybackAllowed.value = false
                _isReelsPlaybackAllowed.value = true
                _activeMediaSource.value = MediaSource.REELS
                requestAndroidAudioFocus(context)
                return true
            }

            MediaSource.HOME_FEED -> {
                if (_isStoryActive.value) {
                    Log.d(TAG, "Home Feed denied focus because Story is active")
                    return false
                }
                _isHomeFeedPlaybackAllowed.value = true
                _isReelsPlaybackAllowed.value = false
                _activeMediaSource.value = MediaSource.HOME_FEED
                return true
            }

            else -> {
                if (_isStoryActive.value) return false
                _activeMediaSource.value = source
                return true
            }
        }
    }

    /**
     * Release media focus.
     * When Story or Reels releases focus, restores eligibility for Home Feed and Reels according to viewport.
     */
    fun releaseFocus(source: MediaSource, context: Context? = null) {
        Log.d(TAG, "releaseFocus for source=$source")
        if (source == MediaSource.STORY) {
            _isStoryActive.value = false
            if (_activeMediaSource.value == MediaSource.STORY) {
                _activeMediaSource.value = null
            }
            // Allow Home Feed and Reels to resume eligible media
            _isHomeFeedPlaybackAllowed.value = true
            _isReelsPlaybackAllowed.value = true
            abandonAndroidAudioFocus(context)
        } else if (source == MediaSource.REELS) {
            if (_activeMediaSource.value == MediaSource.REELS) {
                _activeMediaSource.value = null
            }
            _isHomeFeedPlaybackAllowed.value = !_isStoryActive.value
            _isReelsPlaybackAllowed.value = !_isStoryActive.value
            abandonAndroidAudioFocus(context)
        } else if (source == MediaSource.HOME_FEED) {
            if (_activeMediaSource.value == MediaSource.HOME_FEED) {
                _activeMediaSource.value = null
            }
            _isHomeFeedPlaybackAllowed.value = !_isStoryActive.value
            _isReelsPlaybackAllowed.value = !_isStoryActive.value
            abandonAndroidAudioFocus(context)
        } else if (_activeMediaSource.value == source) {
            _activeMediaSource.value = null
            abandonAndroidAudioFocus(context)
        }
    }

    private fun requestAndroidAudioFocus(context: Context?) {
        try {
            if (context == null) return
            if (audioManager == null) {
                audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            }
            val am = audioManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { /* Handle focus changes if needed */ }
                    .build()

                audioFocusRequest = focusRequest
                val res = am.requestAudioFocus(focusRequest)
                isAudioFocusHeld = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            } else {
                @Suppress("DEPRECATION")
                val res = am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                isAudioFocusHeld = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to request Android audio focus", e)
        }
    }

    private fun abandonAndroidAudioFocus(context: Context?) {
        try {
            val am = audioManager ?: context?.applicationContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null && isAudioFocusHeld) {
                val activeFocusReq = audioFocusRequest
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activeFocusReq != null) {
                    am.abandonAudioFocusRequest(activeFocusReq)
                } else {
                    @Suppress("DEPRECATION")
                    am.abandonAudioFocus(null)
                }
                isAudioFocusHeld = false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to abandon Android audio focus", e)
        }
    }
}
