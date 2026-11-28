package com.example.ai

/**
 * Standard capabilities defined for NVIDIA AI / NIM and multimodal models.
 */
enum class AICapability {
    TEXT,
    VISION,
    IMAGE_INPUT,
    AUDIO_INPUT,
    VIDEO_INPUT,
    IMAGE_GENERATION,
    IMAGE_EDITING,
    VIDEO_GENERATION,
    VIDEO_EDITING,
    SPEECH,
    TRANSCRIPTION,
    TRANSLATION,
    EMBEDDINGS,
    RERANKING,
    TOOL_CALLING,
    STREAMING,
    STRUCTURED_OUTPUT
}

/**
 * Standardized AI Errors ensuring security and user-friendly messaging.
 */
enum class AIErrorCode {
    AI_PROVIDER_UNAVAILABLE,
    AI_MODEL_UNAVAILABLE,
    AI_CAPABILITY_UNAVAILABLE,
    AI_AUTH_ERROR,
    AI_RATE_LIMIT,
    AI_QUOTA_EXCEEDED,
    AI_TIMEOUT,
    AI_CONTENT_REJECTED,
    AI_INVALID_INPUT,
    AI_BACKEND_ERROR,
    AI_OFFLINE
}

class AIException(
    val code: AIErrorCode,
    override val message: String,
    val details: String? = null
) : Exception(message) {
    fun getUserMessage(isHindi: Boolean = false): String {
        return when (code) {
            AIErrorCode.AI_CAPABILITY_UNAVAILABLE -> 
                if (isHindi) "इस AI फीचर के लिए मॉडल कन्फ़िगर नहीं है।" 
                else "This AI capability is not supported by the configured model."
            AIErrorCode.AI_RATE_LIMIT, AIErrorCode.AI_QUOTA_EXCEEDED -> 
                if (isHindi) "दैनिक AI सीमा समाप्त हो गई है। कृपया बाद में प्रयास करें।" 
                else "Daily AI limit reached. Please try again later."
            AIErrorCode.AI_OFFLINE -> 
                if (isHindi) "इंटरनेट कनेक्शन उपलब्ध नहीं है।" 
                else "No internet connection. AI features require network access."
            AIErrorCode.AI_AUTH_ERROR -> 
                if (isHindi) "कृपया AI सुविधाओं का उपयोग करने के लिए लॉगिन करें।" 
                else "Authentication required to use AI features."
            else -> 
                if (isHindi) "AI सेवा अस्थायी रूप से अनुपलब्ध है। कृपया थोड़ी देर बाद प्रयास करें।" 
                else "AI service temporarily unavailable. Please try again later."
        }
    }
}

/**
 * AI Model Metadata representation.
 */
data class AIModel(
    val id: String,
    val name: String,
    val provider: String = "NVIDIA",
    val capabilities: Set<AICapability> = setOf(AICapability.TEXT, AICapability.STREAMING),
    val contextLength: Int = 8192,
    val isDefaultText: Boolean = false,
    val isDefaultVision: Boolean = false,
    val isDefaultAudio: Boolean = false,
    val isDefaultVideo: Boolean = false,
    val isAvailable: Boolean = true
)

/**
 * Dynamic registry of available models and provider discovery.
 */
object AIModelRegistry {
    private val models = mutableMapOf<String, AIModel>()

