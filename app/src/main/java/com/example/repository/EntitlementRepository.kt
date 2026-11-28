package com.example.repository

import android.util.Log
import com.example.model.ContentEntitlement
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * EntitlementRepository: Authoritative Real-Time Access Control & Purchase Status Engine
 *
 * Manages:
 * - Real-time `onSnapshot` lookup for active unlocked individual paid posts (`content_entitlements`)
 * - Real-time `onSnapshot` lookup for active creator subscriptions (`CREATOR_MEMBERSHIP`)
 * - Real-time `onSnapshot` lookup for buyer's exclusive purchase approval states (`exclusive_purchases`:
 *   `pending_approval`, `approved`, `rejected`, `refunded`)
 * - In-memory cache for stutter-free feed scrolling
 */
object EntitlementRepository {
    private const val TAG = "EntitlementRepo"
    private val firestore get() = FirebaseFirestore.getInstance()

    // Key format: "${userId}_${contentId}" or "${userId}_${creatorId}_SUB"
    private val activeEntitlements = ConcurrentHashMap.newKeySet<String>()
    private val purchaseRecordsByContent = ConcurrentHashMap<String, ExclusivePurchaseRecord>()

    private val _unlockedKeysFlow = MutableStateFlow<Set<String>>(emptySet())
    val unlockedKeysFlow: StateFlow<Set<String>> = _unlockedKeysFlow.asStateFlow()

    private val _purchaseStatusMapFlow = MutableStateFlow<Map<String, ExclusivePurchaseRecord>>(emptyMap())
    val purchaseStatusMapFlow: StateFlow<Map<String, ExclusivePurchaseRecord>> = _purchaseStatusMapFlow.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private var activeListenerRegistration: ListenerRegistration? = null
    private var purchasesListenerRegistration: ListenerRegistration? = null

    fun markUnlockedLocally(userId: String, contentId: String) {
        if (userId.isBlank() || contentId.isBlank()) return
        val key = "${userId}_${contentId}"
        activeEntitlements.add(key)
        _unlockedKeysFlow.value = activeEntitlements.toSet()
    }

    fun markPurchasePendingLocally(record: ExclusivePurchaseRecord) {
        if (record.contentId.isBlank()) return
        purchaseRecordsByContent[record.contentId] = record
        _purchaseStatusMapFlow.value = purchaseRecordsByContent.toMap()
    }

    fun getPurchaseRecordFast(contentId: String): ExclusivePurchaseRecord? {
        if (contentId.isBlank()) return null
        return purchaseRecordsByContent[contentId]
    }

