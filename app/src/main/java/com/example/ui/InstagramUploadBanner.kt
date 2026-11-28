package com.example.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.GlobalUploadManager
import com.example.model.UploadStatus

@Composable
fun InstagramUploadBanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uploadState by GlobalUploadManager.currentUpload.collectAsStateWithLifecycle()
    val isPostingStory by com.example.ui.GlobalState.isPostingStory.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = uploadState != null || isPostingStory,
        enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(animationSpec = tween(300)),
        exit = shrinkVertically(animationSpec = tween(400)) + fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        val upload = uploadState
        if (upload == null && !isPostingStory) return@AnimatedVisibility

        val animatedProgress by animateFloatAsState(
            targetValue = upload?.progress ?: 0.5f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "upload_progress"
        )

        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val shimmerAlpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shimmer"
        )

        val displayThumbnail = upload?.thumbnailUri ?: if (isPostingStory) "https://api.dicebear.com/7.x/avataaars/png?seed=story" else null
        val displayCaption = if (isPostingStory) "Uploading Story..." else (if (upload?.caption?.isNotBlank() == true) upload?.caption ?: "New Post" else "New Post")
        val isVideoDisplay = upload?.isVideo == true || isPostingStory
        val displayStatusText = if (isPostingStory) "Preparing..." else upload?.statusText ?: ""
        val isSuccess = upload?.status == UploadStatus.SUCCESS
        val isError = upload?.status == UploadStatus.ERROR
        val animatedProgressToUse = if (isPostingStory) 0.5f else animatedProgress
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .shadow(8.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF262626),
                            Color(0xFF1A1A1A)
                        )
                    )
                )
                .border(1.dp, Color(0xFF3E3E3E), RoundedCornerShape(14.dp))
                .clickable {
                    if (isError || isSuccess) {
                        GlobalUploadManager.dismissUpload()
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Thumbnail preview
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (displayThumbnail != null) {
                        AsyncImage(
                            model = displayThumbnail,
                            contentDescription = "Upload Thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (isVideoDisplay) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Details & Progress Column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = displayCaption,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Status pill / percentage
                        when {
                            isSuccess == true -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Completed",
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Posted!",
                                        color = Color(0xFF00E676),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            isError == true -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Error",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Failed",
                                        color = Color(0xFFFF5252),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = "${(animatedProgressToUse * 100).toInt()}%",
                                    color = Color.White.copy(alpha = shimmerAlpha),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = displayStatusText,
                        color = when {
                            isSuccess == true -> Color(0xFFB9F6CA)
                            isError == true -> Color(0xFFFF8A80)
                            else -> Color(0xFFAAAAAA)
                        },
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress bar
                    if (!isSuccess && !isError) {
                        LinearProgressIndicator(
                            progress = { animatedProgressToUse },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFFEE1D52),
                            trackColor = Color(0xFF333333)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Retry and Dismiss controls for error state
                if (isError) {
                    if (upload?.canRetry == true) {
                        IconButton(
                            onClick = { GlobalUploadManager.retryLastUpload(context) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("upload_retry_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry upload",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { GlobalUploadManager.dismissUpload() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("upload_dismiss_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
