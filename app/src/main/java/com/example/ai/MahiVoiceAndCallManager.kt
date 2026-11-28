package com.example.ai

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class MahiVoiceAndCallManager(private val context: Context) {
    private val TAG = "MahiVoiceManager"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Sarvam AI TTS Engine (Ultra-realistic human voice)
    val sarvamTts = SarvamTtsEngine(context)

    // Android Native TTS Fallback
    private var nativeTts: TextToSpeech? = null
    private var isNativeTtsInitialized = false
    private var speechRecognizer: SpeechRecognizer? = null

    // Voice Settings
    var currentVoiceId = "ritu"
    var currentModel = "bulbul:v3"
    var currentLanguageCode = "hi-IN"
    var currentPace = 1.0f
    var currentPitch = 0.0f

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _livePartialSpeech = MutableStateFlow("")
    val livePartialSpeech: StateFlow<String> = _livePartialSpeech.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    var onSpeechRecognized: ((String) -> Unit)? = null
    var onSpeechPartial: ((String) -> Unit)? = null
    var onSpeechFinished: (() -> Unit)? = null
    var onTtsFinished: (() -> Unit)? = null

    var onUserSpeechResult: ((String) -> Unit)? = null
    var onSpeechCompleted: (() -> Unit)? = null
    var onRecognitionTimeout: (() -> Unit)? = null

    private var retryJob: Job? = null

    init {
        initNativeTts()
        setupSarvamCallbacks()
    }

    private fun setupSarvamCallbacks() {
        sarvamTts.onSpeechStarted = {
            _isSpeaking.value = true
            _audioAmplitude.value = 0.85f
        }

        sarvamTts.onSpeechCompleted = {
            _isSpeaking.value = false
            _audioAmplitude.value = 0f
            onTtsFinished?.invoke()
            onSpeechCompleted?.invoke()
        }

        sarvamTts.onSpeechError = { errorMsg ->
            Log.w(TAG, "Sarvam TTS error: $errorMsg. Falling back to native TTS.")
            _isSpeaking.value = false
            _audioAmplitude.value = 0f
            onSpeechCompleted?.invoke()
        }
    }

    private fun initNativeTts() {
        nativeTts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val hindiLocale = Locale("hi", "IN")
                val englishIndian = Locale("en", "IN")
                
                val result = nativeTts?.setLanguage(hindiLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val fallbackResult = nativeTts?.setLanguage(englishIndian)
                    if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        nativeTts?.setLanguage(Locale.getDefault())
                    }
                }
                
                nativeTts?.setPitch(1.05f)
                nativeTts?.setSpeechRate(0.98f)
                
                nativeTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        _audioAmplitude.value = 0f
                        onTtsFinished?.invoke()
                        onSpeechCompleted?.invoke()
                    }

                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        _audioAmplitude.value = 0f
                        onTtsFinished?.invoke()
                        onSpeechCompleted?.invoke()
                    }
                })
                isNativeTtsInitialized = true
            }
        }
    }

    fun speak(
        text: String,
        voiceId: String = currentVoiceId,
        model: String = currentModel,
        languageCode: String = currentLanguageCode,
        pace: Float = currentPace
    ) {
        if (text.isBlank()) {
            onSpeechCompleted?.invoke()
            return
        }
        stopSpeaking()
        stopListening()

        scope.launch {
            _isSpeaking.value = true
            try {
                sarvamTts.speak(
                    text = text,
                    voiceId = voiceId,
                    model = model,
                    languageCode = languageCode,
                    pace = pace
                )
            } catch (e: Exception) {
                // Fallback to Native Android TTS
                Log.d(TAG, "Falling back to Android Native TTS for: $text")
                nativeTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mahi_tts_${System.currentTimeMillis()}")
            }
        }
    }

    fun stopSpeaking() {
        sarvamTts.stop()
        try {
            nativeTts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping native TTS: ${e.message}")
        }
        _isSpeaking.value = false
        _audioAmplitude.value = 0f
    }

    fun startListening() {
        try {
            retryJob?.cancel()
            stopSpeaking()
            stopListening()

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _recognizedText.value = ""
                        _livePartialSpeech.value = ""
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val norm = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1.0f)
                        _audioAmplitude.value = norm
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _audioAmplitude.value = 0f
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _audioAmplitude.value = 0f
                        Log.d(TAG, "Speech recognition error code: $error")
                        onSpeechFinished?.invoke()

                        // If timed out with no speech, notify for loop continuation
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            onRecognitionTimeout?.invoke()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _audioAmplitude.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _recognizedText.value = text
                            _livePartialSpeech.value = text
                            onSpeechRecognized?.invoke(text)
                            onUserSpeechResult?.invoke(text)
                        } else {
                            onRecognitionTimeout?.invoke()
                        }
                        onSpeechFinished?.invoke()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _recognizedText.value = text
                            _livePartialSpeech.value = text
                            onSpeechPartial?.invoke(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer: ${e.message}")
            _isListening.value = false
        }
    }

    fun stopListening() {
        try {
            retryJob?.cancel()
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            _isListening.value = false
            _audioAmplitude.value = 0f
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognizer: ${e.message}")
        }
    }

    fun setSpeakerphoneOn(on: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.isSpeakerphoneOn = on
        } catch (e: Exception) {
            Log.e(TAG, "Error setting speakerphone: ${e.message}")
        }
    }

    fun destroy() {
        release()
    }

    fun release() {
        retryJob?.cancel()
        stopSpeaking()
        stopListening()
        sarvamTts.release()
        try {
            nativeTts?.shutdown()
            nativeTts = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing TTS: ${e.message}")
        }
    }
}
