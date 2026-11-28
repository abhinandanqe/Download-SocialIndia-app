package com.example.utils

import android.util.Log
import com.example.model.Mention
import com.example.model.RepositoryManager
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class MentionQuery(
    val query: String,
    val startIndex: Int,
    val endIndex: Int
)

object MentionHelper {

    /**
     * Extracts the active mention query starting with '@' immediately preceding the cursor.
     */
    fun extractMentionQuery(text: String, cursorPosition: Int): MentionQuery? {
        if (cursorPosition <= 0 || cursorPosition > text.length) return null
        val sub = text.substring(0, cursorPosition)
        val lastAt = sub.lastIndexOf('@')
        if (lastAt == -1) return null

        // Make sure there are no spaces or linebreaks between '@' and the cursor
        val candidate = sub.substring(lastAt + 1)
        if (candidate.any { it.isWhitespace() }) return null

        // Ensure '@' is at the start of line or preceded by a space/punctuation
        if (lastAt > 0) {
            val prevChar = sub[lastAt - 1]
            if (!prevChar.isWhitespace() && prevChar != '\n' && prevChar != '(' && prevChar != '[' && prevChar != '{') {
                return null
            }
        }

        return MentionQuery(
            query = candidate,
            startIndex = lastAt,
            endIndex = cursorPosition
        )
    }

    /**
     * Replaces the '@query' segment with '@username ' and computes the new cursor position.
     */
    fun replaceMention(text: String, cursorPosition: Int, username: String): Pair<String, Int> {
        val queryInfo = extractMentionQuery(text, cursorPosition) ?: return Pair(text, cursorPosition)
        val beforeAt = text.substring(0, queryInfo.startIndex)
        val afterCursor = text.substring(queryInfo.endIndex)
        val replacement = "@$username "
        val newText = beforeAt + replacement + afterCursor
        val newCursor = beforeAt.length + replacement.length
        return Pair(newText, newCursor)
    }

    /**
     * Extracts and resolves mentioned usernames from text into structured Mention items.
     */
    suspend fun extractMentionsFromText(text: String): List<Mention> = withContext(Dispatchers.IO) {
        val mentionRegex = Regex("@([a-zA-Z0-9_.]+)")
        val usernames = mentionRegex.findAll(text).map { it.groupValues[1].lowercase() }.toSet()
        if (usernames.isEmpty()) return@withContext emptyList<Mention>()

        val db = RepositoryManager.firestore
        val result = mutableListOf<Mention>()
        for (uname in usernames) {
            try {
                val snap = db.collection("users").whereEqualTo("username", uname).limit(1).get().await()
                val doc = snap.documents.firstOrNull()
                if (doc != null) {
                    result.add(Mention(uid = doc.id, username = uname))
                }
            } catch (e: Exception) {
                Log.w("MentionHelper", "Error resolving username @$uname", e)
            }
        }
        result
    }

    /**
     * Dispatches real, deduplicated mention notifications to all mentioned users.
     */
    suspend fun dispatchMentionNotifications(
        actorUid: String,
        actorUsername: String,
        actorAvatar: String,
        mentions: List<Mention>,
        contentId: String,
        contentType: String, // "post", "reel", "story", "comment", "channel"
        previewText: String,
        mediaUrl: String? = null
    ) = withContext(Dispatchers.IO) {
        if (actorUid.isEmpty() || mentions.isEmpty()) return@withContext

        val db = RepositoryManager.firestore
        val processedUids = mutableSetOf<String>()

        for (mention in mentions) {
            val targetUid = mention.uid
            if (targetUid.isEmpty() || targetUid == actorUid || processedUids.contains(targetUid)) {
                continue
            }
            processedUids.add(targetUid)

            try {
                // Ensure target has not blocked actor or deactivated
                val targetDoc = db.collection("users").document(targetUid).get().await()
                if (!targetDoc.exists()) continue
                if (targetDoc.getBoolean("isDeactivated") == true || targetDoc.getBoolean("isDeleted") == true) continue

                // Check privacy
                val whoCanMention = targetDoc.getString("whoCanMention") ?: "everyone"
                if (whoCanMention.equals("nobody", ignoreCase = true)) continue

                val notifId = "mention_${contentType}_${contentId}_${targetUid}"
                val notifData = hashMapOf(
                    "id" to notifId,
                    "recipientId" to targetUid,
                    "type" to "mention",
                    "actorId" to actorUid,
                    "actorUsername" to actorUsername.ifEmpty { "Someone" },
                    "actorAvatar" to actorAvatar,
                    "content" to "mentioned you in a $contentType: \"${previewText.take(60)}\"",
                    "postId" to if (contentType in listOf("post", "reel", "comment")) contentId else "",
                    "storyId" to if (contentType == "story") contentId else "",
                    "channelId" to if (contentType == "channel") contentId else "",
                    "contentType" to contentType,
                    "postThumbnail" to (mediaUrl ?: ""),
                    "timestamp" to FieldValue.serverTimestamp(),
                    "isRead" to false
                )

                // Write to target's notifications subcollection and top-level notifications
                db.collection("users").document(targetUid)
                    .collection("notifications").document(notifId)
                    .set(notifData).await()

                try {
                    db.collection("notifications").document(notifId).set(notifData).await()
                } catch (_: Exception) {}

            } catch (e: Exception) {
                Log.w("MentionHelper", "Failed to dispatch mention notification to $targetUid: ${e.message}")
            }
        }
    }
}
