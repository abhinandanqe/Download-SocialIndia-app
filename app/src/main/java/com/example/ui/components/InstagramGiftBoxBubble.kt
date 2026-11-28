package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MessageEntity
import com.example.utils.SoundManager

/**
 * Instagram-style interactive Gift Box Message bubble.
 * Renders as a 3D wrapped ribbon gift box that animates and unboxes when tapped,
 * revealing the secret message and bonus coins.
 */
@Composable
fun InstagramGiftBoxBubble(
    message: MessageEntity,
    isMine: Boolean,
    modifier: Modifier = Modifier,
    onClaimCoins: (Int) -> Unit = {}
) {
    var isUnwrapped by rememberSaveable(message.id) { mutableStateOf(false) }
    var hasClaimedCoins by rememberSaveable(message.id) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    // Box Style configuration based on mediaUrl (style key)
    val boxStyle = remember(message.mediaUrl) {
        when (message.mediaUrl?.lowercase()) {
            "diamond_blue" -> GiftBoxStyle(
                boxGradient = listOf(Color(0xFF0072FF), Color(0xFF00C6FF)),
                ribbonColor = Color(0xFFFFFFFF),
                accentColor = Color(0xFF00E5FF),
                name = "Diamond Sparkle",
                bowEmoji = "💎"
            )
            "romantic_pink" -> GiftBoxStyle(
                boxGradient = listOf(Color(0xFFE91E63), Color(0xFFFF4081)),
                ribbonColor = Color(0xFFFFD1DC),
                accentColor = Color(0xFFFF80AB),
                name = "Pink Romance",
                bowEmoji = "💖"
            )
            "royal_gold" -> GiftBoxStyle(
                boxGradient = listOf(Color(0xFFB8860B), Color(0xFFFFD700)),
                ribbonColor = Color(0xFF4A148C),
                accentColor = Color(0xFFFFE082),
                name = "Royal Gold",
                bowEmoji = "👑"
            )
            else -> GiftBoxStyle( // Classic Red Ribbon
                boxGradient = listOf(Color(0xFFC62828), Color(0xFFE53935)),
                ribbonColor = Color(0xFFFFD700),
                accentColor = Color(0xFFFFEB3B),
                name = "Classic Crimson",
                bowEmoji = "🎀"
            )
        }
    }

    val coinsAttached = remember(message.thumbnailUrl) {
        message.thumbnailUrl?.toIntOrNull() ?: 0
    }

    // Unwrapping lid spring animation
    val lidOffset by animateFloatAsState(
        targetValue = if (isUnwrapped) -45f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "lidOffset"
    )
    val lidRotation by animateFloatAsState(
        targetValue = if (isUnwrapped) -18f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "lidRotation"
    )

    // Pulsing sparkle animation when still wrapped
    val infiniteTransition = rememberInfiniteTransition(label = "wrapped_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (!isUnwrapped) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = modifier
            .padding(4.dp)
            .widthIn(min = 210.dp, max = 270.dp)
            .clickable {
                try {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    SoundManager.playClickSound()
                } catch (e: Exception) {}
                val willBeUnwrapped = !isUnwrapped
                isUnwrapped = willBeUnwrapped
                if (willBeUnwrapped && !hasClaimedCoins && coinsAttached > 0) {
                    hasClaimedCoins = true
                    onClaimCoins(coinsAttached)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isUnwrapped) {
            // --- WRAPPED GIFT BOX (INSTAGRAM STYLE) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(pulseScale)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(boxStyle.boxGradient))
                    .border(1.dp, boxStyle.ribbonColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background Ribbon Cross
                Box(
                    modifier = Modifier
                        .matchParentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Vertical ribbon
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(18.dp)
                            .background(boxStyle.ribbonColor.copy(alpha = 0.85f))
                    )
                    // Horizontal ribbon
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .background(boxStyle.ribbonColor.copy(alpha = 0.85f))
                    )
                }

                // Center Bow & Interactive Tap prompt
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    // Ribbon Bow
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, boxStyle.accentColor),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = boxStyle.bowEmoji, fontSize = 26.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "🎁 Tap to unwrap",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (coinsAttached > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFD700).copy(alpha = 0.25f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                CoinIcon(size = 12.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "+$coinsAttached Coins Inside",
                                    color = Color(0xFFFFD700),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // --- UNWRAPPED REVEALED MESSAGE CARD ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF26181C), Color(0xFF1F1B24))
                        )
                    )
                    .border(
                        1.5.dp,
                        Brush.horizontalGradient(
                            listOf(boxStyle.accentColor, Color(0xFFFFD700), boxStyle.accentColor)
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Top Unwrapped Banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = boxStyle.accentColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "✨ Unwrapped 🎁",
                                fontSize = 11.sp,
                                color = boxStyle.accentColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (coinsAttached > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CoinIcon(size = 12.dp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "+$coinsAttached Claimed!",
                                    color = Color(0xFFFFD700),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secret Message Content with quotation styling
                    Text(
                        text = "\"${message.message}\"",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Tap to re-wrap 🎀",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

data class GiftBoxStyle(
    val boxGradient: List<Color>,
    val ribbonColor: Color,
    val accentColor: Color,
    val name: String,
    val bowEmoji: String
)
