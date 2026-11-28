package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.GlobalState
import com.example.model.RepositoryManager
import com.example.model.Video
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicExploreBottomSheet(video: Video, onDismiss: () -> Unit, onUseAudio: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var relatedVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showMusicSavers by remember { mutableStateOf(false) }
    var musicSavesCount by remember { mutableStateOf(0) }
    var isMusicSaved by remember { mutableStateOf(false) }
    val currentUserId = RepositoryManager.getCurrentUserId()
    
    val musicId = video.musicTrackUrl ?: "audio_${video.id}"
    
    LaunchedEffect(musicId) {
        try {
            val likesSnap = RepositoryManager.firestore.collection("music_saves").document(musicId).collection("likes").get().await()
            musicSavesCount = likesSnap.size()
            isMusicSaved = likesSnap.documents.any { it.id == currentUserId }
        } catch(e: Exception) {}
    }

    LaunchedEffect(video.musicTrackUrl, video.musicTrack) {
        try {
            val query = RepositoryManager.firestore.collection("posts")
            
            // Try matching by musicTrack (name) or musicTrackUrl if available
            val snapshot = if (video.musicTrackUrl != null) {
                query.whereEqualTo("music_track_url", video.musicTrackUrl).get().await()
            } else {
                query.whereEqualTo("music_track", video.musicTrack).get().await()
            }
            
            val rawVideos = snapshot.documents.mapNotNull { doc ->
                val v = try {
                    doc.toObject(com.example.model.VideoDto::class.java)?.copy(id = doc.id)
                } catch(e: Exception) {
                    com.example.model.VideoDto(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        videoUrl = doc.getString("videoUrl") ?: doc.getString("postUrl") ?: doc.getString("fileUrl") ?: "",
                        description = doc.getString("description") ?: doc.getString("caption") ?: "",
                        likesCount = (doc.getLong("likesCount") ?: 0L).toInt(),
                        commentsCount = (doc.getLong("commentsCount") ?: 0L).toInt()
                    )
                }
                v
            }
            
            val fullVideos = rawVideos.mapNotNull { dto ->
                try {
                    val userDoc = RepositoryManager.firestore.collection("users").document(dto.userId).get().await()
                    if (!userDoc.exists()) return@mapNotNull null
                    val user = com.example.model.User(
                        id = userDoc.id,
                        username = userDoc.getString("username") ?: "User",
                        avatarUrl = userDoc.getString("profilePicUrl") ?: userDoc.getString("avatar_url") ?: "https://api.dicebear.com/7.x/avataaars/png?seed=${userDoc.id}",
                        isVerified = userDoc.getBoolean("isVerified") ?: false
                    )
                    Video(
                        id = dto.id,
                        userId = dto.userId,
                        user = user,
                        videoUrl = dto.videoUrl,
                        description = dto.description,
                        likesCount = dto.likesCount,
                        viewsCount = dto.viewsCount,
                        commentsCount = dto.commentsCount,
                        musicTrack = dto.musicTrack ?: "Original Audio",
                        musicTrackUrl = dto.musicTrackUrl,
                        musicTrackImageUrl = dto.musicTrackImageUrl
                    )
                } catch (e: Exception) {
                    null
                }
            }
            relatedVideos = fullVideos
        } catch (e: Exception) {
            android.util.Log.e("MusicExplore", "Failed to fetch related videos", e)
        } finally {
            isLoading = false
        }
    }
    
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121212),
        modifier = Modifier.fillMaxHeight(0.95f),
        dragHandle = { 
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(5.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color(0xFF151515))
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(28.dp).clickable { onDismiss() })
                Spacer(modifier = Modifier.width(24.dp))
                Text("Audio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Share Audio",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            com.example.utils.SoundManager.playClickSound()
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Listen to ${video.musicTrack ?: "Audio"} on Social India")
                                putExtra(android.content.Intent.EXTRA_TEXT, "Listen to '${video.musicTrack ?: "Audio"}' on Social India: ${video.musicTrackUrl ?: "https://socialindia.app"}")
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Audio"))
                        }
                )
                Spacer(modifier = Modifier.width(20.dp))
                Icon(
                    if (isMusicSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Save",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp).clickable { 
                        com.example.utils.SoundManager.playClickSound()
                        isMusicSaved = !isMusicSaved
                        Toast.makeText(context, if (isMusicSaved) "Audio saved" else "Audio removed from saved", Toast.LENGTH_SHORT).show()
                    }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            com.example.utils.SoundManager.playClickSound()
                            Toast.makeText(context, "Audio details: ${video.musicTrack ?: "Original Audio"}", Toast.LENGTH_SHORT).show()
                        }
                )
            }

            // Main Song Info Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large Artwork
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A2A2A))
                ) {
                    AsyncImage(
                        model = video.musicTrackImageUrl ?: video.user.avatarUrl,
                        contentDescription = "Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.musicTrack,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = video.user.avatarUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier.size(16.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = video.user.username,
                            color = Color.White.copy(alpha=0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (video.user.isVerified || video.user.verificationStatus == "approved" || video.user.verificationStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE) {
                            Spacer(modifier = Modifier.width(4.dp))
                            VerificationBadge(user = video.user, modifier = Modifier.size(14.dp), showDialogOnClick = false)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${relatedVideos.size.coerceAtLeast(1)} reels",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Interactive Play / Pause audio preview button
                var isPlayingPreview by remember { mutableStateOf(false) }
                DisposableEffect(Unit) {
                    onDispose {
                        if (isPlayingPreview) {
                            com.example.utils.GlobalAudioPreviewManager.stop()
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            val playUrl = video.musicTrackUrl?.takeIf { it.isNotBlank() } ?: video.videoUrl
                            if (playUrl.isNotBlank()) {
                                isPlayingPreview = !isPlayingPreview
                                if (isPlayingPreview) {
                                    com.example.utils.GlobalAudioPreviewManager.playOrToggle(context, playUrl)
                                } else {
                                    com.example.utils.GlobalAudioPreviewManager.stop()
                                }
                            } else {
                                Toast.makeText(context, "Audio preview unavailable", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlayingPreview) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = if (isPlayingPreview) "Stop Preview" else "Play Preview",
                        tint = Color.Black
                    )
                }
            }
            
            // Use Audio Button
            Button(
                onClick = {
                    com.example.utils.GlobalAudioPreviewManager.stop()
                    Toast.makeText(context, "Selected audio: ${video.musicTrack}", Toast.LENGTH_SHORT).show()
                    onUseAudio()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C68D7)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Use audio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Grid of videos
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (relatedVideos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No reels found for this audio.", color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 60.dp)
                ) {
                    items(relatedVideos, key = { it.id }) { reel ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(9f / 16f)
                                .border(0.5.dp, Color.Black)
                                .clickable {
                                    com.example.utils.GlobalAudioPreviewManager.stop()
                                    onDismiss()
                                }
                        ) {
                            AsyncImage(
                                model = reel.videoUrl, 
                                contentDescription = "Thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Play count bottom left
                            Row(
                                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Text("183K", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
