package com.example.security

import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized input validation, sanitization, and per-action client-side rate limiting
 * to prevent spam bursts, duplicate counter mutations, and malformed payloads.
 */
object InputGuard {

    private const val MAX_COMMENT_LENGTH = 500
    private const val MAX_CAPTION_LENGTH = 2200
    private const val MAX_CHAT_MESSAGE_LENGTH = 2000
    private const val MAX_BIO_LENGTH = 250
    private const val MAX_USERNAME_LENGTH = 32
    private const val MAX_REPORT_LENGTH = 500

    private val CONTROL_CHARS_REGEX = Regex("[\\p{Cc}&&[^\\n\\t]]")
    private val ZERO_WIDTH_REGEX = Regex("[\\u200B-\\u200D\\uFEFF]")

    // Last action timestamp per key (e.g. "like:<videoId>")
    private val lastActionTimestamps = ConcurrentHashMap<String, Long>()

    // Active in-flight mutation keys to prevent concurrent duplicate network calls
    private val inFlightKeys = ConcurrentHashMap.newKeySet<String>()

    /**
     * Checks whether an action is allowed given a minimum cooldown window.
     * Returns true and records the timestamp if allowed, or false if throttled.
     */
    fun allowAction(actionKey: String, cooldownMs: Long = 450L): Boolean {
        val now = System.currentTimeMillis()
        val previous = lastActionTimestamps[actionKey]
        if (previous != null && (now - previous) < cooldownMs) {
            return false
        }
        lastActionTimestamps[actionKey] = now
        // Prevent unbounded map growth during long sessions
        if (lastActionTimestamps.size > 512) {
            val cutoff = now - 60_000L
            lastActionTimestamps.entries.removeIf { it.value < cutoff }
        }
        return true
    }

    /**
     * Acquires an in-flight lock for a specific mutation key (e.g. "like:post123").
     * Returns false if an identical mutation is already executing.
     */
    fun tryBeginMutation(mutationKey: String): Boolean = inFlightKeys.add(mutationKey)

    fun endMutation(mutationKey: String) {
        inFlightKeys.remove(mutationKey)
    }

    /**
     * Sanitizes general user text by stripping hidden control/zero-width characters,
     * collapsing excessive blank lines, trimming, and enforcing a maximum length.
     */
    fun sanitizeText(raw: String?, maxLength: Int): String {
        if (raw.isNullOrEmpty()) return ""
        val cleaned = raw
            .replace(CONTROL_CHARS_REGEX, "")
            .replace(ZERO_WIDTH_REGEX, "")
            .replace(Regex("\\n{4,}"), "\n\n\n")
            .trim()
        return if (cleaned.length > maxLength) cleaned.take(maxLength) else cleaned
    }

    fun sanitizeComment(raw: String?): String = sanitizeText(raw, MAX_COMMENT_LENGTH)

    fun sanitizeCaption(raw: String?): String = sanitizeText(raw, MAX_CAPTION_LENGTH)

    fun sanitizeChatMessage(raw: String?): String = sanitizeText(raw, MAX_CHAT_MESSAGE_LENGTH)

    fun sanitizeBio(raw: String?): String = sanitizeText(raw, MAX_BIO_LENGTH)

    fun sanitizeReportReason(raw: String?): String = sanitizeText(raw, MAX_REPORT_LENGTH)

    fun sanitizeUsername(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.trim()
            .replace(Regex("[^a-zA-Z0-9._]"), "")
            .take(MAX_USERNAME_LENGTH)
    }

    /**
     * Validates that an external URL uses a safe http/https scheme (blocks javascript:, file:, content: injection).
     */
    fun isSafeHttpUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim().lowercase()
        return trimmed.startsWith("https://") || trimmed.startsWith("http://")
    }
}
