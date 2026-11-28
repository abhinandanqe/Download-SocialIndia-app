package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R
import com.example.model.RepositoryManager
import com.example.notification.model.SocialIndiaNotification
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import java.net.URL

/**
 * Next-Generation Centralized Notification Engine for Social India.
 * Handles Versioned Channels, Rich Media, MessagingStyle with Inline Reply,
 * Multi-account UID binding, and Idempotent Dispatch.
 */
object SocialIndiaNotificationManager {

    private const val TAG = "SINotificationManager"
    private const val GROUP_SOCIAL_INDIA_V2 = "group_social_india_v2"

    // Versioned Android Notification Channel IDs
    const val CHANNEL_SOCIAL_V2 = "social_india_social_v2"
    const val CHANNEL_MESSAGES_V2 = "social_india_messages_v2"
    const val CHANNEL_GROUPS_V2 = "social_india_groups_v2"
    const val CHANNEL_LIVE_V2 = "social_india_live_v2"
    const val CHANNEL_CREATOR_V2 = "social_india_creator_v2"
    const val CHANNEL_SECURITY_V2 = "social_india_security_v2"
    const val CHANNEL_SYSTEM_V2 = "social_india_system_v2"

    // Active listener registration and account state
    private var activeListener: ListenerRegistration? = null
    private var activeListeningUid: String = ""
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Idempotency tracking to avoid duplicate system shade notifications
    private val processedEventIds = mutableSetOf<String>()

    /**
     * Initializes versioned Notification Channels and cleans up legacy channels.
     */
    fun setupNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Clean up old legacy channels
            val legacyChannels = listOf(
                "social_india_channel",
                "social_india_real_alerts",
                "social_india_social",
                "social_india_messages",
                "social_india_groups",
                "social_india_live",
                "social_india_creator",
                "social_india_security",
                "social_india_system",
                "social_india_ai"
            )
            for (oldChannel in legacyChannels) {
                try {
                    notificationManager.deleteNotificationChannel(oldChannel)
                } catch (e: Exception) {
                    // Ignore deletion of non-existing channels
                }
            }

            // 2. Create versioned group
            notificationManager.createNotificationChannelGroup(
                NotificationChannelGroup(GROUP_SOCIAL_INDIA_V2, "Social India")
            )

            // Sound configuration based on user preference
            val soundMode = SocialIndiaNotificationPreferences.getSoundMode(context)
            val soundUri: Uri? = when (soundMode) {
                "silent" -> null
                "default" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                else -> SocialIndiaNotificationSoundManager.getNotificationSoundUri(context)
            }

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val vibrationEnabled = SocialIndiaNotificationPreferences.isVibrationEnabled(context)

            // 3. Register versioned channels
            val channels = listOf(
                NotificationChannel(CHANNEL_SOCIAL_V2, "Social Activity", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Likes, Comments, Follows & Mentions"
                    group = GROUP_SOCIAL_INDIA_V2
                    enableVibration(vibrationEnabled)
                    if (soundUri != null) setSound(soundUri, audioAttributes) else setSound(null, null)
                },
                NotificationChannel(CHANNEL_MESSAGES_V2, "Direct Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Chat messages and direct communication"
                    group = GROUP_SOCIAL_INDIA_V2
                    enableVibration(vibrationEnabled)
                    if (soundUri != null) setSound(soundUri, audioAttributes) else setSound(null, null)
                },
                NotificationChannel(CHANNEL_GROUPS_V2, "Groups & Communities", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Group discussions and broadcast updates"
                    group = GROUP_SOCIAL_INDIA_V2
                    enableVibration(vibrationEnabled)
                    if (soundUri != null) setSound(soundUri, audioAttributes) else setSound(null, null)
                },
                NotificationChannel(CHANNEL_LIVE_V2, "Live Streams", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Live broadcast alerts from friends and creators"
                    group = GROUP_SOCIAL_INDIA_V2
                    enableVibration(vibrationEnabled)
                    if (soundUri != null) setSound(soundUri, audioAttributes) else setSound(null, null)
                },
                NotificationChannel(CHANNEL_CREATOR_V2, "Creator & Monetization", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Milestones, monetization status, and insights"
                    group = GROUP_SOCIAL_INDIA_V2
                    enableVibration(vibrationEnabled)
                    if (soundUri != null) setSound(soundUri, audioAttributes) else setSound(null, null)
                },
                NotificationChannel(CHANNEL_SECURITY_V2, "Security & Account", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Login alerts, security updates, and session events"
                    group = GROUP_SOCIAL_INDIA_V2
                    enableVibration(vibrationEnabled)
                    if (soundUri != null) setSound(soundUri, audioAttributes) else setSound(null, null)
                },
                NotificationChannel(CHANNEL_SYSTEM_V2, "System Updates", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "App updates and announcements"
                    group = GROUP_SOCIAL_INDIA_V2
                    enableVibration(false)
                    setSound(null, null)
                }
            )

