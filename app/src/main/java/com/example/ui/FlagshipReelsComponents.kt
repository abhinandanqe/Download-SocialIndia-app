package com.example.ui

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun PremiumCaptionBox(
    description: String,
    onHashtagClick: (String) -> Unit = {},
    onMentionClick: (String) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }

    val shadowStyle = androidx.compose.ui.text.TextStyle(
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(1f, 1f),
            blurRadius = 8f
        )
    )

    val annotatedString = buildAnnotatedString {
        val words = description.split(" ")
        for (word in words) {
            when {
                word.startsWith("#") -> {
                    pushStyle(SpanStyle(color = Color(0xFFFE2C55), fontWeight = FontWeight.Bold))
                    append(word)
                    pop()
                }
                word.startsWith("@") -> {
                    pushStyle(SpanStyle(color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold))
                    append(word)
                    pop()
                }
                else -> append(word)
            }
            append(" ")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300))
            .clickable { isExpanded = !isExpanded }
    ) {
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = annotatedString,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    style = shadowStyle
                )
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = annotatedString,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = shadowStyle,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (description.length > 40) {
                    Text(
                        text = " ... see more",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        style = shadowStyle
                    )
                }
            }
        }
    }
}

// ==========================================
// TRIAL REELS COMPONENTS
// ==========================================

@Composable
fun TrialReelBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.8f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFFFF9800), androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Default.Science,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Trial Reel • Insights",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TrialReelPerformanceSheet(
    postId: String,
    onDismiss: () -> Unit,
    onPublishedToEveryone: () -> Unit,
    onDeleteTrial: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var analytics by remember { mutableStateOf<com.example.model.TrialReelAnalytics?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPublishing by remember { mutableStateOf(false) }

    LaunchedEffect(postId) {
        isLoading = true
        analytics = com.example.repository.FlagshipFeaturesRepository.fetchTrialReelAnalytics(postId)
        isLoading = false
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Science,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Trial Reel Performance",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Text(
                        text = "Tested with non-follower audience",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFF9800).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = analytics?.status?.uppercase() ?: "TRIAL",
                        color = Color(0xFFFF9800),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else {
                val data = analytics ?: com.example.model.TrialReelAnalytics(contentId = postId)

                // High-level Stats Cards Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TrialStatBox(
                        title = "Views",
                        value = "${data.views}",
                        icon = androidx.compose.material.icons.Icons.Default.Visibility,
                        modifier = Modifier.weight(1f)
                    )
                    TrialStatBox(
                        title = "Avg Duration",
                        value = "${data.avgWatchDurationSeconds}s",
                        icon = androidx.compose.material.icons.Icons.Default.Timer,
                        modifier = Modifier.weight(1f)
                    )
                    TrialStatBox(
                        title = "Completion",
                        value = "${data.completionRatePercent}%",
                        icon = androidx.compose.material.icons.Icons.Default.CheckCircle,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TrialStatBox(
                        title = "Likes",
                        value = "${data.likes}",
                        icon = androidx.compose.material.icons.Icons.Default.Favorite,
                        modifier = Modifier.weight(1f)
                    )
                    TrialStatBox(
                        title = "Comments",
                        value = "${data.comments}",
                        icon = androidx.compose.material.icons.Icons.Default.ChatBubble,
                        modifier = Modifier.weight(1f)
                    )
                    TrialStatBox(
                        title = "Shares / Saves",
                        value = "${data.shares + data.saves}",
                        icon = androidx.compose.material.icons.Icons.Default.Share,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Insights Explanation
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Info,
                            contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (data.completionRatePercent >= 40)
                                "Great engagement! Non-followers watched this well. Publishing will boost reach to your followers and explore."
                            else
                                "Trial insights give you confidence before sharing permanently with all followers.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Decision Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.Button(
                        onClick = {
                            isPublishing = true
                            scope.launch {
                                val ok = com.example.repository.FlagshipFeaturesRepository.publishTrialReelToEveryone(postId)
                                isPublishing = false
                                if (ok) {
                                    android.widget.Toast.makeText(context, "Published to everyone! 🚀", android.widget.Toast.LENGTH_SHORT).show()
                                    onPublishedToEveryone()
                                    onDismiss()
                                } else {
                                    android.widget.Toast.makeText(context, "Failed to publish", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isPublishing
                    ) {
                        if (isPublishing) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publish to Everyone", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Keep in Trial", fontSize = 13.sp)
                        }

                        androidx.compose.material3.OutlinedButton(
                            onClick = onDeleteTrial,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.error
                            )
                        ) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Reel", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrialStatBox(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text = title, fontSize = 10.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ==========================================
// ADD YOURS (PROMPT CHAINS) COMPONENTS
// ==========================================

@Composable
fun AddYoursStickerView(
    promptText: String,
    participantsCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.60f),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f)),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color(0xFFE91E63)
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(4.dp).size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Yours",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                if (participantsCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• $participantsCount",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = promptText.ifBlank { "Add your video" },
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddYoursPromptSheet(
    promptId: String,
    promptText: String,
    creatorUsername: String,
    onDismiss: () -> Unit,
    onAddYoursClick: () -> Unit,
    onOpenContent: (contentId: String) -> Unit
) {
    val participants by com.example.repository.FlagshipFeaturesRepository.listenAddYoursParticipants(promptId)
        .collectAsState(initial = emptyList())

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color(0xFFE91E63)) {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(4.dp).size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Yours Chain", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Started by @${creatorUsername.ifBlank { "creator" }}",
                        fontSize = 11.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                androidx.compose.material3.Button(
                    onClick = {
                        onDismiss()
                        onAddYoursClick()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE91E63)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Yours", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // Prompt Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = promptText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${participants.size} responses",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Grid of responses
            if (participants.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Be the first to add to this chain!",
                            fontSize = 13.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    items(participants.size) { idx ->
                        val item = participants[idx]
                        Box(
                            modifier = Modifier
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.DarkGray)
                                .clickable { onOpenContent(item.contentId) }
                        ) {
                            if (item.mediaUrl.isNotEmpty()) {
                                coil.compose.AsyncImage(
                                    model = item.mediaUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            // Username label
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "@${item.username}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddYoursCreatorDialog(
    onDismiss: () -> Unit,
    onCreatePrompt: (promptText: String) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    val templates = listOf(
        "Sunset today 🌅",
        "Your pet's best angle 🐾",
        "Current view right now 👀",
        "Drop your favorite outfit ✨",
        "Your current music vibe 🎧",
        "Coffee of the day ☕"
    )

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color(0xFFE91E63)) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(4.dp).size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Add Yours Prompt", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = prompt,
                    onValueChange = { if (it.length <= 50) prompt = it },
                    placeholder = { Text("Write a prompt...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Text(
                    text = "Suggestions:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )

                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(templates.size) { idx ->
                        val t = templates[idx]
                        androidx.compose.material3.SuggestionChip(
                            onClick = { prompt = t },
                            label = { Text(t, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    if (prompt.isNotBlank()) {
                        onCreatePrompt(prompt.trim())
                    }
                },
                enabled = prompt.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Add Sticker")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
