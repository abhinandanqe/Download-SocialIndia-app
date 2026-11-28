package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.User
import com.example.model.Video
import com.example.model.StoryUiModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive

class VideoViewModel : ViewModel() {

    enum class FeedType { FOR_YOU, FOLLOWING, COMMUNITY }

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    
    private val _feedType = MutableStateFlow(FeedType.FOR_YOU)
    val feedType: StateFlow<FeedType> = _feedType

    private val _followingUserIds = MutableStateFlow<Set<String>>(emptySet())
    val followingUserIds: StateFlow<Set<String>> = _followingUserIds

    private val _followerUserIds = MutableStateFlow<Set<String>>(emptySet())
    val followerUserIds: StateFlow<Set<String>> = _followerUserIds

    private val _notInterestedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val notInterestedVideoIds: StateFlow<Set<String>> = _notInterestedVideoIds

    private val _hiddenCreatorIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenCreatorIds: StateFlow<Set<String>> = _hiddenCreatorIds

    private data class FeedFilterState(
        val videos: List<Video>,
        val feedType: FeedType,
        val following: Set<String>,
        val followers: Set<String>
    )

    val videos: StateFlow<List<Video>> = combine(
        combine(_videos, _feedType, _followingUserIds, _followerUserIds) { vids, fType, following, followers ->
            FeedFilterState(vids, fType, following, followers)
        },
        _notInterestedVideoIds,
        _hiddenCreatorIds
    ) { baseState, notInterested, hiddenCreators ->
        val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
        val filtered = baseState.videos.filter {
            !notInterested.contains(it.id) && !hiddenCreators.contains(it.userId)
        }
        val result = when (baseState.feedType) {
            FeedType.FOR_YOU -> filtered
            FeedType.FOLLOWING -> filtered.filter { baseState.following.contains(it.userId) || it.isReposted || (currentUserId.isNotEmpty() && it.userId == currentUserId) }
            FeedType.COMMUNITY -> {
                val friendIds = baseState.following + baseState.followers + if (currentUserId.isNotEmpty()) setOf(currentUserId) else emptySet()
                filtered.filter { friendIds.contains(it.userId) || it.isReposted }
            }
        }
        result.distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _feedError = MutableStateFlow<String?>(null)
    val feedError: StateFlow<String?> = _feedError
    
    private val _stories = MutableStateFlow<List<com.example.model.StoryUiModel>>(emptyList())
    val stories: StateFlow<List<com.example.model.StoryUiModel>> = _stories

    private val pageListeners = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
    private val pageDocs = java.util.concurrent.ConcurrentHashMap<Int, List<com.example.model.FirebasePost>>()
    private var lastVisibleDocument: com.google.firebase.firestore.DocumentSnapshot? = null
    private var hasMorePages = true
    private var loadedPageCount = 0
    private var lastRawFetchedCount = 0
    private var isFetchingMore = false

    private var storiesSnapshotListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        val cached = com.example.model.RepositoryManager.getCachedFeedVideos()
        if (cached.isNotEmpty()) {
            _videos.value = cached
            _isLoading.value = false
        }
        com.example.model.GlobalUploadManager.onUploadSuccessCallback = {
            refreshVideos()
        }
        viewModelScope.launch {
            com.example.model.RepositoryManager.triggerAnonymousCleanupOnce()
            fetchVideos()
            fetchStories()
            setupRealtime()
            setupFollowingObserver()
        }
        viewModelScope.launch {
            com.example.auth.MultiAccountManager.activeAccount
                .drop(1)
                .collect {
                    refreshVideos()
                    fetchStories()
                }
        }
    }

    private var feedProcessJob: kotlinx.coroutines.Job? = null

    private fun fetchStories() {
        storiesSnapshotListener?.remove()
        storiesSnapshotListener = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("stories")
            .limit(40)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("VideoViewModel", "Story Listen failed.", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val currentTime = System.currentTimeMillis()
                    val twentyFourHours = 24 * 60 * 60 * 1000L
                    
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            val userId = doc.getString("userId") ?: ""
                            if (userId.isEmpty() || userId == "anonymous_user") return@mapNotNull null
                            
                            val timestamp = doc.getDate("timestamp") 
                                ?: doc.getLong("timestampMillis")?.let { java.util.Date(it) }
                                ?: doc.getLong("expiryTime")?.let { java.util.Date(it - twentyFourHours) }
                                ?: java.util.Date()
                            
                            // Check if story is older than 24 hours (with some grace period for clock skew)
                            if ((currentTime - timestamp.time) > (twentyFourHours + 300000)) return@mapNotNull null
                            
                            val imageUrl = doc.getString("mediaUrl") ?: doc.getString("fileUrl") ?: doc.getString("imageUrl") ?: ""
                            if (imageUrl.isEmpty() && (doc.getString("textOverlay") ?: "").isEmpty()) return@mapNotNull null

                            StoryUiModel(
                                id = doc.id,
                                username = doc.getString("username") ?: "Creator",
                                avatarUrl = doc.getString("avatarUrl") ?: doc.getString("profilePicUrl") ?: "",
                                imageUrl = imageUrl,
                                textOverlay = doc.getString("textOverlay") ?: "",
                                userId = userId,
                                timestamp = timestamp.time,
                                isLive = doc.getBoolean("isLive") ?: false
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedByDescending { it.timestamp }
                    _stories.value = list.distinctBy { it.id }
                }
            }
    }
    
