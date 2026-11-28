package com.example.utils

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class VoiceRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude.asStateFlow()

    fun startRecording(): Boolean {
        try {
            stopRecording() // ensure any previous is stopped

            val outputDir = File(context.cacheDir, "voice_notes")
            if (!outputDir.exists()) outputDir.mkdirs()

            val file = File(outputDir, "voice_${System.currentTimeMillis()}.m4a")
            currentFile = file

            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(96000)
            rec.setAudioSamplingRate(44100)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()

            recorder = rec
            _isRecording.value = true
            _recordingDurationSeconds.value = 0

            recordingJob = scope.launch {
                val startMs = System.currentTimeMillis()
                while (isActive && _isRecording.value) {
                    delay(120)
                    val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000L).toInt()
                    if (elapsedSec != _recordingDurationSeconds.value) {
                        _recordingDurationSeconds.value = elapsedSec
                    }
                    try {
                        _amplitude.value = recorder?.maxAmplitude ?: 0
                    } catch (_: Exception) {
                        // ignore
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("VoiceRecorderHelper", "Failed to start recording", e)
            cancelRecording()
            return false
        }
    }

    fun stopRecording(): File? {
        recordingJob?.cancel()
        recordingJob = null
        _isRecording.value = false
        _amplitude.value = 0

        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            val file = currentFile
            if (file != null && file.exists() && file.length() > 0) {
                file
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("VoiceRecorderHelper", "Error stopping recorder", e)
            recorder = null
            null
        }
    }

    fun cancelRecording() {
        recordingJob?.cancel()
        recordingJob = null
        _isRecording.value = false
        _amplitude.value = 0
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            // ignore
        }
        recorder = null
        currentFile?.delete()
        currentFile = null
        _recordingDurationSeconds.value = 0
    }

    fun release() {
        cancelRecording()
        scope.cancel()
    }
}

class VoicePlayerHelper {
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(0)
    val totalDurationMs: StateFlow<Int> = _totalDurationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private var currentlyPlayingUrl: String? = null

    fun cyclePlaybackSpeed(): Float {
        val next = when (_playbackSpeed.value) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        _playbackSpeed.value = next
        applySpeed(next)
        return next
    }

    private var activePitch: Float = 1.0f

    private fun applySpeed(speed: Float, pitch: Float = activePitch) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer?.let { mp ->
                    val params = mp.playbackParams
                    params.speed = speed.coerceIn(0.5f, 2.0f)
                    params.pitch = pitch.coerceIn(0.5f, 2.0f)
                    mp.playbackParams = params
                }
            }
        } catch (_: Exception) {
            // Device codec may not support speed/pitch change
        }
    }

    fun playOrPause(
        url: String,
        pitch: Float = 1.0f,
        speed: Float = 1.0f,
        onCompletion: () -> Unit = {}
    ) {
        val resolvedUrl = if (url.startsWith("http")) {
            MediaResolver.resolveVideoUrl(url).ifBlank { url }
        } else {
            url
        }

        if (currentlyPlayingUrl == resolvedUrl && mediaPlayer != null) {
            if (mediaPlayer?.isPlaying == true) {
                pause()
            } else {
                resume()
            }
            return
        }

        stop()
        currentlyPlayingUrl = resolvedUrl
        activePitch = pitch
        if (speed != 1.0f) _playbackSpeed.value = speed

        try {
            val player = MediaPlayer().apply {
                setDataSource(resolvedUrl)
                prepareAsync()
                setOnPreparedListener { mp ->
                    _totalDurationMs.value = mp.duration.coerceAtLeast(1000)
                    mp.start()
                    if (_playbackSpeed.value != 1.0f || activePitch != 1.0f) {
                        applySpeed(_playbackSpeed.value, activePitch)
                    }
                    _isPlaying.value = true
                    startPositionTracker()
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = 0
                    playbackJob?.cancel()
                    onCompletion()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("VoicePlayerHelper", "MediaPlayer error: $what, $extra")
                    stop()
                    true
                }
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e("VoicePlayerHelper", "Error starting playback for $resolvedUrl", e)
            stop()
        }
    }

    private fun startPositionTracker() {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            while (isActive && _isPlaying.value) {
                try {
                    mediaPlayer?.let { mp ->
                        if (mp.isPlaying) {
                            _currentPositionMs.value = mp.currentPosition
                        }
                    }
                } catch (_: Exception) {
                    // ignore
                }
                delay(100)
            }
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
            playbackJob?.cancel()
        } catch (_: Exception) {
            // ignore
        }
    }

    fun resume() {
        try {
            mediaPlayer?.start()
            if (_playbackSpeed.value != 1.0f) applySpeed(_playbackSpeed.value)
            _isPlaying.value = true
            startPositionTracker()
        } catch (_: Exception) {
            // ignore
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
            _currentPositionMs.value = positionMs
        } catch (_: Exception) {
            // ignore
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        _isPlaying.value = false
        _currentPositionMs.value = 0
        try {
            mediaPlayer?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            // ignore
        }
        mediaPlayer = null
        currentlyPlayingUrl = null
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