            channels.forEach { notificationManager.createNotificationChannel(it) }
        }
    }

    /**
     * Attaches real-time listener for current active UID.
     * Automatically handles account switches and cleans up previous listeners.
     */
    @Synchronized
    fun attachUserListener(context: Context, uid: String) {
        if (uid.isEmpty()) {
            detachUserListener()
            return
        }

        if (activeListeningUid == uid && activeListener != null) {
            return // Already listening to this active account
        }

        detachUserListener()
        activeListeningUid = uid

        setupNotificationChannels(context)

        try {
            activeListener = RepositoryManager.firestore
                .collection("users")
                .document(uid)
                .collection("notifications")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(40)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    try {
                        for (change in snapshot.documentChanges) {
                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                val doc = change.document
                                val isRead = (doc.get("isRead") as? Boolean) ?: (doc.get("read") as? Boolean) ?: false
                                if (isRead) continue

                                val notif = try {
                                    com.example.notification.model.NotificationMapper.fromDocument(doc)
                                } catch (_: Exception) {
                                    null
                                } ?: continue

                                // Check idempotency
                                if (processedEventIds.contains(notif.id)) continue
                                processedEventIds.add(notif.id)

                                // Dispatch system notification if enabled in preferences
                                if (SocialIndiaNotificationPreferences.isPushEnabled(context)) {
                                    dispatchSystemNotification(context.applicationContext, notif)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
        } catch (_: Exception) {}
    }

    @Synchronized
    fun detachUserListener() {
        activeListener?.remove()
        activeListener = null
        activeListeningUid = ""
    }

    /**
     * Dispatches a rich system notification to Android notification shade.
     */
    fun dispatchSystemNotification(context: Context, notification: SocialIndiaNotification) {
        val channelId = when (notification.type) {
            "message" -> CHANNEL_MESSAGES_V2
            "group_message", "group_invite" -> CHANNEL_GROUPS_V2
            "live_start", "live_invite" -> CHANNEL_LIVE_V2
            "creator_milestone", "monetization" -> CHANNEL_CREATOR_V2
            "security_alert", "new_login" -> CHANNEL_SECURITY_V2
            "system", "announcement" -> CHANNEL_SYSTEM_V2
            else -> CHANNEL_SOCIAL_V2
        }

        // Check if category is enabled in settings
        val categoryKey = when (channelId) {
            CHANNEL_MESSAGES_V2 -> "messages"
            CHANNEL_LIVE_V2 -> "live"
            CHANNEL_CREATOR_V2 -> "creator"
            CHANNEL_SECURITY_V2 -> "security"
            else -> "social"
        }
        if (!SocialIndiaNotificationPreferences.isCategoryEnabled(context, categoryKey)) {
            return
        }

        scope.launch {
            try {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("deep_link_route", notification.deepLink)
                    putExtra("notification_id", notification.id)
                    putExtra("target_id", notification.targetId)
                }

                val requestCode = (notification.id.hashCode() and 0x7FFFFFFF)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )

                val builder = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

                val showPreview = SocialIndiaNotificationPreferences.isMessagePreviewEnabled(context)

                // High-fidelity messaging style for chat notifications
                if (notification.type == "message") {
                    val userPerson = Person.Builder()
                        .setName("You")
                        .build()

                    val peerPerson = Person.Builder()
                        .setName(notification.actorDisplayName.ifEmpty { notification.actorUsername })
                        .setKey(notification.actorUid)
                        .build()

                    val messageBody = if (showPreview) notification.text.ifEmpty { "Sent you a message" } else "New message"

                    val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
                        .setConversationTitle(notification.actorDisplayName.ifEmpty { notification.actorUsername })
                        .addMessage(messageBody, notification.createdAt, peerPerson)

                    builder.setStyle(messagingStyle)
                    builder.setContentTitle(notification.actorDisplayName.ifEmpty { notification.actorUsername })
                    builder.setContentText(messageBody)
                    builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                    builder.setCategory(NotificationCompat.CATEGORY_MESSAGE)

                    // Add Inline Reply action
                    val remoteInput = RemoteInput.Builder(NotificationReplyReceiver.KEY_TEXT_REPLY)
                        .setLabel("Reply to ${notification.actorUsername}...")
                        .build()

                    val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
                        action = NotificationReplyReceiver.ACTION_REPLY
                        putExtra(NotificationReplyReceiver.EXTRA_NOTIFICATION_ID, requestCode)
                        putExtra(NotificationReplyReceiver.EXTRA_SENDER_ID, notification.actorUid)
                        putExtra(NotificationReplyReceiver.EXTRA_SENDER_NAME, notification.actorUsername)
                    }

                    val replyPendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode + 1,
                        replyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                    )

                    val replyAction = NotificationCompat.Action.Builder(
                        R.drawable.ic_notification,
                        "Reply",
                        replyPendingIntent
                    ).addRemoteInput(remoteInput).build()

                    builder.addAction(replyAction)
                } else {
                    // Standard Social Notification
                    val title = notification.actorDisplayName.ifEmpty { notification.actorUsername }
                    val body = when (notification.type) {
                        "like" -> "liked your post."
                        "comment" -> if (notification.text.isNotBlank()) "commented: \"${notification.text}\"" else "commented on your post."
                        "follow" -> "started following you."
                        "mention" -> "mentioned you."
                        "story_mention" -> "mentioned you in a story."
                        "live_start" -> "is live streaming now!"
                        "creator_milestone" -> notification.text.ifEmpty { "Congratulations on your milestone!" }
                        "security_alert" -> notification.text.ifEmpty { "Security check required on your account." }
                        else -> notification.text.ifEmpty { "You have a new update." }
                    }

                    builder.setContentTitle(title)
                    builder.setContentText(body)
                    builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    builder.setCategory(NotificationCompat.CATEGORY_SOCIAL)

                    // Contextual Action Button
                    if (notification.type == "follow" && notification.actorUid.isNotEmpty()) {
                        val profileIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("deep_link_route", "profile/${notification.actorUid}")
                        }
                        val profilePending = PendingIntent.getActivity(
                            context,
                            requestCode + 2,
                            profileIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                        )
                        builder.addAction(R.drawable.ic_notification, "View Profile", profilePending)
                    } else if (notification.type == "live_start" && !notification.targetId.isNullOrEmpty()) {
                        builder.addAction(R.drawable.ic_notification, "Watch Live", pendingIntent)
                    }

                    // Optional media thumbnail
                    if (!notification.mediaUrl.isNullOrEmpty()) {
                        try {
                            val bitmap = downloadBitmap(notification.mediaUrl)
                            if (bitmap != null) {
                                builder.setLargeIcon(bitmap)
                                builder.setStyle(
                                    NotificationCompat.BigPictureStyle()
                                        .bigPicture(bitmap)
                                        .setBigContentTitle(title)
                                        .setSummaryText(body)
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not load notification thumbnail", e)
                        }
                    }
                }

                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.notify(requestCode, builder.build())
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification permission not granted", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying notification", e)
            }
        }
    }

    private fun downloadBitmap(urlStr: String): Bitmap? {
        return try {
            val url = URL(urlStr)
            val connection = url.openConnection()
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            BitmapFactory.decodeStream(connection.getInputStream())
        } catch (e: Exception) {
            null
        }
    }
}