    private var followingObserverJob: kotlinx.coroutines.Job? = null

    private fun setupFollowingObserver() {
        followingObserverJob?.cancel()
        followingObserverJob = viewModelScope.launch {
            val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
            if (currentUserId.isNotEmpty()) {
                launch {
                    com.example.model.RepositoryManager.socialRepository.getFollowingIds(currentUserId).collect { followingSet ->
                        _followingUserIds.value = followingSet
                    }
                }
                launch {
                    com.example.model.RepositoryManager.socialRepository.getFollowerIds(currentUserId).collect { followerSet ->
                        _followerUserIds.value = followerSet
                    }
                }
                launch {
                    com.example.model.RepositoryManager.reelsActionsRepository.getNotInterestedVideoIds(currentUserId).collect { notInterestedSet ->
                        _notInterestedVideoIds.value = notInterestedSet
                    }
                }
                launch {
                    com.example.model.RepositoryManager.reelsActionsRepository.getHiddenCreatorIds(currentUserId).collect { hiddenSet ->
                        _hiddenCreatorIds.value = hiddenSet
                    }
                }
            }
        }
    }

    fun setFeedType(type: FeedType) {
        _feedType.value = type
        if (type == FeedType.FOLLOWING || type == FeedType.COMMUNITY) {
            setupFollowingObserver()
        }
    }

    private fun setupRealtime() {
        viewModelScope.launch {
            // (Removed Appwrite interaction)
        }
    }

    fun refreshVideos() {
        _isRefreshing.value = true
        setupFollowingObserver()
        fetchVideos()
        fetchStories()
    }
    
    private val userCache = java.util.concurrent.ConcurrentHashMap<String, com.example.model.User>()
    
    private fun clearPageListeners() {
        pageListeners.forEach { listener ->
            try { listener.remove() } catch (_: Exception) {}
        }
        pageListeners.clear()
        pageDocs.clear()
        lastVisibleDocument = null
        hasMorePages = true
        loadedPageCount = 0
        isFetchingMore = false
    }

    fun loadMore() {
        val cursor = lastVisibleDocument ?: return
        if (!isFetchingMore && hasMorePages && (loadedPageCount * PAGE_SIZE) < MAX_FEED_LIMIT) {
            isFetchingMore = true
            val nextPageIndex = loadedPageCount
            loadedPageCount++
            attachPageListener(pageIndex = nextPageIndex, cursorDoc = cursor, useOrdering = true)
        }
    }

    private fun fetchVideos() {
        clearPageListeners()
        loadedPageCount = 1
        attachPageListener(pageIndex = 0, cursorDoc = null, useOrdering = true)
    }

    private fun attachPageListener(
        pageIndex: Int,
        cursorDoc: com.google.firebase.firestore.DocumentSnapshot?,
        useOrdering: Boolean
    ) {
        val postsCollection = com.example.model.RepositoryManager.firestore.collection("posts")
        val limitCount = if (pageIndex == 0) INITIAL_FEED_LIMIT.toLong() else PAGE_SIZE.toLong()
        val orderedOrBase = if (useOrdering) {
            postsCollection.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
        } else {
            postsCollection
        }
        val query = if (cursorDoc != null) {
            orderedOrBase.startAfter(cursorDoc).limit(limitCount)
        } else {
            orderedOrBase.limit(limitCount)
        }

        lateinit var registration: com.google.firebase.firestore.ListenerRegistration
        registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("VideoViewModel", "Feed page $pageIndex listen failed (useOrdering=$useOrdering): ${error.message}", error)
                if (useOrdering && pageIndex == 0 && cursorDoc == null) {
                    try { registration.remove() } catch (_: Exception) {}
                    pageListeners.remove(registration)
                    attachPageListener(pageIndex = 0, cursorDoc = null, useOrdering = false)
                    return@addSnapshotListener
                }
                if (pageIndex > 0) {
                    hasMorePages = false
                    isFetchingMore = false
                    return@addSnapshotListener
                }
                _feedError.value = "Couldn't load feed. Check your connection and try again."
                _isRefreshing.value = false
                _isLoading.value = false
                isFetchingMore = false
                return@addSnapshotListener
            }

