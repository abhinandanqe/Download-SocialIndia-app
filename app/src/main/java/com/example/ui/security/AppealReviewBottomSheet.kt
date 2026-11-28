package com.example.ui.security

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.AccountSuspensionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppealReviewBottomSheet(
    uid: String,
    onDismiss: () -> Unit,
    onAppealSubmitted: () -> Unit
) {
    val context = LocalContext.current
    val isDark by com.example.utils.ThemePreference.isDarkTheme.collectAsState()
    val bgColor = if (isDark) Color(0xFF16181F) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111827)
    val subTextColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val cardBg = if (isDark) Color(0xFF20232E) else Color(0xFFF9FAFB)
    val borderColor = if (isDark) Color(0xFF2C3040) else Color(0xFFE5E7EB)

    var appealReason by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }

    val suspensionInfo by AccountSuspensionManager.suspensionInfo.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = bgColor,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isSuccess || suspensionInfo.reviewStatus == "UNDER_REVIEW") {
                // Success / Already Under Review State
                Spacer(modifier = Modifier.height(16.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Review Submitted",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Review Request Submitted",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = textColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Our trust and safety team will carefully review your account details and community history. We typically process appeals within 24 to 48 hours.",
                    fontSize = 13.sp,
                    color = subTextColor,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
                ) {
                    Text("Understood", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))
            } else {
                // Request Review Form
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Gavel,
                            contentDescription = null,
                            tint = Color(0xFFEE1D52),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Request Account Review",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = textColor
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = subTextColor)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Notice Banner
                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = Color(0xFFFF5722),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "If you believe your account suspension was made in error or caused by unauthorized access, provide clear details below for our compliance team.",
                            fontSize = 12.sp,
                            color = subTextColor,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reason input
                Text(
                    text = "Explanation & Context *",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = appealReason,
                    onValueChange = { appealReason = it },
                    placeholder = { Text("Explain why you believe this suspension should be lifted...", fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFEE1D52),
                        unfocusedBorderColor = borderColor,
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = cardBg,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Optional contact email
                Text(
                    text = "Contact Email (for decision updates)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = contactEmail,
                    onValueChange = { contactEmail = it },
                    placeholder = { Text("email@example.com", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFEE1D52),
                        unfocusedBorderColor = borderColor,
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = cardBg,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Submit button
                Button(
                    onClick = {
                        if (appealReason.trim().length < 10) {
                            Toast.makeText(context, "Please provide a detailed explanation (minimum 10 characters).", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmitting = true
                        AccountSuspensionManager.submitAppeal(
                            uid = uid,
                            reason = appealReason.trim(),
                            contactEmail = contactEmail.trim(),
                            onSuccess = {
                                isSubmitting = false
                                isSuccess = true
                                onAppealSubmitted()
                            },
                            onError = { err ->
                                isSubmitting = false
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = !isSubmitting && appealReason.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Submit Review Request", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
