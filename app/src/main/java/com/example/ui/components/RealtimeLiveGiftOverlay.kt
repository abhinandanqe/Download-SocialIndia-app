package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.RepositoryManager
import com.example.repository.RealtimeLiveGiftEvent
import com.example.utils.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * High-energy real-time gift animation overlay for Live Streams.
 * Shows for both Host and all Viewers when any user sends a gift in real time.
 */
@Composable
fun RealtimeLiveGiftOverlay(
    liveStoryId: String,
    modifier: Modifier = Modifier
) {
    if (liveStoryId.isBlank()) return

    val context = LocalContext.current
    val giftQueue = remember { mutableStateListOf<RealtimeLiveGiftEvent>() }
    var currentGift by remember { mutableStateOf<RealtimeLiveGiftEvent?>(null) }

    // Listen to real-time gift events from Firestore
    LaunchedEffect(liveStoryId) {
        RepositoryManager.giftRepository.observeLiveStreamGifts(liveStoryId).collectLatest { event ->
            giftQueue.add(event)
        }
    }

    // Process gift queue one by one with animated duration
    LaunchedEffect(giftQueue.size, currentGift) {
        if (currentGift == null && giftQueue.isNotEmpty()) {
            val next = giftQueue.removeAt(0)
            currentGift = next
            try {
                SoundManager.playClickSound()
            } catch (e: Exception) {}
            delay(3500)
            currentGift = null
        }
    }

    currentGift?.let { gift ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 60.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulse & Scale animation for 3D Gift GIF
            val infiniteTransition = rememberInfiniteTransition(label = "gift_pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )
            val wobbleRotation by infiniteTransition.animateFloat(
                initialValue = -5f,
                targetValue = 5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wobble"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Top Sender Banner
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFFD700), Color(0xFFFF2A6D), Color(0xFF00F0FF))
                            )
                        ),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            // Sender Avatar
                            AsyncImage(
                                model = gift.senderAvatar.ifBlank {
                                    "https://api.dicebear.com/7.x/avataaars/png?seed=${gift.senderId}"
                                },
                                contentDescription = "Sender Avatar",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, Color(0xFFFFD700), CircleShape),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = "@${gift.senderUsername}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Sent ${gift.giftName} ${gift.emoji}",
                                    color = Color(0xFFFFE082),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Coin Tag
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFD700).copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    CoinIcon(size = 14.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "+${gift.coins}",
                                        color = Color(0xFFFFD700),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }

                // Center Animated GIF / 3D Sticker
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(pulseScale)
                        .rotate(wobbleRotation),
                    contentAlignment = Alignment.Center
                ) {
                    if (gift.sticker3dUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(gift.sticker3dUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = gift.giftName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = gift.emoji,
                            fontSize = 110.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Gift Title Glow Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xCC000000),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFFFFF))
                ) {
                    Text(
                        text = "✨ ${gift.giftName} ✨",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
