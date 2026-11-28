package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.example.model.AudioSound
import com.example.model.RepositoryManager
import com.example.model.SoundCategory
import com.example.model.Video
import com.example.utils.NumberFormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDetailsScreen(
    trackId: String,
    trackName: String,
    artistName: String,
    trackImageUrl: String,
    onBack: () -> Unit,
    onUseAudio: (String, String, String, String, String) -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var soundDetails by remember { mutableStateOf<AudioSound?>(null) }
    var videosUsingAudio by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaved by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }

    // Audio Playback State
    var isPlayingAudio by remember { mutableStateOf(false) }
    var playbackPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(30000L) }
    var audioPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Vinyl Rotation Animation
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Load Data
    LaunchedEffect(trackId, trackName, artistName, trackImageUrl) {
        isLoading = true
        try {
            val details = RepositoryManager.socialRepository.getSoundDetails(
                soundId = trackId,
                fallbackTitle = trackName,
                fallbackArtist = artistName,
                fallbackCover = trackImageUrl
            )
            soundDetails = details
            isSaved = details.isSaved

            val videos = RepositoryManager.socialRepository.getVideosForSound(
                soundId = details.id.ifEmpty { trackId },
                soundTitle = details.title.ifEmpty { trackName },
                limit = 60
            )
            videosUsingAudio = videos
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    // Audio Player Lifecycle & Progress Tracker
    LaunchedEffect(soundDetails?.audioUrl) {
        val audioUrl = soundDetails?.audioUrl ?: ""
        if (audioUrl.isNotEmpty()) {
            try {
                val player = com.example.utils.ExoPlayerUtils.createExoPlayer(context, isMusic = true).apply {
                    val resolved = com.example.utils.MediaResolver.resolveVideoUrl(audioUrl)
                    setMediaItem(MediaItem.fromUri(resolved))
                    prepare()
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            isPlayingAudio = isPlaying
                        }
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                totalDurationMs = duration.coerceAtLeast(1000L)
                            } else if (playbackState == Player.STATE_ENDED) {
                                isPlayingAudio = false
                                seekTo(0)
                                pause()
                            }
                        }
                    })
                }
                audioPlayer = player
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Progress Polling loop
    LaunchedEffect(isPlayingAudio) {
        while (isPlayingAudio) {
            audioPlayer?.let { p ->
                playbackPositionMs = p.currentPosition
                if (p.duration > 0) totalDurationMs = p.duration
            }
            delay(200)
        }
    }

    // Cleanup Audio Player on screen exit or lifecycle pause
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                audioPlayer?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            com.example.utils.ExoPlayerUtils.safeRelease(audioPlayer)
            audioPlayer = null
        }
    }

    val currentSound = soundDetails ?: AudioSound(
        id = trackId,
        title = trackName.ifEmpty { "Original Audio" },
        artist = artistName.ifEmpty { "Social India" },
        coverImageUrl = trackImageUrl
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Audio Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
                actions = {
                    // Share Audio
                    IconButton(onClick = { showShareSheet = true }) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Save / Bookmark Audio
                    IconButton(onClick = {
                        val currentUserId = RepositoryManager.getCurrentUserId()
                        if (currentUserId.isNotEmpty()) {
                            scope.launch {
                                val newSaved = RepositoryManager.socialRepository.toggleSaveSound(currentUserId, currentSound)
                                isSaved = newSaved
                                Toast.makeText(
                                    context,
                                    if (newSaved) "Saved to audio library" else "Removed from saved audio",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(context, "Please login to save sounds", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Save Sound",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Hero Sound Header Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Vinyl Artwork Disc
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1E1E))
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable {
                                if (audioPlayer != null) {
                                    if (isPlayingAudio) audioPlayer?.pause() else audioPlayer?.play()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Rotating Vinyl Grooves & Artwork
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .rotate(if (isPlayingAudio) vinylRotation else 0f)
                        ) {
                            AsyncImage(
                                model = currentSound.coverImageUrl.takeIf { it.isNotBlank() },
                                contentDescription = "Audio Art",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Vinyl center ring
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.Center)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.8f))
                                    .border(2.dp, Color.White, CircleShape)
                            )
                        }

                        // Play/Pause Overlay
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingAudio) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Sound Title & Creator Details
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSound.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))

                        // Creator Info
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    if (currentSound.creatorId.isNotEmpty()) {
                                        onNavigateToProfile(currentSound.creatorId)
                                    }
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = if (currentSound.category == SoundCategory.LICENSED_MUSIC) currentSound.artist else "@${currentSound.creatorUsername}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (currentSound.creatorIsVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF1DA1F2),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Trending Status / Tag
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (currentSound.isTrending && currentSound.trendingRank != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFFF3E0),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "🔥 Trending #${currentSound.trendingRank}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100)
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = if (currentSound.category == SoundCategory.LICENSED_MUSIC) "🎧 Song" else "🎵 Original Sound",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "${NumberFormatUtils.formatCount(videosUsingAudio.size.coerceAtLeast(currentSound.reelsCount))} Reels",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                            )
                        }
                    }
                }

                // Audio Playback Seekbar & Timestamps
                if (audioPlayer != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = (playbackPositionMs.toFloat() / totalDurationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f),
                            onValueChange = { fraction ->
                                val newPos = (fraction * totalDurationMs).toLong()
                                audioPlayer?.seekTo(newPos)
                                playbackPositionMs = newPos
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTimeMs(playbackPositionMs),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            Text(
                                text = formatTimeMs(totalDurationMs),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Bar: "Use Sound" Hero CTA
                Button(
                    onClick = {
                        audioPlayer?.pause()
                        onUseAudio(
                            currentSound.id,
                            currentSound.title,
                            currentSound.artist,
                            currentSound.coverImageUrl,
                            currentSound.audioUrl
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .shadow(4.dp, RoundedCornerShape(24.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        Icons.Default.VideoCall,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Use Sound",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )

            // Section Header: Reels Using this sound
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reels using this sound",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "${videosUsingAudio.size} total",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            // Grid of Reels using this sound
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (videosUsingAudio.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicVideo,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No Reels have used this sound yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Be the first creator to make a reel with this audio!",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                audioPlayer?.pause()
                                onUseAudio(
                                    currentSound.id,
                                    currentSound.title,
                                    currentSound.artist,
                                    currentSound.coverImageUrl,
                                    currentSound.audioUrl
                                )
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Reel", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    itemsIndexed(videosUsingAudio, key = { index, video -> "${video.id}_$index" }) { _, video ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(9f / 16f)
                                .background(Color.DarkGray)
                                .clickable {
                                    audioPlayer?.pause()
                                    onNavigateToVideo(video.id)
                                }
                        ) {
                            AsyncImage(
                                model = video.videoUrl,
                                contentDescription = "Reel thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Gradient Scrim
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                            startY = 200f
                                        )
                                    )
                            )

                            // View Count
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = NumberFormatUtils.formatCount(video.viewsCount),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Creator mini avatar
                            AsyncImage(
                                model = video.user.avatarUrl.takeIf { it.isNotBlank() },
                                contentDescription = "Creator Avatar",
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color.White, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        val activeSound = soundDetails
        if (showShareSheet && activeSound != null) {
            val snd = activeSound
            val payload = com.example.model.SharePayload.fromAudio(
                audioId = snd.id,
                title = snd.title,
                artist = snd.artist,
                coverUrl = snd.coverImageUrl,
                audioUrl = snd.audioUrl
            )
            ModernShareBottomSheet(
                payload = payload,
                onDismiss = { showShareSheet = false },
                onAddToStory = {
                    showShareSheet = false
                    onUseAudio(snd.id, snd.title, snd.artist, snd.coverImageUrl, snd.audioUrl)
                }
            )
        }
    }
}

private fun formatTimeMs(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
