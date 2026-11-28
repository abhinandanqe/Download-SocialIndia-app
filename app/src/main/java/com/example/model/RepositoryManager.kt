package com.example.model

import android.content.Context
import com.example.BuildConfig
import com.example.repository.AuthRepository
import com.example.repository.MediaRepository
import com.example.repository.PostRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object RepositoryManager {
    lateinit var authRepository: AuthRepository
    lateinit var socialRepository: SocialRepository
    lateinit var mediaRepository: MediaRepository
    lateinit var postRepository: PostRepository
    lateinit var giftRepository: com.example.repository.GiftRepository
    lateinit var verificationRepository: com.example.repository.VerificationRepository
    lateinit var reelsActionsRepository: com.example.repository.ReelsActionsRepository
    lateinit var stickerRepository: com.example.repository.StickerRepository
    lateinit var firestore: FirebaseFirestore
    lateinit var auth: com.google.firebase.auth.FirebaseAuth
    
    // Legacy Appwrite constants compatibility
    val DATABASE_ID = "663c631e0036127e7d41" // Replace with actual ID
    val USERS_COLLECTION_ID = "users"
    val NOTIFICATIONS_COLLECTION_ID = "notifications"
    val BUCKET_ID = "663c635600122e232420"
    val PROJECT_ID = "663c62a8001712a45d04"
    
    fun isInitialized(): Boolean = ::authRepository.isInitialized && ::firestore.isInitialized
    
    fun isLoggedIn(): Boolean = if (::authRepository.isInitialized) authRepository.isLoggedIn() else false
    fun getCurrentUserId(): String = if (::authRepository.isInitialized) authRepository.getCurrentUserId() else ""
    fun isRealUserLoggedIn(): Boolean = isLoggedIn() && getCurrentUserId().let { it.isNotBlank() && it != "anonymous_user" }

    @Volatile
    private var anonymousCleanupTriggered = false

    fun triggerAnonymousCleanupOnce() {
        if (anonymousCleanupTriggered || !isRealUserLoggedIn() || !::postRepository.isInitialized) return
        anonymousCleanupTriggered = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                postRepository.cleanupAnonymousUploads()
            } catch (_: Exception) {
                anonymousCleanupTriggered = false
            }
        }
    }

    @get:JvmName("currentUserIdProp")
    val currentUserId: String
        get() = getCurrentUserId()

    val currentUsername: String
        get() = _currentUserState.value?.username?.ifBlank { "User" } ?: "User"

    val currentUserAvatar: String
        get() = _currentUserState.value?.avatarUrl ?: ""

    // In-memory cache for followed user IDs to make UI state updates instant and flicker-free
    private val followedUserIds = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val followRequestedIds = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val dismissedUserIds = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val likedPostIds = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val savedPostIds = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    @Volatile
    private var cachedFeedVideos: List<Video> = emptyList()

    fun getCachedFeedVideos(): List<Video> = cachedFeedVideos

    fun setCachedFeedVideos(videos: List<Video>) {
        if (videos.isNotEmpty()) {
            cachedFeedVideos = videos
        }
    }

    fun setPostLikedCached(postId: String, isLiked: Boolean) {
        likedPostIds[postId] = isLiked
    }

    fun isPostLikedCached(postId: String): Boolean {
        return likedPostIds[postId] ?: false
    }

    fun hasPostLikedCached(postId: String): Boolean {
        return likedPostIds.containsKey(postId)
    }

    fun setPostSavedCached(postId: String, isSaved: Boolean) {
        savedPostIds[postId] = isSaved
    }

    fun isPostSavedCached(postId: String): Boolean {
        return savedPostIds[postId] ?: false
    }

    fun hasPostSavedCached(postId: String): Boolean {
        return savedPostIds.containsKey(postId)
    }

    fun setFollowingCached(userId: String, isFollowing: Boolean) {
        followedUserIds[userId] = isFollowing
        if (isFollowing) {
            followRequestedIds[userId] = false
        }
    }

    fun isFollowingCached(userId: String): Boolean {
        return followedUserIds[userId] ?: false
    }

    fun setFollowRequestedCached(userId: String, isRequested: Boolean) {
        followRequestedIds[userId] = isRequested
    }

    fun isFollowRequestedCached(userId: String): Boolean {
        return followRequestedIds[userId] ?: false
    }

    fun dismissUserCached(userId: String) {
        dismissedUserIds[userId] = true
    }

    fun isUserDismissedCached(userId: String): Boolean {
        return dismissedUserIds[userId] ?: false
    }

    fun clearFollowCache() {
        followedUserIds.clear()
        followRequestedIds.clear()
        dismissedUserIds.clear()
    }
    
    fun init(context: Context) {
        firestore = FirebaseFirestore.getInstance()
        try {
            val cacheSettings = com.google.firebase.firestore.PersistentCacheSettings.newBuilder()
                .setSizeBytes(100L * 1024 * 1024) // 100MB offline disk cache
                .build()
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(cacheSettings)
                .build()
            firestore.firestoreSettings = settings
        } catch (_: Exception) {
            // Firestore already started; persistence settings already active
        }
        
        auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val db = AppDatabase.getDatabase(context)
        authRepository = AuthRepository(auth, firestore)
        socialRepository = SocialRepository(context)
        val cloudinaryUrl = "cloudinary://${BuildConfig.CLOUDINARY_API_KEY}:${BuildConfig.CLOUDINARY_API_SECRET}@${BuildConfig.CLOUDINARY_CLOUD_NAME}"
        mediaRepository = MediaRepository(context)
        postRepository = PostRepository()
        giftRepository = com.example.repository.GiftRepository(firestore)
        verificationRepository = com.example.repository.VerificationRepository(firestore)
        reelsActionsRepository = com.example.repository.ReelsActionsRepository(firestore)
        stickerRepository = com.example.repository.StickerRepository(firestore)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                stickerRepository.seedInitialStickerPacksIfEmpty()
            } catch (e: Exception) {
                android.util.Log.e("RepositoryManager", "Failed to seed stickers: ${e.message}")
            }
        }
    }
    
    fun listDocuments(databaseId: String, collectionId: String): com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> = firestore.collection(collectionId).get()
    fun getDocument(databaseId: String, collectionId: String, documentId: String): com.google.android.gms.tasks.Task<com.google.firebase.firestore.DocumentSnapshot> = firestore.collection(collectionId).document(documentId).get()
    fun updateDocument(databaseId: String, collectionId: String, documentId: String, data: Map<String, Any>): com.google.android.gms.tasks.Task<Void> = firestore.collection(collectionId).document(documentId).update(data)

    
    val storage = object {
        fun createFile(bucketId: String, fileId: String, data: Any) = com.google.android.gms.tasks.Tasks.forResult<Any>(null)
    }

        // Global Real-time Current User state for immediate hydration across entire app
    private val _currentUserState = kotlinx.coroutines.flow.MutableStateFlow<User?>(null)
    val currentUserState: kotlinx.coroutines.flow.StateFlow<User?> = _currentUserState

    private var currentUserSnapshotListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun attachCurrentUserRealtimeListener(userId: String) {
        if (userId.isEmpty() || userId == "anonymous_user") return
        triggerAnonymousCleanupOnce()
        currentUserSnapshotListener?.remove()
        try {
            currentUserSnapshotListener = firestore.collection("users").document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    try {
                        fun str(key: String, default: String = ""): String =
                            (snapshot.get(key) as? String) ?: snapshot.get(key)?.toString() ?: default
                        fun bool(key: String, default: Boolean = false): Boolean = when (val v = snapshot.get(key)) {
                            is Boolean -> v
                            is String -> v.equals("true", ignoreCase = true)
                            is Number -> v.toInt() != 0
                            else -> default
                        }
                        fun longVal(key: String): Long? = when (val v = snapshot.get(key)) {
                            is Number -> v.toLong()
                            is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong()
                            else -> null
                        }
                        fun doubleVal(key: String): Double? = when (val v = snapshot.get(key)) {
                            is Number -> v.toDouble()
                            is String -> v.toDoubleOrNull()
                            else -> null
                        }

                        val rawUsername = str("username")
                        val rawName = str("name").ifEmpty { str("displayName") }
                        val finalUsername = rawUsername.ifEmpty { rawName.ifEmpty { "User" } }
                        val avatar = str("avatarUrl")
                            .ifEmpty { str("avatar_url") }
                            .ifEmpty { str("profilePicUrl") }
                        val isVerif = bool("isVerified")
                        val badgeUrl = str("verificationBadgeUrl")
                        val realCoins = longVal("coins")
                            ?: longVal("coinBalance")
                            ?: 0L
                        val realWalletBalance = doubleVal("walletBalance")
                            ?: doubleVal("earnings")
                            ?: 0.0
                        val realEarnings = doubleVal("earnings") ?: realWalletBalance

                        val userObj = User(
                            id = snapshot.id,
                            username = finalUsername,
                            displayName = rawName,
                            email = str("email"),
                            avatarUrl = avatar,
                            isVerified = isVerif,
                            verificationStatus = str("verificationStatus", "none").ifEmpty { "none" },
                            verificationBadgeUrl = badgeUrl,
                            verificationSuspended = bool("verificationSuspended"),
                            bio = str("bio"),
                            category = str("category"),
                            followerCount = (longVal("followerCount") ?: longVal("followersCount") ?: 0L).toInt(),
                            followingCount = (longVal("followingCount") ?: 0L).toInt(),
                            coins = realCoins,
                            walletBalance = realWalletBalance,
                            earnings = realEarnings,
                            isMonetized = bool("isMonetized"),
                            monetizationStatus = str("monetizationStatus", "NOT_ELIGIBLE").ifEmpty { "NOT_ELIGIBLE" },
                            monetizationRequested = bool("monetizationRequested"),
                            accountStatus = str("accountStatus", "ACTIVE").ifEmpty { "ACTIVE" },
                            isBanned = bool("isBanned"),
                            isAdmin = bool("isAdmin")
                        )
                        _currentUserState.value = userObj
                    } catch (e: Exception) {
                        android.util.Log.e("RepositoryManager", "Safe current user snapshot parse failed", e)
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("RepositoryManager", "Failed to attach currentUserSnapshotListener", e)
        }
    }

val account = object {
        fun get() = object {
            val id = getCurrentUserId()
        }
    }
}
