package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.model.RepositoryManager
import kotlinx.coroutines.tasks.await

/**
 * High-fidelity Instagram-style shared Post/Reel card for Direct Messages and Group Chats.
 * Matches Instagram's design with rounded corners, top-left reel glyph, centered play button,
 * and bottom creator avatar with handle.
 */
@Composable
fun InstagramSharedMediaBubble(
    postId: String? = null,
    mediaUrl: String? = null,
    thumbnailUrl: String? = null,
    mediaType: String = "video", // "video", "image", "reel", "post"
    messageCaption: String = "",
    authorName: String? = null,
    authorAvatar: String? = null,
    authorId: String? = null,
    isVerified: Boolean = false,
    isMine: Boolean = false,
    onMediaClick: (postId: String, mediaUrl: String, isVideo: Boolean) -> Unit,
    onAuthorClick: ((userId: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var fetchedThumbnail by remember { mutableStateOf(thumbnailUrl ?: "") }
    var fetchedMediaUrl by remember { mutableStateOf(mediaUrl ?: "") }
    var fetchedAuthorName by remember { mutableStateOf(authorName ?: "") }
    var fetchedAuthorAvatar by remember { mutableStateOf(authorAvatar ?: "") }
    var fetchedAuthorId by remember { mutableStateOf(authorId ?: "") }
    var fetchedIsVerified by remember { mutableStateOf(isVerified) }
    var isVideoType by remember { mutableStateOf(mediaType.contains("video", ignoreCase = true) || mediaType.contains("reel", ignoreCase = true)) }
    var isLoadingDetails by remember { mutableStateOf(false) }

    // Fetch details from Firestore if not directly provided
    LaunchedEffect(postId) {
        if (!postId.isNullOrEmpty()) {
            val cleanPostId = postId.replace("reels://", "").replace("story://", "").replace("post://", "").trim()
            if (fetchedThumbnail.isEmpty() || fetchedAuthorName.isEmpty() || fetchedAuthorName == "User" || fetchedAuthorName == "creator") {
                isLoadingDetails = true
                try {
                    var postDoc = RepositoryManager.firestore.collection("posts").document(cleanPostId).get().await()
                    if (!postDoc.exists()) {
                        postDoc = RepositoryManager.firestore.collection("videos").document(cleanPostId).get().await()
                    }
                    if (postDoc.exists()) {
                        val postUrlsList = postDoc.get("postUrls") as? List<*>
                        val firstPostUrl = postUrlsList?.firstOrNull() as? String
                        val vUrl = postDoc.getString("postUrl")
                            ?: postDoc.getString("videoUrl")
                            ?: postDoc.getString("mediaUrl")
                            ?: firstPostUrl
                            ?: ""
                        val tUrl = postDoc.getString("thumbnailUrl")
                            ?: postDoc.getString("postUrl")
                            ?: firstPostUrl
                            ?: vUrl
                        val uId = postDoc.getString("userId") ?: ""
                        val isVid = postDoc.getBoolean("isVideo")
                            ?: (vUrl.contains(".mp4") || vUrl.contains(".mkv") || vUrl.contains("video") || messageCaption.contains("reel", ignoreCase = true) || mediaType.contains("reel", ignoreCase = true) || mediaType.contains("video", ignoreCase = true))
                        
                        if (fetchedMediaUrl.isEmpty() && vUrl.isNotEmpty()) fetchedMediaUrl = vUrl
                        if (fetchedThumbnail.isEmpty() && tUrl.isNotEmpty()) fetchedThumbnail = tUrl
                        if (fetchedAuthorId.isEmpty() && uId.isNotEmpty()) fetchedAuthorId = uId
                        isVideoType = isVid

                        // Fetch creator details
                        if (uId.isNotEmpty() && (fetchedAuthorName.isEmpty() || fetchedAuthorName == "User" || fetchedAuthorName == "creator")) {
                            val userDoc = RepositoryManager.firestore.collection("users").document(uId).get().await()
                            if (userDoc.exists()) {
                                fetchedAuthorName = userDoc.getString("username") ?: "creator"
                                fetchedAuthorAvatar = userDoc.getString("avatar_url")
                                    ?: userDoc.getString("avatarUrl")
                                    ?: userDoc.getString("profilePicUrl")
                                    ?: ""
                                fetchedIsVerified = userDoc.getBoolean("isVerified") ?: false
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("InstagramSharedCard", "Error fetching post $postId", e)
                } finally {
                    isLoadingDetails = false
                }
            }
        }
    }

    val displayThumbnail = fetchedThumbnail.ifEmpty { fetchedMediaUrl }.ifEmpty {
        "https://picsum.photos/seed/${postId ?: "reel"}/600/900"
    }

    val displayAuthor = fetchedAuthorName.ifEmpty { "creator" }
    val displayAvatar = fetchedAuthorAvatar.ifEmpty { "https://api.dicebear.com/7.x/avataaars/png?seed=${fetchedAuthorId.ifEmpty { displayAuthor }}" }

    val cleanId = (postId ?: "").replace("reels://", "").replace("story://", "").replace("post://", "").trim()

    Column(
        modifier = modifier
            .width(210.dp)
            .padding(vertical = 4.dp)
    ) {
        // Main Instagram Media Card Bubble
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp)
                .clickable {
                    onMediaClick(cleanId, fetchedMediaUrl, isVideoType)
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Media Thumbnail Background
                SubcomposeAsyncImage(
                    model = displayThumbnail,
                    contentDescription = "Shared Content",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF222222)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White.copy(alpha = 0.7f),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isVideoType) Icons.Default.Videocam else Icons.Default.Image,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                )

                // Top Gradient & Reel Badge (Exact Instagram Style)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                            )
                        )
                )

                // Top-Left Badge (Reel / Video Play glyph)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 12.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVideoType) Icons.Default.PlayArrow else Icons.Default.PhotoLibrary,
                        contentDescription = "Reel",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Centered Soft Play Button for Videos/Reels
                if (isVideoType) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.38f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Bottom Gradient & Creator Overlay (Exact Instagram Style)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Creator Avatar
                    AsyncImage(
                        model = displayAvatar,
                        contentDescription = displayAuthor,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                            .clickable {
                                if (fetchedAuthorId.isNotEmpty()) {
                                    onAuthorClick?.invoke(fetchedAuthorId)
                                }
                            },
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Creator Username
                    Text(
                        text = displayAuthor,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable {
                                if (fetchedAuthorId.isNotEmpty()) {
                                    onAuthorClick?.invoke(fetchedAuthorId)
                                }
                            }
                    )

                    // Blue Verified Tick
                    if (fetchedIsVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFF1DA1F2),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        // Custom caption/message if sent with the share (e.g., "Check this out!")
        val cleanMsg = messageCaption.trim()
        val isDefaultMsg = cleanMsg.isEmpty() || 
            cleanMsg.startsWith("Shared a reel", ignoreCase = true) || 
            cleanMsg.startsWith("Shared a post", ignoreCase = true) ||
            cleanMsg.startsWith("Shared a story", ignoreCase = true) ||
            cleanMsg.startsWith("reels://", ignoreCase = true) ||
            cleanMsg.startsWith("story://", ignoreCase = true) ||
            cleanMsg.startsWith("post://", ignoreCase = true) ||
            cleanMsg.contains("reels://", ignoreCase = true) ||
            cleanMsg.contains("post://", ignoreCase = true) ||
            cleanMsg.contains("story://", ignoreCase = true) ||
            cleanMsg.contains("Shared a ", ignoreCase = true)

        if (!isDefaultMsg) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = cleanMsg,
                color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}
