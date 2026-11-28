package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.model.RepositoryManager
import com.example.model.StickerAdminStats
import com.example.model.StickerItem
import com.example.model.StickerPack
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStickerManagementTab(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf("All") } // "All", "Published", "Draft", "Archived"
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Static", "Animated"

    var stats by remember { mutableStateOf(StickerAdminStats()) }
    val allPacks by RepositoryManager.stickerRepository.observeAllPacksForAdmin().collectAsState(initial = emptyList())

    var showCreatePackDialog by remember { mutableStateOf(false) }
    var showUploadStickerDialog by remember { mutableStateOf(false) }
    var selectedPackForViewing by remember { mutableStateOf<StickerPack?>(null) }

    LaunchedEffect(Unit) {
        stats = RepositoryManager.stickerRepository.getAdminStats()
    }

    val filteredPacks = remember(allPacks, selectedTab, searchQuery) {
        allPacks.filter { pack ->
            val matchesTab = when (selectedTab) {
                "Published" -> pack.status == "PUBLISHED"
                "Draft" -> pack.status == "DRAFT"
                "Archived" -> pack.status == "ARCHIVED"
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    pack.name.contains(searchQuery, ignoreCase = true) ||
                    pack.category.contains(searchQuery, ignoreCase = true)
            matchesTab && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Stats Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Sticker System Analytics (Google Drive Storage)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatBox("Packs", "${stats.totalPacks}", MaterialTheme.colorScheme.primary)
                    StatBox("Published", "${stats.publishedPacks}", Color(0xFF4CAF50))
                    StatBox("Drafts", "${stats.draftPacks}", Color(0xFFFFA000))
                    StatBox("Animated", "${stats.animatedStickers}", Color(0xFF9C27B0))
                    StatBox("Usage", "${stats.totalUsageCount}", Color(0xFF00ACC1))
                }
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { showCreatePackDialog = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Pack", fontSize = 13.sp)
            }

            Button(
                onClick = {
                    if (allPacks.isEmpty()) {
                        Toast.makeText(context, "Create a sticker pack first", Toast.LENGTH_SHORT).show()
                    } else {
                        showUploadStickerDialog = true
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload Sticker", fontSize = 13.sp)
            }
        }

        // Search & Status Tabs
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search packs or categories...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Published", "Draft", "Archived").forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    label = { Text(tab, fontSize = 12.sp) }
                )
            }
        }

        // Packs List
        if (filteredPacks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No sticker packs found.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredPacks, key = { it.packId }) { pack ->
                    AdminStickerPackCard(
                        pack = pack,
                        onViewStickers = { selectedPackForViewing = pack },
                        onTogglePublish = {
                            coroutineScope.launch {
                                if (pack.status == "PUBLISHED") {
                                    RepositoryManager.stickerRepository.archivePack(pack.packId)
                                } else {
                                    RepositoryManager.stickerRepository.publishPack(pack.packId)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Dialog: Create Sticker Pack
    if (showCreatePackDialog) {
        CreateStickerPackDialog(
            onDismiss = { showCreatePackDialog = false },
            onPackCreated = {
                showCreatePackDialog = false
                Toast.makeText(context, "Sticker pack created successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog: Upload Sticker
    if (showUploadStickerDialog) {
        UploadStickerDialog(
            packs = allPacks,
            initialPackId = selectedPackForViewing?.packId,
            onDismiss = { showUploadStickerDialog = false },
            onStickerUploaded = {
                showUploadStickerDialog = false
                Toast.makeText(context, "Sticker uploaded to Google Drive!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog / Sheet: View Pack Stickers
    selectedPackForViewing?.let { pack ->
        PackStickersViewerDialog(
            pack = pack,
            onDismiss = { selectedPackForViewing = null }
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AdminStickerPackCard(
    pack: StickerPack,
    onViewStickers: () -> Unit,
    onTogglePublish: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewStickers() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = pack.iconUrl,
                contentDescription = pack.name,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pack.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val statusColor = when (pack.status) {
                        "PUBLISHED" -> Color(0xFF4CAF50)
                        "DRAFT" -> Color(0xFFFFA000)
                        else -> Color(0xFFE53935)
                    }
                    Text(
                        pack.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${pack.category} • ${pack.stickersCount} stickers",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onTogglePublish) {
                Icon(
                    imageVector = if (pack.status == "PUBLISHED") Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle status",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// Dialog: Create Pack
@Composable
fun CreateStickerPackDialog(
    onDismiss: () -> Unit,
    onPackCreated: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Reactions") }
    var language by remember { mutableStateOf("English") }
    var iconUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val categories = listOf(
        "Reactions", "Love", "Funny", "Gaming", "Celebration", "Emotions",
        "Greetings", "Indian", "Festival", "Creator", "Live", "Social India", "Seasonal", "Custom"
    )

    val iconPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) iconUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Sticker Pack", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Pack Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category selector
                Text("Category:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }

                // Pack Icon Selector (Uploaded via Google Drive)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Pack Icon (Google Drive)", fontSize = 13.sp)
                    Button(
                        onClick = { iconPickerLauncher.launch("image/*") },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (iconUri != null) "Selected ✓" else "Choose Icon", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "Enter a pack name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isUploading = true
                    coroutineScope.launch {
                        val result = RepositoryManager.stickerRepository.createPack(
                            name = name,
                            description = description,
                            category = category,
                            language = language,
                            iconUri = iconUri,
                            context = context
                        )
                        isUploading = false
                        if (result != null) {
                            onPackCreated()
                        } else {
                            Toast.makeText(context, "Failed to create pack", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isUploading
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text("Create Pack")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// Dialog: Upload Sticker
@Composable
fun UploadStickerDialog(
    packs: List<StickerPack>,
    initialPackId: String?,
    onDismiss: () -> Unit,
    onStickerUploaded: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedPackId by remember { mutableStateOf(initialPackId ?: packs.firstOrNull()?.packId ?: "") }
    var name by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) mediaUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload Sticker (Google Drive)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Pack selector
                Text("Select Pack:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(packs) { pack ->
                        FilterChip(
                            selected = selectedPackId == pack.packId,
                            onClick = { selectedPackId = pack.packId },
                            label = { Text(pack.name, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Sticker Name") },
                    placeholder = { Text("e.g. Bhangra Dance") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("dance, punjabi, celebration") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Media picker: PNG, WEBP, GIF
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Media File (PNG/WEBP/GIF)", fontSize = 12.sp)
                    Button(
                        onClick = { mediaPickerLauncher.launch("image/*") },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (mediaUri != null) "Selected ✓" else "Browse", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (mediaUri == null) {
                        Toast.makeText(context, "Please select an image/gif file", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (selectedPackId.isBlank()) {
                        Toast.makeText(context, "Please select a pack", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isUploading = true
                    coroutineScope.launch {
                        val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val result = RepositoryManager.stickerRepository.uploadSticker(
                            packId = selectedPackId,
                            name = name,
                            tags = tags,
                            mediaUri = mediaUri!!,
                            context = context
                        )
                        isUploading = false
                        if (result != null) {
                            onStickerUploaded()
                        } else {
                            Toast.makeText(context, "Upload failed. Check Google Drive permissions.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isUploading
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text("Upload to Drive")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// Dialog: View stickers in a pack
@Composable
fun PackStickersViewerDialog(
    pack: StickerPack,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val stickers by RepositoryManager.stickerRepository.observeAllPackStickersForAdmin(pack.packId).collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${pack.name} (${stickers.size} stickers)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            if (stickers.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("No stickers in this pack yet.", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(stickers, key = { it.stickerId }) { stk ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = stk.mediaUrl,
                                contentDescription = stk.name,
                                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stk.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    if (stk.isAnimated) "Animated GIF • Used ${stk.usageCount} times" else "Static • Used ${stk.usageCount} times",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    RepositoryManager.stickerRepository.deleteSticker(stk.stickerId, pack.packId)
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
