package com.example.model

/**
 * Universal payload representing any shareable piece of content in Social India.
 */
data class SharePayload(
    val contentType: ShareContentType = ShareContentType.REEL,
    val contentId: String = "",
    val ownerUid: String = "",
    val ownerUsername: String = "User",
    val ownerAvatar: String = "",
    val thumbnailUrl: String = "",
    val mediaUrl: String = "",
    val caption: String = "",
    val canonicalUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isVerified: Boolean = false,
    val isExclusiveContent: Boolean = false,
    val isUnlocked: Boolean = true,
    val unlockPriceInr: Double = 0.0,
    val extraData: Map<String, String> = emptyMap()
) {
    companion object {
        fun fromVideo(video: Video): SharePayload {
            val isReel = video.videoUrl.isNotBlank()
            val type = if (isReel) ShareContentType.REEL else ShareContentType.POST
            val canonical = "https://socialindia.app/reel/${video.id}"
            val thumb = if (video.videoUrl.isNotBlank()) video.videoUrl else (video.videoUrls.firstOrNull() ?: "")
            val isExPaid = (video.isExclusiveContent || video.isPaidPost) && video.postUnlockPrice >= 10.0
            val curUid = RepositoryManager.getCurrentUserId()
            val isOwner = curUid.isNotEmpty() && video.userId == curUid
            val unlocked = if (!isExPaid || isOwner) {
                true
            } else {
                com.example.repository.EntitlementRepository.hasAccessFast(
                    userId = curUid,
                    creatorId = video.userId,
                    contentId = video.id,
                    isExclusive = true,
                    isPaidPost = true
                )
            }
            return SharePayload(
                contentType = type,
                contentId = video.id,
                ownerUid = video.userId.ifBlank { video.user.id },
                ownerUsername = video.user.username,
                ownerAvatar = video.user.avatarUrl,
                thumbnailUrl = thumb,
                mediaUrl = video.videoUrl,
                caption = video.description,
                canonicalUrl = canonical,
                isVerified = video.user.isVerified,
                isExclusiveContent = isExPaid,
                isUnlocked = unlocked,
                unlockPriceInr = video.postUnlockPrice
            )
        }

        fun fromUser(user: User): SharePayload {
            val canonical = "https://socialindia.app/profile/${user.id}"
            return SharePayload(
                contentType = ShareContentType.PROFILE,
                contentId = user.id,
                ownerUid = user.id,
                ownerUsername = user.username,
                ownerAvatar = user.avatarUrl,
                thumbnailUrl = user.avatarUrl,
                mediaUrl = user.avatarUrl,
                caption = user.bio,
                canonicalUrl = canonical,
                isVerified = user.isVerified
            )
        }

        fun fromStory(
            storyId: String,
            creatorName: String = "User",
            creatorAvatar: String = "",
            mediaUrl: String = "",
            ownerUid: String = "",
            isExclusiveContent: Boolean = false,
            isUnlocked: Boolean = true
        ): SharePayload {
            val canonical = "https://socialindia.app/story/$storyId"
            return SharePayload(
                contentType = ShareContentType.STORY,
                contentId = storyId,
                ownerUid = ownerUid,
                ownerUsername = creatorName,
                ownerAvatar = creatorAvatar,
                thumbnailUrl = mediaUrl,
                mediaUrl = mediaUrl,
                caption = "Story by $creatorName",
                canonicalUrl = canonical,
                isExclusiveContent = isExclusiveContent,
                isUnlocked = isUnlocked
            )
        }

        fun fromAudio(audioId: String, title: String, artist: String, coverUrl: String, audioUrl: String = ""): SharePayload {
            val canonical = "https://socialindia.app/audio/$audioId"
            return SharePayload(
                contentType = ShareContentType.AUDIO,
                contentId = audioId,
                ownerUid = "",
                ownerUsername = artist,
                ownerAvatar = coverUrl,
                thumbnailUrl = coverUrl,
                mediaUrl = audioUrl,
                caption = "$title - $artist",
                canonicalUrl = canonical
            )
        }
    }
}

enum class ShareContentType(val label: String) {
    POST("Post"),
    REEL("Reel"),
    PHOTO("Photo"),
    VIDEO("Video"),
    PROFILE("Profile"),
    AUDIO("Audio"),
    STORY("Story"),
    LIVE("Live Stream")
}
