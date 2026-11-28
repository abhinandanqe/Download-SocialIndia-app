package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.RepositoryManager
import com.example.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

@Composable
fun DirectChatScreenWrapper(
    targetUserId: String,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    BackHandler(onBack = onBack)

    var targetUser by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    val currentUserId = remember(retryTrigger) { RepositoryManager.getCurrentUserId() }
    val videoViewModel: com.example.viewmodel.VideoViewModel = viewModel()
    val videos by videoViewModel.videos.collectAsStateWithLifecycle()

    LaunchedEffect(targetUserId, retryTrigger) {
        if (currentUserId.isBlank()) {
            isLoading = false
            errorMessage = "Please sign in to send direct messages."
            return@LaunchedEffect
        }
        if (targetUserId.isBlank()) {
            isLoading = false
            errorMessage = "Conversation user could not be found."
            return@LaunchedEffect
        }
        isLoading = true
        errorMessage = null
        try {
            val doc = RepositoryManager.firestore.collection("users").document(targetUserId).get().await()
            targetUser = if (doc.exists()) {
                User(
                    id = doc.id,
                    username = doc.getString("username") ?: "User",
                    avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatar_url") ?: "",
                    isVerified = doc.getBoolean("isVerified") ?: false
                )
            } else {
                User(id = targetUserId, username = "Unknown", avatarUrl = "")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = "Unable to load chat. Check your connection and try again."
        } finally {
            isLoading = false
        }
    }

    val loadedTargetUser = targetUser
    when {
        loadedTargetUser != null && currentUserId.isNotEmpty() -> {
            ChatWindow(
                currentUserId = currentUserId,
                otherUser = loadedTargetUser,
                videos = videos,
                onDismiss = onBack,
                onNavigateToHome = onNavigateToHome
            )
        }
        errorMessage != null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = errorMessage ?: "Unable to open chat",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("direct_chat_back_button")
                        ) {
                            Text("Go Back")
                        }
                        if (currentUserId.isNotBlank() && targetUserId.isNotBlank()) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = { retryTrigger++ },
                                modifier = Modifier.testTag("direct_chat_retry_button")
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
        else -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
