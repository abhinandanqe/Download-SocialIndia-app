package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.User
import com.example.model.RepositoryManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AdminReportedItem(
    val id: String = "",
    val targetId: String = "",
    val targetType: String = "POST", // POST, REEL, COMMENT, STORY, DM
    val ownerId: String = "",
    val ownerUsername: String = "user",
    val reporterUsername: String = "community",
    val reason: String = "Inappropriate Content / Community Guideline Flag",
    val contentPreview: String = "",
    val mediaUrl: String = "",
    val reportsCount: Int = 1,
    val status: String = "PENDING",
    val timestamp: Long = System.currentTimeMillis()
)

class AdminViewModel : ViewModel() {
    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    private val _reportedItems = MutableStateFlow<List<AdminReportedItem>>(emptyList())
    val reportedItems: StateFlow<List<AdminReportedItem>> = _reportedItems

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessages = MutableSharedFlow<String>()
    val errorMessages = _errorMessages.asSharedFlow()
    private val firestore = FirebaseFirestore.getInstance()
    private var usersListener: ListenerRegistration? = null
    private var reportsListener: ListenerRegistration? = null
    private var flaggedPostsListener: ListenerRegistration? = null

    init {
        loadUsersRealtime()
        loadReportsAndFlaggedContentRealtime()
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.safeString(field: String, default: String = ""): String {
        return try {
            when (val raw = get(field)) {
                is String -> raw
                is Number -> raw.toString()
                is Boolean -> raw.toString()
                else -> default
            }
        } catch (_: Exception) {
            default
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.safeLong(field: String, default: Long = 0L): Long {
        return try {
            when (val raw = get(field)) {
                is Number -> raw.toLong()
                is com.google.firebase.Timestamp -> raw.toDate().time
                is java.util.Date -> raw.time
                is String -> raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong() ?: default
                else -> default
            }
        } catch (_: Exception) {
            default
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.safeDouble(field: String, default: Double = 0.0): Double {
        return try {
            when (val raw = get(field)) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull() ?: default
                else -> default
            }
        } catch (_: Exception) {
            default
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.safeBoolean(field: String, default: Boolean = false): Boolean {
        return try {
            when (val raw = get(field)) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                is String -> raw.equals("true", ignoreCase = true) || raw == "1"
                else -> default
            }
        } catch (_: Exception) {
            default
        }
    }

    private fun loadUsersRealtime() {
        _isLoading.value = true
        usersListener?.remove()
        usersListener = firestore.collection("users")
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                _isLoading.value = false
                if (error != null) {
                    Log.e("AdminViewModel", "Error loading users in realtime", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            val id = doc.id
                            val username = doc.safeString("username").ifBlank { "User" }
                            val email = doc.safeString("email")
                            val avatarUrl = doc.safeString("profilePicUrl").ifBlank { doc.safeString("avatar_url") }
                            val isAdmin = doc.safeBoolean("isAdmin", false)
                            val isVerified = doc.safeBoolean("isVerified", false)
                            val verificationRequested = doc.safeBoolean("verificationRequested", false)
                            val isBanned = doc.safeBoolean("isBanned", false)
                            val verificationBadgeUrl = doc.safeString("verificationBadgeUrl")
                            val isMonetized = doc.safeBoolean("isMonetized", false)
                            val monetizationRequested = doc.safeBoolean("monetizationRequested", false)
                            val followerCount = doc.safeLong("followerCount", 0L).toInt()
                            val followingCount = doc.safeLong("followingCount", 0L).toInt()
                            val accountStatus = doc.safeString("accountStatus").ifBlank { if (isBanned) "BANNED" else "ACTIVE" }
                            val suspensionReason = doc.safeString("suspensionReason")
                            val suspensionDate = doc.safeLong("suspensionDate", 0L)
                            val suspendedUntil = doc.safeLong("suspendedUntil", 0L)
                            val restrictionType = doc.safeString("restrictionType").ifBlank { "NONE" }
                            val isShadowbanned = doc.safeBoolean("isShadowbanned", false)
                            val strikesCount = doc.safeLong("strikesCount", 0L).toInt()
                            val kycVideoVerified = doc.safeBoolean("kycVideoVerified", false)
                            val reviewStatus = doc.safeString("reviewStatus").ifBlank { "NONE" }
                            val reviewReason = doc.safeString("reviewReason")
                            val walletBalance = doc.safeDouble("walletBalance", 0.0)

                            User(
                                id = id,
                                username = username,
                                email = email,
                                avatarUrl = avatarUrl,
                                isAdmin = isAdmin,
                                isVerified = isVerified,
                                verificationRequested = verificationRequested,
                                isBanned = isBanned,
                                verificationBadgeUrl = verificationBadgeUrl,
                                isMonetized = isMonetized,
                                monetizationRequested = monetizationRequested,
                                followerCount = followerCount,
                                followingCount = followingCount,
                                accountStatus = accountStatus,
                                suspensionReason = suspensionReason,
                                suspensionDate = suspensionDate,
                                suspendedUntil = suspendedUntil,
                                restrictionType = restrictionType,
                                isShadowbanned = isShadowbanned,
                                strikesCount = strikesCount,
                                kycVideoVerified = kycVideoVerified,
                                reviewStatus = reviewStatus,
                                reviewReason = reviewReason,
                                walletBalance = walletBalance
                            )
                        } catch (e: Exception) {
                            Log.w("AdminViewModel", "Skipping malformed user doc ${doc.id}", e)
                            null
                        }
                    }
                    _users.value = list
                }
            }
    }

    private fun loadReportsAndFlaggedContentRealtime() {
        reportsListener?.remove()
        reportsListener = firestore.collection("reports")
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("AdminViewModel", "Error listening to reports", error)
                    return@addSnapshotListener
                }
                val explicitReports = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val status = doc.safeString("status", "PENDING").ifBlank { "PENDING" }
                        if (status == "RESOLVED") return@mapNotNull null
                        AdminReportedItem(
                            id = doc.id,
                            targetId = doc.safeString("targetId").ifBlank { doc.safeString("postId").ifBlank { doc.id } },
                            targetType = doc.safeString("targetType", "POST").ifBlank { "POST" }.uppercase(),
                            ownerId = doc.safeString("ownerId").ifBlank { doc.safeString("reportedUserId") },
                            ownerUsername = doc.safeString("ownerUsername", "creator").ifBlank { "creator" },
                            reporterUsername = doc.safeString("reporterUsername", "user").ifBlank { "user" },
                            reason = doc.safeString("reason", "Policy Violation").ifBlank { "Policy Violation" },
                            contentPreview = doc.safeString("contentPreview").ifBlank { doc.safeString("caption") },
                            mediaUrl = doc.safeString("mediaUrl"),
                            reportsCount = doc.safeLong("reportsCount", 1L).toInt().coerceAtLeast(1),
                            status = status,
                            timestamp = doc.safeLong("timestamp", System.currentTimeMillis())
                        )
                    } catch (e: Exception) {
                        Log.w("AdminViewModel", "Skipping malformed report doc ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                val currentFlagged = _reportedItems.value.filter { it.id.startsWith("post_flag_") }
                _reportedItems.value = (explicitReports + currentFlagged).distinctBy { it.targetId.ifBlank { it.id } }.sortedByDescending { it.timestamp }
            }

        flaggedPostsListener?.remove()
        flaggedPostsListener = firestore.collection("posts")
            .whereGreaterThan("reportsCount", 0)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("AdminViewModel", "Error listening to flagged posts", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val flaggedPosts = snapshot.documents.mapNotNull { doc ->
                        try {
                            val reportsCount = doc.safeLong("reportsCount", 0L).toInt()
                            if (reportsCount <= 0) return@mapNotNull null
                            AdminReportedItem(
                                id = "post_flag_${doc.id}",
                                targetId = doc.id,
                                targetType = doc.safeString("mediaType", "POST").ifBlank { "POST" }.uppercase(),
                                ownerId = doc.safeString("userId"),
                                ownerUsername = doc.safeString("username", "creator").ifBlank { "creator" },
                                reporterUsername = "$reportsCount users",
                                reason = "Flagged by Community ($reportsCount reports)",
                                contentPreview = doc.safeString("caption", "Media Post").ifBlank { "Media Post" },
                                mediaUrl = doc.safeString("postUrl").ifBlank { doc.safeString("fileUrl") },
                                reportsCount = reportsCount,
                                status = "PENDING",
                                timestamp = doc.safeLong("timestamp", System.currentTimeMillis())
                            )
                        } catch (e: Exception) {
                            Log.w("AdminViewModel", "Skipping malformed flagged post doc ${doc.id}", e)
                            null
                        }
                    }
                    val currentExplicit = _reportedItems.value.filter { !it.id.startsWith("post_flag_") }
                    _reportedItems.value = (currentExplicit + flaggedPosts).distinctBy { it.targetId.ifBlank { it.id } }.sortedByDescending { it.timestamp }
                }
            }
    }

    fun setAccountModeration(
        userId: String,
        actionType: String, // "SUSPEND", "RESTRICT_24H", "RESTRICT_7D", "RESTRICT_30D", "SHADOWBAN", "UNSHADOWBAN", "BAN_PERMANENT", "RESTORE_ACTIVE"
        reason: String
    ) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val cleanReason = reason.trim().ifBlank { "Community Guidelines Violation" }
                val updates = mutableMapOf<String, Any>()

                when (actionType) {
                    "SUSPEND" -> {
                        updates["accountStatus"] = "SUSPENDED"
                        updates["restrictionType"] = "SUSPENDED"
                        updates["suspendedUntil"] = 0L
                        updates["suspensionDate"] = now
                        updates["suspensionReason"] = cleanReason
                        updates["isBanned"] = false
                    }
                    "RESTRICT_24H" -> {
                        updates["accountStatus"] = "RESTRICTED_24H"
                        updates["restrictionType"] = "RESTRICTED_24H"
                        updates["suspendedUntil"] = now + 24L * 3600L * 1000L
                        updates["suspensionDate"] = now
                        updates["suspensionReason"] = "$cleanReason (24h Restriction)"
                        updates["isBanned"] = false
                    }
                    "RESTRICT_7D" -> {
                        updates["accountStatus"] = "RESTRICTED_7D"
                        updates["restrictionType"] = "RESTRICTED_7D"
                        updates["suspendedUntil"] = now + 7L * 24L * 3600L * 1000L
                        updates["suspensionDate"] = now
                        updates["suspensionReason"] = "$cleanReason (7-Day Restriction)"
                        updates["isBanned"] = false
                    }
                    "RESTRICT_30D" -> {
                        updates["accountStatus"] = "RESTRICTED_30D"
                        updates["restrictionType"] = "RESTRICTED_30D"
                        updates["suspendedUntil"] = now + 30L * 24L * 3600L * 1000L
                        updates["suspensionDate"] = now
                        updates["suspensionReason"] = "$cleanReason (30-Day Restriction)"
                        updates["isBanned"] = false
                    }
                    "SHADOWBAN" -> {
                        updates["isShadowbanned"] = true
                        updates["suspensionReason"] = cleanReason
                    }
                    "UNSHADOWBAN" -> {
                        updates["isShadowbanned"] = false
                    }
                    "BAN_PERMANENT" -> {
                        updates["accountStatus"] = "BANNED"
                        updates["restrictionType"] = "BANNED"
                        updates["isBanned"] = true
                        updates["suspendedUntil"] = 0L
                        updates["suspensionDate"] = now
                        updates["suspensionReason"] = "$cleanReason (Permanent Ban)"
                    }
                    "RESTORE_ACTIVE" -> {
                        updates["accountStatus"] = "ACTIVE"
                        updates["restrictionType"] = "NONE"
                        updates["isBanned"] = false
                        updates["isShadowbanned"] = false
                        updates["suspendedUntil"] = 0L
                        updates["reviewStatus"] = "APPROVED"
                        updates["suspensionReason"] = ""
                    }
                }

                firestore.collection("users").document(userId).update(updates).await()

                // Write audit log
                firestore.collection("moderation_logs").add(
                    mapOf(
                        "userId" to userId,
                        "action" to actionType,
                        "reason" to cleanReason,
                        "adminId" to RepositoryManager.getCurrentUserId(),
                        "timestamp" to now
                    )
                )
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error applying moderation $actionType", e)
                _errorMessages.emit("Failed: ${e.message}")
            }
        }
    }

    fun issueStrike(userId: String, reason: String) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val userRef = firestore.collection("users").document(userId)
                val snap = userRef.get().await()
                val currentStrikes = snap.safeLong("strikesCount", 0L).toInt()
                val newStrikes = currentStrikes + 1
                val updates = mutableMapOf<String, Any>(
                    "strikesCount" to newStrikes,
                    "suspensionReason" to reason.ifBlank { "Community Guideline Strike #$newStrikes" }
                )
                if (newStrikes >= 3) {
                    updates["accountStatus"] = "SUSPENDED"
                    updates["restrictionType"] = "SUSPENDED_3_STRIKES"
                    updates["suspensionDate"] = now
                }
                userRef.update(updates).await()
                val strikePayload = mapOf(
                    "userId" to userId,
                    "recipientId" to userId,
                    "senderId" to "admin_safety",
                    "senderName" to "Social India Safety",
                    "type" to "STRIKE_WARNING",
                    "title" to "Community Guideline Strike ($newStrikes/3)",
                    "message" to reason.ifBlank { "Your content violated our Community Guidelines." },
                    "isRead" to false,
                    "timestamp" to now
                )
                firestore.collection("notifications").add(strikePayload)
                userRef.collection("notifications").add(strikePayload)
            } catch (e: Exception) {
                _errorMessages.emit("Strike error: ${e.message}")
            }
        }
    }

    fun warnUser(userId: String, warningMessage: String) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val warnPayload = mapOf(
                    "userId" to userId,
                    "recipientId" to userId,
                    "senderId" to "admin_safety",
                    "senderName" to "Social India Safety",
                    "type" to "ADMIN_WARNING",
                    "title" to "Official Safety Warning",
                    "message" to warningMessage.ifBlank { "Please adhere to Social India Community Guidelines." },
                    "isRead" to false,
                    "timestamp" to now
                )
                firestore.collection("notifications").add(warnPayload).await()
                firestore.collection("users").document(userId).collection("notifications").add(warnPayload)
            } catch (e: Exception) {
                _errorMessages.emit("Warning error: ${e.message}")
            }
        }
    }

    fun deleteReportedContent(item: AdminReportedItem, issueStrikeToo: Boolean = false) {
        viewModelScope.launch {
            try {
                if (item.targetId.isNotEmpty()) {
                    when (item.targetType.uppercase()) {
                        "COMMENT" -> firestore.collection("comments").document(item.targetId).delete()
                        "STORY" -> firestore.collection("stories").document(item.targetId).delete()
                        else -> firestore.collection("posts").document(item.targetId).delete()
                    }
                }
                if (!item.id.startsWith("post_flag_")) {
                    firestore.collection("reports").document(item.id).update("status", "RESOLVED")
                }
                if (issueStrikeToo && item.ownerId.isNotEmpty()) {
                    issueStrike(item.ownerId, "Removed reported ${item.targetType}: ${item.reason}")
                }
                _reportedItems.value = _reportedItems.value.filterNot { it.id == item.id }
            } catch (e: Exception) {
                _errorMessages.emit("Delete error: ${e.message}")
            }
        }
    }

    fun reviewVideoKyc(userId: String, approved: Boolean) {
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any>(
                    "kycVideoVerified" to approved,
                    "reviewStatus" to if (approved) "APPROVED" else "REJECTED"
                )
                if (approved) {
                    updates["accountStatus"] = "ACTIVE"
                    updates["restrictionType"] = "NONE"
                    updates["isBanned"] = false
                    updates["suspendedUntil"] = 0L
                }
                firestore.collection("users").document(userId).update(updates).await()
            } catch (e: Exception) {
                _errorMessages.emit("KYC update failed: ${e.message}")
            }
        }
    }

    fun approveMonetization(userId: String) {
        viewModelScope.launch {
            try {
                val currentAdminId = com.example.model.RepositoryManager.getCurrentUserId()
                val now = System.currentTimeMillis()

                firestore.collection("creator_monetizations").document(userId).set(
                    mapOf(
                        "userId" to userId,
                        "status" to com.example.model.AuthoritativeMonetizationState.ACTIVE.name,
                        "reviewedAt" to now,
                        "approvedAt" to now,
                        "reviewedBy" to currentAdminId,
                        "earningsEnabled" to true,
                        "subscriptionsEnabled" to true,
                        "giftsEnabled" to true,
                        "adsEnabled" to true
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()

                firestore.collection("users").document(userId)
                    .update(
                        "isMonetized", true,
                        "monetizationRequested", false,
                        "monetizationStatus", com.example.model.AuthoritativeMonetizationState.ACTIVE.name
                    ).await()
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error approving monetization", e)
                viewModelScope.launch { _errorMessages.emit("Failed: ${e.message ?: "Permission Denied"}") }
            }
        }
    }

    fun toggleVerification(userId: String, currentStatus: Boolean, badgeUrl: String? = null) {
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any>(
                    "isVerified" to !currentStatus,
                    "verificationRequested" to false
                )
                if (badgeUrl != null) {
                    updates["verificationBadgeUrl"] = badgeUrl
                }
                firestore.collection("users").document(userId)
                    .update(updates).await()
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error toggling verification", e)
                viewModelScope.launch { _errorMessages.emit("Failed: ${e.message ?: "Permission Denied"}") }
            }
        }
    }

    fun toggleBanUser(userId: String, currentStatus: Boolean) {
        viewModelScope.launch {
            try {
                val newBan = !currentStatus
                firestore.collection("users").document(userId)
                    .update(
                        mapOf(
                            "isBanned" to newBan,
                            "accountStatus" to if (newBan) "BANNED" else "ACTIVE",
                            "suspensionDate" to System.currentTimeMillis()
                        )
                    ).await()
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error toggling ban", e)
                viewModelScope.launch { _errorMessages.emit("Failed: ${e.message ?: "Permission Denied"}") }
            }
        }
    }

    fun toggleAdminUser(userId: String, currentStatus: Boolean) {
        viewModelScope.launch {
            try {
                firestore.collection("users").document(userId)
                    .update("isAdmin", !currentStatus).await()
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error toggling admin status", e)
                viewModelScope.launch { _errorMessages.emit("Failed: ${e.message ?: "Permission Denied"}") }
            }
        }
    }

    fun boostFollowers(userId: String) {
        addFollowers(userId, 10000)
    }

    fun boostLikes(userId: String) {
        addLikesToAllVideos(userId, 5000)
    }

    private val _userVideos = MutableStateFlow<List<com.example.model.Video>>(emptyList())
    val userVideos: StateFlow<List<com.example.model.Video>> = _userVideos

    fun fetchUserVideos(userId: String) {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("posts").whereEqualTo("userId", userId).limit(100).get().await()
                val list = snapshot.documents.mapNotNull { doc ->
                    val p = try {
                        doc.toObject(com.example.model.FirebasePost::class.java)?.copy(id = doc.id)
                    } catch(e: Exception) {
                        com.example.model.FirebasePost(
                            id = doc.id,
                            userId = doc.safeString("userId"),
                            postUrl = doc.safeString("postUrl").ifBlank { doc.safeString("fileUrl") },
                            caption = doc.safeString("caption"),
                            likesCount = doc.safeLong("likesCount", 0L).toInt(),
                            commentsCount = doc.safeLong("commentsCount", 0L).toInt()
                        )
                    }
                    if (p != null) {
                        com.example.model.Video(
                            id = p.id,
                            videoUrl = p.postUrl,
                            description = p.caption,
                            likesCount = p.likesCount,
                            commentsCount = p.commentsCount,
                            userId = p.userId,
                            viewsCount = p.viewsCount
                        )
                    } else null
                }
                _userVideos.value = list
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error fetching videos", e)
            }
        }
    }

    fun addLikesToVideo(videoId: String, amount: Int) {
        viewModelScope.launch {
            try {
                firestore.collection("posts").document(videoId)
                    .update("likesCount", com.google.firebase.firestore.FieldValue.increment(amount.toLong())).await()
                _userVideos.value = _userVideos.value.map { if (it.id == videoId) it.copy(likesCount = it.likesCount + amount) else it }
            } catch (e: Exception) {
                viewModelScope.launch { _errorMessages.emit("Failed: ${e.message}") }
            }
        }
    }

    fun addViewsToVideo(videoId: String, amount: Int) {
        viewModelScope.launch {
            try {
                firestore.collection("posts").document(videoId)
                    .update("viewsCount", com.google.firebase.firestore.FieldValue.increment(amount.toLong())).await()
                _userVideos.value = _userVideos.value.map { if (it.id == videoId) it.copy(viewsCount = it.viewsCount + amount) else it }
            } catch (e: Exception) {
                viewModelScope.launch { _errorMessages.emit("Failed: ${e.message}") }
            }
        }
    }

    fun addFollowers(userId: String, amount: Int) {
        viewModelScope.launch {
            try {
                firestore.collection("users").document(userId)
                    .update("followerCount", com.google.firebase.firestore.FieldValue.increment(amount.toLong())).await()
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error adding followers", e)
                viewModelScope.launch { _errorMessages.emit("Failed: ${e.message ?: "Permission Denied"}") }
            }
        }
    }

    fun addLikesToAllVideos(userId: String, amount: Int) {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("posts").whereEqualTo("userId", userId).limit(100).get().await()
                for (doc in snapshot.documents) {
                    doc.reference.update("likesCount", com.google.firebase.firestore.FieldValue.increment(amount.toLong())).await()
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error adding likes", e)
                _errorMessages.emit("Failed: ${e.message ?: "Permission Denied"}")
            }
        }
    }

    fun addViewsToAllVideos(userId: String, amount: Int) {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("posts").whereEqualTo("userId", userId).limit(100).get().await()
                for (doc in snapshot.documents) {
                    doc.reference.update("viewsCount", com.google.firebase.firestore.FieldValue.increment(amount.toLong())).await()
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Error adding views", e)
                _errorMessages.emit("Failed: ${e.message ?: "Permission Denied"}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        usersListener?.remove()
        reportsListener?.remove()
        flaggedPostsListener?.remove()
    }
}
