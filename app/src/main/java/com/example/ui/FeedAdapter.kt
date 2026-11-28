package com.example.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.R
import com.example.model.Post
import com.google.android.material.imageview.ShapeableImageView

/**
 * FeedAdapter: RecyclerView Adapter with Instagram-style Creator Subscription Paywall
 */
class FeedAdapter(
    private val currentUserId: String,
    private val listener: OnPostInteractionListener
) : ListAdapter<Post, FeedAdapter.PostViewHolder>(PostDiffCallback()) {

    // Set of creator IDs that the current logged-in user is subscribed to
    private var subscribedCreatorIds: Set<String> = emptySet()

    /**
     * Interface to communicate user actions (Subscribe, Like, Comment, Share) to Activity/Fragment
     */
    interface OnPostInteractionListener {
        fun onSubscribeClick(post: Post)
        fun onLikeClick(post: Post)
        fun onCommentClick(post: Post)
        fun onShareClick(post: Post)
        fun onCreatorProfileClick(creatorId: String)
    }

    /**
     * Updates the set of subscribed creator IDs in real time
     */
    fun updateSubscribedCreators(creatorIds: Set<String>) {
        this.subscribedCreatorIds = creatorIds
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = getItem(position)
        holder.bind(post)
    }

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCreatorAvatar: ShapeableImageView = itemView.findViewById(R.id.ivCreatorAvatar)
        private val tvCreatorName: TextView = itemView.findViewById(R.id.tvCreatorName)
        private val badgeExclusiveCreator: TextView = itemView.findViewById(R.id.badgeExclusiveCreator)
        private val tvTimeAgo: TextView = itemView.findViewById(R.id.tvTimeAgo)
        private val btnMoreOptions: ImageView = itemView.findViewById(R.id.btnMoreOptions)

        private val ivPostMedia: ImageView = itemView.findViewById(R.id.ivPostMedia)
        private val ivVideoIndicator: ImageView = itemView.findViewById(R.id.ivVideoIndicator)
        private val flExclusiveOverlay: FrameLayout = itemView.findViewById(R.id.flExclusiveOverlay)
        private val tvExclusiveDescription: TextView = itemView.findViewById(R.id.tvExclusiveDescription)
        private val btnSubscribeToUnlock: Button = itemView.findViewById(R.id.btnSubscribeToUnlock)

        private val btnLike: ImageView = itemView.findViewById(R.id.btnLike)
        private val btnComment: ImageView = itemView.findViewById(R.id.btnComment)
        private val btnShare: ImageView = itemView.findViewById(R.id.btnShare)
        private val btnBookmark: ImageView = itemView.findViewById(R.id.btnBookmark)

        private val tvLikesCount: TextView = itemView.findViewById(R.id.tvLikesCount)
        private val tvCaption: TextView = itemView.findViewById(R.id.tvCaption)

        fun bind(post: Post) {
            // 1. Creator Info
            val displayName = post.creatorName.ifBlank { "Creator_${post.creatorId.take(5)}" }
            tvCreatorName.text = displayName
            tvTimeAgo.text = "Recent"

            // Creator Profile Avatar
            val avatarUrl = post.creatorAvatarUrl.ifBlank {
                "https://api.dicebear.com/7.x/avataaars/png?seed=${post.creatorId}"
            }
            ivCreatorAvatar.load(avatarUrl) {
                crossfade(true)
            }

            // Creator Profile Click
            ivCreatorAvatar.setOnClickListener { listener.onCreatorProfileClick(post.creatorId) }
            tvCreatorName.setOnClickListener { listener.onCreatorProfileClick(post.creatorId) }

            // 2. Video Indicator
            val isVideoContent = post.isVideo || post.mediaType == "video"
            ivVideoIndicator.visibility = if (isVideoContent) View.VISIBLE else View.GONE

            // 3. Entitlement & Access Check via EntitlementRepository
            val hasAccess = com.example.repository.EntitlementRepository.hasAccessFast(
                userId = currentUserId,
                creatorId = post.creatorId,
                contentId = post.postId,
                isExclusive = post.isExclusive,
                isPaidPost = post.isPaidPost
            )
            val isLocked = !hasAccess

            // Load media thumbnail or image
            val mediaUrl = post.mediaUrl.ifBlank { post.thumbnailUrl }

            if (isLocked) {
                // =========================================================================
                // 🔒 LOCKED STATE: BLUR MEDIA & SHOW APPROPRIATE PAYWALL OVERLAY
                // =========================================================================
                flExclusiveOverlay.visibility = View.VISIBLE
                badgeExclusiveCreator.visibility = View.VISIBLE

                // Load preview with blur effect
                ivPostMedia.load(mediaUrl) {
                    crossfade(true)
                }

                // Apply Hardware Blur Effect (Android 12+) or Dark Frosted Tint
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blurEffect = RenderEffect.createBlurEffect(35f, 35f, Shader.TileMode.CLAMP)
                    ivPostMedia.setRenderEffect(blurEffect)
                } else {
                    // Pre-Android 12 fallback tint
                    ivPostMedia.setColorFilter(android.graphics.Color.argb(180, 10, 10, 16))
                }

                if (post.isPaidPost) {
                    val postPrice = post.postUnlockPrice.toInt().takeIf { it > 0 } ?: 29
                    btnSubscribeToUnlock.text = "Unlock Post for ₹$postPrice"
                    tvExclusiveDescription.text = "This is an individual paid post. Unlock once to permanently view full-resolution media."
                } else {
                    val subPrice = post.subscriptionPrice.toInt().takeIf { it > 0 } ?: 49
                    btnSubscribeToUnlock.text = "Subscribe to Unlock (₹$subPrice/mo)"
                    tvExclusiveDescription.text = "This post is reserved exclusively for subscribers of @$displayName. Subscribe now to view all exclusive media."
                }

                // Trigger action callback
                btnSubscribeToUnlock.setOnClickListener {
                    listener.onSubscribeClick(post)
                }

                ivPostMedia.setOnClickListener(null)
            } else {
                // =========================================================================
                // 🔓 UNLOCKED STATE: CLEAR BLUR & SHOW FULL CRISP MEDIA NORMALLY
                // =========================================================================
                flExclusiveOverlay.visibility = View.GONE
                badgeExclusiveCreator.visibility = if (post.isExclusive) View.VISIBLE else View.GONE

                // Remove blur
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ivPostMedia.setRenderEffect(null)
                } else {
                    ivPostMedia.clearColorFilter()
                }

                // Load high-resolution media
                ivPostMedia.load(mediaUrl) {
                    crossfade(true)
                }

                // Media click
                ivPostMedia.setOnClickListener {
                    // Play video or open full-screen viewer
                }
            }

            // 4. Post Caption & Likes
            tvLikesCount.text = "${post.likesCount} likes"
            if (post.caption.isNotBlank()) {
                tvCaption.visibility = View.VISIBLE
                tvCaption.text = post.caption
            } else {
                tvCaption.visibility = View.GONE
            }

            // 5. Interaction Buttons
            btnLike.setOnClickListener { listener.onLikeClick(post) }
            btnComment.setOnClickListener { listener.onCommentClick(post) }
            btnShare.setOnClickListener { listener.onShareClick(post) }
        }
    }

    /**
     * DiffUtil Callback for 60/120Hz smooth scrolling performance
     */
    class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem == newItem
        }
    }
}
