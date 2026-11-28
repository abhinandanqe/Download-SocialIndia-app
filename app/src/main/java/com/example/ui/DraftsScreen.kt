package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.model.ContentDraft
import com.example.model.RepositoryManager
import com.example.repository.FlagshipFeaturesRepository
import com.example.repository.MediaRepository
import com.example.utils.ExoPlayerUtils
import com.example.utils.MediaResolver
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(
    onBack: () -> Unit,
    onNavigateToScheduled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }

    var drafts by remember { mutableStateOf<List<ContentDraft>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDraftSheet by remember { mutableStateOf(false) }
    var editingDraft by remember { mutableStateOf<ContentDraft?>(null) }
    var schedulingDraft by remember { mutableStateOf<ContentDraft?>(null) }
    var busyDraftId by remember { mutableStateOf<String?>(null) }

    val dateFormatter = remember { SimpleDateFormat("dd MMM • hh:mm a", Locale.getDefault()) }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            RepositoryManager.socialRepository.getContentDrafts(currentUserId).collect {
                drafts = it.sortedByDescending { d -> d.timestamp }
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Drafts Studio", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                        Text("${drafts.size} saved drafts", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("drafts_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToScheduled) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scheduled", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDraftSheet = true },
                containerColor = Color(0xFFFF2A5F),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Draft", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("create_draft_fab")
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFF2A5F))
            }
        } else if (drafts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF2A5F).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Drafts, contentDescription = null, tint = Color(0xFFFF2A5F), modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No Drafts Saved Yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Save photos or reels here to polish captions, publish instantly, or schedule for peak engagement hours.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = { showCreateDraftSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A5F))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Your First Draft", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(drafts, key = { it.id }) { draft ->
                    val isBusy = busyDraftId == draft.id
                    val posterUrl = remember(draft.mediaUri) {
                        if (draft.mediaUri.startsWith("http")) {
                            MediaResolver.resolveVideoPosterUrl(draft.mediaUri, draft.mediaUri).ifBlank { draft.mediaUri }
                        } else draft.mediaUri
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (posterUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = ExoPlayerUtils.buildFastPosterRequest(context, posterUrl, 220, 220),
                                            contentDescription = "Draft preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Outlined.Article, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFF2A5F).copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = draft.type.uppercase(),
                                                color = Color(0xFFFF2A5F),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = dateFormatter.format(Date(draft.timestamp)),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Text(
                                        text = draft.caption.ifBlank { "No caption added yet" },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            FlagshipFeaturesRepository.deleteDraft(draft.id)
                                            Toast.makeText(context, "Draft deleted", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete Draft", tint = MaterialTheme.colorScheme.error)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { editingDraft = draft },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = { schedulingDraft = draft },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Schedule", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        busyDraftId = draft.id
                                        scope.launch {
                                            val ok = FlagshipFeaturesRepository.publishDraftNow(draft)
                                            busyDraftId = null
                                            Toast.makeText(
                                                context,
                                                if (ok) "Draft published live!" else "Failed to publish draft",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    enabled = !isBusy && draft.mediaUri.isNotBlank(),
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A5F))
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(modifier = Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Text("Publish", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDraftSheet) {
        CreateDraftBottomSheet(
            userId = currentUserId,
            onDismiss = { showCreateDraftSheet = false }
        )
    }

    editingDraft?.let { target ->
        EditDraftDialog(
            draft = target,
            onDismiss = { editingDraft = null },
            onSave = { newCaption, newType ->
                scope.launch {
                    val ok = FlagshipFeaturesRepository.updateDraft(target.id, newCaption, newType)
                    Toast.makeText(context, if (ok) "Draft updated" else "Update failed", Toast.LENGTH_SHORT).show()
                    editingDraft = null
                }
            }
        )
    }

    schedulingDraft?.let { target ->
        ScheduleTimePickerDialog(
            title = "Schedule Draft",
            initialCaption = target.caption,
            onDismiss = { schedulingDraft = null },
            onConfirm = { _, scheduledAtMs ->
                scope.launch {
                    val ok = FlagshipFeaturesRepository.scheduleDraft(target, scheduledAtMs)
                    Toast.makeText(context, if (ok) "Moved to Scheduled Content!" else "Scheduling failed", Toast.LENGTH_SHORT).show()
                    schedulingDraft = null
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateDraftBottomSheet(
    userId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedType by remember { mutableStateOf("post") }
    var caption by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

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
            Text("Create New Draft", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
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
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = Color(0xFFFF2A5F), modifier = Modifier.size(38.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Tap to pick Photo or Reel", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = selectedType == "post",
                    onClick = { selectedType = "post" },
                    label = { Text("Photo Post") }
                )
                FilterChip(
                    selected = selectedType == "reel",
                    onClick = { selectedType = "reel" },
                    label = { Text("Reel Video") }
                )
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { if (it.length <= 500) caption = it },
                label = { Text("Caption & #Hashtags") },
                placeholder = { Text("Write your caption for later...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                minLines = 2,
                maxLines = 4
            )

            Button(
                onClick = {
                    val uri = selectedMediaUri
                    if (uri == null) {
                        Toast.makeText(context, "Please pick a photo or video first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSaving = true
                    scope.launch {
                        val uploadedUrl = try {
                            MediaRepository(context).uploadMedia(uri)
                        } catch (_: Exception) { null }
                        val finalUrl = uploadedUrl ?: uri.toString()
                        val ok = FlagshipFeaturesRepository.saveDraft(userId, selectedType, finalUrl, caption)
                        isSaving = false
                        if (ok) {
                            Toast.makeText(context, "Saved to Drafts Studio!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Could not save draft", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A5F))
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Save Draft", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun EditDraftDialog(
    draft: ContentDraft,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var caption by remember { mutableStateOf(draft.caption) }
    var type by remember { mutableStateOf(draft.type) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Draft", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "post", onClick = { type = "post" }, label = { Text("Post") })
                    FilterChip(selected = type == "reel", onClick = { type = "reel" }, label = { Text("Reel") })
                }
                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 500) caption = it },
                    label = { Text("Caption") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(caption, type) }) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
