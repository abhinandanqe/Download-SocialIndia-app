package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import com.example.model.User
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToProfile: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<User>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("Top") }
    val tabs = listOf("Top", "Accounts", "Tags", "Places")

    val context = androidx.compose.ui.platform.LocalContext.current
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

    var trendingSearches by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            val snap = com.example.model.RepositoryManager.firestore.collection("posts")
                .limit(60)
                .get()
                .await()
            val tagCounts = mutableMapOf<String, Int>()
            snap.documents.forEach { doc ->
                val cap = doc.getString("caption") ?: ""
                cap.split(Regex("\\s+")).filter { it.startsWith("#") && it.length > 1 }.forEach { tag ->
                    tagCounts[tag] = tagCounts.getOrDefault(tag, 0) + 1
                }
            }
            trendingSearches = tagCounts.entries.sortedByDescending { it.value }.take(8).map { it.key }
        } catch (_: Exception) {}
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        kotlinx.coroutines.delay(300) // Debounce 300ms
        try {
            searchResults = com.example.model.RepositoryManager.socialRepository.searchUsers(searchQuery)
            saveRecentSearch(searchQuery)
        } catch (_: Exception) {
            searchResults = emptyList()
        } finally {
            isSearching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Search Header
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
                placeholder = { Text("Search...", fontSize = 16.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp), tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        if (searchQuery.isEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                if (recentSearches.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "Clear all",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    recentSearches.clear()
                                    searchPrefs.edit().remove("recent_queries").apply()
                                }
                            )
                        }
                    }
                    items(recentSearches) { query ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { searchQuery = query }
                                .padding(vertical = 10.dp),
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
                
                if (trendingSearches.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Trending Tags", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    items(trendingSearches.withIndex().toList()) { (index, query) ->
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

                if (recentSearches.isEmpty() && trendingSearches.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Search for creators, posts, or hashtags", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
            }
        } else {
            // Tabs
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(selectedTab),
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(selectedTab)]),
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
                val shimmerTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "search_shimmer")
                val translateAnim by shimmerTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1000f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(durationMillis = 1000, easing = androidx.compose.animation.core.LinearEasing),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                    ),
                    label = "search_shimmer_translate"
                )
                val baseColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                val highlightColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                val shimmerBrush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(baseColor, highlightColor, baseColor),
                    start = androidx.compose.ui.geometry.Offset.Zero,
                    end = androidx.compose.ui.geometry.Offset(x = translateAnim, y = translateAnim)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp)
                ) {
                    items(6) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(shimmerBrush)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(shimmerBrush)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .width(90.dp)
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(shimmerBrush)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(78.dp)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(shimmerBrush)
                            )
                        }
                    }
                }
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, contentDescription = "No results", modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No results found for \"$searchQuery\"", color = Color.Gray, fontSize = 16.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp)
                ) {
                    itemsIndexed(searchResults, key = { index, user -> "${user.id}_$index" }) { _, user ->
                        UserSearchResultRow(
                            user = user,
                            onNavigateToProfile = onNavigateToProfile
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserSearchResultRow(
    user: User,
    onNavigateToProfile: (String) -> Unit
) {
    val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
    var isFollowing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(user.id) {
        if (currentUserId.isNotEmpty() && user.id != currentUserId) {
            com.example.model.RepositoryManager.socialRepository.isFollowing(currentUserId, user.id).collect { following ->
                isFollowing = following
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToProfile(user.id) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${user.id}" },
            contentDescription = "Avatar",
            modifier = Modifier.size(50.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.username, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (user.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Verified, contentDescription = "Verified", tint = Color(0xFF1DA1F2), modifier = Modifier.size(14.dp))
                }
            }
            Text("@${user.username}", color = Color.Gray, fontSize = 13.sp)
        }
        
        if (currentUserId.isNotEmpty() && user.id != currentUserId) {
            Button(
                onClick = {
                    val prevFollowing = isFollowing
                    isFollowing = !prevFollowing // Optimistic UI update
                    scope.launch {
                        try {
                            if (prevFollowing) {
                                com.example.model.RepositoryManager.socialRepository.unfollowUser(currentUserId, user.id)
                            } else {
                                com.example.model.RepositoryManager.socialRepository.followUser(currentUserId, user.id)
                            }
                        } catch (_: Exception) {
                            isFollowing = prevFollowing // Roll back on failure
                        }
                    }
                },
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFollowing) Color(0xFF262626) else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text(if (isFollowing) "Following" else "Follow", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
