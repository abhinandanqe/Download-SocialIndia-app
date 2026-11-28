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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.*
import com.example.repository.GroupChatRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * High-fidelity, Instagram-inspired Share Sheet for Social India.
 * Unified architecture for sharing Posts, Reels, Photos, Profiles, Audio, Stories, and Videos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernShareBottomSheet(
    payload: SharePayload,
    onDismiss: () -> Unit,
    onAddToStory: ((SharePayload) -> Unit)? = null,
    onOpenChatWithUser: ((User) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = RepositoryManager.getCurrentUserId()
    var currentUserName by remember { mutableStateOf("You") }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(currentUserId).get().await()
                currentUserName = doc.getString("username") ?: "You"
            } catch (e: Exception) {}
        }
    }

    // Selection States
    val selectedUsers = remember { mutableStateListOf<User>() }
    val selectedGroups = remember { mutableStateListOf<GroupChat>() }

    // Search and Input State
    var searchQuery by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Friends/Users, 1 = Groups
    var isSending by remember { mutableStateOf(false) }
    var isSentSuccess by remember { mutableStateOf(false) }

    // Inline Group Creation State
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    // Real-time Friends (Followers & Following from Realtime/Firestore)
    val followersFlow = remember(currentUserId) {
        RepositoryManager.socialRepository.getFollowers(currentUserId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val followingFlow = remember(currentUserId) {
        RepositoryManager.socialRepository.getFollowing(currentUserId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val allFriends = remember(followersFlow.value, followingFlow.value) {
        (followersFlow.value + followingFlow.value).distinctBy { it.id }.filter { it.id != currentUserId }
    }

    // Real-time User Groups
    val userGroupsFlow = remember(currentUserId) {
        GroupChatRepository.listenUserGroups(currentUserId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // Remote User Search State
    var firestoreSearchResults by remember { mutableStateOf<List<User>>(emptyList()) }
    var isSearchingRemote by remember { mutableStateOf(false) }

    // Debounced Search Effect
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && searchQuery.length >= 2) {
            isSearchingRemote = true
            delay(300) // Debounce
            try {
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("users")
                    .whereGreaterThanOrEqualTo("username", searchQuery)
                    .whereLessThanOrEqualTo("username", searchQuery + "\uf8ff")
                    .limit(20)
                    .get()
                    .await()

                val matched = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.id
                    if (uid == currentUserId) return@mapNotNull null
                    val username = doc.getString("username") ?: "User"
                    val avatarUrl = doc.getString("profilePicUrl")
                        ?: doc.getString("avatarUrl")
                        ?: doc.getString("avatar_url")
                        ?: ""
                    val isVerified = doc.getBoolean("isVerified") ?: false
                    val bio = doc.getString("bio") ?: ""
                    User(id = uid, username = username, avatarUrl = avatarUrl, isVerified = isVerified, bio = bio)
                }
                firestoreSearchResults = matched
            } catch (e: Exception) {
                firestoreSearchResults = emptyList()
            } finally {
                isSearchingRemote = false
            }
        } else {
            firestoreSearchResults = emptyList()
            isSearchingRemote = false
        }
    }

    // Filtered lists
    val displayedUsers = remember(searchQuery, allFriends, firestoreSearchResults) {
        if (searchQuery.isBlank()) {
            allFriends
        } else {
            val localMatches = allFriends.filter { it.username.contains(searchQuery, ignoreCase = true) }
            (localMatches + firestoreSearchResults).distinctBy { it.id }
        }
    }

    val displayedGroups = remember(searchQuery, userGroupsFlow.value) {
        if (searchQuery.isBlank()) {
            userGroupsFlow.value
        } else {
            userGroupsFlow.value.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val totalSelectedCount = selectedUsers.size + selectedGroups.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .fillMaxHeight(0.88f)
        ) {
            // 1. Header Bar: Title + Content Preview Mini-Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Share",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. Shared Content Mini-Card Preview
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Small thumbnail preview
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (payload.thumbnailUrl.isNotBlank()) {
                            AsyncImage(
                                model = payload.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = when (payload.contentType) {
                                    ShareContentType.REEL, ShareContentType.VIDEO -> Icons.Default.PlayArrow
                                    ShareContentType.AUDIO -> Icons.Default.MusicNote
                                    ShareContentType.PROFILE -> Icons.Default.Person
                                    else -> Icons.Default.Image
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = payload.ownerUsername.ifBlank { "Social India" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (payload.isVerified) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = payload.contentType.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (payload.caption.isNotBlank()) {
                            Text(
                                text = payload.caption,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 3. Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search friends or groups...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // 4. Selected Chips Tray (If any user or group is selected)
            AnimatedVisibility(
                visible = totalSelectedCount > 0,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(selectedUsers, key = { "u_${it.id}" }) { user ->
                        AssistChip(
                            onClick = { selectedUsers.remove(user) },
                            label = {
                                Text(
                                    user.username,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.LightGray)
                                ) {
                                    AsyncImage(
                                        model = user.avatarUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    items(selectedGroups, key = { "g_${it.id}" }) { group ->
                        AssistChip(
                            onClick = { selectedGroups.remove(group) },
                            label = {
                                Text(
                                    group.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // 5. Quick Share Actions Carousel (Add to Story, Create Group, Copy Link, System Share, WhatsApp)
            if (totalSelectedCount == 0 && searchQuery.isEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    // Action 0: Repost
                    item {
                        QuickActionCircularButton(
                            icon = Icons.Default.Repeat,
                            label = "Repost",
                            brush = Brush.linearGradient(listOf(Color(0xFF00C6FF), Color(0xFF0072FF))),
                            onClick = {
                                if (payload.isExclusiveContent && !payload.isUnlocked) {
                                    Toast.makeText(context, "Unlock this exclusive content first to repost", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                    return@QuickActionCircularButton
                                }
                                scope.launch {
                                    val success = com.example.repository.FlagshipFeaturesRepository.repost(currentUserId, payload.contentId)
                                    if (success) {
                                        Toast.makeText(context, "Reposted to your profile!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Removed repost", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                onDismiss()
                            }
                        )
                    }

                    // Action 0.5: Save to Collection
                    item {
                        QuickActionCircularButton(
                            icon = Icons.Default.BookmarkBorder,
                            label = "Save",
                            brush = Brush.linearGradient(listOf(Color(0xFFFFD200), Color(0xFFF7971E))),
                            onClick = {
                                scope.launch {
                                    val isSaved = com.example.repository.FlagshipFeaturesRepository.toggleSavePost(currentUserId, payload.contentId)
                                    if (isSaved) {
                                        Toast.makeText(context, "Saved to your collection!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Removed from saved", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                onDismiss()
                            }
                        )
                    }

                    // Action 1: Add to Story (Universal Story Sharing — blocked until exclusive content is unlocked)
                    if (com.example.manager.SocialIndiaShareManager.canShareToStory(payload)) {
                        item {
                            QuickActionCircularButton(
                                icon = Icons.Default.AddCircleOutline,
                                label = "Add to Story",
                                brush = Brush.linearGradient(listOf(Color(0xFFFF8533), Color(0xFFFF2A6D), Color(0xFF9B51E0))),
                                onClick = {
                                    if (payload.isExclusiveContent && !payload.isUnlocked) {
                                        Toast.makeText(context, "Unlock this exclusive content before adding to your story", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } else {
                                        onDismiss()
                                        onAddToStory?.invoke(payload)
                                    }
                                }
                            )
                        }
                    }

                    // Action 2: Create Group
                    item {
                        QuickActionCircularButton(
                            icon = Icons.Default.GroupAdd,
                            label = "Create Group",
                            brush = Brush.linearGradient(listOf(Color(0xFF4A90E2), Color(0xFF0056C6))),
                            onClick = {
                                showCreateGroupDialog = true
                            }
                        )
                    }

                    // Action 3: Copy Link
                    item {
                        QuickActionCircularButton(
                            icon = Icons.Default.Link,
                            label = "Copy Link",
                            brush = Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669))),
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val link = payload.canonicalUrl.ifBlank { "https://socialindia.app/post/${payload.contentId}" }
                                val clip = ClipData.newPlainText("Social India Link", link)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()

                                // Increment share count
                                recordShareCount(payload.contentId)
                                onDismiss()
                            }
                        )
                    }

                    // Action 4: Native System Share / More
                    item {
                        QuickActionCircularButton(
                            icon = Icons.Default.Share,
                            label = "Share via...",
                            brush = Brush.linearGradient(listOf(Color(0xFF6B7280), Color(0xFF374151))),
                            onClick = {
                                try {
                                    val link = payload.canonicalUrl.ifBlank { "https://socialindia.app/post/${payload.contentId}" }
                                    val textToShare = buildString {
                                        if (payload.caption.isNotBlank()) append("${payload.caption}\n\n")
                                        append("Check this out on Social India: $link")
                                    }
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, textToShare)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Share via")
                                    context.startActivity(shareIntent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "No app available to share", Toast.LENGTH_SHORT).show()
                                }

                                recordShareCount(payload.contentId)
                                onDismiss()
                            }
                        )
                    }

                    // Action 5: WhatsApp Direct
                    item {
                        QuickActionCircularButton(
                            icon = Icons.Default.Chat,
                            label = "WhatsApp",
                            brush = Brush.linearGradient(listOf(Color(0xFF25D366), Color(0xFF128C7E))),
                            onClick = {
                                val link = payload.canonicalUrl.ifBlank { "https://socialindia.app/post/${payload.contentId}" }
                                val textToShare = "Check this out on Social India: $link"
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, textToShare)
                                    type = "text/plain"
                                    setPackage("com.whatsapp")
                                }
                                try {
                                    context.startActivity(sendIntent)
                                } catch (e: Exception) {
                                    try {
                                        val fallback = Intent.createChooser(sendIntent.apply { setPackage(null) }, "Share via")
                                        context.startActivity(fallback)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "No app available to share", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                recordShareCount(payload.contentId)
                                onDismiss()
                            }
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // 6. Tabs: Friends / Direct Messages vs Groups
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Friends (${displayedUsers.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "Groups (${displayedGroups.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 7. Tab Content: List of Users or Groups
            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == 0) {
                    // Friends List
                    if (displayedUsers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.PersonSearch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No people found matching \"$searchQuery\"" else "No friends found yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(displayedUsers, key = { it.id }) { user ->
                                val isSelected = selectedUsers.any { it.id == user.id }
                                UserShareRow(
                                    user = user,
                                    isSelected = isSelected,
                                    onToggle = {
                                        if (isSelected) {
                                            selectedUsers.removeAll { it.id == user.id }
                                        } else {
                                            selectedUsers.add(user)
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Groups List
                    if (displayedGroups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No groups matching \"$searchQuery\"" else "No groups yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                FilledTonalButton(
                                    onClick = { showCreateGroupDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Create a Group")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(displayedGroups, key = { it.id }) { group ->
                                val isSelected = selectedGroups.any { it.id == group.id }
                                GroupShareRow(
                                    group = group,
                                    isSelected = isSelected,
                                    onToggle = {
                                        if (isSelected) {
                                            selectedGroups.removeAll { it.id == group.id }
                                        } else {
                                            selectedGroups.add(group)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 8. Bottom Action Area (When Users or Groups are selected)
            AnimatedVisibility(
                visible = totalSelectedCount > 0,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Optional message input
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("Write a message...", fontSize = 14.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick emoji selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("🔥", "❤️", "😂", "👏", "😍", "🙌").forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 24.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { messageText += emoji }
                                        .padding(4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Send Button
                        Button(
                            onClick = {
                                if (isSending) return@Button
                                isSending = true
                                scope.launch {
                                    try {
                                        val contentSnippet = if (messageText.isNotBlank()) {
                                            messageText
                                        } else {
                                            "Shared ${payload.contentType.label.lowercase()}: ${payload.canonicalUrl.ifBlank { "https://socialindia.app/post/${payload.contentId}" }}"
                                        }

                                        val mediaTypeStr = when (payload.contentType) {
                                            ShareContentType.REEL -> "shared_reel"
                                            ShareContentType.POST -> "shared_post"
                                            ShareContentType.PHOTO -> "shared_photo"
                                            ShareContentType.PROFILE -> "shared_profile"
                                            ShareContentType.AUDIO -> "shared_audio"
                                            ShareContentType.STORY -> "shared_story"
                                            else -> "shared_post"
                                        }

                                        // 1. Send to all selected individual friends (Realtime/Firestore Chat)
                                        selectedUsers.forEach { friend ->
                                            try {
                                                RepositoryManager.socialRepository.sendMessage(
                                                    senderId = currentUserId,
                                                    receiverId = friend.id,
                                                    message = contentSnippet,
                                                    sharedVideoId = payload.contentId,
                                                    mediaType = mediaTypeStr,
                                                    mediaUrl = payload.mediaUrl,
                                                    thumbnailUrl = payload.thumbnailUrl,
                                                    videoDurationSeconds = 0
                                                )
                                            } catch (e: Exception) {
                                                // Continue broadcasting to other users
                                            }
                                        }

                                        // 2. Send to all selected groups
                                        selectedGroups.forEach { group ->
                                            try {
                                                val grpMsg = GroupMessage(
                                                    id = "msg_${System.currentTimeMillis()}_${(100..999).random()}",
                                                    groupId = group.id,
                                                    senderId = currentUserId,
                                                    senderName = currentUserName,
                                                    message = contentSnippet,
                                                    type = if (payload.contentType == ShareContentType.REEL || payload.contentType == ShareContentType.VIDEO) "video" else "image",
                                                    mediaUrl = payload.mediaUrl.ifBlank { payload.thumbnailUrl },
                                                    timestamp = System.currentTimeMillis()
                                                )
                                                GroupChatRepository.sendGroupMessage(group.id, grpMsg)
                                            } catch (e: Exception) {
                                                // Continue broadcasting to other groups
                                            }
                                        }

                                        // 3. Record share metrics
                                        recordShareCount(payload.contentId)

                                        isSentSuccess = true
                                        Toast.makeText(context, "Shared successfully to $totalSelectedCount conversation(s)!", Toast.LENGTH_SHORT).show()
                                        delay(400)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error sharing: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSending = false
                                    }
                                }
                            },
                            enabled = !isSending && totalSelectedCount > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEE1D52) // Social India accent red
                            )
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sending...", color = Color.White, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Send ($totalSelectedCount)",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 9. Inline Group Creation Flow
    if (showCreateGroupDialog) {
        InlineGroupCreationDialog(
            currentUserId = currentUserId,
            currentUserName = currentUserName,
            availableUsers = allFriends,
            onDismiss = { showCreateGroupDialog = false },
            onGroupCreated = { newGroupId, newGroupName ->
                showCreateGroupDialog = false
                Toast.makeText(context, "Group \"$newGroupName\" created!", Toast.LENGTH_SHORT).show()
                // Auto-switch to Groups tab
                selectedTab = 1
            }
        )
    }
}

/**
 * Individual User Row inside Share Sheet
 */
@Composable
private fun UserShareRow(
    user: User,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // User Avatar
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.LightGray)
        ) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.username,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (user.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Text(
                text = if (user.bio.isNotBlank()) user.bio else "@${user.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Custom Selection Checkbox
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) Color(0xFFEE1D52) else Color.Transparent
                )
                .border(
                    width = 2.dp,
                    color = if (isSelected) Color(0xFFEE1D52) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Individual Group Row inside Share Sheet
 */
@Composable
private fun GroupShareRow(
    group: GroupChat,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Group Avatar
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (group.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = group.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${group.members.size} members",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Custom Selection Checkbox
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) Color(0xFFEE1D52) else Color.Transparent
                )
                .border(
                    width = 2.dp,
                    color = if (isSelected) Color(0xFFEE1D52) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Circular Quick Action Button with vibrant gradient background
 */
@Composable
private fun QuickActionCircularButton(
    icon: ImageVector,
    label: String,
    brush: Brush,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .width(68.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(brush),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Lightweight Inline Group Creation Dialog directly inside the Share Flow
 */
@Composable
private fun InlineGroupCreationDialog(
    currentUserId: String,
    currentUserName: String,
    availableUsers: List<User>,
    onDismiss: () -> Unit,
    onGroupCreated: (groupId: String, groupName: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    val selectedMemberIds = remember { mutableStateListOf<String>() }
    var userSearchQuery by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    val filteredUsers = remember(userSearchQuery, availableUsers) {
        if (userSearchQuery.isBlank()) availableUsers
        else availableUsers.filter { it.username.contains(userSearchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New Group",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Group Name Input
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name *") },
                    placeholder = { Text("e.g. Creators Club") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Add Members (${selectedMemberIds.size} selected)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Member search
                OutlinedTextField(
                    value = userSearchQuery,
                    onValueChange = { userSearchQuery = it },
                    placeholder = { Text("Search friends...", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Members List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                ) {
                    items(filteredUsers, key = { it.id }) { user ->
                        val isSelected = selectedMemberIds.contains(user.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) selectedMemberIds.remove(user.id)
                                    else selectedMemberIds.add(user.id)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.LightGray)
                            ) {
                                AsyncImage(
                                    model = user.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(
                                text = user.username,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) selectedMemberIds.add(user.id)
                                    else selectedMemberIds.remove(user.id)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (groupName.isBlank()) {
                            Toast.makeText(context, "Please enter a group name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isCreating = true
                        scope.launch {
                            try {
                                val avatarSeed = groupName.replace(" ", "")
                                val avatarUrl = "https://api.dicebear.com/7.x/identicon/png?seed=$avatarSeed"
                                val newGroupId = GroupChatRepository.createGroup(
                                    name = groupName.trim(),
                                    description = groupDescription.trim(),
                                    avatarUrl = avatarUrl,
                                    memberIds = selectedMemberIds.toList(),
                                    creatorId = currentUserId,
                                    creatorName = currentUserName
                                )
                                onGroupCreated(newGroupId, groupName.trim())
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error creating group: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isCreating = false
                            }
                        }
                    },
                    enabled = !isCreating && groupName.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text("Create Group", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Universal helper to record share count on posts in Firestore
 */
private fun recordShareCount(contentId: String) {
    if (contentId.isBlank()) return
    try {
        val cleanId = contentId.replace("reels://", "").replace("story://", "").replace("post://", "").trim()
        RepositoryManager.firestore.collection("posts")
            .document(cleanId)
            .update("sharesCount", FieldValue.increment(1))
    } catch (e: Exception) {
        // Silently ignore if post doc doesn't exist
    }
}

/**
 * Backward-compatible bridge function for existing VideoFeedScreen / HomeFeedScreen calls
 */
@Composable
fun ShareBottomSheet(
    video: Video? = null,
    storyId: String? = null,
    onDismiss: () -> Unit,
    onAddToStory: (Video) -> Unit = {}
) {
    val payload = remember(video, storyId) {
        when {
            video != null -> SharePayload.fromVideo(video)
            !storyId.isNullOrBlank() -> SharePayload.fromStory(storyId)
            else -> SharePayload(contentId = "unknown", caption = "Check this out on Social India!")
        }
    }

    ModernShareBottomSheet(
        payload = payload,
        onDismiss = onDismiss,
        onAddToStory = {
            if (video != null) {
                onAddToStory(video)
            }
        }
    )
}
