package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.GifItem
import com.example.model.GifRepository
import com.example.model.RepositoryManager
import com.example.model.StickerItem
import com.example.model.StickerPack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MediaPickerTab {
    EMOJI,
    GIF,
    STICKER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialMediaPickerBottomSheet(
    onDismissRequest: () -> Unit,
    initialTab: MediaPickerTab = MediaPickerTab.STICKER,
    onStickerSelected: (StickerItem) -> Unit = {},
    onGifSelected: (GifItem) -> Unit = {},
    onEmojiSelected: (String) -> Unit = {}
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentTab by remember { mutableStateOf(initialTab) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = modalBottomSheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
        ) {
            // Top Tab Selector: EMOJI | GIF | STICKER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MediaTabItem(
                    title = "Stickers 🎨",
                    isSelected = currentTab == MediaPickerTab.STICKER,
                    onClick = { currentTab = MediaPickerTab.STICKER }
                )
                Spacer(modifier = Modifier.width(12.dp))
                MediaTabItem(
                    title = "GIFs 🎭",
                    isSelected = currentTab == MediaPickerTab.GIF,
                    onClick = { currentTab = MediaPickerTab.GIF }
                )
                Spacer(modifier = Modifier.width(12.dp))
                MediaTabItem(
                    title = "Emojis 😊",
                    isSelected = currentTab == MediaPickerTab.EMOJI,
                    onClick = { currentTab = MediaPickerTab.EMOJI }
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 8.dp)
            )

            // Content Area based on Tab
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (currentTab) {
                    MediaPickerTab.STICKER -> {
                        StickerPickerContent(
                            onStickerSelected = { sticker ->
                                onStickerSelected(sticker)
                                onDismissRequest()
                            }
                        )
                    }
                    MediaPickerTab.GIF -> {
                        GifPickerContent(
                            onGifSelected = { gif ->
                                onGifSelected(gif)
                                onDismissRequest()
                            }
                        )
                    }
                    MediaPickerTab.EMOJI -> {
                        EmojiPickerContent(
                            onEmojiSelected = { emoji ->
                                onEmojiSelected(emoji)
                                onDismissRequest()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Text(
            text = title,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

// ==========================================
// 1. STICKER PICKER CONTENT
// ==========================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickerPickerContent(
    onStickerSelected: (StickerItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedPackId by remember { mutableStateOf<String?>(null) }

    val publishedPacks by RepositoryManager.stickerRepository.observePublishedPacks().collectAsState(initial = emptyList())
    val recentStickers by RepositoryManager.stickerRepository.getRecentStickers(currentUserId).collectAsState(initial = emptyList())
    val favoriteStickers by RepositoryManager.stickerRepository.getFavoriteStickers(currentUserId).collectAsState(initial = emptyList())

    var displayedStickers by remember { mutableStateOf<List<StickerItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // When a pack is selected, observe its stickers
    val currentPackStickers by produceState<List<StickerItem>>(initialValue = emptyList(), key1 = selectedPackId) {
        val packId = selectedPackId
        if (packId != null) {
            RepositoryManager.stickerRepository.observePackStickers(packId).collect {
                value = it
            }
        } else {
            value = emptyList()
        }
    }

    // Reactively compute displayed stickers based on search, filter, or selected pack
    LaunchedEffect(searchQuery, selectedCategory, selectedPackId, currentPackStickers, publishedPacks, recentStickers, favoriteStickers) {
        if (searchQuery.isNotBlank()) {
            isSearching = true
            delay(300)
            displayedStickers = RepositoryManager.stickerRepository.searchStickers(searchQuery)
            isSearching = false
        } else if (selectedCategory == "Recent") {
            displayedStickers = recentStickers
        } else if (selectedCategory == "Favorites") {
            displayedStickers = favoriteStickers
        } else if (selectedPackId != null) {
            displayedStickers = currentPackStickers
        } else {
            // Default "All": load stickers from first available pack or all published packs
            if (publishedPacks.isNotEmpty()) {
                val firstPackId = publishedPacks.first().packId
                selectedPackId = firstPackId
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search stickers...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(50.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
        )

        // Category Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == "Recent" && searchQuery.isEmpty(),
                    onClick = {
                        selectedCategory = "Recent"
                        selectedPackId = null
                        searchQuery = ""
                    },
                    label = { Text("🕒 Recent") }
                )
            }
            item {
                FilterChip(
                    selected = selectedCategory == "Favorites" && searchQuery.isEmpty(),
                    onClick = {
                        selectedCategory = "Favorites"
                        selectedPackId = null
                        searchQuery = ""
                    },
                    label = { Text("⭐ Favorites") }
                )
            }
            items(publishedPacks) { pack ->
                FilterChip(
                    selected = selectedPackId == pack.packId && selectedCategory !in listOf("Recent", "Favorites") && searchQuery.isEmpty(),
                    onClick = {
                        selectedPackId = pack.packId
                        selectedCategory = pack.category
                        searchQuery = ""
                    },
                    label = { Text(pack.name) },
                    leadingIcon = {
                        if (pack.iconUrl.isNotEmpty()) {
                            AsyncImage(
                                model = pack.iconUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                )
            }
        }

        // Stickers Grid (3-4 columns responsive)
        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else if (displayedStickers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SentimentNeutral,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (selectedCategory == "Favorites") "No favorite stickers yet.\nLong-press any sticker to favorite it!"
                        else if (selectedCategory == "Recent") "No recent stickers used."
                        else "No stickers found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 85.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayedStickers, key = { it.stickerId }) { sticker ->
                    StickerGridItem(
                        sticker = sticker,
                        onClick = {
                            coroutineScope.launch {
                                RepositoryManager.stickerRepository.recordStickerUsage(currentUserId, sticker)
                            }
                            onStickerSelected(sticker)
                        },
                        onLongClick = {
                            coroutineScope.launch {
                                val isFav = RepositoryManager.stickerRepository.toggleFavorite(currentUserId, sticker)
                                Toast.makeText(
                                    context,
                                    if (isFav) "Added to favorites ⭐" else "Removed from favorites",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickerGridItem(
    sticker: StickerItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(sticker.mediaUrl) {
        ImageRequest.Builder(context)
            .data(sticker.mediaUrl.ifEmpty { sticker.thumbnailUrl })
            .crossfade(true)
            .build()
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = sticker.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Animated badge indicator
        if (sticker.isAnimated) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text("GIF", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// 2. GIF PICKER CONTENT
// ==========================================

@Composable
fun GifPickerContent(
    onGifSelected: (GifItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Trending") }
    var gifs by remember { mutableStateOf<List<GifItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val categories = listOf("Trending", "Bollywood", "Funny", "Love", "Cricket", "Dance", "Reactions")

    LaunchedEffect(selectedCategory, searchQuery) {
        isLoading = true
        delay(300)
        gifs = if (searchQuery.isNotBlank()) {
            GifRepository.searchGifs(searchQuery)
        } else {
            GifRepository.getGifsByCategory(selectedCategory)
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search Tenor / Giphy...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(50.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat && searchQuery.isEmpty(),
                    onClick = {
                        selectedCategory = cat
                        searchQuery = ""
                    },
                    label = { Text(cat) }
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(gifs, key = { it.id }) { gif ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { onGifSelected(gif) }
                    ) {
                        AsyncImage(
                            model = gif.previewUrl,
                            contentDescription = gif.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. EMOJI PICKER CONTENT
// ==========================================

@Composable
fun EmojiPickerContent(
    onEmojiSelected: (String) -> Unit
) {
    SocialIndiaEmojiPicker(
        onEmojiSelected = onEmojiSelected,
        modifier = Modifier.fillMaxSize()
    )
}
