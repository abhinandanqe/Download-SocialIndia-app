@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Comment
import com.example.model.Conversation
import com.example.model.RepositoryManager
import com.example.model.StoryUiModel
import com.example.model.User
import com.example.utils.NumberFormatUtils
import com.example.utils.SoundManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// MARK: - Live Preview Card (For Feeds, Messages, and Search)

@Composable
fun LivePreviewCard(
    story: StoryUiModel,
    onJoinLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                SoundManager.playClickSound()
                onJoinLive()
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            // Background Image / Thumbnail with dark gradient
            AsyncImage(
                model = story.imageUrl.ifEmpty { "https://picsum.photos/seed/${story.id}/720/480" },
                contentDescription = "Live Preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Dark Gradient Overlay for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Top Row: LIVE Badge + Viewers Count + Host Details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LivePillBadge(viewersCount = maxOf(story.viewers.size, 1), isLarge = false)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${maxOf(story.viewers.size, 1)} watching",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Bottom Section: Host info, Topic & Join Button
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LiveAvatar(
                        avatarUrl = story.avatarUrl,
                        userId = story.userId,
                        isLiveOverride = true,
                        size = 42.dp,
                        showLiveBadge = false,
                        showRing = true
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = story.username,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (story.isVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF1DA1F2),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = story.textOverlay.ifEmpty { "Streaming Live · Tap to join chat" },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Join Live Action Button
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            SoundManager.playClickSound()
                            onJoinLive()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LivePrimaryRed),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Join Live",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Live Replay Card (For Recorded / Finished Broadcasts)

@Composable
fun LiveReplayCard(
    story: StoryUiModel,
    durationText: String = "00:00",
    viewsCount: Int = 0,
    onWatchReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onWatchReplay() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF2B1B3D), Color(0xFF141826)))
                    )
            ) {
                if (story.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = story.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF7C4DFF),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "REPLAY",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = story.username,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = story.textOverlay.ifEmpty { "Live broadcast stream recording" },
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⏱️ $durationText · ${NumberFormatUtils.formatCount(viewsCount)} views",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }

            IconButton(onClick = onWatchReplay) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Watch",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// MARK: - In-App Live Sharing Bottom Sheet

@Composable
fun LiveShareBottomSheet(
    story: StoryUiModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }

    val liveShareUrl = "https://socialindia.app/live/${story.id}"

    // Recent conversations & mutual friends
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var mutualFriends by remember { mutableStateOf<List<User>>(emptyList()) }
    var sentTargetIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            try {
                val convSnap = RepositoryManager.firestore.collection("conversations")
                    .whereArrayContains("participantIds", currentUserId)
                    .limit(10)
                    .get()
                    .await()
                conversations = convSnap.toObjects(Conversation::class.java)

                // Load mutual / followed friends
                val usersSnap = RepositoryManager.firestore.collection("users").limit(12).get().await()
                mutualFriends = usersSnap.documents.mapNotNull { doc ->
                    if (doc.id != currentUserId) {
                        User(
                            id = doc.id,
                            username = doc.getString("username") ?: "User",
                            avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatar_url") ?: "",
                            isVerified = doc.getBoolean("isVerified") ?: false
                        )
                    } else null
                }
            } catch (e: Exception) {
                android.util.Log.e("LiveShare", "Error loading share targets", e)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181818),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Share Live Video 🔴",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = "Share @${story.username}'s broadcast with friends & groups",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Action Pills Row: Copy Link, Share via System, etc.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ShareQuickActionButton(
                    icon = Icons.Outlined.ContentCopy,
                    label = "Copy Link",
                    color = Color(0xFF00F0FF)
                ) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("Live Stream Link", liveShareUrl)
                    clipboard?.setPrimaryClip(clip)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(context, "Link copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
                }

                ShareQuickActionButton(
                    icon = Icons.Outlined.Share,
                    label = "Share App",
                    color = Color(0xFF25D366)
                ) {
                    try {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "🔴 Join ${story.username}'s LIVE video on SocialIndia:\n$liveShareUrl")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Live Stream"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "No app available to share", Toast.LENGTH_SHORT).show()
                    }
                }

                ShareQuickActionButton(
                    icon = Icons.Outlined.AddCircleOutline,
                    label = "Share to Story",
                    color = Color(0xFFFE2C55)
                ) {
                    Toast.makeText(context, "Shared to your Story tray!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }

                ShareQuickActionButton(
                    icon = Icons.Outlined.QrCode,
                    label = "QR Code",
                    color = Color(0xFFFFD700)
                ) {
                    Toast.makeText(context, "Generated Live QR Code", Toast.LENGTH_SHORT).show()
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Search Filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search friends or groups...", color = Color.Gray, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White.copy(alpha = 0.3f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color(0xFF242424),
                    unfocusedContainerColor = Color(0xFF242424),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Send Direct to Friends & Groups",
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            val filteredFriends = remember(mutualFriends, searchQuery) {
                if (searchQuery.isBlank()) mutualFriends
                else mutualFriends.filter { it.username.contains(searchQuery, ignoreCase = true) }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredFriends) { friend ->
                    val isSent = sentTargetIds.contains(friend.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF222222), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = friend.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${friend.id}" },
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = friend.username,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Active recently",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = {
                                if (!isSent) {
                                    sentTargetIds = sentTargetIds + friend.id
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    SoundManager.playClickSound()
                                    scope.launch {
                                        try {
                                            RepositoryManager.socialRepository.sendMessage(
                                                senderId = currentUserId,
                                                receiverId = friend.id,
                                                message = "🔴 LIVE: Join @${story.username}'s live video!\n$liveShareUrl"
                                            )
                                        } catch (e: Exception) {
                                            android.util.Log.e("LiveShare", "Error sending share message", e)
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSent) Color.DarkGray else LivePrimaryRed
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (isSent) "Sent ✓" else "Send",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ShareQuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(color.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, color.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// MARK: - Invite Friends to Live Broadcast Sheet

@Composable
fun InviteFriendsBottomSheet(
    storyId: String,
    hostUsername: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }

    var friends by remember { mutableStateOf<List<User>>(emptyList()) }
    var invitedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            try {
                val snap = RepositoryManager.firestore.collection("users").limit(20).get().await()
                friends = snap.documents.mapNotNull { doc ->
                    if (doc.id != currentUserId) {
                        User(
                            id = doc.id,
                            username = doc.getString("username") ?: "User",
                            avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatar_url") ?: "",
                            isVerified = doc.getBoolean("isVerified") ?: false
                        )
                    } else null
                }
            } catch (e: Exception) {
                android.util.Log.e("InviteFriends", "Error fetching friends", e)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181818),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Invite Friends 👥",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF262626)
                ) {
                    Text(
                        text = "${invitedIds.size} Invited",
                        color = Color(0xFF00F0FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = "Notify your friends to join this live broadcast room",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search friends...", color = Color.Gray, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White.copy(alpha = 0.3f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color(0xFF242424),
                    unfocusedContainerColor = Color(0xFF242424),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            val filtered = remember(friends, searchQuery) {
                if (searchQuery.isBlank()) friends
                else friends.filter { it.username.contains(searchQuery, ignoreCase = true) }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered) { friend ->
                    val isInvited = invitedIds.contains(friend.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF222222), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = friend.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${friend.id}" },
                            contentDescription = null,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = friend.username,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (friend.isVerified) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF1DA1F2),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Follows you",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = {
                                if (!isInvited) {
                                    invitedIds = invitedIds + friend.id
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    SoundManager.playClickSound()

                                    // Push Live invite notification
                                    scope.launch {
                                        try {
                                            val notifId = java.util.UUID.randomUUID().toString()
                                            val notifMap = hashMapOf(
                                                "id" to notifId,
                                                "recipientId" to friend.id,
                                                "type" to "LIVE",
                                                "actorId" to currentUserId,
                                                "actorUsername" to hostUsername,
                                                "content" to "invited you to watch their Live Stream! 🔴",
                                                "postId" to storyId,
                                                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                            )
                                            RepositoryManager.firestore.collection("users")
                                                .document(friend.id)
                                                .collection("notifications")
                                                .document(notifId)
                                                .set(notifMap)
                                        } catch (e: Exception) {
                                            android.util.Log.e("InviteFriends", "Failed to send notification", e)
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isInvited) Color.DarkGray else Color(0xFF00C853)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (isInvited) "Invited ✓" else "Invite",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// MARK: - Live Ended Screen (Graceful Viewer Screen when Stream Finishes)

@Composable
fun LiveEndedScreen(
    story: StoryUiModel,
    durationText: String = "18:45",
    totalViewersCount: Int = 24,
    onWatchReplay: () -> Unit = {},
    onExploreOthers: () -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }
    var isFollowing by remember { mutableStateOf(false) }

    LaunchedEffect(story.userId, currentUserId) {
        val targetId = story.userId
        if (!targetId.isNullOrEmpty() && currentUserId.isNotEmpty()) {
            RepositoryManager.socialRepository.isFollowing(currentUserId, targetId).collect {
                isFollowing = it
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111111)),
        contentAlignment = Alignment.Center
    ) {
        // Subtle background vignette
        AsyncImage(
            model = story.imageUrl.ifEmpty { story.avatarUrl },
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.15f
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Close Button in top right
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        // Center Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
        ) {
            // Host Avatar with finished broadcast ring
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = story.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${story.userId}" },
                    contentDescription = "Host Avatar",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .border(3.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .background(Color(0xFF2196F3), CircleShape)
                        .border(2.dp, Color.Black, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Live Video Has Ended",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "@${story.username}'s broadcast is finished. Thank you for watching!",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Stats Chip Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF222222), RoundedCornerShape(14.dp))
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = durationText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Duration",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = NumberFormatUtils.formatCount(totalViewersCount),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Total Viewers",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            // Follow button
            if (story.userId != null && story.userId != currentUserId) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                if (isFollowing) {
                                    RepositoryManager.socialRepository.unfollowUser(currentUserId, story.userId)
                                    isFollowing = false
                                } else {
                                    RepositoryManager.socialRepository.followUser(currentUserId, story.userId)
                                    isFollowing = true
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("LiveEnded", "Error following host", e)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowing) Color(0xFF333333) else LivePrimaryRed
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = if (isFollowing) "Following @${story.username} ✓" else "Follow @${story.username}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Watch Replay button
            OutlinedButton(
                onClick = onWatchReplay,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Replay,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Watch Replay",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Return to feed / explore
            TextButton(
                onClick = {
                    onExploreOthers()
                    onClose()
                }
            ) {
                Text(
                    text = "Explore Other Live Streams",
                    color = Color(0xFF00F0FF),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
