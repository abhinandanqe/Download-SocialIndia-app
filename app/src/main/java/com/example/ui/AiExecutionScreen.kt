package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.AIFeatureRegistry
import com.example.model.AiTool
import com.example.viewmodel.AiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiExecutionScreen(
    aiId: String,
    onNavigateBack: () -> Unit,
    viewModel: AiViewModel = viewModel()
) {
    val context = LocalContext.current
    val aiTools by viewModel.aiTools.collectAsStateWithLifecycle()
    
    val fallbackFeature = remember(aiId) { AIFeatureRegistry.getFeature(aiId) }
    val tool = aiTools.find { it.id == aiId } ?: fallbackFeature?.let {
        AiTool(
            id = it.featureId,
            name = it.name,
            description = it.description,
            category = it.category,
            status = if (AIFeatureRegistry.isFeatureAvailable(it.featureId)) "Active" else "Disabled"
        )
    }

    val requestState by viewModel.requestState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }

    val suggestedPrompts = remember(aiId) {
        when (aiId) {
            "ai_caption" -> listOf(
                "Travel sunset reel with soulful vibe",
                "Casual gym fitness reel motivational",
                "Funny foodie video caption in Hinglish",
                "Traditional festival celebration post"
            )
            "ai_hashtag" -> listOf(
                "Viral dance reels India",
                "Street food vlog Delhi",
                "Tech coding & development",
                "Fitness motivation Hindi"
            )
            "ai_reels_studio" -> listOf(
                "5 Life hacks nobody tells you (30s hook)",
                "Day in the life of a creator (Fast cuts)",
                "How to edit videos like a pro (Step-by-step)"
            )
            "ai_story_studio" -> listOf(
                "Poll idea: Tea vs Coffee debate",
                "Engaging Q&A for followers this weekend",
                "Behind the scenes teaser for new Reel"
            )
            "ai_comments" -> listOf(
                "Thank you so much for the love! ❤️",
                "Glad you enjoyed this reel! More coming soon.",
                "Hinglish reply: Bhai shukriya aapka!"
            )
            else -> listOf(
                "Profile bio improve karo for food creator",
                "Viral Reel hook ideas in Hinglish",
                "Comment reply suggest karo",
                "Is week ke top content ideas do"
            )
        }
    }

    LaunchedEffect(aiId) {
        viewModel.fetchAiTools()
        viewModel.fetchHistory()
        viewModel.resetRequestState()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tool?.name ?: "AI Assistant", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = "Powered by NVIDIA NIM",
                            fontSize = 11.sp,
                            color = Color(0xFF76B900),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
        if (tool == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFEE1D52))
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Suggested Prompts
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestedPrompts) { prompt ->
                    SuggestionChip(
                        onClick = { inputText = prompt },
                        label = { Text(prompt, fontSize = 12.sp) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            // Results and History Area
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                reverseLayout = true
            ) {
                if (requestState is AiViewModel.RequestState.Error) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = (requestState as AiViewModel.RequestState.Error).message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                if (requestState is AiViewModel.RequestState.Success) {
                    item {
                        val result = (requestState as AiViewModel.RequestState.Success).result
                        AiResultBubble(text = result, onCopy = {
                            val clip = ClipData.newPlainText("AI Result", result)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        })
                    }
                }
                if (requestState is AiViewModel.RequestState.Loading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFFEE1D52))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Generating response via NVIDIA NIM...", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }
                
                val currentToolHistory = history.filter { it.aiId == aiId }
                items(currentToolHistory) { hist ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        AiMessageBubble(text = hist.prompt, isUser = true)
                        Spacer(modifier = Modifier.height(4.dp))
                        AiResultBubble(text = hist.resultText, onCopy = {
                            val clip = ClipData.newPlainText("AI Result", hist.resultText)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            }

            // Input Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask or describe what you need...", fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.executeAiRequest(tool, inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFEE1D52), RoundedCornerShape(24.dp))
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Execute", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun AiResultBubble(text: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Result",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF76B900)
                )
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun AiMessageBubble(text: String, isUser: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isUser) Color(0xFFEE1D52) else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = text,
                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}
