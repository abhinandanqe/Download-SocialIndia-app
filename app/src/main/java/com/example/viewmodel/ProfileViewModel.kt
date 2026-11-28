package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.FirebasePost
import com.example.model.RepositoryManager
import com.example.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.Query

class ProfileViewModel : ViewModel() {
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _profileUser = MutableStateFlow<User?>(null)
    val profileUser: StateFlow<User?> = _profileUser

    private val _userPosts = MutableStateFlow<List<FirebasePost>>(emptyList())
    val userPosts: StateFlow<List<FirebasePost>> = _userPosts
    
    private val _likedPosts = MutableStateFlow<List<FirebasePost>>(emptyList())
    val likedPosts: StateFlow<List<FirebasePost>> = _likedPosts
    
    private val _reportedPosts = MutableStateFlow<List<FirebasePost>>(emptyList())
    val reportedPosts: StateFlow<List<FirebasePost>> = _reportedPosts
    
    private val _collabPosts = MutableStateFlow<List<FirebasePost>>(emptyList())
    val collabPosts: StateFlow<List<FirebasePost>> = _collabPosts

    private val _repostedPosts = MutableStateFlow<List<FirebasePost>>(emptyList())
    val repostedPosts: StateFlow<List<FirebasePost>> = _repostedPosts

    private val _isLoadingProfile = MutableStateFlow(false)
    val isLoadingProfile: StateFlow<Boolean> = _isLoadingProfile

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _hasMorePosts = MutableStateFlow(true)
    val hasMorePosts: StateFlow<Boolean> = _hasMorePosts

    private val _profileError = MutableStateFlow<String?>(null)
    val profileError: StateFlow<String?> = _profileError

    private val _userNotFound = MutableStateFlow(false)
    val userNotFound: StateFlow<Boolean> = _userNotFound

    private var lastVisiblePostDoc: com.google.firebase.firestore.DocumentSnapshot? = null

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing

