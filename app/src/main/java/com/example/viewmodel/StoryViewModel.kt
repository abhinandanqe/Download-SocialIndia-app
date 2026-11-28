package com.example.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.RepositoryManager
import com.example.model.Story
import com.example.model.StorySticker
import com.example.model.StoryUiModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import java.util.Date

class StoryViewModel : ViewModel() {
    private val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private val _stories = MutableStateFlow<List<StoryUiModel>>(emptyList())
    val stories: StateFlow<List<StoryUiModel>> = _stories

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress

    private val pendingOptimisticStories = java.util.concurrent.ConcurrentHashMap<String, StoryUiModel>()
    private var listenerRegistration: ListenerRegistration? = null
    private val twentyFourHours = STORY_TTL_MS

    private data class CachedStoryUser(
        val username: String,
        val avatarUrl: String,
        val closeFriends: List<String>,
        val isVerified: Boolean = false,
        val verificationBadgeUrl: String = "",
        val fetchedAtMs: Long
    )

    private val userCache = java.util.concurrent.ConcurrentHashMap<String, CachedStoryUser>()
    private val deletedExpiredStoryIds = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    private var cachedFollowingIds: Set<String> = emptySet()
    private var followingFetchedAtMs: Long = 0L
    private var storyProcessJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var discoveryShuffleSeed: Long = System.currentTimeMillis()

    init {
        fetchStories()
        viewModelScope.launch {
            com.example.auth.MultiAccountManager.activeAccount
                .drop(1)
                .collect {
                    userCache.clear()
                    cachedFollowingIds = emptySet()
                    followingFetchedAtMs = 0L
                    discoveryShuffleSeed = System.currentTimeMillis()
                    fetchStories()
                }
        }
    }

    fun refreshStories() {
        followingFetchedAtMs = 0L
        discoveryShuffleSeed = System.currentTimeMillis()
        fetchStories()
    }

