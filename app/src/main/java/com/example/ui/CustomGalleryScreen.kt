package com.example.ui

import android.content.ContentUris
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaItem(val uri: android.net.Uri, val isVideo: Boolean, val duration: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomGalleryScreen(
    onMediaSelected: (List<android.net.Uri>) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var mediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectedUris by remember { mutableStateOf<Set<android.net.Uri>>(emptySet()) }
    var selectMultiple by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("All") }
    
    val filteredList = when(selectedTab) {
        "Videos" -> mediaList.filter { it.isVideo }
        "Photos" -> mediaList.filter { !it.isVideo }
        "AI gallery" -> emptyList()
        else -> mediaList
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = mutableListOf<MediaItem>()
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Video.Media.DURATION
            )
            val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=" +
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR " +
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE}=" +
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext() && list.size < 100) {
                    val id = cursor.getLong(idCol)
                    val type = cursor.getInt(typeCol)
                    val isVideo = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri("external"),
                        id
                    )
                    
                    var durationStr = ""
                    if (isVideo) {
                        val durMs = cursor.getLong(durationCol)
                        if (durMs > 0) {
                            val sec = (durMs / 1000) % 60
                            val min = (durMs / 1000) / 60
                            durationStr = String.format("%02d:%02d", min, sec)
                        }
                    }
                    list.add(MediaItem(uri, isVideo, durationStr))
                }
            }
            mediaList = list
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(28.dp).clickable { onClose() }
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.DarkGray, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Recents", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White)
            }
            
            Spacer(modifier = Modifier.size(28.dp)) // balance
        }

        // Tabs
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("All", "Videos", "Photos", "AI gallery").forEach { tab ->
                val isSelected = selectedTab == tab
                Text(
                    text = tab,
                    color = if (isSelected) Color.White else Color.Gray,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable { selectedTab = tab }.padding(8.dp)
                )
            }
        }

        // Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (filteredList.isEmpty() && selectedTab == "AI gallery") {
                item(span = { GridItemSpan(3) }) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("No AI media generated yet", color = Color.Gray)
                    }
                }
            }
            items(filteredList) { item ->
                val isSelected = selectedUris.contains(item.uri)
                Box(
                    modifier = Modifier.aspectRatio(1f).clickable {
                        if (selectMultiple) {
                            if (isSelected) {
                                selectedUris = selectedUris - item.uri
                            } else {
                                selectedUris = selectedUris + item.uri
                            }
                        } else {
                            onMediaSelected(listOf(item.uri))
                        }
                    }
                ) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(item.uri)
                            .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    if (item.isVideo) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            if (item.duration.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = item.duration,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Selector circle
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .border(1.5.dp, Color.White, CircleShape)
                            .background(if (isSelected) Color(0xFFEE1D52) else Color.Black.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Bottom Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectMultiple = !selectMultiple }) {
                Box(
                    modifier = Modifier.size(24.dp).background(if (selectMultiple) Color(0xFFEE1D52) else Color.LightGray, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectMultiple) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select multiple", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            Button(
                onClick = { onMediaSelected(selectedUris.toList()) },
                enabled = selectedUris.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.height(48.dp).padding(horizontal = 16.dp)
            ) {
                Text("Next", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
