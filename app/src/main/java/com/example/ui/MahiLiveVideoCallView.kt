package com.example.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.ai.MahiCallState
import com.example.viewmodel.MahiAiViewModel

@Composable
fun MahiLiveVideoCallView(
    viewModel: MahiAiViewModel,
    onEndCall: () -> Unit,
    onSwitchToVoice: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val callState by viewModel.callState.collectAsState()
    val durationSec by viewModel.callDurationSeconds.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val userLiveSpeech by viewModel.userLiveSpeech.collectAsState()
    val aiLiveSpeech by viewModel.aiLiveSpeech.collectAsState()
    val isMahiSpeaking by viewModel.isMahiSpeaking.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val amplitude by viewModel.audioAmplitude.collectAsState()
    val adminConfig by viewModel.adminConfig.collectAsState()

    val aiDisplayName = adminConfig.aiName.ifBlank { "Mahi AI" }
    val aiAvatar = adminConfig.aiAvatarUrl

    var isUserVideoEnabled by remember { mutableStateOf(true) }

    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "video_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isMahiSpeaking) 1.18f else if (isListening) 1.08f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val auraRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing)
        ),
        label = "rot"
    )

    val formattedDuration = remember(durationSec) {
        val minutes = durationSec / 60
        val seconds = durationSec % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090714))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // --- Mahi's Video Persona Background / Main Stage ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF190F2E),
                            Color(0xFF0B0718),
                            Color(0xFF000000)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Animated Holographic Presence
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Holographic outer aura
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color(0xFF00E5FF).copy(alpha = 0.35f),
                                        Color(0xFFFF007F).copy(alpha = 0.4f),
                                        Color(0xFF7A00FF).copy(alpha = 0.35f),
                                        Color(0xFF00E5FF).copy(alpha = 0.35f)
                                    )
                                )
                            )
                    )

                    // Core Avatar Card
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFEE1D52), Color(0xFF6200EA))
                                )
                            )
                            .border(
                                width = 3.dp,
                                brush = Brush.sweepGradient(
                                    listOf(Color(0xFF00E5FF), Color(0xFFEE1D52), Color(0xFF7A00FF), Color(0xFF00E5FF))
                                ),
                                shape = CircleShape
                            )
                            .padding(4.dp),
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
                                Text("🌸", fontSize = 72.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = aiDisplayName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                // Live Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isMahiSpeaking) Color(0xFFFF4081) else if (isListening) Color(0xFF00E5FF) else Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val statusLabel = when (callState) {
                        MahiCallState.CONNECTING -> "Connecting..."
                        MahiCallState.LISTENING -> "Listening (Boliyen)..."
                        MahiCallState.THINKING -> "Thinking..."
                        MahiCallState.SPEAKING -> "Speaking..."
                        else -> "Live Video Session"
                    }
                    Text(
                        text = statusLabel,
                        color = if (isListening) Color(0xFF00E5FF) else if (isMahiSpeaking) Color(0xFFFF4081) else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- Top Bar: Duration & Controls ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEE1D52))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "HD LIVE",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Text(
                    text = formattedDuration,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            IconButton(
                onClick = onSwitchToVoice,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.PhoneInTalk,
                    contentDescription = "Switch to Audio",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // --- Picture-in-Picture User Camera Tile (Draggable / Positioned at Top Right) ---
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 70.dp, end = 16.dp)
                .width(110.dp)
                .height(150.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, Color(0xFF00E5FF).copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .background(Color.Black)
        ) {
            if (isUserVideoEnabled) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val selector = if (isFrontCamera) {
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                } else {
                                    CameraSelector.DEFAULT_BACK_CAMERA
                                }
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.VideocamOff,
                        contentDescription = "Camera Off",
                        tint = Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Camera flip mini badge
            IconButton(
                onClick = { viewModel.toggleCameraDirection() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(
                    Icons.Default.FlipCameraIos,
                    contentDescription = "Flip Camera",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // --- Bottom Subtitles & Call Control Actions ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 28.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Dual-Subtitles Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // User Live Transcript
                if (userLiveSpeech.isNotBlank()) {
                    Surface(
                        color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF00E5FF))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Aap (You)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = userLiveSpeech,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // AI Live Transcript
                if (aiLiveSpeech.isNotBlank()) {
                    Surface(
                        color = Color(0xFFEE1D52).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEE1D52).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFEE1D52))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(aiDisplayName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = aiLiveSpeech,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Video Call Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Video On/Off Toggle
                IconButton(
                    onClick = { isUserVideoEnabled = !isUserVideoEnabled },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(if (!isUserVideoEnabled) Color(0xFFE53935) else Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(
                        if (isUserVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = "Toggle Video",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Mic Mute Toggle
                IconButton(
                    onClick = { viewModel.toggleMute() },
                    modifier = Modifier
                        .size(50.dp)
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

                // Flip Camera
                IconButton(
                    onClick = { viewModel.toggleCameraDirection() },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(
                        Icons.Default.FlipCameraAndroid,
                        contentDescription = "Flip Camera",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // End Video Call Button
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF1744))
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