    init {
        // Register verified default NVIDIA NIM models
        register(
            AIModel(
                id = "meta/llama-3.2-11b-vision-instruct",
                name = "Llama 3.2 11B Vision (NVIDIA NIM)",
                provider = "NVIDIA",
                capabilities = setOf(
                    AICapability.TEXT,
                    AICapability.VISION,
                    AICapability.IMAGE_INPUT,
                    AICapability.STREAMING,
                    AICapability.STRUCTURED_OUTPUT,
                    AICapability.TRANSLATION
                ),
                contextLength = 128000,
                isDefaultText = true,
                isDefaultVision = true
            )
        )
        register(
            AIModel(
                id = "meta/llama-3.1-70b-instruct",
                name = "Llama 3.1 70B Instruct (NVIDIA NIM)",
                provider = "NVIDIA",
                capabilities = setOf(
                    AICapability.TEXT,
                    AICapability.STREAMING,
                    AICapability.TOOL_CALLING,
                    AICapability.STRUCTURED_OUTPUT,
                    AICapability.TRANSLATION
                ),
                contextLength = 128000
            )
        )
        register(
            AIModel(
                id = "nvidia/nemotron-4-340b-instruct",
                name = "Nemotron-4 340B (NVIDIA)",
                provider = "NVIDIA",
                capabilities = setOf(
                    AICapability.TEXT,
                    AICapability.STREAMING,
                    AICapability.STRUCTURED_OUTPUT
                ),
                contextLength = 4096
            )
        )
        register(
            AIModel(
                id = "nvidia/embed-qa-4",
                name = "NVIDIA Retrieval Embedding",
                provider = "NVIDIA",
                capabilities = setOf(AICapability.EMBEDDINGS),
                contextLength = 512
            )
        )
    }

    fun register(model: AIModel) {
        models[model.id] = model
    }

    fun getAllModels(): List<AIModel> = models.values.toList()

    fun getModel(id: String): AIModel? = models[id]

    fun getDefaultTextModel(): AIModel {
        return models.values.firstOrNull { it.isDefaultText && it.isAvailable }
            ?: models.values.firstOrNull { it.capabilities.contains(AICapability.TEXT) && it.isAvailable }
            ?: AIModel("meta/llama-3.2-11b-vision-instruct", "Llama 3.2 11B Vision", "NVIDIA")
    }

    fun getDefaultVisionModel(): AIModel? {
        return models.values.firstOrNull { it.isDefaultVision && it.isAvailable }
            ?: models.values.firstOrNull { it.capabilities.contains(AICapability.VISION) && it.isAvailable }
    }
}

/**
 * Central Capability Registry to strictly avoid faking unsupported features.
 */
object AICapabilityRegistry {
    fun supports(capability: AICapability, modelId: String? = null): Boolean {
        val model = if (modelId != null) {
            AIModelRegistry.getModel(modelId)
        } else {
            AIModelRegistry.getDefaultTextModel()
        }
        return model?.capabilities?.contains(capability) == true
    }

    fun supportsAll(capabilities: Collection<AICapability>, modelId: String? = null): Boolean {
        return capabilities.all { supports(it, modelId) }
    }
}

/**
 * Feature Definition in the Social India AI Super Hub.
 */
data class AIFeature(
    val featureId: String,
    val name: String,
    val description: String,
    val category: String, // Studio, Creator, Productivity, Safety, Utility
    val requiredCapabilities: Set<AICapability>,
    val enabled: Boolean = true,
    val requiresConfirmation: Boolean = false,
    val isCreatorOnly: Boolean = false,
    val isAdminOnly: Boolean = false
)

/**
 * Catalog of AI features across the application.
 */
object AIFeatureRegistry {
    private val features = mutableMapOf<String, AIFeature>()

