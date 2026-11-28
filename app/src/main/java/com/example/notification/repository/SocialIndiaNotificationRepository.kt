package com.example.notification.repository

import com.example.model.RepositoryManager
import com.example.notification.model.NotificationActor
import com.example.notification.model.NotificationFilter
import com.example.notification.model.NotificationMapper
import com.example.notification.model.SocialIndiaNotification
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Single source of truth for Social India Notifications.
 * Provides real-time synchronization, smart grouping, filtering, search, and idempotency.
 */
class SocialIndiaNotificationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Real-time stream of notifications for the given user, automatically smart-grouped.
     */
    fun listenNotifications(userId: String): Flow<List<SocialIndiaNotification>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users")
            .document(userId)
            .collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("NotificationRepo", "Error listening to notifications", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val rawList = snapshot.documents.mapNotNull { doc ->
                        NotificationMapper.fromDocument(doc)
                    }
                    val groupedList = applySmartGrouping(rawList)
                    trySend(groupedList)
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Real-time stream of unread notification count for the given user.
     */
    fun listenUnreadCount(userId: String): Flow<Int> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(0)
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users")
            .document(userId)
            .collection("notifications")
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    trySend(snapshot.size())
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Smart Grouping Algorithm:
     * Groups repetitive actions (e.g. multiple likes on the same post or reel)
     * into a single entry with multi-actor attribution.
     */
    private fun applySmartGrouping(rawList: List<SocialIndiaNotification>): List<SocialIndiaNotification> {
        val groupedResult = mutableListOf<SocialIndiaNotification>()
        val groupBuckets = mutableMapOf<String, MutableList<SocialIndiaNotification>>()

        for (item in rawList) {
            val key = item.groupKey
            // Only group likes, reactions, and follows if repetitive
            val isGroupable = item.type in listOf("like", "story_like", "reel_like", "follow")
            if (isGroupable && !key.isNullOrEmpty()) {
                val bucket = groupBuckets.getOrPut(key) { mutableListOf() }
                bucket.add(item)
            } else {
                groupedResult.add(item)
            }
        }

        for ((_, items) in groupBuckets) {
            if (items.isEmpty()) continue

            if (items.size == 1) {
                groupedResult.add(items.first())
            } else {
                val primary = items.first()
                val distinctActors = items.map {
                    NotificationActor(
                        uid = it.actorUid,
                        username = it.actorUsername,
                        displayName = it.actorDisplayName,
                        photoUrl = it.actorPhoto,
                        isVerified = it.actorIsVerified
                    )
                }.distinctBy { it.uid }

                val otherCount = (items.size - 1).coerceAtLeast(0)
                val groupedText = when (primary.type) {
                    "like", "story_like", "reel_like" -> {
                        if (distinctActors.size > 1) {
                            val secondActor = distinctActors.getOrNull(1)?.displayName ?: distinctActors.getOrNull(1)?.username ?: "another"
                            if (items.size > 2) {
                                "${primary.actorDisplayName}, $secondActor and ${items.size - 2} others liked your post."
                            } else {
                                "${primary.actorDisplayName} and $secondActor liked your post."
                            }
                        } else {
                            "${primary.actorDisplayName} and $otherCount others liked your post."
                        }
                    }
                    "follow" -> {
                        if (distinctActors.size > 1) {
                            val secondActor = distinctActors.getOrNull(1)?.displayName ?: distinctActors.getOrNull(1)?.username ?: "another"
                            if (items.size > 2) {
                                "${primary.actorDisplayName}, $secondActor and ${items.size - 2} others started following you."
                            } else {
                                "${primary.actorDisplayName} and $secondActor started following you."
                            }
                        } else {
                            "${primary.actorDisplayName} started following you."
                        }
                    }
                    else -> primary.text
                }

                val allRead = items.all { it.read }

                groupedResult.add(
                    primary.copy(
                        text = groupedText,
                        read = allRead,
                        groupedActors = distinctActors,
                        totalGroupCount = items.size
                    )
                )
            }
        }

        return groupedResult.sortedByDescending { it.createdAt }
    }

    /**
     * Marks a single notification as read.
     */
    suspend fun markAsRead(recipientUid: String, notificationId: String) {
        if (recipientUid.isEmpty() || notificationId.isEmpty()) return
        try {
            firestore.collection("users")
                .document(recipientUid)
                .collection("notifications")
                .document(notificationId)
                .update(mapOf("read" to true, "isRead" to true))
                .await()
        } catch (e: Exception) {
            android.util.Log.e("NotificationRepo", "Failed to mark as read", e)
        }
    }

    /**
     * Marks all notifications as read for current user.
     */
    suspend fun markAllAsRead(recipientUid: String) {
        if (recipientUid.isEmpty()) return
        try {
            val unreadDocs = firestore.collection("users")
                .document(recipientUid)
                .collection("notifications")
                .whereEqualTo("read", false)
                .get()
                .await()

            if (unreadDocs.isEmpty) return

            val batch = firestore.batch()
            for (doc in unreadDocs.documents) {
                batch.update(doc.reference, mapOf("read" to true, "isRead" to true))
            }
            batch.commit().await()
        } catch (e: Exception) {
            android.util.Log.e("NotificationRepo", "Failed to mark all as read", e)
        }
    }

    /**
     * Deletes a notification document.
     */
    suspend fun deleteNotification(recipientUid: String, notificationId: String) {
        if (recipientUid.isEmpty() || notificationId.isEmpty()) return
        try {
            firestore.collection("users")
                .document(recipientUid)
                .collection("notifications")
                .document(notificationId)
                .delete()
                .await()
        } catch (e: Exception) {
            android.util.Log.e("NotificationRepo", "Failed to delete notification", e)
        }
    }

    /**
     * Dispatches a new notification to a recipient with idempotency guarantee.
     */
    suspend fun dispatchNotification(
        idempotencyKey: String,
        recipientUid: String,
        actorUid: String,
        actorUsername: String,
        actorDisplayName: String,
        actorPhoto: String,
        actorIsVerified: Boolean = false,
        type: String,
        targetId: String? = null,
        targetType: String? = null,
        text: String,
        mediaUrl: String? = null,
        groupKey: String? = null,
        deepLink: String? = null,
        metadata: Map<String, Any> = emptyMap()
    ): Boolean {
        if (recipientUid.isEmpty() || recipientUid == actorUid) {
            return false // Do not notify oneself
        }

        try {
            val docRef = firestore.collection("users")
                .document(recipientUid)
                .collection("notifications")
                .document(idempotencyKey)

            val existing = docRef.get().await()
            if (existing.exists()) {
                // Already dispatched this exact event; update timestamp or return
                docRef.update(
                    mapOf(
                        "createdAt" to FieldValue.serverTimestamp(),
                        "read" to false
                    )
                ).await()
                return true
            }

            val data = hashMapOf(
                "id" to idempotencyKey,
                "recipientUid" to recipientUid,
                "actorUid" to actorUid,
                "actorUsername" to actorUsername,
                "actorDisplayName" to actorDisplayName,
                "actorPhoto" to actorPhoto,
                "actorIsVerified" to actorIsVerified,
                "type" to type,
                "targetId" to targetId,
                "targetType" to targetType,
                "text" to text,
                "mediaUrl" to mediaUrl,
                "createdAt" to FieldValue.serverTimestamp(),
                "read" to false,
                "groupKey" to (groupKey ?: if (!targetId.isNullOrEmpty()) "${type}_$targetId" else null),
                "deepLink" to (deepLink ?: ""),
                "metadata" to metadata
            )

            docRef.set(data).await()
            return true
        } catch (e: Exception) {
            android.util.Log.e("NotificationRepo", "Failed to dispatch notification", e)
            return false
        }
    }
}
