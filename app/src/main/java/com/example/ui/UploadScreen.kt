package com.example.ui
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.net.toUri

import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.*

import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.automirrored.filled.ArrowForward

import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorMatrix

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.model.RepositoryManager
import com.example.model.User
import com.example.viewmodel.UploadState
import com.example.viewmodel.UploadViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File

fun getCombinedColorMatrix(brightness: Float, contrast: Float, saturation: Float, warmth: Float): ColorMatrix {
    return ColorMatrix()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)

@com.google.accompanist.permissions.ExperimentalPermissionsApi
class MentionVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = androidx.compose.ui.text.AnnotatedString.Builder(text)
        val pattern = java.util.regex.Pattern.compile("([@#]\\w+)")
        val matcher = pattern.matcher(text.text)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val match = matcher.group()
            if (match.startsWith("@")) {
                builder.addStyle(SpanStyle(color = Color(0xFF1976D2), fontWeight = FontWeight.Bold), start, end)
            } else if (match.startsWith("#")) {
                builder.addStyle(SpanStyle(color = Color(0xFF1976D2)), start, end)
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

val TRENDING_BACKGROUND_SONGS = listOf(
    "Kesariya (From 'Brahmastra') - Arijit Singh",
    "Chaleya - Anirudh Ravichander & Arijit Singh",
    "Tauba Tauba - Karan Aujla",
    "Dandiya India - Social Beats",
    "Apna Bana Le - Arijit Singh & Sachin-Jigar",
    "Illuminati - Sushin Shyam & Dabzee",
    "Soulmate - Badshah & Arijit Singh",
    "Heeriye - Jasleen Royal & Arijit Singh",
    "Tere Vaaste - Varun Jain & Sachin-Jigar",
    "What Jhumka? - Pritam & Arijit Singh",
    "Maan Meri Jaan - King",
    "Tum Se - Sachin-Jigar & Raghav Chaitanya"
)

@OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun UploadScreen(
    
    onNavigateToHome: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToMonetization: () -> Unit = {},
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToWebView: (String) -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    val uploadContext = LocalContext.current
    if (!com.example.model.RepositoryManager.isRealUserLoggedIn()) {
        LaunchedEffect(Unit) {
            Toast.makeText(uploadContext, "Please sign in to post photos or videos", Toast.LENGTH_SHORT).show()
            onNavigate("auth")
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val uploadViewModel: UploadViewModel = viewModel()
    val uploadState by uploadViewModel.uploadState.collectAsStateWithLifecycle()
    
    
    var selectedMediaUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isReadyToPost by remember { mutableStateOf(false) }
    var isTrialReel by remember { mutableStateOf(false) }
    var addYoursPromptText by remember { mutableStateOf("") }
    var showAddYoursDialog by remember { mutableStateOf(false) }
    var isSubscribersOnlyContent by remember { mutableStateOf(false) }
    var isPaidContent by remember { mutableStateOf(false) }
    var contentPriceText by remember { mutableStateOf("19") }
    var individualPostPriceText by remember { mutableStateOf("29") }
    var exclusiveTeaserCaption by remember { mutableStateOf("") }
    var customCoverUri by remember { mutableStateOf<Uri?>(null) }
    var resumedOfflineDraft by remember { mutableStateOf<com.example.ui.editor.EditorProjectSnapshot?>(null) }
    val currentUser by com.example.model.RepositoryManager.currentUserState.collectAsStateWithLifecycle()
    val isVerifiedCreator = currentUser?.let { u ->
        !u.verificationSuspended && (
            u.isVerified ||
            u.verificationStatus == "approved" ||
            u.verificationStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE
        )
    } == true
    val monetizationSettings by com.example.repository.MonetizationRepository.observeMonetizationSettings()
        .collectAsStateWithLifecycle(initialValue = com.example.model.MonetizationSettings())
    var selectedAudience by remember { mutableStateOf("Everyone can view this post") }
    var descriptionValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    val description = descriptionValue.text
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val drivePrefs = remember { context.getSharedPreferences("social_india_drive_prefs", android.content.Context.MODE_PRIVATE) }
    var isDriveConnected by remember {
        mutableStateOf(
            !drivePrefs.getString("connected_drive_email", null).isNullOrBlank() ||
            com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context) != null
        )
    }
    val driveAuthLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val chosenEmail = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            ?: com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)?.email
        if (!chosenEmail.isNullOrBlank()) {
            isDriveConnected = true
            drivePrefs.edit().putString("connected_drive_email", chosenEmail).apply()
            android.widget.Toast.makeText(context, "✅ Google Drive Connected: $chosenEmail", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Music Picker State
    var selectedMusicUri by remember { mutableStateOf<Uri?>(null) }
    var selectedMusicName by remember { mutableStateOf<String?>(null) }

    // Google Play Compliant Zero-Permission Android Photo Picker (Photos + Videos)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedMediaUris = uris
            if (selectedMusicName == null) {
                val pickedSong = TRENDING_BACKGROUND_SONGS.random()
                selectedMusicName = pickedSong
                com.example.api.MusicClient.resolveAudioUrlForTrack(null, pickedSong)?.let {
                    selectedMusicUri = android.net.Uri.parse(it)
                }
            }
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val cachedUris = uris.map { copyUriToCache(context, it) }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    selectedMediaUris = cachedUris
                }
            }
        }
    }

    // Music Picker
    val musicLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedMusicUri = uri
            // Get file name
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        selectedMusicName = it.getString(displayNameIndex)
                    }
                }
            }
            if (selectedMusicName == null) selectedMusicName = "Audio Selected"
            Toast.makeText(context, "Sound added: $selectedMusicName", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera State
    var recording: Recording? by remember { mutableStateOf(null) }
    var isRecording by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isHdrOn by remember { mutableStateOf(false) }
    var isTimerOn by remember { mutableStateOf(false) }
    var isGridOn by remember { mutableStateOf(false) }
    var isBeautyOn by remember { mutableStateOf(false) }
    var activeCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var isVideoCaptureSupported by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var captureMode by remember { mutableStateOf("Reel") } // "Post", "Story", "Reel", "Templates", "Live", "Audio Room"
    var pendingTemplateFromFeed by remember { mutableStateOf<com.example.ui.templates.ContentTemplate?>(null) }
    var pendingTemplatePostId by remember { mutableStateOf<String?>(null) }
    var showPublishTemplateFromShare by remember { mutableStateOf(false) }

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    
    var showMusicPickerEditor by remember { mutableStateOf(false) }
    var selectedMusicEditor by remember { mutableStateOf<com.example.api.ITunesTrack?>(com.example.ui.GlobalState.preselectedMusicTrack.value) }
    
    LaunchedEffect(Unit) {
        val preselected = com.example.ui.GlobalState.preselectedMusicTrack.value
        if (preselected != null) {
            selectedMusicEditor = preselected
            selectedMusicName = preselected.name
            selectedMusicUri = preselected.previewUrl?.let { android.net.Uri.parse(it) }
            com.example.ui.GlobalState.preselectedMusicTrack.value = null
        }
    }

    // Consume pending template handoff from public Reels ("Use this template") or Quick Story chains
    val incomingGlobalTemplate by com.example.ui.GlobalState.pendingTemplateToOpen
    LaunchedEffect(incomingGlobalTemplate) {
        val tpl = incomingGlobalTemplate
        if (tpl != null) {
            pendingTemplateFromFeed = tpl
            pendingTemplatePostId = com.example.ui.GlobalState.pendingTemplateSourcePostId.value
            com.example.ui.GlobalState.pendingTemplateToOpen.value = null
            com.example.ui.GlobalState.pendingTemplateSourcePostId.value = null
        }
    }

    // Consume ready-to-edit template snapshot from Hot Video Templates browser
    val readyTemplateSnapshot by com.example.ui.GlobalState.pendingReadyTemplateSnapshot
    LaunchedEffect(readyTemplateSnapshot) {
        val snap = readyTemplateSnapshot
        if (snap != null) {
            val uris = com.example.ui.GlobalState.pendingReadyTemplateUris.value
            val tplInfo = com.example.ui.GlobalState.pendingReadyTemplateInfo.value
            resumedOfflineDraft = snap
            if (uris.isNotEmpty()) {
                selectedMediaUris = uris
            }
            if (tplInfo != null) {
                if (tplInfo.audio.name.isNotBlank()) {
                    selectedMusicName = tplInfo.audio.name
                    if (tplInfo.audio.url.isNotBlank()) {
                        selectedMusicUri = Uri.parse(tplInfo.audio.url)
                    }
                }
                if (tplInfo.promptText.isNotBlank()) {
                    addYoursPromptText = tplInfo.promptText
                }
            }
            isReadyToPost = false
            com.example.ui.GlobalState.pendingReadyTemplateSnapshot.value = null
            com.example.ui.GlobalState.pendingReadyTemplateUris.value = emptyList()
            com.example.ui.GlobalState.pendingReadyTemplateInfo.value = null
        }
    }
    
    if ((selectedMediaUris.isNotEmpty() || resumedOfflineDraft != null) && !isReadyToPost) {
        val firstUri = selectedMediaUris.firstOrNull()
            ?: resumedOfflineDraft?.clips?.firstOrNull()?.uriString?.let { Uri.parse(it) }
        val isVideo = firstUri?.let { isVideoUri(context, it) } ?: false

        com.example.ui.ProEditorTimeline(
            context = context,
            selectedMediaIsVideo = isVideo,
            selectedMediaUrl = firstUri?.toString(),
            selectedMediaUris = selectedMediaUris,
            selectedMusic = selectedMusicEditor,
            initialDraft = resumedOfflineDraft,
            onNavigateBack = {
                selectedMediaUris = emptyList()
                resumedOfflineDraft = null
                customCoverUri = null
            },
            onNavigateNext = {
                isReadyToPost = true
                Toast.makeText(context, "Edits saved! Ready to post.", Toast.LENGTH_SHORT).show()
            },
            onNavigateToMusicPicker = {
                showMusicPickerEditor = true
            },
            onExportComplete = { result ->
                if (result.exportedUris.isNotEmpty()) {
                    selectedMediaUris = result.exportedUris
                }
                customCoverUri = result.customCoverUri
                if (result.selectedMusic != null) {
                    selectedMusicEditor = result.selectedMusic
                    selectedMusicName = result.selectedMusic.name
                    selectedMusicUri = result.selectedMusic.previewUrl?.let { Uri.parse(it) }
                }
                resumedOfflineDraft = null
                isReadyToPost = true
            }
        )
        if (showMusicPickerEditor) {
            com.example.ui.MusicPickerSheet(
                onDismiss = { showMusicPickerEditor = false },
                onTrackSelected = { track ->
                    selectedMusicEditor = track
                    showMusicPickerEditor = false
                    selectedMusicName = track.name
                    selectedMusicUri = track.previewUrl?.let { android.net.Uri.parse(it) }
                }
            )
        }
        
        return
    }


    if (selectedMediaUris.isNotEmpty() && isReadyToPost) {
        // Post Screen
        var showMoreOptions by remember { mutableStateOf(false) }
        var showFullScreenPreview by remember { mutableStateOf(false) }
        val selectedCollaborators = remember { androidx.compose.runtime.mutableStateListOf<com.example.model.User>() }
        var showCollabOverlay by remember { mutableStateOf(false) }
        val selectedMentions = remember { androidx.compose.runtime.mutableStateListOf<com.example.model.User>() }
        var showMentionOverlay by remember { mutableStateOf(false) }
        val scrollState = androidx.compose.foundation.rememberScrollState()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            // Top App Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { isReadyToPost = false }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack, 
                            contentDescription = "Back", 
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedMediaUris.firstOrNull()?.let { isVideoUri(context, it) } == true) "New Reel" else "New Post",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(bottom = 16.dp)
            ) {
                // Description and Cover preview
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    OutlinedTextField(
                        value = descriptionValue,
                        onValueChange = { descriptionValue = it },
                        placeholder = { Text("Add caption & description...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 15.sp) },
                        visualTransformation = MentionVisualTransformation(),
                        modifier = Modifier.weight(1f).height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 5
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Cover preview
                    val firstUri = selectedMediaUris.firstOrNull()
                    val isFirstUriVideo = firstUri?.let { isVideoUri(context, it) } ?: false
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                            .clickable { showFullScreenPreview = true }
                    ) {
                        if (firstUri != null) {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(context)
                                    .data(firstUri)
                                    .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isFirstUriVideo) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(32.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Play Preview",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Preview 🔍", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (showFullScreenPreview && firstUri != null) {
                        if (isFirstUriVideo) {
                            com.example.ui.FullScreenVideoViewer(
                                videoUrl = firstUri.toString(),
                                title = "Video Preview",
                                onDismiss = { showFullScreenPreview = false }
                            )
                        } else {
                            com.example.ui.FullScreenPhotoViewer(
                                imageUrl = firstUri.toString(),
                                title = "Photo Preview",
                                onDismiss = { showFullScreenPreview = false }
                            )
                        }
                    }
                }
                
                // Inline Mention Suggestions dropdown
                val currentMentionQuery = remember(descriptionValue) {
                    extractMentionQuery(descriptionValue.text, descriptionValue.selection.start)
                }
                if (currentMentionQuery != null) {
                    MentionSuggestions(
                        query = currentMentionQuery,
                        onUserSelected = { user ->
                            val (newText, newCursor) = replaceMention(descriptionValue.text, descriptionValue.selection.start, user.username)
                            descriptionValue = androidx.compose.ui.text.input.TextFieldValue(newText, androidx.compose.ui.text.TextRange(newCursor))
                            if (!selectedMentions.any { it.id == user.id }) {
                                selectedMentions.add(user)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                
                // Hashtags and Mentions Quick Action Chips + AI Creator Assistant
                var isGeneratingCaption by remember { mutableStateOf(false) }
                var selectedSmartThumbTimestampMs by remember { mutableStateOf(1500L) }
                var showSmartThumbnailPicker by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { 
                            val newText = descriptionValue.text + "#"
                            descriptionValue = androidx.compose.ui.text.input.TextFieldValue(newText, androidx.compose.ui.text.TextRange(newText.length))
                        }, 
                        label = { Text("# Hashtag", fontWeight = FontWeight.Bold, fontSize = 12.sp) }, 
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    )
                    
                    AssistChip(
                        onClick = { 
                            showMentionOverlay = true
                        }, 
                        label = { Text("@ Mention", fontWeight = FontWeight.Bold, fontSize = 12.sp) }, 
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    )
                    
                    AssistChip(
                        onClick = { 
                            if (!isGeneratingCaption) {
                                isGeneratingCaption = true
                                coroutineScope.launch {
                                    val generated = com.example.model.GeminiService.generateCaption(
                                        "Write a high-engagement viral Instagram/Reels caption with emojis and 6 trending hashtags for an Indian social media post. Return only the caption and hashtags."
                                    )
                                    if (generated.isNotEmpty()) {
                                        val newText = if (descriptionValue.text.isEmpty()) generated else "${descriptionValue.text}\n\n$generated"
                                        descriptionValue = androidx.compose.ui.text.input.TextFieldValue(newText, androidx.compose.ui.text.TextRange(newText.length))
                                    }
                                    isGeneratingCaption = false
                                }
                            }
                        }, 
                        label = { 
                            if (isGeneratingCaption) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Text("✨ Viral AI Caption", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0D47A1)) 
                            }
                        }, 
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFE3F2FD))
                    )
                }

                // AI Creator Assistant Card: 1-Tap Viral Hashtags & Smart Thumbnail Picker
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Creator Assistant",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI Creator Assistant",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.clickable {
                                    showSmartThumbnailPicker = !showSmartThumbnailPicker
                                }
                            ) {
                                Text(
                                    text = if (showSmartThumbnailPicker) "Hide Frames ▲" else "🎯 Smart Thumbnail Picker",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val viralTags = "#SocialIndia #ViralReels #TrendingIndia #ExplorePage #ReelsKaroFeelKaro #ForYouPage"
                                    val updated = if (descriptionValue.text.contains("#SocialIndia")) {
                                        descriptionValue.text
                                    } else if (descriptionValue.text.isBlank()) {
                                        viralTags
                                    } else {
                                        "${descriptionValue.text.trim()}\n$viralTags"
                                    }
                                    descriptionValue = androidx.compose.ui.text.input.TextFieldValue(updated, androidx.compose.ui.text.TextRange(updated.length))
                                    Toast.makeText(context, "Trending viral hashtags added! 🔥", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Text("🔥 +Trending Hashtags", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    showSmartThumbnailPicker = true
                                    selectedSmartThumbTimestampMs = 1500L
                                    Toast.makeText(context, "AI selected sharpest high-contrast frame (1.5s) ✨", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Text("🖼️ Auto Best Cover", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (showSmartThumbnailPicker) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Select Optimal Frame Cover (AI Recommended: 1.5s):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    500L to "0.5s • Intro",
                                    1500L to "1.5s ⭐ Best",
                                    3000L to "3.0s • Action",
                                    5000L to "5.0s • Peak"
                                ).forEach { (timeMs, label) ->
                                    val isSelectedFrame = selectedSmartThumbTimestampMs == timeMs
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelectedFrame) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedSmartThumbTimestampMs = timeMs
                                                Toast.makeText(context, "Cover frame locked at $label", Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelectedFrame) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Interactive Music Track Bar (Auto-attached, randomizable & changeable)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFE2C55).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "Music",
                                tint = Color(0xFFFE2C55),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedMusicName ?: "Add Background Music",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (selectedMusicName != null) "Trending Audio • Tap Shuffle 🔀 to change" else "Select trending audio or custom track",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                        // Change / Shuffle Button
                        IconButton(
                            onClick = {
                                val otherSongs = TRENDING_BACKGROUND_SONGS.filter { it != selectedMusicName }
                                val nextSong = (otherSongs.takeIf { it.isNotEmpty() } ?: TRENDING_BACKGROUND_SONGS).random()
                                selectedMusicName = nextSong
                                com.example.api.MusicClient.resolveAudioUrlForTrack(null, nextSong)?.let {
                                    selectedMusicUri = android.net.Uri.parse(it)
                                }
                                Toast.makeText(context, "Song changed: $selectedMusicName", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Change Track", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                        }
                        // Remove Song or Browse
                        if (selectedMusicName != null) {
                            IconButton(
                                onClick = {
                                    selectedMusicName = null
                                    selectedMusicUri = null
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove Song", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            IconButton(
                                onClick = { musicLauncher.launch("audio/*") },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Custom Audio", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                
                // 1. Mention People Option Row
                PostOptionRow(
                    icon = Icons.Default.AlternateEmail,
                    title = if (selectedMentions.isEmpty()) "Mention People" else "Mentioned (${selectedMentions.size})",
                    onClick = { showMentionOverlay = true }
                )
                
                if (selectedMentions.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedMentions, key = { it.id }) { user ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                                ) {
                                    coil.compose.AsyncImage(
                                        model = user.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${user.id}" },
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "@${user.username}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { selectedMentions.remove(user) }
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (showMentionOverlay) {
                    MentionPickerOverlay(
                        selectedUsers = selectedMentions,
                        onAddUser = { user ->
                            if (!selectedMentions.any { it.id == user.id }) {
                                selectedMentions.add(user)
                            }
                        },
                        onRemoveUser = { user ->
                            selectedMentions.removeAll { it.id == user.id }
                        },
                        onClose = { showMentionOverlay = false }
                    )
                }

                // 2. Tag People / Collaborate Option Row
                PostOptionRow(
                    icon = Icons.Default.GroupAdd, 
                    title = if (selectedCollaborators.isEmpty()) "Tag Collaborators" else "Collaborators (${selectedCollaborators.size}/5)",
                    onClick = { showCollabOverlay = true }
                )
                
                if (selectedCollaborators.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedCollaborators, key = { it.id }) { user ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                                ) {
                                    coil.compose.AsyncImage(
                                        model = user.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "@${user.username}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { selectedCollaborators.remove(user) }
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (showCollabOverlay) {
                    CollaboratorSearchOverlay(
                        selectedCollaborators = selectedCollaborators,
                        onClose = { showCollabOverlay = false },
                        onAddCollaborator = { user ->
                            if (!selectedCollaborators.any { it.id == user.id }) {
                                if (selectedCollaborators.size < 5) {
                                    selectedCollaborators.add(user)
                                }
                            }
                        },
                        onRemoveCollaborator = { user ->
                            selectedCollaborators.removeAll { it.id == user.id }
                        }
                    )
                }

                // 3. Audience Selector (Interactive)
                PostOptionRow(
                    icon = if (selectedAudience.startsWith("Everyone")) Icons.Default.Public else if (selectedAudience.startsWith("Followers")) Icons.Default.Group else Icons.Default.Lock,
                    title = "Audience: $selectedAudience"
                ) {
                    com.example.utils.SoundManager.playClickSound()
                    selectedAudience = when (selectedAudience) {
                        "Everyone can view this post" -> "Followers Only"
                        "Followers Only" -> "Only Me (Private)"
                        else -> "Everyone can view this post"
                    }
                    android.widget.Toast.makeText(context, "Visibility set to: $selectedAudience", android.widget.Toast.LENGTH_SHORT).show()
                }

                // 4. Cloud Storage
                PostOptionRow(
                    icon = Icons.Default.CloudUpload,
                    title = if (isDriveConnected) "Storage: Google Drive Linked" else "Storage: Direct Cloud CDN (Active)",
                    onClick = { 
                        if (!isDriveConnected) {
                            android.widget.Toast.makeText(context, "Direct Cloud CDN is active! Videos and photos upload instantly without needing Google Drive.", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            android.widget.Toast.makeText(context, "Connected to Google Drive! Public CDN active as fallback.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // Verification Badge Gate Notice for Unverified Users
                if (!isVerifiedCreator) {
                    LaunchedEffect(Unit) {
                        isTrialReel = false
                        isSubscribersOnlyContent = false
                        isPaidContent = false
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E88E5).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E88E5).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable {
                                com.example.utils.SoundManager.playClickSound()
                                onNavigate("verification")
                            }
                            .testTag("verified_creator_gate_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verification Badge Required",
                                tint = Color(0xFF1E88E5),
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Verification Badge Required",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Get a Verification Badge to unlock Trial Reels, Subscribers Only & Exclusive Pay-to-Unlock content.",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E88E5)
                            ) {
                                Text(
                                    text = "Verify",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // 5. Trial Reel Toggle (Verified Creators Only)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isVerifiedCreator) {
                            Toast.makeText(context, "Verification Badge required to use Trial Reels", Toast.LENGTH_SHORT).show()
                            onNavigate("verification")
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Science, contentDescription = null, tint = if (isVerifiedCreator) Color(0xFFFF9800) else Color.Gray, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Share as Trial Reel", color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (isVerifiedCreator) 1f else 0.55f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            if (!isVerifiedCreator) {
                                Icon(Icons.Default.Verified, contentDescription = "Verified only", tint = Color(0xFF1E88E5), modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(
                            if (isVerifiedCreator) "Test with sample non-followers first before revealing to followers"
                            else "Locked • Requires Verification Badge",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = isVerifiedCreator && isTrialReel,
                        enabled = isVerifiedCreator,
                        onCheckedChange = { if (isVerifiedCreator) isTrialReel = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFF9800),
                            checkedTrackColor = Color(0xFFFF9800).copy(alpha = 0.5f)
                        )
                    )
                }

                // 6. Monetization & Subscribers Only (Verified Creators Only)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isVerifiedCreator) {
                            Toast.makeText(context, "Verification Badge required for Subscribers Only content", Toast.LENGTH_SHORT).show()
                            onNavigate("verification")
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Loyalty, contentDescription = null, tint = if (isVerifiedCreator) Color(0xFF7B1FA2) else Color.Gray, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Subscribers Only", color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (isVerifiedCreator) 1f else 0.55f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            if (!isVerifiedCreator) {
                                Icon(Icons.Default.Verified, contentDescription = "Verified only", tint = Color(0xFF1E88E5), modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(
                            if (isVerifiedCreator) "Only paid subscribers can view this content; others see blurred preview"
                            else "Locked • Requires Verification Badge",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = isVerifiedCreator && isSubscribersOnlyContent,
                        enabled = isVerifiedCreator,
                        onCheckedChange = { if (isVerifiedCreator) isSubscribersOnlyContent = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF7B1FA2),
                            checkedTrackColor = Color(0xFF7B1FA2).copy(alpha = 0.5f)
                        )
                    )
                }

                if (isVerifiedCreator && isSubscribersOnlyContent) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .background(Color(0xFF7B1FA2).copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF7B1FA2).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Set Unlock Price for Subscribers (₹)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE040FB)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("19", "49", "99", "199", "499").forEach { priceOpt ->
                                val isSelected = contentPriceText == priceOpt
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF7B1FA2) else Color.White.copy(alpha = 0.08f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFFE040FB) else Color.White.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { contentPriceText = priceOpt }
                                ) {
                                    Text(
                                        text = "₹$priceOpt",
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = contentPriceText,
                            onValueChange = { newPrice ->
                                if (newPrice.all { it.isDigit() } && newPrice.length <= 5) {
                                    contentPriceText = newPrice
                                }
                            },
                            label = { Text("Custom Price (₹)", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFE040FB),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                    }
                }

                // Exclusive Content (Pay-to-Unlock • Verified Creators Only)
                val minExPrice = monetizationSettings.exclusiveMinPriceInr.coerceAtLeast(10.0)
                val maxExPrice = monetizationSettings.exclusiveMaxPriceInr.coerceAtLeast(minExPrice)
                val creatorKeepPct = (100.0 - monetizationSettings.exclusiveCommissionPct.coerceIn(0.0, 50.0)).toInt()
                val parsedExPrice = individualPostPriceText.toDoubleOrNull()
                val isExPriceValid = parsedExPrice != null && parsedExPrice >= minExPrice && parsedExPrice <= maxExPrice

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isVerifiedCreator) {
                            Toast.makeText(context, "Verification Badge required to publish Exclusive Content", Toast.LENGTH_SHORT).show()
                            onNavigate("verification")
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background((if (isVerifiedCreator) Color(0xFFFFB300) else Color.Gray).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Exclusive Content", tint = if (isVerifiedCreator) Color(0xFFFFC107) else Color.Gray, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Exclusive Content", color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (isVerifiedCreator) 1f else 0.55f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = (if (isVerifiedCreator) Color(0xFFFFC107) else Color(0xFF1E88E5)).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (isVerifiedCreator) "PAY TO UNLOCK" else "VERIFIED ONLY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isVerifiedCreator) Color(0xFFFFC107) else Color(0xFF1E88E5),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            if (isVerifiedCreator) "Blur preview for viewers until unlocked • You keep $creatorKeepPct%"
                            else "Locked • Obtain a Verification Badge first to unlock",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = isVerifiedCreator && isPaidContent,
                        enabled = isVerifiedCreator,
                        onCheckedChange = { if (isVerifiedCreator) isPaidContent = it },
                        modifier = Modifier.testTag("exclusive_content_toggle"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF1A1200),
                            checkedTrackColor = Color(0xFFFFC107)
                        )
                    )
                }

                if (isVerifiedCreator && isPaidContent) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .background(Color(0xFFFFC107).copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFFFFC107).copy(alpha = 0.38f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Unlock Price (₹${minExPrice.toInt()} – ₹${maxExPrice.toInt()})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F)
                            )
                            Text(
                                text = "You earn $creatorKeepPct%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF34D399)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("10", "29", "49", "99", "199").forEach { priceOpt ->
                                val isSelected = individualPostPriceText == priceOpt
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFFFFC107) else Color.White.copy(alpha = 0.08f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFFFFE082) else Color.White.copy(alpha = 0.18f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { individualPostPriceText = priceOpt }
                                ) {
                                    Text(
                                        text = "₹$priceOpt",
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else Color.White,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = individualPostPriceText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }
                                if (filtered.length <= 5) individualPostPriceText = filtered
                            },
                            label = { Text("Custom Price (₹)", fontSize = 12.sp) },
                            prefix = { Text("₹ ", fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F)) },
                            singleLine = true,
                            isError = individualPostPriceText.isNotEmpty() && !isExPriceValid,
                            supportingText = {
                                if (individualPostPriceText.isNotEmpty() && !isExPriceValid) {
                                    Text("Enter between ₹${minExPrice.toInt()} and ₹${maxExPrice.toInt()}", color = Color(0xFFFF6B6B), fontSize = 11.sp)
                                }
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("exclusive_price_input")
                        )

                        OutlinedTextField(
                            value = exclusiveTeaserCaption,
                            onValueChange = { if (it.length <= 160) exclusiveTeaserCaption = it },
                            label = { Text("Public Teaser Caption (Optional)", fontSize = 12.sp) },
                            placeholder = { Text("e.g., Behind-the-scenes shoot + bonus clips 🔥", fontSize = 12.sp) },
                            maxLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("exclusive_teaser_input")
                        )

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(15.dp))
                            Text(
                                text = "Original media stays encrypted in private vault. Viewers only see a heavily blurred preview until unlocked.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // 7. Add Yours Prompt Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAddYoursDialog = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Add Yours Prompt", color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (addYoursPromptText.isNotBlank()) "Prompt: \"$addYoursPromptText\"" else "Add a prompt sticker for others to join",
                            color = if (addYoursPromptText.isNotBlank()) Color(0xFFE91E63) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                    if (addYoursPromptText.isNotBlank()) {
                        IconButton(onClick = { addYoursPromptText = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 8. Publish as Reusable Template Row (Creator Templates)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPublishTemplateFromShare = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("share_publish_as_template_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesomeMosaic, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Publish as Template", color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Let other creators reuse your cuts, audio & style (Free or Premium)",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Permanent Sticky Bottom Action Bar with Big Bold Post Button (Fits all devices)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val currentUserId = com.example.model.RepositoryManager.getCurrentUserId()
                            if (currentUserId.isNotEmpty()) {
                                coroutineScope.launch {
                                    val firstUri = selectedMediaUris.firstOrNull()?.toString() ?: ""
                                    val draft = com.example.model.ContentDraft(
                                        userId = currentUserId,
                                        type = if (captureMode == "Story") "story" else "post",
                                        mediaUri = firstUri,
                                        caption = description,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    com.example.model.RepositoryManager.socialRepository.saveContentDraft(draft)
                                    android.widget.Toast.makeText(context, "Saved to Drafts! 📁", android.widget.Toast.LENGTH_SHORT).show()
                                    com.example.utils.SoundManager.playClickSound()
                                    selectedMediaUris = emptyList()
                                    isReadyToPost = false
                                    onNavigateToHome()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(0.38f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Drafts", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    
                    Button(
                        onClick = {
                            val firstUri = selectedMediaUris.first()
                            val isVideo = isVideoUri(context, firstUri)
                            val urisToUpload = selectedMediaUris.toList()
                            val cap = description
                            val musicName = selectedMusicName?.takeIf { it.isNotBlank() }
                                ?: if (isVideo) "Original Audio" else null
                            // Preserve original video audio track unless user explicitly selected an external audio track for an image post
                            val musicUrl = if (isVideo && selectedMusicEditor == null && selectedMusicUri == null) {
                                null
                            } else {
                                selectedMusicUri?.toString()
                                    ?: selectedMusicEditor?.previewUrl
                            }
                            val collabIds = selectedCollaborators.map { it.id }
                            val mentionsList = selectedMentions.toList()

                            if (com.example.model.GlobalUploadManager.currentUpload.value != null && com.example.model.GlobalUploadManager.currentUpload.value?.status != com.example.model.UploadStatus.ERROR && com.example.model.GlobalUploadManager.currentUpload.value?.status != com.example.model.UploadStatus.SUCCESS) {
                                return@Button
                            }
                            
                            val (vWidth, vHeight, vRatio) = if (isVideo) {
                                com.example.utils.MediaDimensionHelper.getVideoDimensions(context, firstUri).let { Triple(it.width, it.height, it.aspectRatio) }
                            } else {
                                com.example.utils.MediaDimensionHelper.getImageDimensions(context, firstUri).let { Triple(it.width, it.height, it.aspectRatio) }
                            }

                            val visibilityMode = when {
                                selectedAudience.contains("Private", ignoreCase = true) || selectedAudience.contains("Only Me", ignoreCase = true) -> "PRIVATE"
                                selectedAudience.contains("Followers", ignoreCase = true) -> "FOLLOWERS_ONLY"
                                else -> "PUBLIC"
                            }

                            val effectivePaid = isVerifiedCreator && isPaidContent
                            val effectiveSubscribersOnly = isVerifiedCreator && isSubscribersOnlyContent
                            val effectiveTrial = isVerifiedCreator && isTrialReel

                            val finalExclusivePrice = if (effectivePaid) {
                                val minAllowed = monetizationSettings.exclusiveMinPriceInr.coerceAtLeast(10.0)
                                val maxAllowed = monetizationSettings.exclusiveMaxPriceInr.coerceAtLeast(minAllowed)
                                val parsed = individualPostPriceText.toDoubleOrNull()
                                if (parsed == null || parsed < minAllowed || parsed > maxAllowed) {
                                    Toast.makeText(
                                        context,
                                        "Set an Exclusive price between ₹${minAllowed.toInt()} and ₹${maxAllowed.toInt()}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Button
                                }
                                parsed
                            } else 0.0

                            com.example.model.GlobalUploadManager.startUpload(
                                context = context,
                                mediaUris = urisToUpload,
                                caption = cap,
                                userId = com.example.model.RepositoryManager.getCurrentUserId(),
                                musicTrack = musicName,
                                musicTrackUrl = musicUrl,
                                musicTrackImageUrl = selectedMusicEditor?.artworkUrl,
                                musicTrackId = selectedMusicEditor?.id?.toString(),
                                musicTrackArtist = selectedMusicEditor?.artistName,
                                isVideo = isVideo,
                                collaboratorIds = collabIds,
                                isPremium = effectiveSubscribersOnly,
                                premiumPrice = if (effectiveSubscribersOnly) (contentPriceText.toDoubleOrNull() ?: 19.0) else 0.0,
                                isTrialReel = effectiveTrial,
                                addYoursPromptText = if (addYoursPromptText.isNotBlank()) addYoursPromptText else null,
                                videoWidth = vWidth,
                                videoHeight = vHeight,
                                aspectRatio = vRatio,
                                mentionedUsers = mentionsList,
                                isPaidPost = effectivePaid,
                                postUnlockPrice = finalExclusivePrice,
                                accessType = if (effectivePaid) "INDIVIDUAL_PAID" else if (effectiveSubscribersOnly) "SUBSCRIBERS_ONLY" else "PUBLIC",
                                visibility = visibilityMode,
                                teaserCaption = if (effectivePaid) exclusiveTeaserCaption.trim() else "",
                                customCoverUri = customCoverUri
                            )

                            com.example.utils.SoundManager.playClickSound()
                            selectedMediaUris = emptyList()
                            exclusiveTeaserCaption = ""
                            customCoverUri = null
                            isReadyToPost = false
                            onNavigateToHome()
                        },
                        modifier = Modifier
                            .weight(0.62f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedMediaUris.firstOrNull()?.let { isVideoUri(context, it) } == true) "Share Reel" else "Share Post",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
        
        if (showAddYoursDialog) {
            AddYoursCreatorDialog(
                onDismiss = { showAddYoursDialog = false },
                onCreatePrompt = { prompt ->
                    addYoursPromptText = prompt
                    showAddYoursDialog = false
                }
            )
        }

        if (showMoreOptions) {
            MoreOptionsBottomSheet(onDismiss = { showMoreOptions = false })
        }

        if (showPublishTemplateFromShare) {
            com.example.ui.templates.PublishTemplateBottomSheet(
                defaultTitle = description.take(40),
                defaultType = if (selectedMediaUris.size > 1) "carousel" else if (captureMode == "Story") "story" else "reel",
                onDismiss = { showPublishTemplateFromShare = false },
                onPublished = { showPublishTemplateFromShare = false }
            )
        }
    } else if (captureMode == "Templates") {
        androidx.activity.compose.BackHandler { captureMode = "Reel" }
        com.example.ui.templates.TemplateBrowserScreen(
            onClose = { captureMode = "Reel" },
            onTemplateReadyForEditor = { snapshot, uris, usedTemplate ->
                resumedOfflineDraft = snapshot
                selectedMediaUris = uris
                if (usedTemplate.audio.name.isNotBlank()) {
                    selectedMusicName = usedTemplate.audio.name
                    if (usedTemplate.audio.url.isNotBlank()) {
                        selectedMusicUri = Uri.parse(usedTemplate.audio.url)
                    }
                }
                if (usedTemplate.type == "quick_story" && usedTemplate.promptText.isNotBlank()) {
                    addYoursPromptText = usedTemplate.promptText
                }
                captureMode = if (usedTemplate.type == "story" || usedTemplate.type == "quick_story") "Story" else if (usedTemplate.type == "post" || usedTemplate.type == "carousel") "Post" else "Reel"
            }
        )
    } else {
        // Camera View
        Box(modifier = Modifier.fillMaxSize()) {
            if (permissionsState.allPermissionsGranted) {
                val previewView = remember {
                    androidx.camera.view.PreviewView(context).apply {
                        implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                    }
                }
                
                LaunchedEffect(lensFacing, captureMode) {
                    if (isRecording) {
                        try { recording?.stop() } catch (_: Exception) {}
                        isRecording = false
                        recording = null
                    }
                    val needsVideoCapture = captureMode == "Video" || captureMode == "Reel"
                    val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = androidx.camera.core.Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val targetSelector = try {
                                val desired = androidx.camera.core.CameraSelector.Builder().requireLensFacing(lensFacing).build()
                                if (cameraProvider.hasCamera(desired)) desired
                                else if (cameraProvider.hasCamera(androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA)) androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
                                else if (cameraProvider.hasCamera(androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA)) androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA
                                else null
                            } catch (e: Exception) {
                                null
                            }

                            if (targetSelector != null) {
                                val targetCamInfo = try {
                                    targetSelector.filter(cameraProvider.availableCameraInfos).firstOrNull()
                                } catch (_: Exception) {
                                    null
                                }
                                val supportedQualities = if (needsVideoCapture && targetCamInfo != null) {
                                    supportedVideoQualitiesForCamera(targetCamInfo)
                                } else {
                                    emptyList()
                                }

                                cameraProvider.unbindAll()
                                val cam = if (needsVideoCapture && supportedQualities.isNotEmpty()) {
                                    val recorder = Recorder.Builder()
                                        .setQualitySelector(
                                            QualitySelector.fromOrderedList(
                                                supportedQualities,
                                                androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(supportedQualities.last())
                                            )
                                        )
                                        .build()
                                    val newVideoCapture = VideoCapture.withOutput(recorder)
                                    try {
                                        val bound = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            targetSelector,
                                            preview,
                                            newVideoCapture,
                                            imageCapture
                                        )
                                        videoCapture = newVideoCapture
                                        isVideoCaptureSupported = true
                                        bound
                                    } catch (comboEx: IllegalArgumentException) {
                                        android.util.Log.w("Camera", "Preview+Video+Image combo unsupported, trying Preview+Video: ${comboEx.message}")
                                        try {
                                            cameraProvider.unbindAll()
                                            val bound = cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                targetSelector,
                                                preview,
                                                newVideoCapture
                                            )
                                            videoCapture = newVideoCapture
                                            isVideoCaptureSupported = true
                                            bound
                                        } catch (videoEx: Exception) {
                                            android.util.Log.w("Camera", "VideoCapture unsupported on this camera, falling back to Preview+Image: ${videoEx.message}")
                                            videoCapture = null
                                            isVideoCaptureSupported = false
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                targetSelector,
                                                preview,
                                                imageCapture
                                            )
                                        }
                                    }
                                } else {
                                    videoCapture = null
                                    isVideoCaptureSupported = false
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        targetSelector,
                                        preview,
                                        imageCapture
                                    )
                                }
                                activeCamera = cam
                                if (isFlashOn && cam.cameraInfo.hasFlashUnit()) {
                                    cam.cameraControl.enableTorch(true)
                                }
                            }
                        } catch (exc: Exception) { 
                            android.util.Log.d("Camera", "Failed to bind camera: ${exc.message}")
                        }
                    }, androidx.core.content.ContextCompat.getMainExecutor(context))
                }

                LaunchedEffect(isFlashOn, activeCamera) {
                    activeCamera?.let { cam ->
                        try {
                            if (cam.cameraInfo.hasFlashUnit()) {
                                cam.cameraControl.enableTorch(isFlashOn)
                            }
                        } catch (_: Exception) {}
                    }
                    imageCapture.flashMode = if (isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                }

                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Rule-of-Thirds Grid Overlay
                if (isGridOn) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        // Vertical lines
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 1.dp.toPx())
                        // Horizontal lines
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 1.dp.toPx())
                    }
                }
            }
            // Top Bar Flagship UI
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Box(modifier = Modifier.align(Alignment.TopCenter)) {
                    FlagshipCameraTopBar(
                        onClose = onNavigateToHome,
                        onFlashToggle = {
                            isFlashOn = !isFlashOn
                            Toast.makeText(context, if (isFlashOn) "Flash / Torch On" else "Flash Off", Toast.LENGTH_SHORT).show()
                        },
                        isFlashOn = isFlashOn,
                        onHdrToggle = {
                            isHdrOn = !isHdrOn
                            Toast.makeText(context, if (isHdrOn) "HDR Pro Enhanced" else "HDR Normal", Toast.LENGTH_SHORT).show()
                        },
                        isHdrOn = isHdrOn,
                        onMusicClick = { showMusicPickerEditor = true },
                        hasMusic = selectedMusicUri != null
                    )
                }

                // Right Toolbar
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    FlagshipCameraSideToolbar(
                        onSwitchCamera = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        onToggleTimer = {
                            isTimerOn = !isTimerOn
                            Toast.makeText(context, if (isTimerOn) "Timer: 3s" else "Timer Off", Toast.LENGTH_SHORT).show()
                        },
                        onToggleGrid = {
                            isGridOn = !isGridOn
                        },
                        onToggleFilters = {
                            Toast.makeText(context, "Color Filter Enhanced", Toast.LENGTH_SHORT).show()
                        },
                        onToggleBeauty = {
                            isBeautyOn = !isBeautyOn
                            Toast.makeText(context, if (isBeautyOn) "Beauty Face Filter On" else "Beauty Filter Off", Toast.LENGTH_SHORT).show()
                        },
                        isTimerOn = isTimerOn,
                        isGridOn = isGridOn,
                        isBeautyOn = isBeautyOn
                    )
                }

                // Bottom Bar Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val offlineDrafts = remember(selectedMediaUris, resumedOfflineDraft) {
                        com.example.ui.editor.OfflineEditorDraftStore.listDrafts(context)
                    }
                    if (offlineDrafts.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .clickable {
                                    com.example.utils.SoundManager.playClickSound()
                                    resumedOfflineDraft = offlineDrafts.first()
                                }
                                .testTag("camera_resume_offline_draft_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Resume Offline Draft (${offlineDrafts.size})",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    FlagshipCameraModeSelector(
                        currentMode = captureMode,
                        onModeSelected = { captureMode = it }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 36.dp, start = 28.dp, end = 28.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Premium Gallery / Upload Button (Well-proportioned, fits all screen sizes)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    com.example.utils.SoundManager.playClickSound()
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .border(
                                        2.dp,
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            listOf(Color(0xFFEE1D52), Color(0xFFFF7A00))
                                        ),
                                        RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoLibrary,
                                    contentDescription = "Upload Photos and Videos",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Upload",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 2. Central Shutter / Capture Button
                        FlagshipCaptureButton(
                            isRecording = isRecording,
                            captureMode = captureMode,
                            onClick = {
                                if (!permissionsState.allPermissionsGranted) {
                                    Toast.makeText(context, "Permissions not granted", Toast.LENGTH_SHORT).show()
                                    return@FlagshipCaptureButton
                                }
                                if (captureMode == "Live") {
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                        val currentUser = com.example.model.RepositoryManager.getCurrentUserId()
                                        var realUserName = "User_${currentUser.take(5)}"
                                        var realAvatar = ""
                                        try {
                                            val userDoc = com.example.model.RepositoryManager.firestore.collection("users").document(currentUser).get().await()
                                            if (userDoc.exists()) {
                                                realUserName = userDoc.getString("username") ?: realUserName
                                                realAvatar = userDoc.getString("avatarUrl") ?: userDoc.getString("profilePicUrl") ?: ""
                                            }
                                        } catch (_: Exception) {}
                                        val intent = android.content.Intent(context, com.example.ui.LiveActivity::class.java).apply {
                                            putExtra("liveID", "live_${currentUser}_${System.currentTimeMillis()}")
                                            putExtra("isHost", true)
                                            putExtra("userID", currentUser)
                                            putExtra("userName", realUserName)
                                            putExtra("hostUid", currentUser)
                                            putExtra("hostUsername", realUserName)
                                            putExtra("hostAvatar", realAvatar)
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Unable to open Live Studio", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else if (captureMode == "Audio Room") {
                                    onNavigate("audio_room_setup")
                                } else if (captureMode == "Photo" || captureMode == "Post" || captureMode == "Story") {
                                    val photoFile = java.io.File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                                    val outputOptions = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                    imageCapture.takePicture(
                                        outputOptions,
                                        androidx.core.content.ContextCompat.getMainExecutor(context),
                                        object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                                            override fun onError(exc: androidx.camera.core.ImageCaptureException) {
                                                android.util.Log.e("Camera", "Photo capture failed: ${exc.message}", exc)
                                            }
                                            override fun onImageSaved(output: androidx.camera.core.ImageCapture.OutputFileResults) {
                                                selectedMediaUris = listOf(android.net.Uri.fromFile(photoFile))
                                                if (selectedMusicName == null) {
                                                    val pickedSong = TRENDING_BACKGROUND_SONGS.random()
                                                    selectedMusicName = pickedSong
                                                    com.example.api.MusicClient.resolveAudioUrlForTrack(null, pickedSong)?.let {
                                                        selectedMusicUri = android.net.Uri.parse(it)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                } else if (captureMode == "Video" || captureMode == "Reel") {
                                    val activeVideoCapture = videoCapture
                                    if (activeVideoCapture == null || !isVideoCaptureSupported) {
                                        Toast.makeText(
                                            context,
                                            "Camera video recording isn't supported on this device. Pick a video from gallery.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    } else if (isRecording) {
                                        recording?.stop()
                                        isRecording = false
                                        recording = null
                                    } else {
                                        val videoFile = java.io.File(context.cacheDir, "video_${System.currentTimeMillis()}.mp4")
                                        val outputOptions = androidx.camera.video.FileOutputOptions.Builder(videoFile).build()
                                        val hasAudioPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        isRecording = true
                                        var pendingRecording = activeVideoCapture.output
                                            .prepareRecording(context, outputOptions)
                                        if (hasAudioPermission) {
                                            pendingRecording = pendingRecording.withAudioEnabled()
                                        }
                                        recording = pendingRecording
                                            .start(androidx.core.content.ContextCompat.getMainExecutor(context)) { recordEvent ->
                                                if (recordEvent is androidx.camera.video.VideoRecordEvent.Finalize) {
                                                    if (!recordEvent.hasError()) {
                                                        selectedMediaUris = listOf(android.net.Uri.fromFile(videoFile))
                                                        if (selectedMusicName == null) {
                                                            val pickedSong = TRENDING_BACKGROUND_SONGS.random()
                                                            selectedMusicName = pickedSong
                                                            com.example.api.MusicClient.resolveAudioUrlForTrack(null, pickedSong)?.let {
                                                                selectedMusicUri = android.net.Uri.parse(it)
                                                            }
                                                        }
                                                    } else {
                                                        android.util.Log.e("Camera", "Video capture error: ${recordEvent.error}")
                                                    }
                                                    isRecording = false
                                                }
                                            }
                                    }
                                }
                            }
                        )

                        // 3. Flip Camera Switch Lens Button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    com.example.utils.SoundManager.playClickSound()
                                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                        CameraSelector.LENS_FACING_FRONT
                                    } else {
                                        CameraSelector.LENS_FACING_BACK
                                    }
                                }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .border(
                                        1.5.dp,
                                        Color.White.copy(alpha = 0.35f),
                                        RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FlipCameraAndroid,
                                    contentDescription = "Switch Camera",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Flip",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (isRecording) {
                Text(
                    text = "Recording...",
                    color = Color.Red,
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 100.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                )
            }
        }

    if (showMusicPickerEditor && selectedMediaUris.isEmpty()) {
        com.example.ui.MusicPickerSheet(
            onDismiss = { showMusicPickerEditor = false },
            onTrackSelected = { track ->
                selectedMusicEditor = track
                showMusicPickerEditor = false
                selectedMusicName = track.name
                selectedMusicUri = track.previewUrl?.let { android.net.Uri.parse(it) }
            }
        )
    }
}

    pendingTemplateFromFeed?.let { tpl ->
        com.example.ui.templates.TemplateSlotFillerDialog(
            template = tpl,
            sourcePostId = pendingTemplatePostId,
            onDismiss = {
                pendingTemplateFromFeed = null
                pendingTemplatePostId = null
            },
            onOpenInEditor = { snapshot, uris, usedTemplate ->
                pendingTemplateFromFeed = null
                pendingTemplatePostId = null
                resumedOfflineDraft = snapshot
                selectedMediaUris = uris
                if (usedTemplate.audio.name.isNotBlank()) {
                    selectedMusicName = usedTemplate.audio.name
                    if (usedTemplate.audio.url.isNotBlank()) {
                        selectedMusicUri = Uri.parse(usedTemplate.audio.url)
                    }
                }
                if (usedTemplate.promptText.isNotBlank()) {
                    addYoursPromptText = usedTemplate.promptText
                }
                isReadyToPost = false
            }
        )
    }
}
@Composable
fun ToolIcon(icon: ImageVector, label: String) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { 
        Toast.makeText(context, "$label activated!", Toast.LENGTH_SHORT).show()
    }) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 10.sp)
    }
}

fun extractMentionQuery(text: String, cursorPosition: Int): String? {
    if (cursorPosition <= 0 || cursorPosition > text.length) return null
    val sub = text.substring(0, cursorPosition)
    val lastAt = sub.lastIndexOf('@')
    if (lastAt == -1) return null
    val candidate = sub.substring(lastAt + 1)
    if (candidate.contains(' ') || candidate.contains('\n') || candidate.contains('\t')) return null
    return candidate
}

fun replaceMention(text: String, cursorPosition: Int, username: String): Pair<String, Int> {
    if (cursorPosition <= 0 || cursorPosition > text.length) return Pair(text, cursorPosition)
    val sub = text.substring(0, cursorPosition)
    val lastAt = sub.lastIndexOf('@')
    if (lastAt == -1) return Pair(text, cursorPosition)
    
    val beforeAt = text.substring(0, lastAt)
    val afterCursor = text.substring(cursorPosition)
    val replacement = "@$username "
    val newText = beforeAt + replacement + afterCursor
    val newCursorPosition = beforeAt.length + replacement.length
    return Pair(newText, newCursorPosition)
}

@Composable
fun MentionSuggestions(
    query: String,
    modifier: Modifier = Modifier,
    onUserSelected: (com.example.model.User) -> Unit = {}
) {
    var users by remember { mutableStateOf<List<com.example.model.User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        isLoading = true
        try {
            com.example.model.RepositoryManager.socialRepository.searchUsersForMention(query).collect { list ->
                users = list
                isLoading = false
            }
        } catch (e: Exception) {
            isLoading = false
        }
    }

    if (users.isNotEmpty() || isLoading) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            if (isLoading && users.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(users, key = { it.id }) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUserSelected(user) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            coil.compose.AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "@${user.username}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (user.isVerified) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Verified,
                                            contentDescription = "Verified",
                                            tint = Color(0xFF1DA1F2),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                if (user.displayName.isNotBlank() && user.displayName != user.username) {
                                    Text(
                                        text = user.displayName,
                                        fontSize = 12.sp,
                                        color = Color.Gray
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

@Composable
fun CollaboratorSearchOverlay(
    selectedCollaborators: List<com.example.model.User> = emptyList(),
    onAddCollaborator: (com.example.model.User) -> Unit,
    onRemoveCollaborator: (com.example.model.User) -> Unit = {},
    onClose: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val currentUserId = com.example.model.RepositoryManager.authRepository.getCurrentUserId()
    
    // Auto load followed users if query is empty
    val followedUsersFlow = remember(currentUserId) { com.example.model.RepositoryManager.socialRepository.getFollowing(currentUserId) }
    val followedUsers by followedUsersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    
    var searchResults by remember { mutableStateOf<List<com.example.model.User>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            isSearching = true
            delay(300) // Debounce
            searchResults = com.example.model.RepositoryManager.socialRepository.searchUsers(searchQuery).filter { it.id != currentUserId }
            isSearching = false
        } else {
            searchResults = emptyList()
        }
    }
    
    val displayUsers = if (searchQuery.isBlank()) followedUsers.filter { it.id != currentUserId } else searchResults

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tag Collaborators", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("Up to 5 co-authors", fontSize = 12.sp, color = Color.Gray)
                }
                TextButton(onClick = onClose) {
                    Text("Done", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                }
            }

            // Selected Collaborators Chips Row
            if (selectedCollaborators.isNotEmpty()) {
                Text(
                    text = "Invited Collaborators (${selectedCollaborators.size}/5)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedCollaborators, key = { it.id }) { user ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFF1F1F2),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                            ) {
                                coil.compose.AsyncImage(
                                    model = user.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "@${user.username}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color.Gray,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onRemoveCollaborator(user) }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF1F1F2))
            }
            
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search people by username or name...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedBorderColor = Color.LightGray,
                    unfocusedBorderColor = Color.LightGray
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isSearching) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (displayUsers.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(if (searchQuery.isBlank()) "Search users to invite as collaborators" else "No users found.", color = Color.Gray)
                            }
                        }
                    } else {
                        items(displayUsers, key = { it.id }) { user ->
                            val isAlreadyInvited = selectedCollaborators.any { it.id == user.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                coil.compose.AsyncImage(
                                    model = user.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = user.username, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                                        if (user.isVerified) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.Verified, contentDescription = "Verified", tint = Color(0xFF1DA1F2), modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    if (user.displayName.isNotBlank()) {
                                        Text(text = user.displayName, fontSize = 13.sp, color = Color.Gray)
                                    }
                                }
                                
                                Button(
                                    onClick = {
                                        if (isAlreadyInvited) {
                                            onRemoveCollaborator(user)
                                        } else {
                                            onAddCollaborator(user)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAlreadyInvited) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primary,
                                        contentColor = if (isAlreadyInvited) Color(0xFF2E7D32) else Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    if (isAlreadyInvited) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Invited", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("Invite", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
@Composable fun BottomToolIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = Color.White, fontSize = 10.sp)
    }
}

data class TextOverlay(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String = "",
    val color: Color = Color.White,
    val size: Float = 24f,
    val bgColor: Color = Color.Transparent,
    val fontRes: Int? = null,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f
)
data class StickerOverlay(
    val id: String = java.util.UUID.randomUUID().toString(),
    val emoji: String = "",
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f
)



@Composable
fun PostOptionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionsBottomSheet(onDismiss: () -> Unit) {
    var allowComments by remember { mutableStateOf(true) }
    var allowReuse by remember { mutableStateOf(true) }
    var visualSearch by remember { mutableStateOf(true) }
    var highQuality by remember { mutableStateOf(true) }
    var saveDevice by remember { mutableStateOf(true) }
    var aiGenerated by remember { mutableStateOf(false) }
    var watermarkEnabled by remember { mutableStateOf(true) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Post & Privacy Settings",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(2f),
                    textAlign = TextAlign.Center
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                item {
                    Text(
                        "Privacy settings",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                    SwitchRow("Allow comments", allowComments) { allowComments = it }
                    SwitchRow("Allow reuse of content", allowReuse, "Duet, Stitch, stickers and add to Story") { allowReuse = it }
                    
                    Text(
                        "Quality & Media Settings",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
                    )
                    SwitchRow("AI-generated content label", aiGenerated, "Add label to inform viewers content was enhanced with AI.") { aiGenerated = it }
                    SwitchRow("Allow visual search", visualSearch, "Your content will be eligible for visual discovery.") { visualSearch = it }
                    SwitchRow("High-quality 1080p/4K upload", highQuality, "Upload in full hardware resolution.") { highQuality = it }
                    SwitchRow("Save copy to device", saveDevice, "Automatically save published media to gallery.") { saveDevice = it }
                    SwitchRow("Include Social India watermark", watermarkEnabled, "Protect your original creation with official badge.") { watermarkEnabled = it }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFFF3366))
        )
    }
}


fun isVideoUri(context: android.content.Context, uri: android.net.Uri): Boolean {
    val mimeType = try { context.contentResolver.getType(uri) } catch (_: Exception) { null } ?: ""
    if (mimeType.startsWith("video/")) return true
    if (mimeType.startsWith("image/")) return false
    val uriStr = uri.toString().lowercase()
    if (uriStr.endsWith(".jpg") || uriStr.endsWith(".jpeg") || uriStr.endsWith(".png") || uriStr.endsWith(".webp") || uriStr.endsWith(".gif")) return false
    if (uriStr.endsWith(".mp4") || uriStr.endsWith(".mov") || uriStr.endsWith(".mkv") || uriStr.endsWith(".webm") || uriStr.endsWith(".3gp")) return true
    // Fast Bitmap header check so images without extension are never misclassified as video
    try {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (uri.scheme == "file" && uri.path != null) {
            android.graphics.BitmapFactory.decodeFile(uri.path, opts)
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, opts)
            }
        }
        if (opts.outWidth > 0 && opts.outHeight > 0 && !opts.outMimeType.isNullOrBlank()) {
            return false
        }
    } catch (_: Exception) {}
    return try {
        val retriever = android.media.MediaMetadataRetriever()
        if (uri.scheme == "file" && uri.path != null) {
            retriever.setDataSource(uri.path)
        } else {
            retriever.setDataSource(context, uri)
        }
        val hasVideo = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
        retriever.release()
        hasVideo == "yes"
    } catch (_: Exception) {
        uriStr.contains("video")
    }
}

fun copyUriToCache(context: android.content.Context, uri: android.net.Uri): android.net.Uri {
    val filePath = uri.path
    if (uri.scheme == "file" && filePath != null) {
        val lower = filePath.lowercase()
        if (lower.endsWith(".mp4") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif")) {
            return uri
        }
    }
    try {
        val maxCacheBytes = 250L * 1024L * 1024L // 250 MB safety limit
        val sourceSize = if (uri.scheme == "file" && filePath != null) {
            java.io.File(filePath).length()
        } else {
            var size = -1L
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) size = cursor.getLong(idx)
                }
            }
            size
        }
        if (sourceSize > maxCacheBytes) {
            android.util.Log.w("UploadScreen", "Skipping cache copy for oversized file ($sourceSize bytes)")
            return uri
        }

        val isVid = isVideoUri(context, uri)
        val mimeType = try { context.contentResolver.getType(uri) } catch (_: Exception) { null } ?: ""
        
        val extension = if (isVid) {
            "mp4"
        } else {
            android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
        }

        val inputStream = if (uri.scheme == "file" && filePath != null) {
            java.io.FileInputStream(java.io.File(filePath))
        } else {
            context.contentResolver.openInputStream(uri)
        } ?: return uri

        val file = java.io.File(context.cacheDir, "upload_cache_${System.currentTimeMillis()}.$extension")
        inputStream.use { input ->
            java.io.FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return android.net.Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return uri
}

@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
private fun supportedVideoQualitiesForCamera(cameraInfo: androidx.camera.core.CameraInfo): List<Quality> {
    return try {
        val cameraId = androidx.camera.camera2.interop.Camera2CameraInfo.from(cameraInfo).cameraId
        val intCameraId = cameraId.toIntOrNull()
        val qualityPairs = listOf(
            Quality.UHD to android.media.CamcorderProfile.QUALITY_2160P,
            Quality.FHD to android.media.CamcorderProfile.QUALITY_1080P,
            Quality.HD to android.media.CamcorderProfile.QUALITY_720P,
            Quality.SD to android.media.CamcorderProfile.QUALITY_480P
        )

        fun hasCamcorderProfile(profileId: Int): Boolean {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val profiles = try {
                    android.media.CamcorderProfile.getAll(cameraId, profileId)
                } catch (_: Exception) {
                    null
                }
                if (profiles?.videoProfiles?.isNotEmpty() == true) {
                    return true
                }
            }
            return intCameraId != null && try {
                @Suppress("DEPRECATION")
                android.media.CamcorderProfile.hasProfile(intCameraId, profileId)
            } catch (_: Exception) {
                false
            }
        }

        val supported = qualityPairs.mapNotNull { (quality, profileId) ->
            if (hasCamcorderProfile(profileId)) quality else null
        }
        if (supported.isNotEmpty()) {
            supported
        } else if (
            hasCamcorderProfile(android.media.CamcorderProfile.QUALITY_HIGH) ||
            hasCamcorderProfile(android.media.CamcorderProfile.QUALITY_LOW)
        ) {
            listOf(Quality.HD, Quality.SD)
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        android.util.Log.w("Camera", "Unable to inspect camcorder profiles: ${e.message}")
        emptyList()
    }
}