    init {
        val allFeatures = listOf(
            AIFeature(
                featureId = "ai_assistant",
                name = "AI Universal Assistant",
                description = "Chat, ask questions, get help and ideas in Hindi, English or Hinglish.",
                category = "Productivity",
                requiredCapabilities = setOf(AICapability.TEXT, AICapability.STREAMING)
            ),
            AIFeature(
                featureId = "creator_assistant",
                name = "Creator Studio Assistant",
                description = "Real engagement insights, posting time suggestions, and hook ideas based on authorized analytics.",
                category = "Creator",
                requiredCapabilities = setOf(AICapability.TEXT),
                isCreatorOnly = true
            ),
            AIFeature(
                featureId = "ai_reels_studio",
                name = "AI Reels Studio",
                description = "Plan concepts, hooks, scripts, captions, and hashtag suggestions for Reels.",
                category = "Studio",
                requiredCapabilities = setOf(AICapability.TEXT)
            ),
            AIFeature(
                featureId = "ai_story_studio",
                name = "AI Story Studio",
                description = "Ideas and text suggestions for Stories without automatic intrusive stickers.",
                category = "Studio",
                requiredCapabilities = setOf(AICapability.TEXT),
                requiresConfirmation = true
            ),
            AIFeature(
                featureId = "ai_caption",
                name = "Smart Caption Generator",
                description = "Generate catchy, minimal, humorous, or professional captions with CTAs.",
                category = "Creator",
                requiredCapabilities = setOf(AICapability.TEXT)
            ),
            AIFeature(
                featureId = "ai_hashtag",
                name = "AI Hashtags Assistant",
                description = "Discover categorized broad, niche, community, and topic hashtags.",
                category = "Creator",
                requiredCapabilities = setOf(AICapability.TEXT)
            ),
            AIFeature(
                featureId = "ai_comments",
                name = "Smart Comment Replies",
                description = "Friendly, professional, and quick replies for your audience.",
                category = "Creator",
                requiredCapabilities = setOf(AICapability.TEXT),
                requiresConfirmation = true
            ),
            AIFeature(
                featureId = "ai_moderation",
                name = "Comment Safety & Moderation",
                description = "Filter spam, harassment, and scam links with multi-level confidence review.",
                category = "Safety",
                requiredCapabilities = setOf(AICapability.TEXT)
            ),
            AIFeature(
                featureId = "ai_search",
                name = "Savi AI Search",
                description = "Natural language semantic search across creators, posts, and sounds.",
                category = "Utility",
                requiredCapabilities = setOf(AICapability.TEXT)
            ),
            AIFeature(
                featureId = "ai_photo_studio",
                name = "AI Photo Studio",
                description = "Background removal, enhancement, and smart crop. (Requires Vision/Editing model)",
                category = "Studio",
                requiredCapabilities = setOf(AICapability.IMAGE_EDITING)
            ),
            AIFeature(
                featureId = "ai_video_studio",
                name = "AI Video Studio",
                description = "Scene detection, highlight analysis, and smart trimming. (Requires Video model)",
                category = "Studio",
                requiredCapabilities = setOf(AICapability.VIDEO_INPUT)
            ),
            AIFeature(
                featureId = "ai_translation",
                name = "AI Multi-language Translator",
                description = "Translate captions and posts across Hindi, English, and regional languages.",
                category = "Utility",
                requiredCapabilities = setOf(AICapability.TRANSLATION)
            ),
            AIFeature(
                featureId = "ai_messages",
                name = "AI Chat Assistant",
                description = "Draft messages, adjust tone, and summarize long conversations safely.",
                category = "Productivity",
                requiredCapabilities = setOf(AICapability.TEXT),
                requiresConfirmation = true
            ),
            AIFeature(
                featureId = "ai_smart_drafts",
                name = "AI Smart Drafts",
                description = "Save and resume unfinished scripts, captions, and content concepts.",
                category = "Productivity",
                requiredCapabilities = setOf(AICapability.TEXT)
            ),
            AIFeature(
                featureId = "ai_privacy",
                name = "AI Privacy Assistant",
                description = "Learn how your data is protected, manage retention, or clear AI history.",
                category = "Safety",
                requiredCapabilities = setOf(AICapability.TEXT)
            ),
            AIFeature(
                featureId = "ai_security",
                name = "AI Security Assistant",
                description = "Guidance on 2FA, login alerts, and account protection.",
                category = "Safety",
                requiredCapabilities = setOf(AICapability.TEXT)
            ),
            AIFeature(
                featureId = "ai_admin",
                name = "AI Admin Dashboard Assistant",
                description = "Analyze system latency, AI error rates, and usage statistics.",
                category = "Productivity",
                requiredCapabilities = setOf(AICapability.TEXT),
                isAdminOnly = true
            )
        )

        allFeatures.forEach { features[it.featureId] = it }
    }

    fun getAllFeatures(): List<AIFeature> = features.values.toList()

    fun getFeature(featureId: String): AIFeature? = features[featureId]

    /**
     * Checks if a feature can actually run on the currently configured AI models.
     */
    fun isFeatureAvailable(featureId: String): Boolean {
        val feat = features[featureId] ?: return false
        if (!feat.enabled) return false
        return AICapabilityRegistry.supportsAll(feat.requiredCapabilities)
    }
}
