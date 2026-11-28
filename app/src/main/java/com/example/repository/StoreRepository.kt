package com.example.repository

import android.util.Log
import com.example.model.FirebasePost
import com.example.model.RepositoryManager
import com.example.model.StoreProduct
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

object StoreRepository {
    private const val TAG = "StoreRepository"
    private val firestore: FirebaseFirestore
        get() = RepositoryManager.firestore

    fun getStoreProductsFlow(): Flow<List<StoreProduct>> = callbackFlow {
        val listener = firestore.collection("store_products")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Failed to listen to store products: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(StoreProduct::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing StoreProduct: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                trySend(list)
            }

        awaitClose { listener.remove() }
    }

    suspend fun addProduct(product: StoreProduct): Result<String> = withContext(Dispatchers.IO) {
        try {
            val docId = if (product.id.isNotBlank()) product.id else "PROD_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val finalProduct = product.copy(
                id = docId,
                createdAt = System.currentTimeMillis()
            )
            firestore.collection("store_products").document(docId).set(finalProduct).await()
            Log.d(TAG, "Successfully added store product: $docId")
            Result.success(docId)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding store product", e)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(productId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (productId.isNotBlank()) {
                firestore.collection("store_products").document(productId).delete().await()
            }
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting store product $productId", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches real Social India videos and posts so the admin can pick existing platform media
     * to promote / attach to a store product.
     */
    suspend fun getSocialIndiaPosts(): List<FirebasePost> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("posts")
                .limit(50)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(FirebasePost::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    val url = doc.getString("postUrl") ?: doc.getString("fileUrl") ?: ""
                    if (url.isNotEmpty()) {
                        FirebasePost(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            postUrl = url,
                            caption = doc.getString("caption") ?: "",
                            thumbnailUrl = doc.getString("thumbnailUrl") ?: url
                        )
                    } else null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Social India posts for store", e)
            emptyList()
        }
    }

    /**
     * Authoritative Admin Verification:
     * Only true platform admins can list, publish, or delete products in the Store.
     */
    suspend fun checkIsAdmin(userId: String): Boolean = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext false
        try {
            val currentEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email?.lowercase() ?: ""
            if (currentEmail == "abhinandankumar7867@gmail.com" || currentEmail == "socialindia.official@gmail.com") {
                return@withContext true
            }
            val doc = firestore.collection("users").document(userId).get().await()
            val isAdmin = doc.getBoolean("isAdmin") ?: false
            val email = doc.getString("email")?.lowercase() ?: ""
            isAdmin || email == "abhinandankumar7867@gmail.com" || email == "socialindia.official@gmail.com"
        } catch (e: Exception) {
            Log.w(TAG, "Error checking admin status: ${e.message}")
            false
        }
    }

    /**
     * Validates banking UTR syntax and checks against known fake test patterns.
     */
    fun validateUtrFormat(utr: String): Pair<Boolean, String> {
        val trimmed = utr.trim()
        if (trimmed.length != 12 || !trimmed.all { it.isDigit() }) {
            return Pair(false, "Invalid UTR format. Official Indian banking UTR / RRN must be exactly 12 numeric digits.")
        }
        val fakePatterns = listOf(
            "000000000000", "111111111111", "222222222222", "333333333333",
            "444444444444", "555555555555", "666666666666", "777777777777",
            "888888888888", "999999999999", "123456789012", "987654321098"
        )
        if (fakePatterns.contains(trimmed)) {
            return Pair(false, "Fraud Warning: Obvious dummy UTR pattern detected. Please enter genuine banking reference.")
        }
        return Pair(true, "")
    }

    /**
     * Anti-Fraud Replay Protection:
     * Queries Firestore collections to check if this UTR has already been submitted or claimed.
     */
    suspend fun isUtrDuplicate(utr: String): Boolean = withContext(Dispatchers.IO) {
        val cleanUtr = utr.trim()
        if (cleanUtr.isEmpty()) return@withContext false
        try {
            // Check 1: store_orders
            val storeOrderCheck = firestore.collection("store_orders")
                .whereEqualTo("utrNumber", cleanUtr)
                .limit(1)
                .get()
                .await()
            if (!storeOrderCheck.isEmpty) return@withContext true

            // Check 2: payment_transactions
            val txnCheck = firestore.collection("payment_transactions")
                .whereEqualTo("providerRef", cleanUtr)
                .limit(1)
                .get()
                .await()
            if (!txnCheck.isEmpty) return@withContext true

            // Check 3: payment_orders
            val orderCheck = firestore.collection("payment_orders")
                .whereEqualTo("providerTxnId", cleanUtr)
                .limit(1)
                .get()
                .await()
            if (!orderCheck.isEmpty) return@withContext true

            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking duplicate UTR", e)
            false
        }
    }

    /**
     * Records authoritative store order and payment audit record in Firestore.
     */
    suspend fun recordStoreOrder(order: com.example.model.StoreOrder): Result<String> = withContext(Dispatchers.IO) {
        try {
            val orderId = if (order.orderId.isNotBlank()) order.orderId else "ORD_STORE_${System.currentTimeMillis()}"
            val finalOrder = order.copy(orderId = orderId)

            firestore.collection("store_orders").document(orderId)
                .set(finalOrder)
                .await()

            // Also log in payment_transactions for consolidated financial tracking
            val txnRecord = com.example.model.PaymentTransactionRecord(
                txnId = finalOrder.txnId.ifBlank { "TXN_${System.currentTimeMillis()}" },
                orderId = orderId,
                userId = finalOrder.buyerId,
                creatorId = "STORE_OFFICIAL",
                productType = "STORE_PURCHASE",
                amountRupees = finalOrder.productPrice,
                currency = "INR",
                paymentMode = finalOrder.paymentMode,
                payeeUpiId = com.example.utils.UpiPaymentManager.getAdminUpiId(),
                status = "SUCCESS",
                providerRef = finalOrder.utrNumber,
                timestamp = finalOrder.createdAt
            )
            firestore.collection("payment_transactions").document(txnRecord.txnId)
                .set(txnRecord)
                .await()

            Log.d(TAG, "Store order $orderId recorded successfully with status ${finalOrder.status}")
            Result.success(orderId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record store order", e)
            Result.failure(e)
        }
    }
}
