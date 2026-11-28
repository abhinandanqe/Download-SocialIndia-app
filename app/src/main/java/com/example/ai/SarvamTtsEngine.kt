package com.example.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.model.RepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class SarvamTtsEngine(private val context: Context) {
    private val TAG = "SarvamTtsEngine"
    private val ENDPOINT = "https://api.sarvam.ai/text-to-speech"

    private var mediaPlayer: MediaPlayer? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isSynthesizing = MutableStateFlow(false)
    val isSynthesizing: StateFlow<Boolean> = _isSynthesizing.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    var onSpeechStarted: (() -> Unit)? = null
    var onSpeechCompleted: (() -> Unit)? = null
    var onSpeechError: ((String) -> Unit)? = null

    private val cacheDir = File(context.cacheDir, "sarvam_tts").apply {
        if (!exists()) mkdirs()
    }

    suspend fun getApiKey(): String = withContext(Dispatchers.IO) {
        var key = ""
        try {
            val doc = RepositoryManager.firestore.collection("admin_secrets")
                .document("ai_keys").get().await()
            if (doc.exists()) {
                val dbKey = doc.getString("sarvam_key") ?: doc.getString("sarvam_api_key")
                if (!dbKey.isNullOrBlank()) key = dbKey
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.d(TAG, "Admin secret key lookup skipped: ${e.message}")
        }

        if (key.isBlank()) {
            key = try { BuildConfig.SARVAM_API_KEY } catch (_: Exception) { "" }
        }
        key
    }

    /**
     * Synthesizes text into human speech using Sarvam AI Bulbul and plays it.
     */
    suspend fun speak(
        text: String,
        voiceId: String = "ritu",
        model: String = "bulbul:v3",
        languageCode: String = "hi-IN",
        pace: Float = 1.0f,
        pitch: Float = 0.0f
    ) = withContext(Dispatchers.IO) {
        val cleanText = text
            .replace(Regex("\\[.*?\\]"), "") // remove bracket tags
            .replace("*", "")
            .replace("#", "")
            .replace("🌸", "")
            .replace("🎬", "")
            .trim()

        if (cleanText.isBlank()) return@withContext

        stop()

        try {
            _isSynthesizing.value = true

            // Check cache first
            val cacheKey = hashString("$cleanText-$voiceId-$model-$languageCode-$pace-$pitch")
            val cachedAudioFile = File(cacheDir, "$cacheKey.wav")

            val audioFile: File = if (cachedAudioFile.exists() && cachedAudioFile.length() > 100) {
                cachedAudioFile
            } else {
                val apiKey = getApiKey()
                if (apiKey.isBlank()) {
                    throw Exception("Sarvam AI API key is missing.")
                }

                val audioBase64 = fetchTtsAudio(
                    apiKey = apiKey,
                    text = cleanText,
                    voiceId = voiceId,
                    model = model,
                    languageCode = languageCode,
                    pace = pace,
                    pitch = pitch
                )

                val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                FileOutputStream(cachedAudioFile).use { it.write(audioBytes) }
                cachedAudioFile
            }

            _isSynthesizing.value = false

            // Play on Main Thread
            withContext(Dispatchers.Main) {
                playAudioFile(audioFile)
            }

        } catch (e: Exception) {
            _isSynthesizing.value = false
            _isSpeaking.value = false
            Log.e(TAG, "Sarvam TTS failed: ${e.message}")
            withContext(Dispatchers.Main) {
                onSpeechError?.invoke(e.message ?: "Failed to generate speech")
            }
        }
    }

    private fun fetchTtsAudio(
        apiKey: String,
        text: String,
        voiceId: String,
        model: String,
        languageCode: String,
        pace: Float,
        pitch: Float
    ): String {
        val url = URL(ENDPOINT)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("api-subscription-key", apiKey)
            connectTimeout = 30000
            readTimeout = 30000
            doOutput = true
        }

        val requestJson = JSONObject().apply {
            val inputsArr = JSONArray()
            inputsArr.put(text)
            put("inputs", inputsArr)
            put("target_language_code", languageCode)
            put("speaker", voiceId)
            put("pitch", pitch)
            put("pace", pace)
            put("loudness", 1.5)
            put("speech_sample_rate", 22050)
            put("enable_preprocessing", true)
            put("model", model)
        }

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(requestJson.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val audios = json.optJSONArray("audios")
            if (audios != null && audios.length() > 0) {
                return audios.getString(0)
            } else {
                throw Exception("No audio returned in Sarvam AI response.")
            }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            Log.e(TAG, "Sarvam AI HTTP $responseCode error: $err")
            throw Exception("Sarvam API Error ($responseCode): $err")
        }
    }

    private fun playAudioFile(file: File) {
        try {
            stop()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                setOnPreparedListener {
                    _isSpeaking.value = true
                    _audioAmplitude.value = 0.85f
                    start()
                    onSpeechStarted?.invoke()
                }
                setOnCompletionListener {
                    _isSpeaking.value = false
                    _audioAmplitude.value = 0f
                    onSpeechCompleted?.invoke()
                    releaseMediaPlayer()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    _isSpeaking.value = false
                    _audioAmplitude.value = 0f
                    onSpeechCompleted?.invoke()
                    releaseMediaPlayer()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file: ${e.message}")
            _isSpeaking.value = false
            _audioAmplitude.value = 0f
            onSpeechCompleted?.invoke()
        }
    }

    fun stop() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            releaseMediaPlayer()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media player: ${e.message}")
        } finally {
            _isSpeaking.value = false
            _isSynthesizing.value = false
            _audioAmplitude.value = 0f
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing media player: ${e.message}")
        }
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun release() {
        stop()
    }
}
