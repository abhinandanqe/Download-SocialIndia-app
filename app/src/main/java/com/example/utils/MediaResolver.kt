package com.example.utils

import android.net.Uri

object MediaResolver {
    private val knownDeadUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    const val FALLBACK_AUDIO_URL = "https://cdn.pixabay.com/audio/2022/10/18/audio_31c2730e64.mp3"

    private val DUMMY_VIDEO_PATTERNS = listOf(
        "mov_bbb",
        "w3schools.com",
        "gtv-videos-bucket",
        "bigbuckbunny",
        "forbigger",
        "elephantsdream",
        "sintel",
        "tearsofsteel",
        "subaruoutback",
        "wearegoingonbullrun",
        "whatcarcanyougetforagrand",
        "sample-videos.com",
        "test-videos.co.uk"
    )

    fun isDummyOrSampleUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase().trim()
        return DUMMY_VIDEO_PATTERNS.any { lower.contains(it) }
    }

    fun markDeadUrl(url: String?) {
        if (!url.isNullOrBlank()) {
            knownDeadUrls.add(url.trim())
        }
    }

    fun isDeadUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        return knownDeadUrls.contains(url.trim())
    }

    fun stripExtParam(url: String): String {
        return url.trim()
            .replace(Regex("([?&])ext=\\.?[a-zA-Z0-9]+(&|$)"), "$1")
            .trimEnd('?', '&')
    }

    fun isAudioUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".mp3") || lower.contains(".wav") || lower.contains(".m4a") ||
                lower.contains(".aac") || lower.contains(".ogg") || lower.contains("/audio/") ||
                lower.contains("saavn") || lower.contains("itunes.apple.com") || lower.contains("soundhelix")
    }

    fun sanitizeKnownBrokenUrl(url: String): String {
        if (isDummyOrSampleUrl(url)) return ""
        val cleaned = stripExtParam(url)
        return when {
            cleaned.contains("audio_03d6a97ee4") -> "https://cdn.pixabay.com/audio/2022/10/18/audio_31c2730e64.mp3"
            cleaned.contains("audio_fc8cb949bc") -> "https://cdn.pixabay.com/audio/2022/08/02/audio_884fe92c21.mp3"
            cleaned.contains("audio_c8c8a73467") -> "https://cdn.pixabay.com/audio/2022/05/27/audio_1808fbf07a.mp3"
            knownDeadUrls.contains(cleaned) && isAudioUrl(cleaned) -> FALLBACK_AUDIO_URL
            else -> cleaned
        }
    }

    fun resolveVideoUrl(url: String?): String {
        if (url.isNullOrEmpty() || isDummyOrSampleUrl(url)) return ""
        val cleanInput = stripExtParam(url)
        if (knownDeadUrls.contains(cleanInput) && !isAudioUrl(cleanInput)) return ""
        val resolved = sanitizeKnownBrokenUrl(cleanInput)
        if (resolved.isEmpty()) return ""

        // If it's strictly an image endpoint (e.g. Drive thumbnail), do not pass to ExoPlayer as a video
        if (isImage(url)) return ""

        // Check if it's a device-local file URI (e.g. file:///data/user/0/...)
        if (resolved.startsWith("file://") || resolved.startsWith("/data/") || resolved.startsWith("/storage/")) {
            val localPath = if (resolved.startsWith("file://")) resolved.removePrefix("file://") else resolved
            val file = java.io.File(localPath)
            if (!file.exists() || file.length() == 0L) {
                return ""
            }
            return resolved
        }

        // Handle tmpfiles.org URLs - convert page link to direct /dl/ streaming endpoint if needed
        if (resolved.contains("tmpfiles.org") && !resolved.contains("/dl/")) {
            val streamUrl = resolved.replace("https://tmpfiles.org/", "https://tmpfiles.org/dl/")
                .replace("http://tmpfiles.org/", "https://tmpfiles.org/dl/")
            return sanitizeKnownBrokenUrl(streamUrl)
        }

        // Extract Google Drive ID and convert to direct streamable URL
        val fileId = extractGoogleDriveFileId(resolved)
        if (!fileId.isNullOrEmpty()) {
            GoogleDrivePermissionRepairManager.repairPermissionAsync(fileId)
            return "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        }

        if (resolved.contains("drive.google.com/uc") && !resolved.contains("confirm=t")) {
            return if (resolved.contains("?")) "$resolved&confirm=t" else "$resolved?confirm=t"
        }

        return resolved
    }

    fun getFallbackVideoUrls(url: String?): List<String> {
        if (url.isNullOrBlank() || isDummyOrSampleUrl(url)) return emptyList()
        val primary = resolveVideoUrl(url)
        if (primary.isEmpty()) return emptyList()
        val fileId = extractGoogleDriveFileId(url)
        if (!fileId.isNullOrEmpty()) {
            GoogleDrivePermissionRepairManager.repairPermissionAsync(fileId)
            return listOf(
                "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t",
                "https://drive.google.com/uc?id=$fileId&export=download&confirm=t"
            ).distinct()
        }
        return listOf(primary)
    }

    fun getFallbackImageUrls(url: String?): List<String> {
        if (url.isNullOrBlank()) return emptyList()
        val primary = resolveImageUrl(url)
        val fileId = extractGoogleDriveFileId(url)
        if (!fileId.isNullOrEmpty()) {
            GoogleDrivePermissionRepairManager.repairPermissionAsync(fileId)
            return listOf(
                "https://drive.google.com/thumbnail?id=$fileId&sz=w1080",
                "https://lh3.googleusercontent.com/d/$fileId=w1080",
                "https://drive.usercontent.google.com/download?id=$fileId&export=view",
                "https://drive.google.com/uc?id=$fileId&export=view"
            ).distinct()
        }
        return if (primary.isNotBlank()) listOf(primary) else emptyList()
    }

    fun resolveVideoPosterUrl(videoUrl: String?, thumbnailUrl: String?): String {
        val cleanThumb = thumbnailUrl?.trim().orEmpty()
        if (cleanThumb.isNotEmpty() && isImage(cleanThumb)) {
            val resolvedThumb = resolveImageUrl(cleanThumb)
            if (resolvedThumb.isNotEmpty()) return resolvedThumb
        }
        val cleanVideo = videoUrl?.trim().orEmpty()
        if (cleanVideo.isEmpty()) return if (cleanThumb.isNotEmpty()) resolveImageUrl(cleanThumb) else ""

        val driveId = extractGoogleDriveFileId(cleanThumb) ?: extractGoogleDriveFileId(cleanVideo)
        if (!driveId.isNullOrEmpty()) {
            GoogleDrivePermissionRepairManager.repairPermissionAsync(driveId)
            return "https://drive.google.com/thumbnail?id=$driveId&sz=w1080"
        }

        if (cleanVideo.contains("res.cloudinary.com") && cleanVideo.contains("/video/upload/")) {
            return cleanVideo.substringBefore("?")
                .replace(Regex("\\.(mp4|mov|webm|mkv|m4v|3gp)$", RegexOption.IGNORE_CASE), ".jpg")
        }

        if (cleanThumb.isNotEmpty()) {
            return resolveImageUrl(cleanThumb)
        }

        return resolveVideoUrl(cleanVideo)
    }

    fun resolveImageUrl(url: String?): String {
        if (url.isNullOrEmpty()) return ""
        val resolved = url.trim()
            .removeSuffix("&ext=.mp4")
            .removeSuffix("?ext=.mp4")
            .removeSuffix("&ext=.jpg")
            .removeSuffix("?ext=.jpg")

        // Check for remote device-local files
        if (resolved.startsWith("file://") || resolved.startsWith("/data/") || resolved.startsWith("/storage/")) {
            val localPath = if (resolved.startsWith("file://")) resolved.removePrefix("file://") else resolved
            val file = java.io.File(localPath)
            if (!file.exists() || file.length() == 0L) {
                return ""
            }
            return resolved
        }

        val fileId = extractGoogleDriveFileId(resolved)
        if (!fileId.isNullOrEmpty()) {
            GoogleDrivePermissionRepairManager.repairPermissionAsync(fileId)
            // High-resolution direct image thumbnail endpoint that loads without cookie/auth blocks
            return "https://drive.google.com/thumbnail?id=$fileId&sz=w1080"
        }

        return resolved
    }

    fun extractGoogleDriveFileId(url: String): String? {
        return try {
            val clean = url.trim().substringBefore("#")
            if (clean.contains("drive.google.com/file/d/")) {
                val parts = clean.split("/")
                val dIndex = parts.indexOf("d")
                if (dIndex != -1 && parts.size > dIndex + 1) {
                    parts[dIndex + 1].substringBefore("?").substringBefore("&").substringBefore("/").trim().takeIf { it.isNotEmpty() }
                } else null
            } else if (clean.contains("docs.google.com/file/d/")) {
                clean.substringAfter("docs.google.com/file/d/").substringBefore("?").substringBefore("&").substringBefore("/").trim().takeIf { it.isNotEmpty() }
            } else if (clean.contains("googleapis.com/drive/v3/files/")) {
                clean.substringAfter("googleapis.com/drive/v3/files/").substringBefore("?").substringBefore("&").substringBefore("/").trim().takeIf { it.isNotEmpty() }
            } else if (clean.contains("lh3.googleusercontent.com/d/")) {
                clean.substringAfter("lh3.googleusercontent.com/d/").substringBefore("=").substringBefore("?").substringBefore("&").substringBefore("/").trim().takeIf { it.isNotEmpty() }
            } else if (clean.contains("drive.google.com/open") ||
                clean.contains("drive.google.com/thumbnail") ||
                clean.contains("drive.google.com/uc") ||
                clean.contains("drive.usercontent.google.com")
            ) {
                val regexMatch = Regex("[?&]id=([a-zA-Z0-9_-]+)").find(clean)?.groupValues?.getOrNull(1)
                regexMatch?.takeIf { it.isNotEmpty() } ?: Uri.parse(clean).getQueryParameter("id")?.trim()?.takeIf { it.isNotEmpty() }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun isImage(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val lower = url.lowercase().trim()

        // Definite video indicators
        if (lower.contains("ext=.mp4") || lower.contains(".mp4") || 
            lower.contains(".mov") || lower.contains(".mkv") || 
            lower.contains(".webm") || lower.contains(".3gp") || 
            lower.contains(".m4v") || lower.contains("video/") ||
            lower.contains("type=video")) {
            return false
        }

        // Definite image indicators
        if (lower.contains("ext=.jpg") || lower.contains("ext=.jpeg") || 
            lower.contains("ext=.png") || lower.contains("ext=.webp") || 
            lower.contains("ext=.gif") || lower.contains("type=image") ||
            lower.contains("/image/") || lower.contains("image") ||
            lower.contains(".jpg") || lower.contains(".jpeg") ||
            lower.contains(".png") || lower.contains(".webp") ||
            lower.contains(".gif") || lower.contains("lh3.googleusercontent.com") ||
            lower.contains("drive.google.com/thumbnail") || lower.contains("avatar") ||
            lower.contains("photo") || lower.contains("picsum") ||
            lower.contains("dicebear") || lower.contains("unsplash")) {
            return true
        }

        val cleanUrl = lower.substringBefore("?")
        if (cleanUrl.endsWith(".jpg") || cleanUrl.endsWith(".jpeg") || 
            cleanUrl.endsWith(".png") || cleanUrl.endsWith(".webp") || 
            cleanUrl.endsWith(".gif")) {
            return true
        }
        
        return false
    }
}
