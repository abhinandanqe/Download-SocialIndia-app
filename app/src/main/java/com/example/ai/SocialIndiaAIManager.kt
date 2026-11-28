package com.example.ai

import android.util.Log
import com.example.model.AiRequestHistory
import com.example.model.RepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Central AI Gateway ensuring safety, authentication, audit, and capability routing.
 */
object SocialIndiaAIGateway {
    private const val TAG = "SocialIndiaAIGateway"
    private val nvidiaProvider = NvidiaAIProvider()

    suspend fun execute(
        featureId: String,
        messages: List<Pair<String, String>>,
        modelId: String? = null,
        imageUrl: String? = null,
        temperature: Double = 0.7,
        maxTokens: Int = 1024
    ): String = withContext(Dispatchers.IO) {
        val uid = RepositoryManager.getCurrentUserId()
        if (uid.isEmpty()) {
            throw AIException(AIErrorCode.AI_AUTH_ERROR, "Please sign in to access Social India AI features.")
        }

        val feature = AIFeatureRegistry.getFeature(featureId)
            ?: throw AIException(AIErrorCode.AI_INVALID_INPUT, "Unknown AI feature: $featureId")

        // 1. Strict capability enforcement
        if (!AICapabilityRegistry.supportsAll(feature.requiredCapabilities, modelId)) {
            AIAuditLogger.logEvent("AI_CAPABILITY_UNAVAILABLE", featureId, mapOf("required" to feature.requiredCapabilities))
            throw AIException(
                AIErrorCode.AI_CAPABILITY_UNAVAILABLE,
                "The configured AI model does not support capabilities required for ${feature.name}."
            )
        }

        // 2. Usage & daily limit check
        val withinLimit = AIUsageManager.checkAndIncrementUsage(featureId)
        if (!withinLimit) {
            AIAuditLogger.logEvent("AI_LIMIT_REACHED", featureId)
            throw AIException(AIErrorCode.AI_QUOTA_EXCEEDED, "Daily AI limit reached. Please try again tomorrow.")
        }

        // 3. Safety scan on last user prompt
        val lastUserMessage = messages.lastOrNull { it.first == "user" }?.second ?: ""
        val safetyResult = AISafetyManager.evaluateTextSafety(lastUserMessage)
        if (safetyResult.actionTaken == "HIDE") {
            AISafetyManager.logSafetyRecord(safetyResult.copy(userId = uid))
            throw AIException(AIErrorCode.AI_CONTENT_REJECTED, "Prompt flagged for inappropriate content.")
        }

        AIAuditLogger.logEvent("AI_REQUEST_STARTED", featureId, mapOf("modelId" to modelId))

        try {
            val response = nvidiaProvider.chat(
                messages = messages,
                modelId = modelId,
                temperature = temperature,
                maxTokens = maxTokens,
                imageUrl = imageUrl
            )

            // Save history record
            val history = AiRequestHistory(
                aiId = featureId,
                aiName = feature.name,
                userId = uid,
                prompt = lastUserMessage,
                resultText = response,
                resultMediaUrl = imageUrl ?: "",
                timestamp = System.currentTimeMillis()
            )
            val docRef = RepositoryManager.firestore.collection("ai_history").document()
            history.id = docRef.id
            docRef.set(history)

            AIAuditLogger.logEvent("AI_REQUEST_COMPLETED", featureId)
            return@withContext response
        } catch (e: Exception) {
            AIAuditLogger.logEvent("AI_REQUEST_FAILED", featureId, mapOf("error" to (e.message ?: "Unknown")))
            throw e
        }
    }

    fun stream(
        featureId: String,
        messages: List<Pair<String, String>>,
        modelId: String? = null
    ): Flow<String> {
        val feature = AIFeatureRegistry.getFeature(featureId)
            ?: throw AIException(AIErrorCode.AI_INVALID_INPUT, "Unknown AI feature: $featureId")

        if (!AICapabilityRegistry.supports(AICapability.STREAMING, modelId)) {
            throw AIException(AIErrorCode.AI_CAPABILITY_UNAVAILABLE, "Streaming is not supported for this model.")
        }

        return nvidiaProvider.chatStream(messages, modelId)
    }

    suspend fun discoverAvailableModels(): List<AIModel> {
        return nvidiaProvider.discoverModels()
    }
}

/**
 * Top-level Orchestrator used by UI and ViewModels across Social India.
 */
object SocialIndiaAIManager {
    private const val SYSTEM_PROMPT = 
        "You are the Social India AI Assistant, a modern, highly helpful social media assistant powered by NVIDIA AI. " +
        "You assist creators and users with captions, reel ideas, story planning, translations, and audience insights. " +
        "You support Hindi, English, and Hinglish fluently. Be concise, engaging, and creative."

