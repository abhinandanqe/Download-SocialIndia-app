package com.example.repository

import android.util.Log
import com.example.model.RepositoryManager
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

data class FeedPollOption(
    val id: Int = 0,
    val text: String = "",
    val votesCount: Int = 0
)

data class FeedPollPost(
    val id: String = "",
    val creatorId: String = "",
    val creatorUsername: String = "",
    val creatorAvatarUrl: String = "",
    val question: String = "",
    val options: List<FeedPollOption> = emptyList(),
    val voters: Map<String, Int> = emptyMap(), // userId -> optionId
    val isQuiz: Boolean = false,
    val correctOptionId: Int = -1,
    val quizExplanation: String = "",
    val totalVotes: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
) {
    val isExpired: Boolean
        get() = expiresAt > 0L && System.currentTimeMillis() > expiresAt
}

object FeedPollRepository {
    private const val COLLECTION_FEED_POLLS = "feed_polls"

    fun listenActivePolls(limit: Long = 20L): Flow<List<FeedPollPost>> = callbackFlow {
        val listener = RepositoryManager.firestore.collection(COLLECTION_FEED_POLLS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w("FeedPollRepo", "Poll listen error: ${err.message}")
                    return@addSnapshotListener
                }
                if (snap != null) {
                    val list = snap.documents.mapNotNull { doc ->
                        try {
                            val rawOptions = (doc.get("options") as? List<*>) ?: emptyList<Any>()
                            val parsedOptions = rawOptions.mapIndexedNotNull { idx, item ->
                                val map = item as? Map<*, *> ?: return@mapIndexedNotNull null
                                FeedPollOption(
                                    id = (map["id"] as? Number)?.toInt() ?: idx,
                                    text = (map["text"] as? String).orEmpty(),
                                    votesCount = (map["votesCount"] as? Number)?.toInt() ?: 0
                                )
                            }
                            if (parsedOptions.size < 2) return@mapNotNull null

                            val rawVoters = (doc.get("voters") as? Map<*, *>) ?: emptyMap<Any, Any>()
                            val votersMap = buildMap<String, Int> {
                                rawVoters.forEach { (k, v) ->
                                    val uid = k as? String
                                    val optId = (v as? Number)?.toInt()
                                    if (!uid.isNullOrBlank() && optId != null) {
                                        put(uid, optId)
                                    }
                                }
                            }

                            FeedPollPost(
                                id = doc.id,
                                creatorId = doc.getString("creatorId").orEmpty(),
                                creatorUsername = doc.getString("creatorUsername").orEmpty().ifBlank { "Creator" },
                                creatorAvatarUrl = doc.getString("creatorAvatarUrl").orEmpty(),
                                question = doc.getString("question").orEmpty(),
                                options = parsedOptions,
                                voters = votersMap,
                                isQuiz = doc.getBoolean("isQuiz") ?: false,
                                correctOptionId = (doc.getLong("correctOptionId") ?: -1L).toInt(),
                                quizExplanation = doc.getString("quizExplanation").orEmpty(),
                                totalVotes = (doc.getLong("totalVotes") ?: votersMap.size.toLong()).toInt(),
                                createdAt = doc.getLong("createdAt") ?: 0L,
                                expiresAt = doc.getLong("expiresAt") ?: 0L
                            )
                        } catch (e: Exception) {
                            Log.w("FeedPollRepo", "Skip malformed poll ${doc.id}: ${e.message}")
                            null
                        }
                    }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun createFeedPoll(
        creatorId: String,
        creatorUsername: String,
        creatorAvatarUrl: String,
        question: String,
        optionTexts: List<String>,
        isQuiz: Boolean,
        correctOptionId: Int,
        quizExplanation: String,
        durationHours: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            require(creatorId.isNotBlank()) { "Please sign in to create a poll" }
            val cleanQuestion = question.trim()
            val cleanOptions = optionTexts.map { it.trim() }.filter { it.isNotEmpty() }
            require(cleanQuestion.isNotEmpty()) { "Question cannot be empty" }
            require(cleanOptions.size >= 2) { "Please add at least 2 options" }

            val now = System.currentTimeMillis()
            val pollId = "fpoll_${UUID.randomUUID().toString().take(10)}"
            val expiresAt = now + durationHours.coerceIn(1, 168) * 60L * 60L * 1000L

            val payload = hashMapOf(
                "creatorId" to creatorId,
                "creatorUsername" to creatorUsername.ifBlank { "Creator" },
                "creatorAvatarUrl" to creatorAvatarUrl,
                "question" to cleanQuestion,
                "options" to cleanOptions.mapIndexed { idx, txt ->
                    mapOf("id" to idx, "text" to txt, "votesCount" to 0)
                },
                "voters" to emptyMap<String, Int>(),
                "isQuiz" to isQuiz,
                "correctOptionId" to if (isQuiz) correctOptionId.coerceIn(0, cleanOptions.lastIndex) else -1,
                "quizExplanation" to if (isQuiz) quizExplanation.trim() else "",
                "totalVotes" to 0,
                "createdAt" to now,
                "expiresAt" to expiresAt
            )

            RepositoryManager.firestore.collection(COLLECTION_FEED_POLLS)
                .document(pollId)
                .set(payload)
                .await()
            Result.success(pollId)
        } catch (e: Exception) {
            Log.e("FeedPollRepo", "Failed to create feed poll", e)
            Result.failure(e)
        }
    }

    suspend fun voteOnPoll(
        pollId: String,
        userId: String,
        optionId: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require(userId.isNotBlank()) { "Please sign in to vote" }
            val docRef = RepositoryManager.firestore.collection(COLLECTION_FEED_POLLS).document(pollId)
            RepositoryManager.firestore.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (!snap.exists()) return@runTransaction

                val expiresAt = snap.getLong("expiresAt") ?: 0L
                if (expiresAt > 0L && System.currentTimeMillis() > expiresAt) {
                    throw IllegalStateException("This poll has ended")
                }

                val isQuiz = snap.getBoolean("isQuiz") ?: false
                val rawVoters = (snap.get("voters") as? Map<*, *>) ?: emptyMap<Any, Any>()
                val voters = mutableMapOf<String, Int>()
                rawVoters.forEach { (k, v) ->
                    val uid = k as? String
                    val opt = (v as? Number)?.toInt()
                    if (!uid.isNullOrBlank() && opt != null) voters[uid] = opt
                }

                val previousVote = voters[userId]
                if (isQuiz && previousVote != null) {
                    // Quiz answers are locked once submitted
                    return@runTransaction
                }
                if (previousVote == optionId) return@runTransaction

                voters[userId] = optionId

                val rawOptions = (snap.get("options") as? List<*>) ?: emptyList<Any>()
                val updatedOptions = rawOptions.mapIndexedNotNull { idx, item ->
                    val map = item as? Map<*, *> ?: return@mapIndexedNotNull null
                    val id = (map["id"] as? Number)?.toInt() ?: idx
                    val text = (map["text"] as? String).orEmpty()
                    val count = voters.values.count { it == id }
                    mapOf("id" to id, "text" to text, "votesCount" to count)
                }

                tx.update(
                    docRef,
                    mapOf(
                        "voters" to voters,
                        "options" to updatedOptions,
                        "totalVotes" to voters.size
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FeedPollRepo", "Failed to vote on poll $pollId", e)
            Result.failure(e)
        }
    }

    suspend fun deletePoll(pollId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            RepositoryManager.firestore.collection(COLLECTION_FEED_POLLS)
                .document(pollId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