    /**
     * Start listening to active entitlements AND buyer exclusive purchase statuses for logged-in user.
     */
    fun startListeningForUser(userId: String) {
        activeListenerRegistration?.remove()
        activeListenerRegistration = null
        purchasesListenerRegistration?.remove()
        purchasesListenerRegistration = null

        if (userId.isBlank()) {
            activeEntitlements.clear()
            purchaseRecordsByContent.clear()
            _unlockedKeysFlow.value = emptySet()
            _purchaseStatusMapFlow.value = emptyMap()
            _isLoaded.value = true
            return
        }

        activeListenerRegistration = firestore.collection("content_entitlements")
            .whereEqualTo("userId", userId)
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error listening to entitlements: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val now = System.currentTimeMillis()
                    val newKeys = mutableSetOf<String>()

                    for (doc in snapshot.documents) {
                        val entitlement = doc.toObject(ContentEntitlement::class.java)
                        if (entitlement != null) {
                            val isNotExpired = entitlement.validUntil == -1L || entitlement.validUntil > now
                            if (isNotExpired) {
                                newKeys.add(doc.id)
                                if (entitlement.contentId.isNotBlank()) {
                                    newKeys.add("${userId}_${entitlement.contentId}")
                                }
                                if (entitlement.creatorId.isNotBlank() && entitlement.entitlementType == "CREATOR_MEMBERSHIP") {
                                    newKeys.add("${userId}_${entitlement.creatorId}_SUB")
                                }
                            }
                        }
                    }

                    activeEntitlements.clear()
                    activeEntitlements.addAll(newKeys)
                    _unlockedKeysFlow.value = newKeys.toSet()
                    _isLoaded.value = true
                }
            }

        // Listen to all exclusive purchases made by this user for instant status badges & live reveal on approval
        purchasesListenerRegistration = firestore.collection("exclusive_purchases")
            .whereEqualTo("buyerId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error listening to exclusive_purchases: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val updatedMap = mutableMapOf<String, ExclusivePurchaseRecord>()
                    val now = System.currentTimeMillis()
                    var keysChanged = false

                    for (doc in snapshot.documents) {
                        try {
                            val rec = doc.toObject(ExclusivePurchaseRecord::class.java)?.copy(id = doc.id)
                            if (rec != null && rec.contentId.isNotBlank()) {
                                updatedMap[rec.contentId] = rec
                                val unlockedKey = "${userId}_${rec.contentId}"
                                if (isExclusiveStatusUnlocked(rec.status)) {
                                    if (activeEntitlements.add(unlockedKey)) {
                                        keysChanged = true
                                    }
                                } else {
                                    // Ensure unapproved / refunded items don't linger unlocked in memory
                                    if (activeEntitlements.remove(unlockedKey)) {
                                        keysChanged = true
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    purchaseRecordsByContent.clear()
                    purchaseRecordsByContent.putAll(updatedMap)
                    _purchaseStatusMapFlow.value = updatedMap.toMap()
                    if (keysChanged) {
                        _unlockedKeysFlow.value = activeEntitlements.toSet()
                    }
                }
            }
    }

    fun stopListening() {
        activeListenerRegistration?.remove()
        activeListenerRegistration = null
        purchasesListenerRegistration?.remove()
        purchasesListenerRegistration = null
    }

    /**
     * Fast synchronous access check for Compose & Feed items.
     */
    fun hasAccessFast(
        userId: String,
        creatorId: String,
        contentId: String,
        isExclusive: Boolean,
        isPaidPost: Boolean
    ): Boolean {
        if (userId.isNotBlank() && userId == creatorId) return true
        if (!isExclusive && !isPaidPost) return true
        if (userId.isBlank()) return false

        if (contentId.isNotBlank()) {
            if (activeEntitlements.contains("${userId}_${contentId}")) {
                return true
            }
            val purchase = purchaseRecordsByContent[contentId]
            if (purchase != null && isExclusiveStatusUnlocked(purchase.status)) {
                return true
            }
        }

        if (creatorId.isNotBlank() && activeEntitlements.contains("${userId}_${creatorId}_SUB")) {
            return true
        }

        return false
    }

    /**
     * Authoritative asynchronous check directly querying Firestore.
     */
    suspend fun verifyAccessAuthoritative(
        userId: String,
        creatorId: String,
        contentId: String,
        isExclusive: Boolean,
        isPaidPost: Boolean
    ): Boolean {
        if (userId.isNotBlank() && userId == creatorId) return true
        if (!isExclusive && !isPaidPost) return true
        if (userId.isBlank()) return false

        try {
            val now = System.currentTimeMillis()

            if (contentId.isNotBlank()) {
                val postEntitlement = firestore.collection("content_entitlements")
                    .document("${userId}_${contentId}")
                    .get()
                    .await()
                if (postEntitlement.exists() && postEntitlement.getBoolean("active") == true) {
                    val validUntil = postEntitlement.getLong("validUntil") ?: -1L
                    if (validUntil == -1L || validUntil > now) return true
                }

                val purchaseDoc = firestore.collection("exclusive_purchases")
                    .document("${userId}_${contentId}")
                    .get()
                    .await()
                if (purchaseDoc.exists() && isExclusiveStatusUnlocked(purchaseDoc.getString("status"))) {
                    return true
                }
            }

            if (creatorId.isNotBlank()) {
                val subEntitlement = firestore.collection("content_entitlements")
                    .document("${userId}_${creatorId}_SUB")
                    .get()
                    .await()
                if (subEntitlement.exists() && subEntitlement.getBoolean("active") == true) {
                    val validUntil = subEntitlement.getLong("validUntil") ?: 0L
                    if (validUntil > now) return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in verifyAccessAuthoritative", e)
        }

        return false
    }
}
