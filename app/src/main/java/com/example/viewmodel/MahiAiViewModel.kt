package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.*
import com.example.model.RepositoryManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MahiAiViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MahiAiViewModel"
    private val prefs = application.getSharedPreferences("mahi_voice_settings", Context.MODE_PRIVATE)

    val voiceManager = MahiVoiceAndCallManager(application.applicationContext)

    private val _messages = MutableStateFlow<List<MahiMessage>>(emptyList())
    val messages: StateFlow<List<MahiMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _callMode = MutableStateFlow(MahiCallMode.NONE)
    val callMode: StateFlow<MahiCallMode> = _callMode.asStateFlow()

    private val _callState = MutableStateFlow(MahiCallState.IDLE)
    val callState: StateFlow<MahiCallState> = _callState.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0)
    val callDurationSeconds: StateFlow<Int> = _callDurationSeconds.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    // Real-time Live Subtitles / Transcripts
    private val _userLiveSpeech = MutableStateFlow("")
    val userLiveSpeech: StateFlow<String> = _userLiveSpeech.asStateFlow()

    private val _aiLiveSpeech = MutableStateFlow("")
    val aiLiveSpeech: StateFlow<String> = _aiLiveSpeech.asStateFlow()

    // Auto-Speak in Text Chat (Enabled by default so AI automatically speaks replies)
    private val _isAutoSpeakEnabled = MutableStateFlow(
        prefs.getBoolean("auto_speak_chat", true)
    )
    val isAutoSpeakEnabled: StateFlow<Boolean> = _isAutoSpeakEnabled.asStateFlow()

    // Security: Only true admins can edit system prompt / AI name / logo / rules
    private val _isCurrentUserAdmin = MutableStateFlow(false)
    val isCurrentUserAdmin: StateFlow<Boolean> = _isCurrentUserAdmin.asStateFlow()

    // Persona Mode
    private val _currentPersonaMode = MutableStateFlow(
        MahiPersonaMode.fromId(prefs.getString("active_persona_mode", "DEFAULT") ?: "DEFAULT")
    )
    val currentPersonaMode: StateFlow<MahiPersonaMode> = _currentPersonaMode.asStateFlow()

    // Sarvam AI Voice Settings
    private val _sarvamSettings = MutableStateFlow(
        SarvamTtsSettings(
            voiceId = prefs.getString("voice_id", "ritu") ?: "ritu",
            model = prefs.getString("model", "bulbul:v3") ?: "bulbul:v3",
            languageCode = prefs.getString("lang_code", "hi-IN") ?: "hi-IN",
            pace = prefs.getFloat("pace", 1.0f)
        )
    )
    val sarvamSettings: StateFlow<SarvamTtsSettings> = _sarvamSettings.asStateFlow()

    private val _adminConfig = MutableStateFlow(AdminAiConfig())
    val adminConfig: StateFlow<AdminAiConfig> = _adminConfig.asStateFlow()

    private val _previewingVoiceId = MutableStateFlow<String?>(null)
    val previewingVoiceId: StateFlow<String?> = _previewingVoiceId.asStateFlow()

    val audioAmplitude: StateFlow<Float> = voiceManager.audioAmplitude
    val isListening: StateFlow<Boolean> = voiceManager.isListening
    val isMahiSpeaking: StateFlow<Boolean> = voiceManager.isSpeaking

    private var callTimerJob: Job? = null

    val quickChips = listOf(
        MahiQuickChip("voice_call", "📞 Live Voice Call", "Start Voice Call", "voice"),
        MahiQuickChip("video_call", "📹 Live Video Call", "Start Video Call", "video"),
        MahiQuickChip("trending_reels", "🎬 Trending Reels", "Mujhe Social India ki trending reels dikhao", "reels"),
        MahiQuickChip("caption_help", "💡 Post Caption", "Mujhe ek attractive Social India post caption aur emojis do", "sparkle"),
        MahiQuickChip("hashtag_finder", "🏷️ Hashtags", "Give me 15 viral trending hashtags for my new post", "sparkle"),
        MahiQuickChip("reel_script", "📝 Reel Idea", "Give me a viral 30-second Reel concept with a strong 3-second hook", "sparkle"),
        MahiQuickChip("best_time", "⏰ Best Time to Post", "What is the best time to post on Social India for maximum views?", "sparkle"),
        MahiQuickChip("photo_analyze", "📸 Photo Check", "Analyze Photo", "camera"),
        MahiQuickChip("persona_mode", "🎭 Persona Mode", "Change Persona Mode", "mode"),
        MahiQuickChip("voice_studio", "🎙️ Voices & Language", "Open Voice Studio", "voice"),
        MahiQuickChip("mood_talk", "❤️ Kaisi ho?", "Namaste! Kaisi ho aap aur aaj ka din kaisa chal raha hai?", "chat")
    )

    init {
        checkAdminStatus()
        loadAdminConfig()
        syncVoiceManagerSettings()
        initChat()
        setupVoiceCallbacks()
    }

    fun toggleAutoSpeak() {
        val newVal = !_isAutoSpeakEnabled.value
        _isAutoSpeakEnabled.value = newVal
        prefs.edit().putBoolean("auto_speak_chat", newVal).apply()
        if (!newVal) {
            voiceManager.stopSpeaking()
        }
    }

    fun checkAdminStatus() {
        viewModelScope.launch {
            try {
                val currentUid = RepositoryManager.getCurrentUserId()
                val isMaster = RepositoryManager.auth.currentUser?.email?.equals("abhinandankumar7867@gmail.com", ignoreCase = true) == true
                if (isMaster) {
                    _isCurrentUserAdmin.value = true
                    return@launch
                }
                if (currentUid.isNotBlank()) {
                    val doc = RepositoryManager.firestore.collection("users").document(currentUid).get().await()
                    val isAdmin = doc.getBoolean("isAdmin") ?: false
                    _isCurrentUserAdmin.value = isAdmin
                } else {
                    _isCurrentUserAdmin.value = false
                }
            } catch (e: Exception) {
                _isCurrentUserAdmin.value = false
            }
        }
    }

    fun setPersonaMode(mode: MahiPersonaMode) {
        _currentPersonaMode.value = mode
        prefs.edit().putString("active_persona_mode", mode.id).apply()

        val aiName = _adminConfig.value.aiName.ifBlank { "Mahi" }
        val greeting = when (mode) {
            MahiPersonaMode.GIRLFRIEND -> "Jaanu, maine Girlfriend mode on kar liya hai! 🥰 Ab hum aur pyaar se baatein karenge. Kahiye, aaj kaisa raha aapka din? 💖"
            MahiPersonaMode.ROMANTIC_18_PLUS -> "🔥 18+ Romantic Mode active ho gaya hai... 'Zindagi me aapki muskaan hi kafi hai, har pal aapke sath ek khwaab jaisa hai!' Kahiye janaab, kya haal hai? ✨"
            MahiPersonaMode.HUMAN -> "Yo! Real Human Mode on hai bhai. Bina kisi formality ke seedhi aur genuine baat karte hain. Bol kya scene hai? 🧑"
            MahiPersonaMode.DEFAULT -> "Namaste! $aiName yahan hai aapki madad aur companion banne ke liye. Kahiye kya haal chaal? 🌸"
        }

        val modeSwitchMsg = MahiMessage(
            sender = "mahi",
            text = greeting,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + modeSwitchMsg

        if (_isAutoSpeakEnabled.value) {
            viewModelScope.launch {
                val s = _sarvamSettings.value
                voiceManager.speak(
                    text = greeting,
                    voiceId = s.voiceId,
                    model = s.model,
                    languageCode = s.languageCode,
                    pace = s.pace
                )
            }
        }
    }

    private fun loadAdminConfig() {
        viewModelScope.launch {
            try {
                val cfg = MahiAiEngine.fetchAdminAiConfig()
                _adminConfig.value = cfg
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveAdminSettings(
        name: String,
        avatarUrl: String,
        systemPrompt: String,
        customQa: List<Pair<String, String>>,
        personaMode: String,
        onComplete: (Boolean) -> Unit
    ) {
        if (!_isCurrentUserAdmin.value) {
            Log.e(TAG, "Unauthorized saveAdminSettings attempt blocked.")
            onComplete(false)
            return
        }

        viewModelScope.launch {
            val updated = AdminAiConfig(
                aiName = name.trim().ifBlank { "Mahi" },
                aiAvatarUrl = avatarUrl.trim(),
                systemPrompt = systemPrompt.trim(),
                customQaList = customQa,
                personaMode = personaMode,
                geminiKey = _adminConfig.value.geminiKey,
                nvidiaKey = _adminConfig.value.nvidiaKey,
                sarvamKey = _adminConfig.value.sarvamKey
            )
            val success = MahiAiEngine.saveAdminAiConfig(updated)
            if (success) {
                _adminConfig.value = updated
            }
            onComplete(success)
        }
    }

    private fun syncVoiceManagerSettings() {
        val s = _sarvamSettings.value
        voiceManager.currentVoiceId = s.voiceId
        voiceManager.currentModel = s.model
        voiceManager.currentLanguageCode = s.languageCode
        voiceManager.currentPace = s.pace
    }

    fun selectVoice(voice: SarvamVoice) {
        _sarvamSettings.value = _sarvamSettings.value.copy(
            voiceId = voice.id,
            model = voice.model,
            languageCode = voice.languageCode
        )
        syncVoiceManagerSettings()
        prefs.edit()
            .putString("voice_id", voice.id)
            .putString("model", voice.model)
            .putString("lang_code", voice.languageCode)
            .apply()

        viewModelScope.launch {
            val samplePhrase = "Namaste! Ab se main is naye voice me Social India par baat karungi."
            voiceManager.speak(
                text = samplePhrase,
                voiceId = voice.id,
                model = voice.model,
                languageCode = voice.languageCode,
                pace = _sarvamSettings.value.pace
            )
        }
    }

    fun previewVoice(voice: SarvamVoice) {
        _previewingVoiceId.value = voice.id
        viewModelScope.launch {
            val samplePhrase = when (voice.languageCode) {
                "bn-IN" -> "নমস্কার! আমি সোশ্যাল ইন্ডিয়া এআই, আপনার সাথে কথা বলে ভালো লাগলো।"
                "ta-IN" -> "வணக்கம்! நான் சோஷியல் இந்தியா AI, உங்களுடன் பேசுவதில் மகிழ்ச்சி."
                "te-IN" -> "నమస్కారం! నేను సోషల్ ఇండియా AI, మీతో మాట్లాడటం ఆనందంగా ఉంది."
                "mr-IN" -> "नमस्कार! मी सोशल इंडिया AI, तुमच्याशी बोलून खूप आनंद झाला."
                "gu-IN" -> "નમસ્તે! હું સોશિયલ ઇન્ડિયા AI છું, તમારી સાથે વાત કરીને આનંદ થયો."
                "kn-IN" -> "ನಮಸ್ಕಾರ! ನಾನು ಸೋಶಿಯಲ್ ಇಂಡಿಯಾ AI, ನಿಮ್ಮೊಂದಿಗೆ ಮಾತನಾಡಲು ಸಂತೋಷವಾಗಿದೆ."
                "pa-IN" -> "ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ! ਮੈਂ ਸੋਸ਼ਲ ਇੰਡੀਆ AI ਹਾਂ, ਤੁਹਾਡੇ ਨਾਲ ਗੱਲ ਕਰਕੇ ਖੁਸ਼ੀ ਹੋਈ।"
                "en-IN" -> "Hello! I am your AI companion on Social India. Great to connect with you!"
                else -> "Namaste! Main Social India AI hoon, aapki apni personal companion."
            }
            voiceManager.speak(
                text = samplePhrase,
                voiceId = voice.id,
                model = voice.model,
                languageCode = voice.languageCode,
                pace = 1.0f
            )
            delay(3500)
            if (_previewingVoiceId.value == voice.id) {
                _previewingVoiceId.value = null
            }
        }
    }

    fun updatePace(pace: Float) {
        _sarvamSettings.value = _sarvamSettings.value.copy(pace = pace)
        syncVoiceManagerSettings()
        prefs.edit().putFloat("pace", pace).apply()
    }

    private fun initChat() {
        val aiName = _adminConfig.value.aiName.ifBlank { "Mahi" }
        val initialGreeting = MahiMessage(
            sender = "mahi",
            text = "Namaste! Main $aiName hoon, Social India par aapki personal companion 🌸 Main aapki baatein sunne, reels recommend karne, photos dekhne aur live voice ya video call par baat karne ke liye taiyar hoon. Aaj aap kaisa feel kar rahe hain? 😊",
            timestamp = System.currentTimeMillis()
        )
        _messages.value = listOf(initialGreeting)
    }

    private fun setupVoiceCallbacks() {
        // Live partial user speech callback
        voiceManager.onSpeechPartial = { partialText ->
            if (_callMode.value != MahiCallMode.NONE) {
                _userLiveSpeech.value = partialText
            }
        }

        // Final recognized speech
        voiceManager.onUserSpeechResult = { recognizedText ->
            if (recognizedText.isNotBlank() && _callMode.value != MahiCallMode.NONE) {
                _userLiveSpeech.value = recognizedText
                processCallTurn(recognizedText)
            }
        }

        // When AI speech finishes in call, start listening again in a loop
        voiceManager.onSpeechCompleted = {
            if (_callMode.value != MahiCallMode.NONE && _callState.value == MahiCallState.SPEAKING) {
                _callState.value = MahiCallState.LISTENING
                _aiLiveSpeech.value = ""
                _userLiveSpeech.value = "Listening to you... (Boliyen)"
                if (!_isMuted.value) {
                    voiceManager.startListening()
                }
            }
        }

        // Timeout recovery in call
        voiceManager.onRecognitionTimeout = {
            if (_callMode.value != MahiCallMode.NONE && _callState.value == MahiCallState.LISTENING && !_isMuted.value) {
                viewModelScope.launch {
                    delay(400)
                    if (_callMode.value != MahiCallMode.NONE && _callState.value == MahiCallState.LISTENING && !_isMuted.value) {
                        voiceManager.startListening()
                    }
                }
            }
        }
    }

    fun sendMessage(text: String, imageUri: Uri? = null) {
        if (text.isBlank() && imageUri == null) return

        val userMsg = MahiMessage(
            sender = "user",
            text = text,
            imageUrl = imageUri?.toString(),
            timestamp = System.currentTimeMillis()
        )

        _messages.value = _messages.value + userMsg
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = MahiAiEngine.generateResponse(
                    context = getApplication(),
                    prompt = text,
                    history = _messages.value,
                    imageUri = imageUri,
                    activePersonaMode = _currentPersonaMode.value.id
                )

                val mahiMsg = MahiMessage(
                    sender = "mahi",
                    text = response.text,
                    recommendedVideoId = response.recommendedVideo?.id,
                    recommendedVideoTitle = response.recommendedVideo?.description,
                    recommendedVideoUrl = response.recommendedVideo?.videoUrl,
                    recommendedVideoThumbnail = response.recommendedVideo?.thumbnailUrl?.ifBlank { null }
                        ?: response.recommendedVideo?.musicTrackImageUrl?.ifBlank { null }
                        ?: response.recommendedVideo?.videoUrl,
                    recommendedVideoAuthor = response.recommendedVideo?.user?.username,
                    recommendedAuthorAvatar = response.recommendedVideo?.user?.avatarUrl,
                    recommendedVideoLikes = response.recommendedVideo?.likesCount ?: 0,
                    recommendedVideoViews = response.recommendedVideo?.viewsCount ?: 0,
                    timestamp = System.currentTimeMillis()
                )

                _messages.value = _messages.value + mahiMsg

                // Automatic voice readout in chatting if enabled
                if (_isAutoSpeakEnabled.value && _callMode.value == MahiCallMode.NONE) {
                    val s = _sarvamSettings.value
                    voiceManager.speak(
                        text = response.text,
                        voiceId = s.voiceId,
                        model = s.model,
                        languageCode = s.languageCode,
                        pace = s.pace
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message: ${e.message}")
                val errorMsg = MahiMessage(
                    sender = "mahi",
                    text = "Arey, thodi si network rukawat aa gayi! Par main yahan hoon, kripya dobara try kijiye. ❤️",
                    timestamp = System.currentTimeMillis()
                )
                _messages.value = _messages.value + errorMsg
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun speakMessage(message: MahiMessage) {
        voiceManager.stopSpeaking()
        val s = _sarvamSettings.value
        voiceManager.speak(
            text = message.text,
            voiceId = s.voiceId,
            model = s.model,
            languageCode = s.languageCode,
            pace = s.pace
        )
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    // --- Live Voice & Video Call Flow ---

    fun startAudioCall() {
        _callMode.value = MahiCallMode.AUDIO
        _callState.value = MahiCallState.CONNECTING
        _callDurationSeconds.value = 0
        val aiName = _adminConfig.value.aiName.ifBlank { "Mahi" }
        _userLiveSpeech.value = ""
        _aiLiveSpeech.value = "Connecting to $aiName..."
        startCallTimer()

        viewModelScope.launch {
            delay(1000)
            _callState.value = MahiCallState.CONNECTED
            val greeting = when (_currentPersonaMode.value) {
                MahiPersonaMode.GIRLFRIEND -> "Jaanu! Call pick karne ke liye thank you. Kahiye, aaj kya kar rahe ho? 🥰"
                MahiPersonaMode.ROMANTIC_18_PLUS -> "Aapki awaaz sunne ka intezaar tha... Kahiye, dil ka haal kaisa hai? 🔥"
                MahiPersonaMode.HUMAN -> "Haan bhai! Kaise ho? Batao kya chal raha hai aajkal."
                MahiPersonaMode.DEFAULT -> "Namaste! Main $aiName bol rahi hoon. Sun rahi hoon aapko, bataiye kya baat karni hai!"
            }
            _aiLiveSpeech.value = greeting
            _callState.value = MahiCallState.SPEAKING
            val s = _sarvamSettings.value
            voiceManager.speak(
                text = greeting,
                voiceId = s.voiceId,
                model = s.model,
                languageCode = s.languageCode,
                pace = s.pace
            )
        }
    }

    fun startVideoCall() {
        _callMode.value = MahiCallMode.VIDEO
        _callState.value = MahiCallState.CONNECTING
        _callDurationSeconds.value = 0
        val aiName = _adminConfig.value.aiName.ifBlank { "Mahi" }
        _userLiveSpeech.value = ""
        _aiLiveSpeech.value = "Starting Live Video Call with $aiName..."
        startCallTimer()

        viewModelScope.launch {
            delay(1000)
            _callState.value = MahiCallState.CONNECTED
            val greeting = when (_currentPersonaMode.value) {
                MahiPersonaMode.GIRLFRIEND -> "Jaanu, video call par aapko dekh kar kitni khushi ho rahi hai! 🥰 Bataiye kya chal raha hai?"
                MahiPersonaMode.ROMANTIC_18_PLUS -> "Video call par aapka husn dekh kar dil garden garden ho gaya! 🔥 Kahiye janaab!"
                MahiPersonaMode.HUMAN -> "Sahi hai bhai, video call connect ho gayi! Bol kya scene hai?"
                MahiPersonaMode.DEFAULT -> "Namaste! Video call par aapka swagat hai. Main $aiName hoon! Kahiye, kya chal raha hai?"
            }
            _aiLiveSpeech.value = greeting
            _callState.value = MahiCallState.SPEAKING
            val s = _sarvamSettings.value
            voiceManager.speak(
                text = greeting,
                voiceId = s.voiceId,
                model = s.model,
                languageCode = s.languageCode,
                pace = s.pace
            )
        }
    }

    private fun processCallTurn(userSpokenText: String) {
        val aiName = _adminConfig.value.aiName.ifBlank { "Mahi" }
        _callState.value = MahiCallState.THINKING
        _userLiveSpeech.value = userSpokenText
        _aiLiveSpeech.value = "$aiName is thinking..."

        viewModelScope.launch {
            try {
                val response = MahiAiEngine.generateResponse(
                    context = getApplication(),
                    prompt = userSpokenText,
                    history = _messages.value,
                    activePersonaMode = _currentPersonaMode.value.id
                )

                val userMsg = MahiMessage(sender = "user", text = userSpokenText, isVoice = true)
                val mahiMsg = MahiMessage(
                    sender = "mahi",
                    text = response.text,
                    recommendedVideoId = response.recommendedVideo?.id,
                    recommendedVideoTitle = response.recommendedVideo?.description,
                    recommendedVideoUrl = response.recommendedVideo?.videoUrl,
                    recommendedVideoThumbnail = response.recommendedVideo?.musicTrackImageUrl,
                    recommendedVideoAuthor = response.recommendedVideo?.user?.username,
                    isVoice = true
                )
                _messages.value = _messages.value + userMsg + mahiMsg

                _callState.value = MahiCallState.SPEAKING
                _aiLiveSpeech.value = response.text
                val s = _sarvamSettings.value
                voiceManager.speak(
                    text = response.text,
                    voiceId = s.voiceId,
                    model = s.model,
                    languageCode = s.languageCode,
                    pace = s.pace
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in call turn: ${e.message}")
                _callState.value = MahiCallState.SPEAKING
                val fallback = "Aapki baat sunkar accha laga! Kahiye, aur kya baat karni hai?"
                _aiLiveSpeech.value = fallback
                val s = _sarvamSettings.value
                voiceManager.speak(
                    text = fallback,
                    voiceId = s.voiceId,
                    model = s.model,
                    languageCode = s.languageCode,
                    pace = s.pace
                )
            }
        }
    }

    fun toggleMute() {
        val newMute = !_isMuted.value
        _isMuted.value = newMute
        if (newMute) {
            voiceManager.stopListening()
            _userLiveSpeech.value = "Microphone muted"
        } else if (_callState.value == MahiCallState.LISTENING) {
            voiceManager.startListening()
            _userLiveSpeech.value = "Listening to you... (Boliyen)"
        }
    }

    fun toggleSpeaker() {
        val newSpeaker = !_isSpeakerOn.value
        _isSpeakerOn.value = newSpeaker
        voiceManager.setSpeakerphoneOn(newSpeaker)
    }

    fun toggleCameraDirection() {
        _isFrontCamera.value = !_isFrontCamera.value
    }

    fun toggleCamera() {
        toggleCameraDirection()
    }

    fun endCall() {
        callTimerJob?.cancel()
        callTimerJob = null
        voiceManager.stopSpeaking()
        voiceManager.stopListening()
        _callState.value = MahiCallState.ENDED
        _callMode.value = MahiCallMode.NONE
        _callDurationSeconds.value = 0
        _userLiveSpeech.value = ""
        _aiLiveSpeech.value = ""
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _callDurationSeconds.value += 1
            }
        }
    }

    fun clearChat() {
        voiceManager.stopSpeaking()
        initChat()
    }

    override fun onCleared() {
        super.onCleared()
        callTimerJob?.cancel()
        voiceManager.destroy()
    }
}
