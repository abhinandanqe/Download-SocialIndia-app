package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.utils.SoundManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-End Payment Success Animation Dialog
 * Displays vibrant expanding spring animation, confetti particle burst,
 * real transaction audit details, copyable order ID, and verified security badge.
 */
@Composable
fun PaymentSuccessAnimationDialog(
    orderId: String,
    amount: Double,
    productTitle: String,
    txnIdOrUtr: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Spring scale for the checkmark badge
    val scaleAnim = remember { Animatable(0f) }
    // Expanding halo ring
    val haloAnim = remember { Animatable(0.6f) }
    // Confetti particles progress (0f to 1f)
    val particlesAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        SoundManager.playClickSound()

        // Pop the checkmark with bouncy spring
        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    LaunchedEffect(Unit) {
        haloAnim.animateTo(
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    LaunchedEffect(Unit) {
        particlesAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(900, easing = FastOutSlowInEasing)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp,
                shadowElevation = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Animation Stack (Halo + Check Circle + Confetti)
                    Box(
                        modifier = Modifier
                            .size(130.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Expanding Halo
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .scale(haloAnim.value)
                                .alpha(1.35f - haloAnim.value)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676).copy(alpha = 0.35f))
                        )

                        // Confetti Particles
                        val particleProgress = particlesAnim.value
                        val particleColors = listOf(
                            Color(0xFFFFD700), Color(0xFF00E676), Color(0xFF00B0FF),
                            Color(0xFFFF4081), Color(0xFF7C4DFF), Color(0xFFFF9100)
                        )
                        repeat(16) { index ->
                            val angle = (index * (360.0 / 16.0)) * (Math.PI / 180.0)
                            val distance = 45.dp + (35.dp * particleProgress)
                            val px = (distance.value * cos(angle)).dp
                            val py = (distance.value * sin(angle)).dp
                            val particleAlpha = (1f - particleProgress).coerceIn(0f, 1f)

                            Box(
                                modifier = Modifier
                                    .offset(x = px, y = py)
                                    .size(if (index % 2 == 0) 7.dp else 5.dp)
                                    .alpha(particleAlpha)
                                    .clip(if (index % 3 == 0) RoundedCornerShape(2.dp) else CircleShape)
                                    .background(particleColors[index % particleColors.size])
                            )
                        }

                        // Main Glowing Checkmark Badge
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .scale(scaleAnim.value)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF00E676), Color(0xFF00B0FF))
                                    )
                                )
                                .border(3.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Payment Successful",
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Payment Verified! 🎉",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Official NPCI & Social India Secure Receipt",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Big Amount Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF00E676).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "₹${String.format(java.util.Locale.US, "%.2f", amount)}",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.SansSerif,
                                color = Color(0xFF00C853)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = Color(0xFF00C853),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Authoritative Payment Completed",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF00C853)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Transaction Breakdown Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            ReceiptRow(label = "Product", value = productTitle)
                            ReceiptRow(label = "Order ID", value = orderId, isCopyable = true, onCopy = {
                                copyToClipboard(context, orderId, "Order ID copied!")
                            })
                            ReceiptRow(label = "Ref / Txn", value = txnIdOrUtr, isCopyable = true, onCopy = {
                                copyToClipboard(context, txnIdOrUtr, "Reference copied!")
                            })
                            ReceiptRow(label = "Payment Mode", value = "NPCI UPI (Real-Time)")
                            ReceiptRow(label = "Security Check", value = "Authoritative 100% Anti-Fraud")
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = "Done & Continue",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(
    label: String,
    value: String,
    isCopyable: Boolean = false,
    onCopy: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.SansSerif
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (isCopyable) Modifier.clickable(onClick = onCopy) else Modifier
        ) {
            Text(
                text = if (value.length > 22) value.take(19) + "..." else value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isCopyable) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String, message: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Payment Info", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
