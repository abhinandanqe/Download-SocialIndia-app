package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.example.api.ITunesTrack
import com.example.api.MusicClient
import com.example.utils.SoundManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPickerSheet(
    onDismiss: () -> Unit,
    onTrackSelected: (ITunesTrack) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var musicTracks by remember { mutableStateOf<List<ITunesTrack>>(emptyList()) }
    var isLoadingMusic by remember { mutableStateOf(false) }
    var playingTrackId by remember { mutableStateOf<Long?>(null) }
    var activePlayingTrack by remember { mutableStateOf<ITunesTrack?>(null) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(30000L) }
    var isPlayerPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val previewExoPlayer = remember {
        try {
            com.example.utils.ExoPlayerUtils.createExoPlayer(context, isMusic = true).apply {
                repeatMode = ExoPlayer.REPEAT_MODE_ONE
            }
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(previewExoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == androidx.media3.common.Player.STATE_BUFFERING
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    val dur = previewExoPlayer?.duration ?: 30000L
                    if (dur > 0) totalDurationMs = dur
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayerPlaying = isPlaying
            }
        }
        previewExoPlayer?.addListener(listener)
        onDispose {
            previewExoPlayer?.removeListener(listener)
            com.example.utils.ExoPlayerUtils.safeRelease(previewExoPlayer)
        }
    }

    LaunchedEffect(isPlayerPlaying, playingTrackId) {
        while (isPlayerPlaying && playingTrackId != null) {
            currentPositionMs = previewExoPlayer?.currentPosition ?: 0L
            val dur = previewExoPlayer?.duration ?: 0L
            if (dur > 0) totalDurationMs = dur
            delay(200)
        }
    }

    // Local device audio picker
    val audioFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val localTrack = MusicClient.createTrackFromLocalUri(context, uri)
                previewExoPlayer?.stop()
                playingTrackId = null
                onTrackSelected(localTrack)
                Toast.makeText(context, "Selected: ${localTrack.name}", Toast.LENGTH_SHORT).show()
                onDismiss()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load audio: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Initial Load & Category Filter
    fun loadTracks(category: String, query: String = "") {
        searchJob?.cancel()
        // Immediately show curated tracks so user never sees a blank screen
        val fallback = MusicClient.searchFallback(query, category)
        if (musicTracks.isEmpty() || query.isBlank()) {
            musicTracks = fallback
        }
        searchJob = scope.launch {
            if (musicTracks.isEmpty()) {
                isLoadingMusic = true
            }
            try {
                val response = MusicClient.searchIndianTracks(query, category)
                if (response.results.isNotEmpty()) {
                    musicTracks = response.results
                } else if (musicTracks.isEmpty()) {
                    musicTracks = fallback
                }
            } catch (e: Exception) {
                if (musicTracks.isEmpty()) {
                    musicTracks = fallback
                }
            } finally {
                isLoadingMusic = false
            }
        }
    }

    LaunchedEffect(selectedCategoryIndex) {
        val cat = MusicClient.categories.getOrElse(selectedCategoryIndex) { "🔥 Trending" }
        loadTracks(cat, searchQuery)
    }

    Dialog(
        onDismissRequest = {
            previewExoPlayer?.stop()
            playingTrackId = null
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler {
            previewExoPlayer?.stop()
            playingTrackId = null
            onDismiss()
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF121212)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Header with Back Button and Import
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            previewExoPlayer?.stop()
                            playingTrackId = null
                            onDismiss()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = "Audio",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }

                    // Pick from Device Button
                    Surface(
                        onClick = {
                            SoundManager.playClickSound()
                            audioFilePickerLauncher.launch("audio/*")
                        },
                        color = Color(0xFF282828),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

            // Search Bar
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        searchJob?.cancel()
                        searchJob = scope.launch {
                            delay(400)
                            val cat = MusicClient.categories.getOrElse(selectedCategoryIndex) { "🔥 Trending" }
                            loadTracks(cat, query)
                        }
                    },
                    placeholder = { Text("Search songs or artists", color = Color(0xFF888888), fontSize = 15.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = ""; loadTracks(MusicClient.categories[selectedCategoryIndex]) }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF222222),
                        unfocusedContainerColor = Color(0xFF222222),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category Chips Row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                itemsIndexed(MusicClient.categories) { index, categoryTitle ->
                    val isSelected = selectedCategoryIndex == index
                    Surface(
                        onClick = {
                            SoundManager.playClickSound()
                            selectedCategoryIndex = index
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF1DB954) else Color(0xFF2A2A2A),
                        border = if (!isSelected) BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null,
                        modifier = Modifier.height(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                            Text(
                                text = categoryTitle,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoadingMusic && musicTracks.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color(0xFF1DB954),
                    trackColor = Color.Transparent
                )
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Track List
            if (isLoadingMusic && musicTracks.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF1DB954), strokeWidth = 3.dp)
                }
            } else if (musicTracks.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.MusicOff, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No music found", color = Color.Gray, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    itemsIndexed(musicTracks) { index, track ->
                        val isPlaying = playingTrackId == track.id
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    SoundManager.playClickSound()
                                    if (isPlaying) {
                                        previewExoPlayer?.stop()
                                        playingTrackId = null
                                        activePlayingTrack = null
                                    } else {
                                        val streamUrl = track.previewUrl
                                        if (!streamUrl.isNullOrEmpty()) {
                                            playingTrackId = track.id
                                            activePlayingTrack = track
                                            currentPositionMs = 0L
                                            previewExoPlayer?.apply {
                                                stop()
                                                setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
                                                prepare()
                                                play()
                                            }
                                        } else {
                                            Toast.makeText(context, "Audio preview not available for this track", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Artwork with Play/Pause overlay
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = track.artworkUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                
                                // Dark overlay for better icon visibility
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                                
                                if (isPlaying && isBuffering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color(0xFF1DB954),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.name ?: "Unknown",
                                    color = if (isPlaying) Color(0xFF1DB954) else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = track.artistName ?: "Unknown Artist",
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            // Select / Use Audio button directly from item
                            IconButton(
                                onClick = {
                                    SoundManager.playClickSound()
                                    previewExoPlayer?.stop()
                                    onTrackSelected(track)
                                    onDismiss()
                                }
                            ) {
                                Icon(
                                    Icons.Default.AddCircle,
                                    contentDescription = "Select Audio",
                                    tint = if (isPlaying) Color(0xFF1DB954) else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Mini Player Bar (Instagram Style)
            AnimatedVisibility(
                visible = activePlayingTrack != null,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                activePlayingTrack?.let { currentTrack ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF222222),
                        border = BorderStroke(1.dp, Color(0xFF1DB954).copy(alpha = 0.5f)),
                        shadowElevation = 8.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = currentTrack.artworkUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (isBuffering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color(0xFF1DB954),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentTrack.name ?: "Unknown Song",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentTrack.artistName ?: "Unknown Artist",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Play / Pause
                                IconButton(
                                    onClick = {
                                        if (isPlayerPlaying) {
                                            previewExoPlayer?.pause()
                                        } else {
                                            previewExoPlayer?.play()
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlayerPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                        contentDescription = if (isPlayerPlaying) "Pause" else "Play",
                                        tint = Color(0xFF1DB954),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                // Use / Select Button
                                Button(
                                    onClick = {
                                        SoundManager.playClickSound()
                                        previewExoPlayer?.stop()
                                        onTrackSelected(currentTrack)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Use", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Close Player
                                IconButton(
                                    onClick = {
                                        previewExoPlayer?.stop()
                                        playingTrackId = null
                                        activePlayingTrack = null
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Seek Bar & Timers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val currentSec = (currentPositionMs / 1000).coerceAtLeast(0)
                                val totalSec = (totalDurationMs / 1000).coerceAtLeast(1)
                                Text(
                                    text = String.format(java.util.Locale.US, "%d:%02d", currentSec / 60, currentSec % 60),
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                                Slider(
                                    value = currentPositionMs.toFloat().coerceIn(0f, totalDurationMs.toFloat().coerceAtLeast(1f)),
                                    onValueChange = { newPos ->
                                        currentPositionMs = newPos.toLong()
                                        previewExoPlayer?.seekTo(newPos.toLong())
                                    },
                                    valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(20.dp)
                                        .padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF1DB954),
                                        activeTrackColor = Color(0xFF1DB954),
                                        inactiveTrackColor = Color.DarkGray
                                    )
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%d:%02d", totalSec / 60, totalSec % 60),
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}
