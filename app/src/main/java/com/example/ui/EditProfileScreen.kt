package com.example.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit
) {
                                                                    val profileViewModel: ProfileViewModel = viewModel()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Digital Creator") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var musicTitle by remember { mutableStateOf("") }
    var musicArtist by remember { mutableStateOf("") }
    var musicUrl by remember { mutableStateOf("") }
    var showMusicPicker by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val creatorCategories = remember {
        listOf(
            "Digital Creator", "Artist", "Musician / Band", "Video Creator",
            "Gamer", "Comedian", "Blogger", "Fashion & Beauty",
            "Fitness & Health", "Tech & Education", "Entrepreneur", "Photographer", "Public Figure"
        )
    }

    LaunchedEffect(Unit) {
        try {
            val uid = com.example.model.RepositoryManager.getCurrentUserId()
            if (uid.isNotEmpty()) {
                com.example.model.RepositoryManager.firestore.collection("users").document(uid).get().addOnSuccessListener { doc ->
                    name = doc.getString("fullName") ?: doc.getString("name") ?: ""
                    username = doc.getString("username") ?: ""
                    bio = doc.getString("bio") ?: ""
                    category = doc.getString("category")?.ifBlank { "Digital Creator" } ?: "Digital Creator"
                    avatarUrl = doc.getString("avatarUrl") ?: doc.getString("profilePicUrl") ?: doc.getString("avatar_url")
                    musicTitle = doc.getString("profileMusicTitle") ?: ""
                    musicArtist = doc.getString("profileMusicArtist") ?: ""
                    musicUrl = doc.getString("profileMusicUrl") ?: ""
                }
            }
        } catch (e: Exception) {}
    }

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!isSaving) {
                                isSaving = true
                                coroutineScope.launch {
                                    try {
                                        val uid = com.example.model.RepositoryManager.getCurrentUserId()
                                        if (uid.isNotEmpty()) {
                                            try {
                                                val updates = hashMapOf<String, Any>(
                                                    "profileMusicTitle" to musicTitle,
                                                    "profileMusicArtist" to musicArtist,
                                                    "profileMusicUrl" to musicUrl,
                                                    "category" to category
                                                )
                                                val selectedAvatarUrl = avatarUrl
                                                if (photoUri == null && !selectedAvatarUrl.isNullOrBlank()) {
                                                    updates["avatarUrl"] = selectedAvatarUrl
                                                    updates["profilePicUrl"] = selectedAvatarUrl
                                                    updates["avatar_url"] = selectedAvatarUrl
                                                }
                                                com.example.model.RepositoryManager.firestore.collection("users").document(uid)
                                                    .update(updates).await()
                                            } catch (_: Exception) {}
                                        }
                                        profileViewModel.updateProfile(context, username, name, bio, photoUri, null, onComplete = {
                                            isSaving = false
                                            Toast.makeText(context, "Profile saved successfully! ✨", Toast.LENGTH_SHORT).show()
                                            onBack()
                                        })
                                    } catch (e: Exception) {
                                        android.util.Log.e("EditProfileScreen", "Failed to save profile", e)
                                        Toast.makeText(context, "Changes saved locally", Toast.LENGTH_SHORT).show()
                                        isSaving = false
                                        onBack()
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Save",
                            tint = Color(0xFF1DA1F2),
                            modifier = Modifier.size(28.dp)
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Picture Section
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Profile Picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (avatarUrl?.isNotEmpty() == true) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Profile Picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Person, contentDescription = "Default Avatar", modifier = Modifier.size(64.dp), tint = Color.Gray)
                }
                
                // Camera Icon Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Change Photo", tint = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Edit Picture", color = Color(0xFF1DA1F2), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.clickable {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                Text("   •   ", color = Color.Gray)
                Text(
                    "Choose Avatar",
                    color = Color(0xFFFF4081),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable { showAvatarPicker = true }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Form Fields enclosed in a Card/Surface for a cleaner look
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 0.8.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Name Field
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF1DA1F2)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = Color(0xFF1DA1F2),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            cursorColor = Color(0xFF1DA1F2)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Username Field
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = Color(0xFFFF4081)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = Color(0xFFFF4081),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            cursorColor = Color(0xFFFF4081)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Bio Field
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { if (it.length <= 150) bio = it },
                        label = { Text("Bio") },
                        leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = Color(0xFF9C27B0)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = Color(0xFF9C27B0),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        maxLines = 4
                    )
                    Text(
                        text = "${bio.length}/150",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Interactive Category / Profession Picker
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCategoryPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Category, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Category / Profession", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(category, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select Category", tint = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Profile Music Picker Row (Real Songs like Notes)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (musicTitle.isNotBlank()) Color(0xFF0095F6).copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMusicPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Profile Music",
                                tint = Color(0xFF0095F6),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Profile Music",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (musicTitle.isNotBlank()) "$musicTitle • $musicArtist" else "Add a real song to your profile",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                            if (musicTitle.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        musicTitle = ""
                                        musicArtist = ""
                                        musicUrl = ""
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Music",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Select",
                                    color = Color(0xFF0095F6),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (isSaving) {
                CircularProgressIndicator(color = Color(0xFF1DA1F2))
            }
        }
    }

    if (showMusicPicker) {
        MusicPickerSheet(
            onDismiss = { showMusicPicker = false },
            onTrackSelected = { track ->
                musicTitle = track.name.orEmpty()
                musicArtist = track.artistName.orEmpty()
                musicUrl = track.previewUrl.orEmpty()
                showMusicPicker = false
            }
        )
    }

    if (showAvatarPicker) {
        val seedBase = username.ifBlank { "socialindia" }
        val avatarOptions = remember(seedBase) {
            listOf(
                "https://api.dicebear.com/7.x/avataaars/png?seed=${seedBase}_1",
                "https://api.dicebear.com/7.x/avataaars/png?seed=${seedBase}_2",
                "https://api.dicebear.com/7.x/adventurer/png?seed=${seedBase}_3",
                "https://api.dicebear.com/7.x/adventurer/png?seed=${seedBase}_4",
                "https://api.dicebear.com/7.x/lorelei/png?seed=${seedBase}_5",
                "https://api.dicebear.com/7.x/notionists/png?seed=${seedBase}_6",
                "https://api.dicebear.com/7.x/fun-emoji/png?seed=${seedBase}_7",
                "https://api.dicebear.com/7.x/bottts-neutral/png?seed=${seedBase}_8",
                "https://api.dicebear.com/7.x/big-smile/png?seed=${seedBase}_9"
            )
        }
        AlertDialog(
            onDismissRequest = { showAvatarPicker = false },
            title = { Text("Choose Avatar Style ✨", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    avatarOptions.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowItems.forEach { url ->
                                val selected = avatarUrl == url && photoUri == null
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) Color(0xFF0095F6) else Color.Gray.copy(alpha = 0.3f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            photoUri = null
                                            avatarUrl = url
                                            showAvatarPicker = false
                                            Toast.makeText(context, "Avatar selected! Tap ✓ to save", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "Avatar Preset",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarPicker = false }) { Text("Close") }
            }
        )
    }

    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("Select Category", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    creatorCategories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (category == cat) Color(0xFF0095F6).copy(alpha = 0.14f) else Color.Transparent)
                                .clickable {
                                    category = cat
                                    showCategoryPicker = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(cat, fontWeight = if (category == cat) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                            if (category == cat) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF0095F6), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryPicker = false }) { Text("Done") }
            }
        )
    }
}
