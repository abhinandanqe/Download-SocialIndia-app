package com.example.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.model.AudioSound
import com.example.model.RepositoryManager
import com.example.model.SoundCategory
import com.example.model.User
import com.example.model.Video
import com.example.utils.NumberFormatUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val SearchVideoScrimBrush = Brush.verticalGradient(
    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlagshipSearchScreen(
    onNavigateToProfile: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onBack: () -> Unit,
    onAudioClick: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onUseAudio: (String, String, String, String) -> Unit = { _, _, _, _ -> }
) {
    BackHandler { onBack() }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<User>>(emptyList()) }
    var videoResults by remember { mutableStateOf<List<Video>>(emptyList()) }
    var topViewedVideos by remember {
        mutableStateOf(
            RepositoryManager.getCachedFeedVideos().sortedByDescending { it.viewsCount }
        )
    }
    var soundResults by remember { mutableStateOf<List<AudioSound>>(emptyList()) }
    var trendingSounds by remember { mutableStateOf<List<AudioSound>>(GlobalState.cachedSearchSounds) }
    var isSearching by remember { mutableStateOf(false) }
    var previewUser by remember { mutableStateOf<User?>(null) }
    var aiAnswerText by remember { mutableStateOf<String?>(null) }
    var aiSuggestions by remember { mutableStateOf<List<com.example.ai.AISearchSuggestion>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("Top") }
    val tabs = listOf("Top", "Videos", "Accounts", "Audio", "Tags")

    val context = LocalContext.current
    val searchPrefs = remember { context.getSharedPreferences("search_history_prefs", android.content.Context.MODE_PRIVATE) }
    val recentSearches = remember {
        val saved = searchPrefs.getString("recent_queries", "") ?: ""
        mutableStateListOf<String>().apply {
            saved.split("|").map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
        }
    }
    fun saveRecentSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        recentSearches.remove(q)
        recentSearches.add(0, q)
        while (recentSearches.size > 15) recentSearches.removeAt(recentSearches.lastIndex)
        searchPrefs.edit().putString("recent_queries", recentSearches.joinToString("|")).apply()
    }
    fun removeRecentSearch(query: String) {
        recentSearches.remove(query)
        searchPrefs.edit().putString("recent_queries", recentSearches.joinToString("|")).apply()
    }

    var trendingSearches by remember { mutableStateOf<List<String>>(GlobalState.cachedSearchTrendingTags) }
    var trendingCreatorsLeaderboard by remember { mutableStateOf<List<User>>(GlobalState.cachedSearchCreators) }

    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        val hasFreshCache = GlobalState.cachedSearchCreators.isNotEmpty() &&
            GlobalState.cachedSearchSounds.isNotEmpty() &&
            topViewedVideos.isNotEmpty() &&
            (now - GlobalState.lastSearchFetchMs) < 60_000L
        if (hasFreshCache) return@LaunchedEffect

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.coroutineScope {
                val creatorsJob = launch {
                    try {
                        val creatorsSnap = RepositoryManager.firestore.collection("users")
                            .limit(30)
                            .get()
                            .await()
                        val parsedCreators = creatorsSnap.documents.mapNotNull { doc ->
                            val uname = doc.getString("username") ?: return@mapNotNull null
                            if (uname.isBlank()) return@mapNotNull null
                            val followers = (doc.getLong("followerCount") ?: doc.getLong("followersCount") ?: 0L).toInt()
                            val verified = doc.getBoolean("isVerified") ?: false
                            val badgeTier = doc.getString("creatorBadgeTier") ?: ""
                            val weeklyPts = doc.getLong("weeklyScore") ?: followers.toLong()
                            User(
                                id = doc.id,
                                username = uname,
                                displayName = doc.getString("displayName") ?: uname,
                                avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatar_url") ?: "",
                                isVerified = verified,
                                verificationBadgeUrl = doc.getString("verificationBadgeUrl") ?: "",
                                followerCount = followers,
                                creatorBadgeTier = badgeTier,
                                weeklyScore = weeklyPts
                            )
                        }.sortedByDescending { it.weeklyScore }.take(10)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            trendingCreatorsLeaderboard = parsedCreators
                            GlobalState.cachedSearchCreators = parsedCreators
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("FlagshipSearch", "Creators load error: ${e.message}")
                    }
                }

                val postsJob = launch {
                    try {
                        val postSnapshot = RepositoryManager.firestore.collection("posts")
                            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                            .limit(50)
                            .get()
                            .await()

                        val hashtags = mutableMapOf<String, Int>()
                        val parsedVideos = postSnapshot.documents.mapNotNull { doc ->
                            val caption = doc.getString("caption") ?: doc.getString("description") ?: ""
                            caption.split(Regex("\\s+"))
                                .filter { it.startsWith("#") && it.length > 1 }
                                .forEach { tag -> hashtags[tag] = hashtags.getOrDefault(tag, 0) + 1 }

                            val rawUrl = doc.getString("postUrl")
                                ?: doc.getString("videoUrl")
                                ?: doc.getString("fileUrl")
                                ?: ""
                            val thumbUrl = doc.getString("thumbnailUrl")
                                ?: doc.getString("coverUrl")
                                ?: doc.getString("imageUrl")
                                ?: ""
                            if (rawUrl.isEmpty() && thumbUrl.isEmpty()) return@mapNotNull null
                            if (com.example.utils.MediaResolver.isDummyOrSampleUrl(rawUrl)) return@mapNotNull null

                            val isImage = com.example.utils.MediaResolver.isImage(rawUrl)
                            val views = (doc.getLong("viewsCount") ?: 0L).toInt()
                            val likes = (doc.getLong("likesCount") ?: 0L).toInt()
                            val comments = (doc.getLong("commentsCount") ?: 0L).toInt()
                            val docUserId = doc.getString("userId") ?: ""
                            val uName = doc.getString("username") ?: "Creator"
                            val uAvatar = doc.getString("userAvatar") ?: doc.getString("avatarUrl") ?: ""
                            Video(
                                id = doc.id,
                                userId = docUserId,
                                user = User(
                                    id = docUserId,
                                    username = uName,
                                    avatarUrl = uAvatar
                                ),
                                videoUrl = rawUrl,
                                thumbnailUrl = thumbUrl.ifEmpty { rawUrl },
                                description = caption,
                                caption = caption,
                                likesCount = likes,
                                commentsCount = comments,
                                viewsCount = views,
                                mediaType = doc.getString("mediaType") ?: if (isImage) "image" else "video"
                            )
                        }.sortedByDescending { it.viewsCount }

                        val topTags = hashtags.entries.sortedByDescending { it.value }.take(6).map { it.key }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (topTags.isNotEmpty()) {
                                trendingSearches = topTags
                                GlobalState.cachedSearchTrendingTags = topTags
                            }
                            if (parsedVideos.isNotEmpty()) topViewedVideos = parsedVideos
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("FlagshipSearch", "Posts load error: ${e.message}")
                    }
                }

                val soundsJob = launch {
                    try {
                        val sounds = RepositoryManager.socialRepository.getTrendingSounds(10)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            trendingSounds = sounds
                            GlobalState.cachedSearchSounds = sounds
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("FlagshipSearch", "Sounds load error: ${e.message}")
                    }
                }

                creatorsJob.join()
                postsJob.join()
                soundsJob.join()
                GlobalState.lastSearchFetchMs = System.currentTimeMillis()
            }
        }
    }

    // Real-time Search Execution (keyed ONLY on searchQuery so switching Top/Videos/Accounts/Audio/Tags is 0ms instant)
    LaunchedEffect(searchQuery) {
        kotlinx.coroutines.delay(200) // Fast debounce
        if (searchQuery.isNotBlank()) {
            isSearching = true
            val queryClean = searchQuery.trim()
            saveRecentSearch(queryClean)
            try {
                // Fetch AI Autocomplete suggestions
                aiSuggestions = com.example.ai.SocialIndiaAIManager.searchService.getAutocompleteSuggestions(queryClean)

                // Perform real AI search & semantic ranking
                val currentUid = RepositoryManager.getCurrentUserId()
                val aiRanked = com.example.ai.SocialIndiaAIManager.searchService.searchAndRank(queryClean, currentUid)

                searchResults = if (aiRanked.users.isNotEmpty()) aiRanked.users else {
                    val userSnapshot = RepositoryManager.firestore.collection("users")
                        .whereGreaterThanOrEqualTo("username", queryClean)
                        .whereLessThanOrEqualTo("username", queryClean + "\uf8ff")
                        .limit(20)
                        .get()
                        .await()

                    userSnapshot.documents.mapNotNull { doc ->
                        User(
                            id = doc.id,
                            username = doc.getString("username") ?: "Unknown",
                            avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatar_url") ?: "",
                            displayName = doc.getString("displayName") ?: doc.getString("username") ?: "User",
                            verificationBadgeUrl = doc.getString("verificationBadgeUrl") ?: "",
                            isVerified = doc.getBoolean("isVerified") ?: false
                        )
                    }
                }

                // Strictly sort video results by views count (jyada bheed wale video top par)
                videoResults = aiRanked.videos.sortedByDescending { it.viewsCount }
                soundResults = aiRanked.sounds
                aiAnswerText = aiRanked.aiAnswer

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSearching = false
            }
        } else {
            searchResults = emptyList()
            videoResults = emptyList()
            soundResults = emptyList()
            aiAnswerText = null
            aiSuggestions = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Search Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                placeholder = { Text("Search videos, accounts, tags...", fontSize = 15.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // 📍 Apna District / City Pulse Local Discovery Bar
        var selectedDistrictPulse by remember { mutableStateOf("All India 🇮🇳") }
        val districtPulses = remember {
            listOf(
                "All India 🇮🇳",
                "Near You 📍",
                "Delhi NCR 🔥",
                "Mumbai 🌊",
                "Bihar & UP ✨",
                "Rajasthan 👑",
                "Punjab 🎵",
                "Bengaluru 💻",
                "Gujarat 🦁",
                "Maharashtra 🚩"
            )
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(districtPulses, key = { "pulse_$it" }, contentType = { "district_pulse" }) { pulse ->
                val isSelected = selectedDistrictPulse == pulse
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) Color(0xFF0095F6) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF00E676) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.clickable {
                        selectedDistrictPulse = pulse
                        if (pulse == "All India 🇮🇳") {
                            searchQuery = ""
                        } else {
                            val cleanKeyword = pulse.substringBefore(" ").lowercase()
                            if (cleanKeyword != "near") {
                                searchQuery = cleanKeyword
                            }
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pulse,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (searchQuery.isEmpty()) {
            val savedDefaultScroll = remember { GlobalState.getScroll("flagship_search_home") }
            val defaultListState = rememberLazyListState(
                initialFirstVisibleItemIndex = savedDefaultScroll.first,
                initialFirstVisibleItemScrollOffset = savedDefaultScroll.second
            )
            DisposableEffect(defaultListState) {
                onDispose {
                    GlobalState.saveScroll(
                        "flagship_search_home",
                        defaultListState.firstVisibleItemIndex,
                        defaultListState.firstVisibleItemScrollOffset
                    )
                }
            }
            LazyColumn(
                state = defaultListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                // 🏆 Weekly Trending Creators Leaderboard & Badges (Gold, Silver, Bronze, Verified Star)
                if (trendingCreatorsLeaderboard.isNotEmpty()) {
                    item(key = "creator_leaderboard_header", contentType = "search_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = "Leaderboard",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        "Trending Creators Leaderboard",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        "Weekly Gold, Silver, Bronze & Verified Star Rankings",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                    item(key = "creator_leaderboard_row") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            itemsIndexed(
                                trendingCreatorsLeaderboard,
                                key = { _, creator -> "leader_${creator.id}" }
                            ) { index, creator ->
                                val resolvedTier = when {
                                    creator.creatorBadgeTier.isNotBlank() -> creator.creatorBadgeTier.uppercase()
                                    index == 0 -> "GOLD"
                                    index == 1 -> "SILVER"
                                    index == 2 -> "BRONZE"
                                    creator.isVerified -> "VERIFIED_STAR"
                                    else -> "BRONZE"
                                }
                                val (badgeLabel, badgeColor) = when (resolvedTier) {
                                    "GOLD" -> "🏆 GOLD #1" to Color(0xFFFFD700)
                                    "SILVER" -> "🥈 SILVER #2" to Color(0xFFC0C0C0)
                                    "VERIFIED_STAR" -> "⭐ STAR" to Color(0xFF00E5FF)
                                    else -> "🥉 BRONZE" to Color(0xFFCD7F32)
                                }

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = androidx.compose.foundation.BorderStroke(1.2.dp, badgeColor.copy(alpha = 0.65f)),
                                    modifier = Modifier
                                        .width(136.dp)
                                        .clickable { onNavigateToProfile(creator.id) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = badgeColor.copy(alpha = 0.18f)
                                        ) {
                                            Text(
                                                text = badgeLabel,
                                                color = badgeColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box {
                                            val leaderAvatarReq = remember(creator.id, creator.avatarUrl) {
                                                com.example.utils.ExoPlayerUtils.buildFastPosterRequest(
                                                    context,
                                                    creator.avatarUrl.ifBlank { "https://api.dicebear.com/7.x/avataaars/png?seed=${creator.id}" },
                                                    160,
                                                    160
                                                )
                                            }
                                            AsyncImage(
                                                model = leaderAvatarReq,
                                                contentDescription = creator.username,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(CircleShape)
                                                    .border(2.dp, badgeColor, CircleShape)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = creator.username,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (creator.isVerified) {
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Verified,
                                                    contentDescription = "Verified",
                                                    tint = Color(0xFF0095F6),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${creator.weeklyScore} pts",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "creator_leaderboard_divider") {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.5.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // 🔥 Most Watched Videos Section (Jyada Bheed on Top)
                if (topViewedVideos.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5722),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Most Watched Videos",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Text(
                                "See All (${topViewedVideos.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { selectedTab = "Videos"; searchQuery = " " }
                            )
                        }
                    }

                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(topViewedVideos.take(12), key = { "top_bheed_${it.id}" }) { video ->
                                SearchVideoCard(
                                    video = video,
                                    onVideoClick = onVideoClick,
                                    modifier = Modifier
                                        .width(135.dp)
                                        .height(210.dp)
                                )
                            }
                        }
                    }

                    item {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.5.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Trending Sounds Discovery Section
                if (trendingSounds.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Trending Sounds", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                            }
                            Text(
                                "See All",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { selectedTab = "Audio"; searchQuery = " " }
                            )
                        }
                    }

                    itemsIndexed(
                        trendingSounds.take(4),
                        key = { idx, sound -> "trend_sound_${sound.id}_$idx" },
                        contentType = { _, _ -> "trending_sound_card" }
                    ) { index, sound ->
                        TrendingSoundSearchCard(
                            rank = index + 1,
                            sound = sound,
                            onClick = {
                                onAudioClick(sound.id, sound.title, sound.artist, sound.coverImageUrl)
                            },
                            onUseClick = {
                                onUseAudio(sound.id, sound.title, sound.artist, sound.coverImageUrl)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item(key = "trending_sounds_divider") {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                if (trendingSearches.isNotEmpty()) {
                    item(key = "trending_topics_header") {
                        Text("Trending Topics", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    itemsIndexed(
                        trendingSearches,
                        key = { idx, query -> "trend_topic_${query}_$idx" },
                        contentType = { _, _ -> "trending_topic_row" }
                    ) { index, query ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { searchQuery = query }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${index + 1}", fontWeight = FontWeight.Bold, color = if (index < 3) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.width(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(query, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (recentSearches.isNotEmpty()) {
                    item(key = "recent_searches_header") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent Searches", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "Clear all",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    recentSearches.clear()
                                    searchPrefs.edit().remove("recent_queries").apply()
                                }
                            )
                        }
                    }
                    items(recentSearches, key = { "recent_q_$it" }, contentType = { "recent_search_row" }) { query ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { searchQuery = query }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(query, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { removeRecentSearch(query) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        } else {
            // Tabs Bar
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                indicator = { tabPositions ->
                    val tabIdx = tabs.indexOf(selectedTab).coerceAtLeast(0)
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[tabIdx]),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                divider = {}
            ) {
                tabs.forEach { title ->
                    Tab(
                        selected = selectedTab == title,
                        onClick = { selectedTab = title },
                        text = {
                            Text(
                                text = title,
                                color = if (selectedTab == title) MaterialTheme.colorScheme.onBackground else Color.Gray,
                                fontWeight = if (selectedTab == title) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    )
                }
            }

            if (isSearching) {
                val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "flagshipSearchShimmer")
                val shimmerOffset by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1000f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = tween(durationMillis = 1000, easing = androidx.compose.animation.core.LinearEasing),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                    ),
                    label = "flagshipSearchShimmerOffset"
                )
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    start = androidx.compose.ui.geometry.Offset(shimmerOffset - 300f, 0f),
                    end = androidx.compose.ui.geometry.Offset(shimmerOffset, 300f)
                )
                if (selectedTab == "Videos" || selectedTab == "Tags") {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(9, key = { "search_grid_skel_$it" }, contentType = { "search_grid_skel" }) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(0.68f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(shimmerBrush)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(8, key = { "search_row_skel_$it" }, contentType = { "search_row_skel" }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(shimmerBrush)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(shimmerBrush)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(90.dp)
                                            .height(12.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(shimmerBrush)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                when (selectedTab) {
                    // TAB 1: TOP (Aggregated View)
                    "Top" -> {
                        if (searchResults.isEmpty() && videoResults.isEmpty() && soundResults.isEmpty()) {
                            NoResultsView(searchQuery)
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // AI Grounded Answer Card (if available and verified)
                                if (!aiAnswerText.isNullOrBlank()) {
                                    item(key = "ai_answer_card", contentType = "ai_answer") {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.AutoAwesome,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        "Social India AI Answer",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = aiAnswerText ?: "",
                                                    fontSize = 14.sp,
                                                    lineHeight = 20.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                // 1. Matching Videos & Reels (Ranked with highest views/bheed at top)
                                if (videoResults.isNotEmpty()) {
                                    item(key = "top_videos_header", contentType = "section_header") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.LocalFireDepartment,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFF5722),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Videos & Reels", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                            Text(
                                                "See all (${videoResults.size})",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.clickable { selectedTab = "Videos" }
                                            )
                                        }
                                    }

                                    item(key = "top_videos_row", contentType = "horizontal_videos") {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(videoResults.take(10), key = { "top_vid_${it.id}" }, contentType = { "video_card" }) { video ->
                                                SearchVideoCard(
                                                    video = video,
                                                    onVideoClick = onVideoClick,
                                                    modifier = Modifier.width(130.dp).height(200.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // 2. Matching Accounts
                                if (searchResults.isNotEmpty()) {
                                    item(key = "top_accounts_header", contentType = "section_header") {
                                        Text(
                                            "Accounts",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                    items(searchResults.take(3), key = { "top_user_${it.id}" }, contentType = { "user_row" }) { user ->
                                        SearchUserRow(user = user, onNavigateToProfile = onNavigateToProfile)
                                    }
                                }

                                // 3. Matching Audio Sounds
                                if (soundResults.isNotEmpty()) {
                                    item(key = "top_audio_header", contentType = "section_header") {
                                        Text(
                                            "Audio & Sounds",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                    itemsIndexed(soundResults.take(3), key = { idx, s -> "top_snd_${s.id}_$idx" }, contentType = { _, _ -> "sound_card" }) { idx, sound ->
                                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                            TrendingSoundSearchCard(
                                                rank = idx + 1,
                                                sound = sound,
                                                onClick = {
                                                    onAudioClick(sound.id, sound.title, sound.artist, sound.coverImageUrl)
                                                },
                                                onUseClick = {
                                                    onUseAudio(sound.id, sound.title, sound.artist, sound.coverImageUrl)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 2: VIDEOS (Real-time Video Grid with Thumbnails - highest views on top, paginated 10 at a time)
                    "Videos" -> {
                        val displayVideos = if (searchQuery.isBlank()) topViewedVideos else videoResults
                        if (displayVideos.isEmpty()) {
                            NoResultsView(searchQuery)
                        } else {
                            var visibleVideoCount by remember(searchQuery) { mutableIntStateOf(10) }
                            val pagedVideos = remember(displayVideos, visibleVideoCount) { displayVideos.take(visibleVideoCount) }
                            val videosGridState = rememberLazyGridState()
                            val shouldLoadMoreVideos by remember {
                                derivedStateOf {
                                    val total = videosGridState.layoutInfo.totalItemsCount
                                    val lastVisible = videosGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    total > 0 && lastVisible >= total - 3
                                }
                            }
                            LaunchedEffect(shouldLoadMoreVideos, displayVideos.size) {
                                if (shouldLoadMoreVideos && visibleVideoCount < displayVideos.size) {
                                    visibleVideoCount = (visibleVideoCount + 10).coerceAtMost(displayVideos.size)
                                }
                            }
                            LazyVerticalGrid(
                                state = videosGridState,
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(pagedVideos, key = { "vid_grid_${it.id}" }, contentType = { "video_grid_tile" }) { video ->
                                    SearchVideoCard(
                                        video = video,
                                        onVideoClick = onVideoClick,
                                        modifier = Modifier.aspectRatio(0.68f)
                                    )
                                }
                            }
                        }
                    }

                    // TAB 3: ACCOUNTS (Paginated 10 at a time)
                    "Accounts" -> {
                        if (searchResults.isEmpty()) {
                            NoResultsView(searchQuery)
                        } else {
                            var visibleAccountsCount by remember(searchQuery) { mutableIntStateOf(10) }
                            val pagedAccounts = remember(searchResults, visibleAccountsCount) { searchResults.take(visibleAccountsCount) }
                            val accountsListState = rememberLazyListState()
                            val shouldLoadMoreAccounts by remember {
                                derivedStateOf {
                                    val total = accountsListState.layoutInfo.totalItemsCount
                                    val lastVisible = accountsListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    total > 0 && lastVisible >= total - 3
                                }
                            }
                            LaunchedEffect(shouldLoadMoreAccounts, searchResults.size) {
                                if (shouldLoadMoreAccounts && visibleAccountsCount < searchResults.size) {
                                    visibleAccountsCount = (visibleAccountsCount + 10).coerceAtMost(searchResults.size)
                                }
                            }
                            LazyColumn(
                                state = accountsListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 8.dp)
                            ) {
                                items(pagedAccounts, key = { "acc_${it.id}" }, contentType = { "user_row" }) { user ->
                                    SearchUserRow(user = user, onNavigateToProfile = onNavigateToProfile)
                                }
                            }
                        }
                    }

                    // TAB 4: AUDIO (Paginated 10 at a time)
                    "Audio" -> {
                        val displaySounds = if (searchQuery.isBlank()) trendingSounds else soundResults
                        if (displaySounds.isEmpty()) {
                            NoResultsView(searchQuery)
                        } else {
                            var visibleSoundsCount by remember(searchQuery) { mutableIntStateOf(10) }
                            val pagedSounds = remember(displaySounds, visibleSoundsCount) { displaySounds.take(visibleSoundsCount) }
                            val audioListState = rememberLazyListState()
                            val shouldLoadMoreSounds by remember {
                                derivedStateOf {
                                    val total = audioListState.layoutInfo.totalItemsCount
                                    val lastVisible = audioListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    total > 0 && lastVisible >= total - 3
                                }
                            }
                            LaunchedEffect(shouldLoadMoreSounds, displaySounds.size) {
                                if (shouldLoadMoreSounds && visibleSoundsCount < displaySounds.size) {
                                    visibleSoundsCount = (visibleSoundsCount + 10).coerceAtMost(displaySounds.size)
                                }
                            }
                            LazyColumn(
                                state = audioListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(pagedSounds, key = { idx, s -> "snd_${s.id}_$idx" }, contentType = { _, _ -> "sound_card" }) { idx, sound ->
                                    TrendingSoundSearchCard(
                                        rank = sound.trendingRank ?: (idx + 1),
                                        sound = sound,
                                        onClick = {
                                            onAudioClick(sound.id, sound.title, sound.artist, sound.coverImageUrl)
                                        },
                                        onUseClick = {
                                            onUseAudio(sound.id, sound.title, sound.artist, sound.coverImageUrl)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // TAB 5: TAGS (Paginated 10 at a time)
                    "Tags" -> {
                        val tagMatches = remember(videoResults) {
                            videoResults.filter { it.caption.contains("#") || it.tags.isNotEmpty() }
                        }
                        if (tagMatches.isEmpty()) {
                            NoResultsView(searchQuery)
                        } else {
                            var visibleTagsCount by remember(searchQuery) { mutableIntStateOf(10) }
                            val pagedTags = remember(tagMatches, visibleTagsCount) { tagMatches.take(visibleTagsCount) }
                            val tagsGridState = rememberLazyGridState()
                            val shouldLoadMoreTags by remember {
                                derivedStateOf {
                                    val total = tagsGridState.layoutInfo.totalItemsCount
                                    val lastVisible = tagsGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    total > 0 && lastVisible >= total - 3
                                }
                            }
                            LaunchedEffect(shouldLoadMoreTags, tagMatches.size) {
                                if (shouldLoadMoreTags && visibleTagsCount < tagMatches.size) {
                                    visibleTagsCount = (visibleTagsCount + 10).coerceAtMost(tagMatches.size)
                                }
                            }
                            LazyVerticalGrid(
                                state = tagsGridState,
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(pagedTags, key = { "tag_vid_${it.id}" }, contentType = { "video_grid_tile" }) { video ->
                                    SearchVideoCard(
                                        video = video,
                                        onVideoClick = onVideoClick,
                                        modifier = Modifier.aspectRatio(0.68f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        previewUser?.let { user ->
            SocialUserProfilePreviewDialog(
                userId = user.id,
                username = user.username,
                onDismiss = { previewUser = null },
                onViewFullProfile = { uid ->
                    previewUser = null
                    onNavigateToProfile(uid)
                }
            )
        }
    }
}

@Composable
fun SearchVideoCard(
    video: Video,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val targetThumbnail = remember(video.videoUrl, video.thumbnailUrl, video.mediaType) {
        if (!video.mediaType.equals("image", ignoreCase = true) && !com.example.utils.MediaResolver.isImage(video.videoUrl)) {
            com.example.utils.MediaResolver.resolveVideoPosterUrl(video.videoUrl, video.thumbnailUrl)
                .ifBlank { video.thumbnailUrl.ifEmpty { video.videoUrl } }
        } else {
            com.example.utils.MediaResolver.resolveImageUrl(video.thumbnailUrl.ifEmpty { video.videoUrl })
        }
    }
    val thumbReq = remember(targetThumbnail) {
        com.example.utils.ExoPlayerUtils.buildFastPosterRequest(context, targetThumbnail, 360, 540)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onVideoClick(video.id) }
    ) {
        AsyncImage(
            model = thumbReq,
            contentDescription = "Video Thumbnail",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter)
                .background(SearchVideoScrimBrush)
        )

        // Play views badge at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = NumberFormatUtils.formatCount(video.viewsCount.coerceAtLeast(1)),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Creator badge at top right
        if (video.user.avatarUrl.isNotEmpty()) {
            val badgeAvatarReq = remember(video.user.avatarUrl) {
                com.example.utils.ExoPlayerUtils.buildFastPosterRequest(context, video.user.avatarUrl, 120, 120)
            }
            AsyncImage(
                model = badgeAvatarReq,
                contentDescription = "Creator",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun SearchUserRow(
    user: User,
    onNavigateToProfile: (String) -> Unit
) {
    val context = LocalContext.current
    val avatarReq = remember(user.id, user.avatarUrl) {
        com.example.utils.ExoPlayerUtils.buildFastPosterRequest(
            context,
            user.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${user.id}" },
            144,
            144
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToProfile(user.id) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = avatarReq,
            contentDescription = "Avatar",
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user.displayName.ifEmpty { user.username },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (user.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Verified, contentDescription = "Verified", tint = Color(0xFF1DA1F2), modifier = Modifier.size(14.dp))
                }
            }
            Text(
                "@${user.username}",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
        SocialFollowButton(
            targetUser = user,
            isCompact = true
        )
    }
}

@Composable
fun NoResultsView(query: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.SearchOff, contentDescription = "No results", modifier = Modifier.size(64.dp), tint = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))
            Text("No results found for \"$query\"", color = Color.Gray, fontSize = 16.sp)
        }
    }
}

@Composable
fun TrendingSoundSearchCard(
    rank: Int,
    sound: AudioSound,
    onClick: () -> Unit,
    onUseClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Number or Icon
            Text(
                text = "#$rank",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (rank <= 3) Color(0xFFFF9800) else Color.Gray,
                modifier = Modifier.width(28.dp)
            )

            // Cover Image Thumbnail
            val coverContext = LocalContext.current
            val coverReq = remember(sound.coverImageUrl) {
                com.example.utils.ExoPlayerUtils.buildFastPosterRequest(
                    coverContext,
                    sound.coverImageUrl.ifEmpty { "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=120" },
                    144,
                    144
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = coverReq,
                    contentDescription = sound.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Sound Metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sound.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (sound.category == SoundCategory.LICENSED_MUSIC) sound.artist else "@${sound.creatorUsername}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${NumberFormatUtils.formatCount(sound.reelsCount)} Reels",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Use Sound CTA
            Button(
                onClick = onUseClick,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Use", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
