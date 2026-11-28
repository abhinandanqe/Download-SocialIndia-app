package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.model.RepositoryManager
import com.example.model.User
import com.example.utils.NumberFormatUtils
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

data class ExplorePost(
    val id: String,
    val mediaUrl: String,
    val thumbnailUrl: String = "",
    val isVideo: Boolean = true,
    val heightRatio: Float = 1.35f,
    val caption: String = "",
    val viewsCount: Int = 0,
    val creatorUsername: String = "User",
    val creatorAvatarUrl: String = ""
)

private val ExploreTilePlaceholderBrush = Brush.linearGradient(
    listOf(Color(0xFF231F3A), Color(0xFF1B182E))
)

private val ExploreTileScrimBrush = Brush.verticalGradient(
    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiscoverScreen(
    onNavigateToInbox: () -> Unit,
    onNavigateToHome: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var explorePosts by remember { mutableStateOf(GlobalState.cachedDiscoverPosts) }
    var isLoading by remember { mutableStateOf(GlobalState.cachedDiscoverPosts.isEmpty()) }
    
    var categories by remember {
        mutableStateOf(
            GlobalState.cachedDiscoverCategories.ifEmpty {
                listOf("For You", "Trending", "Reels", "Comedy", "Music", "Dance", "Food")
            }
        )
    }
    var selectedCategory by remember { mutableStateOf("For You") }

    BackHandler(searchQuery.isNotEmpty()) {
        searchQuery = ""
    }

    var searchResults by remember { mutableStateOf<List<User>>(emptyList()) }

    LaunchedEffect(searchQuery) {
        kotlinx.coroutines.delay(250) // Debounce
        if (searchQuery.isNotBlank() && !searchQuery.startsWith("#")) {
            searchResults = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                RepositoryManager.socialRepository.searchUsers(searchQuery)
            }
        } else {
            searchResults = emptyList()
        }
    }

    // Single combined Firestore fetch on IO with 60s TTL cache so tab switching is instant
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        if (GlobalState.cachedDiscoverPosts.isNotEmpty() && (now - GlobalState.lastDiscoverFetchMs) < 60_000L) {
            isLoading = false
            return@LaunchedEffect
        }
        try {
            val (sortedPosts, resolvedCategories) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val snapshot = RepositoryManager.firestore.collection("posts")
                    .limit(50)
                    .get()
                    .await()

                val hashtags = mutableMapOf<String, Int>()
                val hashtagRegex = Regex("#\\w+")
                val ratios = listOf(1.2f, 1.4f, 1.3f, 1.5f, 1.25f)
                var ratioIdx = 0

                val posts = snapshot.documents.mapNotNull { doc ->
                    val postUserId = doc.getString("userId") ?: ""
                    if (postUserId.isBlank() || postUserId == "anonymous_user") return@mapNotNull null
                    if (doc.getBoolean("isDeleted") == true || doc.getBoolean("isArchived") == true) return@mapNotNull null
                    val caption = doc.getString("caption") ?: doc.getString("description") ?: ""
                    hashtagRegex.findAll(caption).forEach { match ->
                        val tag = match.value
                        hashtags[tag] = hashtags.getOrDefault(tag, 0) + 1
                    }

                    val pUrl = doc.getString("postUrl")
                        ?: doc.getString("videoUrl")
                        ?: doc.getString("fileUrl")
                        ?: ""
                    val tUrl = doc.getString("thumbnailUrl")
                        ?: doc.getString("coverUrl")
                        ?: doc.getString("imageUrl")
                        ?: pUrl

                    if (pUrl.isEmpty() && tUrl.isEmpty()) return@mapNotNull null
                    if (com.example.utils.MediaResolver.isDummyOrSampleUrl(pUrl)) return@mapNotNull null

                    val isImage = com.example.utils.MediaResolver.isImage(pUrl)
                    val isVideo = pUrl.isNotEmpty() && !isImage
                    val uName = doc.getString("username") ?: "Creator"
                    val uAvatar = doc.getString("userAvatar") ?: doc.getString("avatarUrl") ?: ""
                    val views = (doc.getLong("viewsCount") ?: 0L).toInt()

                    ExplorePost(
                        id = doc.id,
                        mediaUrl = pUrl,
                        thumbnailUrl = tUrl,
                        isVideo = isVideo,
                        heightRatio = ratios[ratioIdx++ % ratios.size],
                        caption = caption,
                        viewsCount = views,
                        creatorUsername = uName,
                        creatorAvatarUrl = uAvatar
                    )
                }.sortedByDescending { it.viewsCount }

                val trending = hashtags.entries.sortedByDescending { it.value }.take(8).map { it.key }
                val cats = if (trending.isNotEmpty()) listOf("For You", "Trending", "Reels") + trending else emptyList()
                posts to cats
            }

            if (sortedPosts.isNotEmpty()) {
                explorePosts = sortedPosts
                GlobalState.cachedDiscoverPosts = sortedPosts
                GlobalState.lastDiscoverFetchMs = System.currentTimeMillis()
            }
            if (resolvedCategories.isNotEmpty()) {
                categories = resolvedCategories
                GlobalState.cachedDiscoverCategories = resolvedCategories
            }
        } catch (e: Exception) {
            android.util.Log.w("DiscoverScreen", "Discover fetch error: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search Header: Full-width rounded search bar with magnifying glass icon and right-side Bookmark/Filter icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                placeholder = { Text("Search", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                ),
                singleLine = true
            )

            IconButton(
                onClick = {
                    selectedCategory = if (selectedCategory == "Reels") "For You" else "Reels"
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Category,
                    contentDescription = "Filter",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        
        val context = LocalContext.current
        val searchPrefs = remember { context.getSharedPreferences("search_history_prefs", android.content.Context.MODE_PRIVATE) }
        var selectedSearchTab by remember { mutableStateOf("Top") }
        val searchTabs = listOf("Top", "Accounts", "Audio", "Tags", "Places")
        val recentSearches = remember {
            val saved = searchPrefs.getString("recent_queries", "") ?: ""
            mutableStateListOf<String>().apply {
                saved.split("|").map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
            }
        }

        // Categories Row (when not typing) or Multi-Tab Search Bar (Top, Accounts, Audio, Tags, Places)
        if (searchQuery.isBlank()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories, key = { "cat_$it" }, contentType = { "category_chip" }) { category ->
                    val isSelected = selectedCategory == category
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { selectedCategory = category }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (category == "Trending") {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(15.dp), tint = if (isSelected) MaterialTheme.colorScheme.background else Color(0xFFFF9800))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = category,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        } else {
            // Categorized Search Tabs: Top, Accounts, Audio, Tags, Places
            ScrollableTabRow(
                selectedTabIndex = searchTabs.indexOf(selectedSearchTab).coerceAtLeast(0),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                edgePadding = 12.dp,
                divider = { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)) }
            ) {
                searchTabs.forEach { tabName ->
                    val isSelected = selectedSearchTab == tabName
                    Tab(
                        selected = isSelected,
                        onClick = { selectedSearchTab = tabName },
                        text = {
                            Text(
                                text = tabName,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.5.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Gray
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (searchQuery.isNotBlank()) {
            val matchingPosts = remember(explorePosts, searchQuery) {
                explorePosts.filter {
                    it.caption.contains(searchQuery, ignoreCase = true) || it.creatorUsername.contains(searchQuery, ignoreCase = true)
                }.take(6)
            }
            val matchingVideos = remember(explorePosts, searchQuery) {
                explorePosts.filter {
                    it.isVideo && (it.caption.contains(searchQuery, ignoreCase = true) || it.creatorUsername.contains(searchQuery, ignoreCase = true))
                }
            }
            val cleanTag = remember(searchQuery) { searchQuery.removePrefix("#").lowercase() }
            val matchingTagPosts = remember(explorePosts, cleanTag) {
                explorePosts.filter { it.caption.contains("#$cleanTag", ignoreCase = true) }
            }
            val matchingLocationPosts = remember(explorePosts, searchQuery) {
                explorePosts.filter { it.caption.contains(searchQuery, ignoreCase = true) }
            }

            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Recent Searches & Save to Recent
                if (recentSearches.isNotEmpty() && selectedSearchTab == "Top") {
                    item(key = "recent_searches_block", contentType = "recent_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent Searches", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
                            Text(
                                "Clear all",
                                fontSize = 12.sp,
                                color = Color(0xFF0095F6),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    recentSearches.clear()
                                    searchPrefs.edit().remove("recent_queries").apply()
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recentSearches, key = { "rec_$it" }, contentType = { "recent_chip" }) { recent ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.clickable { searchQuery = recent }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(recent, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                                    }
                                }
                            }
                        }
                    }
                }

                when (selectedSearchTab) {
                    "Accounts", "Top" -> {
                        items(searchResults, key = { "s_user_${it.id}" }, contentType = { "search_user" }) { user ->
                            val avatarReq = remember(user.id, user.avatarUrl) {
                                if (user.avatarUrl.isNotBlank()) {
                                    com.example.utils.ExoPlayerUtils.buildFastPosterRequest(
                                        context,
                                        user.avatarUrl,
                                        144,
                                        144
                                    )
                                } else null
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!recentSearches.contains(user.username)) {
                                            recentSearches.add(0, user.username)
                                            searchPrefs.edit().putString("recent_queries", recentSearches.joinToString("|")).apply()
                                        }
                                        onNavigateToProfile(user.id)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (avatarReq != null) {
                                    AsyncImage(
                                        model = avatarReq,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = user.username.firstOrNull()?.uppercase() ?: "U",
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            user.username,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (user.isVerified || user.verificationStatus == "approved" || user.verificationStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            VerificationBadge(user = user, modifier = Modifier.size(14.dp), showDialogOnClick = false)
                                        }
                                    }
                                    Text(user.bio.ifEmpty { "@${user.username}" }, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                                }
                                Icon(Icons.Default.NorthEast, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                        if (selectedSearchTab == "Top") {
                            items(matchingPosts, key = { "s_post_${it.id}" }, contentType = { "search_post" }) { post ->
                                val thumbUrl = remember(post.mediaUrl, post.thumbnailUrl, post.isVideo) {
                                    if (post.isVideo) {
                                        com.example.utils.MediaResolver.resolveVideoPosterUrl(post.mediaUrl, post.thumbnailUrl)
                                            .ifEmpty { post.thumbnailUrl.ifEmpty { post.mediaUrl } }
                                    } else {
                                        com.example.utils.MediaResolver.resolveImageUrl(post.thumbnailUrl.ifEmpty { post.mediaUrl })
                                    }
                                }
                                val thumbReq = remember(thumbUrl) {
                                    com.example.utils.ExoPlayerUtils.buildFastPosterRequest(context, thumbUrl, 160, 160)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToHome(post.id) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = thumbReq,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(post.caption.ifBlank { "Reel by @${post.creatorUsername}" }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                                        Text("@${post.creatorUsername} • ${post.viewsCount} views", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    "Audio" -> {
                        if (matchingVideos.isEmpty()) {
                            item(key = "audio_empty") {
                                Text("No matching audio tracks found", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 24.dp))
                            }
                        } else {
                            items(matchingVideos, key = { "s_audio_${it.id}" }, contentType = { "search_audio" }) { post ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToHome(post.id) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF0095F6).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF0095F6))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Original Audio • @${post.creatorUsername}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("${post.viewsCount} views", color = Color.Gray, fontSize = 12.sp)
                                    }
                                    Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                        }
                    }
                    "Tags" -> {
                        item(key = "tag_result_$cleanTag") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = ""
                                        selectedCategory = "#$cleanTag"
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, Color.Gray.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("#", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("#$cleanTag", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("${matchingTagPosts.size} public posts", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    "Places" -> {
                        item(key = "place_result_$searchQuery") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val firstPost = matchingLocationPosts.firstOrNull()
                                        if (firstPost != null) onNavigateToHome(firstPost.id)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE91E63).copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFE91E63))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(searchQuery, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${matchingLocationPosts.size} posts", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else if (isLoading && explorePosts.isEmpty()) {
            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "exploreShimmer")
            val shimmerTranslate by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1000f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = tween(durationMillis = 1050, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                ),
                label = "exploreShimmerOffset"
            )
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                start = androidx.compose.ui.geometry.Offset(shimmerTranslate - 300f, 0f),
                end = androidx.compose.ui.geometry.Offset(shimmerTranslate, 300f)
            )
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                verticalItemSpacing = 1.5.dp
            ) {
                items(12, key = { "explore_skeleton_$it" }, contentType = { "explore_skeleton" }) { idx ->
                    val ratio = if (idx % 5 == 2) 0.5f else 1f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                            .background(shimmerBrush)
                    )
                }
            }
        } else {
            val displayPosts = remember(explorePosts, selectedCategory) {
                val filtered = if (selectedCategory == "Reels") {
                    explorePosts.filter { it.isVideo }
                } else if (selectedCategory != "For You" && selectedCategory != "Trending" && selectedCategory.startsWith("#")) {
                    val catMatches = explorePosts.filter { it.caption.contains(selectedCategory, ignoreCase = true) }
                    if (catMatches.isNotEmpty()) catMatches else explorePosts
                } else {
                    explorePosts
                }
                filtered.sortedByDescending { it.viewsCount }
            }

            if (displayPosts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = "No posts yet",
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No posts yet", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text("New photos and reels will appear here", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            } else {
                var visibleCount by remember(selectedCategory) { mutableIntStateOf(18) }
                val paginatedExplorePosts = remember(displayPosts, visibleCount) {
                    displayPosts.take(visibleCount)
                }
                val savedScroll = remember(selectedCategory) { GlobalState.getScroll("discover_$selectedCategory") }
                val staggeredState = rememberLazyStaggeredGridState(
                    initialFirstVisibleItemIndex = savedScroll.first,
                    initialFirstVisibleItemScrollOffset = savedScroll.second
                )
                DisposableEffect(staggeredState, selectedCategory) {
                    onDispose {
                        GlobalState.saveScroll(
                            "discover_$selectedCategory",
                            staggeredState.firstVisibleItemIndex,
                            staggeredState.firstVisibleItemScrollOffset
                        )
                    }
                }
                val shouldLoadMoreExplore by remember {
                    derivedStateOf {
                        val total = staggeredState.layoutInfo.totalItemsCount
                        val lastVisible = staggeredState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        total > 0 && lastVisible >= total - 6
                    }
                }
                LaunchedEffect(shouldLoadMoreExplore, displayPosts.size) {
                    if (shouldLoadMoreExplore && visibleCount < displayPosts.size) {
                        visibleCount = (visibleCount + 15).coerceAtMost(displayPosts.size)
                    }
                }

                // Edge-to-Edge 3-Column Instagram Masonry/Grid mixing 1:1 square posts and vertical 2x2-height Reel tiles
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(3),
                    state = staggeredState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                    verticalItemSpacing = 1.5.dp
                ) {
                    itemsIndexed(
                        items = paginatedExplorePosts,
                        key = { index, post -> "${post.id}_$index" },
                        contentType = { _, post -> if (post.isVideo) "explore_video" else "explore_image" }
                    ) { index, post ->
                        val cellAspectRatio = if (index % 5 == 2 || (post.isVideo && index % 3 == 0)) 0.5f else 1f
                        ExplorePostItem(
                            post = post,
                            aspectRatio = cellAspectRatio,
                            onClick = { onNavigateToHome(post.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExplorePostItem(
    post: ExplorePost,
    aspectRatio: Float = 0.75f,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val targetUrl = remember(post.mediaUrl, post.thumbnailUrl, post.isVideo) {
        if (post.isVideo) {
            com.example.utils.MediaResolver.resolveVideoPosterUrl(post.mediaUrl, post.thumbnailUrl).ifEmpty {
                post.thumbnailUrl.ifEmpty { post.mediaUrl }
            }
        } else {
            com.example.utils.MediaResolver.resolveImageUrl(post.thumbnailUrl.ifEmpty { post.mediaUrl })
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(ExploreTilePlaceholderBrush)
            .clickable { onClick() }
    ) {
        val tileImageRequest = remember(targetUrl) {
            com.example.utils.ExoPlayerUtils.buildFastPosterRequest(context, targetUrl, 360, 480)
        }
        AsyncImage(
            model = tileImageRequest,
            contentDescription = "Explore Media",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Subtle gradient overlay at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.38f)
                .align(Alignment.BottomCenter)
                .background(ExploreTileScrimBrush)
        )

        // Top right Reel / Video indicator icon
        if (post.isVideo) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Reel",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp)
            )
        }

        // Bottom-left eye/play icon and view count overlay
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = NumberFormatUtils.formatCount(post.viewsCount.coerceAtLeast(0)),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