    private fun fetchStories() {
        listenerRegistration?.remove()
        val storiesRef = FirebaseFirestore.getInstance().collection("stories")

        fun attachStoryListener(useOrdering: Boolean) {
            val query = if (useOrdering) {
                storiesRef.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(80)
            } else {
                storiesRef.limit(80)
            }
            listenerRegistration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (useOrdering) {
                        listenerRegistration?.remove()
                        attachStoryListener(false)
                    }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val currentTime = Date().time
                storyProcessJob?.cancel()
                storyProcessJob = viewModelScope.launch(Dispatchers.IO) {
                    val rawStories = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(Story::class.java)?.copy(id = doc.id)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    val currentUserId = RepositoryManager.getCurrentUserId()
                    val validStories = rawStories.filter { story ->
                        if (story.isLive || story.isDeleted || story.isRemovedByAdmin || story.userId.isBlank() || story.userId == "anonymous_user") return@filter false
                        val storyTime = story.timestamp?.time ?: currentTime
                        val isExpired = (currentTime - storyTime) > twentyFourHours
                        if (isExpired && story.id.isNotEmpty() && story.userId == currentUserId && deletedExpiredStoryIds.add(story.id)) {
                            deleteStory(story.id)
                        }
                        val hasContent = story.mediaUrl.isNotBlank() || story.blurredThumbnailUrl.isNotBlank() || story.textOverlay.isNotBlank()
                        !isExpired && hasContent && !com.example.utils.MediaResolver.isDummyOrSampleUrl(story.mediaUrl)
                    }.sortedByDescending { it.timestamp ?: Date(0) }

                    val nowMs = System.currentTimeMillis()
                    if (currentUserId.isNotEmpty() && (cachedFollowingIds.isEmpty() || nowMs - followingFetchedAtMs > 60_000L)) {
                        try {
                            val followsSnapshot = FirebaseFirestore.getInstance().collection("follows")
                                .whereEqualTo("followerId", currentUserId)
                                .limit(200)
                                .get()
                                .await()
                            cachedFollowingIds = followsSnapshot.documents.mapNotNull { it.getString("followingId") }.toSet()
                            followingFetchedAtMs = nowMs
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e("StoryViewModel", "Failed to fetch following ids", e)
                        }
                    }
                    val followingIds = cachedFollowingIds

                    // Prioritize own stories -> followed users -> randomly shuffled public discoverable creator stories
                    val myStories = validStories.filter { it.userId.isNotEmpty() && it.userId == currentUserId }
                    val followedStories = validStories.filter { it.userId.isNotEmpty() && it.userId != currentUserId && followingIds.contains(it.userId) }
                    val publicDiscoverStories = validStories
                        .filter { it.userId.isNotEmpty() && it.userId != currentUserId && !followingIds.contains(it.userId) && !it.isCloseFriendsOnly }
                        .shuffled(kotlin.random.Random(discoveryShuffleSeed))
                    val eligibleStories = (myStories + followedStories + publicDiscoverStories).distinctBy { it.id }

                    // Batch-fetch missing unique story authors in parallel chunks of 10 (eliminates N+1 sequential queries)
                    val missingAuthorIds = eligibleStories.map { it.userId }.distinct().filter { uid ->
                        userCache[uid]?.let { nowMs - it.fetchedAtMs < 120_000L } != true
                    }
                    if (missingAuthorIds.isNotEmpty()) {
                        kotlinx.coroutines.coroutineScope {
                            missingAuthorIds.chunked(10).map { chunk ->
                                async(Dispatchers.IO) {
                                    try {
                                        val snap = RepositoryManager.firestore.collection("users")
                                            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                                            .get()
                                            .await()
                                        snap.documents.forEach { userDoc ->
                                            try {
                                                val closeFriendsList = (userDoc.get("closeFriends") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                                                val realAvatar = (userDoc.get("avatarUrl") as? String)
                                                    ?: (userDoc.get("profilePicUrl") as? String)
                                                    ?: (userDoc.get("avatar_url") as? String)
                                                    ?: ""
                                                val realUsername = (userDoc.get("username") as? String)
                                                    ?: (userDoc.get("displayName") as? String)
                                                    ?: "User"
                                                val vStatus = (userDoc.get("verificationStatus") as? String) ?: ""
                                                val vSuspended = (userDoc.get("verificationSuspended") as? Boolean) ?: false
                                                val realVerified = !vSuspended && (
                                                    ((userDoc.get("isVerified") as? Boolean) ?: false) ||
                                                    vStatus == "approved" ||
                                                    vStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE
                                                )
                                                val realBadgeUrl = (userDoc.get("verificationBadgeUrl") as? String) ?: ""
                                                userCache[userDoc.id] = CachedStoryUser(
                                                    username = realUsername,
                                                    avatarUrl = realAvatar,
                                                    closeFriends = closeFriendsList,
                                                    isVerified = realVerified,
                                                    verificationBadgeUrl = realBadgeUrl,
                                                    fetchedAtMs = nowMs
                                                )
                                            } catch (_: Exception) {}
                                        }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (_: Exception) {}
                                }
                            }.forEach { it.await() }
                        }
                    }

                    val liveCurrentUser = RepositoryManager.currentUserState.value
                    val list = eligibleStories.mapNotNull { story ->
                        try {
                            val cachedUser = userCache[story.userId] ?: CachedStoryUser(
                                username = "User",
                                avatarUrl = "",
                                closeFriends = emptyList(),
                                isVerified = false,
                                verificationBadgeUrl = "",
                                fetchedAtMs = nowMs
                            )
                            val effectiveVerified = if (story.userId == currentUserId && liveCurrentUser != null) {
                                !liveCurrentUser.verificationSuspended && (
                                    liveCurrentUser.isVerified ||
                                    liveCurrentUser.verificationStatus == "approved" ||
                                    liveCurrentUser.verificationStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE ||
                                    cachedUser.isVerified
                                )
                            } else {
                                cachedUser.isVerified
                            }
                            val effectiveBadgeUrl = if (story.userId == currentUserId && liveCurrentUser != null) {
                                liveCurrentUser.verificationBadgeUrl.ifBlank { cachedUser.verificationBadgeUrl }
                            } else {
                                cachedUser.verificationBadgeUrl
                            }

                            // Close friends privacy check
                            if (story.isCloseFriendsOnly && story.userId != currentUserId) {
                                if (!cachedUser.closeFriends.contains(currentUserId)) {
                                    return@mapNotNull null
                                }
                            }

                            val stickersList: List<StorySticker> = if (!story.stickersJson.isNullOrEmpty()) {
                                try {
                                    jsonParser.decodeFromString<List<StorySticker>>(story.stickersJson)
                                } catch (e: Exception) {
                                    emptyList()
                                }
                            } else emptyList()

                            StoryUiModel(
                                id = story.id,
                                username = cachedUser.username,
                                avatarUrl = cachedUser.avatarUrl,
                                imageUrl = story.mediaUrl,
                                textOverlay = story.textOverlay,
                                timeAgo = "Just now",
                                mentionedUserId = story.mentionedUserId,
                                mentionedUsername = story.mentionedUsername,
                                userId = story.userId,
                                viewers = story.viewers,
                                likesCount = story.likesCount,
                                commentsCount = story.commentsCount,
                                isVerified = effectiveVerified,
                                verificationBadgeUrl = effectiveBadgeUrl,
                                timestamp = story.timestamp?.time ?: currentTime,
                                isLive = story.isLive,
                                isReback = story.isReback,
                                repostedPostId = story.repostedPostId,
                                musicTrackName = story.musicTrackName,
                                musicTrackArtist = story.musicTrackArtist,
                                musicTrackUrl = story.musicTrackUrl,
                                isCloseFriendsOnly = story.isCloseFriendsOnly,
                                stickers = stickersList,
                                isExclusiveContent = story.isExclusiveContent || (story.isPremium && story.premiumPrice >= 10.0),
                                exclusivePrice = if (story.exclusivePrice >= 10.0) story.exclusivePrice else story.premiumPrice,
                                teaserCaption = story.teaserCaption,
                                blurredThumbnailUrl = story.blurredThumbnailUrl,
                                hasVaultMedia = story.hasVaultMedia,
                                unlockCount = story.unlockCount
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val distinctRemote = list.distinctBy { it.id }
                    distinctRemote.forEach { remote -> pendingOptimisticStories.remove(remote.id) }
                    _stories.value = mergeStoriesWithOptimistic(distinctRemote, pendingOptimisticStories, currentTime)
                }
            }
        }
        attachStoryListener(true)
    }

    private fun deleteStory(storyId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FirebaseFirestore.getInstance().collection("stories").document(storyId).delete().await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("StoryViewModel", "Failed to delete expired story: $storyId", e)
            }
        }
    }

    fun uploadStory(
        mediaUri: Uri?,
        textOverlay: String,
        musicTrackUrl: String?,
        musicTrackName: String?,
        musicTrackArtist: String?,
        mentionedUserId: String? = null,
        mentionedUsername: String? = null,
        isReback: Boolean = false,
        repostedPostId: String? = null,
        isCloseFriendsOnly: Boolean = false,
        stickersJson: String = "",
        isExclusiveContent: Boolean = false,
        exclusivePrice: Double = 0.0,
        teaserCaption: String = "",
        appContext: android.content.Context? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val storyId = java.util.UUID.randomUUID().toString()
            try {
                _isUploading.value = true
                _uploadProgress.value = 0.05f
                val currentUserId = RepositoryManager.getCurrentUserId()
                if (currentUserId.isEmpty() || currentUserId == "anonymous_user") {
                    throw Exception("Please sign in to share a story")
                }

                val isExclusivePaid = isExclusiveContent && exclusivePrice >= 10.0
                val liveUser = RepositoryManager.currentUserState.value
                val activeAcc = com.example.auth.MultiAccountManager.activeAccount.value
                val cachedAuthor = userCache[currentUserId]
                val authorUsername = liveUser?.username?.takeIf { it.isNotBlank() }
                    ?: activeAcc?.username?.takeIf { it.isNotBlank() }
                    ?: cachedAuthor?.username?.takeIf { it.isNotBlank() }
                    ?: "You"
                val authorAvatar = liveUser?.avatarUrl?.takeIf { it.isNotBlank() }
                    ?: activeAcc?.avatarUrl?.takeIf { it.isNotBlank() }
                    ?: cachedAuthor?.avatarUrl
                    ?: ""

                // Instant optimistic story in stories bar while upload runs
                val localPreviewUrl = mediaUri?.toString().orEmpty()
                val optimisticStory = StoryUiModel(
                    id = storyId,
                    username = authorUsername,
                    avatarUrl = authorAvatar,
                    imageUrl = if (isExclusivePaid) "" else localPreviewUrl,
                    textOverlay = if (isExclusivePaid) teaserCaption.ifBlank { textOverlay } else textOverlay,
                    timeAgo = "Just now",
                    mentionedUserId = mentionedUserId,
                    mentionedUsername = mentionedUsername,
                    userId = currentUserId,
                    isVerified = liveUser?.isVerified ?: cachedAuthor?.isVerified ?: false,
                    verificationBadgeUrl = liveUser?.verificationBadgeUrl ?: cachedAuthor?.verificationBadgeUrl ?: "",
                    timestamp = System.currentTimeMillis(),
                    isReback = isReback,
                    repostedPostId = repostedPostId,
                    musicTrackName = musicTrackName,
                    musicTrackArtist = musicTrackArtist,
                    musicTrackUrl = musicTrackUrl,
                    isCloseFriendsOnly = isCloseFriendsOnly,
                    isExclusiveContent = isExclusivePaid,
                    exclusivePrice = if (isExclusivePaid) exclusivePrice else 0.0,
                    teaserCaption = if (isExclusivePaid) teaserCaption.trim() else "",
                    blurredThumbnailUrl = if (isExclusivePaid) localPreviewUrl else "",
                    hasVaultMedia = isExclusivePaid
                )
                pendingOptimisticStories[storyId] = optimisticStory
                _stories.value = mergeStoriesWithOptimistic(_stories.value, pendingOptimisticStories)

                var finalMediaUrl = ""
                var blurredPreviewUrl = ""
                var isVideoStr = false

                if (mediaUri != null) {
                    val uriStr = mediaUri.toString()
                    val resolvedMime = try {
                        appContext?.contentResolver?.getType(mediaUri) ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                    isVideoStr = resolvedMime.startsWith("video/", ignoreCase = true) ||
                        uriStr.contains("video", ignoreCase = true) ||
                        uriStr.endsWith(".mp4", ignoreCase = true) ||
                        isReback

                    if (uriStr.startsWith("http")) {
                        finalMediaUrl = uriStr
                    } else {
                        val uploadedUrl = RepositoryManager.mediaRepository.uploadMedia(mediaUri) { pct ->
                            _uploadProgress.value = (0.05f + (pct.coerceIn(0, 100) / 100f) * 0.78f).coerceIn(0.05f, 0.85f)
                        }
                        val rawUrl = uploadedUrl ?: throw Exception("Failed to upload story media. Please check connection.")
                        finalMediaUrl = com.example.utils.GoogleDrivePermissionRepairManager.normalizeDriveUrl(rawUrl, isVideoStr)
                    }

                    if (!com.example.utils.VideoTranscoder.isValidRemoteMediaUrl(finalMediaUrl)) {
                        throw Exception("Story media URL could not be verified after upload.")
                    }

                    if (isVideoStr && !finalMediaUrl.contains("video", ignoreCase = true) && !finalMediaUrl.contains(".mp4", ignoreCase = true)) {
                        finalMediaUrl += "#video.mp4"
                    }

                    // Generate separate heavily blurred preview for exclusive stories
                    if (isExclusivePaid && appContext != null && !uriStr.startsWith("http")) {
                        try {
                            val blurFile = com.example.utils.ExclusiveMediaHelper.generateBlurredPreviewFile(
                                context = appContext,
                                mediaUri = mediaUri,
                                isVideo = isVideoStr
                            )
                            if (blurFile != null && blurFile.exists()) {
                                blurredPreviewUrl = RepositoryManager.mediaRepository.uploadMedia(Uri.fromFile(blurFile)) ?: ""
                                try { blurFile.delete() } catch (_: Exception) {}
                            }
                        } catch (e: Exception) {
                            Log.w("StoryViewModel", "Exclusive story blur generation failed: ${e.message}")
                        }
                    }
                } else if (textOverlay.isBlank() && stickersJson.isBlank()) {
                    throw Exception("Please capture or pick a photo/video, or add text first.")
                }

                _uploadProgress.value = 0.88f
                if (isExclusivePaid) {
                    val vaultSaved = com.example.repository.ExclusiveContentRepository.saveVaultMedia(
                        contentId = storyId,
                        creatorId = currentUserId,
                        contentType = "STORY",
                        mediaUrl = finalMediaUrl,
                        postUrls = if (finalMediaUrl.isNotBlank()) listOf(finalMediaUrl) else emptyList(),
                        thumbnailUrl = blurredPreviewUrl
                    )
                    if (!vaultSaved) {
                        throw Exception("Could not secure exclusive story media. Please try again.")
                    }
                }

                _uploadProgress.value = 0.93f
                val publicStoryUrl = if (isExclusivePaid) blurredPreviewUrl else finalMediaUrl
                val storyMap = hashMapOf(
                    "id" to storyId,
                    "userId" to currentUserId,
                    "mediaUrl" to publicStoryUrl,
                    "blurredThumbnailUrl" to if (isExclusivePaid) blurredPreviewUrl else "",
                    "textOverlay" to if (isExclusivePaid) teaserCaption.ifBlank { textOverlay } else textOverlay,
                    "teaserCaption" to if (isExclusivePaid) teaserCaption.trim() else "",
                    "isExclusiveContent" to isExclusivePaid,
                    "isPremium" to isExclusivePaid,
                    "exclusivePrice" to if (isExclusivePaid) exclusivePrice else 0.0,
                    "premiumPrice" to if (isExclusivePaid) exclusivePrice else 0.0,
                    "hasVaultMedia" to isExclusivePaid,
                    "unlockCount" to 0,
                    "exclusiveRevenue" to 0.0,
                    "isRemovedByAdmin" to false,
                    "mentionedUserId" to (mentionedUserId ?: ""),
                    "mentionedUsername" to (mentionedUsername ?: ""),
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "musicTrackUrl" to (musicTrackUrl ?: ""),
                    "musicTrackName" to (musicTrackName ?: ""),
                    "musicTrackArtist" to (musicTrackArtist ?: ""),
                    "expireAt" to Date(System.currentTimeMillis() + twentyFourHours),
                    "isLive" to false,
                    "isReback" to isReback,
                    "repostedPostId" to (repostedPostId ?: ""),
                    "isCloseFriendsOnly" to isCloseFriendsOnly,
                    "stickersJson" to stickersJson
                )

                val docRef = FirebaseFirestore.getInstance().collection("stories").document(storyId)
                docRef.set(storyMap).await()
                _uploadProgress.value = 1f
                pendingOptimisticStories[storyId] = optimisticStory.copy(
                    imageUrl = publicStoryUrl,
                    blurredThumbnailUrl = if (isExclusivePaid) blurredPreviewUrl else ""
                )
                _stories.value = mergeStoriesWithOptimistic(_stories.value, pendingOptimisticStories)

                // Save authoritative Story Mentions and dispatch notifications
                try {
                    val currentUserDoc = FirebaseFirestore.getInstance().collection("users").document(currentUserId).get().await()
                    val username = currentUserDoc.getString("username") ?: "User"
                    val avatarUrl = currentUserDoc.getString("profilePicUrl") ?: currentUserDoc.getString("avatarUrl") ?: ""
                    val displayName = currentUserDoc.getString("displayName") ?: username

                    val storyMentions = mutableListOf<com.example.model.MentionDoc>()

                    // 1. Direct sticker / picker mentions
                    if (!mentionedUserId.isNullOrEmpty() && mentionedUserId != currentUserId) {
                        storyMentions.add(
                            com.example.model.MentionDoc(
                                mentionedUserId = mentionedUserId,
                                mentionedUsername = mentionedUsername ?: "User",
                                contentType = "story",
                                contentId = docRef.id,
                                sourceContext = "story_picker",
                                mentionText = textOverlay,
                                mediaUrl = finalMediaUrl
                            )
                        )
                    }

                    // 2. Mentions inside stickersJson
                    if (!stickersJson.isNullOrEmpty()) {
                        try {
                            val stickersList: List<com.example.model.StorySticker> = jsonParser.decodeFromString(stickersJson)
                            for (st in stickersList) {
                                if (st.type == "mention" && st.mentionUserId.isNotEmpty() && st.mentionUserId != currentUserId) {
                                    if (!storyMentions.any { it.mentionedUserId == st.mentionUserId }) {
                                        storyMentions.add(
                                            com.example.model.MentionDoc(
                                                mentionedUserId = st.mentionUserId,
                                                mentionedUsername = st.mentionUsername,
                                                contentType = "story",
                                                contentId = docRef.id,
                                                sourceContext = "story_sticker",
                                                mentionText = textOverlay,
                                                mediaUrl = finalMediaUrl,
                                                xRatio = st.xPercent,
                                                yRatio = st.yPercent
                                            )
                                        )
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // 3. Caption / textOverlay regex mentions
                    val mentionRegex = Regex("@([a-zA-Z0-9_.]+)")
                    val mentionedUsernames = mentionRegex.findAll(textOverlay).map { it.groupValues[1].lowercase() }.toSet()
                    for (mUsername in mentionedUsernames) {
                        try {
                            val userSnap = FirebaseFirestore.getInstance().collection("users").whereEqualTo("username", mUsername).limit(1).get().await()
                            val mDoc = userSnap.documents.firstOrNull()
                            if (mDoc != null && mDoc.id != currentUserId && !storyMentions.any { it.mentionedUserId == mDoc.id }) {
                                storyMentions.add(
                                    com.example.model.MentionDoc(
                                        mentionedUserId = mDoc.id,
                                        mentionedUsername = mUsername,
                                        contentType = "story",
                                        contentId = docRef.id,
                                        sourceContext = "story_text",
                                        mentionText = textOverlay,
                                        mediaUrl = finalMediaUrl
                                    )
                                )
                            }
                        } catch (_: Exception) {}
                    }

                    if (storyMentions.isNotEmpty()) {
                        com.example.repository.MentionRepository.saveContentMentions(
                            contentId = docRef.id,
                            contentType = "story",
                            authorUid = currentUserId,
                            authorUsername = username,
                            authorAvatar = avatarUrl,
                            authorDisplayName = displayName,
                            mentions = storyMentions,
                            caption = textOverlay,
                            mediaUrl = finalMediaUrl
                        )
                    }
                } catch (e: Exception) {
                    Log.w("StoryViewModel", "Failed saving story mentions", e)
                }

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    _isUploading.value = false
                    _uploadProgress.value = 0f
                    onSuccess()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                pendingOptimisticStories.remove(storyId)
                _stories.value = _stories.value.filterNot { it.id == storyId }
                _isUploading.value = false
                _uploadProgress.value = 0f
                throw e
            } catch (e: Exception) {
                pendingOptimisticStories.remove(storyId)
                _stories.value = _stories.value.filterNot { it.id == storyId }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    _isUploading.value = false
                    _uploadProgress.value = 0f
                    onError(e.message ?: "Failed to publish story")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        storyProcessJob?.cancel()
        storyProcessJob = null
        listenerRegistration?.remove()
    }

    companion object {
        const val STORY_TTL_MS = 24 * 60 * 60 * 1000L

        fun isStoryExpired(storyTimestampMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
            if (storyTimestampMs <= 0L) return false
            return (nowMs - storyTimestampMs) > STORY_TTL_MS
        }

        fun mergeStoriesWithOptimistic(
            remoteStories: List<StoryUiModel>,
            optimisticMap: Map<String, StoryUiModel>,
            nowMs: Long = System.currentTimeMillis()
        ): List<StoryUiModel> {
            val remoteIds = remoteStories.map { it.id }.toSet()
            val activeOptimistic = optimisticMap.values.filter {
                it.id !in remoteIds && !isStoryExpired(it.timestamp, nowMs)
            }
            return (activeOptimistic + remoteStories).distinctBy { it.id }
        }
    }
}
