package com.example.ui.security

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.example.auth.MultiAccountManager
import com.example.security.FaceLockManager

@Composable
fun FaceLockScreen(
    onUnlocked: () -> Unit,
    onOpenAccountSwitcher: () -> Unit
) {
    // Disable system back button to prevent lock screen bypass
    BackHandler(enabled = true) {
        // Cannot bypass lock screen with back button
    }

    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val isDark by com.example.utils.ThemePreference.isDarkTheme.collectAsState()
    val bgColor = if (isDark) Color(0xFF0D0E12) else Color(0xFFFFFFFF)
    val cardBg = if (isDark) Color(0xFF16181F) else Color(0xFFF9FAFB)
    val textColor = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111827)
    val subTextColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF262935) else Color(0xFFE5E7EB)

    val activeAccount by MultiAccountManager.activeAccount.collectAsState()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    // Pulsing animation for the biometric ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    fun triggerAuth() {
        if (activity == null) return
        isAuthenticating = true
        errorMessage = null
        FaceLockManager.authenticate(
            activity = activity,
            onSuccess = {
                isAuthenticating = false
                onUnlocked()
            },
            onError = { err ->
                isAuthenticating = false
                errorMessage = err
            }
        )
    }

    // Auto-prompt on initial launch
    LaunchedEffect(Unit) {
        triggerAuth()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top App Branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                // Social India Vibrant Gradient Logo
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFEE1D52), Color(0xFFFF5722), Color(0xFFFF9800))
                            )
                        )
                        .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Social India App Lock",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Social India",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = textColor,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Unlock Social India",
                    fontSize = 16.sp,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Face / Biometric Authentication",
                    fontSize = 13.sp,
                    color = subTextColor,
                    fontWeight = FontWeight.Medium
                )
            }

            // Center: User Profile & Biometric Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                // Active Account Profile Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    modifier = Modifier.padding(bottom = 36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val avatarUrl = activeAccount?.avatarUrl ?: ""
                        if (avatarUrl.isNotEmpty()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "Profile",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, Color(0xFFEE1D52), CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEE1D52).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (activeAccount?.username?.take(1) ?: "U").uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEE1D52),
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Column {
                            Text(
                                text = activeAccount?.displayName?.ifEmpty { null } ?: activeAccount?.username ?: "User",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = textColor
                            )
                            Text(
                                text = "@" + (activeAccount?.username ?: "social_user"),
                                fontSize = 12.sp,
                                color = subTextColor
                            )
                        }
                    }
                }

                // Interactive Biometric Pulse Button
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .scale(if (isAuthenticating) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFFEE1D52).copy(alpha = 0.2f),
                                    Color(0xFFFF5722).copy(alpha = 0.35f),
                                    Color(0xFFEE1D52).copy(alpha = 0.2f)
                                )
                            )
                        )
                        .clickable { triggerAuth() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFEE1D52), Color(0xFFFF5722))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "Authenticate with Face",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = if (isAuthenticating) "Verifying Identity..." else "Tap to Unlock",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = textColor
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage ?: "",
                        fontSize = 12.sp,
                        color = Color(0xFFE53935),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            // Bottom Actions: PIN fallback & Switch Account
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Use PIN or Password fallback
                OutlinedButton(
                    onClick = { triggerAuth() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = cardBg,
                        contentColor = textColor
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Password,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Use PIN or Password",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                // Switch Account Option
                TextButton(
                    onClick = onOpenAccountSwitcher,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEE1D52))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SwitchAccount,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Switch Account",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
