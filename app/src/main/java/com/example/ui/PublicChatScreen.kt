package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.RepositoryManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class PublicMessage(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)

@Composable
fun PublicChatScreen() {
    val scope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<PublicMessage>() }
    var currentUserId by remember { mutableStateOf("") }
    var showGifPicker by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var chatError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        currentUserId = RepositoryManager.getCurrentUserId()
        val reg = RepositoryManager.firestore.collection("public_chat")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .limitToLast(100)
            .addSnapshotListener { snapshot, error ->
                isLoading = false
                if (error != null) {
                    chatError = "Unable to load chat right now."
                    return@addSnapshotListener
                }
                chatError = null
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        PublicMessage(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            username = doc.getString("username") ?: "User",
                            avatarUrl = doc.getString("avatar_url") ?: doc.getString("avatarUrl") ?: doc.getString("profilePicUrl") ?: "",
                            text = doc.getString("text") ?: "",
                            timestamp = try {
                                val raw = doc.get("timestamp")
                                when (raw) {
                                    is com.google.firebase.Timestamp -> raw.seconds * 1000
                                    is java.util.Date -> raw.time
                                    is Long -> raw
                                    is Double -> raw.toLong()
                                    is Number -> raw.toLong()
                                    else -> 0L
                                }
                            } catch (e: Exception) {
                                0L
                            }
                        )
                    }
                    messages.clear()
                    messages.addAll(list)
                }
            }
        onDispose {
            reg.remove()
        }
    }

    if (showGifPicker) {
        GifPickerBottomSheet(
            onDismissRequest = { showGifPicker = false },
            onGifSelected = { gif ->
                showGifPicker = false
                if (currentUserId.isNotEmpty()) {
                    scope.launch {
                        try {
                            val userDoc = RepositoryManager.firestore.collection("users").document(currentUserId).get().await()
                            val msg = hashMapOf(
                                "userId" to currentUserId,
                                "username" to (userDoc.getString("username") ?: "User"),
                                "avatarUrl" to (userDoc.getString("avatar_url") ?: userDoc.getString("profilePicUrl") ?: ""),
                                "text" to "[GIF:${gif.fullUrl}]",
                                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                            RepositoryManager.firestore.collection("public_chat").add(msg)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.e("PublicChat", "Error sending GIF", e)
                        }
                    }
                }
            },
            onStickerSelected = { sticker ->
                showGifPicker = false
                if (currentUserId.isNotEmpty()) {
                    scope.launch {
                        try {
                            val userDoc = RepositoryManager.firestore.collection("users").document(currentUserId).get().await()
                            val msg = hashMapOf(
                                "userId" to currentUserId,
                                "username" to (userDoc.getString("username") ?: "User"),
                                "avatarUrl" to (userDoc.getString("avatar_url") ?: userDoc.getString("profilePicUrl") ?: ""),
                                "text" to "[GIF:${sticker.mediaUrl.ifEmpty { sticker.thumbnailUrl }}]",
                                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                            RepositoryManager.firestore.collection("public_chat").add(msg)
                            RepositoryManager.stickerRepository.recordStickerUsage(currentUserId, sticker)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.e("PublicChat", "Error sending Sticker", e)
                        }
                    }
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
        if (isLoading && messages.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFEE1D52))
            }
        } else if (chatError != null && messages.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chatError ?: "Unable to load chat.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else if (messages.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No messages yet. Say hello!",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMe = msg.userId == currentUserId
                val isGif = msg.text.contains("[GIF:") || msg.text.contains("giphy.com") || msg.text.contains("tenor.com") || msg.text.endsWith(".gif")
                val gifUrl = if (msg.text.contains("[GIF:")) {
                    msg.text.substringAfter("[GIF:").substringBefore("]").trim()
                } else if (msg.text.contains("http")) {
                    Regex("""https?://\S+""").find(msg.text)?.value?.trim()
                } else null

                val cleanText = msg.text.replace(Regex("""\[GIF:.*?\]"""), "").trim()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    if (!isMe) {
                        AsyncImage(
                            model = msg.avatarUrl.takeIf { it.isNotEmpty() } ?: "https://api.dicebear.com/7.x/avataaars/png?seed=${msg.userId}",
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                        if (!isMe) {
                            Text(msg.username, color = Color.Gray, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        if (!gifUrl.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 200.dp)
                                    .heightIn(max = 140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF2E2E2E))
                            ) {
                                AsyncImage(
                                    model = gifUrl,
                                    contentDescription = "GIF",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)
                                )
                            }
                            if (cleanText.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        if (cleanText.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isMe) 16.dp else 0.dp,
                                        bottomEnd = if (isMe) 0.dp else 16.dp
                                    ))
                                    .background(if (isMe) Color(0xFFEE1D52) else Color(0xFF2E2E2E))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(cleanText, color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Say something...", color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    com.example.utils.SoundManager.playClickSound()
                    showGifPicker = true
                }
            ) {
                Icon(Icons.Default.Gif, contentDescription = "GIF", tint = Color(0xFFEE1D52), modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val sanitized = com.example.security.InputGuard.sanitizeChatMessage(messageText)
                    if (sanitized.isNotEmpty() && currentUserId.isNotEmpty() &&
                        com.example.security.InputGuard.allowAction("public_chat", 700L)
                    ) {
                        messageText = ""
                        scope.launch {
                            try {
                                val userDoc = RepositoryManager.firestore.collection("users").document(currentUserId).get().await()
                                val msg = hashMapOf(
                                    "userId" to currentUserId,
                                    "username" to (userDoc.getString("username") ?: "User"),
                                    "avatarUrl" to (userDoc.getString("avatar_url") ?: userDoc.getString("profilePicUrl") ?: ""),
                                    "text" to sanitized,
                                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                )
                                RepositoryManager.firestore.collection("public_chat").add(msg)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.e("PublicChat", "Error sending message", e)
                            }
                        }
                    }
                },
                enabled = messageText.isNotBlank() && currentUserId.isNotEmpty()
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFFEE1D52))
            }
        }
    }
}
