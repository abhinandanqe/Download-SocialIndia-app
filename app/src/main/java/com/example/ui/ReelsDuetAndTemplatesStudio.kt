package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.model.SmartReelTemplate
import com.example.model.Video
import com.example.repository.MediaRepository
import com.example.repository.ProCreatorSuiteRepository
import com.example.utils.GlobalAudioPreviewManager
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Feature 2A: Reels Duet / Remix Studio (Side-by-Side, Top-Bottom, PiP Reaction)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsRemixAndDuetSheet(
    originalVideo: Video,
    onDismiss: () -> Unit,
    onPublished: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaRepo = remember { MediaRepository(context) }

    var layoutMode by remember { mutableStateOf("SIDE_BY_SIDE") } // SIDE_BY_SIDE, TOP_BOTTOM, PIP
    var reactionMediaUri by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("Remix with @${originalVideo.user.username} 🔥 #duet #remix") }
    var originalAudioBalance by remember { mutableFloatStateOf(0.5f) }
    var isPublishing by remember { mutableStateOf(false) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) reactionMediaUri = uri }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E0E18),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Compare, contentDescription = "Remix", tint = Color(0xFF00F0FF))
                    Column {
                        Text("Duet & Remix Studio", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text("Remixing @${originalVideo.user.username}'s Reel", color = Color(0xFFA0A0B8), fontSize = 11.sp)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFFA0A0B8)) }
            }

            // Live Split-Screen Preview Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF171728))
                    .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            ) {
                when (layoutMode) {
                    "SIDE_BY_SIDE" -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                AsyncImage(
                                    model = originalVideo.thumbnailUrl.ifBlank { originalVideo.videoUrl },
                                    contentDescription = "Original Reel",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                ) {
                                    Text("@${originalVideo.user.username}", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color(0xFF1F1633))
                                    .clickable {
                                        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (reactionMediaUri != null) {
                                    AsyncImage(
                                        model = reactionMediaUri,
                                        contentDescription = "Your Reaction",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.VideoCall, contentDescription = null, tint = Color(0xFFFF2A6D), modifier = Modifier.size(34.dp))
                                        Text("Tap to Pick Clip", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    "TOP_BOTTOM" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = originalVideo.thumbnailUrl.ifBlank { originalVideo.videoUrl },
                                contentDescription = "Original",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(Color(0xFF1F1633))
                                    .clickable {
                                        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (reactionMediaUri != null) {
                                    AsyncImage(model = reactionMediaUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Text("Tap to Add Reaction Clip", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    else -> {
                        // PiP Reaction
                        AsyncImage(
                            model = originalVideo.thumbnailUrl.ifBlank { originalVideo.videoUrl },
                            contentDescription = "Original",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .size(width = 92.dp, height = 132.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1F1633))
                                .border(2.dp, Color(0xFFFF2A6D), RoundedCornerShape(14.dp))
                                .clickable {
                                    mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (reactionMediaUri != null) {
                                AsyncImage(model = reactionMediaUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Text("Add PiP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Layout Mode Selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("SIDE_BY_SIDE" to "Side by Side", "TOP_BOTTOM" to "Top / Bottom", "PIP" to "Reaction PiP").forEach { (key, label) ->
                    FilterChip(
                        selected = layoutMode == key,
                        onClick = { layoutMode = key },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Audio Mix Balance
            Column {
                Text(
                    text = "Audio Mix: Original ${(originalAudioBalance * 100).toInt()}% / Yours ${((1f - originalAudioBalance) * 100).toInt()}%",
                    color = Color(0xFFB4B4CC),
                    fontSize = 11.sp
                )
                Slider(value = originalAudioBalance, onValueChange = { originalAudioBalance = it })
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Remix Caption") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (isPublishing) return@Button
                    val uid = RepositoryManager.getCurrentUserId()
                    if (uid.isBlank()) {
                        Toast.makeText(context, "Please sign in first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isPublishing = true
                    scope.launch {
                        try {
                            val uploadedUrl = reactionMediaUri?.let { mediaRepo.uploadMedia(it) {} } ?: originalVideo.videoUrl
                            val currentUser = RepositoryManager.currentUserState.value
                            val postId = "remix_${UUID.randomUUID().toString().take(12)}"
                            RepositoryManager.firestore.collection("posts").document(postId).set(
                                mapOf(
                                    "id" to postId,
                                    "userId" to uid,
                                    "username" to (currentUser?.username?.ifBlank { "Creator" } ?: "Creator"),
                                    "userAvatarUrl" to (currentUser?.avatarUrl ?: ""),
                                    "isUserVerified" to (currentUser?.isVerified ?: false),
                                    "postUrl" to uploadedUrl,
                                    "postUrls" to listOf(uploadedUrl, originalVideo.videoUrl).filter { it.isNotBlank() },
                                    "thumbnailUrl" to (originalVideo.thumbnailUrl.ifBlank { uploadedUrl }),
                                    "mediaType" to "video",
                                    "caption" to "$caption [Remix:$layoutMode]",
                                    "musicTrack" to originalVideo.musicTrack.ifBlank { "Remix Audio" },
                                    "musicTrackUrl" to (originalVideo.musicTrackUrl ?: ""),
                                    "remixSourceVideoId" to originalVideo.id,
                                    "remixLayout" to layoutMode,
                                    "visibility" to "PUBLIC",
                                    "timestamp" to FieldValue.serverTimestamp(),
                                    "createdAtMillis" to System.currentTimeMillis()
                                )
                            ).await()
                            isPublishing = false
                            GlobalState.homeFeedRefreshTrigger.value = System.currentTimeMillis()
                            Toast.makeText(context, "🎬 Duet Remix published to Reels!", Toast.LENGTH_LONG).show()
                            onPublished()
                            onDismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            isPublishing = false
                            Toast.makeText(context, e.message ?: "Failed to publish remix", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isPublishing,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A6D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("publish_remix_btn")
            ) {
                Text(
                    text = if (isPublishing) "Publishing Remix..." else "Publish Duet / Remix 🎬",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * Feature 2B: Video Reply to Comment Sheet (Instagram/TikTok Comment Sticker Overlay)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentVideoReplySheet(
    targetCommentUsername: String,
    targetCommentText: String,
    sourceVideoId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaRepo = remember { MediaRepository(context) }

    var replyMediaUri by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("Replying to @$targetCommentUsername 💬") }
    var isPublishing by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) replyMediaUri = uri }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E0E18)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Video Reply to Comment", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)

            // Preview Stage with Floating Comment Sticker
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1F1538), Color(0xFF101324))))
                    .clickable {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                contentAlignment = Alignment.Center
            ) {
                if (replyMediaUri != null) {
                    AsyncImage(model = replyMediaUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VideoCall, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(38.dp))
                        Text("Tap to pick reply video/photo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Floating TikTok/Instagram style Comment Sticker
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.94f),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(14.dp)
                        .widthIn(max = 260.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = "Reply to @$targetCommentUsername's comment",
                            color = Color(0xFF6B7280),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = targetCommentText,
                            color = Color(0xFF111827),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Reply Reel Caption") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (isPublishing || replyMediaUri == null) {
                        if (replyMediaUri == null) Toast.makeText(context, "Pick a video or photo first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isPublishing = true
                    scope.launch {
                        try {
                            val url = mediaRepo.uploadMedia(replyMediaUri!!) {} ?: ""
                            if (url.isBlank()) throw IllegalStateException("Upload failed")
                            val uid = RepositoryManager.getCurrentUserId()
                            val me = RepositoryManager.currentUserState.value
                            val postId = "vreply_${UUID.randomUUID().toString().take(12)}"
                            RepositoryManager.firestore.collection("posts").document(postId).set(
                                mapOf(
                                    "id" to postId,
                                    "userId" to uid,
                                    "username" to (me?.username?.ifBlank { "Creator" } ?: "Creator"),
                                    "userAvatarUrl" to (me?.avatarUrl ?: ""),
                                    "isUserVerified" to (me?.isVerified ?: false),
                                    "postUrl" to url,
                                    "mediaType" to "video",
                                    "caption" to "$caption\n💬 \"${targetCommentText.take(80)}\"",
                                    "replyToVideoId" to sourceVideoId,
                                    "replyToCommentUser" to targetCommentUsername,
                                    "visibility" to "PUBLIC",
                                    "timestamp" to FieldValue.serverTimestamp(),
                                    "createdAtMillis" to System.currentTimeMillis()
                                )
                            ).await()
                            isPublishing = false
                            GlobalState.homeFeedRefreshTrigger.value = System.currentTimeMillis()
                            Toast.makeText(context, "🎬 Video Reply posted with Comment Sticker!", Toast.LENGTH_LONG).show()
                            onDismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            isPublishing = false
                            Toast.makeText(context, e.message ?: "Failed to post video reply", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isPublishing,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("publish_comment_video_reply_btn")
            ) {
                Text(
                    text = if (isPublishing) "Posting Video Reply..." else "Post Video Reply 🎬",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * Feature 6: Smart Beat-Synced Reel & Story Templates + Animated Auto-Captions Studio
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartTemplatesAndCaptionsScreen(
    initialTemplateId: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaRepo = remember { MediaRepository(context) }

    val templates = ProCreatorSuiteRepository.SMART_TEMPLATES
    val captionStyles = ProCreatorSuiteRepository.AUTO_CAPTION_STYLES

    var selectedTemplate by remember(initialTemplateId) {
        mutableStateOf(templates.firstOrNull { it.id == initialTemplateId } ?: templates.first())
    }
    var selectedCaptionStyle by remember(selectedTemplate.id) {
        mutableStateOf(captionStyles.firstOrNull { it.id == selectedTemplate.defaultCaptionStyle } ?: captionStyles.first())
    }
    // Real song state (defaults to template's audio, replaceable with ANY real song via MusicPickerSheet!)
    var selectedSongTitle by remember { mutableStateOf(selectedTemplate.musicTitle) }
    var selectedSongArtist by remember { mutableStateOf(selectedTemplate.musicArtist) }
    var selectedSongUrl by remember { mutableStateOf(selectedTemplate.musicUrl) }
    var selectedSongArtwork by remember { mutableStateOf("") }
    var hasPickedCustomSong by remember { mutableStateOf(false) }
    var showMusicPicker by remember { mutableStateOf(false) }

    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var captionLinesInput by remember { mutableStateOf("Every beat tells a story\nBuilt for creators\nShine in full HD ✨") }
    var isPreviewPlaying by remember { mutableStateOf(true) }
    var currentSlideIdx by remember { mutableIntStateOf(0) }
    var isPublishing by remember { mutableStateOf(false) }

    val captionLines = remember(captionLinesInput) {
        captionLinesInput.lines().map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("Your Auto-Captions Here ✨") }
    }

    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 6)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris = uris
            currentSlideIdx = 0
        }
    }

    // Beat-synced auto-advance loop while previewing
    LaunchedEffect(selectedTemplate, selectedUris.size, isPreviewPlaying) {
        if (!isPreviewPlaying) return@LaunchedEffect
        val totalSlides = maxOf(selectedUris.size, captionLines.size, 1)
        while (isPreviewPlaying) {
            delay(selectedTemplate.clipDurationMs)
            currentSlideIdx = (currentSlideIdx + 1) % totalSlides
        }
    }

    DisposableEffect(Unit) {
        onDispose { GlobalAudioPreviewManager.stop() }
    }

    if (showMusicPicker) {
        MusicPickerSheet(
            onDismiss = { showMusicPicker = false },
            onTrackSelected = { track ->
                val url = track.previewUrl.orEmpty()
                selectedSongTitle = track.name?.ifBlank { "Selected Song" } ?: "Selected Song"
                selectedSongArtist = track.artistName?.ifBlank { "Social India Audio" } ?: "Social India Audio"
                selectedSongUrl = url.ifBlank { selectedTemplate.musicUrl }
                selectedSongArtwork = track.artworkUrl.orEmpty()
                hasPickedCustomSong = true
                showMusicPicker = false
                if (selectedSongUrl.isNotBlank()) {
                    GlobalAudioPreviewManager.playOrToggle(context, selectedSongUrl)
                }
                Toast.makeText(context, "🎵 Added song: $selectedSongTitle", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Scaffold(
        containerColor = Color(0xFF07070F),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Beat Templates Studio", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text("Real Songs • Beat-Synced Reels • Auto-Captions", color = Color(0xFFFF4D8D), fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("templates_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Surface(
                        onClick = { showMusicPicker = true },
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF00F0FF).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.55f)),
                        modifier = Modifier.padding(end = 10.dp).testTag("template_topbar_song_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = "Add Song", tint = Color(0xFF00F0FF), modifier = Modifier.size(14.dp))
                            Text("Add Song", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF07070F))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Beat-Synced Template Preview Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(listOf(selectedTemplate.primaryColor, selectedTemplate.secondaryColor))
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            ) {
                if (selectedUris.isNotEmpty()) {
                    val safeUri = selectedUris[currentSlideIdx % selectedUris.size]
                    AnimatedContent(
                        targetState = safeUri,
                        transitionSpec = {
                            when (selectedTemplate.transitionStyle) {
                                "SLIDE_FLASH" -> (slideInHorizontally { it / 2 } + fadeIn(tween(260))) togetherWith (slideOutHorizontally { -it / 2 } + fadeOut(tween(260)))
                                "ZOOM_PULSE" -> (scaleIn(initialScale = 1.15f, animationSpec = tween(320)) + fadeIn(tween(280))) togetherWith fadeOut(tween(240))
                                else -> fadeIn(tween(420)) togetherWith fadeOut(tween(420))
                            }
                        },
                        label = "template_slide"
                    ) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = "Template Slide",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clickable {
                                multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tap to Select 2–6 Photos", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Text("${selectedTemplate.name} • ${selectedTemplate.bpm} BPM Sync", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                }

                // Real Song Pill Overlay (Tap to pick ANY real song!)
                Surface(
                    onClick = { showMusicPicker = true },
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.5f)),
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (selectedSongArtwork.isNotBlank()) {
                            AsyncImage(
                                model = selectedSongArtwork,
                                contentDescription = "Song Cover",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(20.dp).clip(CircleShape)
                            )
                        } else {
                            Icon(Icons.Default.MusicNote, contentDescription = "Audio", tint = Color(0xFF00F0FF), modifier = Modifier.size(15.dp))
                        }
                        Text(
                            text = "$selectedSongTitle • $selectedSongArtist",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 210.dp)
                        )
                    }
                }

                // Animated Kinetic Auto-Caption Overlay
                val activeCaption = captionLines[currentSlideIdx % captionLines.size]
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    AnimatedContent(
                        targetState = activeCaption,
                        transitionSpec = { (scaleIn(initialScale = 0.9f) + fadeIn(tween(220))) togetherWith fadeOut(tween(180)) },
                        label = "auto_caption_anim"
                    ) { line ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = selectedCaptionStyle.backgroundColor,
                            border = BorderStroke(1.dp, selectedCaptionStyle.highlightColor.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = line,
                                color = selectedCaptionStyle.textColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // Pick Media, Pick REAL Song & Play Beat Actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = {
                        multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(46.dp).testTag("template_pick_photos_btn")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(if (selectedUris.isEmpty()) "Photos/Clips" else "${selectedUris.size} Picked", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { showMusicPicker = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF1F163B),
                        contentColor = Color(0xFF00F0FF)
                    ),
                    modifier = Modifier.weight(1f).height(46.dp).testTag("template_pick_real_song_btn")
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = "Pick Real Song", modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(if (hasPickedCustomSong) "Change Song" else "Real Song", fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = {
                        GlobalAudioPreviewManager.playOrToggle(context, selectedSongUrl)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Preview Song", modifier = Modifier.size(18.dp))
                }
            }

            // Template Picker Strip
            Text("Choose Beat-Synced Template", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(templates, key = { it.id }) { tpl ->
                    val active = selectedTemplate.id == tpl.id
                    Surface(
                        onClick = {
                            selectedTemplate = tpl
                            selectedCaptionStyle = captionStyles.firstOrNull { it.id == tpl.defaultCaptionStyle } ?: captionStyles.first()
                            if (!hasPickedCustomSong) {
                                selectedSongTitle = tpl.musicTitle
                                selectedSongArtist = tpl.musicArtist
                                selectedSongUrl = tpl.musicUrl
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF151524),
                        border = BorderStroke(if (active) 2.dp else 1.dp, if (active) tpl.primaryColor else Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.width(172.dp).testTag("template_card_${tpl.id}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.horizontalGradient(listOf(tpl.primaryColor, tpl.secondaryColor)))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(tpl.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            Text("${tpl.bpm} BPM • ${tpl.clipDurationMs / 1000f}s cut", color = Color(0xFFA0A0B8), fontSize = 11.sp)
                        }
                    }
                }
            }

            // Auto-Captions / Kinetic Lyrics Editor
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.ClosedCaption, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(18.dp))
                Text("Auto-Captions / Kinetic Lyrics (1 line per beat)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(captionStyles, key = { it.id }) { style ->
                    FilterChip(
                        selected = selectedCaptionStyle.id == style.id,
                        onClick = { selectedCaptionStyle = style },
                        label = { Text(style.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            OutlinedTextField(
                value = captionLinesInput,
                onValueChange = { captionLinesInput = it },
                label = { Text("Timed Caption Lines") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            // Publish Button
            Button(
                onClick = {
                    if (isPublishing) return@Button
                    if (selectedUris.isEmpty()) {
                        Toast.makeText(context, "Please pick 2–6 photos first!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isPublishing = true
                    scope.launch {
                        try {
                            val uploadedUrls = selectedUris.mapNotNull { uri -> mediaRepo.uploadMedia(uri) {} }
                            if (uploadedUrls.isEmpty()) throw IllegalStateException("Upload failed")
                            val uid = RepositoryManager.getCurrentUserId()
                            val me = RepositoryManager.currentUserState.value
                            val postId = "tpl_${UUID.randomUUID().toString().take(12)}"
                            RepositoryManager.firestore.collection("posts").document(postId).set(
                                mapOf(
                                    "id" to postId,
                                    "userId" to uid,
                                    "username" to (me?.username?.ifBlank { "Creator" } ?: "Creator"),
                                    "userAvatarUrl" to (me?.avatarUrl ?: ""),
                                    "isUserVerified" to (me?.isVerified ?: false),
                                    "postUrl" to uploadedUrls.first(),
                                    "postUrls" to uploadedUrls,
                                    "thumbnailUrl" to uploadedUrls.first(),
                                    "mediaType" to "image",
                                    "caption" to "${captionLines.joinToString(" • ")}\n✨ Made with ${selectedTemplate.name}",
                                    "musicTrack" to selectedSongTitle,
                                    "musicTrackArtist" to selectedSongArtist,
                                    "musicTrackUrl" to selectedSongUrl,
                                    "musicTrackImageUrl" to selectedSongArtwork,
                                    "templateId" to selectedTemplate.id,
                                    "autoCaptionStyle" to selectedCaptionStyle.id,
                                    "visibility" to "PUBLIC",
                                    "timestamp" to FieldValue.serverTimestamp(),
                                    "createdAtMillis" to System.currentTimeMillis()
                                )
                            ).await()
                            isPublishing = false
                            GlobalAudioPreviewManager.stop()
                            GlobalState.homeFeedRefreshTrigger.value = System.currentTimeMillis()
                            Toast.makeText(context, "✨ Beat-Synced Template Reel published!", Toast.LENGTH_LONG).show()
                            onBack()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            isPublishing = false
                            Toast.makeText(context, e.message ?: "Publish failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isPublishing,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A6D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("publish_template_reel_btn")
            ) {
                Icon(Icons.Default.Publish, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isPublishing) "Publishing Template..." else "Publish Beat-Synced Reel ✨",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
