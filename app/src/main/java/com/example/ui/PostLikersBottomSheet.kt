package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.RepositoryManager
import com.example.model.User
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class LikerItem(
    val user: User,
    val isFollowing: Boolean = false,
    val likedAt: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostLikersBottomSheet(
    postId: String,
    onDismiss: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }

    var likers by remember { mutableStateOf<List<LikerItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var totalLikesCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(postId) {
        if (postId.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            val firestore = RepositoryManager.firestore
            // 1. Fetch likes subcollection for this post
            val likesSnapshot = firestore.collection("posts")
                .document(postId)
                .collection("likes")
                .limit(100)
                .get()
                .await()

            val likerUids = likesSnapshot.documents.map { it.id }.filter { it.isNotBlank() }
            totalLikesCount = likerUids.size

            if (likerUids.isEmpty()) {
                likers = emptyList()
                isLoading = false
                return@LaunchedEffect
            }

            // 2. Fetch current user's followings to show accurate Follow/Following state
            val followingSet = mutableSetOf<String>()
            if (currentUserId.isNotEmpty()) {
                try {
                    val followsSnap = firestore.collection("follows")
                        .whereEqualTo("followerId", currentUserId)
                        .get()
                        .await()
                    followingSet.addAll(followsSnap.documents.mapNotNull { it.getString("followingId") })
                } catch (e: Exception) {
                    // Ignore follows query error
                }
            }

            // 3. Resolve user profiles in chunks of 10
            val resolvedUsers = mutableListOf<LikerItem>()
            for (chunk in likerUids.chunked(10)) {
                for (uid in chunk) {
                    try {
                        val userDoc = firestore.collection("users").document(uid).get().await()
                        if (userDoc.exists()) {
                            val vStatus = userDoc.getString("verificationStatus") ?: ""
                            val vSuspended = userDoc.getBoolean("verificationSuspended") ?: false
                            val verified = !vSuspended && (
                                (userDoc.getBoolean("isVerified") ?: false) ||
                                vStatus == "approved" ||
                                vStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE
                            )
                            val user = User(
                                id = uid,
                                username = userDoc.getString("username") ?: "user_${uid.take(4)}",
                                displayName = userDoc.getString("fullName") ?: userDoc.getString("name") ?: userDoc.getString("displayName") ?: "",
                                avatarUrl = userDoc.getString("avatar_url")
                                    ?: userDoc.getString("profilePicUrl")
                                    ?: userDoc.getString("avatarUrl")
                                    ?: "https://api.dicebear.com/7.x/avataaars/png?seed=$uid",
                                isVerified = verified,
                                verificationStatus = vStatus,
                                verificationBadgeUrl = userDoc.getString("verificationBadgeUrl") ?: ""
                            )
                            resolvedUsers.add(
                                LikerItem(
                                    user = user,
                                    isFollowing = followingSet.contains(uid)
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // Skip unresolvable user safely
                    }
                }
            }

            likers = resolvedUsers
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    val filteredLikers = remember(likers, searchQuery) {
        if (searchQuery.isBlank()) likers
        else {
            val q = searchQuery.trim().lowercase()
            likers.filter {
                it.user.username.lowercase().contains(q) ||
                it.user.displayName.lowercase().contains(q)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFFF1744),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Likes",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (totalLikesCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "($totalLikesCount)",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }

            // Search Bar
            if (likers.size > 5) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search likes...", fontSize = 13.sp, color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .height(44.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

            // Body
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFF1744), strokeWidth = 2.5.dp)
                }
            } else if (filteredLikers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color.Gray.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No users matched" else "No likes yet",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredLikers, key = { it.user.id }) { item ->
                        var isFollowing by remember(item.user.id) { mutableStateOf(item.isFollowing) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onDismiss()
                                    onNavigateToProfile(item.user.id)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            AsyncImage(
                                model = item.user.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${item.user.id}" },
                                contentDescription = item.user.username,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            // Name & Username
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.user.username,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (item.user.isVerified) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = "Verified",
                                            tint = Color(0xFF1DA1F2),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                if (item.user.displayName.isNotBlank()) {
                                    Text(
                                        text = item.user.displayName,
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Follow / Following Button (not for own account)
                            if (currentUserId.isNotEmpty() && item.user.id != currentUserId) {
                                Button(
                                    onClick = {
                                        val newState = !isFollowing
                                        isFollowing = newState
                                        coroutineScope.launch {
                                            try {
                                                if (newState) {
                                                    RepositoryManager.socialRepository.followUser(currentUserId, item.user.id)
                                                } else {
                                                    RepositoryManager.socialRepository.unfollowUser(currentUserId, item.user.id)
                                                }
                                            } catch (e: Exception) {
                                                isFollowing = !newState // revert on error
                                            }
                                        }
                                    },
                                    colors = if (isFollowing) {
                                        ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0095F6),
                                            contentColor = Color.White
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = if (isFollowing) "Following" else "Follow",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
