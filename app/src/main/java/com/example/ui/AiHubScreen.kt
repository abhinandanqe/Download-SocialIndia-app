package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ai.MahiCallMode
import com.example.ai.MahiMessage
import com.example.ai.MahiPersonaMode
import com.example.ai.MahiQuickChip
import com.example.ai.SarvamVoiceRegistry
import com.example.viewmodel.MahiAiViewModel
import com.example.viewmodel.VideoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHubScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReel: ((String) -> Unit)? = null,
    onNavigateToAiTool: ((String) -> Unit)? = null,
    viewModel: MahiAiViewModel = viewModel()
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val callMode by viewModel.callMode.collectAsStateWithLifecycle()
    val isMahiSpeaking by viewModel.isMahiSpeaking.collectAsStateWithLifecycle()
    val sarvamSettings by viewModel.sarvamSettings.collectAsStateWithLifecycle()
    val adminConfig by viewModel.adminConfig.collectAsStateWithLifecycle()
    val currentPersonaMode by viewModel.currentPersonaMode.collectAsStateWithLifecycle()
    val isCurrentUserAdmin by viewModel.isCurrentUserAdmin.collectAsStateWithLifecycle()
    val isAutoSpeakEnabled by viewModel.isAutoSpeakEnabled.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val recognizedSpeechText by viewModel.voiceManager.recognizedText.collectAsStateWithLifecycle()
    val partialSpeechText by viewModel.voiceManager.livePartialSpeech.collectAsStateWithLifecycle()

    val aiDisplayName = adminConfig.aiName.ifBlank { "Mahi AI" }
    val aiAvatar = adminConfig.aiAvatarUrl

    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showVoiceStudioSheet by remember { mutableStateOf(false) }
    var showPersonaModeSheet by remember { mutableStateOf(false) }
    var showAdminTrainingSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val currentVoice = remember(sarvamSettings.voiceId) {
        SarvamVoiceRegistry.getVoiceById(sarvamSettings.voiceId)
    }

    // Update input text live when voice typing in chat
    LaunchedEffect(partialSpeechText, recognizedSpeechText, isListening) {
        if (callMode == MahiCallMode.NONE && isListening) {
            val liveText = partialSpeechText.ifBlank { recognizedSpeechText }
            if (liveText.isNotBlank()) {
                inputText = liveText
            }
        }
    }

    // Scroll to bottom when messages update
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    // Handle back button when in a call
    BackHandler {
        if (callMode != MahiCallMode.NONE) {
            viewModel.endCall()
        } else {
            onNavigateBack()
        }
    }

    // --- Live Call Overlays ---
    if (callMode == MahiCallMode.AUDIO) {
        MahiLiveVoiceCallView(
            viewModel = viewModel,
            onEndCall = { viewModel.endCall() },
            onSwitchToVideo = { viewModel.startVideoCall() }
        )
        return
    }

    if (callMode == MahiCallMode.VIDEO) {
        MahiLiveVideoCallView(
            viewModel = viewModel,
            onEndCall = { viewModel.endCall() },
            onSwitchToVoice = { viewModel.startAudioCall() }
        )
        return
    }

    // Sarvam Voice Studio Bottom Sheet
    if (showVoiceStudioSheet) {
        SarvamVoiceStudioSheet(
            viewModel = viewModel,
            onDismiss = { showVoiceStudioSheet = false }
        )
    }

    // Persona Mode Sheet
    if (showPersonaModeSheet) {
        AiPersonaModeSheet(
            viewModel = viewModel,
            currentMode = currentPersonaMode,
            onDismiss = { showPersonaModeSheet = false }
        )
    }

    // Admin Training Sheet
    if (showAdminTrainingSheet) {
        AdminAiTrainingSheet(
            viewModel = viewModel,
            adminConfig = adminConfig,
            onDismiss = { showAdminTrainingSheet = false }
        )
    }

    // Info Dialog about Mahi
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = {
                if (aiAvatar.isNotBlank()) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape)) {
                        AsyncImage(model = aiAvatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    Text("🌸", fontSize = 32.sp)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("About $aiDisplayName", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = Color(0xFF0095F6),
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "$aiDisplayName is your human-like AI companion on Social India.",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Text("• 🎭 Multi-Modes: Girlfriend, 18+ Romantic Flirty, Real Human & Default.")
                    Text("• 🎙️ Natural Voice Engine with real-time Indian voices.")
                    Text("• 📞 Real-time Live Voice & Video Calls.")
                    Text("• 🎬 In-app Reel recommendations & Photo analysis.")
                    Text("• ⚙️ Admin Customizable Name, Avatar & Custom Training Prompts.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Samajh Gaya", color = Color(0xFFEE1D52), fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showInfoDialog = true }
                    ) {
                        // AI Avatar with glowing ring
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFEE1D52), Color(0xFF7A00FF), Color(0xFF00E5FF))
                                    )
                                )
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E1035)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (aiAvatar.isNotBlank()) {
                                    AsyncImage(
                                        model = aiAvatar,
                                        contentDescription = "Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text("🌸", fontSize = 20.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = aiDisplayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Verified AI",
                                    tint = Color(0xFF0095F6),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showPersonaModeSheet = true }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFEE1D52).copy(alpha = 0.2f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${currentPersonaMode.icon} ${currentPersonaMode.title.split(" ").first()}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEE1D52)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isMahiSpeaking) "Speaking..." else "Voice: ${currentVoice.displayName.split(" ").first()}",
                                    fontSize = 11.sp,
                                    color = if (isMahiSpeaking) Color(0xFF00E5FF) else Color.Gray
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Persona Mode Selector Button (For all users)
                    IconButton(onClick = { showPersonaModeSheet = true }) {
                        Text(currentPersonaMode.icon, fontSize = 20.sp)
                    }

                    // Sarvam Voice & Language Studio Button (For all users)
                    IconButton(onClick = { showVoiceStudioSheet = true }) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = "Voice & Language",
                            tint = Color(0xFFFF4081),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Admin AI Training Studio Button (ONLY VISIBLE TO ADMINS)
                    if (isCurrentUserAdmin) {
                        IconButton(onClick = { showAdminTrainingSheet = true }) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = "Admin AI Training Studio",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Live Audio Call Button
                    IconButton(onClick = { viewModel.startAudioCall() }) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "Live Audio Call",
                            tint = Color(0xFF00C853),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Live Video Call Button
                    IconButton(onClick = { viewModel.startVideoCall() }) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = "Live Video Call",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // More Menu
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Change Persona Mode") },
                            onClick = {
                                menuExpanded = false
                                showPersonaModeSheet = true
                            },
                            leadingIcon = { Text("🎭", fontSize = 18.sp) }
                        )
                        DropdownMenuItem(
                            text = { Text("Voice & Language Studio") },
                            onClick = {
                                menuExpanded = false
                                showVoiceStudioSheet = true
                            },
                            leadingIcon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = Color(0xFFFF4081)) }
                        )
                        // Admin-Only Menu Option
                        if (isCurrentUserAdmin) {
                            DropdownMenuItem(
                                text = { Text("Admin AI Training Studio") },
                                onClick = {
                                    menuExpanded = false
                                    showAdminTrainingSheet = true
                                },
                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF00E5FF)) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("About $aiDisplayName") },
                            onClick = {
                                menuExpanded = false
                                showInfoDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Chat") },
                            onClick = {
                                menuExpanded = false
                                viewModel.clearChat()
                            },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // Chat Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MahiMessageItem(
                        message = msg,
                        aiAvatarUrl = aiAvatar,
                        onSpeakClick = { viewModel.speakMessage(msg) },
                        onReelClick = { videoId ->
                            if (onNavigateToReel != null) {
                                onNavigateToReel(videoId)
                            } else {
                                VideoViewModel.selectedVideoIdToPlay = videoId
                                onNavigateBack()
                            }
                        }
                    )
                }

                if (isLoading) {
                    item {
                        MahiTypingIndicator()
                    }
                }
            }

            // Quick Suggestion Action Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.quickChips) { chip ->
                    SuggestionChip(
                        onClick = {
                            when (chip.id) {
                                "voice_call" -> viewModel.startAudioCall()
                                "video_call" -> viewModel.startVideoCall()
                                "persona_mode" -> showPersonaModeSheet = true
                                "voice_studio" -> showVoiceStudioSheet = true
                                "photo_analyze" -> photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                                else -> viewModel.sendMessage(chip.prompt)
                            }
                        },
                        label = {
                            Text(
                                text = chip.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = null
                    )
                }
            }

            // Attached Image Preview chip if selected
            if (selectedImageUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Selected Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = { selectedImageUri = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Photo attached for analysis",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            // Bottom Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Image picker button
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = "Send Photo",
                            tint = Color(0xFF00E5FF)
                        )
                    }

                    // Auto-Voice Toggle Button (Automatic audio speech on/off)
                    IconButton(
                        onClick = { viewModel.toggleAutoSpeak() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isAutoSpeakEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = if (isAutoSpeakEnabled) "Auto Voice ON" else "Auto Voice OFF",
                            tint = if (isAutoSpeakEnabled) Color(0xFF00E5FF) else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Voice Input Trigger (Live Voice Typing)
                    IconButton(
                        onClick = {
                            if (isListening) {
                                viewModel.voiceManager.stopListening()
                            } else {
                                viewModel.voiceManager.startListening()
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isListening) Color(0xFFEE1D52).copy(alpha = 0.2f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = if (isListening) Color(0xFFEE1D52) else Color(0xFFFF4081),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Text Field
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                "Mahi se kuch bhi poochiye...",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Send Button
                    val canSend = inputText.isNotBlank() || selectedImageUri != null
                    IconButton(
                        onClick = {
                            if (canSend) {
                                viewModel.sendMessage(inputText.trim(), selectedImageUri)
                                inputText = ""
                                selectedImageUri = null
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) Brush.linearGradient(listOf(Color(0xFFEE1D52), Color(0xFF7A00FF)))
                                else Brush.linearGradient(listOf(Color.DarkGray, Color.Gray))
                            )
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MahiMessageItem(
    message: MahiMessage,
    aiAvatarUrl: String,
    onSpeakClick: () -> Unit,
    onReelClick: (String) -> Unit
) {
    val isUser = message.sender == "user"
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1035)),
                contentAlignment = Alignment.Center
            ) {
                if (aiAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = aiAvatarUrl,
                        contentDescription = "Mahi",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("🌸", fontSize = 16.sp)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 290.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Attached Image if exists
            if (message.imageUrl != null) {
                AsyncImage(
                    model = message.imageUrl,
                    contentDescription = "Attached Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF7A00FF).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Message Bubble
            if (message.text.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    ),
                    color = if (isUser) Color(0xFFEE1D52) else MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = message.text,
                        color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        lineHeight = 20.sp
                    )
                }
            }

            // Recommended In-App Video / Trending Reel Card
            if (message.recommendedVideoId != null && message.recommendedVideoUrl != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onReelClick(message.recommendedVideoId) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1730))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Video Thumbnail Box
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!message.recommendedVideoThumbnail.isNullOrBlank()) {
                                    AsyncImage(
                                        model = message.recommendedVideoThumbnail,
                                        contentDescription = "Video Thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Play Reel",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🎬 ${message.recommendedVideoTitle ?: "Social India Trending Reel"}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Creator Avatar
                                    if (!message.recommendedAuthorAvatar.isNullOrBlank()) {
                                        AsyncImage(
                                            model = message.recommendedAuthorAvatar,
                                            contentDescription = "Creator Avatar",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = "@${message.recommendedVideoAuthor ?: "creator"}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF00E5FF),
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Views and Likes stats
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (message.recommendedVideoViews > 0) {
                                        Text(
                                            text = "👁️ ${message.recommendedVideoViews} views",
                                            fontSize = 10.sp,
                                            color = Color.LightGray
                                        )
                                    }
                                    if (message.recommendedVideoLikes > 0) {
                                        Text(
                                            text = "❤️ ${message.recommendedVideoLikes} likes",
                                            fontSize = 10.sp,
                                            color = Color(0xFFFF80AB)
                                        )
                                    }
                                    Text(
                                        text = "▶ Tap to watch",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFFFD54F)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action row below message (for AI messages: Speak & Copy buttons)
            if (!isUser) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSpeakClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Suniyen",
                            tint = Color(0xFFFF4081),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Suniyen",
                        fontSize = 11.sp,
                        color = Color(0xFFFF4081),
                        modifier = Modifier.clickable { onSpeakClick() }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Mahi", message.text))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MahiTypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "d1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200), RepeatMode.Reverse), label = "d2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400), RepeatMode.Reverse), label = "d3"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("🌸 Mahi is thinking", fontSize = 12.sp, color = Color.Gray)
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFEE1D52).copy(alpha = dot1Alpha)))
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFEE1D52).copy(alpha = dot2Alpha)))
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFEE1D52).copy(alpha = dot3Alpha)))
    }
}
