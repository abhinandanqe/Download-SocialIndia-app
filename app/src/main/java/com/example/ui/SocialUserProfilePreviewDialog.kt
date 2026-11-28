package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.RepositoryManager
import com.example.model.User
import com.example.utils.SoundManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * High-craft, Social India branded profile preview dialog.
 * Opens whenever a username / user ID / mention / avatar is tapped in Stories or elsewhere.
 * Displays profile photo, username, verification, bio, followers/following counts,
 * active status, Follow/Unfollow toggle, and Message & View Profile buttons.
 */
@Composable
fun SocialUserProfilePreviewDialog(
    userId: String,
    username: String? = null,
    currentUserId: String = RepositoryManager.getCurrentUserId(),
    onDismiss: () -> Unit,
    onViewFullProfile: (String) -> Unit,
    onDirectMessage: ((User) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var userProfile by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isFollowing by remember { mutableStateOf(false) }
    var isFollowLoading by remember { mutableStateOf(false) }

    val effectiveUserId = userProfile?.id ?: userId
    val isSelf = effectiveUserId.isNotEmpty() && effectiveUserId == currentUserId

    LaunchedEffect(userId, username) {
        if (userId.isBlank() && username.isNullOrBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            var foundUser: User? = null
            if (userId.isNotBlank()) {
                val doc = RepositoryManager.firestore.collection("users").document(userId).get().await()
                if (doc.exists()) {
                    foundUser = User(
                        id = doc.id,
                        username = doc.getString("username") ?: "User",
                        bio = doc.getString("bio") ?: "",
                        avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: "",
                        isVerified = doc.getBoolean("isVerified") ?: false,
                        followerCount = doc.getLong("followerCount")?.toInt() ?: (doc.get("followers") as? List<*>)?.size ?: 0,
                        followingCount = doc.getLong("followingCount")?.toInt() ?: (doc.get("following") as? List<*>)?.size ?: 0,
                        status = doc.getString("status") ?: "offline",
                        lastSeen = doc.getLong("lastSeen") ?: 0L,
                        verificationBadgeUrl = doc.getString("verificationBadgeUrl") ?: ""
                    )
                }
            }
            if (foundUser == null && !username.isNullOrBlank()) {
                val cleanUname = username.removePrefix("@").trim()
                val currentAcc = com.example.auth.MultiAccountManager.activeAccount.value
                val curUid = RepositoryManager.getCurrentUserId()
                if (curUid.isNotEmpty() && (cleanUname.equals(currentAcc?.username, ignoreCase = true) || cleanUname.equals(currentAcc?.displayName, ignoreCase = true))) {
                    foundUser = User(
                        id = curUid,
                        username = currentAcc?.username?.ifEmpty { null } ?: currentAcc?.displayName ?: cleanUname,
                        avatarUrl = currentAcc?.avatarUrl ?: "",
                        isVerified = currentAcc?.isVerified ?: false
                    )
                }
                if (foundUser == null) {
                    val q = RepositoryManager.firestore.collection("users")
                        .whereEqualTo("username", cleanUname)
                        .limit(1)
                        .get()
                        .await()
                    val doc = q.documents.firstOrNull()
                    if (doc != null && doc.exists()) {
                        foundUser = User(
                            id = doc.id,
                            username = doc.getString("username") ?: cleanUname,
                            bio = doc.getString("bio") ?: "",
                            avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: "",
                            isVerified = doc.getBoolean("isVerified") ?: false,
                            followerCount = doc.getLong("followerCount")?.toInt() ?: (doc.get("followers") as? List<*>)?.size ?: 0,
                            followingCount = doc.getLong("followingCount")?.toInt() ?: (doc.get("following") as? List<*>)?.size ?: 0,
                            status = doc.getString("status") ?: "offline",
                            lastSeen = doc.getLong("lastSeen") ?: 0L,
                            verificationBadgeUrl = doc.getString("verificationBadgeUrl") ?: ""
                        )
                    }
                }
            }
            userProfile = foundUser
        } catch (e: Exception) {
            android.util.Log.e("UserProfilePreview", "Error fetching user", e)
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(effectiveUserId, currentUserId) {
        if (effectiveUserId.isNotBlank() && currentUserId.isNotBlank() && !isSelf) {
            RepositoryManager.socialRepository.isFollowing(currentUserId, effectiveUserId).collect { following ->
                isFollowing = following
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            elevation = CardDefaults.cardElevation(10.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFE2C55))
                }
            } else {
                val user = userProfile ?: User(id = userId, username = "User")
                val isOnline = user.status == "online" || (System.currentTimeMillis() - user.lastSeen < 3 * 60 * 1000)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bar with Brand tag & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnline) Color(0xFF4CAF50) else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isOnline) "Active Now" else "Social India Profile",
                                color = if (isOnline) Color(0xFF4CAF50) else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        IconButton(
                            onClick = {
                                SoundManager.playClickSound()
                                onDismiss()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    var showFullImageDialog by remember { mutableStateOf(false) }
                    val finalAvatarUrl = user.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${user.id}" }

                    // Avatar with gradient border + Clickable to Zoom / Drag / Preview full profile picture like Instagram
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AsyncImage(
                            model = finalAvatarUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                                .border(
                                    2.5.dp,
                                    Brush.sweepGradient(
                                        listOf(
                                            Color(0xFFFE2C55),
                                            Color(0xFFFF8A00),
                                            Color(0xFFE040FB),
                                            Color(0xFFFE2C55)
                                        )
                                    ),
                                    CircleShape
                                )
                                .clickable {
                                    SoundManager.playClickSound()
                                    showFullImageDialog = true
                                }
                        )
                        if (isOnline) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                                    .border(2.dp, Color(0xFF1E1E1E), CircleShape)
                            )
                        }
                    }

                    if (showFullImageDialog) {
                        Dialog(onDismissRequest = { showFullImageDialog = false }) {
                            var scale by remember { mutableStateOf(1f) }
                            var offsetX by remember { mutableStateOf(0f) }
                            var offsetY by remember { mutableStateOf(0f) }

                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "@${user.username}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        IconButton(onClick = { showFullImageDialog = false }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(340.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF111111))
                                            .pointerInput(Unit) {
                                                detectTransformGestures { _, pan: androidx.compose.ui.geometry.Offset, zoom: Float, _ ->
                                                    scale = (scale * zoom).coerceIn(1f, 4f)
                                                    if (scale > 1f) {
                                                        offsetX += pan.x
                                                        offsetY += pan.y
                                                    } else {
                                                        offsetX = 0f
                                                        offsetY = 0f
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = finalAvatarUrl,
                                            contentDescription = "Full Size Profile Picture",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer(
                                                    scaleX = scale,
                                                    scaleY = scale,
                                                    translationX = offsetX,
                                                    translationY = offsetY
                                                ),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Pinch to zoom • Drag to pan",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Username + Verification badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = user.username.ifEmpty { "user_${user.id.take(5)}" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (user.isVerified || user.verificationBadgeUrl.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = Color(0xFF1DA1F2),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Bio
                    if (user.bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = user.bio,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats Row (Followers & Following counts)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${user.followerCount}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Followers",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(Color.White.copy(alpha = 0.15f))
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${user.followingCount}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Following",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action Buttons: Follow/Unfollow & Message / View Profile
                    if (!isSelf && currentUserId.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Follow / Following Button
                            Button(
                                onClick = {
                                    if (isFollowLoading) return@Button
                                    SoundManager.playClickSound()
                                    isFollowLoading = true
                                    scope.launch {
                                        try {
                                            if (isFollowing) {
                                                RepositoryManager.socialRepository.unfollowUser(currentUserId, user.id)
                                                isFollowing = false
                                                userProfile = userProfile?.copy(followerCount = maxOf(0, user.followerCount - 1))
                                            } else {
                                                RepositoryManager.socialRepository.followUser(currentUserId, user.id)
                                                isFollowing = true
                                                userProfile = userProfile?.copy(followerCount = user.followerCount + 1)
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("UserProfilePreview", "Error toggling follow", e)
                                        } finally {
                                            isFollowLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowing) Color.White.copy(alpha = 0.12f) else Color(0xFFFE2C55)
                                )
                            ) {
                                if (isFollowLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                } else {
                                    Text(
                                        text = if (isFollowing) "Following" else "Follow",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                }
                            }

                            // Message Button
                            OutlinedButton(
                                onClick = {
                                    SoundManager.playClickSound()
                                    onDismiss()
                                    onDirectMessage?.invoke(user) ?: onViewFullProfile(user.id)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Message", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Message", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // View Full Profile Button
                    TextButton(
                        onClick = {
                            SoundManager.playClickSound()
                            onDismiss()
                            onViewFullProfile(user.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "View Full Profile",
                            color = Color(0xFF00F0FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
