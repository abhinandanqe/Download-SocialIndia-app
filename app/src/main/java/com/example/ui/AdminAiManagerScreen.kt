package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.viewmodel.AdminAiViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAiManagerScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdminAiViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val adminConfig by viewModel.adminConfig.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var aiName by remember { mutableStateOf("Mahi") }
    var aiAvatarUrl by remember { mutableStateOf("") }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var systemPrompt by remember { mutableStateOf("") }
    var personaMode by remember { mutableStateOf("COMPANION") }
    var geminiKey by remember { mutableStateOf("") }
    var nvidiaKey by remember { mutableStateOf("") }
    var sarvamKey by remember { mutableStateOf("") }

    var customQaList by remember { mutableStateOf(mutableListOf<Pair<String, String>>()) }
    var newQuestion by remember { mutableStateOf("") }
    var newAnswer by remember { mutableStateOf("") }

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            isUploadingAvatar = true
            scope.launch {
                try {
                    val uploaded = com.example.model.RepositoryManager.mediaRepository.uploadMedia(uri)
                    if (!uploaded.isNullOrBlank()) {
                        aiAvatarUrl = uploaded
                        android.widget.Toast.makeText(context, "AI Avatar uploaded successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Failed to upload image.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Upload error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                } finally {
                    isUploadingAvatar = false
                }
            }
        }
    }

    // Sync state when adminConfig loads
    LaunchedEffect(adminConfig) {
        aiName = adminConfig.aiName.ifEmpty { "Mahi" }
        aiAvatarUrl = adminConfig.aiAvatarUrl
        systemPrompt = adminConfig.systemPrompt
        personaMode = adminConfig.personaMode.ifEmpty { "COMPANION" }
        geminiKey = adminConfig.geminiKey
        nvidiaKey = adminConfig.nvidiaKey
        sarvamKey = adminConfig.sarvamKey
        customQaList = adminConfig.customQaList.toMutableList()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message ?: "")
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AI Studio & Training", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEE1D52).copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("ADMIN", color = Color(0xFFEE1D52), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            viewModel.saveAdminAiConfig(
                                aiName = aiName,
                                aiAvatarUrl = aiAvatarUrl,
                                systemPrompt = systemPrompt,
                                personaMode = personaMode,
                                customQaList = customQaList,
                                geminiKey = geminiKey,
                                nvidiaKey = nvidiaKey,
                                sarvamKey = sarvamKey
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save All", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 1. AI Identity & Branding Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Face, contentDescription = null, tint = Color(0xFF00E5FF))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("1. AI Identity & Branding", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // Preview Avatar & Upload Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E1035))
                                    .border(2.dp, Color(0xFF00E5FF), CircleShape)
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isUploadingAvatar) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF00E5FF),
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp
                                    )
                                } else if (aiAvatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = aiAvatarUrl,
                                        contentDescription = "Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text("🌸", fontSize = 28.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = aiName.ifBlank { "Mahi AI" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Button(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = !isUploadingAvatar
                                ) {
                                    Icon(
                                        Icons.Default.PhotoCamera,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isUploadingAvatar) "Uploading..." else "Upload Photo (Gallery)",
                                        color = Color.Black,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = aiName,
                            onValueChange = { aiName = it },
                            label = { Text("AI Assistant Name") },
                            placeholder = { Text("e.g. Mahi, Social India AI") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = aiAvatarUrl,
                            onValueChange = { aiAvatarUrl = it },
                            label = { Text("AI Avatar Image URL (Or Upload Photo above)") },
                            placeholder = { Text("https://example.com/ai_avatar.png") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // 2. Personality & Persona Modes Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFFFF4081))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("2. AI Persona Mode", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        val modes = listOf(
                            "COMPANION" to "💖 Companion / Friend (Dost Mode - Sweet, warm & caring)",
                            "HUMAN" to "👤 Natural Human Mode (Real, grounded everyday Indian person)",
                            "ASSISTANT" to "⚡ Smart Assistant (Crisp, fast, productive & intelligent)",
                            "CREATIVE" to "💡 Social Creator Mode (Viral reels, scripts, captions & ideas)"
                        )

                        modes.forEach { (modeKey, modeDesc) ->
                            val isSelected = personaMode.equals(modeKey, ignoreCase = true)
                            Surface(
                                onClick = { personaMode = modeKey },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFFEE1D52).copy(alpha = 0.15f) else Color.Transparent,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEE1D52)) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { personaMode = modeKey },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFEE1D52))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(modeDesc, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Custom AI Training & System Instructions
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFF76B900))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("3. Admin Training & System Instructions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Yahan aap AI ko custom commands ya instructions de sakte hain. AI hamesha in rules ko follow karega:",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            label = { Text("Custom System Prompt / Training Commands") },
                            placeholder = { Text("e.g. Always greet users with love. Promote Social India creators. Speak with sweet tone.") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // 4. Custom Knowledge Q&A Rules
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Quiz, contentDescription = null, tint = Color(0xFFFF9800))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("4. Custom Q&A Rules (Admin Memory)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Jab user specific sawal puchega (jaise 'tumhe kisne banaya?'), toh AI wahi exact jawab dega:",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Existing Q&A items
                        customQaList.forEachIndexed { index, pair ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Q: ${pair.first}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF00E5FF))
                                        Text("A: ${pair.second}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    IconButton(
                                        onClick = {
                                            val newList = customQaList.toMutableList()
                                            newList.removeAt(index)
                                            customQaList = newList
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Add new Q&A
                        OutlinedTextField(
                            value = newQuestion,
                            onValueChange = { newQuestion = it },
                            label = { Text("User Question / Keyword") },
                            placeholder = { Text("e.g. tumhe kisne banaya") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = newAnswer,
                            onValueChange = { newAnswer = it },
                            label = { Text("AI Exact Answer") },
                            placeholder = { Text("e.g. Mujhe Social India team ne banaya hai!") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (newQuestion.isNotBlank() && newAnswer.isNotBlank()) {
                                    val newList = customQaList.toMutableList()
                                    newList.add(newQuestion.trim() to newAnswer.trim())
                                    customQaList = newList
                                    newQuestion = ""
                                    newAnswer = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Q&A Rule", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 5. API Keys Configuration
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("5. API Credentials & Voice Engine", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = sarvamKey,
                            onValueChange = { sarvamKey = it },
                            label = { Text("Sarvam AI TTS API Key (Bulbul Neural Voice)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = geminiKey,
                            onValueChange = { geminiKey = it },
                            label = { Text("Gemini API Key (Chat & Vision)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = nvidiaKey,
                            onValueChange = { nvidiaKey = it },
                            label = { Text("NVIDIA NIM API Key (Fallback)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Save Action Button
            item {
                Button(
                    onClick = {
                        viewModel.saveAdminAiConfig(
                            aiName = aiName,
                            aiAvatarUrl = aiAvatarUrl,
                            systemPrompt = systemPrompt,
                            personaMode = personaMode,
                            customQaList = customQaList,
                            geminiKey = geminiKey,
                            nvidiaKey = nvidiaKey,
                            sarvamKey = sarvamKey
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Apply AI Settings", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
