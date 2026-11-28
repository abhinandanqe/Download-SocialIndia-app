package com.example.model

import java.io.Serializable

enum class SoundCategory {
    ORIGINAL_AUDIO,
    LICENSED_MUSIC,
    TRENDING_REMIX
}

data class AudioSound(
    val id: String = "",
    val title: String = "Original Audio",
    val artist: String = "Social India",
    val category: SoundCategory = SoundCategory.ORIGINAL_AUDIO,
    val creatorId: String = "",
    val creatorUsername: String = "",
    val creatorDisplayName: String = "",
    val creatorAvatarUrl: String = "",
    val creatorIsVerified: Boolean = false,
    val audioUrl: String = "",
    val coverImageUrl: String = "",
    val durationSeconds: Int = 30,
    val reelsCount: Int = 0,
    val totalViews: Long = 0L,
    val isTrending: Boolean = false,
    val trendingRank: Int? = null,
    val velocityScore: Double = 0.0,
    val isSaved: Boolean = false,
    val originalVideoId: String = "",
    val createdAt: Long = System.currentTimeMillis()
) : Serializable