            if (snapshot != null) {
                lastRawFetchedCount = snapshot.documents.size
                isFetchingMore = false
                _feedError.value = null
                if (pageIndex == loadedPageCount - 1) {
                    lastVisibleDocument = snapshot.documents.lastOrNull()
                    hasMorePages = snapshot.documents.size >= limitCount && (loadedPageCount * PAGE_SIZE) < MAX_FEED_LIMIT
                }

                val parsedPageDocs = snapshot.documents.mapNotNull { doc ->
                    try {
                        val p = doc.toObject(com.example.model.FirebasePost::class.java)
                        p?.copy(id = doc.id)
                    } catch (e: Exception) {
                        android.util.Log.e("VideoViewModel", "Failed to parse post " + doc.id, e)
                        try {
                            val likes = (doc.get("likesCount") as? Number)?.toInt() ?: 0
                            val comments = (doc.get("commentsCount") as? Number)?.toInt() ?: 0
                            com.example.model.FirebasePost(
                                id = doc.id,
                                userId = (doc.get("userId") as? String) ?: "",
                                postUrl = (doc.get("postUrl") as? String) ?: (doc.get("fileUrl") as? String) ?: "",
                                caption = (doc.get("caption") as? String) ?: "",
                                likesCount = likes,
                                commentsCount = comments
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                pageDocs[pageIndex] = parsedPageDocs
                val postsList = mergePaginatedPosts(pageDocs)

                feedProcessJob?.cancel()
                feedProcessJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    if (_videos.value.isEmpty()) {
                        _isLoading.value = true
                    }
                    try {
                            val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
                            val followingSet = _followingUserIds.value
                            val posts = postsList
                                .distinctBy { it.id }
                                .filter { post ->
                                    if (post.isDeleted || post.isArchived || post.userId.isBlank() || post.userId == "anonymous_user") return@filter false
                                    val vis = post.visibility.uppercase().ifBlank { "PUBLIC" }
                                    val isAuthorized = when (vis) {
                                        "PRIVATE" -> currentUserId.isNotEmpty() && post.userId == currentUserId
                                        "FOLLOWERS_ONLY" -> currentUserId.isNotEmpty() && (post.userId == currentUserId || followingSet.contains(post.userId))
                                        else -> true
                                    }
                                    if (!isAuthorized) return@filter false

                                    val isExclusiveOrPaid = post.isExclusive || post.isPaidPost || post.hasVaultMedia
                                    val rawUrl = post.postUrl.ifEmpty { post.postUrls.firstOrNull() ?: "" }
                                        .ifEmpty { post.blurredThumbnailUrl }
                                        .ifEmpty { post.thumbnailUrl ?: "" }
                                    if (!isExclusiveOrPaid && rawUrl.isBlank()) return@filter false
                                    if (rawUrl.isNotBlank() && com.example.utils.MediaResolver.isDummyOrSampleUrl(rawUrl)) return@filter false
                                    if (post.postUrls.any { com.example.utils.MediaResolver.isDummyOrSampleUrl(it) }) return@filter false

                                    if (isExclusiveOrPaid) return@filter true

                                    val isPostImage = when {
                                        post.mediaType.equals("video", ignoreCase = true) || post.mediaType.equals("reel", ignoreCase = true) -> false
                                        post.mediaType.equals("image", ignoreCase = true) || post.postUrls.size > 1 -> true
                                        else -> com.example.utils.MediaResolver.isImage(rawUrl)
                                    }
                                    if (isPostImage) {
                                        com.example.utils.MediaResolver.resolveImageUrl(rawUrl).isNotBlank()
                                    } else {
                                        com.example.utils.MediaResolver.resolveVideoUrl(rawUrl).isNotBlank()
                                    }
                                }
                                .sortedWith(
                                    compareByDescending<com.example.model.FirebasePost> {
                                        it.timestamp?.time?.takeIf { t -> t > 0L } ?: it.createdAtMillis
                                    }.thenByDescending { it.id }
                                )
                            
                            // Step 1: Instant emission to eliminate UI freeze
                            val fastVideoList = posts.map { post ->
                                val uId = if (post.userId.isNullOrEmpty()) "unknown" else post.userId
                                val currentUserRealtime = com.example.model.RepositoryManager.currentUserState.value
                                val activeAcc = com.example.auth.MultiAccountManager.activeAccount.value
                                val postAuthorName = post.username?.takeIf { it.isNotBlank() }
                                    ?: (if (post.userId == currentUserId) currentUserRealtime?.username ?: activeAcc?.username ?: activeAcc?.displayName else null)
                                    ?: (if (post.userId.isNotBlank()) "user_" + post.userId.take(5) else "User")
                                val postAuthorAvatar = post.userAvatarUrl?.takeIf { it.isNotBlank() }
                                    ?: post.userProfilePic?.takeIf { it.isNotBlank() }
                                    ?: (if (post.userId == currentUserId) currentUserRealtime?.avatarUrl ?: activeAcc?.avatarUrl else null)
                                    ?: ""
                                val cachedUser = userCache[uId] ?: User(
                                    id = post.userId,
                                    username = postAuthorName,
                                    avatarUrl = postAuthorAvatar,
                                    isVerified = post.isUserVerified,
                                    verificationBadgeUrl = post.userVerificationBadgeUrl ?: ""
                                )
                                val isLiked = if (currentUserId.isNotEmpty()) {
                                    com.example.model.RepositoryManager.isPostLikedCached(post.id) || post.likedByUserIds.contains(currentUserId) || post.likes.containsKey(currentUserId)
                                } else false
                                val isSaved = if (currentUserId.isNotEmpty()) com.example.model.RepositoryManager.isPostSavedCached(post.id) else false
                                val effectiveMediaUrl = post.postUrl.ifEmpty { post.postUrls.firstOrNull() ?: "" }
                                val isPostImage = when {
                                    post.mediaType.equals("video", ignoreCase = true) || post.mediaType.equals("reel", ignoreCase = true) -> false
                                    post.mediaType.equals("image", ignoreCase = true) || post.postUrls.size > 1 -> true
                                    else -> com.example.utils.MediaResolver.isImage(effectiveMediaUrl)
                                }
                                val effectiveThumb = if (isPostImage) {
                                    com.example.utils.MediaResolver.resolveImageUrl((post.thumbnailUrl ?: "").ifEmpty { effectiveMediaUrl }).ifBlank { effectiveMediaUrl }
                                } else {
                                    com.example.utils.MediaResolver.resolveVideoPosterUrl(effectiveMediaUrl, post.thumbnailUrl)
                                }
                                val finalMediaType = if (isPostImage) "image" else "video"
                                val postTime = post.timestamp?.time?.takeIf { it > 0L } ?: post.createdAtMillis

                                Video(
                                    id = post.id,
                                    userId = post.userId,
                                    user = cachedUser,
                                    collaborators = emptyList(),
                                    collaboratorIds = post.collaboratorIds,
                                    pendingCollaboratorIds = post.pendingCollaboratorIds,
                                    videoUrl = effectiveMediaUrl,
                                    thumbnailUrl = effectiveThumb,
                                    mediaType = finalMediaType,
                                    description = post.caption,
                                    likesCount = post.likesCount,
                                    viewsCount = post.viewsCount,
                                    commentsCount = post.commentsCount,
                                    savesCount = post.savesCount,
                                    reportsCount = post.reportsCount,
                                    sharesCount = post.sharesCount,
                                    repostsCount = post.repostsCount,
                                    musicTrack = post.musicTrack ?: "Original Sound",
                                    musicTrackUrl = post.musicTrackUrl,
                                    musicTrackImageUrl = post.musicTrackImageUrl,
                                    musicTrackId = post.musicTrackId,
                                    musicTrackArtist = post.musicTrackArtist,
                                    isLiked = isLiked,
                                    isSaved = isSaved,
                                    videoUrls = post.postUrls,
                                    isPremium = post.isSubscribersOnly || post.isPaidPost,
                                    premiumPrice = if (post.isPaidPost && post.postUnlockPrice > 0) post.postUnlockPrice else post.premiumPrice,
                                    isPaidPost = post.isPaidPost,
                                    postUnlockPrice = post.postUnlockPrice,
                                    accessType = post.accessType,
                                    visibility = post.visibility.ifBlank { "PUBLIC" },
                                    isExclusiveContent = post.isExclusive || post.isPaidPost,
                                    blurredThumbnailUrl = post.blurredThumbnailUrl,
                                    teaserCaption = post.teaserCaption,
                                    hasVaultMedia = post.hasVaultMedia,
                                    unlockCount = post.unlockCount,
                                    exclusiveRevenue = post.exclusiveRevenue,
                                    timestamp = postTime,
                                    isTrialReel = post.isTrialReel,
                                    trialAudience = post.trialAudience,
                                    addYoursPromptId = post.addYoursPromptId,
                                    addYoursPromptTitle = post.addYoursPromptTitle,
                                    videoWidth = post.videoWidth,
                                    videoHeight = post.videoHeight,
                                    aspectRatio = post.aspectRatio
                                )
                            }
                            // Step 1: Instant real-time emission so changes reflect in result-time immediately
                            ensureActive()
                            val fastList = fastVideoList.distinctBy { it.id }
                            _videos.value = fastList
                            _isLoading.value = false
                            _isRefreshing.value = false

                            // Step 2: Batch-fetch all missing unique user profiles in chunks of 10 (eliminates N+1 queries)
                            val missingUserIds = buildSet {
                                for (post in posts) {
                                    if (post.userId.isNotBlank() && !userCache.containsKey(post.userId)) {
                                        add(post.userId)
                                    }
                                    val reposterId = post.repostedByUserIds.firstOrNull { it == currentUserId || followingSet.contains(it) }
                                    if (!reposterId.isNullOrBlank() && !userCache.containsKey(reposterId)) {
                                        add(reposterId)
                                    }
                                    for (cId in post.collaboratorIds.take(10)) {
                                        if (cId.isNotBlank() && !userCache.containsKey(cId)) {
                                            add(cId)
                                        }
                                    }
                                }
                            }.toList()

                            if (missingUserIds.isNotEmpty()) {
                                coroutineScope {
                                    missingUserIds.chunked(10).map { chunk ->
                                        async(kotlinx.coroutines.Dispatchers.IO) {
                                            try {
                                                val snap = kotlinx.coroutines.withTimeoutOrNull(2500) {
                                                    com.example.model.RepositoryManager.firestore.collection("users")
                                                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                                                        .get()
                                                        .await()
                                                }
                                                snap?.documents?.forEach { userDoc ->
                                                    val uid = userDoc.id
                                                    val uname = userDoc.getString("username")
                                                        ?: userDoc.getString("name")
                                                        ?: userDoc.getString("displayName")
                                                        ?: userDoc.getString("email")?.substringBefore("@")
                                                        ?: "user_${uid.take(5)}"
                                                    val avatar = userDoc.getString("profilePicUrl")
                                                        ?: userDoc.getString("avatarUrl")
                                                        ?: userDoc.getString("avatar_url")
                                                        ?: ""
                                                    val vStatus = userDoc.getString("verificationStatus") ?: ""
                                                    val vSuspended = userDoc.getBoolean("verificationSuspended") ?: false
                                                    val verif = !vSuspended && (
                                                        (userDoc.getBoolean("isVerified") ?: userDoc.getBoolean("verified") ?: false) ||
                                                        vStatus == "approved" ||
                                                        vStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE
                                                    )
                                                    val badge = userDoc.getString("verificationBadgeUrl") ?: ""
                                                    userCache[uid] = User(
                                                        id = uid,
                                                        username = uname,
                                                        avatarUrl = avatar,
                                                        isVerified = verif,
                                                        verificationBadgeUrl = badge
                                                    )
                                                }
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (_: Exception) {}
                                        }
                                    }.awaitAll()
                                }
                            }

                            val currentUserRealtime = com.example.model.RepositoryManager.currentUserState.value
                            val activeAcc = com.example.auth.MultiAccountManager.activeAccount.value

                            val videoList = posts.map { post ->
                                val reposterId = post.repostedByUserIds.firstOrNull { it == currentUserId || followingSet.contains(it) }
                                val isReposted = reposterId != null
                                var repostThought: String? = null
                                var reposterUsername: String? = null
                                var reposterAvatarUrl: String? = null
                                if (reposterId != null && reposterId.isNotEmpty()) {
                                    repostThought = post.repostThoughts[reposterId] ?: "❤️"
                                    val cachedReposter = userCache[reposterId]
                                    reposterUsername = cachedReposter?.username ?: "User_${reposterId.take(5)}"
                                    reposterAvatarUrl = cachedReposter?.avatarUrl?.takeIf { it.isNotBlank() } ?: ""
                                }
                                val uId = if (post.userId.isNullOrEmpty()) "unknown" else post.userId
                                val cachedUser = userCache[uId]
                                val username = cachedUser?.username
                                    ?: (if (uId == currentUserId) currentUserRealtime?.username ?: activeAcc?.username ?: activeAcc?.displayName else null)
                                    ?: post.username?.takeIf { it.isNotBlank() }
                                    ?: (if (post.userId.isNotBlank()) "user_" + post.userId.take(5) else "User")
                                val avatarUrl = cachedUser?.avatarUrl?.takeIf { it.isNotBlank() }
                                    ?: (if (uId == currentUserId) currentUserRealtime?.avatarUrl ?: activeAcc?.avatarUrl else null)
                                    ?: post.userAvatarUrl?.takeIf { it.isNotBlank() }
                                    ?: ""
                                val isVerified = cachedUser?.isVerified ?: post.isUserVerified
                                val verificationBadgeUrl = cachedUser?.verificationBadgeUrl?.ifEmpty { post.userVerificationBadgeUrl ?: "" }
                                    ?: (post.userVerificationBadgeUrl ?: "")

                                val isLiked = if (currentUserId.isNotEmpty()) {
                                    if (com.example.model.RepositoryManager.hasPostLikedCached(post.id)) {
                                        com.example.model.RepositoryManager.isPostLikedCached(post.id)
                                    } else {
                                        post.likedByUserIds.contains(currentUserId) || post.likes.containsKey(currentUserId)
                                    }
                                } else {
                                    false
                                }

                                val isSaved = if (currentUserId.isNotEmpty()) {
                                    com.example.model.RepositoryManager.isPostSavedCached(post.id)
                                } else {
                                    false
                                }

                                val collaborators = post.collaboratorIds.take(10).mapNotNull { cid -> userCache[cid] }
                                val effectiveMediaUrl = post.postUrl.ifEmpty { post.postUrls.firstOrNull() ?: "" }
                                val isPostImage = when {
                                    post.mediaType.equals("video", ignoreCase = true) || post.mediaType.equals("reel", ignoreCase = true) -> false
                                    post.mediaType.equals("image", ignoreCase = true) || post.postUrls.size > 1 -> true
                                    else -> com.example.utils.MediaResolver.isImage(effectiveMediaUrl)
                                }
                                val effectiveThumb = if (isPostImage) {
                                    com.example.utils.MediaResolver.resolveImageUrl((post.thumbnailUrl ?: "").ifEmpty { effectiveMediaUrl }).ifBlank { effectiveMediaUrl }
                                } else {
                                    com.example.utils.MediaResolver.resolveVideoPosterUrl(effectiveMediaUrl, post.thumbnailUrl)
                                }
                                val finalMediaType = if (isPostImage) "image" else "video"
                                val postTime = post.timestamp?.time?.takeIf { it > 0L } ?: post.createdAtMillis
                                Video(
                                    id = post.id,
                                    userId = post.userId,
                                    user = User(
                                        id = post.userId,
                                        username = username,
                                        avatarUrl = avatarUrl,
                                        isVerified = isVerified,
                                        verificationBadgeUrl = verificationBadgeUrl
                                    ),
                                    collaborators = collaborators,
                                    collaboratorIds = post.collaboratorIds,
                                    pendingCollaboratorIds = post.pendingCollaboratorIds,
                                    videoUrl = effectiveMediaUrl,
                                    thumbnailUrl = effectiveThumb,
                                    mediaType = finalMediaType,
                                    description = post.caption,
                                    likesCount = post.likesCount,
                                    viewsCount = post.viewsCount,
                                    commentsCount = post.commentsCount,
                                    savesCount = post.savesCount,
                                    reportsCount = post.reportsCount,
                                    sharesCount = post.sharesCount,
                                    repostsCount = post.repostsCount,
                                    musicTrack = post.musicTrack ?: "Original Sound",
                                    musicTrackUrl = post.musicTrackUrl,
                                    musicTrackImageUrl = post.musicTrackImageUrl,
                                    musicTrackId = post.musicTrackId,
                                    musicTrackArtist = post.musicTrackArtist,
                                    isLiked = isLiked,
                                    isSaved = isSaved,
                                    isReposted = isReposted,
                                    repostThought = repostThought,
                                    reposterUsername = reposterUsername,
                                    reposterAvatarUrl = reposterAvatarUrl,
                                    videoUrls = post.postUrls,
                                    isPremium = post.isSubscribersOnly || post.isPaidPost,
                                    premiumPrice = if (post.isPaidPost && post.postUnlockPrice > 0) post.postUnlockPrice else post.premiumPrice,
                                    isPaidPost = post.isPaidPost,
                                    postUnlockPrice = post.postUnlockPrice,
                                    accessType = post.accessType,
                                    visibility = post.visibility.ifBlank { "PUBLIC" },
                                    isExclusiveContent = post.isExclusive || post.isPaidPost,
                                    blurredThumbnailUrl = post.blurredThumbnailUrl,
                                    teaserCaption = post.teaserCaption,
                                    hasVaultMedia = post.hasVaultMedia,
                                    unlockCount = post.unlockCount,
                                    exclusiveRevenue = post.exclusiveRevenue,
                                    timestamp = postTime,
                                    isTrialReel = post.isTrialReel,
                                    trialAudience = post.trialAudience,
                                    addYoursPromptId = post.addYoursPromptId,
                                    addYoursPromptTitle = post.addYoursPromptTitle,
                                    videoWidth = post.videoWidth,
                                    videoHeight = post.videoHeight,
                                    aspectRatio = post.aspectRatio
                                )
                            }

                            
            ensureActive()
            val uniqueVideos = videoList.distinctBy { it.id }
            _videos.value = uniqueVideos
            com.example.model.RepositoryManager.setCachedFeedVideos(uniqueVideos)
            _isLoading.value = false
            _isRefreshing.value = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _isLoading.value = false
            _isRefreshing.value = false
            Log.e("VideoViewModel", "Error loading videos", e)
        }
        } // end launch
            } // end if snapshot != null
        } // end snapshot listener
        pageListeners.add(registration)
    } // end attachPageListener

    fun toggleLike(videoId: String) {
        val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
        if (currentUserId.isEmpty()) return
        if (!com.example.security.InputGuard.allowAction("vm_like:$videoId", 350L)) return

        viewModelScope.launch {
            val video = _videos.value.find { it.id == videoId } ?: return@launch
            val isLiked = video.isLiked
            val prevCount = video.likesCount
            val nextLiked = !isLiked
            val nextCount = if (isLiked) (prevCount - 1).coerceAtLeast(0) else prevCount + 1
            
            // Optimistic UI update
            com.example.model.RepositoryManager.setPostLikedCached(videoId, nextLiked)
            _videos.value = _videos.value.map {
                if (it.id == videoId) {
                    it.copy(
                        isLiked = nextLiked,
                        likesCount = nextCount
                    )
                } else it
            }
            com.example.model.RepositoryManager.setCachedFeedVideos(_videos.value)

            try {
                com.example.model.RepositoryManager.socialRepository.toggleVideoLike(videoId, currentUserId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("VideoViewModel", "Error toggling like, rolling back", e)
                // Revert on failure
                com.example.model.RepositoryManager.setPostLikedCached(videoId, isLiked)
                _videos.value = _videos.value.map {
                    if (it.id == videoId) {
                        it.copy(
                            isLiked = isLiked,
                            likesCount = prevCount
                        )
                    } else it
                }
                com.example.model.RepositoryManager.setCachedFeedVideos(_videos.value)
            }
        }
    }

    fun toggleSave(videoId: String) {
        val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
        if (currentUserId.isEmpty()) return
        if (!com.example.security.InputGuard.allowAction("vm_save:$videoId", 350L)) return

        viewModelScope.launch {
            val video = _videos.value.find { it.id == videoId } ?: return@launch
            val isSaved = video.isSaved
            val prevSaves = video.savesCount
            val nextSaved = !isSaved
            val nextSaves = if (isSaved) (prevSaves - 1).coerceAtLeast(0) else prevSaves + 1
            
            // Optimistic UI update
            com.example.model.RepositoryManager.setPostSavedCached(videoId, nextSaved)
            _videos.value = _videos.value.map {
                if (it.id == videoId) {
                    it.copy(
                        isSaved = nextSaved,
                        savesCount = nextSaves
                    )
                } else it
            }
            com.example.model.RepositoryManager.setCachedFeedVideos(_videos.value)

            try {
                val newSavedState = com.example.model.RepositoryManager.socialRepository.toggleSave(currentUserId, videoId)
                _videos.value = _videos.value.map {
                    if (it.id == videoId) {
                        it.copy(isSaved = newSavedState)
                    } else it
                }
                com.example.model.RepositoryManager.setCachedFeedVideos(_videos.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("VideoViewModel", "Error toggling save, rolling back", e)
                // Revert on failure
                com.example.model.RepositoryManager.setPostSavedCached(videoId, isSaved)
                _videos.value = _videos.value.map {
                    if (it.id == videoId) {
                        it.copy(
                            isSaved = isSaved,
                            savesCount = prevSaves
                        )
                    } else it
                }
                com.example.model.RepositoryManager.setCachedFeedVideos(_videos.value)
            }
        }
    }

    fun ensureVideoInFeed(videoId: String, onReady: (Int) -> Unit) {
        val currentIndex = _videos.value.indexOfFirst { it.id == videoId }
        if (currentIndex != -1) {
            onReady(currentIndex)
            return
        }
        viewModelScope.launch {
            try {
                val doc = com.example.model.RepositoryManager.firestore.collection("posts").document(videoId).get().await()
                if (doc.exists()) {
                    val p = doc.toObject(com.example.model.FirebasePost::class.java)?.copy(id = doc.id)
                    if (p != null) {
                        val uId = if (p.userId.isNullOrEmpty()) "unknown" else p.userId
                        val userDoc = com.example.model.RepositoryManager.firestore.collection("users").document(uId).get().await()
                        val u = com.example.model.User(
                            id = uId,
                            username = userDoc.getString("username") ?: "Creator",
                            avatarUrl = userDoc.getString("profilePicUrl") ?: userDoc.getString("avatarUrl") ?: ""
                        )
                        val newVideo = com.example.model.Video(
                            id = p.id,
                            userId = p.userId,
                            user = u,
                            videoUrl = p.postUrl.ifEmpty { p.postUrls.firstOrNull() ?: "" },
                            description = p.caption,
                            likesCount = p.likesCount,
                            viewsCount = p.viewsCount,
                            commentsCount = p.commentsCount,
                            savesCount = p.savesCount,
                            sharesCount = p.sharesCount,
                            repostsCount = p.repostsCount,
                            musicTrack = p.musicTrack ?: "Original Sound",
                            musicTrackUrl = p.musicTrackUrl,
                            musicTrackImageUrl = p.musicTrackImageUrl,
                            musicTrackId = p.musicTrackId,
                            musicTrackArtist = p.musicTrackArtist,
                            videoUrls = p.postUrls,
                            isPremium = p.isSubscribersOnly || p.isPaidPost,
                            premiumPrice = if (p.isPaidPost && p.postUnlockPrice > 0) p.postUnlockPrice else p.premiumPrice,
                            isPaidPost = p.isPaidPost,
                            postUnlockPrice = p.postUnlockPrice,
                            accessType = p.accessType,
                            isExclusiveContent = p.isExclusive || p.isPaidPost,
                            blurredThumbnailUrl = p.blurredThumbnailUrl,
                            teaserCaption = p.teaserCaption,
                            hasVaultMedia = p.hasVaultMedia,
                            unlockCount = p.unlockCount,
                            exclusiveRevenue = p.exclusiveRevenue,
                            timestamp = p.timestamp?.time ?: 0L
                        )
                        val existing = _videos.value.filter { it.id != newVideo.id }
                        _videos.value = listOf(newVideo) + existing
                        onReady(0)
                        return@launch
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("VideoViewModel", "Failed to fetch specific video $videoId", e)
            }
            onReady(0)
        }
    }

    fun markNotInterested(videoId: String, creatorId: String) {
        _notInterestedVideoIds.value = _notInterestedVideoIds.value + videoId
        val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
        if (currentUserId.isNotEmpty()) {
            viewModelScope.launch {
                com.example.model.RepositoryManager.reelsActionsRepository.markNotInterested(currentUserId, videoId, creatorId)
            }
        }
    }

    fun undoNotInterested(videoId: String) {
        _notInterestedVideoIds.value = _notInterestedVideoIds.value - videoId
        val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
        if (currentUserId.isNotEmpty()) {
            viewModelScope.launch {
                com.example.model.RepositoryManager.reelsActionsRepository.undoNotInterested(currentUserId, videoId)
            }
        }
    }

    fun hideCreator(creatorId: String, username: String) {
        _hiddenCreatorIds.value = _hiddenCreatorIds.value + creatorId
        val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
        if (currentUserId.isNotEmpty()) {
            viewModelScope.launch {
                com.example.model.RepositoryManager.reelsActionsRepository.hideCreator(currentUserId, creatorId, username)
            }
        }
    }

    fun undoHideCreator(creatorId: String) {
        _hiddenCreatorIds.value = _hiddenCreatorIds.value - creatorId
        val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
        if (currentUserId.isNotEmpty()) {
            viewModelScope.launch {
                com.example.model.RepositoryManager.reelsActionsRepository.undoHideCreator(currentUserId, creatorId)
            }
        }
    }

    fun deleteVideoLocally(videoId: String) {
        _videos.value = _videos.value.filter { it.id != videoId }
    }

    fun updateVideoCaptionLocally(videoId: String, newCaption: String) {
        _videos.value = _videos.value.map { video ->
            if (video.id == videoId) video.copy(description = newCaption) else video
        }
    }

    override fun onCleared() {
        super.onCleared()
        feedProcessJob?.cancel()
        feedProcessJob = null
        followingObserverJob?.cancel()
        followingObserverJob = null
        clearPageListeners()
        storiesSnapshotListener?.remove()
        storiesSnapshotListener = null
    }

    companion object {
        const val INITIAL_FEED_LIMIT = 10
        const val PAGE_SIZE = 10
        const val MAX_FEED_LIMIT = 250
        var selectedVideoIdToPlay: String? = null

        fun mergePaginatedPosts(pages: Map<Int, List<com.example.model.FirebasePost>>): List<com.example.model.FirebasePost> {
            return pages.toSortedMap().values.flatten()
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<com.example.model.FirebasePost> {
                        it.timestamp?.time?.takeIf { t -> t > 0L } ?: it.createdAtMillis
                    }.thenByDescending { it.id }
                )
        }
    }
}
