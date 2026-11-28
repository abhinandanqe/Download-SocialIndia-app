package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.model.RepositoryManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SocialIndiaMessagingService : FirebaseMessagingService() {

    private val TAG = "SocialIndiaFCM"
    private val CALL_CHANNEL_ID = "social_india_calls"
    private val MSG_CHANNEL_ID = "social_india_messages"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Registration Token: $token")
        
        try {
            if (RepositoryManager.isRealUserLoggedIn()) {
                val userId = RepositoryManager.getCurrentUserId()
                if (userId.isNotEmpty()) {
                    RepositoryManager.firestore.collection("users")
                        .document(userId)
                        .update("fcmToken", token)
                        .addOnSuccessListener {
                            Log.d(TAG, "FCM Token saved successfully to Firestore.")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to save FCM token to Firestore", e)
                        }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating FCM token", e)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Received FCM Message from: ${remoteMessage.from}")

        // 1. Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "FCM Message Data payload: ${remoteMessage.data}")
            val type = remoteMessage.data["type"] ?: ""
            val peerName = remoteMessage.data["peerName"] ?: "Someone"
            val messageText = remoteMessage.data["message"] ?: ""

            if (type == "call") {
                val isVideo = remoteMessage.data["mediaType"] == "video"
                showCallNotification(peerName, isVideo)
            } else {
                showStandardNotification(peerName, messageText, remoteMessage.data["thumbnailUrl"])
            }
        }

        // 2. Check if message contains notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "FCM Message Notification Body: ${it.body}")
            showStandardNotification(it.title ?: "Social India", it.body ?: "", remoteMessage.data["thumbnailUrl"])
        }
    }

    private fun showCallNotification(peerName: String, isVideo: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels(notificationManager)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("nav_to", "inbox")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callTypeStr = if (isVideo) "Video Call" else "Audio Call"
        val ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val notificationBuilder = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Incoming $callTypeStr")
            .setContentText("$peerName is calling you on Social India")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setSound(ringUri)
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true) // Show high-priority heads-up notification

        notificationManager.notify(101, notificationBuilder.build())
    }

    private fun showStandardNotification(title: String, messageText: String, thumbnailUrl: String? = null) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels(notificationManager)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        val notificationBuilder = NotificationCompat.Builder(this, MSG_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(messageText)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            
        // If a thumbnail URL is provided, try to load it and apply BigPictureStyle
        if (!thumbnailUrl.isNullOrEmpty()) {
            try {
                val url = java.net.URL(thumbnailUrl)
                val bitmap = android.graphics.BitmapFactory.decodeStream(url.openConnection().getInputStream())
                if (bitmap != null) {
                    notificationBuilder.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .setBigContentTitle(title)
                            .setSummaryText(messageText)
                    )
                    notificationBuilder.setLargeIcon(bitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load thumbnail for BigPictureStyle", e)
            }
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun createNotificationChannels(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Call Notification Channel
            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                "Call Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming Social India Audio & Video call alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(1000, 1000, 1000, 1000)
            }
            notificationManager.createNotificationChannel(callChannel)

            // Message Notification Channel
            val msgChannel = NotificationChannel(
                MSG_CHANNEL_ID,
                "New Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Social India new messages and comments feed updates"
            }
            notificationManager.createNotificationChannel(msgChannel)
        }
    }
}
