package com.example.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.R
import com.example.model.RepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles inline replies directly from the Android notification shade.
 * Dispatches message to Firebase chat and updates notification status.
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPLY = "com.example.notification.ACTION_REPLY"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_SENDER_ID = "extra_sender_id"
        const val EXTRA_SENDER_NAME = "extra_sender_name"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != ACTION_REPLY) return

        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = remoteInput.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim() ?: return
        if (replyText.isEmpty()) return

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val peerUserId = intent.getStringExtra(EXTRA_SENDER_ID) ?: ""
        val peerUserName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: "Chat"

        val currentUserId = RepositoryManager.getCurrentUserId()
        if (currentUserId.isEmpty() || peerUserId.isEmpty()) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Send reply to Firebase Chat
                RepositoryManager.socialRepository.sendMessage(
                    senderId = currentUserId,
                    receiverId = peerUserId,
                    message = replyText,
                    mediaType = "text"
                )

                // Update notification in system tray to confirm reply
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val repliedNotification = NotificationCompat.Builder(context, "social_india_messages_v2")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(peerUserName)
                    .setContentText("You: $replyText")
                    .setTimeoutAfter(3000)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(notificationId, repliedNotification)
            } catch (e: Exception) {
                android.util.Log.e("NotificationReply", "Failed to send inline reply", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
