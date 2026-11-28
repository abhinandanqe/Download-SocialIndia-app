package com.example.security

import com.example.model.RepositoryManager
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SuspensionInfo(
    val accountStatus: String = "ACTIVE",
    val suspensionReason: String = "Community Guidelines Violation",
    val suspensionDate: Long = System.currentTimeMillis(),
    val suspendedUntil: Long = 0L,
    val restrictionType: String = "NONE",
    val isShadowbanned: Boolean = false,
    val strikesCount: Int = 0,
    val kycVideoVerified: Boolean = false,
    val reviewStatus: String = "NONE", // "NONE", "UNDER_REVIEW", "APPROVED", "REJECTED"
    val reviewRequestedAt: Long = 0L,
    val reviewReason: String = "",
    val caseId: String = ""
)

object AccountSuspensionManager {
    private var listenerRegistration: ListenerRegistration? = null
    private var currentObservedUid: String = ""

    private val _suspensionInfo = MutableStateFlow(SuspensionInfo())
    val suspensionInfo: StateFlow<SuspensionInfo> = _suspensionInfo.asStateFlow()

    private val _isSuspended = MutableStateFlow(false)
    val isSuspended: StateFlow<Boolean> = _isSuspended.asStateFlow()

    /**
     * Attaches a real-time Firestore listener to the active Firebase user document.
     * When account is switched, removes previous listener and attaches to the new UID.
     */
    fun observeUserAccountStatus(uid: String) {
        if (uid.isEmpty()) {
            listenerRegistration?.remove()
            listenerRegistration = null
            currentObservedUid = ""
            _isSuspended.value = false
            _suspensionInfo.value = SuspensionInfo()
            return
        }

        if (currentObservedUid == uid && listenerRegistration != null) {
            return // Already observing this UID
        }

        listenerRegistration?.remove()
        currentObservedUid = uid

        try {
            listenerRegistration = RepositoryManager.firestore.collection("users")
                .document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        return@addSnapshotListener
                    }
                    try {
                        fun str(key: String, default: String = ""): String =
                            (snapshot.get(key) as? String) ?: snapshot.get(key)?.toString() ?: default
                        fun bool(key: String, default: Boolean = false): Boolean = when (val v = snapshot.get(key)) {
                            is Boolean -> v
                            is String -> v.equals("true", ignoreCase = true)
                            is Number -> v.toInt() != 0
                            else -> default
                        }
                        fun longVal(key: String, default: Long = 0L): Long = when (val v = snapshot.get(key)) {
                            is Number -> v.toLong()
                            is com.google.firebase.Timestamp -> v.toDate().time
                            is String -> v.toLongOrNull() ?: default
                            else -> default
                        }

                        val rawStatus = str("accountStatus", "ACTIVE").ifEmpty { "ACTIVE" }
                        val isBanned = bool("isBanned")
                        val suspendedUntil = longVal("suspendedUntil")
                        val restrictionType = str("restrictionType", "NONE").ifEmpty { "NONE" }
                        val isShadowbanned = bool("isShadowbanned")
                        val strikesCount = longVal("strikesCount").toInt()
                        val kycVideoVerified = bool("kycVideoVerified")

                        val now = System.currentTimeMillis()
                        val isTempExpired = suspendedUntil > 0L && now > suspendedUntil && !isBanned && !rawStatus.equals("BANNED", ignoreCase = true)
                        if (isTempExpired && (rawStatus.startsWith("RESTRICTED", ignoreCase = true) || rawStatus.equals("SUSPENDED", ignoreCase = true))) {
                            RepositoryManager.firestore.collection("users").document(uid).update(
                                mapOf(
                                    "accountStatus" to "ACTIVE",
                                    "restrictionType" to "NONE",
                                    "suspendedUntil" to 0L
                                )
                            )
                        }

                        val isStatusSuspended = !isTempExpired && (
                            rawStatus.equals("SUSPENDED", ignoreCase = true) ||
                            rawStatus.equals("BANNED", ignoreCase = true) ||
                            rawStatus.startsWith("RESTRICTED", ignoreCase = true) ||
                            isBanned
                        )

                        val reason = str("suspensionReason").takeIf { it.isNotBlank() }
                            ?: "Your account may have violated Social India's Community Guidelines on authenticity, safety, or respectful community behavior."

                        val date = longVal("suspensionDate", System.currentTimeMillis())
                        val review = str("reviewStatus", "NONE").ifEmpty { "NONE" }
                        val requestedAt = longVal("reviewRequestedAt")
                        val reviewReason = str("reviewReason")
                        val caseRef = "SI-CASE-" + uid.takeLast(6).uppercase()

                        _suspensionInfo.value = SuspensionInfo(
                            accountStatus = if (isStatusSuspended) rawStatus.ifBlank { "SUSPENDED" } else "ACTIVE",
                            suspensionReason = reason,
                            suspensionDate = date,
                            suspendedUntil = suspendedUntil,
                            restrictionType = restrictionType,
                            isShadowbanned = isShadowbanned,
                            strikesCount = strikesCount,
                            kycVideoVerified = kycVideoVerified,
                            reviewStatus = review,
                            reviewRequestedAt = requestedAt,
                            reviewReason = reviewReason,
                            caseId = caseRef
                        )

                        _isSuspended.value = isStatusSuspended
                    } catch (_: Exception) {}
                }
        } catch (_: Exception) {
            _isSuspended.value = false
        }
    }

    fun submitAppeal(
        uid: String,
        reason: String,
        contactEmail: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (uid.isEmpty()) {
            onError("User ID is missing.")
            return
        }

        val updateData = mapOf(
            "reviewStatus" to "UNDER_REVIEW",
            "reviewRequestedAt" to System.currentTimeMillis(),
            "reviewReason" to reason.trim(),
            "appealEmail" to contactEmail.trim()
        )

        val ticketData = mapOf(
            "userId" to uid,
            "type" to "SUSPENSION_APPEAL",
            "reason" to reason.trim(),
            "contactEmail" to contactEmail.trim(),
            "status" to "PENDING",
            "createdAt" to System.currentTimeMillis()
        )

        RepositoryManager.firestore.collection("users").document(uid).update(updateData)
            .addOnSuccessListener {
                // Also create support ticket for review tracking
                RepositoryManager.firestore.collection("support_tickets").add(ticketData)
                _suspensionInfo.value = _suspensionInfo.value.copy(
                    reviewStatus = "UNDER_REVIEW",
                    reviewRequestedAt = System.currentTimeMillis(),
                    reviewReason = reason
                )
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Failed to submit appeal. Please check your network connection.")
            }
    }

    fun reset() {
        listenerRegistration?.remove()
        listenerRegistration = null
        currentObservedUid = ""
        _isSuspended.value = false
        _suspensionInfo.value = SuspensionInfo()
    }
}