    private var profileLoadJob: kotlinx.coroutines.Job? = null
    private var followStatusJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            com.example.auth.MultiAccountManager.activeAccount
                .drop(1)
                .collect {
                    val currentUserId = RepositoryManager.getCurrentUserId()
                    if (currentUserId.isNotEmpty()) {
                        loadProfile(null)
                    }
                }
        }
        viewModelScope.launch {
            com.example.ui.GlobalState.homeFeedRefreshTrigger.collect { trigger ->
                if (trigger > 0L) {
                    val activeProfileId = _profileUser.value?.id?.takeIf { it.isNotEmpty() }
                    loadProfile(activeProfileId, isRefresh = true)
                }
            }
        }
    }

    fun refreshProfile(userId: String?) {
        loadProfile(userId, isRefresh = true)
    }

    fun loadProfile(userId: String?, isRefresh: Boolean = false) {
        profileLoadJob?.cancel()
        profileLoadJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val currentUserId = RepositoryManager.getCurrentUserId()
            val targetId = (userId ?: currentUserId).trim()
            val isSwitchingUser = _profileUser.value?.id != null && _profileUser.value?.id != targetId
            if (isSwitchingUser) {
                _profileUser.value = null
                _userPosts.value = emptyList()
                _collabPosts.value = emptyList()
                _likedPosts.value = emptyList()
                _repostedPosts.value = emptyList()
            }

            _profileError.value = null
            _userNotFound.value = false

            if (isRefresh) {
                _isRefreshing.value = true
            } else if (_profileUser.value == null || _userPosts.value.isEmpty()) {
                _isLoadingProfile.value = true
            }

            try {
                if (targetId.isEmpty()) {
                    _profileUser.value = null
                    _userNotFound.value = userId != null
                    _isLoadingProfile.value = false
                    _isRefreshing.value = false
                    return@launch
                }

                // Instant hydration from in-memory cache for zero-latency tab switches
                com.example.ui.GlobalState.getCachedProfileUser(targetId)?.let { cachedUser ->
                    if (_profileUser.value == null || _profileUser.value?.id != targetId) {
                        _profileUser.value = cachedUser
                    }
                }
                com.example.ui.GlobalState.getCachedProfilePosts(targetId)?.let { cachedPosts ->
                    if (cachedPosts.isNotEmpty()) {
                        _userPosts.value = cachedPosts
                        _isLoadingProfile.value = false
                    }
                }
                
                // Cancel previous follow status observer to prevent listener leaks across profile visits
                followStatusJob?.cancel()
                if (currentUserId.isNotEmpty() && targetId != currentUserId) {
                    _isFollowing.value = RepositoryManager.isFollowingCached(targetId)
                    followStatusJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        RepositoryManager.socialRepository.isFollowing(currentUserId, targetId).collect { following ->
                            _isFollowing.value = following
                        }
                    }
                }
                
                userProfileListener?.remove()
                try {
                    userProfileListener = RepositoryManager.firestore.collection("users").document(targetId)
                        .addSnapshotListener { doc, err ->
                            if (err != null) {
                                if (_profileUser.value == null) {
                                    _profileError.value = err.localizedMessage ?: "Unable to load profile"
                                }
                                _isLoadingProfile.value = false
                                _isRefreshing.value = false
                                return@addSnapshotListener
                            }
                            if (doc == null || !doc.exists()) {
                                _profileUser.value = null
                                _userNotFound.value = true
                                _isLoadingProfile.value = false
                                _isRefreshing.value = false
                                return@addSnapshotListener
                            }
                            try {
                                fun str(key: String): String = (doc.get(key) as? String) ?: doc.get(key)?.toString() ?: ""
                                fun bool(key: String): Boolean = when (val v = doc.get(key)) {
                                    is Boolean -> v
                                    is String -> v.equals("true", ignoreCase = true)
                                    is Number -> v.toInt() != 0
                                    else -> false
                                }
                                fun longVal(key: String): Long = when (val v = doc.get(key)) {
                                    is Number -> v.toLong()
                                    is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong() ?: 0L
                                    else -> 0L
                                }
                                fun doubleVal(key: String): Double = when (val v = doc.get(key)) {
                                    is Number -> v.toDouble()
                                    is String -> v.toDoubleOrNull() ?: 0.0
                                    else -> 0.0
                                }

                                val vStatus = str("verificationStatus")
                                val vSuspended = bool("verificationSuspended")
                                val isUserVerified = !vSuspended && (
                                    bool("isVerified") ||
                                    vStatus == "approved" ||
                                    vStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE
                                )
                                val user = User(
                                    id = doc.id,
                                    username = str("username"),
                                    displayName = str("displayName").ifEmpty { str("fullName") },
                                    bio = str("bio"),
                                    avatarUrl = str("avatarUrl").ifEmpty { str("profilePicUrl").ifEmpty { str("avatar_url") } },
                                    isVerified = isUserVerified,
                                    verificationStatus = vStatus,
                                    verificationBadgeUrl = str("verificationBadgeUrl"),
                                    verificationType = str("verificationType"),
                                    verificationExpiryTimestamp = longVal("verificationExpiryTimestamp"),
                                    verificationSuspended = vSuspended,
                                    followerCount = longVal("followerCount").toInt(),
                                    followingCount = longVal("followingCount").toInt(),
                                    coins = longVal("coins"),
                                    earnings = doubleVal("earnings"),
                                    walletBalance = doubleVal("walletBalance"),
                                    profileMusicTitle = str("profileMusicTitle"),
                                    profileMusicArtist = str("profileMusicArtist"),
                                    profileMusicUrl = str("profileMusicUrl")
                                )
                                _userNotFound.value = false
                                _profileError.value = null
                                _profileUser.value = user
                                com.example.ui.GlobalState.cacheProfileUser(targetId, user)
                                _isLoadingProfile.value = false
                            } catch (e: Exception) {
                                android.util.Log.e("ProfileViewModel", "Safe profile snapshot parse failed for $targetId", e)
                            }
                        }
                } catch (e: Exception) {
                    android.util.Log.e("ProfileViewModel", "Failed to attach userProfileListener", e)
                }

                // Single real-time listener for user posts (eliminates duplicate .get() read)
                userPostsListener?.remove()
                try {
                    userPostsListener = RepositoryManager.firestore.collection("posts")
                        .whereEqualTo("userId", targetId)
                        .limit(60)
                        .addSnapshotListener { snap, err ->
                            if (err != null || snap == null) {
                                _isLoadingProfile.value = false
                                _isRefreshing.value = false
                                return@addSnapshotListener
                            }
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                try {
                                    val parsed = snap.documents.mapNotNull { doc ->
                                        parsePostDocument(doc, targetId)
                                    }
                                    val viewerId = RepositoryManager.getCurrentUserId()
                                    val filtered = parsed
                                        .distinctBy { it.id }
                                        .filter { post ->
                                            if (post.isDeleted || post.isArchived) return@filter false
                                            if (post.postUrl.isBlank() || com.example.utils.MediaResolver.isDummyOrSampleUrl(post.postUrl)) return@filter false
                                            val vis = post.visibility.uppercase().ifBlank { "PUBLIC" }
                                            when (vis) {
                                                "PRIVATE" -> viewerId.isNotEmpty() && post.userId == viewerId
                                                "FOLLOWERS_ONLY" -> viewerId.isNotEmpty() && (post.userId == viewerId || _isFollowing.value)
                                                else -> true
                                            }
                                        }
                                        .sortedWith(
                                            compareByDescending<FirebasePost> {
                                                it.timestamp?.time?.takeIf { t -> t > 0L } ?: it.createdAtMillis
                                            }.thenByDescending { it.id }
                                        )
                                    _userPosts.value = filtered
                                    com.example.ui.GlobalState.cacheProfilePosts(targetId, filtered)
                                    lastVisiblePostDoc = snap.documents.lastOrNull()
                                    _hasMorePosts.value = snap.documents.size >= 60
                                } catch (e: Exception) {
                                    android.util.Log.e("ProfileViewModel", "Safe posts snapshot parse failed", e)
                                } finally {
                                    _isLoadingProfile.value = false
                                    _isRefreshing.value = false
                                }
                            }
                        }
                } catch (e: Exception) {
                    android.util.Log.e("ProfileViewModel", "Failed to attach userPostsListener", e)
                    _isLoadingProfile.value = false
                    _isRefreshing.value = false
                }

                // Fetch Collabs, Liked, and Reposted tabs IN PARALLEL on Dispatchers.IO (3x faster!)
                kotlinx.coroutines.coroutineScope {
                    launch {
                        try {
                            val collabsSnap = RepositoryManager.firestore.collection("posts")
                                .whereArrayContains("collaboratorIds", targetId)
                                .limit(30)
                                .get().await()
                            val collabs = collabsSnap.documents.mapNotNull { doc ->
                                parsePostDocument(doc, targetId)
                            }
                            _collabPosts.value = collabs
                                .filter { it.postUrl.isNotBlank() && !com.example.utils.MediaResolver.isDummyOrSampleUrl(it.postUrl) }
                                .sortedByDescending { it.timestamp?.time ?: it.createdAtMillis }
                        } catch (_: Exception) {}
                    }
                    launch {
                        try {
                            val likedSnap = RepositoryManager.firestore.collection("posts")
                                .whereArrayContains("likedByUserIds", targetId)
                                .limit(30)
                                .get().await()
                            _likedPosts.value = likedSnap.documents.mapNotNull { doc ->
                                parsePostDocument(doc, targetId)
                            }.filter { it.postUrl.isNotBlank() && !com.example.utils.MediaResolver.isDummyOrSampleUrl(it.postUrl) }
                        } catch (_: Exception) {
                            _likedPosts.value = emptyList()
                        }
                    }
                    launch {
                        try {
                            val repostedSnap = RepositoryManager.firestore.collection("posts")
                                .whereArrayContains("repostedByUserIds", targetId)
                                .limit(30)
                                .get().await()
                            _repostedPosts.value = repostedSnap.documents.mapNotNull { doc ->
                                parsePostDocument(doc, targetId)
                            }.filter { it.postUrl.isNotBlank() && !com.example.utils.MediaResolver.isDummyOrSampleUrl(it.postUrl) }
                        } catch (_: Exception) {
                            _repostedPosts.value = emptyList()
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading profile", e)
                _profileError.value = e.localizedMessage ?: "Failed to load profile"
            } finally {
                _isLoadingProfile.value = false
                _isRefreshing.value = false
            }
        }
    }

    fun loadMorePosts(userId: String?) {
        if (_isLoadingMore.value || !_hasMorePosts.value) return
        val lastDoc = lastVisiblePostDoc ?: return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val currentUserId = RepositoryManager.getCurrentUserId()
                val targetId = userId ?: currentUserId
                if (targetId.isEmpty()) {
                    _isLoadingMore.value = false
                    return@launch
                }

                val nextSnap = RepositoryManager.firestore.collection("posts")
                    .whereEqualTo("userId", targetId)
                    .startAfter(lastDoc)
                    .limit(10)
                    .get().await()

                if (!nextSnap.isEmpty) {
                    val newPosts = nextSnap.documents.mapNotNull { doc ->
                        try { doc.toObject(FirebasePost::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
                    }
                    val currentList = _userPosts.value
                    val combined = (currentList + newPosts).distinctBy { it.id }.sortedByDescending { it.timestamp }
                    _userPosts.value = combined
                    com.example.ui.GlobalState.cacheProfilePosts(targetId, combined)
                    lastVisiblePostDoc = nextSnap.documents.lastOrNull()
                    _hasMorePosts.value = nextSnap.documents.size >= 10
                } else {
                    _hasMorePosts.value = false
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading more posts", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun updateProfile(
        context: Context, 
        username: String, 
        fullName: String, 
        bio: String, 
        imageUri: Uri?, 
        imageBytes: ByteArray?, 
        pronouns: String = "", 
        gender: String = "", 
        isAiCreator: Boolean = false, 
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            com.example.viewmodel.UploadViewModel.globalUploadState.value = com.example.viewmodel.UploadState.Uploading
            
            try {
                val userId = com.example.model.RepositoryManager.getCurrentUserId()
                if (userId.isEmpty()) {
                    _isSaving.value = false
                    return@launch
                }
                var uploadedAvatarUrl: String? = null
                if (imageUri != null) {
                    try {
                        uploadedAvatarUrl = com.example.model.RepositoryManager.mediaRepository.uploadMedia(imageUri)
                    } catch (e: Exception) {
                        Log.e("ProfileViewModel", "Failed to upload avatar", e)
                        com.example.viewmodel.UploadViewModel.globalUploadState.value = com.example.viewmodel.UploadState.Error("Failed to upload avatar: ${e.message ?: "Network error"}")
                        delay(2000)
                        com.example.viewmodel.UploadViewModel.globalUploadState.value = com.example.viewmodel.UploadState.Idle
                        _isSaving.value = false
                        return@launch
                    }
                }
                val updateData = mutableMapOf<String, Any>(
                    "username" to username,
                    "fullName" to fullName,
                    "bio" to bio,
                    "pronouns" to pronouns,
                    "gender" to gender,
                    "isAiCreator" to isAiCreator
                )
                if (uploadedAvatarUrl != null) {
                    updateData["profilePicUrl"] = uploadedAvatarUrl
                }
                com.example.model.RepositoryManager.firestore.collection("users").document(userId)
                    .update(updateData).await()
                    
                _profileUser.value = _profileUser.value?.copy(
                    username = username,
                    bio = bio,
                    avatarUrl = uploadedAvatarUrl ?: _profileUser.value?.avatarUrl ?: ""
                )
                
                com.example.viewmodel.UploadViewModel.globalUploadState.value = com.example.viewmodel.UploadState.SuccessWithMessage("Profile Updated Successfully!")
                delay(2000)
                com.example.viewmodel.UploadViewModel.globalUploadState.value = com.example.viewmodel.UploadState.Idle
                onComplete()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("ProfileViewModel", "Error updating profile", e)
                com.example.viewmodel.UploadViewModel.globalUploadState.value = com.example.viewmodel.UploadState.Error(e.message ?: "Failed")
                delay(2000)
                com.example.viewmodel.UploadViewModel.globalUploadState.value = com.example.viewmodel.UploadState.Idle
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun toggleFollow(targetUserId: String) {
        viewModelScope.launch {
            val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
            if (currentUserId.isEmpty()) return@launch
            try {
                if (_isFollowing.value) {
                    com.example.model.RepositoryManager.socialRepository.unfollowUser(currentUserId, targetUserId)
                    _profileUser.value = _profileUser.value?.let { it.copy(followerCount = Math.max(0, it.followerCount - 1)) }
                } else {
                    com.example.model.RepositoryManager.socialRepository.followUser(currentUserId, targetUserId)
                    _profileUser.value = _profileUser.value?.let { it.copy(followerCount = it.followerCount + 1) }
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error toggling follow", e)
            }
        }
    }

    fun requestVerification() {
        viewModelScope.launch {
            try {
                val userId = com.example.model.RepositoryManager.getCurrentUserId()
                if (userId.isEmpty()) return@launch
                com.example.model.RepositoryManager.firestore.collection("users").document(userId)
                    .update("verificationRequested", true).await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error requesting verification", e)
            }
        }
    }

    private var userProfileListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var userPostsListener: com.google.firebase.firestore.ListenerRegistration? = null

    private fun parsePostDocument(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        fallbackUserId: String
    ): FirebasePost? {
        return try {
            val p = doc.toObject(FirebasePost::class.java)
            val pUrl = doc.getString("postUrl")
                ?: doc.getString("videoUrl")
                ?: doc.getString("fileUrl")
                ?: ""
            val rawThumb = doc.getString("thumbnailUrl")
                ?: doc.getString("coverUrl")
                ?: doc.getString("imageUrl")
                ?: ""
            val mType = doc.getString("mediaType").orEmpty()
            val isImg = when {
                mType.equals("video", ignoreCase = true) || mType.equals("reel", ignoreCase = true) -> false
                mType.equals("image", ignoreCase = true) -> true
                else -> com.example.utils.MediaResolver.isImage(pUrl)
            }
            val resolvedThumb = if (isImg) {
                com.example.utils.MediaResolver.resolveImageUrl(rawThumb.ifBlank { pUrl }).ifBlank { pUrl }
            } else {
                com.example.utils.MediaResolver.resolveVideoPosterUrl(pUrl, rawThumb).ifBlank { rawThumb.ifBlank { pUrl } }
            }
            val finalMediaType = if (isImg) "image" else "video"
            p?.copy(
                id = doc.id,
                postUrl = if (p.postUrl.isNotEmpty()) p.postUrl else pUrl,
                thumbnailUrl = resolvedThumb,
                mediaType = finalMediaType
            ) ?: FirebasePost(
                id = doc.id,
                userId = fallbackUserId,
                postUrl = pUrl,
                thumbnailUrl = resolvedThumb,
                mediaType = finalMediaType
            )
        } catch (e: Exception) {
            null
        }
    }

    fun instantVerifyUser(isVerified: Boolean) {
        viewModelScope.launch {
            try {
                val userId = com.example.model.RepositoryManager.getCurrentUserId()
                if (userId.isEmpty()) return@launch
                com.example.model.RepositoryManager.firestore.collection("users").document(userId)
                    .update(mapOf(
                        "isVerified" to isVerified,
                        "verificationRequested" to false,
                        "verificationBadgeUrl" to ""
                    )).await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error updating verification status", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        userProfileListener?.remove()
        userProfileListener = null
        userPostsListener?.remove()
        userPostsListener = null
    }
}
