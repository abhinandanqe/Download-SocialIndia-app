package com.example.ui

import androidx.compose.runtime.mutableStateOf
import com.example.model.FirebasePost
import com.example.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

object GlobalState {
    var targetVideoId = mutableStateOf<String?>(null)
    var openCommentsForVideoId = mutableStateOf<String?>(null)
    var targetStoryId = mutableStateOf<String?>(null)
    var pendingDeepLinkRoute = mutableStateOf<String?>(null)
    var preselectedMusic = mutableStateOf<String?>(null)
    var preselectedMusicUrl = mutableStateOf<String?>(null)
    var preselectedMusicTrack = mutableStateOf<com.example.api.ITunesTrack?>(null)
    val isPostingStory = MutableStateFlow(false)
    val homeFeedRefreshTrigger = MutableStateFlow(0L)
    val showAccountSwitcher = MutableStateFlow(false)
    var pendingTemplateToOpen = mutableStateOf<com.example.ui.templates.ContentTemplate?>(null)
    var pendingTemplateSourcePostId = mutableStateOf<String?>(null)
    var pendingReadyTemplateSnapshot = mutableStateOf<com.example.ui.editor.EditorProjectSnapshot?>(null)
    var pendingReadyTemplateUris = mutableStateOf<List<android.net.Uri>>(emptyList())
    var pendingReadyTemplateInfo = mutableStateOf<com.example.ui.templates.ContentTemplate?>(null)

    // Scroll position persistence across tab switches and back navigation
    private val scrollPositions = ConcurrentHashMap<String, Pair<Int, Int>>()
    private val pagerPositions = ConcurrentHashMap<String, Int>()

    fun saveScroll(key: String, index: Int, offset: Int) {
        scrollPositions[key] = index.coerceAtLeast(0) to offset.coerceAtLeast(0)
    }

    fun getScroll(key: String): Pair<Int, Int> {
        return scrollPositions[key] ?: (0 to 0)
    }

    fun savePagerPage(key: String, page: Int) {
        pagerPositions[key] = page.coerceAtLeast(0)
    }

    fun getPagerPage(key: String): Int {
        return pagerPositions[key] ?: 0
    }

    // Instant tab-switch caches
    @Volatile
    var cachedExplorePosts: List<FirebasePost> = emptyList()
    @Volatile
    var cachedDiscoverPosts: List<ExplorePost> = emptyList()
    @Volatile
    var cachedDiscoverCategories: List<String> = emptyList()
    @Volatile
    var lastDiscoverFetchMs: Long = 0L
    @Volatile
    var cachedHomeSuggestions: List<com.example.model.SuggestedUser> = emptyList()
    @Volatile
    var cachedHomeAds: List<com.example.model.Advertisement> = emptyList()
    @Volatile
    var lastHomeExtrasFetchMs: Long = 0L
    @Volatile
    var cachedSearchCreators: List<User> = emptyList()
    @Volatile
    var cachedSearchTrendingTags: List<String> = emptyList()
    @Volatile
    var cachedSearchSounds: List<com.example.model.AudioSound> = emptyList()
    @Volatile
    var lastSearchFetchMs: Long = 0L
    private val cachedProfiles = ConcurrentHashMap<String, User>()
    private val cachedProfilePosts = ConcurrentHashMap<String, List<FirebasePost>>()

    fun cacheProfileUser(userId: String, user: User) {
        if (userId.isNotBlank()) cachedProfiles[userId] = user
    }

    fun getCachedProfileUser(userId: String): User? = cachedProfiles[userId]

    fun cacheProfilePosts(userId: String, posts: List<FirebasePost>) {
        if (userId.isNotBlank()) cachedProfilePosts[userId] = posts
    }

    fun getCachedProfilePosts(userId: String): List<FirebasePost>? = cachedProfilePosts[userId]
}

