package com.example.model

import androidx.compose.runtime.Stable
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Post: Data model for Social India feed posts
 * Fully supports:
 * 1. Public Posts
 * 2. Followers Only
 * 3. Subscribers Only (Creator Subscription)
 * 4. Individual Paid Unlock (One-time purchase)
 */
@Stable
data class Post(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String = "",

    @get:PropertyName("userId") @set:PropertyName("userId")
    var userId: String = "",

    @get:PropertyName("creatorName") @set:PropertyName("creatorName")
    var creatorName: String = "",

    @get:PropertyName("creatorAvatarUrl") @set:PropertyName("creatorAvatarUrl")
    var creatorAvatarUrl: String = "",

    @get:PropertyName("postUrl") @set:PropertyName("postUrl")
    var postUrl: String = "",

    @get:PropertyName("thumbnailUrl") @set:PropertyName("thumbnailUrl")
    var thumbnailUrl: String = "",

    @get:PropertyName("caption") @set:PropertyName("caption")
    var caption: String = "",

    @get:PropertyName("mediaType") @set:PropertyName("mediaType")
    var mediaType: String = "image", // "image" or "video"

    @get:PropertyName("isVideo") @set:PropertyName("isVideo")
    var isVideo: Boolean = false,

    // --- ACCESS & MONETIZATION SETTINGS ---
    @get:PropertyName("accessType") @set:PropertyName("accessType")
    var accessType: String = "PUBLIC", // "PUBLIC", "FOLLOWERS_ONLY", "SUBSCRIBERS_ONLY", "INDIVIDUAL_PAID"

    @get:PropertyName("isExclusive") @set:PropertyName("isExclusive")
    var isExclusive: Boolean = false, // True if locked for Creator Subscribers

    @get:PropertyName("subscriptionPrice") @set:PropertyName("subscriptionPrice")
    var subscriptionPrice: Double = 99.00, // Monthly subscription price in INR

    @get:PropertyName("isPaidPost") @set:PropertyName("isPaidPost")
    var isPaidPost: Boolean = false, // True if post can be unlocked individually

    @get:PropertyName("postUnlockPrice") @set:PropertyName("postUnlockPrice")
    var postUnlockPrice: Double = 29.00, // One-time price to permanently unlock this post

    @get:PropertyName("likesCount") @set:PropertyName("likesCount")
    var likesCount: Int = 0,

    @get:PropertyName("commentsCount") @set:PropertyName("commentsCount")
    var commentsCount: Int = 0,

    @ServerTimestamp
    @get:PropertyName("timestamp") @set:PropertyName("timestamp")
    var timestamp: Date? = null
) {
    val postId: String get() = id
    val creatorId: String get() = userId
    val mediaUrl: String get() = postUrl
}
