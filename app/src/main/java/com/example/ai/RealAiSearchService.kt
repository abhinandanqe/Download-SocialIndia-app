package com.example.ai

import android.util.Log
import com.example.model.AudioSound
import com.example.model.RepositoryManager
import com.example.model.User
import com.example.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/**
 * Real AI Search Implementation combining NLP query intent parsing,
 * Firestore indexed queries, semantic ranking, and grounded AI answers.
 */
class RealAiSearchService(
    private val aiProvider: AIProvider = NvidiaAIProvider()
) : AiSearchService {

    companion object {
        private const val TAG = "RealAiSearchService"
    }

    override suspend fun parseQueryIntent(query: String): AISearchIntent = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext AISearchIntent(query)

        // Fast rule-based heuristics as primary fallback
        val lower = trimmed.lowercase(Locale.ROOT)
        var intentType = SearchIntentType.GENERAL
        var targetContentType: String? = null
        val keywords = mutableListOf<String>()
        val hashtags = mutableListOf<String>()

        trimmed.split(Regex("\\s+")).forEach { word ->
            if (word.startsWith("#") && word.length > 1) {
                hashtags.add(word)
            } else if (word.isNotBlank()) {
                keywords.add(word)
            }
        }

        if (lower.contains("reel") || lower.contains("video") || lower.contains("clip")) {
            intentType = SearchIntentType.FIND_REELS
            targetContentType = "video"
        } else if (lower.contains("song") || lower.contains("music") || lower.contains("audio") || lower.contains("sound")) {
            intentType = SearchIntentType.FIND_MUSIC
            targetContentType = "audio"
        } else if (lower.startsWith("@") || lower.contains("user") || lower.contains("profile") || lower.contains("creator") || lower.contains("people")) {
            intentType = SearchIntentType.FIND_PEOPLE
            targetContentType = "user"
        } else if (lower.startsWith("how to") || lower.startsWith("what is") || lower.contains("kya hai") || lower.contains("kaise")) {
            intentType = SearchIntentType.HELP_OR_QUESTION
        } else {
            intentType = SearchIntentType.TOPICAL_EXPLORE
        }

        val onlyFollowed = lower.contains("i follow") || lower.contains("following") || lower.contains("mere dost")

        // Try AI understanding via prompt if online
        try {
            val systemPrompt = "Analyze search query for a social media app. Output JSON only: " +
                    "{\"intent\":\"FIND_REELS|FIND_MUSIC|FIND_PEOPLE|TOPICAL_EXPLORE|GENERAL\"," +
                    "\"keywords\":[\"word\"],\"hashtags\":[\"#tag\"],\"targetContentType\":\"video|audio|user|all\"}"
            val prompt = "Query: \"$trimmed\""
            val raw = aiProvider.chat(
                messages = listOf("system" to systemPrompt, "user" to prompt),
                temperature = 0.2,
                maxTokens = 200
            )
            val jsonStart = raw.indexOf('{')
            val jsonEnd = raw.lastIndexOf('}')
            if (jsonStart != -1 && jsonEnd > jsonStart) {
                val json = JSONObject(raw.substring(jsonStart, jsonEnd + 1))
                val aiIntent = when (json.optString("intent", "")) {
                    "FIND_REELS" -> SearchIntentType.FIND_REELS
                    "FIND_MUSIC" -> SearchIntentType.FIND_MUSIC
                    "FIND_PEOPLE" -> SearchIntentType.FIND_PEOPLE
                    "TOPICAL_EXPLORE" -> SearchIntentType.TOPICAL_EXPLORE
                    else -> intentType
                }
                val aiKw = mutableListOf<String>()
                json.optJSONArray("keywords")?.let { arr ->
                    for (i in 0 until arr.length()) aiKw.add(arr.getString(i))
                }
                val aiHt = mutableListOf<String>()
                json.optJSONArray("hashtags")?.let { arr ->
                    for (i in 0 until arr.length()) aiHt.add(arr.getString(i))
                }
                val aiType = json.optString("targetContentType", targetContentType ?: "all")

                return@withContext AISearchIntent(
                    rawQuery = trimmed,
                    intentType = aiIntent,
                    extractedKeywords = if (aiKw.isNotEmpty()) aiKw else keywords,
                    extractedHashtags = if (aiHt.isNotEmpty()) aiHt else hashtags,
                    targetContentType = if (aiType.isNotEmpty() && aiType != "all") aiType else targetContentType,
                    isOnlyFollowedAccounts = onlyFollowed
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "AI intent parsing fallback to rules: ${e.message}")
        }

        AISearchIntent(
            rawQuery = trimmed,
            intentType = intentType,
            extractedKeywords = keywords,
            extractedHashtags = hashtags,
            targetContentType = targetContentType,
            isOnlyFollowedAccounts = onlyFollowed
        )
    }

    override suspend fun searchAndRank(query: String, currentUserId: String): AISearchRankedResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext AISearchRankedResult(query = query)

        val intent = parseQueryIntent(trimmed)

        // 1. Fetch Candidates from Firestore using indexed queries
        val matchedUsers = mutableListOf<User>()
        val matchedVideos = mutableListOf<Video>()
        val matchedSounds = mutableListOf<AudioSound>()
        val matchedHashtags = mutableListOf<String>()

        try {
            // Search Users
            if (intent.targetContentType == null || intent.targetContentType == "user" || intent.targetContentType == "all") {
                val userClean = trimmed.removePrefix("@").lowercase(Locale.ROOT)
                val userSnap = RepositoryManager.firestore.collection("users")
                    .whereGreaterThanOrEqualTo("username_lowercase", userClean)
                    .whereLessThanOrEqualTo("username_lowercase", userClean + "\uf8ff")
                    .limit(20)
                    .get()
                    .await()
                userSnap.documents.forEach { doc ->
                    doc.toObject(User::class.java)?.let { u ->
                        val fixedUser = u.copy(id = doc.id)
                        matchedUsers.add(fixedUser)
                    }
                }

                if (matchedUsers.isEmpty()) {
                    // Fallback to social repo search
                    val fbUsers = RepositoryManager.socialRepository.searchUsers(userClean)
                    matchedUsers.addAll(fbUsers)
                }
            }

            // Search Videos / Reels
            if (intent.targetContentType == null || intent.targetContentType == "video" || intent.targetContentType == "all") {
                val postSnap = RepositoryManager.firestore.collection("posts")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(100)
                    .get()
                    .await()

                val searchTokens = (intent.extractedKeywords + trimmed.split(" "))
                    .map { it.lowercase(Locale.ROOT).trim() }
                    .filter { it.length > 1 }

                postSnap.documents.forEach { doc ->
                    val caption = (doc.getString("caption") ?: "").lowercase(Locale.ROOT)
                    val musicTitle = (doc.getString("music_title") ?: doc.getString("audioTitle") ?: "").lowercase(Locale.ROOT)
                    val creator = (doc.getString("username") ?: "").lowercase(Locale.ROOT)

                    var score = 0
                    for (token in searchTokens) {
                        if (caption.contains(token)) score += 3
                        if (musicTitle.contains(token)) score += 2
                        if (creator.contains(token)) score += 2
                    }

                    // Check hashtag match
                    for (ht in intent.extractedHashtags) {
                        if (caption.contains(ht.lowercase(Locale.ROOT))) score += 4
                    }

                    if (score > 0 || searchTokens.isEmpty()) {
                        val v = doc.toObject(Video::class.java)
                        if (v != null) {
                            val fixedV = v.copy(id = doc.id)
                            matchedVideos.add(fixedV)
                        }
                    }
                }
            }

            // Search Sounds
            if (intent.targetContentType == null || intent.targetContentType == "audio" || intent.targetContentType == "all") {
                val soundSnap = RepositoryManager.firestore.collection("audio_library")
                    .limit(50)
                    .get()
                    .await()
                val queryLower = trimmed.lowercase(Locale.ROOT)
                soundSnap.documents.forEach { doc ->
                    val title = (doc.getString("title") ?: "").lowercase(Locale.ROOT)
                    val artist = (doc.getString("artist") ?: "").lowercase(Locale.ROOT)
                    if (title.contains(queryLower) || artist.contains(queryLower)) {
                        doc.toObject(AudioSound::class.java)?.let { s ->
                            val fixedS = s.copy(id = doc.id)
                            matchedSounds.add(fixedS)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Firestore search candidate queries: ${e.message}")
        }

        // Rank Videos by views count (jyada bheed), engagement, relevance, and freshness
        val rankedVideos = matchedVideos.sortedWith(
            compareByDescending<Video> { v ->
                var weight = 0L
                val cap = v.caption.lowercase(Locale.ROOT)
                if (intent.extractedKeywords.any { cap.contains(it.lowercase(Locale.ROOT)) }) weight += 5000L
                // Primary weight on views ("jyada bheed" gets top priority)
                weight += (v.viewsCount.toLong() * 10L)
                weight += (v.likesCount.coerceAtMost(5000).toLong() * 4L)
                weight += (v.sharesCount.coerceAtMost(2000).toLong() * 3L)
                weight
            }.thenByDescending { it.viewsCount }
             .thenByDescending { it.timestamp }
        ).take(40)

        // Generate grounded AI answer if applicable
        val aiAnswer = generateAnswerIfApplicable(trimmed, rankedVideos)

        AISearchRankedResult(
            query = trimmed,
            aiAnswer = aiAnswer,
            users = matchedUsers.take(15),
            videos = rankedVideos,
            sounds = matchedSounds.take(15),
            hashtags = intent.extractedHashtags,
            explanationReason = "Ranked using query intent: ${intent.intentType.name}"
        )
    }

    override suspend fun getAutocompleteSuggestions(partialQuery: String): List<AISearchSuggestion> = withContext(Dispatchers.IO) {
        val query = partialQuery.trim()
        if (query.isEmpty()) return@withContext emptyList()

        val list = mutableListOf<AISearchSuggestion>()
        val clean = query.removePrefix("@").removePrefix("#").lowercase(Locale.ROOT)

        try {
            // Suggest users
            val users = RepositoryManager.socialRepository.searchUsers(clean)
            users.take(3).forEach { u ->
                list.add(
                    AISearchSuggestion(
                        title = "@${u.username}",
                        subtitle = u.displayName.ifBlank { "User" },
                        type = SuggestionType.USER,
                        itemId = u.id
                    )
                )
            }

            // Suggest hashtag
            if (query.startsWith("#") || query.length >= 2) {
                list.add(
                    AISearchSuggestion(
                        title = if (clean.startsWith("#")) clean else "#$clean",
                        subtitle = "Trending hashtag",
                        type = SuggestionType.HASHTAG
                    )
                )
            }

            // Suggest topic / query completion
            list.add(
                AISearchSuggestion(
                    title = query,
                    subtitle = "Explore on Social India",
                    type = SuggestionType.TOPIC
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Autocomplete error: ${e.message}")
        }

        list
    }

    override suspend fun generateAnswerIfApplicable(query: String, verifiedItems: List<Video>): String? = withContext(Dispatchers.IO) {
        val qLower = query.lowercase(Locale.ROOT)
        val isQuestionOrRequest = qLower.contains("dikhAO") || qLower.contains("batao") ||
                qLower.contains("what") || qLower.contains("how") || qLower.contains("chahiye") ||
                qLower.contains("comedy") || qLower.contains("dance") || qLower.contains("funny")

        if (!isQuestionOrRequest) return@withContext null
        if (verifiedItems.isEmpty()) {
            return@withContext "Social India पर \"$query\" से संबंधित कोई सत्यापित पोस्ट नहीं मिली।"
        }

        try {
            val topCaptions = verifiedItems.take(5).joinToString("; ") { v ->
                "@${v.user.username}: \"${v.caption.take(60)}\""
            }
            val prompt = "User asked: \"$query\". Based on real verified Social India content: [$topCaptions], " +
                    "provide a concise 1-2 sentence response guiding the user to these reels. Do not hallucinate fake links or users."
            val res = aiProvider.chat(
                messages = listOf(
                    "system" to "You are Social India Search Assistant. Speak in clear Hindi/Hinglish. Never hallucinate fake content.",
                    "user" to prompt
                ),
                temperature = 0.3,
                maxTokens = 120
            )
            return@withContext res.trim()
        } catch (e: Exception) {
            Log.w(TAG, "Answer generation skipped: ${e.message}")
            return@withContext null
        }
    }
}
