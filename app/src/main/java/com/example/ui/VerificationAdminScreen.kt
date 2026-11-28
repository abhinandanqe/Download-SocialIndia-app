package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationAdminScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repo = RepositoryManager.verificationRepository
    val adminId = RepositoryManager.getCurrentUserId()

    var requests by remember { mutableStateOf<List<VerificationRequest>>(emptyList()) }
    var plans by remember { mutableStateOf<List<VerificationPlan>>(emptyList()) }
    var settings by remember { mutableStateOf(VerificationSettings()) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("All", "Pending", "Payment Pending", "Approved", "Rejected", "Expired", "Suspended", "Renewal", "Settings")

    var searchQuery by remember { mutableStateOf("") }
    var selectedRequestForDetail by remember { mutableStateOf<VerificationRequest?>(null) }
    var showActionDialog by remember { mutableStateOf<AdminActionType?>(null) }
    var actionNoteOrReason by remember { mutableStateOf("") }
    var actionExtensionMonths by remember { mutableIntStateOf(1) }

    // Load Data
    fun reloadData() {
        isLoading = true
        coroutineScope.launch {
            try {
                launch {
                    repo.getAllAdminRequests().collect { list ->
                        requests = list
                        isLoading = false
                    }
                }
                launch {
                    repo.getVerificationPlans().collect { pList ->
                        plans = pList
                    }
                }
                launch {
                    repo.getVerificationSettings().collect { s ->
                        settings = s
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadData()
    }

    val filteredRequests = remember(requests, selectedTabIndex, searchQuery) {
        val list = when (selectedTabIndex) {
            0 -> requests // All
            1 -> requests.filter { it.status == VerificationStatus.APPLICATION_SUBMITTED || it.status == VerificationStatus.UNDER_REVIEW || it.status == "pending" }
            2 -> requests.filter { it.status == VerificationStatus.PAYMENT_VERIFICATION || it.status == VerificationStatus.PAYMENT_PENDING }
            3 -> requests.filter { it.status == VerificationStatus.VERIFIED_ACTIVE || it.status == "approved" }
            4 -> requests.filter { it.status == VerificationStatus.REJECTED || it.status == "rejected" }
            5 -> requests.filter { it.status == VerificationStatus.EXPIRED }
            6 -> requests.filter { it.status == VerificationStatus.SUSPENDED }
            7 -> requests.filter { it.isRenewal }
            else -> requests
        }

        if (searchQuery.trim().isEmpty()) {
            list
        } else {
            val q = searchQuery.trim().lowercase()
            list.filter {
                it.username.lowercase().contains(q) ||
                it.fullName.lowercase().contains(q) ||
                it.userId.lowercase().contains(q) ||
                it.businessName.lowercase().contains(q) ||
                it.id.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Admin Verification Panel", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${requests.size} total requests", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { reloadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
            // Scrollable Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            val count = when (index) {
                                1 -> requests.count { it.status == VerificationStatus.APPLICATION_SUBMITTED || it.status == "pending" }
                                2 -> requests.count { it.status == VerificationStatus.PAYMENT_VERIFICATION || it.status == VerificationStatus.PAYMENT_PENDING }
                                3 -> requests.count { it.status == VerificationStatus.VERIFIED_ACTIVE || it.status == "approved" }
                                6 -> requests.count { it.status == VerificationStatus.SUSPENDED }
                                else -> null
                            }
                            Text(if (count != null && count > 0) "$title ($count)" else title)
                        }
                    )
                }
            }

            if (selectedTabIndex == 8) {
                // Settings & Plans Tab
                AdminSettingsAndPlansContent(
                    settings = settings,
                    plans = plans,
                    onSaveSettings = { updatedSettings ->
                        coroutineScope.launch {
                            val res = repo.saveAdminSettings(updatedSettings)
                            if (res.isSuccess) {
                                Toast.makeText(context, "Payment settings saved!", Toast.LENGTH_SHORT).show()
                                settings = updatedSettings
                            } else {
                                Toast.makeText(context, "Failed to save settings", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onSavePlan = { plan ->
                        coroutineScope.launch {
                            val res = repo.saveVerificationPlan(plan)
                            if (res.isSuccess) {
                                Toast.makeText(context, "Plan saved!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            } else {
                // Request List View
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by username, full name, UID or App ID...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (filteredRequests.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No requests found in this category.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredRequests) { req ->
                                AdminRequestCard(
                                    request = req,
                                    onClick = { selectedRequestForDetail = req },
                                    onQuickApproveDoc = {
                                        selectedRequestForDetail = req
                                        showActionDialog = AdminActionType.APPROVE_APPLICATION
                                    },
                                    onQuickApprovePayment = {
                                        selectedRequestForDetail = req
                                        showActionDialog = AdminActionType.APPROVE_PAYMENT
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Modal / Sheet
    selectedRequestForDetail?.let { req ->
        AdminRequestDetailDialog(
            request = req,
            onDismiss = { selectedRequestForDetail = null },
            onAction = { actionType ->
                showActionDialog = actionType
            }
        )
    }

    // Action Confirmation Dialog
    showActionDialog?.let { actionType ->
        val targetReq = selectedRequestForDetail
        if (targetReq != null) {
            AlertDialog(
                onDismissRequest = {
                    showActionDialog = null
                    actionNoteOrReason = ""
                },
                title = {
                    Text(
                        text = when (actionType) {
                            AdminActionType.APPROVE_APPLICATION -> "Approve Documents?"
                            AdminActionType.REJECT_APPLICATION -> "Reject Verification Request"
                            AdminActionType.APPROVE_PAYMENT -> "Approve Payment & Activate Verification"
                            AdminActionType.REJECT_PAYMENT -> "Reject Payment Details"
                            AdminActionType.SUSPEND -> "Suspend Verification Badge"
                            AdminActionType.RESTORE -> "Restore Verification"
                            AdminActionType.EXTEND -> "Extend Verification Duration"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = when (actionType) {
                                AdminActionType.APPROVE_APPLICATION -> "Approving documents will move this request to PAYMENT_PENDING so the user can complete subscription payment."
                                AdminActionType.APPROVE_PAYMENT -> "This will activate the user's official Social India Blue Badge for ${targetReq.planDurationMonths} months, enable business tools, and generate an audit record."
                                AdminActionType.REJECT_APPLICATION -> "Enter the reason for rejection (this will be visible to the user):"
                                AdminActionType.REJECT_PAYMENT -> "Enter the payment rejection reason (e.g., invalid UTR number):"
                                AdminActionType.SUSPEND -> "Enter the suspension reason:"
                                AdminActionType.RESTORE -> "Are you sure you want to restore active verification for @${targetReq.username}?"
                                AdminActionType.EXTEND -> "Select additional months to extend:"
                            }
                        )

                        if (actionType == AdminActionType.EXTEND) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 3, 6, 12).forEach { m ->
                                    FilterChip(
                                        selected = actionExtensionMonths == m,
                                        onClick = { actionExtensionMonths = m },
                                        label = { Text("+$m Mos") }
                                    )
                                }
                            }
                        }

                        if (actionType != AdminActionType.RESTORE && actionType != AdminActionType.EXTEND) {
                            OutlinedTextField(
                                value = actionNoteOrReason,
                                onValueChange = { actionNoteOrReason = it },
                                label = { Text(if (actionType.name.contains("REJECT") || actionType == AdminActionType.SUSPEND) "Reason *" else "Admin Notes (Optional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val res: Result<Unit> = when (actionType) {
                                    AdminActionType.APPROVE_APPLICATION -> repo.adminApproveApplication(targetReq.id, adminId, actionNoteOrReason)
                                    AdminActionType.REJECT_APPLICATION -> repo.adminRejectApplication(targetReq.id, adminId, actionNoteOrReason.ifEmpty { "Requirements not met." })
                                    AdminActionType.APPROVE_PAYMENT -> repo.adminApprovePayment(targetReq.id, adminId, actionNoteOrReason)
                                    AdminActionType.REJECT_PAYMENT -> repo.adminRejectPayment(targetReq.id, adminId, actionNoteOrReason.ifEmpty { "Payment details could not be matched." })
                                    AdminActionType.SUSPEND -> repo.adminSuspendVerification(targetReq.id, adminId, actionNoteOrReason.ifEmpty { "Policy violation." })
                                    AdminActionType.RESTORE -> repo.adminRestoreVerification(targetReq.id, adminId)
                                    AdminActionType.EXTEND -> repo.adminExtendVerification(targetReq.id, adminId, actionExtensionMonths)
                                }

                                if (res.isSuccess) {
                                    Toast.makeText(context, "Action completed successfully!", Toast.LENGTH_SHORT).show()
                                    showActionDialog = null
                                    selectedRequestForDetail = null
                                    actionNoteOrReason = ""
                                    reloadData()
                                } else {
                                    Toast.makeText(context, "Action failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showActionDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

enum class AdminActionType {
    APPROVE_APPLICATION,
    REJECT_APPLICATION,
    APPROVE_PAYMENT,
    REJECT_PAYMENT,
    SUSPEND,
    RESTORE,
    EXTEND
}

@Composable
fun AdminRequestCard(
    request: VerificationRequest,
    onClick: () -> Unit,
    onQuickApproveDoc: () -> Unit,
    onQuickApprovePayment: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(
                        model = request.userAvatarUrl.ifEmpty { "https://i.pravatar.cc/150?u=${request.userId}" },
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(request.fullName.ifEmpty { request.username }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            if (request.status == VerificationStatus.VERIFIED_ACTIVE || request.status == "approved") {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF1DA1F2), modifier = Modifier.size(16.dp))
                            }
                        }
                        Text("@${request.username} • ${request.verificationType}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Status Badge
                Surface(
                    color = when (request.status) {
                        VerificationStatus.VERIFIED_ACTIVE, "approved" -> Color(0xFF0F9D58)
                        VerificationStatus.PAYMENT_VERIFICATION, VerificationStatus.PAYMENT_PENDING -> Color(0xFFFF9800)
                        VerificationStatus.REJECTED, "rejected", VerificationStatus.SUSPENDED, VerificationStatus.EXPIRED -> Color(0xFFD32F2F)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = request.status.replace("_", " "),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (request.verificationType == VerificationType.BUSINESS && request.businessName.isNotEmpty()) {
                Text("Business: ${request.businessName} (${request.businessCategory})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Plan: ${request.planName} (₹${request.planPrice.toInt()})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(dateFormat.format(Date(request.submittedAt)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (request.paymentReference.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("UTR / Ref: ${request.paymentReference}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(request.paymentStatus, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (request.paymentStatus == "APPROVED") Color(0xFF0F9D58) else Color(0xFFFF9800))
                    }
                }
            }

            // Quick Actions Bar
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (request.status == VerificationStatus.APPLICATION_SUBMITTED || request.status == "pending") {
                    TextButton(onClick = onQuickApproveDoc) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve Docs")
                    }
                }
                if (request.status == VerificationStatus.PAYMENT_VERIFICATION || request.paymentStatus == "PAYMENT_PENDING_ADMIN_REVIEW") {
                    Button(
                        onClick = onQuickApprovePayment,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F9D58))
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve Payment")
                    }
                }
            }
        }
    }
}

@Composable
fun AdminRequestDetailDialog(
    request: VerificationRequest,
    onDismiss: () -> Unit,
    onAction: (AdminActionType) -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
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
                        Text("Verification Application Detail", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("ID: #${request.id.take(8).uppercase()} • @${request.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Account info
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Account Details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Full Name: ${request.fullName}", fontSize = 13.sp)
                            Text("Username: @${request.username}", fontSize = 13.sp)
                            Text("UID: ${request.userId}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Type: ${request.verificationType}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Category: ${request.category}", fontSize = 13.sp)
                            Text("Country: ${request.country}", fontSize = 13.sp)
                            if (request.email.isNotEmpty()) Text("Email: ${request.email}", fontSize = 13.sp)
                            if (request.phone.isNotEmpty()) Text("Phone: ${request.phone}", fontSize = 13.sp)
                            if (request.reason.isNotEmpty()) Text("Reason/Bio: ${request.reason}", fontSize = 13.sp)
                        }
                    }

                    // Business Info if applicable
                    if (request.verificationType == VerificationType.BUSINESS || request.businessName.isNotEmpty()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Business Information", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Company Name: ${request.businessName}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Category: ${request.businessCategory}", fontSize = 13.sp)
                                Text("Contact Person: ${request.businessContact}", fontSize = 13.sp)
                                if (request.businessWebsite.isNotEmpty()) Text("Website: ${request.businessWebsite}", fontSize = 13.sp)
                                if (request.businessAddress.isNotEmpty()) Text("Address: ${request.businessAddress}", fontSize = 13.sp)
                                if (request.businessRegistrationNumber.isNotEmpty()) Text("GST/Reg: ${request.businessRegistrationNumber}", fontSize = 13.sp)
                            }
                        }
                    }

                    // Plan & Payment Info
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Subscription & Payment", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Plan: ${request.planName} (${request.planDurationMonths} Months)", fontSize = 13.sp)
                            Text("Price: ₹${request.planPrice.toInt()} ${request.planCurrency}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Payment Status: ${request.paymentStatus}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            if (request.paymentReference.isNotEmpty()) {
                                Text("UTR / Ref ID: ${request.paymentReference}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            if (request.expiryTimestamp > 0L) {
                                Text("Valid Until: ${dateFormat.format(Date(request.expiryTimestamp))}", fontSize = 13.sp)
                            }
                        }
                    }

                    // Documents Preview
                    Text("Identity & Payment Proof Documents", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (request.documentUrl.isNotEmpty()) {
                        Text("Government ID Document:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AsyncImage(
                            model = request.documentUrl,
                            contentDescription = "Govt ID",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.05f)),
                            contentScale = ContentScale.Fit
                        )
                    }

                    if (request.selfieUrl.isNotEmpty()) {
                        Text("Selfie Document:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AsyncImage(
                            model = request.selfieUrl,
                            contentDescription = "Selfie",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.05f)),
                            contentScale = ContentScale.Fit
                        )
                    }

                    if (request.businessDocUrl.isNotEmpty()) {
                        Text("Business License / GST:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AsyncImage(
                            model = request.businessDocUrl,
                            contentDescription = "Business License",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.05f)),
                            contentScale = ContentScale.Fit
                        )
                    }

                    if (request.paymentProofUrl.isNotEmpty()) {
                        Text("Payment Screenshot / Proof:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AsyncImage(
                            model = request.paymentProofUrl,
                            contentDescription = "Payment Proof",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.05f)),
                            contentScale = ContentScale.Fit
                        )
                    }

                    if (request.adminNotes.isNotEmpty()) {
                        Text("Admin Notes: ${request.adminNotes}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Action Bar
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (request.status == VerificationStatus.APPLICATION_SUBMITTED || request.status == "pending") {
                        Button(onClick = { onAction(AdminActionType.APPROVE_APPLICATION) }) {
                            Text("Approve Docs")
                        }
                        OutlinedButton(
                            onClick = { onAction(AdminActionType.REJECT_APPLICATION) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                        ) {
                            Text("Reject Docs")
                        }
                    }

                    if (request.status == VerificationStatus.PAYMENT_VERIFICATION || request.paymentStatus == "PAYMENT_PENDING_ADMIN_REVIEW" || request.status == VerificationStatus.PAYMENT_PENDING) {
                        Button(
                            onClick = { onAction(AdminActionType.APPROVE_PAYMENT) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F9D58))
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve Payment")
                        }
                        OutlinedButton(
                            onClick = { onAction(AdminActionType.REJECT_PAYMENT) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                        ) {
                            Text("Reject Payment")
                        }
                    }

                    if (request.status == VerificationStatus.VERIFIED_ACTIVE || request.status == "approved") {
                        Button(
                            onClick = { onAction(AdminActionType.EXTEND) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Extend")
                        }
                        OutlinedButton(
                            onClick = { onAction(AdminActionType.SUSPEND) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                        ) {
                            Text("Suspend")
                        }
                    }

                    if (request.status == VerificationStatus.SUSPENDED) {
                        Button(
                            onClick = { onAction(AdminActionType.RESTORE) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F9D58))
                        ) {
                            Text("Restore Badge")
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// SETTINGS & PLANS (PART 5 & PART 6 ADMIN CONFIG)
// -------------------------------------------------------------------------

@Composable
fun AdminSettingsAndPlansContent(
    settings: VerificationSettings,
    plans: List<VerificationPlan>,
    onSaveSettings: (VerificationSettings) -> Unit,
    onSavePlan: (VerificationPlan) -> Unit
) {
    var upiId by remember(settings) { mutableStateOf(settings.upiId) }
    var instructions by remember(settings) { mutableStateOf(settings.paymentInstructions) }
    var bankDetails by remember(settings) { mutableStateOf(settings.bankAccountDetails) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Manual Payment Instructions & Gateway", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Configure the official bank and UPI coordinates presented to users during verification checkout.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it },
                    label = { Text("Official UPI ID *") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Checkout Instructions *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                OutlinedTextField(
                    value = bankDetails,
                    onValueChange = { bankDetails = it },
                    label = { Text("Official Bank Account Details *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Button(
                    onClick = {
                        onSaveSettings(
                            settings.copy(
                                upiId = upiId.trim(),
                                paymentInstructions = instructions.trim(),
                                bankAccountDetails = bankDetails.trim()
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Payment Settings", fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("Configured Verification Plans", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        plans.forEach { plan ->
            var priceText by remember(plan) { mutableStateOf(plan.price.toInt().toString()) }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(plan.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${plan.durationMonths} Months", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Price (INR)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val newPrice = priceText.toDoubleOrNull() ?: plan.price
                            onSavePlan(plan.copy(price = newPrice))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Update Price")
                    }
                }
            }
        }
    }
}
