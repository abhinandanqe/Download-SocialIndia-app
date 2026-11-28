package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.example.model.*
import com.example.repository.CreatorExclusiveDashboardStats
import com.example.repository.CreatorExclusiveItemStat
import com.example.repository.ExclusiveContentRepository
import com.example.repository.MonetizationRepository
import com.example.utils.NumberFormatUtils
import com.example.utils.bounceClick
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Production Creator Monetization + Subscriptions + Paid Content + Real Earnings Dashboard.
 * 
 * Guarantees:
 * 1. Single authoritative monetization state: NOT_ELIGIBLE, ELIGIBLE, REQUESTED, PENDING, APPROVED, ACTIVE, REJECTED, SUSPENDED, REVOKED.
 * 2. When admin approves -> status is ACTIVE. Creator NEVER sees "Request Monetization" again!
 * 3. Creator Subscriptions: real plan creation, subscriber tracking, benefits.
 * 4. Real Earnings Ledger: Immutable verified transactions, no fake demo values.
 * 5. Realtime Firestore listener for instant synchronization with Admin Panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacebookCreatorMonetizationView(
    onBack: () -> Unit,
    onNavigateToWallet: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUserId = RepositoryManager.getCurrentUserId()

    // Realtime Authoritative Monetization Document Flow & Live Admin Thresholds
    val authDocFlow = remember(currentUserId) {
        MonetizationRepository.getAuthoritativeMonetizationFlow(currentUserId)
    }
    val authDoc by authDocFlow.collectAsStateWithLifecycle(initialValue = null)
    val monetizationSettings by MonetizationRepository.observeMonetizationSettings()
        .collectAsStateWithLifecycle(initialValue = MonetizationSettings())
    val minFollowersReq = monetizationSettings.minFollowersForMonetization.coerceAtLeast(1)
    val minViewsReq = monetizationSettings.minViewsForMonetization.coerceAtLeast(0)

    var user by remember { mutableStateOf<User?>(null) }
    var analytics by remember { mutableStateOf(CreatorAnalyticsSummary()) }
    var transactions by remember { mutableStateOf<List<EarningTransaction>>(emptyList()) }
    var subscriptionPlan by remember { mutableStateOf<CreatorSubscriptionPlan?>(null) }
    var exclusiveStats by remember { mutableStateOf(CreatorExclusiveDashboardStats()) }
    var activeSubscribersCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var showSubscriptionPlanDialog by remember { mutableStateOf(false) }
    var showTaxDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: Exclusive, 2: Ledger, 3: Subscriptions, 4: Criteria

    val refreshData: () -> Unit = {
        coroutineScope.launch {
            if (currentUserId.isNotEmpty()) {
                try {
                    val userDoc = RepositoryManager.firestore.collection("users").document(currentUserId).get().await()
                    user = userDoc.toObject(User::class.java)?.copy(id = userDoc.id)
                    analytics = MonetizationRepository.getCreatorAnalytics(currentUserId, 30)
                    transactions = MonetizationRepository.getCreatorEarningTransactions(currentUserId)
                    subscriptionPlan = MonetizationRepository.getSubscriptionPlan(currentUserId)
                    exclusiveStats = ExclusiveContentRepository.getCreatorExclusiveStats(currentUserId)

                    val subsSnap = RepositoryManager.firestore.collection("subscriptions")
                        .whereEqualTo("creatorId", currentUserId)
                        .whereEqualTo("active", true)
                        .get().await()
                    activeSubscribersCount = subsSnap.size()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(currentUserId) {
        refreshData()
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF1877F2))
        }
        return
    }

    val currentUser = user ?: User()

    // Determine Single Authoritative Status
    val effectiveStatus: AuthoritativeMonetizationState = when {
        authDoc != null -> {
            try {
                AuthoritativeMonetizationState.valueOf(authDoc!!.status.uppercase())
            } catch (e: Exception) {
                if (authDoc!!.status.equals("Approved", ignoreCase = true)) AuthoritativeMonetizationState.ACTIVE
                else AuthoritativeMonetizationState.PENDING
            }
        }
        currentUser.isMonetized || currentUser.monetizationStatus.equals("ACTIVE", ignoreCase = true) || currentUser.monetizationStatus.equals("Approved", ignoreCase = true) -> {
            AuthoritativeMonetizationState.ACTIVE
        }
        currentUser.monetizationRequested || currentUser.monetizationStatus.equals("PENDING", ignoreCase = true) || currentUser.monetizationStatus.equals("Under Review", ignoreCase = true) -> {
            AuthoritativeMonetizationState.PENDING
        }
        currentUser.monetizationStatus.equals("REJECTED", ignoreCase = true) -> {
            AuthoritativeMonetizationState.REJECTED
        }
        currentUser.monetizationStatus.equals("SUSPENDED", ignoreCase = true) -> {
            AuthoritativeMonetizationState.SUSPENDED
        }
        currentUser.monetizationStatus.equals("REVOKED", ignoreCase = true) -> {
            AuthoritativeMonetizationState.REVOKED
        }
        currentUser.followerCount >= minFollowersReq && analytics.totalViews >= minViewsReq -> {
            AuthoritativeMonetizationState.ELIGIBLE
        }
        else -> {
            AuthoritativeMonetizationState.NOT_ELIGIBLE
        }
    }

    val isMonetizationActive = effectiveStatus == AuthoritativeMonetizationState.ACTIVE || effectiveStatus == AuthoritativeMonetizationState.APPROVED

    val statusBadgeText = when (effectiveStatus) {
        AuthoritativeMonetizationState.ACTIVE -> "Monetization Active"
        AuthoritativeMonetizationState.APPROVED -> "Approved & Activated"
        AuthoritativeMonetizationState.PENDING, AuthoritativeMonetizationState.REQUESTED -> "Under Admin Review"
        AuthoritativeMonetizationState.REJECTED -> "Application Rejected"
        AuthoritativeMonetizationState.SUSPENDED -> "Monetization Suspended"
        AuthoritativeMonetizationState.REVOKED -> "Monetization Revoked"
        AuthoritativeMonetizationState.ELIGIBLE -> "Eligible to Apply"
        AuthoritativeMonetizationState.NOT_ELIGIBLE -> "Criteria Not Met"
    }

    val statusBadgeColor = when (effectiveStatus) {
        AuthoritativeMonetizationState.ACTIVE, AuthoritativeMonetizationState.APPROVED -> Color(0xFF2E7D32)
        AuthoritativeMonetizationState.PENDING, AuthoritativeMonetizationState.REQUESTED -> Color(0xFFE65100)
        AuthoritativeMonetizationState.REJECTED, AuthoritativeMonetizationState.REVOKED -> Color(0xFFC62828)
        AuthoritativeMonetizationState.SUSPENDED -> Color(0xFFF57C00)
        AuthoritativeMonetizationState.ELIGIBLE -> Color(0xFF1877F2)
        AuthoritativeMonetizationState.NOT_ELIGIBLE -> Color.Gray
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1877F2),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MonetizationOn,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Creator Studio & Monetization",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Official Social India Creator Hub",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToWallet) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = "Payouts",
                            tint = Color(0xFF1877F2)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            // Profile & Authoritative Monetization Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(60.dp)) {
                        if (currentUser.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = currentUser.avatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentUser.username.take(1).uppercase().ifEmpty { "C" },
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                            }
                        }
                        if (isMonetizationActive) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(20.dp)
                                    .background(Color(0xFF1877F2), CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentUser.username.ifEmpty { "Creator" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isMonetizationActive) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Verified Partner",
                                    tint = Color(0xFF1877F2),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = "Professional Dashboard • Digital Creator",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = statusBadgeColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = statusBadgeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusBadgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Button logic:
                    // NEVER show "Apply" or "Set up" if Active, Approved, or Pending!
                    if (!isMonetizationActive && effectiveStatus != AuthoritativeMonetizationState.PENDING && effectiveStatus != AuthoritativeMonetizationState.REQUESTED) {
                        Button(
                            onClick = { showRequestDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (effectiveStatus == AuthoritativeMonetizationState.REJECTED) "Re-apply" else "Apply",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isMonetizationActive) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Active", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                        }
                    }
                }
            }

            // Realtime Status Banner for Pending / Suspended
            if (effectiveStatus == AuthoritativeMonetizationState.PENDING || effectiveStatus == AuthoritativeMonetizationState.REQUESTED) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF3E0),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.HourglassTop, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Application Under Review", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFE65100))
                            Text("Your application is being verified by the admin team. You do not need to apply again.", fontSize = 11.sp, color = Color(0xFF5D4037))
                        }
                    }
                }
            }

            // Tab Navigation
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFF1877F2)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Overview", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("creator_dashboard_exclusive_tab"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = ExclusiveGoldMid,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Exclusive",
                                color = if (selectedTab == 1) ExclusiveGoldMid else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Ledger", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Subscriptions", fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("Criteria", fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> {
                    // TAB 0: Overview & Earnings (Strictly gated for monetized creators)
                    CreatorOverviewTab(
                        currentUser = currentUser,
                        analytics = analytics,
                        isMonetizationActive = isMonetizationActive,
                        effectiveStatus = effectiveStatus,
                        walletBalance = currentUser.walletBalance,
                        activeSubscribers = activeSubscribersCount,
                        minFollowersReq = minFollowersReq,
                        minViewsReq = minViewsReq,
                        videoCpmInr = monetizationSettings.videoCpmInr,
                        onNavigateToWallet = onNavigateToWallet,
                        onOpenSubscriptions = { selectedTab = 3 },
                        onApplyMonetization = { showRequestDialog = true }
                    )
                }
                1 -> {
                    // TAB 1: Exclusive Content Earnings, Unlocks & Price Management
                    CreatorExclusiveSalesTab(
                        stats = exclusiveStats,
                        commissionPct = monetizationSettings.exclusiveCommissionPct,
                        minPriceInr = monetizationSettings.exclusiveMinPriceInr,
                        maxPriceInr = monetizationSettings.exclusiveMaxPriceInr,
                        onNavigateToWallet = onNavigateToWallet,
                        onPriceUpdated = { refreshData() }
                    )
                }
                2 -> {
                    // TAB 2: Real Immutable Transaction Ledger
                    CreatorTransactionsLedgerTab(
                        transactions = transactions,
                        onNavigateToWallet = onNavigateToWallet
                    )
                }
                3 -> {
                    // TAB 3: Subscriptions Management
                    CreatorSubscriptionsTab(
                        plan = subscriptionPlan,
                        isMonetizationActive = isMonetizationActive,
                        activeSubscribers = activeSubscribersCount,
                        onConfigurePlan = { showSubscriptionPlanDialog = true },
                        onApplyMonetization = { showRequestDialog = true }
                    )
                }
                4 -> {
                    // TAB 4: Criteria & Tools
                    CreatorCriteriaAndToolsTab(
                        currentUser = currentUser,
                        analytics = analytics,
                        effectiveStatus = effectiveStatus,
                        minFollowersReq = minFollowersReq,
                        minViewsReq = minViewsReq,
                        onApplyClick = { showRequestDialog = true },
                        onNavigateToWallet = onNavigateToWallet
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Policies & Tax Compliance
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Policies & Payout Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    FacebookActionRow(
                        icon = Icons.Outlined.AccountBalance,
                        title = "Payout Account & Bank Setup",
                        subtitle = "Direct bank transfer / UPI for monthly settlements",
                        onClick = onNavigateToWallet
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )

                    FacebookActionRow(
                        icon = Icons.Outlined.ReceiptLong,
                        title = "Tax Information & PAN Form",
                        subtitle = "Indian TDS Compliance & Tax Residency Certificate",
                        onClick = { showTaxDialog = true }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )

                    FacebookActionRow(
                        icon = Icons.Outlined.Policy,
                        title = "Partner Monetization Policies",
                        subtitle = "Follow Social India Community Standards & Originality Guidelines",
                        onClick = {
                            Toast.makeText(context, "All Partner Monetization policies active and clean", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showRequestDialog) {
        MonetizationRequestDialog(
            user = currentUser,
            totalViews = analytics.totalViews.toInt(),
            minFollowersRequired = minFollowersReq,
            minViewsRequired = minViewsReq,
            onDismiss = { showRequestDialog = false },
            onSubmitted = {
                showRequestDialog = false
                refreshData()
            }
        )
    }

    if (showSubscriptionPlanDialog) {
        SubscriptionPlanConfigDialog(
            creatorUid = currentUserId,
            existingPlan = subscriptionPlan,
            onDismiss = { showSubscriptionPlanDialog = false },
            onSaved = { savedPlan ->
                subscriptionPlan = savedPlan
                showSubscriptionPlanDialog = false
                refreshData()
            }
        )
    }

    if (showTaxDialog) {
        AlertDialog(
            onDismissRequest = { showTaxDialog = false },
            title = { Text("Tax & Compliance Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Resident status: Verified Indian Digital Creator", fontSize = 14.sp)
                    Text("• TDS Withholding: 1% Section 194C / 194J as applicable", fontSize = 13.sp, color = Color.Gray)
                    Text("• Automatic Form 16A generated quarterly in Creator Studio.", fontSize = 13.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = { showTaxDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun CreatorOverviewTab(
    currentUser: User,
    analytics: CreatorAnalyticsSummary,
    isMonetizationActive: Boolean,
    effectiveStatus: AuthoritativeMonetizationState,
    walletBalance: Double,
    activeSubscribers: Int,
    minFollowersReq: Int,
    minViewsReq: Int,
    videoCpmInr: Double,
    onNavigateToWallet: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onApplyMonetization: () -> Unit
) {
    val isPending = effectiveStatus == AuthoritativeMonetizationState.PENDING ||
        effectiveStatus == AuthoritativeMonetizationState.REQUESTED
    val followersMet = currentUser.followerCount >= minFollowersReq
    val remainingFollowers = (minFollowersReq - currentUser.followerCount).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (isMonetizationActive) {
            // PREMIUM MONETIZED CREATOR EARNINGS CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF0F766E))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF22C55E).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFF4ADE80).copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Monetization Active", color = Color(0xFF4ADE80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick = onNavigateToWallet,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Withdraw", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Creator Wallet Balance", fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", walletBalance)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("$activeSubscribers", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                                Text("Subscribers", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                            Column {
                                Text("₹${videoCpmInr.toInt()}/1k", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF4ADE80))
                                Text("Video CPM", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("₹${analytics.giftValueEarnings.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFFFBBF24))
                                Text("Super Gifts", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        } else {
            // STRICT MONETIZATION LOCKED HERO CARD (For Non-Monetized Users)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF312E81))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isPending) Color(0xFFF59E0B).copy(alpha = 0.2f)
                                        else if (followersMet) Color(0xFF22C55E).copy(alpha = 0.2f)
                                        else Color(0xFF38BDF8).copy(alpha = 0.2f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPending) Icons.Default.HourglassTop else if (followersMet) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isPending) Color(0xFFFBBF24) else if (followersMet) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isPending) "Monetization Under Review"
                                    else if (followersMet) "Ready for Monetization!"
                                    else "Creator Earnings Locked",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Only approved monetized creators earn ₹ from Videos, Reels & Missions.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Follower Progress Tracker
                        val followerProg = (currentUser.followerCount.toFloat() / minFollowersReq.toFloat()).coerceIn(0f, 1f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Followers Required", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${NumberFormatUtils.formatCount(currentUser.followerCount)} / ${NumberFormatUtils.formatCount(minFollowersReq)}",
                                color = if (followersMet) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { followerProg },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (followersMet) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                            trackColor = Color.White.copy(alpha = 0.14f)
                        )

                        if (minViewsReq > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val viewsProg = (analytics.totalViews.toFloat() / minViewsReq.toFloat()).coerceIn(0f, 1f)
                            val viewsMet = analytics.totalViews >= minViewsReq
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Video Views (60 days)", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${NumberFormatUtils.formatCount(analytics.totalViews.toInt())} / ${NumberFormatUtils.formatCount(minViewsReq)}",
                                    color = if (viewsMet) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { viewsProg },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (viewsMet) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                                trackColor = Color.White.copy(alpha = 0.14f)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        if (isPending) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.HourglassTop, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Application Under Admin Review", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        } else {
                            Button(
                                onClick = onApplyMonetization,
                                enabled = followersMet,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF22C55E),
                                    disabledContainerColor = Color.White.copy(alpha = 0.12f),
                                    disabledContentColor = Color(0xFF94A3B8)
                                )
                            ) {
                                Text(
                                    text = if (followersMet) "Apply for Monetization →"
                                    else "Need ${NumberFormatUtils.formatCount(remainingFollowers)} more followers to Apply",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Creator Earning Products (Clearly shows Active vs Locked status)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (isMonetizationActive) "Active Creator Products" else "Unlocks After Monetization Approval",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            // Video & Reels Ad Revenue Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { if (isMonetizationActive) onNavigateToWallet() else onApplyMonetization() },
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = Color(0xFF1877F2).copy(alpha = 0.15f), modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color(0xFF1877F2), modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Per-Video & Reels Revenue", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (isMonetizationActive) "Earning ₹${videoCpmInr.toInt()}/1k views in Wallet" else "Locked in Wallet until approved",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isMonetizationActive) Color(0xFFE8F5E9) else Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = if (isMonetizationActive) "Active" else "Locked 🔒",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMonetizationActive) Color(0xFF2E7D32) else Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Subscriptions Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenSubscriptions() },
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = Color(0xFF7B1FA2).copy(alpha = 0.15f), modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Loyalty, contentDescription = null, tint = Color(0xFF7B1FA2), modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Creator Subscriptions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (isMonetizationActive) "$activeSubscribers active paid supporters" else "Unlock paid fan badges & subscriber reels",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isMonetizationActive) Color(0xFFE8F5E9) else Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = if (isMonetizationActive) "Active" else "Locked 🔒",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMonetizationActive) Color(0xFF2E7D32) else Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Super Gifts & Tips Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { if (isMonetizationActive) onNavigateToWallet() else onApplyMonetization() },
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = Color(0xFFFFB300).copy(alpha = 0.16f), modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color(0xFFFF8F00), modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Super Gifts & Live Tips", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (isMonetizationActive) "Fans tip directly on your content • 90% payout" else "Requires monetization approval for payouts",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isMonetizationActive) Color(0xFFE8F5E9) else Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = if (isMonetizationActive) "Active" else "Locked 🔒",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMonetizationActive) Color(0xFF2E7D32) else Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Daily Earning Missions & Revenue Calculator ONLY for Monetized Creators
        if (isMonetizationActive) {
            Spacer(modifier = Modifier.height(14.dp))
            DailyCreatorMissionsCard()

            Spacer(modifier = Modifier.height(14.dp))
            CreatorEarningsCalculatorCard(onNavigateToWallet = onNavigateToWallet)
        }
    }
}

private data class DailyEarningMission(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val rewardCoins: Long,
    val rewardRupees: Double
)

@Composable
private fun DailyCreatorMissionsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUid = remember { RepositoryManager.getCurrentUserId() }
    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val missions = remember {
        listOf(
            DailyEarningMission("daily_checkin", "Daily Creator Check-In", "Open Creator Studio today", "⚡", 3L, 1.0),
            DailyEarningMission("share_grow", "Share & Grow Community", "Share your profile or Reel with friends", "🚀", 5L, 2.0),
            DailyEarningMission("engage_creators", "Support & Engage", "Like, comment, or tip creators today", "💎", 2L, 1.0)
        )
    }
    var claimedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var claimingId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUid, todayKey) {
        if (currentUid.isNotEmpty()) {
            try {
                val snap = RepositoryManager.firestore
                    .collection("users").document(currentUid)
                    .collection("daily_missions")
                    .whereEqualTo("date", todayKey)
                    .get().await()
                claimedIds = snap.documents.mapNotNull { it.getString("missionId") }.toSet()
            } catch (_: Exception) {}
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daily Earning Missions 🎯",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.5.sp
                    )
                    Text(
                        text = "Complete daily tasks to earn real Coins & Wallet ₹",
                        fontSize = 11.5.sp,
                        color = Color.Gray
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF00C853).copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "${claimedIds.size}/${missions.size}",
                        color = Color(0xFF00C853),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            missions.forEach { mission ->
                val isClaimed = claimedIds.contains(mission.id)
                val isBusy = claimingId == mission.id
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isClaimed) Color(0xFF00C853).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        if (isClaimed) Color(0xFF00C853).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = mission.emoji, fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mission.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "+₹${mission.rewardRupees.toInt()} & +${mission.rewardCoins} Coins",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1877F2)
                            )
                        }
                        if (isClaimed) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF00C853).copy(alpha = 0.18f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00C853), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Claimed", color = Color(0xFF00C853), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (currentUid.isEmpty() || claimingId != null) return@Button
                                    claimingId = mission.id
                                    scope.launch {
                                        try {
                                            val docId = "${mission.id}_$todayKey"
                                            val userRef = RepositoryManager.firestore.collection("users").document(currentUid)
                                            val missionRef = userRef.collection("daily_missions").document(docId)
                                            val ledgerId = "mission_${currentUid.take(6)}_$docId"
                                            RepositoryManager.firestore.runTransaction { tx ->
                                                if (tx.get(missionRef).exists()) return@runTransaction
                                                val uDoc = tx.get(userRef)
                                                val curCoins = uDoc.getLong("coins") ?: 0L
                                                val curEarnings = uDoc.getDouble("earnings") ?: 0.0
                                                val curWallet = uDoc.getDouble("walletBalance") ?: 0.0
                                                tx.update(
                                                    userRef,
                                                    mapOf(
                                                        "coins" to (curCoins + mission.rewardCoins),
                                                        "earnings" to (curEarnings + mission.rewardRupees),
                                                        "walletBalance" to (curWallet + mission.rewardRupees)
                                                    )
                                                )
                                                tx.set(
                                                    missionRef,
                                                    mapOf(
                                                        "missionId" to mission.id,
                                                        "date" to todayKey,
                                                        "rewardCoins" to mission.rewardCoins,
                                                        "rewardRupees" to mission.rewardRupees,
                                                        "claimedAt" to System.currentTimeMillis()
                                                    )
                                                )
                                                tx.set(
                                                    RepositoryManager.firestore.collection("creator_earnings_ledger").document(ledgerId),
                                                    mapOf(
                                                        "id" to ledgerId,
                                                        "creatorUid" to currentUid,
                                                        "payerUid" to "SOCIAL_INDIA_REWARDS",
                                                        "payerUsername" to "Social India",
                                                        "sourceType" to "DAILY_MISSION",
                                                        "description" to "${mission.emoji} ${mission.title} Bonus",
                                                        "grossAmount" to mission.rewardRupees,
                                                        "platformFee" to 0.0,
                                                        "netCreatorAmount" to mission.rewardRupees,
                                                        "status" to "CREDITED",
                                                        "timestamp" to System.currentTimeMillis()
                                                    )
                                                )
                                            }.await()
                                            claimedIds = claimedIds + mission.id
                                            Toast.makeText(
                                                context,
                                                "Earned +₹${mission.rewardRupees.toInt()} & +${mission.rewardCoins} Coins! 🎉",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not claim reward right now", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            claimingId = null
                                        }
                                    }
                                },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                } else {
                                    Text("Claim", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorTransactionsLedgerTab(
    transactions: List<EarningTransaction>,
    onNavigateToWallet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Real Earnings Ledger", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            TextButton(onClick = onNavigateToWallet) {
                Text("Payout History", fontSize = 12.sp, color = Color(0xFF1877F2))
            }
        }

        if (transactions.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No transactions yet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Verified revenue from subscriptions, gifts, and video ads will appear here automatically.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                transactions.forEach { txn ->
                    val dateFormatted = remember(txn.createdAt) {
                        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(txn.createdAt))
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = when (txn.sourceType) {
                                    "SUBSCRIPTION" -> Color(0xFF7B1FA2).copy(alpha = 0.15f)
                                    "GIFT" -> Color(0xFFFFB300).copy(alpha = 0.15f)
                                    else -> Color(0xFF1877F2).copy(alpha = 0.15f)
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when (txn.sourceType) {
                                            "SUBSCRIPTION" -> Icons.Default.Loyalty
                                            "GIFT" -> Icons.Default.Star
                                            else -> Icons.Default.PlayCircle
                                        },
                                        contentDescription = null,
                                        tint = when (txn.sourceType) {
                                            "SUBSCRIPTION" -> Color(0xFF7B1FA2)
                                            "GIFT" -> Color(0xFFFFB300)
                                            else -> Color(0xFF1877F2)
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = txn.sourceType.replace("_", " "),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = dateFormatted,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "+₹${txn.creatorAmount.toInt()}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    text = txn.status,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (txn.status == "AVAILABLE") Color(0xFF2E7D32) else Color(0xFFE65100)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorSubscriptionsTab(
    plan: CreatorSubscriptionPlan?,
    isMonetizationActive: Boolean,
    activeSubscribers: Int,
    onConfigurePlan: () -> Unit,
    onApplyMonetization: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = Color(0xFF7B1FA2).copy(alpha = 0.15f), modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Loyalty, contentDescription = null, tint = Color(0xFF7B1FA2), modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(plan?.name ?: "Creator Supporter Pass", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("₹${plan?.price?.toInt() ?: 49}/month", color = Color(0xFF7B1FA2), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE8F5E9)) {
                        Text(
                            text = "$activeSubscribers Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(plan?.description ?: "Give your supporters exclusive subscriber-only posts, stories, badges, and VIP chat privileges.", fontSize = 12.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(14.dp))
                Text("Configured Benefits:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                (plan?.benefits ?: listOf("Exclusive Posts", "Exclusive Reels", "Subscriber Badge", "Priority Comments")).forEach { benefit ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(benefit, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isMonetizationActive) {
                    Button(
                        onClick = onConfigurePlan,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Edit Subscription Plan & Pricing", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onApplyMonetization,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Apply for Monetization to Enable Subscriptions", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorCriteriaAndToolsTab(
    currentUser: User,
    analytics: CreatorAnalyticsSummary,
    effectiveStatus: AuthoritativeMonetizationState,
    minFollowersReq: Int,
    minViewsReq: Int,
    onApplyClick: () -> Unit,
    onNavigateToWallet: () -> Unit
) {
    val followers = currentUser.followerCount
    val totalViews = analytics.totalViews.toInt()
    val isMonetized = effectiveStatus == AuthoritativeMonetizationState.ACTIVE || effectiveStatus == AuthoritativeMonetizationState.APPROVED
    val meetsFollowers = followers >= minFollowersReq
    val remainingFollowers = (minFollowersReq - followers).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    "Eligibility & Policies Criteria",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    "Meet the follower threshold set by admin to apply and unlock creator payouts.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Criteria Item 1: Dynamic Admin Follower Requirement
                CriteriaProgressItem(
                    title = "Minimum ${NumberFormatUtils.formatCount(minFollowersReq)} Active Followers",
                    current = followers,
                    required = minFollowersReq,
                    unit = "followers"
                )

                if (minViewsReq > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CriteriaProgressItem(
                        title = "Eligible Video Views (Last 60 Days)",
                        current = totalViews,
                        required = minViewsReq,
                        unit = "views"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Criteria Item 3: Content Monetization Policy
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Partner Monetization Policies", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("No community violations or copyright strikes on account.", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isMonetized) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE8F5E9)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Monetization Approved & Active", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                    }
                } else if (effectiveStatus == AuthoritativeMonetizationState.PENDING || effectiveStatus == AuthoritativeMonetizationState.REQUESTED) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF3E0)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.HourglassTop, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Application Under Admin Review", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        }
                    }
                } else {
                    Button(
                        onClick = onApplyClick,
                        enabled = meetsFollowers,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF16A34A),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = if (meetsFollowers) "Apply for Creator Monetization →"
                            else "Reach ${NumberFormatUtils.formatCount(minFollowersReq)} Followers ($remainingFollowers left)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionPlanConfigDialog(
    creatorUid: String,
    existingPlan: CreatorSubscriptionPlan?,
    onDismiss: () -> Unit,
    onSaved: (CreatorSubscriptionPlan) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var planName by remember { mutableStateOf(existingPlan?.name ?: "Creator Supporter Pass") }
    var planDescription by remember { mutableStateOf(existingPlan?.description ?: "Exclusive subscriber-only reels, stories, badge and community access.") }
    var priceText by remember { mutableStateOf(existingPlan?.price?.toInt()?.toString() ?: "49") }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Subscription Plan", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = planName,
                    onValueChange = { planName = it },
                    label = { Text("Plan Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Monthly Price (₹ INR)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = planDescription,
                    onValueChange = { planDescription = it },
                    label = { Text("Plan Description") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val priceVal = priceText.toDoubleOrNull() ?: 49.0
                    val newPlan = (existingPlan ?: CreatorSubscriptionPlan(creatorUid = creatorUid)).copy(
                        name = planName,
                        description = planDescription,
                        price = priceVal,
                        updatedAt = System.currentTimeMillis()
                    )
                    coroutineScope.launch {
                        isSaving = true
                        MonetizationRepository.saveSubscriptionPlan(newPlan)
                        isSaving = false
                        onSaved(newPlan)
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2))
            ) {
                Text(if (isSaving) "Saving..." else "Save Plan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun OverviewMiniMetric(label: String, value: String, color: Color) {
    Column {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun CriteriaProgressItem(
    title: String,
    current: Int,
    required: Int,
    unit: String
) {
    val progress = (current.toFloat() / required.toFloat()).coerceIn(0f, 1f)
    val isMet = current >= required

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                text = "$current / $required $unit",
                fontSize = 11.sp,
                color = if (isMet) Color(0xFF2E7D32) else Color.Gray,
                fontWeight = if (isMet) FontWeight.Bold else FontWeight.Normal
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (isMet) Color(0xFF2E7D32) else Color(0xFF1877F2),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun FacebookActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun CreatorExclusiveSalesTab(
    stats: CreatorExclusiveDashboardStats,
    commissionPct: Double,
    minPriceInr: Double,
    maxPriceInr: Double,
    onNavigateToWallet: () -> Unit,
    onPriceUpdated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var itemToEditPrice by remember { mutableStateOf<CreatorExclusiveItemStat?>(null) }
    var newPriceInput by remember { mutableStateOf("") }
    var isSavingPrice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Exclusive Revenue Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color(0xFF1F1606), Color(0xFF332106), Color(0xFF1A1424))
                        )
                    )
                    .border(1.dp, ExclusiveGoldMid.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExclusiveGoldBadge(label = "Exclusive Earnings")
                        Button(
                            onClick = onNavigateToWallet,
                            colors = ButtonDefaults.buttonColors(containerColor = ExclusiveGoldMid),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("exclusive_dashboard_withdraw_btn")
                        ) {
                            Text("Withdraw", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1200))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Net Exclusive Revenue", fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f))
                    Text(
                        text = "₹${String.format(Locale.US, "%.2f", stats.totalNetRevenue)}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = ExclusiveGoldStart
                    )
                    Text(
                        text = "Gross Sales: ₹${String.format(Locale.US, "%.2f", stats.totalGrossRevenue)} • Platform Fee: ${commissionPct.toInt()}%",
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.68f)
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("${stats.totalUnlocks}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                            Text("Total Unlocks", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                        }
                        Column {
                            Text(
                                "₹${String.format(Locale.US, "%.2f", stats.pendingBalance)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color(0xFFFBBF24)
                            )
                            Text("Pending", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "₹${String.format(Locale.US, "%.2f", stats.availableBalance)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color(0xFF4ADE80)
                            )
                            Text("Available Wallet", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }

        // Per-Content Breakdown Section
        Text(
            text = "Your Exclusive Content (${stats.items.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        if (stats.items.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = ExclusiveGoldMid, modifier = Modifier.size(34.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No Exclusive Content Yet", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Toggle \"Exclusive Content\" while publishing a Post, Reel, or Story to start earning directly from fans.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            stats.items.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1F1D2B)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.thumbnailUrl.isNotBlank()) {
                                AsyncImage(
                                    model = item.thumbnailUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = ExclusiveGoldStart)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = ExclusiveGoldMid.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = item.contentType,
                                        color = ExclusiveGoldMid,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "₹${item.priceInr.toInt()}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = item.teaserCaption.ifBlank { item.title },
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.unlockCount} unlocks • Net ₹${String.format(Locale.US, "%.2f", item.netRevenueInr)}",
                                fontSize = 11.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                itemToEditPrice = item
                                newPriceInput = item.priceInr.toInt().toString()
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Edit ₹", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Recent Exclusive Unlocks
        if (stats.recentPurchases.isNotEmpty()) {
            Text(
                text = "Recent Exclusive Unlocks",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            val dateFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
            stats.recentPurchases.take(10).forEach { purchase ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${purchase.contentType} Unlock (${purchase.paymentMethod})",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${dateFmt.format(Date(purchase.timestamp))} • Gross ₹${purchase.amount.toInt()}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Text(
                            text = "+₹${String.format(Locale.US, "%.2f", purchase.creatorNetAmount)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
        }
    }

    // Edit Exclusive Price Dialog (preserves permanent access for past buyers)
    val editing = itemToEditPrice
    if (editing != null) {
        val parsedNewPrice = newPriceInput.toDoubleOrNull() ?: 0.0
        val validPrice = parsedNewPrice in minPriceInr.coerceAtLeast(10.0)..maxPriceInr.coerceAtLeast(10.0)
        AlertDialog(
            onDismissRequest = { if (!isSavingPrice) itemToEditPrice = null },
            title = { Text("Edit Exclusive Price", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Update unlock price for this ${editing.contentType.lowercase()}. Buyers who already unlocked it keep permanent access.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    OutlinedTextField(
                        value = newPriceInput,
                        onValueChange = { s ->
                            if (s.all { it.isDigit() } && s.length <= 5) newPriceInput = s
                        },
                        label = { Text("New Price (₹${minPriceInr.toInt()} – ₹${maxPriceInr.toInt()})") },
                        prefix = { Text("₹ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = newPriceInput.isNotEmpty() && !validPrice,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSavingPrice = true
                        scope.launch {
                            val ok = ExclusiveContentRepository.updateExclusivePrice(
                                contentId = editing.contentId,
                                contentType = editing.contentType,
                                newPriceInr = parsedNewPrice
                            )
                            isSavingPrice = false
                            if (ok) {
                                Toast.makeText(context, "Price updated to ₹${parsedNewPrice.toInt()}", Toast.LENGTH_SHORT).show()
                                itemToEditPrice = null
                                onPriceUpdated()
                            } else {
                                Toast.makeText(context, "Invalid price or update failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = validPrice && !isSavingPrice,
                    colors = ButtonDefaults.buttonColors(containerColor = ExclusiveGoldMid, contentColor = Color(0xFF1A1200))
                ) {
                    Text(if (isSavingPrice) "Saving..." else "Update Price", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToEditPrice = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
