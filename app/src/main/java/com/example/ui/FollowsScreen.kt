package com.example.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.LivePresenceManager
import com.example.model.RepositoryManager
import com.example.model.User
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowsScreen(
    targetUserId: String,
    initialTab: String,
    onNavigateToProfile: (String) -> Unit,
    onBack: () -> Unit
) {
    var user by remember { mutableStateOf(User()) }
    var isLoading by remember { mutableStateOf(true) }
    
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }
    val isCurrentUser = targetUserId == currentUserId
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(
        when (initialTab) {
            "following" -> 1
            "mutual" -> 2
            else -> 0
        }
    ) }

    val followersFlow = remember(targetUserId) {
        RepositoryManager.socialRepository.getFollowers(targetUserId)
    }
    val followingFlow = remember(targetUserId) {
        RepositoryManager.socialRepository.getFollowing(targetUserId)
    }
    
    val followersList by followersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val followingList by followingFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    
    val mutualList = remember(followersList, followingList) {
        followersList.filter { f -> followingList.any { it.id == f.id } }
    }

    LaunchedEffect(targetUserId) {
        try {
            val doc = RepositoryManager.firestore.collection("users").document(targetUserId).get().await()
            if (doc.exists()) {
                user = User(
                    id = doc.id,
                    username = doc.getString("username") ?: "",
                    avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: "",
                    verificationBadgeUrl = doc.getString("verificationBadgeUrl") ?: ""
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(user.username.ifEmpty { "Follows" }, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            divider = { HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f)) }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("${followersList.size} Followers", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("${followingList.size} Following", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
            )
            if (isCurrentUser) {
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("${mutualList.size} Mutual", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        var searchQuery by remember { mutableStateOf("") }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(44.dp),
            placeholder = { Text("Search", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp)) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent
            ),
            singleLine = true
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val list = when (selectedTab) {
                0 -> followersList
                1 -> followingList
                2 -> mutualList
                else -> emptyList()
            }
            val filtered = list.filter { it.username.contains(searchQuery, ignoreCase = true) }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(filtered, key = { index, u -> "${u.id}_$index" }) { _, u ->
                    val context = androidx.compose.ui.platform.LocalContext.current
                    FollowListItem(
                        user = u,
                        isCurrentUser = isCurrentUser,
                        isFollowersTab = (selectedTab == 0),
                        onNavigateToProfile = { onNavigateToProfile(it) },
                        onRemove = {
                            if (isCurrentUser) {
                                scope.launch {
                                    if (selectedTab == 0) {
                                        // Remove user from following me
                                        RepositoryManager.socialRepository.unfollowUser(followerId = u.id, followingId = currentUserId)
                                        android.widget.Toast.makeText(context, "Removed @${u.username} from followers", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        // Unfollow user that I am following
                                        RepositoryManager.socialRepository.unfollowUser(followerId = currentUserId, followingId = u.id)
                                        android.widget.Toast.makeText(context, "Unfollowed @${u.username}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FollowListItem(
    user: User,
    isCurrentUser: Boolean,
    isFollowersTab: Boolean = false,
    onNavigateToProfile: (String) -> Unit,
    onRemove: () -> Unit
) {
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }
    val scope = rememberCoroutineScope()
    val isFollowingFlow = remember(currentUserId, user.id) { RepositoryManager.socialRepository.isFollowing(currentUserId, user.id) }
    val isFollowing by isFollowingFlow.collectAsStateWithLifecycle(initialValue = false)
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToProfile(user.id) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${user.id}" },
            contentDescription = "Avatar",
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.LightGray),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.username, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                if (user.verificationBadgeUrl.isNotEmpty() || user.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Verified, contentDescription = "Verified", tint = Color(0xFF1DA1F2), modifier = Modifier.size(14.dp))
                }
            }
            if (user.displayName.isNotEmpty() && user.displayName != user.username) {
                Text(user.displayName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        if (currentUserId != user.id) {
            if (isCurrentUser && isFollowersTab) {
                // On my own followers tab: prominent Remove button
                OutlinedButton(
                    onClick = {
                        onRemove()
                    },
                    modifier = Modifier
                        .height(32.dp)
                        .defaultMinSize(minWidth = 85.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            if (isFollowing) {
                                RepositoryManager.socialRepository.unfollowUser(currentUserId, user.id)
                            } else {
                                RepositoryManager.socialRepository.followUser(currentUserId, user.id)
                            }
                        }
                    },
                    modifier = Modifier
                        .height(32.dp)
                        .defaultMinSize(minWidth = 100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                        contentColor = if (isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Text(if (isFollowing) "Following" else "Follow", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            if (isCurrentUser) {
                Spacer(modifier = Modifier.width(6.dp))
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (isFollowersTab) {
                            DropdownMenuItem(
                                text = { Text("Remove follower", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onRemove()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Block @${user.username}", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                scope.launch {
                                    RepositoryManager.socialRepository.blockUser(currentUserId, user.id)
                                    android.widget.Toast.makeText(context, "Blocked @${user.username}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Mute stories & posts", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showMenu = false
                                scope.launch {
                                    RepositoryManager.socialRepository.dismissSuggestion(user.id)
                                    android.widget.Toast.makeText(context, "Muted @${user.username}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Restrict", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showMenu = false
                                android.widget.Toast.makeText(context, "Restricted @${user.username}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}
