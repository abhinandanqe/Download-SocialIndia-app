package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.model.*
import com.example.viewmodel.VerificationUiState
import com.example.viewmodel.VerificationViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(
    onBack: () -> Unit,
    viewModel: VerificationViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val request by viewModel.currentRequest.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tickets by viewModel.supportTickets.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: Plans, 2: Benefits, 3: Support
    var showApplicationSheet by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showRenewalDialog by remember { mutableStateOf(false) }
    var showTicketDialog by remember { mutableStateOf(false) }

    val paymentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            Toast.makeText(context, "Payment Verified! Identity KYC form unlocked.", Toast.LENGTH_SHORT).show()
            viewModel.refresh()
            showApplicationSheet = true
        }
    }

    val onStartVerificationFlow: () -> Unit = {
        if (currentUser?.verificationPaid == true || request?.paymentStatus == "VERIFIED") {
            showApplicationSheet = true
        } else {
            val intent = Intent(context, SubscriptionActivity::class.java).apply {
                putExtra("purpose", "BLUE_TICK_VERIFICATION")
                putExtra("amount", "99")
            }
            paymentLauncher.launch(intent)
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is VerificationUiState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            is VerificationUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    // Determine Real Verification Status
    val now = System.currentTimeMillis()
    val isVerifiedUser = currentUser?.isVerified == true || currentUser?.verificationStatus == "approved"
    val isSuspended = currentUser?.verificationSuspended == true || request?.status == VerificationStatus.SUSPENDED
    val expiryTimestamp = if ((currentUser?.verificationExpiryTimestamp ?: 0L) > 0L) {
        currentUser?.verificationExpiryTimestamp ?: 0L
    } else {
        request?.expiryTimestamp ?: 0L
    }
    val isExpired = expiryTimestamp in 1..now

    val effectiveStatus = when {
        isSuspended -> VerificationStatus.SUSPENDED
        isExpired -> VerificationStatus.EXPIRED
        isVerifiedUser -> {
            val remainingDays = TimeUnit.MILLISECONDS.toDays(expiryTimestamp - now)
            if (expiryTimestamp > 0L && remainingDays <= 15) VerificationStatus.EXPIRING_SOON else VerificationStatus.VERIFIED_ACTIVE
        }
        request != null -> {
            when (request?.status) {
                VerificationStatus.PAYMENT_VERIFICATION -> VerificationStatus.PAYMENT_VERIFICATION
                VerificationStatus.PAYMENT_PENDING -> VerificationStatus.PAYMENT_PENDING
                VerificationStatus.REJECTED -> VerificationStatus.REJECTED
                else -> VerificationStatus.APPLICATION_SUBMITTED
            }
        }
        else -> VerificationStatus.NOT_VERIFIED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Social India Verification", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF1DA1F2), modifier = Modifier.size(20.dp))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Overview") },
                    icon = { Icon(Icons.Outlined.Badge, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Plans") },
                    icon = { Icon(Icons.Outlined.CardMembership, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Benefits") },
                    icon = { Icon(Icons.Outlined.VerifiedUser, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Support") },
                    icon = { Icon(Icons.Outlined.SupportAgent, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> VerificationOverviewContent(
                        status = effectiveStatus,
                        currentUser = currentUser,
                        request = request,
                        expiryTimestamp = expiryTimestamp,
                        onApplyClick = onStartVerificationFlow,
                        onPayClick = onStartVerificationFlow,
                        onRenewClick = { showRenewalDialog = true },
                        onSupportClick = { selectedTab = 3 }
                    )
                    1 -> VerificationPlansContent(
                        plans = plans,
                        currentStatus = effectiveStatus,
                        onSelectPlan = { plan ->
                            if (effectiveStatus == VerificationStatus.EXPIRED || effectiveStatus == VerificationStatus.EXPIRING_SOON || effectiveStatus == VerificationStatus.VERIFIED_ACTIVE) {
                                showRenewalDialog = true
                            } else {
                                onStartVerificationFlow()
                            }
                        }
                    )
                    2 -> VerificationBenefitsContent()
                    3 -> VerificationSupportContent(
                        tickets = tickets,
                        isVerified = isVerifiedUser && !isExpired && !isSuspended,
                        onCreateTicketClick = { showTicketDialog = true }
                    )
                }

                if (uiState is VerificationUiState.Loading) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = (uiState as VerificationUiState.Loading).message,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Multi-Step Application Modal
    if (showApplicationSheet) {
        VerificationApplicationDialog(
            currentUser = currentUser,
            plans = plans,
            onDismiss = { showApplicationSheet = false },
            onSubmit = { fullName, email, phone, country, category, type, bName, bCat, bContact, bEmail, bPhone, bWeb, bAddr, bReg, reason, docUri, selfieUri, bDocUri, plan ->
                viewModel.submitApplication(
                    fullName = fullName,
                    email = email,
                    phone = phone,
                    country = country,
                    category = category,
                    verificationType = type,
                    businessName = bName,
                    businessCategory = bCat,
                    businessContact = bContact,
                    businessEmail = bEmail,
                    businessPhone = bPhone,
                    businessWebsite = bWeb,
                    businessAddress = bAddr,
                    businessRegistrationNumber = bReg,
                    reason = reason,
                    documentUri = docUri,
                    selfieUri = selfieUri,
                    businessDocUri = bDocUri,
                    selectedPlan = plan
                )
                showApplicationSheet = false
            }
        )
    }

    // Manual Payment Submission Dialog
    if (showPaymentDialog) {
        VerificationPaymentDialog(
            request = request,
            settings = settings,
            onDismiss = { showPaymentDialog = false },
            onSubmit = { ref, proofUri ->
                viewModel.submitPayment(ref, proofUri)
                showPaymentDialog = false
            }
        )
    }

    // Renewal Dialog
    if (showRenewalDialog) {
        VerificationRenewalDialog(
            plans = plans,
            expiryTimestamp = expiryTimestamp,
            onDismiss = { showRenewalDialog = false },
            onConfirmRenew = { plan ->
                viewModel.submitRenewal(plan)
                showRenewalDialog = false
                showPaymentDialog = true
            }
        )
    }

    // Create Support Ticket Dialog
    if (showTicketDialog) {
        VerificationCreateTicketDialog(
            onDismiss = { showTicketDialog = false },
            onSubmit = { subject, message, category ->
                viewModel.createSupportTicket(subject, message, category)
                showTicketDialog = false
            }
        )
    }
}

// -------------------------------------------------------------------------
// OVERVIEW TAB
// -------------------------------------------------------------------------

@Composable
fun VerificationOverviewContent(
    status: String,
    currentUser: User?,
    request: VerificationRequest?,
    expiryTimestamp: Long,
    onApplyClick: () -> Unit,
    onPayClick: () -> Unit,
    onRenewClick: () -> Unit,
    onSupportClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val now = System.currentTimeMillis()
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Hero Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (status) {
                    VerificationStatus.VERIFIED_ACTIVE -> Color(0xFF0F9D58).copy(alpha = 0.12f)
                    VerificationStatus.EXPIRING_SOON, VerificationStatus.PAYMENT_PENDING -> Color(0xFFFF9800).copy(alpha = 0.12f)
                    VerificationStatus.EXPIRED, VerificationStatus.SUSPENDED, VerificationStatus.REJECTED -> Color(0xFFD32F2F).copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = when (status) {
                                VerificationStatus.VERIFIED_ACTIVE -> Color(0xFF1DA1F2)
                                VerificationStatus.EXPIRING_SOON -> Color(0xFFFF9800)
                                VerificationStatus.EXPIRED, VerificationStatus.SUSPENDED -> Color(0xFFD32F2F)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (status == VerificationStatus.VERIFIED_ACTIVE) Icons.Default.Verified else Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Current Status",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = when (status) {
                                    VerificationStatus.VERIFIED_ACTIVE -> "✓ Social India Verified"
                                    VerificationStatus.EXPIRING_SOON -> "Expiring Soon"
                                    VerificationStatus.EXPIRED -> "Verification Expired"
                                    VerificationStatus.SUSPENDED -> "Verification Suspended"
                                    VerificationStatus.PAYMENT_PENDING -> "Payment Pending"
                                    VerificationStatus.PAYMENT_VERIFICATION -> "Payment Review Pending"
                                    VerificationStatus.APPLICATION_SUBMITTED, VerificationStatus.UNDER_REVIEW -> "Application Under Review"
                                    VerificationStatus.REJECTED -> "Application Rejected"
                                    else -> "Not Verified"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }

                    // Badge Pill
                    Surface(
                        color = when (status) {
                            VerificationStatus.VERIFIED_ACTIVE -> Color(0xFF1DA1F2)
                            VerificationStatus.EXPIRING_SOON, VerificationStatus.PAYMENT_PENDING -> Color(0xFFFF9800)
                            VerificationStatus.EXPIRED, VerificationStatus.SUSPENDED, VerificationStatus.REJECTED -> Color(0xFFD32F2F)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = status.replace("_", " "),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (status == VerificationStatus.NOT_VERIFIED) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Detail Rows
                val vType = currentUser?.verificationType?.ifEmpty { request?.verificationType } ?: "Personal"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Verification Type", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(vType, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                if (request != null && request.id.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Application ID", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("#${request.id.take(8).uppercase()}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                if (status == VerificationStatus.VERIFIED_ACTIVE || status == VerificationStatus.EXPIRING_SOON || status == VerificationStatus.EXPIRED) {
                    val startTimestamp = currentUser?.verificationStartTimestamp ?: request?.startTimestamp ?: 0L
                    if (startTimestamp > 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Active Since", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateFormat.format(Date(startTimestamp)), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (expiryTimestamp > 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Expiration Date", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateFormat.format(Date(expiryTimestamp)), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        if (expiryTimestamp > now) {
                            val remainingDays = TimeUnit.MILLISECONDS.toDays(expiryTimestamp - now)
                            val remainingMonths = remainingDays / 30
                            val remainingStr = if (remainingMonths > 0) "$remainingMonths month(s), ${remainingDays % 30} day(s)" else "$remainingDays day(s)"
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Remaining Time", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(remainingStr, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (remainingDays <= 15) Color(0xFFFF9800) else Color(0xFF1DA1F2))
                            }
                        }
                    }
                }

                if (request != null && request.paymentReference.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Payment Ref", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(request.paymentReference, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                if (status == VerificationStatus.REJECTED && !request?.rejectionReason.isNullOrEmpty()) {
                    Text(
                        text = "Reason: ${request?.rejectionReason}",
                        fontSize = 12.sp,
                        color = Color(0xFFD32F2F)
                    )
                }

                if (status == VerificationStatus.SUSPENDED && !request?.suspensionReason.isNullOrEmpty()) {
                    Text(
                        text = "Suspension Reason: ${request?.suspensionReason}",
                        fontSize = 12.sp,
                        color = Color(0xFFD32F2F)
                    )
                }

                // CTA Button based on status
                Spacer(modifier = Modifier.height(4.dp))
                when (status) {
                    VerificationStatus.NOT_VERIFIED, VerificationStatus.REJECTED -> {
                        val isPaid = currentUser?.verificationPaid == true || request?.paymentStatus == "VERIFIED"
                        Button(
                            onClick = onApplyClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = if (isPaid) ButtonDefaults.buttonColors(containerColor = Color(0xFF0F9D58)) else ButtonDefaults.buttonColors(containerColor = Color(0xFF1DA1F2))
                        ) {
                            Icon(if (isPaid) Icons.Default.AssignmentInd else Icons.Default.Verified, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isPaid) "Complete Identity KYC Details" else "Get Meta Verified Badge (₹99/mo)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!isPaid) {
                            Text(
                                text = "🔒 Automated UPI payment required first to unlock official KYC identity submission (Govt ID + Selfie) for Official Social India Verification.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    VerificationStatus.PAYMENT_PENDING -> {
                        Button(
                            onClick = onPayClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submit Payment Reference", fontWeight = FontWeight.Bold)
                        }
                    }
                    VerificationStatus.PAYMENT_VERIFICATION -> {
                        OutlinedButton(
                            onClick = onPayClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update Payment Info", fontWeight = FontWeight.Bold)
                        }
                    }
                    VerificationStatus.EXPIRING_SOON, VerificationStatus.EXPIRED -> {
                        Button(
                            onClick = onRenewClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Autorenew, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Renew Verification Now", fontWeight = FontWeight.Bold)
                        }
                    }
                    VerificationStatus.VERIFIED_ACTIVE -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onRenewClick,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Renew Plan", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onSupportClick,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("VIP Support", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Business Account Eligibility Callout (Part 14)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Outlined.Storefront,
                        contentDescription = null,
                        tint = if (status == VerificationStatus.VERIFIED_ACTIVE) Color(0xFF1DA1F2) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Business Account Eligibility", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text(
                    text = if (status == VerificationStatus.VERIFIED_ACTIVE) {
                        "✓ Your verified status unlocks full Business Account, advertising suite, and verified storefront eligibility."
                    } else {
                        "Business Accounts on Social India require an active verified badge. Complete verification to activate company tools and enterprise profiles."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Key Value Props
        Text("Why Get Verified on Social India?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        ValuePropRow(
            icon = Icons.Default.Verified,
            title = "Official Identity Authenticity",
            desc = "Display the authentic blue badge across Feed, Reels, Stories, Search, Profile, and Comments."
        )
        ValuePropRow(
            icon = Icons.Default.Security,
            title = "Impersonation Protection",
            desc = "Proactive monitoring prevents copycat accounts and protects your personal or brand reputation."
        )
        ValuePropRow(
            icon = Icons.Default.SupportAgent,
            title = "Priority Support Queue",
            desc = "Access dedicated fast-track support tickets with guaranteed human response times."
        )
    }
}

@Composable
fun ValuePropRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF1DA1F2).copy(alpha = 0.15f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color(0xFF1DA1F2), modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// -------------------------------------------------------------------------
// PLANS TAB (PART 5)
// -------------------------------------------------------------------------

@Composable
fun VerificationPlansContent(
    plans: List<VerificationPlan>,
    currentStatus: String,
    onSelectPlan: (VerificationPlan) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Verification Subscription Plans", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Choose the verification duration that fits your creative or business goals. Fully managed with manual admin payment verification.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(plans) { plan ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (plan.popularBadge.isNotEmpty()) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                border = if (plan.popularBadge.isNotEmpty()) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(plan.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${plan.durationMonths} Month Duration", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (plan.popularBadge.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    plan.popularBadge,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "₹${plan.price.toInt()}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    plan.benefits.forEach { benefit ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF0F9D58), modifier = Modifier.size(16.dp))
                            Text(benefit, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { onSelectPlan(plan) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (currentStatus == VerificationStatus.VERIFIED_ACTIVE) "Renew with this Plan" else "Select ${plan.name}",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// BENEFITS TAB (PART 11)
// -------------------------------------------------------------------------

@Composable
fun VerificationBenefitsContent() {
    val benefitsList = listOf(
        Pair("Verified Badge", "An authentic blue checkmark displayed beside your name across all app surfaces."),
        Pair("Authenticity Signal", "Clear official confirmation that Social India has authenticated your identity."),
        Pair("Impersonation Defense", "Continuous account surveillance against duplicate accounts and fraudsters."),
        Pair("Verified Business Suite", "Exclusive unlock to activate Business Profiles, hours, contact links, and ads."),
        Pair("Priority Support", "Fast response times from dedicated account representatives."),
        Pair("Creator Analytics", "Deeper real-time insights on your viewer retention and profile visits."),
        Pair("Collaboration Trust", "Higher conversion when reaching out to brands and fellow creators."),
        Pair("Early Feature Access", "Test upcoming tools, broadcast channel upgrades, and studio features first.")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Social India Verified Benefits", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Authentic platform tools and official trust signals provided with active verification.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        items(benefitsList) { item ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF1DA1F2), modifier = Modifier.size(22.dp))
                    Column {
                        Text(item.first, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(item.second, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// SUPPORT TAB (PART 20)
// -------------------------------------------------------------------------

@Composable
fun VerificationSupportContent(
    tickets: List<VerificationSupportTicket>,
    isVerified: Boolean,
    onCreateTicketClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SupportAgent, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Verification Support Desk", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(
                    text = if (isVerified) {
                        "★ Priority VIP Support Active: Your tickets are routed to our senior verification team with priority response."
                    } else {
                        "Submit questions regarding verification applications, payments, or identity guidelines."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onCreateTicketClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Support Ticket", fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("Your Tickets", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        if (tickets.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No support tickets submitted yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tickets) { ticket ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ticket.subject, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Surface(
                                    color = if (ticket.status == "RESOLVED") Color(0xFF0F9D58) else Color(0xFFFF9800),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        ticket.status,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(ticket.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateFormat.format(Date(ticket.createdAt)), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))

                            if (ticket.adminReply.isNotEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Admin Response:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(ticket.adminReply, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// MULTI-STEP VERIFICATION APPLICATION (PART 3)
// -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationApplicationDialog(
    currentUser: User?,
    plans: List<VerificationPlan>,
    onDismiss: () -> Unit,
    onSubmit: (
        fullName: String,
        email: String,
        phone: String,
        country: String,
        category: String,
        type: String,
        bName: String,
        bCat: String,
        bContact: String,
        bEmail: String,
        bPhone: String,
        bWeb: String,
        bAddr: String,
        bReg: String,
        reason: String,
        docUri: Uri?,
        selfieUri: Uri?,
        bDocUri: Uri?,
        selectedPlan: VerificationPlan
    ) -> Unit
) {
    var step by remember { mutableIntStateOf(1) } // 1: Type, 2: Account Info, 3: Identity/Biz, 4: Docs, 5: Review & Plan

    var selectedType by remember { mutableStateOf(VerificationType.PERSONAL) }
    var fullName by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var email by remember { mutableStateOf(currentUser?.email ?: "") }
    var phone by remember { mutableStateOf(currentUser?.businessPhone ?: "") }
    var country by remember { mutableStateOf("India") }
    var category by remember { mutableStateOf(currentUser?.category?.ifEmpty { null } ?: "Creator / Influencer") }

    // Business fields
    var businessName by remember { mutableStateOf(currentUser?.businessName ?: "") }
    var businessCategory by remember { mutableStateOf(currentUser?.businessCategory ?: "E-Commerce & Retail") }
    var businessContact by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var businessEmail by remember { mutableStateOf(currentUser?.businessEmail ?: currentUser?.email ?: "") }
    var businessPhone by remember { mutableStateOf(currentUser?.businessPhone ?: "") }
    var businessWebsite by remember { mutableStateOf(currentUser?.businessWebsite ?: "") }
    var businessAddress by remember { mutableStateOf(currentUser?.businessAddress ?: "") }
    var businessRegNumber by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    // Document Uris
    var docUri by remember { mutableStateOf<Uri?>(null) }
    var selfieUri by remember { mutableStateOf<Uri?>(null) }
    var bizDocUri by remember { mutableStateOf<Uri?>(null) }

    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { docUri = it }
    val selfieLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selfieUri = it }
    val bizDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { bizDocUri = it }

    val defaultSelectedPlan = plans.firstOrNull { it.durationMonths == 12 } ?: plans.firstOrNull() ?: VerificationPlan(name = "Annual", price = 4499.0)
    var selectedPlan by remember { mutableStateOf(defaultSelectedPlan) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Apply for Verification", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Step $step of 5", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                LinearProgressIndicator(
                    progress = { step / 5f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clip(CircleShape)
                )

                // Body content based on step
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (step) {
                        1 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Select Verification Type", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Choose the profile identity that matches your online presence.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                listOf(
                                    Triple(VerificationType.PERSONAL, "Personal", "For notable individuals, journalists, and public figures"),
                                    Triple(VerificationType.CREATOR, "Creator", "For digital video creators, artists, musicians & influencers"),
                                    Triple(VerificationType.BUSINESS, "Business", "For registered brands, local businesses, companies & startups")
                                ).forEach { (typeKey, title, desc) ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selectedType == typeKey) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        border = if (selectedType == typeKey) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedType = typeKey }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            RadioButton(selected = selectedType == typeKey, onClick = { selectedType = typeKey })
                                            Column {
                                                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Account Information", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Verify the account details to link to your verified badge.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                OutlinedTextField(
                                    value = fullName,
                                    onValueChange = { fullName = it },
                                    label = { Text("Full Legal Name *") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Account Email *") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it },
                                    label = { Text("Contact Phone *") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = country,
                                    onValueChange = { country = it },
                                    label = { Text("Country") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        3 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = if (selectedType == VerificationType.BUSINESS) "Business Information" else "Category & Identity Information",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )

                                if (selectedType == VerificationType.BUSINESS) {
                                    OutlinedTextField(
                                        value = businessName,
                                        onValueChange = { businessName = it },
                                        label = { Text("Business / Company Name *") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = businessCategory,
                                        onValueChange = { businessCategory = it },
                                        label = { Text("Business Category *") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = businessContact,
                                        onValueChange = { businessContact = it },
                                        label = { Text("Authorized Contact Person") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = businessWebsite,
                                        onValueChange = { businessWebsite = it },
                                        label = { Text("Company Website") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = businessAddress,
                                        onValueChange = { businessAddress = it },
                                        label = { Text("Official Business Address") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = businessRegNumber,
                                        onValueChange = { businessRegNumber = it },
                                        label = { Text("GST / Registration Number (Optional)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = category,
                                        onValueChange = { category = it },
                                        label = { Text("Primary Category *") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = reason,
                                        onValueChange = { reason = it },
                                        label = { Text("Tell us about your work or online presence *") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3
                                    )
                                }
                            }
                        }

                        4 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Supporting Documents", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("All identity documents are securely encrypted and reviewed exclusively by authorized administrators. Never exposed to public users.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                DocUploadCard(
                                    title = "Government Photo ID *",
                                    desc = "Aadhaar Card, Passport, Driver's License or PAN Card",
                                    uri = docUri,
                                    onClick = { docLauncher.launch("image/*") }
                                )

                                DocUploadCard(
                                    title = "Identity Confirmation Selfie (Optional)",
                                    desc = "Clear photo to confirm physical ownership",
                                    uri = selfieUri,
                                    onClick = { selfieLauncher.launch("image/*") }
                                )

                                if (selectedType == VerificationType.BUSINESS) {
                                    DocUploadCard(
                                        title = "Business Registration / License",
                                        desc = "GST Certificate, Trade License, or Incorporation Document",
                                        uri = bizDocUri,
                                        onClick = { bizDocLauncher.launch("image/*") }
                                    )
                                }
                            }
                        }

                        5 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Review & Select Plan", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                                // Summary Card
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Application Summary", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Type: $selectedType", fontSize = 13.sp)
                                        Text("Legal Name: $fullName", fontSize = 13.sp)
                                        if (selectedType == VerificationType.BUSINESS) {
                                            Text("Business: $businessName ($businessCategory)", fontSize = 13.sp)
                                        } else {
                                            Text("Category: $category", fontSize = 13.sp)
                                        }
                                        Text("Country: $country", fontSize = 13.sp)
                                    }
                                }

                                Text("Select Verification Plan", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                plans.forEach { plan ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selectedPlan.id == plan.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        border = if (selectedPlan.id == plan.id) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedPlan = plan }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                RadioButton(selected = selectedPlan.id == plan.id, onClick = { selectedPlan = plan })
                                                Column {
                                                    Text(plan.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Text("${plan.durationMonths} Months", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Text("₹${plan.price.toInt()}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer Navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 1) {
                        OutlinedButton(onClick = { step-- }) {
                            Text("Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (step < 5) {
                        Button(
                            onClick = {
                                if (step == 2 && fullName.trim().isEmpty()) return@Button
                                step++
                            }
                        ) {
                            Text("Continue")
                        }
                    } else {
                        Button(
                            onClick = {
                                onSubmit(
                                    fullName, email, phone, country, category, selectedType,
                                    businessName, businessCategory, businessContact, businessEmail,
                                    businessPhone, businessWebsite, businessAddress, businessRegNumber,
                                    reason, docUri, selfieUri, bizDocUri, selectedPlan
                                )
                            }
                        ) {
                            Text("Submit Application", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DocUploadCard(title: String, desc: String, uri: Uri?, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (uri != null) Color(0xFF0F9D58).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, if (uri != null) Color(0xFF0F9D58) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (uri != null) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                contentDescription = null,
                tint = if (uri != null) Color(0xFF0F9D58) else MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(if (uri != null) "File attached (tap to replace)" else desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// -------------------------------------------------------------------------
// MANUAL PAYMENT DIALOG (PART 6)
// -------------------------------------------------------------------------

@Composable
fun VerificationPaymentDialog(
    request: VerificationRequest?,
    settings: VerificationSettings,
    onDismiss: () -> Unit,
    onSubmit: (reference: String, proofUri: Uri?) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var paymentRef by remember { mutableStateOf(request?.paymentReference ?: "") }
    var proofUri by remember { mutableStateOf<Uri?>(null) }
    val proofLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { proofUri = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Payment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Verification Payment", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Plan info
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(request?.planName ?: "Verified Plan", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = "Amount: ₹${request?.planPrice?.toInt() ?: 4499} for ${request?.planDurationMonths ?: 12} Months",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                    }
                }

                // Admin Payment Instructions
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Payment Instructions", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(settings.paymentInstructions, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                        // UPI ID with Copy
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Official UPI ID", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(settings.upiId, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(settings.upiId)) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy UPI", modifier = Modifier.size(18.dp))
                            }
                        }

                        // Bank Account Info
                        Text("Bank Details:\n${settings.bankAccountDetails}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Reference Input
                OutlinedTextField(
                    value = paymentRef,
                    onValueChange = { paymentRef = it },
                    label = { Text("Transaction Reference ID / UTR *") },
                    placeholder = { Text("e.g. UPI Ref 312345678901") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Proof Upload
                DocUploadCard(
                    title = "Payment Screenshot / Proof (Optional)",
                    desc = "Attach bank confirmation receipt",
                    uri = proofUri,
                    onClick = { proofLauncher.launch("image/*") }
                )

                Text(
                    text = "Important: Payments are verified manually by an admin. Your badge will activate immediately after approval.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (paymentRef.trim().isNotEmpty()) {
                        onSubmit(paymentRef.trim(), proofUri)
                    }
                },
                enabled = paymentRef.trim().isNotEmpty()
            ) {
                Text("Submit Payment", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

// -------------------------------------------------------------------------
// RENEWAL DIALOG (PART 10)
// -------------------------------------------------------------------------

@Composable
fun VerificationRenewalDialog(
    plans: List<VerificationPlan>,
    expiryTimestamp: Long,
    onDismiss: () -> Unit,
    onConfirmRenew: (VerificationPlan) -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val defaultPlan = plans.firstOrNull { it.durationMonths == 12 } ?: plans.firstOrNull() ?: VerificationPlan(name = "12 Months", price = 4499.0)
    var selectedPlan by remember { mutableStateOf(defaultPlan) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Renew Verification", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (expiryTimestamp > 0L) {
                    Text(
                        text = "Current Expiry: ${dateFormat.format(Date(expiryTimestamp))}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Renewing your plan extends your blue checkmark badge, business account access, and verified trust signals.",
                    fontSize = 13.sp
                )

                Text("Select Renewal Duration:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                plans.forEach { plan ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedPlan.id == plan.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (selectedPlan.id == plan.id) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPlan = plan }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RadioButton(selected = selectedPlan.id == plan.id, onClick = { selectedPlan = plan })
                                Column {
                                    Text(plan.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("${plan.durationMonths} Months Extension", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("₹${plan.price.toInt()}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirmRenew(selectedPlan) }) {
                Text("Continue to Payment", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

// -------------------------------------------------------------------------
// CREATE TICKET DIALOG (PART 20)
// -------------------------------------------------------------------------

@Composable
fun VerificationCreateTicketDialog(
    onDismiss: () -> Unit,
    onSubmit: (subject: String, message: String, category: String) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Verification Inquiries") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Support Ticket", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject *") },
                    placeholder = { Text("e.g. Question about payment or document") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Describe your issue *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(subject, message, category) },
                enabled = subject.trim().isNotEmpty() && message.trim().isNotEmpty()
            ) {
                Text("Submit Ticket", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
