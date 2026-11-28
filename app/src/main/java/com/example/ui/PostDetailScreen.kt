package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Instagram-standard Post Detail / Posts Feed Screen.
 * Displays the exact post(s) selected by the user, preserving full context,
 * high-fidelity media, interactions (Like, Comment, Share, Save), and smooth navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    initialPostId: String,
    userId: String? = null,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToReel: (String) -> Unit = {},
    onAudioClick: (String, String, String, String) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }

    var posts by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var creatorUser by remember { mutableStateOf<User?>(null) }
    var selectedVideoForComments by remember { mutableStateOf<Video?>(null) }
    var selectedVideoForShare by remember { mutableStateOf<Video?>(null) }
    var selectedVideoForOptions by remember { mutableStateOf<Video?>(null) }
    var storyVideoToShare by remember { mutableStateOf<Video?>(null) }

    val listState = rememberLazyListState()

    // Fetch Posts from Firestore
    LaunchedEffect(initialPostId, userId) {
        isLoading = true
        try {
            val fetchedVideos = mutableListOf<Video>()
            val userCache = mutableMapOf<String, User>()

            suspend fun getAuthor(uid: String): User {
                if (uid.isEmpty()) return User(id = "", username = "User", avatarUrl = "")
                userCache[uid]?.let { return it }
                return try {
                    val uDoc = firestore.collection("users").document(uid).get().await()
                    val u = User(
                        id = uid,
                        username = uDoc.getString("username") ?: "User",
                        avatarUrl = uDoc.getString("profilePicUrl")
                            ?: uDoc.getString("avatar_url")
                            ?: uDoc.getString("avatarUrl")
                            ?: "",
                        isVerified = uDoc.getBoolean("isVerified") ?: false
                    )
                    userCache[uid] = u
                    u
                } catch (e: Exception) {
                    User(id = uid, username = "User", avatarUrl = "")
                }
            }

            suspend fun mapFirebasePostToVideo(p: FirebasePost, fallbackAuthor: User? = null): Video {
                val mediaUrl = p.postUrl.ifEmpty { p.postUrls.firstOrNull() ?: "" }
                val pAuthor = fallbackAuthor ?: getAuthor(p.userId)
                val resolvedTrackUrl = com.example.api.MusicClient.resolveAudioUrlForTrack(p.musicTrackUrl, p.musicTrack)
                val isImg = when {
                    p.mediaType.equals("video", ignoreCase = true) || p.mediaType.equals("reel", ignoreCase = true) -> false
                    p.mediaType.equals("image", ignoreCase = true) || p.postUrls.size > 1 -> true
                    else -> com.example.utils.MediaResolver.isImage(mediaUrl)
                }
                val resolvedThumb = if (isImg) {
                    com.example.utils.MediaResolver.resolveImageUrl((p.thumbnailUrl ?: "").ifEmpty { mediaUrl }).ifBlank { mediaUrl }
                } else {
                    com.example.utils.MediaResolver.resolveVideoPosterUrl(mediaUrl, p.thumbnailUrl)
                }
                val currentUserId = RepositoryManager.getCurrentUserId()
                val isLiked = currentUserId.isNotEmpty() && (RepositoryManager.isPostLikedCached(p.id) || p.likedByUserIds.contains(currentUserId) || p.likes.containsKey(currentUserId))
                val isSaved = currentUserId.isNotEmpty() && RepositoryManager.isPostSavedCached(p.id)
                val postTime = p.timestamp?.time?.takeIf { it > 0L } ?: p.createdAtMillis

                return Video(
                    id = p.id,
                    userId = p.userId,
                    user = pAuthor,
                    videoUrl = mediaUrl,
                    thumbnailUrl = resolvedThumb,
                    mediaType = if (isImg) "image" else "video",
                    description = p.caption,
                    likesCount = p.likesCount,
                    viewsCount = p.viewsCount,
                    commentsCount = p.commentsCount,
                    savesCount = p.savesCount,
                    sharesCount = p.sharesCount,
                    repostsCount = p.repostsCount,
                    musicTrack = p.musicTrack ?: "Original Audio",
                    musicTrackUrl = resolvedTrackUrl ?: p.musicTrackUrl,
                    musicTrackImageUrl = p.musicTrackImageUrl,
                    musicTrackId = p.musicTrackId,
                    musicTrackArtist = p.musicTrackArtist,
                    isLiked = isLiked,
                    isSaved = isSaved,
                    videoUrls = p.postUrls,
                    isPremium = p.isSubscribersOnly || p.isPaidPost,
                    premiumPrice = if (p.isPaidPost && p.postUnlockPrice > 0) p.postUnlockPrice else p.premiumPrice,
                    isPaidPost = p.isPaidPost,
                    postUnlockPrice = p.postUnlockPrice,
                    accessType = p.accessType,
                    visibility = p.visibility.ifBlank { "PUBLIC" },
                    timestamp = postTime,
                    videoWidth = p.videoWidth,
                    videoHeight = p.videoHeight,
                    aspectRatio = p.aspectRatio
                )
            }

            // 1. If userId is provided, load the user's posts in order
            if (!userId.isNullOrEmpty()) {
                val userObj = getAuthor(userId)
                creatorUser = userObj
                val viewerId = RepositoryManager.getCurrentUserId()

                val postsSnapshot = firestore.collection("posts")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

                val rawPosts = postsSnapshot.documents.mapNotNull { doc ->
                    try { doc.toObject(FirebasePost::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
                }.filter { p ->
                    if (p.isDeleted || p.isArchived) return@filter false
                    val vis = p.visibility.uppercase().ifBlank { "PUBLIC" }
                    if (vis == "PRIVATE" && p.userId != viewerId) return@filter false
                    true
                }.sortedWith(
                    compareByDescending<FirebasePost> {
                        it.timestamp?.time?.takeIf { t -> t > 0L } ?: it.createdAtMillis
                    }.thenByDescending { it.id }
                )

                for (p in rawPosts) {
                    val pAuthor = if (p.userId == userId) userObj else getAuthor(p.userId)
                    fetchedVideos.add(mapFirebasePostToVideo(p, pAuthor))
                }

                // If initialPostId is not in the user's fetched list, fetch it directly
                if (fetchedVideos.none { it.id == initialPostId }) {
                    val singleDoc = firestore.collection("posts").document(initialPostId).get().await()
                    if (singleDoc.exists()) {
                        val sp = try { singleDoc.toObject(FirebasePost::class.java)?.copy(id = singleDoc.id) } catch (_: Exception) { null }
                        if (sp != null && !sp.isDeleted) {
                            val spAuthor = getAuthor(sp.userId)
                            fetchedVideos.add(0, mapFirebasePostToVideo(sp, spAuthor))
                        }
                    }
                }
            } else {
                // 2. Load single post by initialPostId
                val singleDoc = firestore.collection("posts").document(initialPostId).get().await()
                if (singleDoc.exists()) {
                    val sp = try { singleDoc.toObject(FirebasePost::class.java)?.copy(id = singleDoc.id) } catch (_: Exception) { null }
                    if (sp != null && !sp.isDeleted) {
                        val authorUser = getAuthor(sp.userId)
                        creatorUser = authorUser
                        fetchedVideos.add(mapFirebasePostToVideo(sp, authorUser))
                    }
                }
            }

            posts = fetchedVideos
            isLoading = false

            // Scroll immediately to the initialPostId
            val targetIdx = fetchedVideos.indexOfFirst { it.id == initialPostId }
            if (targetIdx in fetchedVideos.indices && targetIdx > 0) {
                try {
                    listState.scrollToItem(targetIdx)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val headerName = creatorUser?.username?.ifEmpty { "POSTS" } ?: "POSTS"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = headerName.uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            creatorUser?.let { cu ->
                                if (cu.isVerified || cu.verificationStatus == "approved" || cu.verificationStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    VerificationBadge(user = cu, modifier = Modifier.size(13.dp), showDialogOnClick = false)
                                }
                            }
                        }
                        Text(
                            text = "Posts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (posts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ImageNotSupported,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Post not available or deleted",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = onBack) {
                            Text("Go Back")
                        }
                    }
                }
            } else {
                val currentVisibleItem = remember { derivedStateOf { listState.firstVisibleItemIndex } }
                val activePlayingId = posts.getOrNull(currentVisibleItem.value)?.id ?: ""

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = 600.dp)
                ) {
                    itemsIndexed(
                        items = posts,
                        key = { _, item -> item.id }
                    ) { _, video ->
                        HomeVideoPostItem(
                            video = video,
                            isPlaying = video.id == activePlayingId,
                            onLike = {
                                scope.launch {
                                    try {
                                        if (currentUserId.isNotEmpty()) {
                                            val postRef = firestore.collection("posts").document(video.id)
                                            val snap = postRef.get().await()
                                            val likedUsers = (snap.get("likedByUserIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                                            val isLiked = likedUsers.contains(currentUserId)
                                            val newLikedUsers = if (isLiked) likedUsers - currentUserId else likedUsers + currentUserId
                                            postRef.update(
                                                mapOf(
                                                    "likedByUserIds" to newLikedUsers,
                                                    "likesCount" to newLikedUsers.size
                                                )
                                            ).await()
                                        }
                                    } catch (_: Exception) {}
                                }
                            },
                            onCommentClick = { selectedVideoForComments = video },
                            onShareClick = { selectedVideoForShare = video },
                            onSaveClick = {
                                scope.launch {
                                    try {
                                        if (currentUserId.isNotEmpty()) {
                                            val userRef = firestore.collection("users").document(currentUserId)
                                            val snap = userRef.get().await()
                                            val savedPostIds = (snap.get("savedPostIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                                            val isSaved = savedPostIds.contains(video.id)
                                            val newSaved = if (isSaved) savedPostIds - video.id else savedPostIds + video.id
                                            userRef.update("savedPostIds", newSaved).await()
                                            Toast.makeText(context, if (isSaved) "Removed from saved" else "Saved to collection", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (_: Exception) {}
                                }
                            },
                            onRepostClick = {
                                scope.launch {
                                    try {
                                        if (currentUserId.isNotEmpty()) {
                                            val postRef = firestore.collection("posts").document(video.id)
                                            val snapshot = postRef.get().await()
                                            if (snapshot.exists()) {
                                                val currentReposted = snapshot.get("repostedByUserIds") as? List<*> ?: emptyList<Any>()
                                                val newReposted = currentReposted.filterIsInstance<String>().toMutableList()
                                                val isCurrentlyReposted = newReposted.contains(currentUserId)
                                                if (isCurrentlyReposted) {
                                                    newReposted.remove(currentUserId)
                                                } else {
                                                    newReposted.add(currentUserId)
                                                }
                                                firestore.runTransaction { tx ->
                                                    tx.update(postRef, "repostedByUserIds", newReposted)
                                                    tx.update(postRef, "repostsCount", newReposted.size)
                                                }.await()
                                                Toast.makeText(
                                                    context,
                                                    if (isCurrentlyReposted) "Repost removed" else "Reposted",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            },
                            onOptionsClick = { selectedVideoForOptions = video },
                            onProfileClick = { onNavigateToProfile(video.userId) },
                            onReelsClick = { onNavigateToReel(video.id) },
                            onAudioClick = onAudioClick
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }

    // Modern Comments Bottom Sheet
    selectedVideoForComments?.let { video ->
        ModernCommentBottomSheet(
            video = video,
            onDismiss = { selectedVideoForComments = null },
            currentUserId = currentUserId,
            onProfileClick = { uid ->
                selectedVideoForComments = null
                onNavigateToProfile(uid)
            }
        )
    }

    // Share Bottom Sheet with "Add to Story"
    selectedVideoForShare?.let { video ->
        ShareBottomSheet(
            video = video,
            onDismiss = { selectedVideoForShare = null },
            onAddToStory = { sharedVideo ->
                selectedVideoForShare = null
                val viewerUid = RepositoryManager.getCurrentUserId()
                val isExLocked = (sharedVideo.isExclusiveContent || sharedVideo.isPaidPost) &&
                    sharedVideo.postUnlockPrice >= 10.0 &&
                    sharedVideo.userId != viewerUid &&
                    !com.example.repository.EntitlementRepository.hasAccessFast(
                        userId = viewerUid,
                        creatorId = sharedVideo.userId,
                        contentId = sharedVideo.id,
                        isExclusive = true,
                        isPaidPost = true
                    )
                if (isExLocked) {
                    Toast.makeText(context, "Unlock this exclusive content before adding to your story", Toast.LENGTH_SHORT).show()
                } else {
                    storyVideoToShare = sharedVideo
                }
            }
        )
    }

    // Story Creator from Shared Post
    storyVideoToShare?.let { targetVideo ->
        val cachedVault = com.example.repository.ExclusiveContentRepository.getCachedVaultMedia(targetVideo.id)
        val targetMediaUri = (cachedVault?.mediaUrl?.takeIf { it.isNotEmpty() }
            ?: targetVideo.videoUrl.takeIf { it.isNotEmpty() }
            ?: cachedVault?.postUrls?.firstOrNull()?.takeIf { it.isNotEmpty() }
            ?: targetVideo.videoUrls.firstOrNull()?.takeIf { it.isNotEmpty() }
            ?: cachedVault?.thumbnailUrl?.takeIf { it.isNotEmpty() }
            ?: targetVideo.thumbnailUrl.takeIf { it.isNotEmpty() })
            ?.let { android.net.Uri.parse(it) }

        StoryCreatorDialog(
            onDismiss = { storyVideoToShare = null },
            repostedPostId = targetVideo.id,
            isReback = true,
            mentionedUserId = targetVideo.user.id,
            mentionedUsername = targetVideo.user.username,
            initialMediaUri = targetMediaUri
        )
    }

    // Post Options Dialog
    selectedVideoForOptions?.let { post ->
        val isOwn = post.userId == currentUserId
        AlertDialog(
            onDismissRequest = { selectedVideoForOptions = null },
            title = { Text("Post Options", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val link = "https://socialindia.app/post/${post.id}"
                            clipMgr?.setPrimaryClip(ClipData.newPlainText("Post Link", link))
                            Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                            selectedVideoForOptions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Copy Link")
                    }

                    if (isOwn) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        firestore.collection("posts").document(post.id).update("isDeleted", true).await()
                                        posts = posts.filter { it.id != post.id }
                                        Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
                                    }
                                    selectedVideoForOptions = null
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Delete Post")
                        }
                    } else {
                        TextButton(
                            onClick = {
                                Toast.makeText(context, "Report submitted. Thank you.", Toast.LENGTH_SHORT).show()
                                selectedVideoForOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Report Post")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedVideoForOptions = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
