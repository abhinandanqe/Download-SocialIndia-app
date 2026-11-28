package com.example.model

import com.google.firebase.firestore.ServerTimestamp
import java.io.Serializable
import java.util.Date

data class StoreProduct(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val originalPrice: Double = 0.0,
    val mediaUrl: String = "",
    val isVideo: Boolean = false,
    val thumbnailUrl: String = "",
    val category: String = "Trending",
    val stock: Int = 50,
    val rating: Double = 4.9,
    val reviewsCount: Int = 42,
    val creatorId: String = "",
    val creatorName: String = "Social India Store",
    val creatorAvatar: String = "",
    val sourcePostId: String = "",
    val isFeatured: Boolean = true,
    @ServerTimestamp
    val timestamp: Date? = null,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

data class StoreOrder(
    val orderId: String = "",
    val productId: String = "",
    val productTitle: String = "",
    val productPrice: Double = 0.0,
    val buyerId: String = "",
    val buyerName: String = "",
    val buyerEmail: String = "",
    val txnId: String = "",
    val utrNumber: String = "",
    val paymentMode: String = "DIRECT_UPI",
    val status: String = "VERIFIED_PAID", // VERIFIED_PAID, PENDING_CONFIRMATION, FAILED
    val createdAt: Long = System.currentTimeMillis()
) : Serializable
