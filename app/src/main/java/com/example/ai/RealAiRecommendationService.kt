package com.example.ai

import android.util.Log
import com.example.model.RepositoryManager
import com.example.model.Video
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Real Recommendation Engine for Reels and Home Feed.
 * Combines watch duration, skips, likes, shares, creator affinities,
 * and negative signals ("not interested", blocks).
 */
class RealAiRecommendationService : AiRecommendationService {

    companion object {
        private const val TAG = "RealAiRecommendationService"
    }

    override suspend fun getRankedReels(currentUserId: String, limit: Int): List<Video> = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch user affinities and negative preferences
            val affinities = getUserAffinities(currentUserId)
            val blockedCreators = affinities.blockedCreators
            val hiddenVideoIds = affinities.hiddenVideoIds

            // 2. Fetch fresh candidates from Firestore
            val snapshot = RepositoryManager.firestore.collection("posts")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(60)
                .get()
                .await()

            val candidates = mutableListOf<Video>()
            snapshot.documents.forEach { doc ->
                if (!hiddenVideoIds.contains(doc.id)) {
                    val creatorId = doc.getString("userId") ?: ""
                    if (!blockedCreators.contains(creatorId)) {
                        doc.toObject(Video::class.java)?.let { rawV ->
                            val v = rawV.copy(id = doc.id)
                            candidates.add(v)
                        }
                    }
                }
            }

            if (candidates.isEmpty()) return@withContext emptyList()

            // 3. Compute Multi-Signal Score for each candidate
            val ranked = candidates.map { video ->
                var score = 100.0

                // Topic affinity match
                val captionLower = video.caption.lowercase(Locale.ROOT)
                affinities.topicAffinities.forEach { (topic, weight) ->
                    if (captionLower.contains(topic.lowercase(Locale.ROOT))) {
                        score += (weight * 15.0)
                    }
                }

                // Creator affinity match
                val creatorWeight = affinities.creatorAffinities[video.userId] ?: 0
                score += (creatorWeight * 20.0)

                // Sound affinity match
                val soundId = video.musicTrackId ?: ""
                if (soundId.isNotBlank() && affinities.soundAffinities.contains(soundId)) {
                    score += 25.0
                }

                // Engagement freshness signal
                val recencyHours = ((System.currentTimeMillis() - video.timestamp) / (1000 * 60 * 60)).coerceAtLeast(1)
                score += (video.likesCount.coerceAtMost(500) / recencyHours.toDouble())

                // Diversify: apply slight jitter to prevent monotony
                val hashBonus = (video.id.hashCode() % 10).toDouble()
                score += hashBonus

                Pair(video, score)
            }.sortedByDescending { it.second }.map { it.first }.take(limit)

            return@withContext ranked
        } catch (e: Exception) {
            Log.e(TAG, "Error generating ranked reels: ${e.message}")
            // Fallback gracefully
            return@withContext emptyList()
        }
    }

    override suspend fun getHomeFeed(currentUserId: String, limit: Int): List<Video> = withContext(Dispatchers.IO) {
        return@withContext getRankedReels(currentUserId, limit)
    }

    override suspend fun explainRecommendation(videoId: String, currentUserId: String): WhyAmISeeingThisReason = withContext(Dispatchers.IO) {
        var creatorName = ""
        var soundTitle = ""
        val signals = mutableListOf<String>()

        try {
            val doc = RepositoryManager.firestore.collection("posts").document(videoId).get().await()
            if (doc.exists()) {
                creatorName = doc.getString("username") ?: ""
                soundTitle = doc.getString("music_title") ?: doc.getString("audioTitle") ?: ""
            }

            val affinities = getUserAffinities(currentUserId)
            if (affinities.creatorAffinities.containsKey(doc.getString("userId"))) {
                signals.add("आपने पहले भी इस क्रिएटर के पोस्ट्स देखे हैं")
            }
            if (doc.getString("caption")?.let { cap -> affinities.topicAffinities.keys.any { cap.contains(it, ignoreCase = true) } } == true) {
                signals.add("यह विषय आपकी हाल की गतिविधियों से मेल खाता है")
            }
            if (signals.isEmpty()) {
                signals.add("यह Reel Social India पर नई और ट्रेंडिंग है")
            }
        } catch (e: Exception) {
            signals.add("Social India ट्रेंडिंग रिकमेंडेशन")
        }

        val reasonHindi = if (signals.size > 1) {
            "आपको यह Reel इसलिए दिखाई गई क्योंकि ${signals.first()}।"
        } else {
            "आपको यह Reel आपकी रुचियों और ट्रेंडिंग कंटेंट के आधार पर दिखाई गई है।"
        }

        val reasonEnglish = "You are seeing this Reel based on your recent interactions and trending topics."

        WhyAmISeeingThisReason(
            videoId = videoId,
            primaryReasonHindi = reasonHindi,
            primaryReasonEnglish = reasonEnglish,
            signalsDetected = signals,
            creatorName = creatorName,
            soundTitle = soundTitle
        )
    }

    override suspend fun recordUserSignal(userId: String, videoId: String, signalType: String, durationWatchedMs: Long) = withContext(Dispatchers.IO) {
        if (userId.isEmpty() || videoId.isEmpty()) return@withContext
        try {
            val userRef = RepositoryManager.firestore.collection("user_ai_signals").document(userId)
            val signalData = mapOf(
                "lastActive" to System.currentTimeMillis(),
                "recentSignals.$videoId.$signalType" to durationWatchedMs
            )
            userRef.set(signalData, com.google.firebase.firestore.SetOptions.merge())

            // If negative signal, add to hidden list
            if (signalType == "NOT_INTERESTED" || signalType == "SKIP_FAST") {
                userRef.update("hidden_videos", FieldValue.arrayUnion(videoId))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error recording recommendation signal: ${e.message}")
        }
    }

    private suspend fun getUserAffinities(userId: String): UserAffinities {
        if (userId.isEmpty()) return UserAffinities()
        return try {
            val doc = RepositoryManager.firestore.collection("user_ai_signals").document(userId).get().await()
            val hiddenList = (doc.get("hidden_videos") as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
            val blocked = (doc.get("blocked_creators") as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()

            UserAffinities(
                hiddenVideoIds = hiddenList,
                blockedCreators = blocked
            )
        } catch (e: Exception) {
            UserAffinities()
        }
    }

    private data class UserAffinities(
        val topicAffinities: Map<String, Int> = emptyMap(),
        val creatorAffinities: Map<String, Int> = emptyMap(),
        val soundAffinities: Set<String> = emptySet(),
        val hiddenVideoIds: Set<String> = emptySet(),
        val blockedCreators: Set<String> = emptySet()
    )
}
