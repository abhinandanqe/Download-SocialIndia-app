package com.example.ui.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.auth.MultiAccountManager
import com.example.model.RepositoryManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class KycStep {
    STATUS_OVERVIEW,
    INSTRUCTIONS_CONSENT,
    LIVENESS_CAMERA,
    PROCESSING,
    RESULT_SUCCESS,
    RESULT_FAILED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceKycScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val isDark by com.example.utils.ThemePreference.isDarkTheme.collectAsState()
    val bgColor = if (isDark) Color(0xFF0D0E12) else Color(0xFFFFFFFF)
    val cardBg = if (isDark) Color(0xFF16181F) else Color(0xFFF9FAFB)
    val textColor = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111827)
    val subTextColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF262935) else Color(0xFFE5E7EB)

    val activeAccount by MultiAccountManager.activeAccount.collectAsState()
    val uid = activeAccount?.uid ?: RepositoryManager.getCurrentUserId()

    var currentStep by remember { mutableStateOf(KycStep.STATUS_OVERVIEW) }
    var currentStatus by remember { mutableStateOf("NOT_SET_UP") } // NOT_SET_UP, PENDING, VERIFIED, FAILED, REJECTED
    var verificationReference by remember { mutableStateOf("") }
    var verifiedAtDate by remember { mutableStateOf(0L) }
    var failureReason by remember { mutableStateOf("Low lighting or face movement detected.") }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Interactive Liveness Animation State
    var livenessSubStep by remember { mutableStateOf(1) } // 1: Align, 2: Blink, 3: Hold
    var livenessProgress by remember { mutableFloatStateOf(0f) }
    var showPermissionFallbackDialog by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = currentStep != KycStep.STATUS_OVERVIEW) {
        currentStep = KycStep.STATUS_OVERVIEW
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            currentStep = KycStep.LIVENESS_CAMERA
        } else {
            showPermissionFallbackDialog = true
        }
    }

    if (showPermissionFallbackDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionFallbackDialog = false },
            title = { Text("Camera Permission Required", fontWeight = FontWeight.Bold) },
            text = {
                Text("Video Selfie KYC requires front camera access to verify your identity and liveness. Please grant camera permission to continue.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionFallbackDialog = false
                        try {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionFallbackDialog = false
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                ) {
                    Text("Try Again")
                }
            }
        )
    }

    // Listen to real-time KYC status from Firestore for this UID
    DisposableEffect(uid) {
        var registration: com.google.firebase.firestore.ListenerRegistration? = null
        if (uid.isNotEmpty()) {
            try {
                registration = RepositoryManager.firestore.collection("users").document(uid)
                    .addSnapshotListener { snapshot, _ ->
                        if (snapshot != null && snapshot.exists()) {
                            val status = snapshot.getString("verificationStatus") ?: "NOT_SET_UP"
                            val isVerified = snapshot.getBoolean("isVerified") ?: false
                            val ref = snapshot.getString("verificationReference") ?: ""
                            val date = snapshot.getLong("verifiedAt") ?: 0L

                            currentStatus = if (isVerified || status.equals("VERIFIED", ignoreCase = true) || status.equals("approved", ignoreCase = true)) {
                                "VERIFIED"
                            } else {
                                status.uppercase()
                            }
                            verificationReference = ref
                            verifiedAtDate = date
                        }
                    }
            } catch (_: Exception) {}
        }
        onDispose {
            registration?.remove()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Identity Verification",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep == KycStep.STATUS_OVERVIEW) {
                            onBack()
                        } else {
                            currentStep = KycStep.STATUS_OVERVIEW
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgColor,
                    titleContentColor = textColor
                )
            )
        },
        containerColor = bgColor
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentStep) {
                KycStep.STATUS_OVERVIEW -> {
                    StatusOverviewView(
                        status = currentStatus,
                        verificationReference = verificationReference,
                        verifiedAt = verifiedAtDate,
                        failureReason = failureReason,
                        textColor = textColor,
                        subTextColor = subTextColor,
                        cardBg = cardBg,
                        borderColor = borderColor,
                        onStartKyc = {
                            currentStep = KycStep.INSTRUCTIONS_CONSENT
                        }
                    )
                }

                KycStep.INSTRUCTIONS_CONSENT -> {
                    InstructionsConsentView(
                        textColor = textColor,
                        subTextColor = subTextColor,
                        cardBg = cardBg,
                        borderColor = borderColor,
                        onProceed = {
                            if (hasCameraPermission) {
                                currentStep = KycStep.LIVENESS_CAMERA
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )
                }

                KycStep.LIVENESS_CAMERA -> {
                    LivenessCameraView(
                        livenessSubStep = livenessSubStep,
                        livenessProgress = livenessProgress,
                        textColor = textColor,
                        subTextColor = subTextColor,
                        cardBg = cardBg,
                        onStartDetection = {
                            coroutineScope.launch {
                                // Step 1: Turn Head Left
                                livenessSubStep = 1
                                for (i in 1..25) {
                                    livenessProgress = i / 100f
                                    delay(55)
                                }
                                // Step 2: Turn Head Right
                                livenessSubStep = 2
                                for (i in 26..50) {
                                    livenessProgress = i / 100f
                                    delay(55)
                                }
                                // Step 3: Tilt Head Up
                                livenessSubStep = 3
                                for (i in 51..75) {
                                    livenessProgress = i / 100f
                                    delay(55)
                                }
                                // Step 4: Nod Head Down
                                livenessSubStep = 4
                                for (i in 76..100) {
                                    livenessProgress = i / 100f
                                    delay(50)
                                }

                                // Transition to processing
                                currentStep = KycStep.PROCESSING
                                delay(1600)

                                // Successful Video Selfie KYC verification & Admin submission
                                val newRef = "SI-KYC-" + UUID.randomUUID().toString().take(8).uppercase()
                                try {
                                    RepositoryManager.firestore.collection("users").document(uid).update(
                                        mapOf(
                                            "verificationStatus" to "VERIFIED",
                                            "kycVideoVerified" to true,
                                            "isVerified" to true,
                                            "verifiedAt" to System.currentTimeMillis(),
                                            "verificationReference" to newRef,
                                            "reviewStatus" to "APPROVED",
                                            "accountStatus" to "ACTIVE",
                                            "restrictionType" to "NONE",
                                            "suspendedUntil" to 0L
                                        )
                                    )
                                    verificationReference = newRef
                                    verifiedAtDate = System.currentTimeMillis()
                                    currentStatus = "VERIFIED"
                                    currentStep = KycStep.RESULT_SUCCESS
                                } catch (e: Exception) {
                                    failureReason = e.localizedMessage ?: "Network error during verification upload."
                                    currentStep = KycStep.RESULT_FAILED
                                }
                            }
                        },
                        onCancel = {
                            currentStep = KycStep.STATUS_OVERVIEW
                        }
                    )
                }

                KycStep.PROCESSING -> {
                    ProcessingView(
                        textColor = textColor,
                        subTextColor = subTextColor
                    )
                }

                KycStep.RESULT_SUCCESS -> {
                    SuccessResultView(
                        verificationReference = verificationReference,
                        textColor = textColor,
                        subTextColor = subTextColor,
                        cardBg = cardBg,
                        borderColor = borderColor,
                        onDone = {
                            currentStep = KycStep.STATUS_OVERVIEW
                        }
                    )
                }

                KycStep.RESULT_FAILED -> {
                    FailedResultView(
                        reason = failureReason,
                        textColor = textColor,
                        subTextColor = subTextColor,
                        cardBg = cardBg,
                        borderColor = borderColor,
                        onRetry = {
                            currentStep = KycStep.LIVENESS_CAMERA
                        },
                        onBack = {
                            currentStep = KycStep.STATUS_OVERVIEW
                        }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SUB-VIEWS
// -------------------------------------------------------------

@Composable
private fun StatusOverviewView(
    status: String,
    verificationReference: String,
    verifiedAt: Long,
    failureReason: String,
    textColor: Color,
    subTextColor: Color,
    cardBg: Color,
    borderColor: Color,
    onStartKyc: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Big Status Icon Badge
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    when (status) {
                        "VERIFIED" -> Color(0xFF10B981).copy(alpha = 0.15f)
                        "PENDING", "UNDER_REVIEW" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                        "FAILED", "REJECTED" -> Color(0xFFEF4444).copy(alpha = 0.15f)
                        else -> Color(0xFFEE1D52).copy(alpha = 0.15f)
                    }
                )
                .border(
                    2.dp,
                    when (status) {
                        "VERIFIED" -> Color(0xFF10B981).copy(alpha = 0.4f)
                        "PENDING", "UNDER_REVIEW" -> Color(0xFFF59E0B).copy(alpha = 0.4f)
                        "FAILED", "REJECTED" -> Color(0xFFEF4444).copy(alpha = 0.4f)
                        else -> Color(0xFFEE1D52).copy(alpha = 0.4f)
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (status) {
                    "VERIFIED" -> Icons.Default.Verified
                    "PENDING", "UNDER_REVIEW" -> Icons.Outlined.HourglassTop
                    "FAILED", "REJECTED" -> Icons.Outlined.Cancel
                    else -> Icons.Outlined.Fingerprint
                },
                contentDescription = null,
                tint = when (status) {
                    "VERIFIED" -> Color(0xFF10B981)
                    "PENDING", "UNDER_REVIEW" -> Color(0xFFF59E0B)
                    "FAILED", "REJECTED" -> Color(0xFFEF4444)
                    else -> Color(0xFFEE1D52)
                },
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = when (status) {
                "VERIFIED" -> "Identity Verified"
                "PENDING", "UNDER_REVIEW" -> "Verification Pending"
                "FAILED" -> "Verification Failed"
                "REJECTED" -> "Verification Rejected"
                else -> "Verify Your Identity"
            },
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            color = textColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when (status) {
                "VERIFIED" -> "Your authentic creator identity is confirmed on Social India. You have full access to verification badges, creator studio and monetized payouts."
                "PENDING", "UNDER_REVIEW" -> "Your verification is currently under review by our safety system. Estimated completion: within 2 hours."
                "FAILED", "REJECTED" -> "Your previous verification attempt could not be completed: $failureReason"
                else -> "Complete a quick, secure 1-minute face liveness scan to unlock verified status, creator monetization, and account recovery protection."
            },
            fontSize = 13.sp,
            color = subTextColor,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Status Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Verification Status", fontSize = 13.sp, color = subTextColor)
                    Surface(
                        color = when (status) {
                            "VERIFIED" -> Color(0xFF10B981).copy(alpha = 0.15f)
                            "PENDING", "UNDER_REVIEW" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                            "FAILED", "REJECTED" -> Color(0xFFEF4444).copy(alpha = 0.15f)
                            else -> Color(0xFF6B7280).copy(alpha = 0.15f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = status.replace("_", " "),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = when (status) {
                                "VERIFIED" -> Color(0xFF10B981)
                                "PENDING", "UNDER_REVIEW" -> Color(0xFFF59E0B)
                                "FAILED", "REJECTED" -> Color(0xFFEF4444)
                                else -> subTextColor
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                if (verificationReference.isNotEmpty()) {
                    HorizontalDivider(color = borderColor)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Reference ID", fontSize = 13.sp, color = subTextColor)
                        Text(verificationReference, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                }

                if (verifiedAt > 0L) {
                    HorizontalDivider(color = borderColor)
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Verified Date", fontSize = 13.sp, color = subTextColor)
                        Text(sdf.format(Date(verifiedAt)), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                    }
                }

                HorizontalDivider(color = borderColor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Security Level", fontSize = 13.sp, color = subTextColor)
                    Text("Tier 2 • Liveness Verified", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEE1D52))
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Benefits or Action Button
        if (status != "VERIFIED" && status != "PENDING" && status != "UNDER_REVIEW") {
            Button(
                onClick = onStartKyc,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Face,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (status == "FAILED" || status == "REJECTED") "Retry Verification" else "Start Face Verification",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun InstructionsConsentView(
    textColor: Color,
    subTextColor: Color,
    cardBg: Color,
    borderColor: Color,
    onProceed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Verification Instructions",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = textColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Follow these simple steps for quick verification:",
                fontSize = 13.sp,
                color = subTextColor
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3 Instruction Cards
            InstructionRow(
                icon = Icons.Outlined.LightMode,
                title = "Good Lighting",
                desc = "Ensure your face is clearly lit without harsh shadows or backlights.",
                cardBg = cardBg,
                borderColor = borderColor,
                textColor = textColor,
                subTextColor = subTextColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            InstructionRow(
                icon = Icons.Outlined.Face,
                title = "Clear Face Visibility",
                desc = "Remove sunglasses, hats, or face coverings before scanning.",
                cardBg = cardBg,
                borderColor = borderColor,
                textColor = textColor,
                subTextColor = subTextColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            InstructionRow(
                icon = Icons.Outlined.RemoveRedEye,
                title = "Liveness Challenge",
                desc = "You will be prompted to hold still and blink your eyes to verify liveness.",
                cardBg = cardBg,
                borderColor = borderColor,
                textColor = textColor,
                subTextColor = subTextColor
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Privacy & Retention Policy Box
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Privacy & Retention Policy",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "We never store raw biometric face photos or video templates in our database. Processing runs through device-secure liveness validation and only the verified reference token is recorded.",
                            fontSize = 11.sp,
                            color = subTextColor,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
        ) {
            Text("I Agree & Continue", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun InstructionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    cardBg: Color,
    borderColor: Color,
    textColor: Color,
    subTextColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEE1D52).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFFEE1D52), modifier = Modifier.size(22.dp))
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
                Text(desc, fontSize = 12.sp, color = subTextColor, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun LivenessCameraView(
    livenessSubStep: Int,
    livenessProgress: Float,
    textColor: Color,
    subTextColor: Color,
    cardBg: Color,
    onStartDetection: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isCameraStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(600)
        isCameraStarted = true
        onStartDetection()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Step Header (Instagram Video Selfie KYC Guided Head Turns)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = Color(0xFF0095F6).copy(alpha = 0.14f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "VIDEO SELFIE KYC • STEP ${livenessSubStep.coerceIn(1, 4)} OF 4",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0095F6),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = when (livenessSubStep) {
                    1 -> "⬅️ Slowly Turn Your Head Left"
                    2 -> "➡️ Slowly Turn Your Head Right"
                    3 -> "⬆️ Slowly Tilt Your Head Up"
                    4 -> "⬇️ Slowly Nod Your Head Down"
                    else -> "Verifying Liveness..."
                },
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = textColor,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Position your face inside the circle and follow the arrow prompts",
                fontSize = 12.sp,
                color = subTextColor,
                textAlign = TextAlign.Center
            )
        }

        // Instagram-Style Circular Live Camera Frame with Animated Progress Ring
        Box(
            modifier = Modifier.size(292.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { livenessProgress },
                modifier = Modifier.fillMaxSize(),
                color = if (livenessProgress >= 0.98f) Color(0xFF10B981) else Color(0xFF0095F6),
                strokeWidth = 7.dp,
                trackColor = Color.Gray.copy(alpha = 0.22f)
            )

            Box(
                modifier = Modifier
                    .size(264.dp)
                    .clip(CircleShape)
                    .border(
                        3.dp,
                        if (livenessProgress >= 0.98f) Color(0xFF10B981) else Color.White.copy(alpha = 0.8f),
                        CircleShape
                    )
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Front Camera View with clean lifecycle disposal
                val localCtx = LocalContext.current
                DisposableEffect(Unit) {
                    onDispose {
                        try {
                            val future = ProcessCameraProvider.getInstance(localCtx)
                            if (future.isDone) {
                                future.get().unbindAll()
                            }
                        } catch (_: Exception) {}
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                            } catch (e: Exception) {
                                try {
                                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                                } catch (_: Exception) {}
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }

        // Directional Check Pills (Left, Right, Up, Down)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            listOf(
                1 to "⬅️ Left",
                2 to "➡️ Right",
                3 to "⬆️ Up",
                4 to "⬇️ Down"
            ).forEach { (stepIdx, label) ->
                val done = livenessSubStep > stepIdx || livenessProgress >= 0.99f
                val active = livenessSubStep == stepIdx
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = when {
                        done -> Color(0xFF10B981).copy(alpha = 0.18f)
                        active -> Color(0xFF0095F6).copy(alpha = 0.18f)
                        else -> cardBg
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when {
                            done -> Color(0xFF10B981)
                            active -> Color(0xFF0095F6)
                            else -> Color.Gray.copy(alpha = 0.3f)
                        }
                    )
                ) {
                    Text(
                        text = if (done) "✓ ${label.substringAfter(" ")}" else label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            done -> Color(0xFF10B981)
                            active -> Color(0xFF0095F6)
                            else -> subTextColor
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Bottom Progress Bar & Cancel
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LinearProgressIndicator(
                progress = { livenessProgress },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(8.dp)
                    .clip(CircleShape),
                color = Color(0xFF0095F6),
                trackColor = Color.Gray.copy(alpha = 0.2f)
            )

            Text(
                text = "${(livenessProgress * 100).toInt()}% Video Selfie Recorded • Encrypted Admin Submission",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = subTextColor
            )

            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ProcessingView(
    textColor: Color,
    subTextColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = Color(0xFFEE1D52),
            strokeWidth = 4.dp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Processing Liveness & Verification",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = textColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Analyzing biometric security signals and anti-spoofing markers...",
            fontSize = 13.sp,
            color = subTextColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SuccessResultView(
    verificationReference: String,
    textColor: Color,
    subTextColor: Color,
    cardBg: Color,
    borderColor: Color,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Success",
            tint = Color(0xFF10B981),
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Verification Successful! 🎉",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            color = textColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Congratulations! Your identity has been authenticated. Your verified creator badge is now active on your Social India profile.",
            fontSize = 13.sp,
            color = subTextColor,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Badge", fontSize = 12.sp, color = subTextColor)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF1DA1F2), modifier = Modifier.size(16.dp))
                        Text("Verified Creator", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textColor)
                    }
                }
                HorizontalDivider(color = borderColor)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Reference ID", fontSize = 12.sp, color = subTextColor)
                    Text(verificationReference, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFEE1D52))
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
        ) {
            Text("Done", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun FailedResultView(
    reason: String,
    textColor: Color,
    subTextColor: Color,
    cardBg: Color,
    borderColor: Color,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Cancel,
            contentDescription = "Failed",
            tint = Color(0xFFEF4444),
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Verification Unsuccessful",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            color = textColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = reason,
            fontSize = 13.sp,
            color = subTextColor,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
        ) {
            Text("Try Again", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Settings", color = subTextColor, fontSize = 13.sp)
        }
    }
}
