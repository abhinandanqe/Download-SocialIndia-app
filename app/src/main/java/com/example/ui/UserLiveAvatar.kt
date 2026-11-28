package com.example.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.LivePresenceManager

@Composable
fun UserLiveAvatar(
    userId: String,
    avatarUrl: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val activeLives by LivePresenceManager.activeLives.collectAsStateWithLifecycle()
    val isLive = activeLives.containsKey(userId)
    val liveSession = activeLives[userId]

    LiveAvatar(
        avatarUrl = avatarUrl,
        userId = userId,
        isLiveOverride = isLive,
        size = size,
        modifier = modifier,
        onClick = onClick,
        onLiveClick = {
            if (liveSession != null) {
                try {
                    val currentUid = com.example.model.RepositoryManager.getCurrentUserId()
                    val intent = Intent(context, LiveActivity::class.java).apply {
                        putExtra("liveID", liveSession.storyId)
                        putExtra("userID", if (currentUid.isNotEmpty()) currentUid else "guest_${System.currentTimeMillis() % 10000}")
                        putExtra("userName", if (currentUid.isNotEmpty()) "User_${currentUid.take(5)}" else "Guest")
                        putExtra("isHost", false)
                        putExtra("hostUid", userId)
                        putExtra("hostUsername", liveSession.username.ifEmpty { "Host" })
                        putExtra("hostAvatar", avatarUrl)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            }
        }
    )
}
