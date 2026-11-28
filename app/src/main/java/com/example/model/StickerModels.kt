package com.example.model

data class StickerPack(
    val packId: String = "",
    val name: String = "",
    val description: String = "",
    val iconDriveFileId: String = "",
    val iconUrl: String = "",
    val category: String = "Reactions",
    val language: String = "English",
    val status: String = "PUBLISHED", // "PUBLISHED", "DRAFT", "ARCHIVED"
    val sortOrder: Int = 0,
    val stickersCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class StickerItem(
    val stickerId: String = "",
    val packId: String = "",
    val name: String = "",
    val description: String = "",
    val type: String = "STATIC", // "STATIC", "ANIMATED"
    val driveFileId: String = "",
    val mediaUrl: String = "",
    val thumbnailDriveFileId: String = "",
    val thumbnailUrl: String = "",
    val mimeType: String = "image/webp",
    val width: Int = 512,
    val height: Int = 512,
    val fileSize: Long = 0L,
    val duration: Long = 0L,
    val frameCount: Int = 1,
    val tags: List<String> = emptyList(),
    val sortOrder: Int = 0,
    val status: String = "PUBLISHED", // "PUBLISHED", "DRAFT", "ARCHIVED"
    val usageCount: Long = 0L,
    val isAnimated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class StickerReport(
    val reportId: String = "",
    val stickerId: String = "",
    val reporterUid: String = "",
    val reason: String = "",
    val status: String = "PENDING", // "PENDING", "RESOLVED", "DISMISSED"
    val createdAt: Long = System.currentTimeMillis()
)

data class StickerAdminStats(
    val totalPacks: Int = 0,
    val publishedPacks: Int = 0,
    val draftPacks: Int = 0,
    val animatedStickers: Int = 0,
    val staticStickers: Int = 0,
    val totalUsageCount: Long = 0L
)
