package com.example.ui.security

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.auth.MultiAccountManager
import com.example.security.AccountSuspensionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SuspendedAccountScreen(
    onNavigateToKyc: () -> Unit,
    onOpenAccountSwitcher: () -> Unit,
    onLogout: () -> Unit
) {
    // Strictly disable back navigation to prevent suspension bypass
    BackHandler(enabled = true) {
        // Enforce lock: user cannot navigate away from suspension screen
    }

    val context = LocalContext.current
    val isDark by com.example.utils.ThemePreference.isDarkTheme.collectAsState()
    val bgColor = if (isDark) Color(0xFF0D0E12) else Color(0xFFFFFFFF)
    val cardBg = if (isDark) Color(0xFF16181F) else Color(0xFFF9FAFB)
    val textColor = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111827)
    val subTextColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF262935) else Color(0xFFE5E7EB)

    val activeAccount by MultiAccountManager.activeAccount.collectAsState()
    val suspensionInfo by AccountSuspensionManager.suspensionInfo.collectAsState()

    var showAppealSheet by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val formattedDate = remember(suspensionInfo.suspensionDate) {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        sdf.format(Date(suspensionInfo.suspensionDate))
    }

    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(suspensionInfo.suspendedUntil, suspensionInfo.suspensionDate) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }

    // Calculate countdown target: either explicit suspendedUntil (for 24h/7d/30d restrictions) or 180-day appeal window
    val targetDeadlineMillis = remember(suspensionInfo.suspendedUntil, suspensionInfo.suspensionDate) {
        if (suspensionInfo.suspendedUntil > System.currentTimeMillis()) {
            suspensionInfo.suspendedUntil
        } else {
            suspensionInfo.suspensionDate + (180L * 24L * 3600L * 1000L)
        }
    }
    val remainingMillis = (targetDeadlineMillis - nowMillis).coerceAtLeast(0L)
    val remDays = remainingMillis / (1000L * 60L * 60L * 24L)
    val remHours = (remainingMillis / (1000L * 60L * 60L)) % 24L
    val remMinutes = (remainingMillis / (1000L * 60L)) % 60L
    val remSeconds = (remainingMillis / 1000L) % 60L
    val countdownString = String.format(Locale.US, "%02dd : %02dh : %02dm : %02ds", remDays, remHours, remMinutes, remSeconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Social India Branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFEE1D52), Color(0xFFFF5722))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("SI", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
                Text(
                    text = "Social India",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = textColor
                )
            }

            // Warning Icon with Alert Glow
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                    .border(2.dp, Color(0xFFEF4444).copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Account Suspended",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Profile Avatar with Status
            Box(contentAlignment = Alignment.BottomEnd) {
                val avatarUrl = activeAccount?.avatarUrl ?: ""
                if (avatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(2.dp, borderColor, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(cardBg)
                            .border(2.dp, borderColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (activeAccount?.username?.take(1) ?: "U").uppercase(),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                        .border(2.dp, bgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Block,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "@" + (activeAccount?.username ?: "user"),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = subTextColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when {
                    suspensionInfo.accountStatus.equals("BANNED", ignoreCase = true) -> "Account Permanently Disabled"
                    suspensionInfo.restrictionType.startsWith("RESTRICT", ignoreCase = true) -> "Your Account Is Temporarily Restricted"
                    else -> "Your Account Has Been Suspended"
                },
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                color = textColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "We suspended your account on $formattedDate because it doesn't follow our Community Guidelines. Complete Video Selfie KYC or submit an appeal before the timer expires.",
                fontSize = 13.sp,
                color = subTextColor,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live Countdown Timer Banner (Instagram Lockout Style)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFEF4444).copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (suspensionInfo.suspendedUntil > nowMillis) "RESTRICTION TIME REMAINING" else "TIME LEFT TO APPEAL BEFORE PERMANENT DELETION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = countdownString,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Detail Card (Instagram Style)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = cardBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Reason
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "Reason",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = subTextColor,
                            modifier = Modifier.width(90.dp)
                        )
                        Text(
                            text = suspensionInfo.suspensionReason,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End
                        )
                    }

                    HorizontalDivider(color = borderColor)

                    // Strikes & Restriction Type
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Strike Record",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = subTextColor
                        )
                        Text(
                            text = "${suspensionInfo.strikesCount.coerceAtLeast(1)} / 3 Community Strikes",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B)
                        )
                    }

                    HorizontalDivider(color = borderColor)

                    // Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Date",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = subTextColor
                        )
                        Text(
                            text = formattedDate,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor
                        )
                    }

                    HorizontalDivider(color = borderColor)

                    // Case ID
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Case Reference",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = subTextColor
                        )
                        Text(
                            text = suspensionInfo.caseId,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEE1D52)
                        )
                    }

                    HorizontalDivider(color = borderColor)

                    // Appeal / Review Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Review Status",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = subTextColor
                        )
                        val statusText = when (suspensionInfo.reviewStatus) {
                            "UNDER_REVIEW" -> "Under Review (In Progress)"
                            "REJECTED" -> "Review Rejected"
                            "APPROVED" -> "Approved (Restoring)"
                            else -> "Not Requested"
                        }
                        val statusColor = when (suspensionInfo.reviewStatus) {
                            "UNDER_REVIEW" -> Color(0xFFF59E0B)
                            "REJECTED" -> Color(0xFFEF4444)
                            "APPROVED" -> Color(0xFF10B981)
                            else -> subTextColor
                        }
                        Text(
                            text = statusText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary CTA: Appeal / Start Video KYC
                Button(
                    onClick = onNavigateToKyc,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0095F6)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Appeal / Start Video KYC",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }

                // Written Appeal Request Review Button
                val isUnderReview = suspensionInfo.reviewStatus == "UNDER_REVIEW"
                OutlinedButton(
                    onClick = { showAppealSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = cardBg,
                        contentColor = textColor
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                ) {
                    Icon(
                        imageVector = if (isUnderReview) Icons.Outlined.HourglassTop else Icons.Outlined.Gavel,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isUnderReview) "Appeal Under Review (Update Details)" else "Submit Written Appeal",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                // Help & Support
                TextButton(
                    onClick = { showHelpDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HelpOutline,
                        contentDescription = null,
                        tint = subTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Help & Community Guidelines",
                        color = subTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(color = borderColor, modifier = Modifier.padding(vertical = 4.dp))

                // Bottom: Switch Account & Log Out
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onOpenAccountSwitcher) {
                        Icon(
                            imageVector = Icons.Outlined.SwitchAccount,
                            contentDescription = null,
                            tint = Color(0xFFEE1D52),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Switch Account",
                            color = Color(0xFFEE1D52),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    TextButton(onClick = onLogout) {
                        Text(
                            text = "Log Out",
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Appeal Review Bottom Sheet
    if (showAppealSheet) {
        val uid = activeAccount?.uid ?: ""
        AppealReviewBottomSheet(
            uid = uid,
            onDismiss = { showAppealSheet = false },
            onAppealSubmitted = {
                showAppealSheet = false
                Toast.makeText(context, "Review request submitted successfully.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.HelpOutline, contentDescription = null, tint = Color(0xFFEE1D52))
                    Text("Community Safety & Appeals", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Social India maintains strict community safety standards to protect creators, users, and authentic voices.",
                        fontSize = 13.sp
                    )
                    Text(
                        "• Accounts may be suspended due to spam, impersonation, hate speech, or multiple automated policy flags.\n• You can request an appeal directly from this screen with context.\n• If your identity was questioned, complete Face KYC verification to verify authenticity.",
                        fontSize = 12.sp,
                        color = subTextColor
                    )
                    Text(
                        "For further inquiries, contact compliance@socialindia.app with your Case Reference ID: ${suspensionInfo.caseId}.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEE1D52)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
