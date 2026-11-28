package com.example.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.api.ITunesTrack
import com.example.model.BroadcastChannel
import com.example.model.ChannelMember
import com.example.model.ChannelMessage
import com.example.model.RepositoryManager
import com.example.repository.FlagshipFeaturesRepository
import com.example.utils.GlobalAudioPreviewManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

val CHANNEL_EMOJI_REACTIONS = listOf("❤️", "🔥", "😂", "👏", "🎉", "😮")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastChannelsScreen(
    currentUserId: String,
    currentUsername: String,
    currentUserAvatar: String,
    onNavigateBack: () -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Your Channels, 1: Discover
    var showCreateDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    val allChannels by FlagshipFeaturesRepository.listenBroadcastChannels().collectAsStateWithLifecycle(initialValue = emptyList())

    // Filter channels based on search and category
    val filteredChannels = remember(allChannels, searchQuery, selectedCategoryFilter) {
        allChannels.filter { channel ->
            val matchesQuery = if (searchQuery.isBlank()) true else {
                channel.title.contains(searchQuery, ignoreCase = true) ||
                channel.username.contains(searchQuery, ignoreCase = true) ||
                channel.creatorUsername.contains(searchQuery, ignoreCase = true) ||
                channel.description.contains(searchQuery, ignoreCase = true)
            }
            val matchesCategory = if (selectedCategoryFilter == "All") true else {
                channel.category.equals(selectedCategoryFilter, ignoreCase = true)
            }
            matchesQuery && matchesCategory
        }
    }

    val myChannels = remember(filteredChannels, currentUserId) {
        filteredChannels.filter { it.creatorId == currentUserId }
    }
    val discoverChannels = remember(filteredChannels, currentUserId) {
        filteredChannels.filter { it.creatorId != currentUserId }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search channels, @handles...", fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("channel_search_bar"),
                            shape = RoundedCornerShape(20.dp),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            }
                        )
                    } else {
                        Text(
                            "Broadcast Channels",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                searchQuery = ""
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("create_channel_button")
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Create Channel", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            BoxWithConstraints {
                val isCompact = maxWidth < 360.dp
                if (isCompact) {
                    FloatingActionButton(
                        onClick = { showCreateDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .testTag("fab_create_channel")
                            .navigationBarsPadding()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Channel")
                    }
                } else {
                    ExtendedFloatingActionButton(
                        onClick = { showCreateDialog = true },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("New Channel", fontWeight = FontWeight.SemiBold) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .testTag("fab_create_channel")
                            .navigationBarsPadding()
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Category Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryFilter == "All",
                        onClick = { selectedCategoryFilter = "All" },
                        label = { Text("All", fontSize = 12.sp) }
                    )
                }
                items(BROADCAST_CHANNEL_CATEGORIES) { category ->
                    FilterChip(
                        selected = selectedCategoryFilter == category,
                        onClick = { selectedCategoryFilter = category },
                        label = { Text(category, fontSize = 12.sp) }
                    )
                }
            }

            // Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Your Channels", fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (myChannels.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("${myChannels.size}")
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Discover", fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (discoverChannels.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text("${discoverChannels.size}")
                                }
                            }
                        }
                    }
                )
            }

            val listToShow = if (selectedTab == 0) myChannels else discoverChannels

            if (listToShow.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Outlined.Podcasts else Icons.Outlined.Explore,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = if (selectedTab == 0) "No Channels Created Yet" else "No Channels Found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )

                        Text(
                            text = if (selectedTab == 0)
                                "Create a broadcast channel to share exclusive updates, polls, voice notes, and media with your followers."
                            else
                                "Explore public channels from your favorite creators across India.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        if (selectedTab == 0) {
                            Button(
                                onClick = { showCreateDialog = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Your First Channel")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(listToShow, key = { it.id }) { channel ->
                        ChannelListItemCard(
                            channel = channel,
                            currentUserId = currentUserId,
                            isCreator = channel.creatorId == currentUserId,
                            onClick = { onOpenChannel(channel.id) },
                            onJoin = {
                                scope.launch {
                                    FlagshipFeaturesRepository.joinChannel(channel.id, currentUserId)
                                    Toast.makeText(context, "Joined ${channel.title}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateBroadcastChannelDialog(
            currentUserId = currentUserId,
            currentUsername = currentUsername,
            currentUserAvatar = currentUserAvatar,
            onDismiss = { showCreateDialog = false },
            onChannelCreated = { newChannelId ->
                showCreateDialog = false
                onOpenChannel(newChannelId)
            }
        )
    }
}

@Composable
fun ChannelListItemCard(
    channel: BroadcastChannel,
    currentUserId: String,
    isCreator: Boolean,
    onClick: () -> Unit,
    onJoin: () -> Unit
) {
    val isMember by FlagshipFeaturesRepository.isChannelMember(channel.id, currentUserId)
        .collectAsStateWithLifecycle(initialValue = isCreator)
    val isFollowersOnly = channel.isFollowersOnly
    val isFollowingCreator by RepositoryManager.socialRepository
        .isFollowing(currentUserId, channel.creatorId)
        .collectAsStateWithLifecycle(initialValue = false)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("channel_item_${channel.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // LEFT: Channel Cover / Avatar
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (channel.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = channel.imageUrl,
                        contentDescription = "Channel Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Outlined.Podcasts,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // CENTER & INFO CONTENT
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                // Channel Title + Owner / Action on top right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = channel.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (channel.isVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = "Verified Channel",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // RIGHT: Owner/Admin badge or Join button
                    if (isCreator) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Owner",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else if (isMember) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Joined",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else if (isFollowersOnly && !isFollowingCreator) {
                        OutlinedButton(
                            onClick = onClick,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Follow to Join", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    } else {
                        Button(
                            onClick = onJoin,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Join", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }

                if (channel.username.isNotEmpty()) {
                    Text(
                        text = "@${channel.username}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                if (channel.description.isNotEmpty()) {
                    Text(
                        text = channel.description,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // BOTTOM/INFO ROW: Category | Public / Followers only | Member count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = channel.category.ifEmpty { "Creator" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (channel.isFollowersOnly) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = if (channel.isFollowersOnly) "Followers only" else "Public",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            color = if (channel.isFollowersOnly) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1
                        )
                    }

                    Text(
                        text = "${channel.membersCount} members",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Production-ready Channel Chat Screen (Channel Home)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastChannelChatScreen(
    channelId: String,
    currentUserId: String,
    currentUsername: String,
    currentUserAvatar: String,
    onNavigateBack: () -> Unit,
    onOpenPost: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var channel by remember { mutableStateOf<BroadcastChannel?>(null) }
    var textInput by remember { mutableStateOf("") }
    var showOptionsMenu by remember { mutableStateOf(false) }

    // Dialog & Sheet States
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAnalyticsDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showMusicDialog by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var fullscreenMediaUrl by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // (url, isVideo)

    val messages by FlagshipFeaturesRepository.listenChannelMessages(channelId).collectAsStateWithLifecycle(initialValue = emptyList())
    val membership by FlagshipFeaturesRepository.listenChannelMembership(channelId, currentUserId).collectAsStateWithLifecycle(initialValue = null)

    // Load channel details
    LaunchedEffect(channelId) {
        FlagshipFeaturesRepository.listenBroadcastChannels().collect { list ->
            channel = list.find { it.id == channelId }
        }
    }

    // Mark channel as read when opened
    LaunchedEffect(channelId, currentUserId) {
        FlagshipFeaturesRepository.markChannelAsRead(channelId, currentUserId)
    }

    val isOwner = channel?.creatorId == currentUserId
    val isMember = membership != null || isOwner
    val isMuted = membership?.isMuted == true
    val isFollowersOnly = channel?.isFollowersOnly == true

    val creatorId = channel?.creatorId.orEmpty()
    val isFollowingCreator by RepositoryManager.socialRepository
        .isFollowing(currentUserId, creatorId)
        .collectAsStateWithLifecycle(initialValue = false)

    var isRelationshipBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(creatorId, currentUserId) {
        if (creatorId.isNotEmpty() && currentUserId.isNotEmpty() && creatorId != currentUserId) {
            isRelationshipBlocked = FlagshipFeaturesRepository.isRelationshipBlocked(currentUserId, creatorId)
        }
    }

    var isFollowLoading by remember { mutableStateOf(false) }
    var isJoinLoading by remember { mutableStateOf(false) }
    val isFollowRequired = channel != null && !isOwner && isFollowersOnly && !isFollowingCreator

    // Image Picker for creator broadcast
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                Toast.makeText(context, "Uploading image...", Toast.LENGTH_SHORT).show()
                val url = RepositoryManager.mediaRepository.uploadMedia(uri)
                if (!url.isNullOrEmpty()) {
                    val msg = ChannelMessage(
                        channelId = channelId,
                        senderId = currentUserId,
                        senderUsername = currentUsername,
                        senderAvatarUrl = currentUserAvatar,
                        text = "",
                        mediaUrl = url,
                        mediaType = "photo",
                        timestamp = System.currentTimeMillis()
                    )
                    FlagshipFeaturesRepository.sendChannelMessage(msg)
                } else {
                    Toast.makeText(context, "Image upload failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Video Picker for creator broadcast
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                Toast.makeText(context, "Uploading video...", Toast.LENGTH_SHORT).show()
                val url = RepositoryManager.mediaRepository.uploadMedia(uri)
                if (!url.isNullOrEmpty()) {
                    val msg = ChannelMessage(
                        channelId = channelId,
                        senderId = currentUserId,
                        senderUsername = currentUsername,
                        senderAvatarUrl = currentUserAvatar,
                        text = "",
                        mediaUrl = url,
                        mediaType = "video",
                        timestamp = System.currentTimeMillis()
                    )
                    FlagshipFeaturesRepository.sendChannelMessage(msg)
                } else {
                    Toast.makeText(context, "Video upload failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            if (isOwner) showSettingsDialog = true
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (channel?.imageUrl?.isNotEmpty() == true) {
                                AsyncImage(
                                    model = channel?.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Outlined.Podcasts, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = channel?.title ?: "Broadcast Channel",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (channel?.isVerified == true) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${channel?.membersCount ?: 1} members",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isFollowersOnly) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = if (isFollowersOnly) "Followers only" else "Public",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        color = if (isFollowersOnly) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                if (isMember) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = if (isOwner) "Owner" else "Member",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Mute / Unmute Quick Action
                    if (isMember && !isOwner) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val newMuted = !isMuted
                                    FlagshipFeaturesRepository.toggleChannelMute(channelId, currentUserId, newMuted)
                                    Toast.makeText(context, if (newMuted) "Notifications muted" else "Notifications turned on", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                if (isMuted) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
                                contentDescription = "Toggle Notifications",
                                tint = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Share Channel
                    IconButton(onClick = { showShareDialog = true }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share Channel")
                    }

                    // Options Menu
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) {
                        if (isOwner) {
                            DropdownMenuItem(
                                text = { Text("Channel Settings") },
                                onClick = {
                                    showOptionsMenu = false
                                    showSettingsDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Analytics") },
                                onClick = {
                                    showOptionsMenu = false
                                    showAnalyticsDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Insights, contentDescription = null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete Channel", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showOptionsMenu = false
                                    showDeleteConfirmDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        } else {
                            if (isMember) {
                                DropdownMenuItem(
                                    text = { Text(if (isMuted) "Unmute Channel" else "Mute Channel") },
                                    onClick = {
                                        showOptionsMenu = false
                                        scope.launch {
                                            FlagshipFeaturesRepository.toggleChannelMute(channelId, currentUserId, !isMuted)
                                        }
                                    },
                                    leadingIcon = { Icon(if (isMuted) Icons.Default.Notifications else Icons.Default.NotificationsOff, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Leave Channel") },
                                    onClick = {
                                        showOptionsMenu = false
                                        scope.launch {
                                            FlagshipFeaturesRepository.leaveChannel(channelId, currentUserId)
                                            Toast.makeText(context, "Left channel", Toast.LENGTH_SHORT).show()
                                            onNavigateBack()
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Report Channel") },
                                onClick = {
                                    showOptionsMenu = false
                                    scope.launch {
                                        FlagshipFeaturesRepository.reportChannel(channelId, "Inappropriate channel content", currentUserId)
                                        Toast.makeText(context, "Report submitted. Thank you.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.ReportProblem, contentDescription = null) }
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isRelationshipBlocked) {
            BroadcastChannelBlockedView(
                onBack = onNavigateBack,
                modifier = Modifier.padding(paddingValues)
            )
        } else if (isFollowRequired && channel != null) {
            val activeChannel = channel ?: return@Scaffold
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                FollowersOnlyRequiredCard(
                    channel = activeChannel,
                    isFollowing = isFollowingCreator,
                    isFollowLoading = isFollowLoading,
                    onFollowCreator = {
                        scope.launch {
                            isFollowLoading = true
                            try {
                                RepositoryManager.socialRepository.followUser(currentUserId, activeChannel.creatorId)
                                Toast.makeText(context, "Now following @${activeChannel.creatorUsername}", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {}
                            isFollowLoading = false
                        }
                    },
                    onJoinChannel = {
                        scope.launch {
                            isJoinLoading = true
                            try {
                                val success = FlagshipFeaturesRepository.joinChannel(channelId, currentUserId)
                                if (success) {
                                    Toast.makeText(context, "Joined ${activeChannel.title}!", Toast.LENGTH_SHORT).show()
                                }
                            } catch (_: Exception) {}
                            isJoinLoading = false
                        }
                    }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Pinned Update Banner if any
            if (!channel?.pinnedMessageText.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pinned: ${channel?.pinnedMessageText}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isOwner) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        FlagshipFeaturesRepository.unpinChannelMessage(channelId)
                                        Toast.makeText(context, "Unpinned update", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Unpin", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // Updates Feed
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    // View count tracking
                    LaunchedEffect(message.id) {
                        FlagshipFeaturesRepository.recordMessageView(channelId, message.id)
                    }

                    ChannelMessageBubble(
                        message = message,
                        channelId = channelId,
                        currentUserId = currentUserId,
                        isOwner = isOwner,
                        onVotePoll = { optionIdx ->
                            scope.launch {
                                FlagshipFeaturesRepository.voteChannelPoll(channelId, message.id, currentUserId, optionIdx)
                            }
                        },
                        onToggleReaction = { emoji ->
                            scope.launch {
                                FlagshipFeaturesRepository.toggleChannelMessageReaction(channelId, message.id, currentUserId, emoji)
                            }
                        },
                        onPin = {
                            scope.launch {
                                val snippet = message.text.ifBlank { message.pollQuestion ?: "Media update" }
                                FlagshipFeaturesRepository.pinChannelMessage(channelId, message.id, snippet)
                                Toast.makeText(context, "Pinned message", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = {
                            scope.launch {
                                FlagshipFeaturesRepository.deleteChannelMessage(channelId, message.id)
                                Toast.makeText(context, "Deleted message", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenPost = onOpenPost,
                        onOpenFullscreenMedia = { url, isVideo ->
                            fullscreenMediaUrl = Pair(url, isVideo)
                        }
                    )
                }
            }

            // Bottom Bar: Creator broadcast options vs Member join bar
            if (isOwner) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Quick Attachment Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Photo
                            IconButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Icon(Icons.Outlined.PhotoCamera, contentDescription = "Photo", tint = MaterialTheme.colorScheme.primary)
                            }
                            // Video
                            IconButton(onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }) {
                                Icon(Icons.Outlined.Videocam, contentDescription = "Video", tint = MaterialTheme.colorScheme.primary)
                            }
                            // Voice
                            IconButton(onClick = { showVoiceDialog = true }) {
                                Icon(Icons.Outlined.Mic, contentDescription = "Voice Note", tint = MaterialTheme.colorScheme.primary)
                            }
                            // Music
                            IconButton(onClick = { showMusicDialog = true }) {
                                Icon(Icons.Outlined.MusicNote, contentDescription = "Music", tint = MaterialTheme.colorScheme.primary)
                            }
                            // Poll / Quiz
                            IconButton(onClick = { showPollDialog = true }) {
                                Icon(Icons.Outlined.Poll, contentDescription = "Poll / Quiz", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // Text Broadcast Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                placeholder = { Text("Broadcast an update to members...", fontSize = 14.sp) },
                                modifier = Modifier.weight(1f).testTag("channel_input_field"),
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    if (textInput.isNotBlank()) {
                                        val newMsg = ChannelMessage(
                                            channelId = channelId,
                                            senderId = currentUserId,
                                            senderUsername = currentUsername,
                                            senderAvatarUrl = currentUserAvatar,
                                            text = textInput.trim(),
                                            timestamp = System.currentTimeMillis()
                                        )
                                        scope.launch {
                                            FlagshipFeaturesRepository.sendChannelMessage(newMsg)
                                            textInput = ""
                                            listState.animateScrollToItem(messages.size.coerceAtLeast(1) - 1)
                                        }
                                    }
                                },
                                enabled = textInput.isNotBlank(),
                                modifier = Modifier.testTag("send_broadcast_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Broadcast",
                                    tint = if (textInput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isMember) "Only the channel creator can send updates" else "Join to receive updates & vote on polls",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isMember) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val success = FlagshipFeaturesRepository.joinChannel(channelId, currentUserId)
                                        if (success) {
                                            Toast.makeText(context, "Joined channel!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Cannot join: Must follow creator first", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Join Channel", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

    // Settings Dialog
    val currentChannelForDialogs = channel
    if (showSettingsDialog && currentChannelForDialogs != null) {
        BroadcastChannelSettingsDialog(
            channel = currentChannelForDialogs,
            onDismiss = { showSettingsDialog = false },
            onUpdated = {
                // refreshed via listener
            }
        )
    }

    // Analytics Dialog
    if (showAnalyticsDialog) {
        BroadcastChannelAnalyticsSheet(
            channelId = channelId,
            onDismiss = { showAnalyticsDialog = false }
        )
    }

    // Share Dialog
    if (showShareDialog && currentChannelForDialogs != null) {
        BroadcastChannelShareDialog(
            channel = currentChannelForDialogs,
            onDismiss = { showShareDialog = false }
        )
    }

    // Voice Recorder Dialog
    if (showVoiceDialog) {
        BroadcastChannelVoiceDialog(
            onDismiss = { showVoiceDialog = false },
            onSendVoice = { url, duration ->
                showVoiceDialog = false
                scope.launch {
                    val voiceMsg = ChannelMessage(
                        channelId = channelId,
                        senderId = currentUserId,
                        senderUsername = currentUsername,
                        senderAvatarUrl = currentUserAvatar,
                        mediaUrl = url,
                        mediaType = "voice",
                        voiceDurationSeconds = duration,
                        timestamp = System.currentTimeMillis()
                    )
                    FlagshipFeaturesRepository.sendChannelMessage(voiceMsg)
                }
            }
        )
    }

    // Music Picker Dialog
    if (showMusicDialog) {
        BroadcastChannelMusicPickerDialog(
            onDismiss = { showMusicDialog = false },
            onSelectTrack = { track: ITunesTrack ->
                showMusicDialog = false
                scope.launch {
                    val musicMsg = ChannelMessage(
                        channelId = channelId,
                        senderId = currentUserId,
                        senderUsername = currentUsername,
                        senderAvatarUrl = currentUserAvatar,
                        mediaType = "music",
                        musicTrackId = track.id?.toString() ?: "",
                        musicTrackName = track.name ?: "Track",
                        musicTrackArtist = track.artistName ?: "Artist",
                        musicTrackArtwork = track.artworkUrl ?: "",
                        musicTrackUrl = track.previewUrl ?: "",
                        timestamp = System.currentTimeMillis()
                    )
                    FlagshipFeaturesRepository.sendChannelMessage(musicMsg)
                }
            }
        )
    }

    // Poll or Quiz Dialog
    if (showPollDialog) {
        BroadcastChannelPollOrQuizDialog(
            onDismiss = { showPollDialog = false },
            onCreate = { question, options, isQuiz, correctIndex ->
                showPollDialog = false
                scope.launch {
                    val pollMsg = ChannelMessage(
                        channelId = channelId,
                        senderId = currentUserId,
                        senderUsername = currentUsername,
                        senderAvatarUrl = currentUserAvatar,
                        text = if (isQuiz) "🧠 Channel Quiz" else "📊 Channel Poll",
                        pollQuestion = question,
                        pollOptions = options,
                        pollIsQuiz = isQuiz,
                        quizCorrectOptionIndex = correctIndex,
                        timestamp = System.currentTimeMillis()
                    )
                    FlagshipFeaturesRepository.sendChannelMessage(pollMsg)
                }
            }
        )
    }

    // Delete Channel Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Broadcast Channel?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete \"${channel?.title}\"? All updates and member history will be removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        scope.launch {
                            val deleted = FlagshipFeaturesRepository.deleteBroadcastChannel(channelId)
                            if (deleted) {
                                Toast.makeText(context, "Channel deleted", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            } else {
                                Toast.makeText(context, "Failed to delete channel", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Fullscreen Media Viewer
    fullscreenMediaUrl?.let { (url, isVideo) ->
        FullscreenMediaViewerDialog(
            mediaUrl = url,
            isVideo = isVideo,
            onDismiss = { fullscreenMediaUrl = null }
        )
    }
}

/**
 * Message Bubble Supporting 9 Update Types & Reactions
 */
@Composable
fun ChannelMessageBubble(
    message: ChannelMessage,
    channelId: String,
    currentUserId: String,
    isOwner: Boolean,
    onVotePoll: (Int) -> Unit,
    onToggleReaction: (String) -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenFullscreenMedia: (url: String, isVideo: Boolean) -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var isVoicePlaying by remember { mutableStateOf(false) }
    var isMusicPlaying by remember { mutableStateOf(false) }

    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    DisposableEffect(message.id) {
        onDispose {
            if (isMusicPlaying) {
                GlobalAudioPreviewManager.stop()
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Creator Header Info
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                if (message.senderAvatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = message.senderAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.senderUsername.ifBlank { "Creator" },
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = timeFormat.format(Date(message.timestamp)),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.MoreHoriz, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (isOwner) {
                    DropdownMenuItem(
                        text = { Text("Pin Update") },
                        onClick = { showMenu = false; onPin() },
                        leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Update") },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Report Update") },
                    onClick = {
                        showMenu = false
                        Toast.makeText(context, "Update reported. Thank you.", Toast.LENGTH_SHORT).show()
                    },
                    leadingIcon = { Icon(Icons.Default.ReportProblem, contentDescription = null) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Card Content
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // 1. Text Update
                if (message.text.isNotBlank() && message.pollQuestion.isNullOrBlank()) {
                    Text(
                        text = message.text,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 2. Photo Update
                if (message.mediaType == "photo" && !message.mediaUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onOpenFullscreenMedia(message.mediaUrl, false) },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = message.mediaUrl,
                            contentDescription = "Photo Update",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // 3. Video Update
                if (message.mediaType == "video" && !message.mediaUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .clickable { onOpenFullscreenMedia(message.mediaUrl, true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play Video", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                        Text(
                            text = "Tap to play video",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                        )
                    }
                }

                // 4. Voice Update
                if (message.mediaType == "voice" && !message.mediaUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (isVoicePlaying) {
                                        GlobalAudioPreviewManager.pause()
                                        isVoicePlaying = false
                                    } else {
                                        GlobalAudioPreviewManager.playOrToggle(context, message.mediaUrl)
                                        isVoicePlaying = true
                                    }
                                }
                            ) {
                                Icon(
                                    if (isVoicePlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                    contentDescription = "Play Voice Note",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Voice Broadcast", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    if (message.voiceDurationSeconds > 0) "${message.voiceDurationSeconds} seconds" else "Audio Note",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 5. Music Update (S45 Music API)
                if (message.mediaType == "music") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!message.musicTrackArtwork.isNullOrBlank()) {
                                AsyncImage(
                                    model = message.musicTrackArtwork,
                                    contentDescription = null,
                                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.musicTrackName ?: "Music Track",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = message.musicTrackArtist ?: "Artist",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!message.musicTrackUrl.isNullOrBlank()) {
                                IconButton(
                                    onClick = {
                                        if (isMusicPlaying) {
                                            GlobalAudioPreviewManager.pause()
                                            isMusicPlaying = false
                                        } else {
                                            GlobalAudioPreviewManager.playOrToggle(context, message.musicTrackUrl)
                                            isMusicPlaying = true
                                        }
                                    }
                                ) {
                                    Icon(
                                        if (isMusicPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                        contentDescription = "Play Music Preview",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Poll / Quiz Card
                if (!message.pollQuestion.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (message.pollIsQuiz) "🧠 Quiz" else "📊 Poll",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.pollQuestion,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val totalVotes = message.pollVotes.values.sum()
                    val userVote = message.votedUsers[currentUserId]
                    val hasVoted = userVote != null

                    message.pollOptions.forEachIndexed { index, option ->
                        val votesForOption = message.pollVotes[index.toString()] ?: 0
                        val percent = if (totalVotes > 0) (votesForOption.toFloat() / totalVotes.toFloat()) else 0f
                        val isSelected = userVote == index
                        val isCorrect = message.pollIsQuiz && message.quizCorrectOptionIndex == index

                        Surface(
                            onClick = { onVotePoll(index) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (hasVoted && isCorrect) Color(0xFFE8F5E9)
                            else if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (hasVoted && isCorrect) Color(0xFF4CAF50)
                                else if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (totalVotes > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(percent)
                                            .background(
                                                if (hasVoted && isCorrect) Color(0xFF81C784).copy(alpha = 0.25f)
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            )
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (hasVoted && isCorrect) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Correct", tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                        } else if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = "Voted", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = option,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected || (hasVoted && isCorrect)) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                    if (totalVotes > 0) {
                                        Text(
                                            text = "${(percent * 100).toInt()}%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (hasVoted && isCorrect) Color(0xFF388E3C) else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$totalVotes votes",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 7. Shared Post Card
                if (!message.sharedPostId.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        onClick = { onOpenPost(message.sharedPostId) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!message.sharedPostThumbnail.isNullOrBlank()) {
                                AsyncImage(
                                    model = message.sharedPostThumbnail,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column {
                                Text(
                                    text = "Post by @${message.sharedPostUsername ?: "Creator"}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                if (!message.sharedPostCaption.isNullOrBlank()) {
                                    Text(
                                        text = message.sharedPostCaption,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Row: Reactions and Views Count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reactions Bar
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Display active reactions
                        message.reactions.forEach { (emoji, count) ->
                            if (count > 0) {
                                val userChoseThis = message.userReactions[currentUserId] == emoji
                                Surface(
                                    onClick = { onToggleReaction(emoji) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (userChoseThis) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (userChoseThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                ) {
                                    Text(
                                        text = "$emoji $count",
                                        fontSize = 11.sp,
                                        fontWeight = if (userChoseThis) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // Add reaction button
                        Box {
                            IconButton(
                                onClick = { showReactionPicker = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Outlined.AddReaction, contentDescription = "React", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }

                            DropdownMenu(
                                expanded = showReactionPicker,
                                onDismissRequest = { showReactionPicker = false }
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                                    CHANNEL_EMOJI_REACTIONS.forEach { emoji ->
                                        IconButton(
                                            onClick = {
                                                showReactionPicker = false
                                                onToggleReaction(emoji)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Text(emoji, fontSize = 18.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Views Count
                    Text(
                        text = "👁 ${message.viewsCount.coerceAtLeast(1)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
