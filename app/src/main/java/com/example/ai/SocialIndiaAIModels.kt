package com.example.ai

import com.example.model.Video
import com.example.model.User
import com.example.model.AudioSound
import kotlinx.coroutines.flow.Flow

/**
 * Standard AI Provider Contract supporting pluggable backend models
 * (NVIDIA NIM, Gemini server-side, OpenAI-compatible, custom on-prem).
 */
interface AIProvider {
    val providerName: String
    val isAvailable: Boolean

    suspend fun chat(
        messages: List<Pair<String, String>>,
        modelId: String? = null,
        temperature: Double = 0.7,
        maxTokens: Int = 1024,
        imageUrl: String? = null
    ): String

    fun chatStream(
        messages: List<Pair<String, String>>,
        modelId: String? = null,
        temperature: Double = 0.7,
        maxTokens: Int = 1024
    ): Flow<String>

    suspend fun discoverModels(): List<AIModel>
}

/**
 * Universal Search Intent representation.
 */
data class AISearchIntent(
    val rawQuery: String,
    val intentType: SearchIntentType = SearchIntentType.GENERAL,
    val extractedKeywords: List<String> = emptyList(),
    val extractedHashtags: List<String> = emptyList(),
    val targetContentType: String? = null, // "video", "photo", "audio", "user", "all"
    val targetLanguage: String? = null,
    val isOnlyFollowedAccounts: Boolean = false,
    val topicConfidence: Float = 0.8f
)

enum class SearchIntentType {
    GENERAL,
    FIND_PEOPLE,
    FIND_REELS,
    FIND_MUSIC,
    TOPICAL_EXPLORE,
    HELP_OR_QUESTION
}

/**
 * Real search ranked results container.
 */
data class AISearchRankedResult(
    val query: String,
    val aiAnswer: String? = null,
    val users: List<User> = emptyList(),
    val videos: List<Video> = emptyList(),
    val sounds: List<AudioSound> = emptyList(),
    val hashtags: List<String> = emptyList(),
    val explanationReason: String? = null
)

/**
 * Autocomplete AI suggestion.
 */
data class AISearchSuggestion(
    val title: String,
    val subtitle: String,
    val type: SuggestionType,
    val itemId: String = ""
)

enum class SuggestionType {
    USER,
    HASHTAG,
    SOUND,
    TOPIC,
    RECENT
}

/**
 * Real Recommendation Reason for "Why am I seeing this?"
 */
data class WhyAmISeeingThisReason(
    val videoId: String,
    val primaryReasonHindi: String,
    val primaryReasonEnglish: String,
    val signalsDetected: List<String>,
    val creatorName: String = "",
    val soundTitle: String = ""
)

/**
 * Creator Assistant Ideas & Plan.
 */
data class AICreatorIdea(
    val conceptTitle: String,
    val hookHindi: String,
    val hookEnglish: String,
    val structureSteps: List<String>,
    val suggestedAudioVibe: String,
    val suggestedCaptions: List<String>,
    val suggestedHashtags: List<String>,
    val callToAction: String
)

/**
 * Video Editor AI Enhancement Suggestions.
 */
data class AIEditorSuggestions(
    val recommendedCutsMs: List<Long> = emptyList(),
    val detectedScenes: List<String> = emptyList(),
    val audioCleanupSuggested: Boolean = false,
    val hookTimestampMs: Long = 0L,
    val generatedSubtitles: List<Pair<Long, String>> = emptyList(),
    val suggestedCoverFrameMs: Long = 0L,
    val suggestedAspectCrop: String = "9:16"
)

/**
 * AI Safety Evaluation Response.
 */
data class AISafetyEvaluation(
    val isSafe: Boolean,
    val violationCategories: List<String> = emptyList(),
    val severityScore: Float = 0.0f,
    val recommendedAction: String = "ALLOW", // ALLOW, WARN, BLUR, BLOCK
    val userNotice: String = ""
)

/**
 * Central AI Architecture Interfaces.
 */
interface AiSearchService {
    suspend fun parseQueryIntent(query: String): AISearchIntent
    suspend fun searchAndRank(query: String, currentUserId: String): AISearchRankedResult
    suspend fun getAutocompleteSuggestions(partialQuery: String): List<AISearchSuggestion>
    suspend fun generateAnswerIfApplicable(query: String, verifiedItems: List<Video>): String?
}

interface AiRecommendationService {
    suspend fun getRankedReels(currentUserId: String, limit: Int = 20): List<Video>
    suspend fun getHomeFeed(currentUserId: String, limit: Int = 20): List<Video>
    suspend fun explainRecommendation(videoId: String, currentUserId: String): WhyAmISeeingThisReason
    suspend fun recordUserSignal(userId: String, videoId: String, signalType: String, durationWatchedMs: Long = 0)
}

interface AiCreatorService {
    suspend fun generateContentIdeas(topic: String, format: String = "Reel"): AICreatorIdea
    suspend fun suggestCaptions(context: String, tone: String, language: String): List<String>
    suspend fun suggestHashtags(topic: String, count: Int = 10): List<String>
    suspend fun analyzeCreatorAnalytics(userId: String): Map<String, String>
}

interface AiEditorService {
    suspend fun analyzeVideo(videoDurationMs: Long, audioEnergyLevels: List<Float>): AIEditorSuggestions
    suspend fun generateSubtitles(audioUrl: String, language: String): List<Pair<Long, String>>
}

interface AiSafetyService {
    suspend fun evaluateText(content: String): AISafetyEvaluation
    suspend fun evaluateComment(commentText: String): AISafetyEvaluation
    suspend fun checkContentPolicy(caption: String, tags: List<String>): AISafetyEvaluation
}

interface AiTranslationService {
    suspend fun translateText(text: String, targetLanguage: String): String
    suspend fun detectLanguage(text: String): String
}
