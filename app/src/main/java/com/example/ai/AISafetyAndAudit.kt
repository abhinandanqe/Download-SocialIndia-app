package com.example.ai

import android.util.Log
import com.example.model.RepositoryManager
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Safety and confidence levels for AI moderation and safety screening.
 */
enum class SafetyConfidence {
    LOW,
    MEDIUM,
    HIGH
}

data class SafetyAuditRecord(
    val contentId: String = "",
    val userId: String = "",
    val modelId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val classification: String = "SAFE", // SAFE, SPAM, HARASSMENT, SCAM, INAPPROPRIATE
    val confidence: SafetyConfidence = SafetyConfidence.LOW,
    val actionTaken: String = "ALLOW", // ALLOW, REVIEW, HIDE
    val reviewState: String = "AUTO_CLEARED" // AUTO_CLEARED, PENDING_HUMAN_REVIEW, FLAGGED
)

object AISafetyManager {
    private const val TAG = "AISafetyManager"

    /**
     * Performs a fast local and pattern-based safety scan before sending to AI or posting.
     */
    fun evaluateTextSafety(text: String): SafetyAuditRecord {
        val lower = text.lowercase()
        val spamPatterns = listOf("free money", "win cash now", "click here to claim", "telegram me @", "whatsapp me at")
        val abusivePatterns = listOf("hate", "kill", "threat")

        return when {
            spamPatterns.any { lower.contains(it) } -> {
                SafetyAuditRecord(
                    classification = "SPAM",
                    confidence = SafetyConfidence.HIGH,
                    actionTaken = "REVIEW",
                    reviewState = "PENDING_HUMAN_REVIEW"
                )
            }
            abusivePatterns.any { lower.contains(it) } -> {
                SafetyAuditRecord(
                    classification = "HARASSMENT",
                    confidence = SafetyConfidence.MEDIUM,
                    actionTaken = "REVIEW",
                    reviewState = "PENDING_HUMAN_REVIEW"
                )
            }
            else -> {
                SafetyAuditRecord(
                    classification = "SAFE",
                    confidence = SafetyConfidence.HIGH,
                    actionTaken = "ALLOW",
                    reviewState = "AUTO_CLEARED"
                )
            }
        }
    }

    suspend fun logSafetyRecord(record: SafetyAuditRecord) = withContext(Dispatchers.IO) {
        try {
            val recordMap = hashMapOf(
                "contentId" to record.contentId,
                "userId" to record.userId,
                "modelId" to record.modelId,
                "timestamp" to record.timestamp,
                "classification" to record.classification,
                "confidence" to record.confidence.name,
                "actionTaken" to record.actionTaken,
                "reviewState" to record.reviewState
            )
            RepositoryManager.firestore.collection("ai_safety_audit")
                .add(recordMap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log safety record: ${e.message}")
        }
    }
}

object AIAuditLogger {
    private const val TAG = "AIAuditLogger"

    fun logEvent(event: String, featureId: String, details: Map<String, Any?> = emptyMap()) {
        Log.i(TAG, "[$event] Feature: $featureId | Data: $details")
        // Log to Firestore asynchronously for admin transparency
        try {
            val uid = RepositoryManager.getCurrentUserId()
            val auditData = hashMapOf(
                "event" to event,
                "featureId" to featureId,
                "userId" to uid,
                "timestamp" to System.currentTimeMillis(),
                "details" to details.toString()
            )
            RepositoryManager.firestore.collection("admin").document("ai_logs")
                .collection("events").add(auditData)
        } catch (e: Exception) {
            // non-fatal
        }
    }
}

object AIUsageManager {
    private const val USER_DAILY_LIMIT = 50
    private var cachedUserCount = 0
    private var lastCheckDay = 0

    suspend fun checkAndIncrementUsage(featureId: String): Boolean = withContext(Dispatchers.IO) {
        val uid = RepositoryManager.getCurrentUserId()
        if (uid.isEmpty()) return@withContext false

        try {
            val todayDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            if (todayDay != lastCheckDay) {
                cachedUserCount = 0
                lastCheckDay = todayDay
            }

            if (cachedUserCount >= USER_DAILY_LIMIT) {
                return@withContext false
            }

            cachedUserCount++

            // Update in Firestore
            val userUsageRef = RepositoryManager.firestore.collection("users").document(uid)
                .collection("aiUsage").document("today")
            
            userUsageRef.set(
                hashMapOf(
                    "count" to FieldValue.increment(1),
                    "lastFeature" to featureId,
                    "updatedAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            return@withContext true
        } catch (e: Exception) {
            // In case of network glitch, fail open gracefully within local limit
            return@withContext cachedUserCount < USER_DAILY_LIMIT
        }
    }
}

object AIPrivacyManager {
    suspend fun clearUserAIHistory(): Boolean = withContext(Dispatchers.IO) {
        val uid = RepositoryManager.getCurrentUserId()
        if (uid.isEmpty()) return@withContext false
        try {
            val docs = RepositoryManager.firestore.collection("ai_history")
                .whereEqualTo("userId", uid).get().await()
            for (doc in docs.documents) {
                doc.reference.delete()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
