package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ai.MahiCallState
import com.example.viewmodel.MahiAiViewModel

@Composable
fun MahiLiveVoiceCallView(
    viewModel: MahiAiViewModel,
    onEndCall: () -> Unit,
    onSwitchToVideo: () -> Unit
) {
    val callState by viewModel.callState.collectAsState()
    val durationSec by viewModel.callDurationSeconds.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val userLiveSpeech by viewModel.userLiveSpeech.collectAsState()
    val aiLiveSpeech by viewModel.aiLiveSpeech.collectAsState()
    val isMahiSpeaking by viewModel.isMahiSpeaking.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val amplitude by viewModel.audioAmplitude.collectAsState()
    val adminConfig by viewModel.adminConfig.collectAsState()

    val aiDisplayName = adminConfig.aiName.ifBlank { "Mahi AI" }
    val aiAvatar = adminConfig.aiAvatarUrl

    // Infinite breathing pulse for aura
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isMahiSpeaking) 1.25f else if (isListening) 1.12f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val auraRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    val formattedDuration = remember(durationSec) {
        val minutes = durationSec / 60
        val seconds = durationSec % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0F0C20),
                        Color(0xFF180E29),
                        Color(0xFF080611)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isListening || isMahiSpeaking) Color(0xFF00E5FF) else Color(0xFF4CAF50))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$aiDisplayName • LIVE VOICE SESSION",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formattedDuration,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Light
            )

            Spacer(modifier = Modifier.height(4.dp))
            val statusText = when (callState) {
                MahiCallState.CONNECTING -> "Connecting to $aiDisplayName..."
                MahiCallState.LISTENING -> "🎙️ Listening to you... (Aap boliyen)"
                MahiCallState.THINKING -> "⚡ $aiDisplayName is thinking..."
                MahiCallState.SPEAKING -> "🌸 $aiDisplayName is speaking..."
                else -> "Live Conversation Active"
            }
            Text(
                text = statusText,
                color = if (isListening) Color(0xFF00E5FF) else if (isMahiSpeaking) Color(0xFFFF4081) else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Center Pulsing Avatar & Waveform
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(190.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer Holographic Aura Rings
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFEE1D52).copy(alpha = 0.4f),
                                    Color(0xFF7A00FF).copy(alpha = 0.4f),
                                    Color(0xFF00E5FF).copy(alpha = 0.4f),
                                    Color(0xFFEE1D52).copy(alpha = 0.4f)
                                )
                            )
                        )
                )

                // Inner Glow Ring
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFEE1D52),
                                    Color(0xFF7A00FF),
                                    Color(0xFF00E5FF)
                                )
                            )
                        )
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF160D2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (aiAvatar.isNotBlank()) {
                            AsyncImage(
                                model = aiAvatar,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("🌸", fontSize = 68.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Dynamic Audio Waveform Bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barCount = 18
                for (i in 0 until barCount) {
                    val factor = kotlin.math.sin((i.toFloat() / barCount) * Math.PI).toFloat()
                    val dynamicHeight = if (isMahiSpeaking || amplitude > 0.1f) {
                        (10.dp + (34.dp * factor * (if (isMahiSpeaking) pulseScale - 0.2f else amplitude * 1.5f))).coerceIn(6.dp, 40.dp)
                    } else {
                        6.dp
                    }
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(dynamicHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF00E5FF),
                                        Color(0xFFEE1D52)
                                    )
                                )
                            )
                    )
                }
            }
        }

        // Bottom Section: Dual Real-Time Subtitles (User & AI) + Call Actions
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 28.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Real-Time Subtitles Box showing both User speech and AI speech
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // User Live Speech Transcript (Aap)
                if (userLiveSpeech.isNotBlank()) {
                    Surface(
                        color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF00E5FF))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Aap (You)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = userLiveSpeech,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // AI Live Speech Transcript (Mahi)
                if (aiLiveSpeech.isNotBlank()) {
                    Surface(
                        color = Color(0xFFEE1D52).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEE1D52).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEE1D52))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(aiDisplayName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = aiLiveSpeech,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Call Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute Mic Toggle
                IconButton(
                    onClick = { viewModel.toggleMute() },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) Color(0xFFE53935) else Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(
                        if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute Mic",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Switch to Video Call
                IconButton(
                    onClick = onSwitchToVideo,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = "Switch to Video",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Speaker Toggle
                IconButton(
                    onClick = { viewModel.toggleSpeaker() },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (isSpeakerOn) Color(0xFF7A00FF) else Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(
                        if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Speaker",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // End Call Button
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF1744))
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}
