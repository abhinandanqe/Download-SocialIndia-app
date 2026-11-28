package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.RepositoryManager
import com.example.model.StoryHighlight
import com.example.model.StoryUiModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryOwnerMoreBottomSheet(
    story: StoryUiModel,
    onDismiss: () -> Unit,
    onEditStory: () -> Unit,
    onDeleteStory: () -> Unit,
    onOpenViewers: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddToHighlight: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Story Options",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(6.dp))

            // Edit Story
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onEditStory()
                    }
                    .padding(vertical = 12.dp)
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Edit Story", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            // Viewers
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onOpenViewers()
                    }
                    .padding(vertical = 12.dp)
            ) {
                Icon(Icons.Outlined.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Story Viewers", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            // Add to Highlight
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onAddToHighlight()
                    }
                    .padding(vertical = 12.dp)
            ) {
                Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Add to Highlights", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            // Story Settings / Privacy
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onOpenSettings()
                    }
                    .padding(vertical = 12.dp)
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Story Settings & Privacy", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))

            // Delete Story (Destructive)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onDeleteStory()
                    }
                    .padding(vertical = 12.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Delete Story", color = Color(0xFFFF5252), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DeleteStoryConfirmationDialog(
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this story?", fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to delete this story? It will be removed immediately from your profile and feed.") },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirmDelete()
            }) {
                Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditStoryDialog(
    story: StoryUiModel,
    onDismiss: () -> Unit,
    onStoryUpdated: (StoryUiModel) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var textOverlay by remember { mutableStateOf(story.textOverlay ?: "") }
    var isCloseFriendsOnly by remember { mutableStateOf(story.isCloseFriendsOnly) }
    var selectedFilter by remember { mutableStateOf("Normal") }
    var isSaving by remember { mutableStateOf(false) }

    val filterOptions = listOf("Normal", "Vivid", "Warm", "Cool", "Vintage", "B&W")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text("Edit Story", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Button(
                        onClick = {
                            isSaving = true
                            scope.launch {
                                try {
                                    val updates = hashMapOf<String, Any>(
                                        "textOverlay" to textOverlay.trim(),
                                        "isCloseFriendsOnly" to isCloseFriendsOnly,
                                        "filter" to selectedFilter,
                                        "updatedAt" to System.currentTimeMillis()
                                    )
                                    RepositoryManager.postRepository.updateStory(story.id, updates)
                                    val updatedModel = story.copy(
                                        textOverlay = textOverlay.trim(),
                                        isCloseFriendsOnly = isCloseFriendsOnly
                                    )
                                    Toast.makeText(context, "Story updated!", Toast.LENGTH_SHORT).show()
                                    onStoryUpdated(updatedModel)
                                    onDismiss()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to update story", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Media Preview Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = story.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Text Overlay Preview
                    if (textOverlay.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = textOverlay,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isCloseFriendsOnly) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF10B981))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Close Friends Only", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Controls Panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161616))
                        .padding(16.dp)
                ) {
                    // Caption / Text Overlay input
                    OutlinedTextField(
                        value = textOverlay,
                        onValueChange = { textOverlay = it },
                        placeholder = { Text("Edit text overlay or caption", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF69C9D0),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Color Tone Filters
                    Text("Filters & Tone", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filterOptions) { filter ->
                            val isSelected = selectedFilter == filter
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) Color(0xFFEE1D52) else Color(0xFF2A2A2A))
                                    .border(1.dp, if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(16.dp))
                                    .clickable { selectedFilter = filter }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(filter, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Privacy Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF222222))
                            .clickable { isCloseFriendsOnly = !isCloseFriendsOnly }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Close Friends", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Only shared with your close friends list", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = isCloseFriendsOnly,
                            onCheckedChange = { isCloseFriendsOnly = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StoryPrivacySettingsDialog(
    story: StoryUiModel,
    onDismiss: () -> Unit,
    onPrivacyUpdated: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCloseFriends by remember { mutableStateOf(story.isCloseFriendsOnly) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Story Privacy", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select who can view this story:", fontSize = 14.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCloseFriends = false }
                        .padding(vertical = 6.dp)
                ) {
                    RadioButton(selected = !isCloseFriends, onClick = { isCloseFriends = false })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Public (Followers & Profile Visitors)", fontWeight = FontWeight.Medium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCloseFriends = true }
                        .padding(vertical = 6.dp)
                ) {
                    RadioButton(selected = isCloseFriends, onClick = { isCloseFriends = true })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Close Friends Only", fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    scope.launch {
                        try {
                            RepositoryManager.postRepository.updateStory(
                                story.id,
                                mapOf("isCloseFriendsOnly" to isCloseFriends)
                            )
                            Toast.makeText(context, "Privacy updated", Toast.LENGTH_SHORT).show()
                            onPrivacyUpdated(isCloseFriends)
                            onDismiss()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error updating privacy", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddStoryToHighlightDialog(
    storyId: String,
    userId: String,
    coverUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val highlights by RepositoryManager.postRepository.listenUserHighlights(userId).collectAsState(initial = emptyList())
    var isCreatingNew by remember { mutableStateOf(false) }
    var newHighlightTitle by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Add to Highlights", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(14.dp))

                if (isCreatingNew) {
                    OutlinedTextField(
                        value = newHighlightTitle,
                        onValueChange = { newHighlightTitle = it },
                        placeholder = { Text("Highlight Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { isCreatingNew = false }) { Text("Back") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newHighlightTitle.isBlank()) return@Button
                                isProcessing = true
                                scope.launch {
                                    try {
                                        RepositoryManager.postRepository.createHighlight(
                                            userId = userId,
                                            title = newHighlightTitle.trim(),
                                            coverUrl = coverUrl,
                                            storyIds = listOf(storyId)
                                        )
                                        Toast.makeText(context, "Added to new highlight!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to create highlight", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            },
                            enabled = !isProcessing && newHighlightTitle.isNotBlank()
                        ) {
                            Text("Add")
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isCreatingNew = true }
                            .padding(vertical = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text("New Highlight", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    if (highlights.isEmpty()) {
                        Text("No existing highlights. Create one above!", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            items(highlights, key = { it.id }) { highlight ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(64.dp)
                                        .clickable {
                                            isProcessing = true
                                            scope.launch {
                                                try {
                                                    RepositoryManager.postRepository.addStoryToHighlight(highlight.id, storyId)
                                                    Toast.makeText(context, "Added to ${highlight.title}!", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error adding to highlight", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isProcessing = false
                                                }
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = highlight.coverUrl.ifEmpty { "https://picsum.photos/200" },
                                        contentDescription = null,
                                        modifier = Modifier.size(54.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(highlight.title, maxLines = 1, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}
