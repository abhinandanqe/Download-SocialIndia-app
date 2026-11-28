package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.RepositoryManager
import com.example.ui.Notification
import com.example.ui.NotificationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NotificationViewModel : ViewModel() {

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        fetchNotifications()
    }

    fun retry() {
        _errorMessage.value = null
        fetchNotifications()
    }

    private fun formatRelativeTime(millis: Long): String {
        val diff = System.currentTimeMillis() - millis
        if (diff < 60_000L) return "Just now"
        val minutes = diff / 60_000L
        if (minutes < 60) return "${minutes}m"
        val hours = diff / 3_600_000L
        if (hours < 24) return "${hours}h"
        val days = diff / 86_400_000L
        if (days < 7) return "${days}d"
        val weeks = days / 7
        return "${weeks}w"
    }

    private fun parseTimestampMillis(raw: Any?): Long {
        return when (raw) {
            is com.google.firebase.Timestamp -> raw.toDate().time
            is java.util.Date -> raw.time
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: System.currentTimeMillis()
            else -> System.currentTimeMillis()
        }
    }

    private fun parseNotificationDoc(doc: com.google.firebase.firestore.DocumentSnapshot): Notification? {
        val data = doc.data ?: return null
        val tsMillis = parseTimestampMillis(data["timestamp"])
        val timeStr = formatRelativeTime(tsMillis)
        val rawType = data["type"]?.toString()?.lowercase() ?: ""

        val notifType = when {
            rawType == "story_mention" || (rawType == "mention" && (data["contentType"]?.toString() == "story" || !data["storyId"]?.toString().isNullOrEmpty())) -> NotificationType.STORY_MENTION
            rawType == "like" -> NotificationType.LIKE
            rawType == "comment" -> NotificationType.COMMENT
            rawType == "mention" -> NotificationType.MENTION
            rawType == "reply" -> NotificationType.REPLY
            rawType == "share" -> NotificationType.SHARE
            rawType == "story" -> NotificationType.STORY
            rawType == "verification" -> NotificationType.VERIFICATION
            rawType in listOf("collaboration_request", "collaboration_invite") -> NotificationType.COLLABORATION_REQUEST
            rawType == "collaboration_accepted" -> NotificationType.COLLABORATION_ACCEPTED
            rawType == "collaboration_declined" -> NotificationType.COLLABORATION_DECLINED
            rawType == "collaboration_removed" -> NotificationType.COLLABORATION_REMOVED
            rawType == "new_post" -> NotificationType.NEW_POST
            rawType in listOf("channel_update", "channel_message", "channel_reply") -> NotificationType.CHANNEL_UPDATE
            rawType in listOf("message", "group_message") -> NotificationType.MESSAGE
            rawType == "follow_request" -> NotificationType.FOLLOW_REQUEST
            rawType == "follow_accepted" -> NotificationType.FOLLOW_ACCEPTED
            rawType in listOf("live", "live_started") -> NotificationType.LIVE
            rawType == "note_reply" -> NotificationType.NOTE_REPLY
            else -> NotificationType.FOLLOW
        }

        return Notification(
            id = doc.id,
            type = notifType,
            userId = data["senderId"]?.toString() ?: data["actorId"]?.toString() ?: "",
            username = data["senderName"]?.toString() ?: data["actorUsername"]?.toString() ?: "User",
            avatarUrl = data["senderProfilePic"]?.toString() ?: data["actorAvatar"]?.toString() ?: "",
            content = data["message"]?.toString() ?: data["content"]?.toString() ?: "",
            time = timeStr,
            isRead = data["isRead"] as? Boolean ?: false,
            postId = data["postId"]?.toString(),
            storyId = data["storyId"]?.toString(),
            channelId = data["channelId"]?.toString(),
            commentId = data["commentId"]?.toString(),
            contentType = data["contentType"]?.toString(),
            postThumbnail = data["postThumbnail"]?.toString() ?: data["mediaUrl"]?.toString(),
            timestamp = tsMillis.toString(),
            timestampMillis = tsMillis
        )
    }

    private var notifListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var fallbackNotifListener: com.google.firebase.firestore.ListenerRegistration? = null

    private fun fetchNotifications() {
        viewModelScope.launch {
            _isLoading.value = true
            val currentUserId = RepositoryManager.getCurrentUserId()
            if (currentUserId.isEmpty()) {
                _isLoading.value = false
                return@launch
            }
            notifListener?.remove()
            fallbackNotifListener?.remove()
            notifListener = RepositoryManager.firestore.collection("users").document(currentUserId).collection("notifications")
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    _isLoading.value = false
                    if (error == null && snapshot != null) {
                        _errorMessage.value = null
                        val combinedNotifications = snapshot.documents.mapNotNull { doc ->
                            parseNotificationDoc(doc)
                        }
                        _notifications.value = combinedNotifications.sortedByDescending { it.timestampMillis }
                    } else if (error != null) {
                        Log.w("NotificationVM", "Error listening to users/uid/notifications: ${error.message}")
                        fallbackNotifListener?.remove()
                        fallbackNotifListener = RepositoryManager.firestore.collection("notifications")
                            .whereEqualTo("recipientId", currentUserId)
                            .limit(50)
                            .addSnapshotListener { topSnapshot, topError ->
                                if (topError == null && topSnapshot != null) {
                                    _errorMessage.value = null
                                    val combinedNotifications = topSnapshot.documents.mapNotNull { doc ->
                                        parseNotificationDoc(doc)
                                    }
                                    _notifications.value = combinedNotifications.sortedByDescending { it.timestampMillis }
                                } else if (topError != null) {
                                    Log.w("NotificationVM", "Fallback top-level notifications also failed: ${topError.message}")
                                    _errorMessage.value = "Unable to load notifications. Tap Retry."
                                }
                            }
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        notifListener?.remove()
        notifListener = null
        fallbackNotifListener?.remove()
        fallbackNotifListener = null
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            try {
                val currentUserId = RepositoryManager.getCurrentUserId()
                if (currentUserId.isNotEmpty()) {
                    RepositoryManager.firestore.collection("users")
                        .document(currentUserId)
                        .collection("notifications")
                        .document(notificationId)
                        .update("isRead", true)
                        .await()
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e("NotificationVM", "Failed to mark as read: ${e.message}")
            }
        }
        _notifications.value = _notifications.value.map { 
            if (it.id == notificationId) it.copy(isRead = true) else it 
        }
    }

    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            try {
                val currentUserId = RepositoryManager.getCurrentUserId()
                if (currentUserId.isNotEmpty()) {
                    RepositoryManager.firestore.collection("users")
                        .document(currentUserId)
                        .collection("notifications")
                        .document(notificationId)
                        .delete()
                        .await()
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e("NotificationVM", "Failed to delete notification: ${e.message}")
            }
        }
        _notifications.value = _notifications.value.filter { it.id != notificationId }
    }
}