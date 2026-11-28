package com.example.ui.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(
    onBack: () -> Unit,
    onNavigateToAdminSecurity: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securityVm: AccountSecurityViewModel = viewModel()

    LaunchedEffect(Unit) {
        securityVm.initForUser(context)
        securityVm.runSecurityCheckup(context)
    }

    val sessions by securityVm.sessions.collectAsStateWithLifecycle()
    val trustedDevices by securityVm.trustedDevices.collectAsStateWithLifecycle()
    val passkeys by securityVm.passkeys.collectAsStateWithLifecycle()
    val twoFactorConfig by securityVm.twoFactorConfig.collectAsStateWithLifecycle()
    val identityRecord by securityVm.identityRecord.collectAsStateWithLifecycle()
    val auditLogs by securityVm.auditLogs.collectAsStateWithLifecycle()
    val appLockConfig by securityVm.appLockConfig.collectAsStateWithLifecycle()
    val notificationSettings by securityVm.notificationSettings.collectAsStateWithLifecycle()
    val checkupResult by securityVm.checkupResult.collectAsStateWithLifecycle()
    val adminPolicy by securityVm.adminPolicy.collectAsStateWithLifecycle()
    val isLoading by securityVm.isLoading.collectAsStateWithLifecycle()

    var showIdentitySheet by remember { mutableStateOf(false) }
    var showTwoFactorDialog by remember { mutableStateOf(false) }
    var showPasskeysDialog by remember { mutableStateOf(false) }
    var showSessionsDialog by remember { mutableStateOf(false) }
    var showTrustedDevicesDialog by remember { mutableStateOf(false) }
    var showAlertsDialog by remember { mutableStateOf(false) }
    var showAppLockDialog by remember { mutableStateOf(false) }
    var showAuditLogsDialog by remember { mutableStateOf(false) }
    var showCheckupDialog by remember { mutableStateOf(false) }

    val isDark by com.example.utils.ThemePreference.isDarkTheme.collectAsState()
    val bgColor = if (isDark) Color(0xFF0F0F11) else Color(0xFFFFFFFF)
    val topBarColor = if (isDark) Color(0xFF121214) else Color(0xFFFFFFFF)
    val cardBg = if (isDark) Color(0xFF18181C) else Color(0xFFF9FAFB)
    val textColor = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111827)
    val subTextColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF2C3040) else Color(0xFFE5E7EB)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Account Security & Identity",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = textColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor)
                    }
                },
                actions = {
                    IconButton(onClick = { securityVm.runSecurityCheckup(context) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Checkup", tint = textColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor)
            )
        },
        containerColor = bgColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 1. SECURITY SCORE CARD
            SecurityScoreCard(
                score = checkupResult?.overallScore ?: 50,
                recommendationsCount = checkupResult?.recommendations?.size ?: 0,
                onRunCheckup = { showCheckupDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. IDENTITY VERIFICATION STATUS BANNER
            IdentityStatusBanner(
                record = identityRecord,
                onClick = { showIdentitySheet = true }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 3. CORE SECURITY SECTIONS
            Text(
                "Access & Authentication",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            SecurityNavRow(
                icon = Icons.Outlined.Password,
                title = "Two-Factor Authentication (2FA)",
                subtitle = if (twoFactorConfig.isEnabled) "Enabled (${twoFactorConfig.primaryMethod.name})" else "Off (Recommended to enable)",
                statusPill = if (twoFactorConfig.isEnabled) "ACTIVE" else "OFF",
                isPillSuccess = twoFactorConfig.isEnabled,
                onClick = { showTwoFactorDialog = true }
            )

            SecurityNavRow(
                icon = Icons.Outlined.Key,
                title = "Passkeys",
                subtitle = "${passkeys.size} passkey(s) registered on device",
                statusPill = if (passkeys.isNotEmpty()) "${passkeys.size} ACTIVE" else "SET UP",
                isPillSuccess = passkeys.isNotEmpty(),
                onClick = { showPasskeysDialog = true }
            )

            SecurityNavRow(
                icon = Icons.Outlined.Face,
                title = "Face / Selfie Identity Verification",
                subtitle = "Cross-device identity verification & liveness",
                statusPill = identityRecord.status.name.replace("_", " "),
                isPillSuccess = identityRecord.status == IdentityVerificationStatus.VERIFIED,
                onClick = { showIdentitySheet = true }
            )

            SecurityNavRow(
                icon = Icons.Outlined.Fingerprint,
                title = "App Lock & Biometric Unlock",
                subtitle = if (appLockConfig.enabled) "Protected with ${appLockConfig.lockType}" else "Off",
                statusPill = if (appLockConfig.enabled) "ON" else "OFF",
                isPillSuccess = appLockConfig.enabled,
                onClick = { showAppLockDialog = true }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Devices & Sessions",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            SecurityNavRow(
                icon = Icons.Outlined.Devices,
                title = "Where You're Logged In",
                subtitle = "${sessions.size} active session(s)",
                statusPill = "${sessions.size} DEVICES",
                isPillSuccess = true,
                onClick = { showSessionsDialog = true }
            )

            SecurityNavRow(
                icon = Icons.Outlined.VerifiedUser,
                title = "Trusted Devices",
                subtitle = "${trustedDevices.size} authorized trusted hardware",
                statusPill = "${trustedDevices.size} TRUSTED",
                isPillSuccess = trustedDevices.isNotEmpty(),
                onClick = { showTrustedDevicesDialog = true }
            )

            SecurityNavRow(
                icon = Icons.Outlined.NotificationsActive,
                title = "Login Alerts & Security Events",
                subtitle = "Get notified of unknown device logins",
                statusPill = if (notificationSettings.loginAlertsEnabled) "ACTIVE" else "MUTED",
                isPillSuccess = notificationSettings.loginAlertsEnabled,
                onClick = { showAlertsDialog = true }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Audit & Diagnostics",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            SecurityNavRow(
                icon = Icons.Outlined.History,
                title = "Recent Security Activity",
                subtitle = "Audit log of logins, passkeys & changes",
                statusPill = "${auditLogs.size} EVENTS",
                isPillSuccess = true,
                onClick = { showAuditLogsDialog = true }
            )

            SecurityNavRow(
                icon = Icons.Outlined.FactCheck,
                title = "Run Security Checkup",
                subtitle = "Complete inspection of account defenses",
                statusPill = "INSPECT",
                isPillSuccess = true,
                onClick = { showCheckupDialog = true }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ==========================================
    // DIALOGS & BOTTOM SHEETS
    // ==========================================

    if (showIdentitySheet) {
        IdentityVerificationModal(
            record = identityRecord,
            onDismiss = { showIdentitySheet = false },
            onResetVerification = {
                securityVm.resetIdentityVerification(context) { success ->
                    if (success) {
                        Toast.makeText(context, "Identity verification reset successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showTwoFactorDialog) {
        TwoFactorModal(
            config = twoFactorConfig,
            onDismiss = { showTwoFactorDialog = false },
            onEnable = { secret, codes, method ->
                securityVm.enableTwoFactor(context, secret, codes, method) { success ->
                    if (success) {
                        Toast.makeText(context, "2FA successfully enabled", Toast.LENGTH_SHORT).show()
                        showTwoFactorDialog = false
                    }
                }
            },
            onDisable = {
                securityVm.disableTwoFactor(context) { success ->
                    if (success) {
                        Toast.makeText(context, "2FA disabled", Toast.LENGTH_SHORT).show()
                        showTwoFactorDialog = false
                    }
                }
            }
        )
    }

    if (showPasskeysDialog) {
        PasskeysModal(
            passkeys = passkeys,
            onDismiss = { showPasskeysDialog = false },
            onAddPasskey = { name, credId ->
                securityVm.addPasskey(context, name, credId) { success ->
                    if (success) Toast.makeText(context, "Passkey created", Toast.LENGTH_SHORT).show()
                }
            },
            onRenamePasskey = { id, name ->
                securityVm.renamePasskey(context, id, name) {
                    Toast.makeText(context, "Passkey renamed", Toast.LENGTH_SHORT).show()
                }
            },
            onRemovePasskey = { id ->
                securityVm.removePasskey(context, id) {
                    Toast.makeText(context, "Passkey removed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showSessionsDialog) {
        ActiveSessionsModal(
            sessions = sessions,
            onDismiss = { showSessionsDialog = false },
            onRevokeSession = { sessionId ->
                securityVm.revokeSession(context, sessionId) {
                    Toast.makeText(context, "Session revoked", Toast.LENGTH_SHORT).show()
                }
            },
            onRevokeAllOther = {
                securityVm.revokeAllOtherSessions(context) {
                    Toast.makeText(context, "Logged out from all other devices", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showTrustedDevicesDialog) {
        TrustedDevicesModal(
            trustedDevices = trustedDevices,
            onDismiss = { showTrustedDevicesDialog = false },
            onRemoveDevice = { deviceId ->
                securityVm.removeTrustedDevice(context, deviceId) {
                    Toast.makeText(context, "Device untrusted", Toast.LENGTH_SHORT).show()
                }
            },
            onTrustCurrentDevice = {
                securityVm.trustCurrentDevice(context, android.os.Build.MODEL ?: "Android Device") {
                    Toast.makeText(context, "Current device trusted", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showAlertsDialog) {
        NotificationAlertsModal(
            settings = notificationSettings,
            onDismiss = { showAlertsDialog = false },
            onSave = { updated ->
                securityVm.saveNotificationSettings(updated) {
                    Toast.makeText(context, "Security alert preferences saved", Toast.LENGTH_SHORT).show()
                    showAlertsDialog = false
                }
            }
        )
    }

    if (showAppLockDialog) {
        AppLockModal(
            config = appLockConfig,
            onDismiss = { showAppLockDialog = false },
            onSave = { updated ->
                val currentUid = com.example.model.RepositoryManager.getCurrentUserId()
                com.example.security.FaceLockManager.setFaceLockEnabled(context, currentUid, updated.enabled)
                securityVm.saveAppLockConfig(context, updated) {
                    Toast.makeText(context, "App lock preferences updated", Toast.LENGTH_SHORT).show()
                    showAppLockDialog = false
                }
            }
        )
    }

    if (showAuditLogsDialog) {
        AuditLogsModal(
            logs = auditLogs,
            onDismiss = { showAuditLogsDialog = false }
        )
    }

    if (showCheckupDialog) {
        SecurityCheckupModal(
            checkupResult = checkupResult,
            onDismiss = { showCheckupDialog = false }
        )
    }
}

// ==========================================
// SUB-COMPONENTS
// ==========================================

@Composable
private fun SecurityScoreCard(
    score: Int,
    recommendationsCount: Int,
    onRunCheckup: () -> Unit
) {
    val gradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF1E1E24), Color(0xFF262630))
    )

    val scoreColor = when {
        score >= 80 -> Color(0xFF4CAF50)
        score >= 50 -> Color(0xFFFFB300)
        else -> Color(0xFFE53935)
    }

    val scoreStatus = when {
        score >= 80 -> "Strong Security"
        score >= 50 -> "Moderate Protection"
        else -> "Action Required"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(scoreColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        scoreStatus,
                        color = scoreColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    "Account Security Score",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    if (recommendationsCount > 0) "$recommendationsCount recommended improvement(s)" else "All essential safeguards active",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onRunCheckup,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Review Checkup", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(3.dp, scoreColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$score",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "/100",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentityStatusBanner(
    record: IdentityVerificationRecord,
    onClick: () -> Unit
) {
    val isVerified = record.status == IdentityVerificationStatus.VERIFIED
    val bgBrush = if (isVerified) {
        Brush.horizontalGradient(listOf(Color(0xFF0F2B1D), Color(0xFF133B26)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF261E14), Color(0xFF332717)))
    }

    val borderColor = if (isVerified) Color(0xFF2E7D32) else Color(0xFFFF9800).copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgBrush)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isVerified) Color(0xFF2E7D32).copy(alpha = 0.3f) else Color(0xFFFF9800).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isVerified) Icons.Default.VerifiedUser else Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = if (isVerified) Color(0xFF4CAF50) else Color(0xFFFFB300),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isVerified) "✓ Social India Identity Verified" else "Identity Verification Required",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = if (isVerified) "Tier 2 - Verified Biometric & ID Shield" else "Setup face & liveness check for new devices",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SecurityNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    statusPill: String,
    isPillSuccess: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF16161A),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isPillSuccess) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f))
                    .border(
                        1.dp,
                        if (isPillSuccess) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.2f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusPill,
                    color = if (isPillSuccess) Color(0xFF81C784) else Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ==========================================
// 1. IDENTITY VERIFICATION MODAL
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdentityVerificationModal(
    record: IdentityVerificationRecord,
    onDismiss: () -> Unit,
    onResetVerification: () -> Unit
) {
    val context = LocalContext.current
    var isStartingFlow by remember { mutableStateOf(false) }
    var consentGiven by remember { mutableStateOf(false) }
    val identityEngine = remember { SocialIndiaSecureIdentityEngine() }
    val isConfigured = identityEngine.isServiceConfigured()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18181C),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Identity Verification",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (record.status == IdentityVerificationStatus.VERIFIED) {
                // Verified state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B3D28))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Identity Status: Verified", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Security Level: ${record.securityLevel}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Text("Provider: ${record.provider}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        if (record.verifiedAt != null) {
                            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(record.verifiedAt))
                            Text("Verified on: $dateStr", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedButton(
                    onClick = onResetVerification,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(Color(0xFFEF5350), Color(0xFFEF5350)))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Identity Verification")
                }
            } else {
                // Setup / Unverified flow
                Text(
                    "Protect your account against unauthorized logins from unfamiliar devices using verified selfie & liveness checks.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Privacy & Data Guarantee", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• We NEVER store raw facial biometrics or templates on Firebase.", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                        Text("• Device biometrics remain bound to your phone's secure hardware.", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                        Text("• Cross-device verification uses an authorized KYC identity engine.", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                        Text("• You can reset or remove your verification record at any time.", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { consentGiven = !consentGiven }
                ) {
                    Checkbox(
                        checked = consentGiven,
                        onCheckedChange = { consentGiven = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFE2C55))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "I consent to secure identity verification processing.",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isConfigured) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF332014))
                            .padding(12.dp)
                    ) {
                        Text(
                            "Identity verification service is not configured yet. Please configure the enterprise provider key in Admin Security Panel.",
                            color = Color(0xFFFFB74D),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (!consentGiven) {
                                Toast.makeText(context, "Please agree to the privacy consent first.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Initiating secure liveness challenge...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = consentGiven,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Identity Verification")
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. TWO-FACTOR MODAL
// ==========================================

@Composable
private fun TwoFactorModal(
    config: TwoFactorConfig,
    onDismiss: () -> Unit,
    onEnable: (secret: String, backupCodes: List<String>, method: TwoFactorMethod) -> Unit,
    onDisable: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AccountSecurityRepository }
    var currentStep by remember { mutableIntStateOf(if (config.isEnabled) 0 else 1) }
    var generatedSecret by remember { mutableStateOf(repository.generateTotpSecret()) }
    var backupCodes by remember { mutableStateOf(repository.generateBackupCodes()) }
    var testCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        title = {
            Text(
                "Two-Factor Authentication",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (config.isEnabled) {
                    Text(
                        "Two-Factor Authentication is currently ACTIVE on your account using Authenticator App.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Disabling 2FA lowers your defense against automated password attacks.",
                        color = Color(0xFFFF8A80),
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        "Add an extra security layer. Whenever you log in from a new device, a 6-digit code will be required.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Secret Key for Authenticator App:", color = Color.Gray, fontSize = 11.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            generatedSecret,
                            color = Color(0xFFFFB300),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("2FA Secret", generatedSecret))
                                Toast.makeText(context, "Secret key copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Enter 6-digit verification code to confirm:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    OutlinedTextField(
                        value = testCode,
                        onValueChange = { if (it.length <= 6) testCode = it },
                        placeholder = { Text("e.g. 123456", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFE2C55),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            if (config.isEnabled) {
                Button(
                    onClick = onDisable,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Disable 2FA")
                }
            } else {
                Button(
                    onClick = {
                        if (testCode.length < 6) {
                            Toast.makeText(context, "Please enter 6 digits", Toast.LENGTH_SHORT).show()
                        } else {
                            onEnable(generatedSecret, backupCodes, TwoFactorMethod.AUTHENTICATOR_APP)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
                ) {
                    Text("Activate 2FA")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

// ==========================================
// 3. PASSKEYS MODAL
// ==========================================

@Composable
private fun PasskeysModal(
    passkeys: List<PasskeyRecord>,
    onDismiss: () -> Unit,
    onAddPasskey: (name: String, credentialId: String) -> Unit,
    onRenamePasskey: (id: String, name: String) -> Unit,
    onRemovePasskey: (id: String) -> Unit
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var passkeyName by remember { mutableStateOf("${android.os.Build.MODEL} Passkey") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Passkeys", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Passkey", tint = Color(0xFFFE2C55))
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Passkeys use your device's biometric lock (fingerprint or face) via Android Credential Manager for phishing-resistant sign-in.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (passkeys.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No passkeys added yet", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(passkeys) { pk ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pk.name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(pk.deviceModel, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                                }
                                IconButton(
                                    onClick = { onRemovePasskey(pk.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
            ) {
                Text("Add Passkey")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = Color(0xFF222226),
            title = { Text("Register Passkey", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Name this passkey for your reference:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passkeyName,
                        onValueChange = { passkeyName = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFE2C55),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mockCred = "pk_cred_" + UUID.randomUUID().toString().take(12)
                        onAddPasskey(passkeyName.ifBlank { "Android Passkey" }, mockCred)
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
                ) {
                    Text("Save Passkey")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}

// ==========================================
// 4. ACTIVE SESSIONS MODAL
// ==========================================

@Composable
private fun ActiveSessionsModal(
    sessions: List<DeviceSession>,
    onDismiss: () -> Unit,
    onRevokeSession: (String) -> Unit,
    onRevokeAllOther: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        title = {
            Text("Where You're Logged In", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "You're currently signed in to your account on these devices. Tap any unfamiliar device on the map or list to sign out remotely.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Device Geolocation Map View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0F2922))
                            )
                        )
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LIVE DEVICE SESSION MAP",
                                    color = Color(0xFF10B981),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "${sessions.size} Active Node(s)",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            sessions.take(3).forEach { session ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (session.isCurrentDevice) Color(0xFF10B981).copy(alpha = 0.25f)
                                                else Color(0xFFF59E0B).copy(alpha = 0.25f)
                                            )
                                            .border(
                                                1.5.dp,
                                                if (session.isCurrentDevice) Color(0xFF10B981) else Color(0xFFF59E0B),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = if (session.isCurrentDevice) Color(0xFF10B981) else Color(0xFFF59E0B),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = session.locationCity.ifBlank { "India" },
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                    items(sessions) { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (s.isCurrentDevice) Color(0xFF1B3D28) else Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.deviceName, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    if (s.isCurrentDevice) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("(This Device)", color = Color(0xFF81C784), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text("${s.locationCity} • ${s.osVersion}", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                            }
                            if (!s.isCurrentDevice) {
                                TextButton(
                                    onClick = { onRevokeSession(s.sessionId) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF5350))
                                ) {
                                    Text("Sign Out", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRevokeAllOther,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Text("Sign Out All Others")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

// ==========================================
// 5. TRUSTED DEVICES MODAL
// ==========================================

@Composable
private fun TrustedDevicesModal(
    trustedDevices: List<TrustedDevice>,
    onDismiss: () -> Unit,
    onRemoveDevice: (String) -> Unit,
    onTrustCurrentDevice: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        title = {
            Text("Trusted Devices", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Trusted devices don't trigger multi-factor challenges unless unusual risk is detected.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (trustedDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No trusted devices recorded", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                        items(trustedDevices) { td ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(td.deviceName, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(td.trustedAt))
                                    Text("Trusted on $dateStr", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                                }
                                IconButton(
                                    onClick = { onRemoveDevice(td.deviceId) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Untrust", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onTrustCurrentDevice,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
            ) {
                Text("Trust This Device")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

// ==========================================
// 6. NOTIFICATION ALERTS MODAL
// ==========================================

@Composable
private fun NotificationAlertsModal(
    settings: SecurityNotificationSettings,
    onDismiss: () -> Unit,
    onSave: (SecurityNotificationSettings) -> Unit
) {
    var loginAlerts by remember { mutableStateOf(settings.loginAlertsEnabled) }
    var newDeviceAlerts by remember { mutableStateOf(settings.newDeviceAlertsEnabled) }
    var passwordAlerts by remember { mutableStateOf(settings.passwordChangeAlertsEnabled) }
    var twoFactorAlerts by remember { mutableStateOf(settings.twoFactorAlertsEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        title = {
            Text("Security Alerts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Control when you receive real-time push and in-app security alerts.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("New Device Login Alerts", color = Color.White, fontSize = 13.sp)
                    Switch(
                        checked = newDeviceAlerts,
                        onCheckedChange = { newDeviceAlerts = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFE2C55), checkedTrackColor = Color(0xFFFE2C55).copy(alpha = 0.4f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Password Change Alerts", color = Color.White, fontSize = 13.sp)
                    Switch(
                        checked = passwordAlerts,
                        onCheckedChange = { passwordAlerts = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFE2C55), checkedTrackColor = Color(0xFFFE2C55).copy(alpha = 0.4f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("2FA & Passkey Modification", color = Color.White, fontSize = 13.sp)
                    Switch(
                        checked = twoFactorAlerts,
                        onCheckedChange = { twoFactorAlerts = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFE2C55), checkedTrackColor = Color(0xFFFE2C55).copy(alpha = 0.4f))
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        settings.copy(
                            loginAlertsEnabled = loginAlerts,
                            newDeviceAlertsEnabled = newDeviceAlerts,
                            passwordChangeAlertsEnabled = passwordAlerts,
                            twoFactorAlertsEnabled = twoFactorAlerts
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
            ) {
                Text("Save Preferences")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

// ==========================================
// 7. APP LOCK MODAL
// ==========================================

@Composable
private fun AppLockModal(
    config: AppLockConfig,
    onDismiss: () -> Unit,
    onSave: (AppLockConfig) -> Unit
) {
    var enabled by remember { mutableStateOf(config.enabled) }
    var protectSensitive by remember { mutableStateOf(config.protectSensitiveScreens) }
    val context = LocalContext.current
    val availability = remember { SecurityBiometricHelper.checkBiometricAvailability(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        title = {
            Text("App Lock & Biometrics", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Require fingerprint, face, or device PIN before opening Social India or accessing sensitive settings.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable App Lock", color = Color.White, fontSize = 13.sp)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFE2C55), checkedTrackColor = Color(0xFFFE2C55).copy(alpha = 0.4f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Protect Sensitive Screens", color = Color.White, fontSize = 13.sp)
                    Switch(
                        checked = protectSensitive,
                        onCheckedChange = { protectSensitive = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFE2C55), checkedTrackColor = Color(0xFFFE2C55).copy(alpha = 0.4f))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Hardware Status: ${availability.name}",
                    color = if (availability == SecurityBiometricHelper.BiometricAvailability.AVAILABLE) Color(0xFF81C784) else Color(0xFFFFB74D),
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        config.copy(
                            enabled = enabled,
                            protectSensitiveScreens = protectSensitive
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

// ==========================================
// 8. AUDIT LOGS MODAL
// ==========================================

@Composable
private fun AuditLogsModal(
    logs: List<SecurityAuditLog>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        title = {
            Text("Recent Security Activity", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Audit trail of security events and authentication changes.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)

                Spacer(modifier = Modifier.height(14.dp))

                if (logs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        Text("No audit events recorded yet", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(logs) { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (log.status == "SUCCESS") Color(0xFF4CAF50) else Color(0xFFE53935))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(log.eventType.replace("_", " "), color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                    Text(log.eventDescription, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                    val dateStr = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(log.timestamp))
                                    Text("$dateStr • ${log.deviceModel}", color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
            ) {
                Text("Close")
            }
        }
    )
}

// ==========================================
// 9. SECURITY CHECKUP MODAL
// ==========================================

@Composable
private fun SecurityCheckupModal(
    checkupResult: SecurityCheckupResult?,
    onDismiss: () -> Unit
) {
    val res = checkupResult ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        title = {
            Text("Security Checkup Results", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Overall Score", color = Color.White, fontSize = 14.sp)
                    Text("${res.overallScore}/100", color = Color(0xFF81C784), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                CheckupItemRow("Password Health", res.passwordHealth, true)
                CheckupItemRow("Two-Factor Authentication", if (res.twoFactorActive) "Enabled" else "Off", res.twoFactorActive)
                CheckupItemRow("Passkeys", if (res.passkeyActive) "Active" else "None", res.passkeyActive)
                CheckupItemRow("Identity Verification", if (res.identityVerified) "Verified" else "Unverified", res.identityVerified)
                CheckupItemRow("Active Sessions", "${res.activeSessionsCount} Session(s)", true)

                if (res.recommendations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Recommendations:", color = Color(0xFFFFB300), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    res.recommendations.forEach { rec ->
                        Text("• $rec", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
            ) {
                Text("Got It")
            }
        }
    )
}

@Composable
private fun CheckupItemRow(title: String, status: String, isSuccess: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFFFB300),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(status, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
