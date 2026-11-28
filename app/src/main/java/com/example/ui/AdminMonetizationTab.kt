package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.model.MonetizationRequest
import com.example.model.MonetizationSettings
import com.example.model.RepositoryManager
import com.example.repository.ExclusiveContentRepository
import com.example.repository.ExclusiveReportRecord
import com.example.repository.MonetizationRepository
import com.example.ui.components.CoinIcon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminMonetizationTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var requests by remember { mutableStateOf<List<MonetizationRequest>>(emptyList()) }
    var exclusiveReports by remember { mutableStateOf<List<ExclusiveReportRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var adminSection by remember { mutableStateOf("Applications") } // "Applications" or "Exclusive Reports"
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedRequestForAction by remember { mutableStateOf<MonetizationRequest?>(null) }
    var actionNote by remember { mutableStateOf("") }
    var actionType by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showRefundDialog by remember { mutableStateOf(false) }
    var refundPurchaseIdInput by remember { mutableStateOf("") }
    var refundReasonInput by remember { mutableStateOf("Policy violation / User refund request") }
    var isRefunding by remember { mutableStateOf(false) }
    val currentSettings by MonetizationRepository.observeMonetizationSettings()
        .collectAsState(initial = MonetizationSettings())

    val loadRequests = {
        scope.launch {
            isLoading = true
            requests = MonetizationRepository.getAllMonetizationRequests()
            exclusiveReports = ExclusiveContentRepository.getAllExclusiveReports()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadRequests()
    }

    val filteredRequests = remember(requests, selectedFilter) {
        if (selectedFilter == "All") requests else requests.filter { it.status.equals(selectedFilter, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Control Bar: Settings Button & Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (adminSection == "Applications") "Monetization Applications" else "Exclusive Content Moderation",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            OutlinedButton(
                onClick = { showSettingsDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Rates & Rules", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Section Switcher: Applications vs Exclusive Moderation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = adminSection == "Applications",
                onClick = { adminSection = "Applications" },
                label = { Text("Applications (${requests.size})", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF1877F2),
                    selectedLabelColor = Color.White
                )
            )
            val openReportsCount = exclusiveReports.count { it.status == "OPEN" }
            FilterChip(
                selected = adminSection == "Exclusive Reports",
                onClick = {
                    adminSection = "Exclusive Reports"
                    loadRequests()
                },
                label = { Text("Exclusive Reports ($openReportsCount)", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ExclusiveGoldMid,
                    selectedLabelColor = Color(0xFF1A1200)
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (adminSection == "Exclusive Reports") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Fee: ${currentSettings.exclusiveCommissionPct.toInt()}% • Max ₹${currentSettings.exclusiveMaxPriceInr.toInt()}",
                    fontSize = 11.5.sp,
                    color = Color.Gray
                )
                OutlinedButton(
                    onClick = { showRefundDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CurrencyRupee, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refund Purchase", fontSize = 11.5.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ExclusiveGoldMid)
                }
            } else if (exclusiveReports.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No reported exclusive content", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(exclusiveReports, key = { it.id }) { report ->
                        ExclusiveReportModerationCard(
                            report = report,
                            onRemoveContent = {
                                scope.launch {
                                    val ok = ExclusiveContentRepository.adminRemoveExclusiveContent(
                                        contentId = report.contentId,
                                        contentType = report.contentType,
                                        reportId = report.id
                                    )
                                    Toast.makeText(
                                        context,
                                        if (ok) "Exclusive content removed & vault purged" else "Removal failed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    loadRequests()
                                }
                            },
                            onDismissReport = {
                                scope.launch {
                                    val ok = ExclusiveContentRepository.adminDismissExclusiveReport(report.id)
                                    if (ok) {
                                        Toast.makeText(context, "Report dismissed", Toast.LENGTH_SHORT).show()
                                        loadRequests()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        } else {
            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Submitted", "Approved", "Rejected", "Suspended").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFEE1D52),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFEE1D52))
                }
            } else if (filteredRequests.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inbox, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No monetization requests found", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredRequests) { request ->
                        MonetizationRequestCard(
                            request = request,
                            onApprove = {
                                selectedRequestForAction = request
                                actionType = "Approved"
                                actionNote = "Approved after creator quality check."
                            },
                            onReject = {
                                selectedRequestForAction = request
                                actionType = "Rejected"
                                actionNote = "Does not meet current guidelines."
                            },
                            onSuspend = {
                                selectedRequestForAction = request
                                actionType = "Suspended"
                                actionNote = "Temporarily suspended for policy review."
                            }
                        )
                    }
                }
            }
        }
    }

    // Admin Refund Exclusive Purchase Dialog
    if (showRefundDialog) {
        AlertDialog(
            onDismissRequest = { if (!isRefunding) showRefundDialog = false },
            title = { Text("Refund Exclusive Purchase", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the Purchase ID (buyerId_contentId) to atomically credit the buyer's wallet and revoke entitlement.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    OutlinedTextField(
                        value = refundPurchaseIdInput,
                        onValueChange = { refundPurchaseIdInput = it },
                        label = { Text("Purchase ID (buyerId_contentId)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = refundReasonInput,
                        onValueChange = { refundReasonInput = it },
                        label = { Text("Refund Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isRefunding = true
                        scope.launch {
                            val ok = ExclusiveContentRepository.adminRefundPurchase(
                                purchaseId = refundPurchaseIdInput.trim(),
                                reason = refundReasonInput.trim()
                            )
                            isRefunding = false
                            Toast.makeText(
                                context,
                                if (ok) "Purchase refunded & entitlement revoked!" else "Refund failed or already refunded",
                                Toast.LENGTH_LONG
                            ).show()
                            if (ok) {
                                refundPurchaseIdInput = ""
                                showRefundDialog = false
                            }
                        }
                    },
                    enabled = refundPurchaseIdInput.isNotBlank() && !isRefunding,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
                ) {
                    Text(if (isRefunding) "Refunding..." else "Issue Refund")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRefundDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation / Review Dialog
    if (selectedRequestForAction != null) {
        val req = selectedRequestForAction!!
        AlertDialog(
            onDismissRequest = { selectedRequestForAction = null },
            title = { Text("$actionType Request", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Creator: @${req.username} (${req.fullName})", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = actionNote,
                        onValueChange = { actionNote = it },
                        label = { Text("Admin Review Note / Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentAdminId = RepositoryManager.getCurrentUserId()
                        scope.launch {
                            val success = MonetizationRepository.updateMonetizationRequestStatus(
                                requestId = req.id,
                                userId = req.userId,
                                status = actionType,
                                notes = actionNote,
                                reviewerId = currentAdminId
                            )
                            if (success) {
                                Toast.makeText(context, "Request updated to $actionType", Toast.LENGTH_SHORT).show()
                                loadRequests()
                            } else {
                                Toast.makeText(context, "Failed to update request", Toast.LENGTH_SHORT).show()
                            }
                            selectedRequestForAction = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (actionType == "Approved") Color(0xFF4CAF50) else Color(0xFFEE1D52)
                    )
                ) {
                    Text("Confirm $actionType")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedRequestForAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rates & Rules Settings Dialog
    if (showSettingsDialog) {
        var videoCpm by remember { mutableStateOf(currentSettings.videoCpmInr.toString()) }
        var reelCpm by remember { mutableStateOf(currentSettings.reelCpmInr.toString()) }
        var photoCpm by remember { mutableStateOf(currentSettings.photoCpmInr.toString()) }
        var giftShare by remember { mutableStateOf((currentSettings.liveGiftRevenueShare * 100).toInt().toString()) }
        var refBonus by remember { mutableStateOf(currentSettings.referralBonusInr.toString()) }
        var minWithdraw by remember { mutableStateOf(currentSettings.minWithdrawalInr.toString()) }
        var coinsToInr by remember { mutableStateOf(currentSettings.coinsToInrRate.toString()) }
        var coinIconUrlInput by remember { mutableStateOf(currentSettings.coinIconUrl) }
        var payoutDay by remember { mutableStateOf(currentSettings.scheduledPayoutDayOfMonth.toString()) }
        var exclusiveCommPct by remember { mutableStateOf(currentSettings.exclusiveCommissionPct.toInt().toString()) }
        var exclusiveMaxPrice by remember { mutableStateOf(currentSettings.exclusiveMaxPriceInr.toInt().toString()) }
        var fraudEnabled by remember { mutableStateOf(currentSettings.fraudDetectionEnabled) }
        var isSavingSettings by remember { mutableStateOf(false) }
        var isUploadingCoinMedia by remember { mutableStateOf(false) }

        val mediaRepo = remember { com.example.repository.MediaRepository(context) }
        val coinPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    isUploadingCoinMedia = true
                    try {
                        val uploaded = mediaRepo.uploadMedia(uri) { _ -> }
                        if (!uploaded.isNullOrEmpty()) {
                            coinIconUrlInput = uploaded
                            Toast.makeText(context, "Coin image uploaded!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isUploadingCoinMedia = false
                    }
                }
            }
        }
        val coinFilePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    isUploadingCoinMedia = true
                    try {
                        val uploaded = mediaRepo.uploadMedia(uri) { _ -> }
                        if (!uploaded.isNullOrEmpty()) {
                            coinIconUrlInput = uploaded
                            Toast.makeText(context, "Coin image uploaded!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isUploadingCoinMedia = false
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isSavingSettings) showSettingsDialog = false },
            title = { Text("Monetization & Fraud Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Earnings Rates (INR)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = videoCpm,
                            onValueChange = { videoCpm = it },
                            label = { Text("Video CPM (₹)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = reelCpm,
                            onValueChange = { reelCpm = it },
                            label = { Text("Reel CPM (₹)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = photoCpm,
                            onValueChange = { photoCpm = it },
                            label = { Text("Photo CPM (₹)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = giftShare,
                            onValueChange = { giftShare = it },
                            label = { Text("Gift Share (%)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = refBonus,
                            onValueChange = { refBonus = it },
                            label = { Text("Referral Bonus (₹)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minWithdraw,
                            onValueChange = { minWithdraw = it },
                            label = { Text("Min Payout (₹)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text("Coins Conversion & Auto Payout Date", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = coinsToInr,
                            onValueChange = { coinsToInr = it },
                            label = { Text("1 Coin = ₹ INR Value") },
                            placeholder = { Text("0.10 (10 coins = ₹1)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = payoutDay,
                            onValueChange = { payoutDay = it },
                            label = { Text("Monthly Payout Day (1-28)") },
                            placeholder = { Text("25 (Every 25th)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("Exclusive Content Rules (Pay-to-Unlock)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = exclusiveCommPct,
                            onValueChange = { exclusiveCommPct = it },
                            label = { Text("Platform Fee (%)") },
                            placeholder = { Text("15") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = exclusiveMaxPrice,
                            onValueChange = { exclusiveMaxPrice = it },
                            label = { Text("Max Price (₹)") },
                            placeholder = { Text("5000") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fraud Detection Layer", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text("Block self-views, bot velocity & fake traffic", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = fraudEnabled,
                            onCheckedChange = { fraudEnabled = it }
                        )
                    }

                    Text("Coins & Earnings Icon (App-Wide)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                    // Live Coin Icon Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E1E1E))
                            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CoinIcon(
                            size = 42.dp,
                            customUrl = coinIconUrlInput.ifBlank { null }
                        )
                        if (isUploadingCoinMedia) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFFFFD700))
                        }
                    }

                    // Direct Upload Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                coinPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("🖼️ Gallery", fontSize = 11.sp, color = Color.White)
                        }
                        Button(
                            onClick = { coinFilePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF383838)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("📁 Browse Files", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    // Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val presets = listOf(
                            Pair("3D Gold", "https://fonts.gstatic.com/s/e/notoemoji/latest/1fa99/512.gif"),
                            Pair("Diamond", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f48e/512.gif"),
                            Pair("Star", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f31f/512.gif"),
                            Pair("Default 🪙", "")
                        )
                        presets.forEach { (pName, pUrl) ->
                            AssistChip(
                                onClick = { coinIconUrlInput = pUrl },
                                label = { Text(pName, fontSize = 9.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (coinIconUrlInput == pUrl) Color(0xFFFFD700).copy(alpha = 0.25f) else Color(0xFF262626)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = coinIconUrlInput,
                        onValueChange = { coinIconUrlInput = it },
                        label = { Text("Coins & Earnings Icon GIF/Image URL") },
                        placeholder = { Text("https://... (.gif / .png / 🪙)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSavingSettings = true
                        scope.launch {
                            val updated = currentSettings.copy(
                                videoCpmInr = videoCpm.toDoubleOrNull() ?: currentSettings.videoCpmInr,
                                reelCpmInr = reelCpm.toDoubleOrNull() ?: currentSettings.reelCpmInr,
                                photoCpmInr = photoCpm.toDoubleOrNull() ?: currentSettings.photoCpmInr,
                                liveGiftRevenueShare = (giftShare.toDoubleOrNull() ?: 70.0) / 100.0,
                                referralBonusInr = refBonus.toDoubleOrNull() ?: currentSettings.referralBonusInr,
                                minWithdrawalInr = minWithdraw.toDoubleOrNull() ?: currentSettings.minWithdrawalInr,
                                coinsToInrRate = coinsToInr.toDoubleOrNull() ?: currentSettings.coinsToInrRate,
                                coinIconUrl = coinIconUrlInput.trim(),
                                scheduledPayoutDayOfMonth = payoutDay.toIntOrNull()?.coerceIn(1, 28) ?: currentSettings.scheduledPayoutDayOfMonth,
                                exclusiveCommissionPct = exclusiveCommPct.toDoubleOrNull()?.coerceIn(0.0, 50.0) ?: currentSettings.exclusiveCommissionPct,
                                exclusiveMaxPriceInr = exclusiveMaxPrice.toDoubleOrNull()?.coerceAtLeast(10.0) ?: currentSettings.exclusiveMaxPriceInr,
                                fraudDetectionEnabled = fraudEnabled
                            )
                            val ok = MonetizationRepository.updateMonetizationSettings(updated)
                            if (ok) {
                                Toast.makeText(context, "Monetization rates & Coin Icon updated app-wide!", Toast.LENGTH_SHORT).show()
                                showSettingsDialog = false
                            } else {
                                Toast.makeText(context, "Failed to update rates", Toast.LENGTH_SHORT).show()
                            }
                            isSavingSettings = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
                ) {
                    if (isSavingSettings) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Text("Save Changes")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MonetizationRequestCard(
    request: MonetizationRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onSuspend: () -> Unit
) {
    val dateStr = remember(request.timestamp) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(request.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = request.userAvatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(request.fullName.ifEmpty { request.username }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("@${request.username} • ${request.contentCategory}", color = Color.Gray, fontSize = 12.sp)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (request.status) {
                        "Approved" -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                        "Rejected" -> Color(0xFFF44336).copy(alpha = 0.15f)
                        "Suspended" -> Color(0xFFFF9800).copy(alpha = 0.15f)
                        else -> Color(0xFF2196F3).copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = request.status,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (request.status) {
                            "Approved" -> Color(0xFF4CAF50)
                            "Rejected" -> Color(0xFFF44336)
                            "Suspended" -> Color(0xFFFF9800)
                            else -> Color(0xFF2196F3)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Followers: ${request.followerCount}", fontSize = 12.sp, color = Color.Gray)
                Text("Views: ${request.totalViews}", fontSize = 12.sp, color = Color.Gray)
                Text("Region: ${request.primaryRegion}", fontSize = 12.sp, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text("Programs: ${request.selectedMethods.joinToString(", ")}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("Applied: $dateStr", fontSize = 11.sp, color = Color.Gray)

            if (request.adminReviewNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Admin Note: ${request.adminReviewNotes}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (request.status != "Approved") {
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("Approve", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (request.status != "Rejected") {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF44336)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("Reject", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (request.status == "Approved") {
                    OutlinedButton(
                        onClick = onSuspend,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF9800)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("Suspend", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExclusiveReportModerationCard(
    report: ExclusiveReportRecord,
    onRemoveContent: () -> Unit,
    onDismissReport: () -> Unit
) {
    val dateStr = remember(report.timestamp) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(report.timestamp))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ExclusiveGoldMid.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = report.contentType,
                            color = ExclusiveGoldMid,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text("@${report.creatorUsername.ifBlank { report.creatorId.take(8) }}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (report.status) {
                        "REMOVED" -> Color(0xFFF44336).copy(alpha = 0.15f)
                        "DISMISSED" -> Color.Gray.copy(alpha = 0.15f)
                        else -> Color(0xFFFF9800).copy(alpha = 0.16f)
                    }
                ) {
                    Text(
                        text = report.status,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (report.status) {
                            "REMOVED" -> Color(0xFFF44336)
                            "DISMISSED" -> Color.Gray
                            else -> Color(0xFFFF9800)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text("Reason: ${report.reason}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (report.details.isNotBlank()) {
                Text(report.details, fontSize = 12.sp, color = Color.Gray)
            }
            Text("Content ID: ${report.contentId} • $dateStr", fontSize = 11.sp, color = Color.Gray)

            if (report.status == "OPEN") {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRemoveContent,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Remove Content", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onDismissReport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Dismiss", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
