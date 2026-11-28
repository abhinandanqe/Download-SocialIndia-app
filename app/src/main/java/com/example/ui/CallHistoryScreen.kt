package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.RepositoryManager
import com.example.zego.SocialIndiaZegoManager
import java.text.SimpleDateFormat
import java.util.*

data class CallHistoryDisplayItem(
    val callId: String,
    val peerUid: String,
    val peerUsername: String,
    val peerPhotoUrl: String,
    val isVideo: Boolean,
    val isOutgoing: Boolean,
    val status: String,
    val timestamp: Long,
    val durationSeconds: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var callList by remember { mutableStateOf<List<CallHistoryDisplayItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val currentUserId = RepositoryManager.getCurrentUserId()
        if (currentUserId.isNotEmpty()) {
            try {
                val snapshot = RepositoryManager.firestore.collection("users")
                    .document(currentUserId)
                    .collection("call_history")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()

                callList = snapshot.documents.map { doc ->
                    CallHistoryDisplayItem(
                        callId = doc.getString("callId") ?: doc.id,
                        peerUid = doc.getString("peerUid") ?: "",
                        peerUsername = doc.getString("peerUsername") ?: "User",
                        peerPhotoUrl = doc.getString("peerPhotoUrl") ?: "",
                        isVideo = doc.getBoolean("isVideo") ?: true,
                        isOutgoing = doc.getBoolean("isOutgoing") ?: true,
                        status = doc.getString("status") ?: "ended",
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                        durationSeconds = doc.getLong("duration") ?: 0L
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("CallHistoryScreen", "Failed to fetch call history", e)
            } finally {
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
                    Text(
                        "Call History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (callList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhoneCallback,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No Recent Calls",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Voice and video calls made with friends and creators will appear here.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(callList, key = { it.callId }) { item ->
                        CallHistoryRow(
                            item = item,
                            onCallback = {
                                if (item.peerUid.isNotEmpty()) {
                                    if (item.isVideo) {
                                        SocialIndiaZegoManager.startVideoCall(
                                            context, item.peerUid, item.peerUsername, item.peerPhotoUrl
                                        )
                                    } else {
                                        SocialIndiaZegoManager.startVoiceCall(
                                            context, item.peerUid, item.peerUsername, item.peerPhotoUrl
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CallHistoryRow(
    item: CallHistoryDisplayItem,
    onCallback: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val formattedDate = remember(item.timestamp) { dateFormat.format(Date(item.timestamp)) }

    val formattedDuration = remember(item.durationSeconds) {
        if (item.durationSeconds > 0) {
            val min = item.durationSeconds / 60
            val sec = item.durationSeconds % 60
            String.format("%02d:%02d", min, sec)
        } else {
            ""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCallback() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Peer Avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = item.peerPhotoUrl.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${item.peerUid}" },
                contentDescription = item.peerUsername,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Call Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.peerUsername,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isMissed = item.status == "missed"
                val callDirectionIcon = when {
                    isMissed -> Icons.AutoMirrored.Filled.CallMissed
                    item.isOutgoing -> Icons.AutoMirrored.Filled.CallMade
                    else -> Icons.AutoMirrored.Filled.CallReceived
                }
                val iconTint = when {
                    isMissed -> Color(0xFFE53935)
                    item.isOutgoing -> Color(0xFF4CAF50)
                    else -> MaterialTheme.colorScheme.primary
                }

                Icon(
                    callDirectionIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = buildString {
                        append(if (item.isVideo) "Video Call • " else "Voice Call • ")
                        append(formattedDate)
                        if (formattedDuration.isNotEmpty()) {
                            append(" ($formattedDuration)")
                        }
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Action Button
        IconButton(onClick = onCallback) {
            Icon(
                if (item.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = "Return call",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
