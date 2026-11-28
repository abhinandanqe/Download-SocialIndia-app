package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.SavedCollection
import com.example.model.SavedPostItem
import com.example.repository.FlagshipFeaturesRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedCollectionsScreen(
    currentUserId: String,
    onNavigateBack: () -> Unit,
    onOpenPost: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: All Saved, 1: Collections
    var selectedCollection by remember { mutableStateOf<SavedCollection?>(null) }
    var showCreateCollectionDialog by remember { mutableStateOf(false) }

    val savedPosts by FlagshipFeaturesRepository.listenSavedPosts(currentUserId, selectedCollection?.id).collectAsState(initial = emptyList())
    val collections by FlagshipFeaturesRepository.listenCollections(currentUserId).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedCollection?.name ?: "Saved",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedCollection != null) {
                                selectedCollection = null
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
                    val activeCollection = selectedCollection
                    if (activeCollection == null) {
                        IconButton(onClick = { showCreateCollectionDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "New Collection", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        var showCollectionMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showCollectionMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showCollectionMenu,
                            onDismissRequest = { showCollectionMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete Collection") },
                                onClick = {
                                    showCollectionMenu = false
                                    val colId = activeCollection.id
                                    scope.launch {
                                        try {
                                            FlagshipFeaturesRepository.deleteCollection(currentUserId, colId)
                                            Toast.makeText(context, "Collection deleted", Toast.LENGTH_SHORT).show()
                                            selectedCollection = null
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to delete collection", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedCollection == null) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("All Posts", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Collections", fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            if (selectedCollection == null && selectedTab == 1) {
                // Collections Grid
                if (collections.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Organize Your Saved Posts", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Create collections for inspiration, photography, recipes, and more.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { showCreateCollectionDialog = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("New Collection")
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(collections, key = { index, col -> "${col.id}_$index" }) { _, col ->
                            Surface(
                                onClick = { selectedCollection = col },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (col.coverUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = col.coverUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Lock, contentDescription = "Private", tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(col.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Private collection", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Saved Posts Grid (All or in Collection)
                if (savedPosts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No Saved Posts", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "When you save photos and videos, they'll appear here. Only you can see what you've saved.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(savedPosts, key = { index, item -> "${item.id}_$index" }) { _, item ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onOpenPost(item.postId) }
                            ) {
                                if (item.thumbnailUrl.isNotEmpty() || item.mediaUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = item.thumbnailUrl.ifEmpty { item.mediaUrl },
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateCollectionDialog) {
        var collectionName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateCollectionDialog = false },
            title = { Text("New Collection", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = collectionName,
                    onValueChange = { if (it.length <= 30) collectionName = it },
                    label = { Text("Collection Name") },
                    placeholder = { Text("e.g. Travel, Recipes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (collectionName.isNotBlank()) {
                            scope.launch {
                                FlagshipFeaturesRepository.createCollection(currentUserId, collectionName.trim())
                                Toast.makeText(context, "Collection created", Toast.LENGTH_SHORT).show()
                                showCreateCollectionDialog = false
                            }
                        }
                    },
                    enabled = collectionName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateCollectionDialog = false }) { Text("Cancel") }
            }
        )
    }
}
