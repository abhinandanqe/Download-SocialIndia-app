package com.example.model

/**
 * Global Firebase update configuration stored at `app_config/update_info`.
 */
data class UpdateInfo(
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val url: String = "",
    val notes: String = "",
    val updateMessage: String = "A new version of Social India is available with performance and feature upgrades.",
    val releaseDate: String = "",
    val mandatory: Boolean = false,
    val popupEnabled: Boolean = true,
    val driveFileId: String = "",
    val fileSizeMb: Double = 0.0,
    val releasedAtMillis: Long = 0L,
    val releasedByEmail: String = "",
    val githubRepo: String = "",
    val githubAutoSyncEnabled: Boolean = true,
    val source: String = "FIREBASE",
    val githubReleaseTag: String = "",
    val githubHtmlUrl: String = ""
)

/**
 * Per-authenticated-user update tracking record stored at `user_update_status/{uid}`
 * and mirrored onto `users/{uid}`.
 */
data class UserUpdateStatus(
    val uid: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val currentVersion: String = "1.0",
    val currentVersionCode: Int = 1,
    val latestVersion: String = "1.0",
    val latestVersionCode: Int = 1,
    val downloadStatus: String = "PENDING", // PENDING, DOWNLOADING, DOWNLOADED, INSTALLING, INSTALLED, FAILED
    val installedVersion: String = "1.0",
    val installedVersionCode: Int = 1,
    val updateCompletedDate: String = "",
    val updateCompletedTime: String = "",
    val lastUpdatedMillis: Long = 0L
)

/**
 * Historical release entry stored at `app_update_history/{id}`.
 */
data class UpdateHistoryRecord(
    val id: String = "",
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val url: String = "",
    val releaseDate: String = "",
    val updateMessage: String = "",
    val notes: String = "",
    val mandatory: Boolean = false,
    val popupEnabled: Boolean = true,
    val fileSizeMb: Double = 0.0,
    val releasedAtMillis: Long = 0L,
    val releasedBy: String = ""
)

/**
 * Extracted metadata from a local .apk file before upload or installation.
 */
data class ApkPackageMetadata(
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val fileSizeMb: Double,
    val isCompatiblePackage: Boolean
)
