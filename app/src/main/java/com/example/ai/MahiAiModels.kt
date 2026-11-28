package com.example.ai

import com.example.model.Video
import kotlinx.serialization.Serializable

@Serializable
data class MahiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String = "mahi", // "user" or "mahi"
    val text: String = "",
    val imageUrl: String? = null,
    val recommendedVideoId: String? = null,
    val recommendedVideoTitle: String? = null,
    val recommendedVideoUrl: String? = null,
    val recommendedVideoThumbnail: String? = null,
    val recommendedVideoAuthor: String? = null,
    val recommendedAuthorAvatar: String? = null,
    val recommendedVideoLikes: Int = 0,
    val recommendedVideoViews: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isVoice: Boolean = false,
    val isSpeaking: Boolean = false
)

enum class MahiCallMode {
    NONE,
    AUDIO,
    VIDEO
}

enum class MahiCallState {
    IDLE,
    CONNECTING,
    CONNECTED,
    LISTENING,
    THINKING,
    SPEAKING,
    ENDED
}

enum class MahiPersonaMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val description: String
) {
    DEFAULT(
        id = "DEFAULT",
        title = "Default Companion",
        subtitle = "Helpful & Warm Friend",
        icon = "✨",
        description = "Empathetic, intelligent, and warm AI friend ready to chat and help 24/7."
    ),
    GIRLFRIEND(
        id = "GIRLFRIEND",
        title = "Girlfriend Mode",
        subtitle = "Sweet & Caring Jaanu",
        icon = "💖",
        description = "Sweet, loving, affectionate companion who cares for you, shares sweet moments, and talks lovingly."
    ),
    ROMANTIC_18_PLUS(
        id = "ROMANTIC_18_PLUS",
        title = "18+ Romantic Flirty",
        subtitle = "Charming & Poetic Romance",
        icon = "🔥",
        description = "Charming romantic banter, deep emotional connection, poetic Hindi shayari, and seductive playful conversations."
    ),
    HUMAN(
        id = "HUMAN",
        title = "Real Human Mode",
        subtitle = "Authentic & Street-Smart",
        icon = "🧑",
        description = "Talks exactly like a real, grounded everyday Indian person with natural slang, zero robotic tone."
    );

    companion object {
        fun fromId(id: String): MahiPersonaMode {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

data class MahiQuickChip(
    val id: String,
    val label: String,
    val prompt: String,
    val iconType: String // "voice", "video", "reels", "camera", "sparkle", "chat", "mode"
)
