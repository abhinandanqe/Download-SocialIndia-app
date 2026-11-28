package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.RepositoryManager
import com.example.model.StoryHighlight
import com.example.model.StoryUiModel
import com.example.utils.ExoPlayerUtils
import com.example.utils.MediaResolver
import com.example.utils.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoryHighlightsTray(
    userId: String,
    isCurrentUser: Boolean,
    onHighlightClick: (StoryHighlight) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val highlightsFlow = remember(userId) {
        if (userId.isNotBlank()) {
            RepositoryManager.postRepository.listenUserHighlights(userId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }
    val highlights by highlightsFlow.collectAsState(initial = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }
    var highlightForOptions by remember { mutableStateOf<StoryHighlight?>(null) }
    var highlightToEdit by remember { mutableStateOf<StoryHighlight?>(null) }
    var highlightToDelete by remember { mutableStateOf<StoryHighlight?>(null) }

    if (highlights.isEmpty() && !isCurrentUser) {
        return
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCurrentUser) {
                item(key = "new_highlight_btn") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(70.dp)
                            .bounceClick()
                            .clickable { showCreateDialog = true }
                            .testTag("new_highlight_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), CircleShape)
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Highlight",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "New",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            items(highlights, key = { it.id }) { highlight ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(70.dp)
                        .bounceClick()
                        .combinedClickable(
                            onClick = { onHighlightClick(highlight) },
                            onLongClick = {
                                if (isCurrentUser) {
                                    highlightForOptions = highlight
                                }
                            }
                        )
                ) {
                    Box(modifier = Modifier.size(66.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(
                                    2.dp,
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFFFF2A5F),
                                            Color(0xFFFF8A00),
                                            Color(0xFFFFD54F)
                                        )
                                    ),
                                    CircleShape
                                )
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val coverUrl = remember(highlight.coverUrl) {
                                MediaResolver.resolveVideoPosterUrl(highlight.coverUrl, highlight.coverUrl)
                            }
                            if (coverUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ExoPlayerUtils.buildFastPosterRequest(context, coverUrl, 180, 180),
                                    contentDescription = highlight.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(listOf(Color(0xFF4A154B), Color(0xFFFF2A5F)))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        if (highlight.storyIds.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.background),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${highlight.storyIds.size}",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = highlight.title.ifEmpty { "Highlight" },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateEditHighlightDialog(
            userId = userId,
            existingHighlight = null,
            onDismiss = { showCreateDialog = false },
            onSaved = { showCreateDialog = false }
        )
    }

    highlightToEdit?.let { editing ->
        CreateEditHighlightDialog(
            userId = userId,
            existingHighlight = editing,
            onDismiss = { highlightToEdit = null },
            onSaved = { highlightToEdit = null }
        )
    }

    highlightForOptions?.let { highlight ->
        HighlightOptionsBottomSheet(
            highlight = highlight,
            onDismiss = { highlightForOptions = null },
            onEdit = {
                highlightToEdit = highlight
                highlightForOptions = null
            },
            onDelete = {
                highlightToDelete = highlight
                highlightForOptions = null
            }
        )
    }

    highlightToDelete?.let { h ->
        AlertDialog(
            onDismissRequest = { highlightToDelete = null },
            title = { Text("Delete Highlight?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${h.title}\"? Your stories remain safely in your Stories Archive.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                RepositoryManager.postRepository.deleteHighlight(h.id)
                                Toast.makeText(context, "Highlight deleted", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "Failed to delete highlight", Toast.LENGTH_SHORT).show()
                            } finally {
                                highlightToDelete = null
                            }
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { highlightToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightOptionsBottomSheet(
    highlight: StoryHighlight,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = highlight.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "${highlight.storyIds.size} stories saved in this highlight",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onEdit() }
                    .padding(vertical = 13.dp, horizontal = 4.dp)
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(14.dp))
                Text("Edit Highlight & Cover", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onDelete() }
                    .padding(vertical = 13.dp, horizontal = 4.dp)
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(14.dp))
                Text("Delete Highlight", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun CreateEditHighlightDialog(
    userId: String,
    existingHighlight: StoryHighlight? = null,
    preselectedStoryId: String? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(existingHighlight?.title ?: "") }
    var selectedStoryIds by remember {
        mutableStateOf(
            buildSet {
                existingHighlight?.storyIds?.forEach { add(it) }
                if (!preselectedStoryId.isNullOrBlank()) add(preselectedStoryId)
            }
        )
    }
    var customCoverUrl by remember { mutableStateOf(existingHighlight?.coverUrl.orEmpty()) }
    var availableStories by remember { mutableStateOf<List<StoryUiModel>>(emptyList()) }
    var isLoadingStories by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        isLoadingStories = true
        try {
            val list = withContext(Dispatchers.IO) {
                val snap = RepositoryManager.firestore.collection("stories")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                snap.documents.mapNotNull { doc ->
                    val imageUrl = doc.getString("imageUrl") ?: ""
                    val isLive = doc.getBoolean("isLive") ?: false
                    if (isLive || imageUrl.isBlank()) return@mapNotNull null
                    StoryUiModel(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        username = doc.getString("username") ?: "Me",
                        avatarUrl = doc.getString("avatarUrl") ?: "",
                        imageUrl = imageUrl,
                        textOverlay = doc.getString("textOverlay") ?: "",
                        timeAgo = "Archived"
                    )
                }
            }
            availableStories = list
            if (customCoverUrl.isBlank()) {
                customCoverUrl = list.firstOrNull { selectedStoryIds.contains(it.id) }?.imageUrl.orEmpty()
            }
        } catch (e: Exception) {
            android.util.Log.e("HighlightDialog", "Error fetching user stories", e)
        } finally {
            isLoadingStories = false
        }
    }

    val selectedStories = remember(availableStories, selectedStoryIds) {
        availableStories.filter { selectedStoryIds.contains(it.id) }
    }
    val previewCover = customCoverUrl.ifBlank { selectedStories.firstOrNull()?.imageUrl.orEmpty() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                    Text(
                        text = if (existingHighlight == null) "New Highlight" else "Edit Highlight",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                Toast.makeText(context, "Please enter a highlight name", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (selectedStoryIds.isEmpty()) {
                                Toast.makeText(context, "Select at least 1 story from archive", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSaving = true
                            scope.launch {
                                try {
                                    val cover = previewCover
                                    if (existingHighlight == null) {
                                        RepositoryManager.postRepository.createHighlight(
                                            userId = userId,
                                            title = title.trim(),
                                            coverUrl = cover,
                                            storyIds = selectedStoryIds.toList()
                                        )
                                        Toast.makeText(context, "Highlight created!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        RepositoryManager.postRepository.updateHighlight(
                                            highlightId = existingHighlight.id,
                                            title = title.trim(),
                                            coverUrl = cover,
                                            storyIds = selectedStoryIds.toList()
                                        )
                                        Toast.makeText(context, "Highlight updated!", Toast.LENGTH_SHORT).show()
                                    }
                                    onSaved()
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Error saving highlight", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving && title.isNotBlank() && selectedStoryIds.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A5F)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(if (existingHighlight == null) "Create" else "Save", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                HorizontalDivider()

                // Live Circular Cover Preview + Title Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .border(
                                2.dp,
                                Brush.linearGradient(listOf(Color(0xFFFF2A5F), Color(0xFFFF8A00), Color(0xFFFFD54F))),
                                CircleShape
                            )
                            .padding(3.dp)
                    ) {
                        if (previewCover.isNotBlank()) {
                            AsyncImage(
                                model = ExoPlayerUtils.buildFastPosterRequest(context, previewCover, 200, 200),
                                contentDescription = "Cover Preview",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 24) title = it },
                        label = { Text("Highlight Name") },
                        placeholder = { Text("e.g. Travel, Events, Tips") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }

                // Cover Selector Strip when multiple stories selected
                if (selectedStories.size > 1) {
                    Text(
                        text = "Choose Cover Frame",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(selectedStories, key = { "cover_${it.id}" }) { st ->
                            val isCover = previewCover == st.imageUrl
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isCover) 2.5.dp else 1.dp,
                                        color = if (isCover) Color(0xFFFF2A5F) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                                    .clickable { customCoverUrl = st.imageUrl }
                            ) {
                                AsyncImage(
                                    model = ExoPlayerUtils.buildFastPosterRequest(context, st.imageUrl, 160, 160),
                                    contentDescription = "Select Cover",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Select Stories from Archive (${selectedStoryIds.size} selected)",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (isLoadingStories) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFF2A5F))
                    }
                } else if (availableStories.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                            Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("No stories in your archive yet", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Post a story first and it will automatically appear here to save in Highlights.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(availableStories, key = { it.id }) { storyItem ->
                            val isSelected = selectedStoryIds.contains(storyItem.id)
                            val posterUrl = remember(storyItem.imageUrl) {
                                MediaResolver.resolveVideoPosterUrl(storyItem.imageUrl, storyItem.imageUrl)
                            }
                            Box(
                                modifier = Modifier
                                    .aspectRatio(0.72f)
                                    .clickable {
                                        selectedStoryIds = if (isSelected) {
                                            selectedStoryIds - storyItem.id
                                        } else {
                                            selectedStoryIds + storyItem.id
                                        }
                                        if (!isSelected && customCoverUrl.isBlank()) {
                                            customCoverUrl = storyItem.imageUrl
                                        }
                                    }
                            ) {
                                AsyncImage(
                                    model = ExoPlayerUtils.buildFastPosterRequest(context, posterUrl, 280, 380),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f))
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFFFF2A5F) else Color.Black.copy(alpha = 0.45f))
                                        .border(1.5.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
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
}

@Composable
fun HighlightViewerDialog(
    highlight: StoryHighlight,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var stories by remember { mutableStateOf<List<StoryUiModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(highlight.id) {
        if (highlight.storyIds.isEmpty()) {
            Toast.makeText(context, "No stories in this highlight", Toast.LENGTH_SHORT).show()
            onDismiss()
            return@LaunchedEffect
        }
        try {
            val loaded = withContext(Dispatchers.IO) {
                val list = mutableListOf<StoryUiModel>()
                highlight.storyIds.chunked(10).forEach { chunk ->
                    val snap = RepositoryManager.firestore.collection("stories")
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                        .get()
                        .await()
                    val byId = snap.documents.associateBy { it.id }
                    chunk.forEach { sid ->
                        val doc = byId[sid] ?: return@forEach
                        val imageUrl = doc.getString("imageUrl") ?: ""
                        if (imageUrl.isNotEmpty()) {
                            list.add(
                                StoryUiModel(
                                    id = doc.id,
                                    userId = doc.getString("userId") ?: "",
                                    username = highlight.title,
                                    avatarUrl = highlight.coverUrl,
                                    imageUrl = imageUrl,
                                    textOverlay = doc.getString("textOverlay") ?: "",
                                    timeAgo = "Highlight"
                                )
                            )
                        }
                    }
                }
                list
            }
            if (loaded.isEmpty()) {
                Toast.makeText(context, "Stories in this highlight are unavailable", Toast.LENGTH_SHORT).show()
                onDismiss()
            } else {
                stories = loaded
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Failed to load highlight", Toast.LENGTH_SHORT).show()
            onDismiss()
        } finally {
            isLoading = false
        }
    }

    if (stories.isNotEmpty()) {
        StoryPlayerOverlay(
            stories = stories,
            initialIndex = 0,
            onDismiss = onDismiss
        )
    } else if (isLoading) {
        Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
            }
        }
    }
}
