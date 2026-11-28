package com.example.model

data class AdminDriveAccount(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val refreshToken: String = "", // OAuth refresh token or service key/bearer
    val accessToken: String = "",
    val tokenExpiry: Long = 0L,
    val folderId: String = "",
    val totalStorageBytes: Long = 15L * 1024 * 1024 * 1024, // 15 GB default Google Drive free quota
    val usedStorageBytes: Long = 0L,
    val isFull: Boolean = false,
    val isActive: Boolean = true,
    val priorityOrder: Int = 0,
    val uploadedFilesCount: Int = 0,
    val lastUsedTimestamp: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
