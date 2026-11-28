package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.User
import com.example.model.VerificationStatus
import com.example.model.VerificationType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized VerificationBadge Component.
 *
 * Enforces unified badge visibility across the entire application:
 * - Checks active verification status
 * - Excludes suspended accounts
 * - Excludes expired accounts (currentTime >= expiryDate)
 * - Shows interactive authenticity dialog with verification type and expiration date
 */
@Composable
fun VerificationBadge(
    isVerified: Boolean,
    badgeUrl: String? = null,
    verificationType: String = VerificationType.PERSONAL,
    expiryTimestamp: Long = 0L,
    isSuspended: Boolean = false,
    modifier: Modifier = Modifier.size(16.dp),
    showDialogOnClick: Boolean = true
) {
    val now = System.currentTimeMillis()
    val isExpired = expiryTimestamp in 1..now

    // If account is suspended or expired, never render the active verified badge
    if (!isVerified || isSuspended || isExpired) {
        return
    }

    var showInfoDialog by remember { mutableStateOf(false) }
    val customUrl = badgeUrl?.takeIf {
        it.isNotBlank() && !it.contains("i.ibb.co/8Lhv9YND") && !it.contains("i.ibb.co/5g7pLKGW")
    }

    Box(
        modifier = if (showDialogOnClick) {
            modifier.clickable { showInfoDialog = true }
        } else {
            modifier
        },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Verified,
            contentDescription = "Social India Verified Badge",
            tint = Color(0xFF0095F6),
            modifier = Modifier.fillMaxSize()
        )
        if (!customUrl.isNullOrBlank()) {
            AsyncImage(
                model = customUrl,
                contentDescription = "Social India Verified Badge",
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (showInfoDialog) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val expiryString = if (expiryTimestamp > 0L) dateFormat.format(Date(expiryTimestamp)) else "Active"

        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = Color(0xFF1DA1F2),
                    modifier = Modifier.size(44.dp)
                )
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Social India Verified",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "$verificationType Account",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "This badge confirms that Social India has authenticated this profile as an authentic, notable creator, business, or official entity.",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (expiryTimestamp > 0L) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "Valid until: $expiryString",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Got it", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun VerificationBadge(
    user: User,
    modifier: Modifier = Modifier.size(16.dp),
    showDialogOnClick: Boolean = true
) {
    val isVerifiedState = user.isVerified ||
            user.verificationStatus == "approved" ||
            user.verificationStatus == VerificationStatus.VERIFIED_ACTIVE

    VerificationBadge(
        isVerified = isVerifiedState,
        badgeUrl = user.verificationBadgeUrl,
        verificationType = if (user.verificationType.isNotEmpty()) user.verificationType else {
            if (user.accountType == "BUSINESS") VerificationType.BUSINESS else VerificationType.PERSONAL
        },
        expiryTimestamp = user.verificationExpiryTimestamp,
        isSuspended = user.verificationSuspended,
        modifier = modifier,
        showDialogOnClick = showDialogOnClick
    )
}
