package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.GroupChat
import com.example.model.User
import com.example.repository.GroupChatRepository
import com.example.repository.MediaRepository
import com.example.utils.SoundManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreationDialog(
    currentUserId: String,
    currentUserName: String,
    availableUsers: List<User>,
    onDismiss: () -> Unit,
    onGroupCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaRepo = remember { MediaRepository(context) }

    // Step state: 0 = Info, 1 = Member Selection, 2 = Review & Create
    var currentStep by remember { mutableIntStateOf(0) }

    // Group info state
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    var selectedAvatarUrl by remember { mutableStateOf("https://api.dicebear.com/7.x/identicon/png?seed=${System.currentTimeMillis()}") }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    // Member selection state
    val selectedMemberIds = remember { mutableStateListOf<String>() }
    var searchQuery by remember { mutableStateOf("") }

    // Permissions
    var onlyAdminsCanPost by remember { mutableStateOf(false) }

    // Loading indicator for group creation
    var isCreating by remember { mutableStateOf(false) }

    // Avatar Presets
    val avatarPresets = remember {
        listOf(
            "https://api.dicebear.com/7.x/identicon/png?seed=TechIndia",
            "https://api.dicebear.com/7.x/identicon/png?seed=CreatorsClub",
            "https://api.dicebear.com/7.x/identicon/png?seed=BollywoodFans",
            "https://api.dicebear.com/7.x/identicon/png?seed=GamingSquad",
            "https://api.dicebear.com/7.x/identicon/png?seed=StudyGroup",
            "https://api.dicebear.com/7.x/identicon/png?seed=CricketHub"
        )
    }

    // Photo picker for custom avatar
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isUploadingAvatar = true
                try {
                    val uploadedUrl = mediaRepo.uploadMedia(uri)
                    if (uploadedUrl != null) {
                        selectedAvatarUrl = uploadedUrl
                        Toast.makeText(context, "Group photo uploaded!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Upload failed, using avatar preset", Toast.LENGTH_SHORT).show()
                } finally {
                    isUploadingAvatar = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = when (currentStep) {
                                    0 -> "New Group Details"
                                    1 -> "Add Members"
                                    else -> "Review & Create"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Step ${currentStep + 1} of 3",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            SoundManager.playClickSound()
                            if (currentStep > 0) {
                                currentStep--
                            } else {
                                onDismiss()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (currentStep < 2) {
                            TextButton(
                                onClick = {
                                    SoundManager.playClickSound()
                                    if (currentStep == 0) {
                                        if (groupName.trim().isEmpty()) {
                                            Toast.makeText(context, "Please enter a group name", Toast.LENGTH_SHORT).show()
                                        } else {
                                            currentStep = 1
                                        }
                                    } else if (currentStep == 1) {
                                        currentStep = 2
                                    }
                                }
                            ) {
                                Text("Next", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
            ) {
                when (currentStep) {
                    // STEP 1: GROUP DETAILS
                    0 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Avatar preview & upload button
                            Box(contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = selectedAvatarUrl,
                                    contentDescription = "Group Icon",
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                if (isUploadingAvatar) {
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        SoundManager.playClickSound()
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = "Upload Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tap camera to upload custom photo or pick preset below",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                items(avatarPresets) { preset ->
                                    AsyncImage(
                                        model = preset,
                                        contentDescription = "Preset",
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = if (selectedAvatarUrl == preset) 2.dp else 0.dp,
                                                color = if (selectedAvatarUrl == preset) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                SoundManager.playClickSound()
                                                selectedAvatarUrl = preset
                                            },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Name Input
                            OutlinedTextField(
                                value = groupName,
                                onValueChange = { if (it.length <= 40) groupName = it },
                                label = { Text("Group Name *") },
                                placeholder = { Text("e.g. Mumbai Creators Squad") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                supportingText = { Text("${groupName.length}/40") },
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Description Input
                            OutlinedTextField(
                                value = groupDescription,
                                onValueChange = { if (it.length <= 150) groupDescription = it },
                                label = { Text("Group Description (Optional)") },
                                placeholder = { Text("What is this group about?") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth(),
                                supportingText = { Text("${groupDescription.length}/150") },
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = {
                                    SoundManager.playClickSound()
                                    if (groupName.trim().isEmpty()) {
                                        Toast.makeText(context, "Please enter a group name", Toast.LENGTH_SHORT).show()
                                    } else {
                                        currentStep = 1
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Continue to Add Members", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null)
                            }
                        }
                    }

                    // STEP 2: MEMBER SELECTION
                    1 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // Search Box
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search users to add...", color = Color.Gray) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Selected members chips
                            if (selectedMemberIds.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Selected (${selectedMemberIds.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    items(selectedMemberIds) { uid ->
                                        val user = availableUsers.firstOrNull { it.id == uid }
                                        val name = user?.username ?: uid.take(6)
                                        AssistChip(
                                            onClick = {
                                                SoundManager.playClickSound()
                                                selectedMemberIds.remove(uid)
                                            },
                                            label = { Text(name, maxLines = 1) },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remove",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val filteredUsers = availableUsers.filter {
                                it.id != currentUserId &&
                                (it.username.contains(searchQuery, ignoreCase = true) ||
                                 it.email.contains(searchQuery, ignoreCase = true))
                            }

                            if (filteredUsers.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No matching users found.\nYou can also add members later!",
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(filteredUsers, key = { it.id }) { user ->
                                        val isSelected = selectedMemberIds.contains(user.id)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    SoundManager.playClickSound()
                                                    if (isSelected) {
                                                        selectedMemberIds.remove(user.id)
                                                    } else {
                                                        selectedMemberIds.add(user.id)
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = user.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${user.id}" },
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = user.username.ifEmpty { "User" },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                                if (user.bio.isNotEmpty()) {
                                                    Text(
                                                        text = user.bio,
                                                        fontSize = 12.sp,
                                                        color = Color.Gray,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = {
                                                    SoundManager.playClickSound()
                                                    if (isSelected) {
                                                        selectedMemberIds.remove(user.id)
                                                    } else {
                                                        selectedMemberIds.add(user.id)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    SoundManager.playClickSound()
                                    currentStep = 2
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (selectedMemberIds.isEmpty()) "Skip to Review" else "Review Group (${selectedMemberIds.size} members)",
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null)
                            }
                        }
                    }

                    // STEP 3: REVIEW & CREATE
                    2 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AsyncImage(
                                        model = selectedAvatarUrl,
                                        contentDescription = "Group Icon",
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = groupName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    if (groupDescription.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = groupDescription,
                                            fontSize = 13.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AssistChip(
                                            onClick = {},
                                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            label = { Text("Owner: $currentUserName") }
                                        )
                                        AssistChip(
                                            onClick = {},
                                            leadingIcon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            label = { Text("${selectedMemberIds.size + 1} Members") }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Permissions Section
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Group Permissions", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Only Admins Can Post", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                            Text("Turn on to make this an announcement channel", fontSize = 12.sp, color = Color.Gray)
                                        }
                                        Switch(
                                            checked = onlyAdminsCanPost,
                                            onCheckedChange = { onlyAdminsCanPost = it }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = {
                                    SoundManager.playClickSound()
                                    scope.launch {
                                        isCreating = true
                                        try {
                                            val newGroupId = GroupChatRepository.createGroup(
                                                name = groupName,
                                                description = groupDescription,
                                                avatarUrl = selectedAvatarUrl,
                                                memberIds = selectedMemberIds.toList(),
                                                creatorId = currentUserId,
                                                creatorName = currentUserName,
                                                onlyAdminsCanPost = onlyAdminsCanPost
                                            )
                                            Toast.makeText(context, "Group \"$groupName\" created successfully!", Toast.LENGTH_SHORT).show()
                                            onGroupCreated(newGroupId)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to create group: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isCreating = false
                                        }
                                    }
                                },
                                enabled = !isCreating,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isCreating) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Creating Group in Firebase...")
                                } else {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Finish & Launch Group", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