    suspend fun askAssistant(prompt: String, conversationHistory: List<Pair<String, String>> = emptyList()): String {
        val messages = mutableListOf<Pair<String, String>>()
        messages.add("system" to SYSTEM_PROMPT)
        messages.addAll(conversationHistory)
        messages.add("user" to prompt)
        return SocialIndiaAIGateway.execute("ai_assistant", messages)
    }

    fun askAssistantStream(prompt: String, conversationHistory: List<Pair<String, String>> = emptyList()): Flow<String> {
        val messages = mutableListOf<Pair<String, String>>()
        messages.add("system" to SYSTEM_PROMPT)
        messages.addAll(conversationHistory)
        messages.add("user" to prompt)
        return SocialIndiaAIGateway.stream("ai_assistant", messages)
    }

    suspend fun generateCaptions(
        context: String,
        tone: String = "Engaging",
        language: String = "Hinglish"
    ): String {
        val prompt = "Generate 3 creative, high-performing social media captions for this content: \"$context\". " +
                "Tone: $tone. Language: $language. Include catchy emojis and 1 call-to-action (CTA)."
        val messages = listOf(
            "system" to SYSTEM_PROMPT,
            "user" to prompt
        )
        return SocialIndiaAIGateway.execute("ai_caption", messages)
    }

    suspend fun generateHashtags(
        topic: String,
        count: Int = 15
    ): String {
        val prompt = "Provide $count trending and relevant hashtags for: \"$topic\". " +
                "Categorize them into: #Broad, #Niche, and #Community hashtags."
        val messages = listOf(
            "system" to SYSTEM_PROMPT,
            "user" to prompt
        )
        return SocialIndiaAIGateway.execute("ai_hashtag", messages)
    }

    suspend fun generateReelConcept(
        topic: String,
        mood: String = "Energetic"
    ): String {
        val prompt = "Create a viral 30-second Reel concept on \"$topic\". " +
                "Mood: $mood. Include: 1) Strong 3-second Hook, 2) Visual Scene Plan, 3) Suggested S45 Audio vibe, 4) Call to Action."
        val messages = listOf(
            "system" to SYSTEM_PROMPT,
            "user" to prompt
        )
        return SocialIndiaAIGateway.execute("ai_reels_studio", messages)
    }

    suspend fun generateStorySuggestions(theme: String): String {
        val prompt = "Suggest 3 creative interactive Story ideas for \"$theme\". " +
                "Suggest interactive poll questions, quiz questions, and a background color aesthetic. " +
                "Remember: Stories must remain clean, so give pure textual ideas the creator can choose to add."
        val messages = listOf(
            "system" to SYSTEM_PROMPT,
            "user" to prompt
        )
        return SocialIndiaAIGateway.execute("ai_story_studio", messages)
    }

    suspend fun suggestCommentReplies(
        comment: String,
        tone: String = "Friendly"
    ): String {
        val prompt = "Suggest 3 quick, authentic replies to this user comment: \"$comment\". " +
                "Tone: $tone. Provide options in English and Hinglish."
        val messages = listOf(
            "system" to SYSTEM_PROMPT,
            "user" to prompt
        )
        return SocialIndiaAIGateway.execute("ai_comments", messages)
    }

    suspend fun translateText(
        text: String,
        targetLanguage: String = "Hindi"
    ): String {
        val prompt = "Translate the following social media text naturally to $targetLanguage, preserving tone and emojis: \"$text\""
        val messages = listOf(
            "system" to SYSTEM_PROMPT,
            "user" to prompt
        )
        return SocialIndiaAIGateway.execute("ai_translation", messages)
    }

    suspend fun analyzeCreatorPerformance(
        recentReelsCount: Int,
        totalLikes: Long,
        totalViews: Long,
        topQuestion: String
    ): String {
        val prompt = "Analyze authorized creator metrics: " +
                "Recent Reels: $recentReelsCount, Total Views: $totalViews, Total Likes: $totalLikes. " +
                "Creator asks: \"$topQuestion\". Provide actionable advice based strictly on these metrics."
        val messages = listOf(
            "system" to SYSTEM_PROMPT,
            "user" to prompt
        )
        return SocialIndiaAIGateway.execute("creator_assistant", messages)
    }

    // Modular Pluggable Services
    val searchService: AiSearchService = RealAiSearchService()
    val recommendationService: AiRecommendationService = RealAiRecommendationService()
    val creatorService: AiCreatorService = RealAiCreatorService()
    val editorService: AiEditorService = RealAiEditorService()
    val safetyService: AiSafetyService = RealAiSafetyService()
    val translationService: AiTranslationService = RealAiTranslationService()
}
