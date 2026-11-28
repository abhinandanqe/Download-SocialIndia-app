package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.model.User
import com.example.model.Video
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerOverlay(
    videos: List<Video>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    androidx.activity.compose.BackHandler { onDismiss() }
    if (videos.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val safeInitialPage = initialIndex.coerceIn(0, videos.lastIndex)
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = safeInitialPage, pageCount = { videos.size })
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.foundation.pager.VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val video = videos.getOrNull(page) ?: return@VerticalPager
            val isCurrentPage = pagerState.currentPage == page
            
            var exoPlayer by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
            
            DisposableEffect(video.videoUrl, isCurrentPage) {
                var player: androidx.media3.exoplayer.ExoPlayer? = null
                if (isCurrentPage) {
                    player = com.example.utils.ExoPlayerUtils.createExoPlayer(context).apply {
                        var playUrl = video.videoUrl
                        if (playUrl.contains("drive.google.com/uc") && !playUrl.contains("confirm=t")) {
                            playUrl += "&confirm=t"
                        }
                        val mediaItem = MediaItem.Builder().setUri(android.net.Uri.parse(playUrl)).build()
                        setMediaItem(mediaItem)
                        prepare()
                        playWhenReady = true
                        repeatMode = Player.REPEAT_MODE_ONE
                    }
                    exoPlayer = player
                }
                onDispose {
                    com.example.utils.ExoPlayerUtils.safeRelease(player)
                    exoPlayer = null
                }
            }
            Box(
                modifier = Modifier.fillMaxSize().clickable { onDismiss() }
            ) {
                AndroidView(
                    factory = { ctx ->
                        com.example.utils.ExoPlayerUtils.createTexturePlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                    },
                    update = { view ->
                        if (view.player != exoPlayer) {
                            view.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Overlay controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text("@${video.user.username}", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(video.description, color = Color.White, fontSize = 14.sp)
                }
            }
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun DiscoveryCard(
    video: Video,
    onCreatorClick: (User) -> Unit = {},
    onAvatarClick: (User) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { com.example.utils.SoundManager.playClickSound();  onCreatorClick(video.user) },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .background(Color.DarkGray)
            ) {
                val isImage = com.example.utils.MediaResolver.isImage(video.videoUrl)
                val thumbUrl = com.example.utils.MediaResolver.resolveVideoUrl(video.videoUrl)
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(thumbUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (!isImage) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // Multi image overlay icon top right if applicable
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Photo Mode",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Description/Title with hashtags colored
                Text(
                    text = video.description,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Bottom creator row with likes matching Image 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { com.example.utils.SoundManager.playClickSound(); onAvatarClick(video.user) }
                    ) {
                        AsyncImage(
                            model = video.user.avatarUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.LightGray),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = video.user.username.replace("@", ""),
                            color = Color.Gray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (video.user.isVerified) {
                            Spacer(modifier = Modifier.width(2.dp))
                            VerificationBadge(user = video.user, modifier = Modifier.size(10.dp))
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = "Likes",
                            tint = Color.Gray,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = com.example.utils.NumberFormatUtils.formatCount(video.likesCount),
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}


