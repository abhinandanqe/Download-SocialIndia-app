package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.FirebasePost
import com.example.model.RepositoryManager
import com.example.model.StoryHighlight
import com.example.model.StoryUiModel
import com.example.repository.FlagshipFeaturesRepository
import com.example.utils.ExoPlayerUtils
import com.example.utils.MediaResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private data class ArchivedStoryItem(
    val story: StoryUiModel,
    val createdAtMs: Long,
    val dateBadge: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveAndDeletedScreen(
    currentUserId: String,
    initialTab: Int = 0, // 0: Stories Archive, 1: Posts Archive, 2: Recently Deleted
    onNavigateBack: () -> Unit,
    onOpenPost: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var selectedPostForAction by remember { mutableStateOf<FirebasePost?>(null) }
    var actionType by remember { mutableStateOf("") } // "unarchive", "restore_or_delete"

    // Stories Archive state
    var archivedStories by remember { mutableStateOf<List<ArchivedStoryItem>>(emptyList()) }
    var isLoadingStories by remember { mutableStateOf(true) }
    var previewStoryIndex by remember { mutableStateOf<Int?>(null) }
    var storyForHighlight by remember { mutableStateOf<ArchivedStoryItem?>(null) }
    var showCreateHighlightWithStoryId by remember { mutableStateOf<String?>(null) }

    val userHighlights by RepositoryManager.postRepository.listenUserHighlights(currentUserId).collectAsState(initial = emptyList())
    val archivedPosts by FlagshipFeaturesRepository.listenArchivedPosts(currentUserId).collectAsState(initial = emptyList())
    val recentlyDeletedPosts by FlagshipFeaturesRepository.listenRecentlyDeletedPosts(currentUserId).collectAsState(initial = emptyList())

    val dateFormatter = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isBlank()) {
            isLoadingStories = false
            return@LaunchedEffect
        }
        isLoadingStories = true
        try {
            val loaded = withContext(Dispatchers.IO) {
                val snap = RepositoryManager.firestore.collection("stories")
                    .whereEqualTo("userId", currentUserId)
                    .get()
                    .await()
                snap.documents.mapNotNull { doc ->
                    val imageUrl = doc.getString("imageUrl").orEmpty()
                    val isLive = doc.getBoolean("isLive") ?: false
                    if (isLive || imageUrl.isBlank()) return@mapNotNull null
                    val tsMs = doc.getTimestamp("timestamp")?.toDate()?.time
                        ?: doc.getLong("timestamp")
                        ?: doc.getLong("createdAt")
                        ?: System.currentTimeMillis()
                    val badge = dateFormatter.format(Date(tsMs))
                    ArchivedStoryItem(
                        story = StoryUiModel(
                            id = doc.id,
                            userId = currentUserId,
                            username = doc.getString("username") ?: "You",
                            avatarUrl = doc.getString("avatarUrl") ?: "",
                            imageUrl = imageUrl,
                            textOverlay = doc.getString("textOverlay") ?: "",
                            timeAgo = badge
                        ),
                        createdAtMs = tsMs,
                        dateBadge = badge
                    )
                }.sortedByDescending { it.createdAtMs }
            }
            archivedStories = loaded
        } catch (e: Exception) {
            android.util.Log.w("ArchiveScreen", "Story archive fetch failed: ${e.message}")
        } finally {
            isLoadingStories = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "Stories Archive"
                            1 -> "Posts Archive"
                            else -> "Recently Deleted"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = Color(0xFFFF2A5F),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFFFF2A5F)
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Stories (${archivedStories.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Posts (${archivedPosts.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Deleted (${recentlyDeletedPosts.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // STORIES ARCHIVE TAB
                    if (isLoadingStories) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFFFF2A5F))
                        }
                    } else if (archivedStories.isEmpty()) {
                        ArchiveEmptyState(
                            icon = Icons.Outlined.History,
                            tint = Color(0xFFFF2A5F),
                            title = "No Archived Stories Yet",
                            subtitle = "Every story you share is automatically archived here after 24 hours so you can re-watch or save it to Profile Highlights."
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(archivedStories, key = { _, item -> item.story.id }) { idx, item ->
                                val poster = remember(item.story.imageUrl) {
                                    MediaResolver.resolveVideoPosterUrl(item.story.imageUrl, item.story.imageUrl)
                                }
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(0.68f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { previewStoryIndex = idx }
                                ) {
                                    AsyncImage(
                                        model = ExoPlayerUtils.buildFastPosterRequest(context, poster, 300, 420),
                                        contentDescription = "Archived Story",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                                )
                                            )
                                    )
                                    // Date pill top-left
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.65f),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = item.dateBadge,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    // 1-tap Add to Highlight button bottom-right
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFFF2A5F),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .size(28.dp)
                                            .clickable { storyForHighlight = item }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.FavoriteBorder,
                                                contentDescription = "Add to Highlight",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // POSTS ARCHIVE TAB
                    if (archivedPosts.isEmpty()) {
                        ArchiveEmptyState(
                            icon = Icons.Outlined.Archive,
                            tint = MaterialTheme.colorScheme.primary,
                            title = "No Archived Posts",
                            subtitle = "Posts you archive are hidden from your profile, and you can restore them anytime."
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(archivedPosts, key = { index, post -> "${post.id}_$index" }) { _, post ->
                                val poster = remember(post.postUrl, post.thumbnailUrl) {
                                    MediaResolver.resolveVideoPosterUrl(post.postUrl, post.thumbnailUrl)
                                        .ifBlank { post.postUrl }
                                }
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            selectedPostForAction = post
                                            actionType = "unarchive"
                                        }
                                ) {
                                    AsyncImage(
                                        model = ExoPlayerUtils.buildFastPosterRequest(context, poster, 300, 300),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Archive, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // RECENTLY DELETED TAB
                    if (recentlyDeletedPosts.isEmpty()) {
                        ArchiveEmptyState(
                            icon = Icons.Outlined.DeleteOutline,
                            tint = MaterialTheme.colorScheme.error,
                            title = "No Recently Deleted Content",
                            subtitle = "Items deleted in the last 30 days appear here. After 30 days, they're permanently deleted."
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(recentlyDeletedPosts, key = { index, post -> "${post.id}_$index" }) { _, post ->
                                val deletedAt = post.deletedAt ?: System.currentTimeMillis()
                                val daysPassed = ((System.currentTimeMillis() - deletedAt) / (1000 * 60 * 60 * 24)).toInt()
                                val daysRemaining = maxOf(0, 30 - daysPassed)
                                val poster = remember(post.postUrl, post.thumbnailUrl) {
                                    MediaResolver.resolveVideoPosterUrl(post.postUrl, post.thumbnailUrl)
                                        .ifBlank { post.postUrl }
                                }

                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            selectedPostForAction = post
                                            actionType = "restore_or_delete"
                                        }
                                ) {
                                    AsyncImage(
                                        model = ExoPlayerUtils.buildFastPosterRequest(context, poster, 300, 300),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(topStart = 8.dp),
                                        modifier = Modifier.align(Alignment.BottomEnd)
                                    ) {
                                        Text(
                                            text = "${daysRemaining}d left",
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

    // Full-screen Archived Story Preview
    previewStoryIndex?.let { idx ->
        if (archivedStories.isNotEmpty()) {
            StoryPlayerOverlay(
                stories = archivedStories.map { it.story },
                initialIndex = idx.coerceIn(0, archivedStories.lastIndex),
                onDismiss = { previewStoryIndex = null }
            )
        }
    }

    // Add Archived Story to Highlight Sheet
    storyForHighlight?.let { target ->
        AddArchivedStoryToHighlightSheet(
            highlights = userHighlights,
            onDismiss = { storyForHighlight = null },
            onSelectExisting = { highlight ->
                scope.launch {
                    val updatedIds = (highlight.storyIds + target.story.id).distinct()
                    RepositoryManager.postRepository.updateHighlight(
                        highlightId = highlight.id,
                        title = highlight.title,
                        coverUrl = highlight.coverUrl.ifBlank { target.story.imageUrl },
                        storyIds = updatedIds
                    )
                    Toast.makeText(context, "Added to \"${highlight.title}\" Highlight!", Toast.LENGTH_SHORT).show()
                    storyForHighlight = null
                }
            },
            onCreateNew = {
                showCreateHighlightWithStoryId = target.story.id
                storyForHighlight = null
            }
        )
    }

    showCreateHighlightWithStoryId?.let { preStoryId ->
        CreateEditHighlightDialog(
            userId = currentUserId,
            existingHighlight = null,
            preselectedStoryId = preStoryId,
            onDismiss = { showCreateHighlightWithStoryId = null },
            onSaved = { showCreateHighlightWithStoryId = null }
        )
    }

    // Unarchive Dialog
    if (selectedPostForAction != null && actionType == "unarchive") {
        AlertDialog(
            onDismissRequest = { selectedPostForAction = null },
            title = { Text("Archived Post", fontWeight = FontWeight.Bold) },
            text = { Text("Show this post on your profile again?") },
            confirmButton = {
                Button(
                    onClick = {
                        val p = selectedPostForAction!!
                        scope.launch {
                            FlagshipFeaturesRepository.setPostArchive(p.id, false)
                            Toast.makeText(context, "Restored to profile", Toast.LENGTH_SHORT).show()
                            selectedPostForAction = null
                        }
                    }
                ) {
                    Text("Show on Profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPostForAction = null }) { Text("Cancel") }
            }
        )
    }

    // Restore or Permanently Delete Dialog
    if (selectedPostForAction != null && actionType == "restore_or_delete") {
        val p = selectedPostForAction!!
        AlertDialog(
            onDismissRequest = { selectedPostForAction = null },
            title = { Text("Manage Deleted Post", fontWeight = FontWeight.Bold) },
            text = { Text("You can restore this post to your profile or permanently delete it right now.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            FlagshipFeaturesRepository.restorePost(p.id)
                            Toast.makeText(context, "Post restored to profile", Toast.LENGTH_SHORT).show()
                            selectedPostForAction = null
                        }
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            FlagshipFeaturesRepository.permanentDeletePost(p.id)
                            Toast.makeText(context, "Post permanently deleted", Toast.LENGTH_SHORT).show()
                            selectedPostForAction = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
                }
            }
        )
    }
}

@Composable
private fun ArchiveEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddArchivedStoryToHighlightSheet(
    highlights: List<StoryHighlight>,
    onDismiss: () -> Unit,
    onSelectExisting: (StoryHighlight) -> Unit,
    onCreateNew: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Add Story to Highlight", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFF2A5F).copy(alpha = 0.12f))
                    .clickable { onCreateNew() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color(0xFFFF2A5F))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Create New Highlight", fontWeight = FontWeight.Bold, color = Color(0xFFFF2A5F))
            }

            highlights.forEach { h ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectExisting(h) }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(h.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("${h.storyIds.size} stories", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
