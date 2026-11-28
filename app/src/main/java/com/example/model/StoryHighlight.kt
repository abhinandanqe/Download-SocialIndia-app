package com.example.model

data class StoryHighlight(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val coverUrl: String = "",
    val storyIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
