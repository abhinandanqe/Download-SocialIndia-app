package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.SocialIndiaAnimatedLogo
import com.example.ui.components.SocialIndiaBrandDots
import com.example.utils.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val context = LocalContext.current
    var startAnimation by remember { mutableStateOf(false) }

    // Spring scale entrance for the application icon
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logo_scale"
    )

    // Smooth alpha fade-in
    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "logo_alpha"
    )

    // Text elements staggered entrance
    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 900, delayMillis = 250),
        label = "text_alpha"
    )

    val taglineOffsetY by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 18f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tagline_offset"
    )

    LaunchedEffect(Unit) {
        try {
            SoundManager.playSplashSound(context)
        } catch (_: Exception) {}
        startAnimation = true
        // Pre-warm Firestore posts query in background while splash animates so Home Feed opens instantly
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.example.model.RepositoryManager.firestore.collection("posts")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(10)
                    .get()
            } catch (_: Exception) {}
        }
        delay(1100)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            // Official Social India Application Icon with glowing ambient pulse
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                    }
                    .alpha(logoAlpha)
            ) {
                SocialIndiaAnimatedLogo(
                    size = 118.dp,
                    showGlow = true,
                    isAnimated = true
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // App Name: Social India
            Text(
                text = "Social India",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.alpha(textAlpha)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline: Connect • Share • Celebrate
            Text(
                text = "Connect • Share • Celebrate",
                color = Color(0xFFB0B3BC),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .graphicsLayer {
                        translationY = taglineOffsetY
                    }
                    .alpha(textAlpha)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Pulsing Brand Loader Dots
            Box(
                modifier = Modifier.alpha(textAlpha)
            ) {
                SocialIndiaBrandDots(
                    dotSize = 7.dp,
                    spacing = 8.dp
                )
            }
        }

        // Bottom Footer Badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .alpha(textAlpha)
        ) {
            Text(
                text = "MADE IN INDIA",
                color = Color(0xFF6B7280),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
        }
    }
}
