package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.RepositoryManager
import com.example.model.ScheduledContent
import com.example.repository.FlagshipFeaturesRepository
import com.example.repository.MediaRepository
import com.example.utils.ExoPlayerUtils
import com.example.utils.MediaResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledContentScreen(
    currentUserId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheduledList by RepositoryManager.socialRepository.getScheduledContent(currentUserId).collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("EEE, dd MMM • hh:mm a", Locale.getDefault()) }

    var filterMode by remember { mutableStateOf("scheduled") } // "scheduled", "published"
    var showNewScheduleSheet by remember { mutableStateOf(false) }
    var reschedulingItem by remember { mutableStateOf<ScheduledContent?>(null) }
    var publishingId by remember { mutableStateOf<String?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    // Automatically check and publish due scheduled items when opening the studio
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            val count = FlagshipFeaturesRepository.autoPublishDueScheduledPosts(currentUserId)
            if (count > 0) {
                Toast.makeText(context, "$count scheduled post(s) auto-published!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val filteredItems = remember(scheduledList, filterMode) {
        if (filterMode == "scheduled") {
            scheduledList.filter { it.status != "published" }.sortedBy { it.scheduledTime }
        } else {
            scheduledList.filter { it.status == "published" }.sortedByDescending { it.scheduledTime }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Scheduled Content", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                        Text("Auto-publish posts & reels on time", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewScheduleSheet = true },
                containerColor = Color(0xFFFF2A5F),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.ScheduleSend, contentDescription = null) },
                text = { Text("Schedule Post", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("schedule_new_post_fab")
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val upcomingCount = scheduledList.count { it.status != "published" }
                val publishedCount = scheduledList.count { it.status == "published" }
                FilterChip(
                    selected = filterMode == "scheduled",
                    onClick = { filterMode = "scheduled" },
                    label = { Text("Upcoming ($upcomingCount)", fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF2A5F),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = filterMode == "published",
                    onClick = { filterMode = "published" },
                    label = { Text("Published ($publishedCount)", fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF00C853),
                        selectedLabelColor = Color.White
                    )
                )
            }

            if (filteredItems.isEmpty()) {
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
                                .background(Color(0xFFFF2A5F).copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = Color(0xFFFF2A5F), modifier = Modifier.size(34.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (filterMode == "scheduled") "No Upcoming Scheduled Posts" else "No Published History Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Schedule Reels or Photos for peak evening hours. They'll automatically go live on time or with one tap.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        val isPublished = item.status == "published"
                        val countdownText = remember(item.scheduledTime, nowMs, isPublished) {
                            if (isPublished) "Published Live" else formatCountdown(item.scheduledTime - nowMs)
                        }
                        val posterUrl = remember(item.mediaUri) {
                            if (item.mediaUri.startsWith("http")) {
                                MediaResolver.resolveVideoPosterUrl(item.mediaUri, item.mediaUri).ifBlank { item.mediaUri }
                            } else item.mediaUri
                        }

                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (posterUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = ExoPlayerUtils.buildFastPosterRequest(context, posterUrl, 220, 220),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(66.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isPublished) Color(0xFF00C853).copy(alpha = 0.15f) else Color(0xFFFF2A5F).copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = countdownText,
                                                    color = if (isPublished) Color(0xFF00C853) else Color(0xFFFF2A5F),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 10.5.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                            Text(
                                                text = item.type.uppercase(),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Text(
                                            text = dateFormat.format(Date(item.scheduledTime)),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = item.caption.ifBlank { "No caption" },
                                            fontSize = 13.5.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (!isPublished) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    FlagshipFeaturesRepository.deleteScheduledItem(item.id)
                                                    Toast.makeText(context, "Cancelled scheduled post", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("Delete", fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        OutlinedButton(
                                            onClick = { reschedulingItem = item },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Reschedule", fontSize = 12.sp)
                                        }
                                        Button(
                                            onClick = {
                                                publishingId = item.id
                                                scope.launch {
                                                    val success = FlagshipFeaturesRepository.publishScheduledItemNow(item)
                                                    publishingId = null
                                                    Toast.makeText(
                                                        context,
                                                        if (success) "Published live to Feed!" else "Failed to publish",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                            enabled = publishingId != item.id,
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A5F)),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            if (publishingId == item.id) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                            } else {
                                                Text("Publish Now", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
    }

    if (showNewScheduleSheet) {
        CreateScheduledPostSheet(
            userId = currentUserId,
            onDismiss = { showNewScheduleSheet = false }
        )
    }

    reschedulingItem?.let { target ->
        ScheduleTimePickerDialog(
            title = "Reschedule Post",
            initialCaption = target.caption,
            onDismiss = { reschedulingItem = null },
            onConfirm = { newCaption, newTimeMs ->
                scope.launch {
                    val ok = FlagshipFeaturesRepository.updateScheduledItem(target.id, newCaption, newTimeMs)
                    Toast.makeText(context, if (ok) "Schedule updated!" else "Update failed", Toast.LENGTH_SHORT).show()
                    reschedulingItem = null
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateScheduledPostSheet(
    userId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedType by remember { mutableStateOf("post") }
    var caption by remember { mutableStateOf("") }
    var hoursAhead by remember { mutableIntStateOf(2) }
    var isUploading by remember { mutableStateOf(false) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            val mime = context.contentResolver.getType(uri).orEmpty()
            selectedType = if (mime.startsWith("video")) "reel" else "post"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Schedule New Post / Reel", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable {
                        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                contentAlignment = Alignment.Center
            ) {
                if (selectedMediaUri != null) {
                    AsyncImage(
                        model = selectedMediaUri,
                        contentDescription = "Selected Media",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.VideoCall, contentDescription = null, tint = Color(0xFFFF2A5F), modifier = Modifier.size(38.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Tap to pick Photo or Reel Video", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    }
                }
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { if (it.length <= 500) caption = it },
                label = { Text("Caption & #Hashtags") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                minLines = 2,
                maxLines = 4
            )

            Text("Publish Time", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1 to "+1h", 3 to "+3h", 12 to "+12h", 24 to "Tomorrow", 72 to "+3 Days").forEach { (hrs, label) ->
                    FilterChip(
                        selected = hoursAhead == hrs,
                        onClick = { hoursAhead = hrs },
                        label = { Text(label, fontSize = 11.5.sp) }
                    )
                }
            }

            Button(
                onClick = {
                    val uri = selectedMediaUri
                    if (uri == null) {
                        Toast.makeText(context, "Select a photo or video first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isUploading = true
                    scope.launch {
                        val cloudUrl = try {
                            MediaRepository(context).uploadMedia(uri)
                        } catch (_: Exception) { null }
                        if (cloudUrl.isNullOrBlank()) {
                            isUploading = false
                            Toast.makeText(context, "Media upload failed — please retry", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val targetMs = System.currentTimeMillis() + hoursAhead * 60L * 60L * 1000L
                        val ok = FlagshipFeaturesRepository.createScheduledItem(
                            userId = userId,
                            type = selectedType,
                            mediaUri = cloudUrl,
                            caption = caption,
                            scheduledTime = targetMs
                        )
                        isUploading = false
                        if (ok) {
                            Toast.makeText(context, "Post scheduled successfully!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Failed to schedule post", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isUploading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A5F))
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Confirm Schedule", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ScheduleTimePickerDialog(
    title: String,
    initialCaption: String,
    onDismiss: () -> Unit,
    onConfirm: (caption: String, scheduledTimeMs: Long) -> Unit
) {
    var caption by remember { mutableStateOf(initialCaption) }
    var selectedHours by remember { mutableIntStateOf(2) }
    val previewFormat = remember { SimpleDateFormat("EEE, dd MMM • hh:mm a", Locale.getDefault()) }
    val targetTimeMs = remember(selectedHours) {
        System.currentTimeMillis() + selectedHours * 60L * 60L * 1000L
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 500) caption = it },
                    label = { Text("Caption") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Text("Select Publish Delay", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1 to "+1h", 4 to "+4h", 12 to "+12h", 24 to "+1d", 72 to "+3d").forEach { (h, lbl) ->
                        FilterChip(
                            selected = selectedHours == h,
                            onClick = { selectedHours = h },
                            label = { Text(lbl, fontSize = 11.sp) }
                        )
                    }
                }
                Text(
                    text = "Will publish: ${previewFormat.format(Date(targetTimeMs))}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(caption, targetTimeMs) }) {
                Text("Save Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatCountdown(remainingMs: Long): String {
    if (remainingMs <= 0L) return "Due Now"
    val totalMinutes = remainingMs / (1000L * 60L)
    val days = totalMinutes / (60L * 24L)
    val hours = (totalMinutes % (60L * 24L)) / 60L
    val mins = totalMinutes % 60L
    return when {
        days > 0L -> "In ${days}d ${hours}h"
        hours > 0L -> "In ${hours}h ${mins}m"
        else -> "In ${mins.coerceAtLeast(1L)}m"
    }
}
