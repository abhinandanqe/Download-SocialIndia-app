package com.example.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.example.model.WithdrawalRequest
import com.example.model.DepositRequest

import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.example.model.User
import com.example.viewmodel.AdminViewModel
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable

import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.filled.Lock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    onBack: () -> Unit,
    onNavigateToAiManager: () -> Unit = {},
    onNavigateToAdsManager: () -> Unit = {},
    adminViewModel: AdminViewModel = viewModel()
) {
    var isAuthorized by remember { mutableStateOf<Boolean?>(null) }
    var checkingAuth by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        try {
            val userId = com.example.model.RepositoryManager.getCurrentUserId()
            val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val authEmail = authUser?.email.orEmpty().trim()

            if (userId.isEmpty() && authEmail.isEmpty()) {
                isAuthorized = false
                checkingAuth = false
                return@LaunchedEffect
            }

            var isUserAdmin = false
            var userEmail = ""
            if (userId.isNotEmpty()) {
                val doc = com.example.model.RepositoryManager.firestore.collection("users").document(userId).get().await()
                userEmail = (doc.get("email") as? String).orEmpty().trim()
                isUserAdmin = when (val adm = doc.get("isAdmin")) {
                    is Boolean -> adm
                    is Number -> adm.toInt() != 0
                    is String -> adm.equals("true", ignoreCase = true)
                    else -> false
                }
            }

            val isMaster = authEmail.equals("abhinandankumar7867@gmail.com", ignoreCase = true) ||
                           userEmail.equals("abhinandankumar7867@gmail.com", ignoreCase = true)

            if (isMaster && userId.isNotEmpty() && !isUserAdmin) {
                try {
                    com.example.model.RepositoryManager.firestore.collection("users").document(userId)
                        .update(mapOf("isAdmin" to true, "isVerified" to true))
                } catch (e: Exception) {}
                isUserAdmin = true
            }

            isAuthorized = isMaster || isUserAdmin
        } catch (e: Exception) {
            val authEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email.orEmpty()
            isAuthorized = authEmail.equals("abhinandankumar7867@gmail.com", ignoreCase = true)
        } finally {
            checkingAuth = false
        }
    }

    if (checkingAuth) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFEE1D52))
        }
    } else if (isAuthorized == false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Restricted",
                    tint = Color(0xFFEE1D52),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Access Denied",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "You do not have permissions to access the Administrator Panel. Please log in with an administrator account to continue.",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
                ) {
                    Text("Go Back", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        val users by adminViewModel.users.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showPermissionError by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        adminViewModel.errorMessages.collect { msg ->
            if (msg.contains("PERMISSION DENIED")) {
                showPermissionError = msg
            } else {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    if (showPermissionError != null) {
        AlertDialog(
            onDismissRequest = { showPermissionError = null },
            title = { Text("Security Rules Error 🔒") },
            text = { Text("""Firebase blocked the request: PERMISSION DENIED.

You must go to the Firebase Console -> Firestore Database -> Rules, and set them to:

rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if true;
    }
  }
}""") },
            confirmButton = {
                Button(onClick = { showPermissionError = null }) { Text("Got it") }
            }
        )
    }



        val isLoading by adminViewModel.isLoading.collectAsStateWithLifecycle()
        
        var currentTab by remember { mutableStateOf(0) }
        val tabs = listOf("Users", "User Storage", "Monetization", "Withdrawals", "Deposits & UTR", "Gifts", "Bank Manager", "Creator Payouts", "Settings", "ZEGO Cloud", "Stickers", "Moderation & KYC", "App Updates", "Referrals")
        var withdrawals by remember { mutableStateOf<List<WithdrawalRequest>>(emptyList()) }
        var deposits by remember { mutableStateOf<List<DepositRequest>>(emptyList()) }
        var adminUpiQrUrl by remember { mutableStateOf(com.example.utils.UpiPaymentManager.getCustomQrUrl()) }
        var isUploadingQr by remember { mutableStateOf(false) }
        var adminUpiIdInput by remember { mutableStateOf(com.example.utils.UpiPaymentManager.getPrimaryUpiId()) }
        var adminSecondaryUpiIdInput by remember { mutableStateOf(com.example.utils.UpiPaymentManager.getSecondaryUpiId()) }
        var adminPayeeNameInput by remember { mutableStateOf(com.example.utils.UpiPaymentManager.getPayeeName()) }
        var adminGatewayProviderInput by remember { mutableStateOf(com.example.utils.UpiPaymentManager.getGatewayProvider()) }
        var adminMerchantKeyIdInput by remember { mutableStateOf(com.example.utils.UpiPaymentManager.getMerchantKeyId()) }
        var adminMerchantSecretInput by remember { mutableStateOf(com.example.utils.UpiPaymentManager.getMerchantKeySecret()) }
        var isSavingUpiConfig by remember { mutableStateOf(false) }
        var payoutDialogTarget by remember { mutableStateOf<WithdrawalRequest?>(null) }
        var payoutUtrInput by remember { mutableStateOf("") }
        var payoutRemarksInput by remember { mutableStateOf("Transferred via UPI/IMPS by Admin") }
        val adminScope = rememberCoroutineScope()

        val qrPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                isUploadingQr = true
                adminScope.launch(Dispatchers.Main) {
                    try {
                        val uploadUrl = com.example.repository.MediaRepository(context).uploadMedia(uri)
                        if (uploadUrl != null) {
                            com.example.model.RepositoryManager.firestore.collection("admin_settings").document("payment_settings")
                                .set(mapOf("upi_qr_url" to uploadUrl), com.google.firebase.firestore.SetOptions.merge()).await()
                            adminUpiQrUrl = uploadUrl
                            Toast.makeText(context, "QR Code Updated", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "Failed to upload QR", Toast.LENGTH_SHORT).show()
                    }
                    isUploadingQr = false
                }
            }
        }

        // Real-time listeners for Withdrawals and Deposits/UTR Queue
        DisposableEffect(Unit) {
            val db = com.example.model.RepositoryManager.firestore
            val wSub = db.collection("withdrawals").addSnapshotListener { snap, _ ->
                if (snap != null) {
                    withdrawals = snap.documents.mapNotNull { doc ->
                        try { doc.toObject(WithdrawalRequest::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
                    }.sortedByDescending { it.timestamp }
                }
            }
            val dSub = db.collection("deposit_requests").addSnapshotListener { snap, _ ->
                if (snap != null) {
                    deposits = snap.documents.mapNotNull { doc ->
                        try { doc.toObject(DepositRequest::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
                    }.sortedByDescending { it.timestamp }
                }
            }
            onDispose {
                wSub.remove()
                dSub.remove()
            }
        }

        LaunchedEffect(currentTab) {
            if (currentTab == 5) {
                try {
                    com.example.model.RepositoryManager.giftRepository.seedDefaultGiftsIfEmpty()
                } catch (_: Exception) {}
            } else if (currentTab == 6 || currentTab == 8) {
                try {
                    val doc = com.example.model.RepositoryManager.firestore.collection("admin_settings").document("payment_settings").get().await()
                    adminUpiQrUrl = (doc.get("upi_qr_url") as? String) ?: ""
                    (doc.get("primary_upi_id") as? String)?.takeIf { it.isNotBlank() }?.let { adminUpiIdInput = it }
                    (doc.get("secondary_upi_id") as? String)?.takeIf { it.isNotBlank() }?.let { adminSecondaryUpiIdInput = it }
                    (doc.get("payee_name") as? String)?.takeIf { it.isNotBlank() }?.let { adminPayeeNameInput = it }
                    (doc.get("gateway_provider") as? String)?.takeIf { it.isNotBlank() }?.let { adminGatewayProviderInput = it }
                    (doc.get("merchant_key_id") as? String)?.let { adminMerchantKeyIdInput = it }
                    (doc.get("merchant_key_secret") as? String)?.let { adminMerchantSecretInput = it }
                } catch (_: Exception) {}
            }
        }

        // Dialog for entering 12-Digit Payout UTR when Admin marks a Creator Withdrawal as Paid
        payoutDialogTarget?.let { targetWithdrawal ->
            AlertDialog(
                onDismissRequest = { payoutDialogTarget = null },
                title = { Text("Confirm Creator Payout (₹${targetWithdrawal.amount.toInt()})", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val destText = if (targetWithdrawal.payoutMethod == "UPI" || targetWithdrawal.upiId.isNotBlank()) {
                            "Creator UPI ID: ${targetWithdrawal.upiId.ifBlank { targetWithdrawal.accountNumber }}"
                        } else {
                            "Bank: ${targetWithdrawal.bankName}\nA/C: ${targetWithdrawal.accountNumber} (${targetWithdrawal.ifscCode})"
                        }
                        Text(destText, color = Color(0xFF00E676), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = payoutUtrInput,
                            onValueChange = { payoutUtrInput = it.filter { ch -> ch.isLetterOrDigit() }.take(16) },
                            label = { Text("Enter 12-Digit Bank/UPI UTR Ref") },
                            placeholder = { Text("e.g. 412398765432") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = payoutRemarksInput,
                            onValueChange = { payoutRemarksInput = it },
                            label = { Text("Admin Remarks") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val utr = payoutUtrInput.trim().ifBlank { "UTR${System.currentTimeMillis().toString().takeLast(9)}" }
                            val target = targetWithdrawal
                            payoutDialogTarget = null
                            adminScope.launch(Dispatchers.IO) {
                                com.example.repository.MonetizationRepository.updateWithdrawalStatus(
                                    withdrawalId = target.id,
                                    userId = target.userId,
                                    newStatus = "Completed",
                                    remarks = payoutRemarksInput,
                                    utrReference = utr
                                )
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Payout marked Paid (UTR: $utr) & Creator notified!", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                    ) {
                        Text("Confirm Paid & Notify", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { payoutDialogTarget = null }) {
                        Text("Cancel", color = Color.LightGray)
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Admin Panel", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToAdsManager) {
                            Icon(Icons.Outlined.Campaign, contentDescription = "Ads Manager", tint = Color(0xFF00E5FF))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
                )
            },
            containerColor = Color(0xFF0F172A)
        ) { padding ->
            var showBadgeDialogForUser by remember { mutableStateOf<User?>(null) }
            var customBadgeUrl by remember { mutableStateOf("https://cdn-icons-png.flaticon.com/512/12471/12471415.png") }
            var isUploadingBadge by remember { mutableStateOf(false) }
            val context = LocalContext.current

            val badgeImageLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    isUploadingBadge = true
                    val fileName = "badges_${System.currentTimeMillis()}.png"
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        val mimeType = context.contentResolver.getType(uri) ?: "image/png"
                        val secureUrl = com.example.model.RepositoryManager.mediaRepository.uploadMedia(uri)
                        if (secureUrl != null) {
                            customBadgeUrl = secureUrl
                            isUploadingBadge = false
                            Toast.makeText(context, "Badge uploaded successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            isUploadingBadge = false
                            Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            val targetUser = showBadgeDialogForUser
            if (targetUser != null) {
                AlertDialog(
                    onDismissRequest = { showBadgeDialogForUser = null },
                    title = { Text("Approve Verification", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Text("Are you sure you want to approve blue tick verification for @${targetUser.username}?\nThis will automatically apply the official verification badge.", color = Color.Gray, fontSize = 14.sp)
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                com.example.utils.SoundManager.playClickSound()
                                adminViewModel.toggleVerification(targetUser.id, targetUser.isVerified, "https://i.ibb.co/5g7pLKGW/checklist.png")
                                showBadgeDialogForUser = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
                        ) {
                            Text("Approve", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBadgeDialogForUser = null }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF1E1E1E)
                )
            }

            if (isLoading && currentTab == 0) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFEE1D52))
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    ScrollableTabRow(
                        selectedTabIndex = currentTab,
                        containerColor = Color(0xFF1E1E24),
                        contentColor = Color(0xFFEE1D52),
                        edgePadding = 12.dp
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = currentTab == index,
                                onClick = { currentTab = index },
                                text = {
                                    Text(
                                        title,
                                        fontWeight = if (currentTab == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentTab == index) Color(0xFFEE1D52) else Color.White
                                    )
                                }
                            )
                        }
                    }
                    if (currentTab == 0) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(users, key = { idx, u -> "${u.id}_$idx" }) { _, user ->
                                AdminUserCard(
                                    user = user,
                                    adminViewModel = adminViewModel,
                                    onToggleVerification = {
                                        if (user.isVerified) {
                                            adminViewModel.toggleVerification(user.id, user.isVerified, "")
                                        } else {
                                            showBadgeDialogForUser = user
                                        }
                                    },
                                    onToggleBan = { adminViewModel.toggleBanUser(user.id, user.isBanned) },
                                    onToggleAdmin = { adminViewModel.toggleAdminUser(user.id, user.isAdmin) },
                                    onApproveMonetization = { adminViewModel.approveMonetization(user.id) }
                                )
                            }
                        }
                    } else if (currentTab == 1) {
                        AdminUserStorageTab()
                    } else if (currentTab == 2) {
                        AdminMonetizationTab()
                    } else if (currentTab == 3) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item(key = "withdrawals_summary") {
                                val pendingTotal = withdrawals.filter { it.status == "Pending" || it.status == "Processing" }.sumOf { it.amount }
                                val completedTotal = withdrawals.filter { it.status == "Completed" || it.status == "Paid" }.sumOf { it.amount }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text("Creator Withdrawals & Payout Queue (Live)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Surface(
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF0F172A)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("Pending/Processing (${withdrawals.count { it.status == "Pending" || it.status == "Processing" }})", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                                    Text("₹${String.format(java.util.Locale.US, "%.2f", pendingTotal)}", color = Color(0xFFFFA000), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                }
                                            }
                                            Surface(
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF0F172A)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("Paid Out (${withdrawals.count { it.status == "Completed" || it.status == "Paid" }})", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                                    Text("₹${String.format(java.util.Locale.US, "%.2f", completedTotal)}", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (withdrawals.isEmpty()) {
                                item(key = "empty_withdrawals") {
                                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                        Text("No creator withdrawal requests found", color = Color(0xFF94A3B8))
                                    }
                                }
                            } else {
                                itemsIndexed(withdrawals, key = { index, item -> if (item.id.isNotEmpty()) item.id else "w_$index" }) { _, withdrawal ->
                                    val isUpiPayout = withdrawal.payoutMethod == "UPI" || withdrawal.upiId.isNotBlank()
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    text = if (withdrawal.username.isNotBlank()) "@${withdrawal.username} (${withdrawal.userId.take(6)})" else "User: ${withdrawal.userId}",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = withdrawal.status,
                                                    color = when (withdrawal.status) {
                                                        "Pending" -> Color(0xFFFFA000)
                                                        "Processing" -> Color(0xFF38BDF8)
                                                        "Completed", "Paid" -> Color(0xFF4ADE80)
                                                        else -> Color(0xFFEF4444)
                                                    },
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("Amount: ₹${String.format(java.util.Locale.US, "%.2f", withdrawal.amount)}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF4ADE80))
                                            if (isUpiPayout) {
                                                val targetUpi = withdrawal.upiId.ifBlank { withdrawal.accountNumber }
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("UPI ID: $targetUpi", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    TextButton(
                                                        onClick = {
                                                            val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                            cb.setPrimaryClip(android.content.ClipData.newPlainText("Creator UPI", targetUpi))
                                                            Toast.makeText(context, "Copied Creator UPI: $targetUpi", Toast.LENGTH_SHORT).show()
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                                    ) {
                                                        Text("Copy UPI", fontSize = 11.sp, color = Color(0xFFFFD54F))
                                                    }
                                                }
                                            } else {
                                                Text("Bank: ${withdrawal.bankName} (A/C: ${withdrawal.accountNumber})", color = Color(0xFFCBD5E1), fontSize = 14.sp)
                                                Text("IFSC: ${withdrawal.ifscCode} • Holder: ${withdrawal.accountHolderName}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                            }
                                            if (withdrawal.utrReference.isNotBlank()) {
                                                Text("Payout UTR: ${withdrawal.utrReference}", color = Color(0xFF00E676), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }

                                            if (withdrawal.status == "Pending" || withdrawal.status == "Processing") {
                                                Spacer(modifier = Modifier.height(14.dp))
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    if (withdrawal.status == "Pending") {
                                                        OutlinedButton(
                                                            onClick = {
                                                                adminScope.launch(Dispatchers.IO) {
                                                                    com.example.repository.MonetizationRepository.updateWithdrawalStatus(
                                                                        withdrawalId = withdrawal.id,
                                                                        userId = withdrawal.userId,
                                                                        newStatus = "Processing",
                                                                        remarks = "Payout is being processed by Admin"
                                                                    )
                                                                }
                                                            },
                                                            modifier = Modifier.weight(1f),
                                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                                                            shape = RoundedCornerShape(8.dp)
                                                        ) {
                                                            Text("Processing", fontSize = 11.sp)
                                                        }
                                                    }
                                                    Button(
                                                        onClick = {
                                                            payoutUtrInput = ""
                                                            payoutDialogTarget = withdrawal
                                                        },
                                                        modifier = Modifier.weight(1.2f),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text("Mark Paid (UTR)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                    }
                                                    OutlinedButton(
                                                        onClick = {
                                                            adminScope.launch(Dispatchers.IO) {
                                                                com.example.repository.MonetizationRepository.updateWithdrawalStatus(
                                                                    withdrawalId = withdrawal.id,
                                                                    userId = withdrawal.userId,
                                                                    newStatus = "Rejected",
                                                                    remarks = "Rejected by Admin - refunded to wallet"
                                                                )
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text("Reject", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (currentTab == 4) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item(key = "deposits_summary") {
                                val pendingTotal = deposits.filter { it.status == "Pending" }.sumOf { it.amount }
                                val approvedTotal = deposits.filter { it.status == "Approved" }.sumOf { it.amount }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text("Live UPI & 12-Digit UTR Verification Queue", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                        Text("Approving a UTR immediately credits Wallet/Coins or unlocks Subscriptions, Blue Tick, Super Tips & Ad Boosts.", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Surface(
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF0F172A)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("Pending UTRs (${deposits.count { it.status == "Pending" }})", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                                    Text("₹${String.format(java.util.Locale.US, "%.2f", pendingTotal)}", color = Color(0xFFFFA000), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                }
                                            }
                                            Surface(
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF0F172A)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("Approved (${deposits.count { it.status == "Approved" }})", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                                    Text("₹${String.format(java.util.Locale.US, "%.2f", approvedTotal)}", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (deposits.isEmpty()) {
                                item(key = "empty_deposits") {
                                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                        Text("No UPI / UTR verification requests in queue", color = Color(0xFF94A3B8))
                                    }
                                }
                            } else {
                                itemsIndexed(deposits, key = { index, item -> if (item.id.isNotEmpty()) item.id else "d_$index" }) { _, deposit ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Column {
                                                    Text(
                                                        text = if (deposit.username.isNotBlank()) "@${deposit.username}" else "User: ${deposit.userId.take(10)}",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                    Text(
                                                        text = "Type: ${deposit.productType.replace("_", " ")}",
                                                        color = Color(0xFF00E5FF),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                Text(
                                                    text = deposit.status,
                                                    color = when (deposit.status) {
                                                        "Pending" -> Color(0xFFFFA000)
                                                        "Approved" -> Color(0xFF4ADE80)
                                                        "Rejected" -> Color(0xFFEF4444)
                                                        else -> Color.Gray
                                                    },
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Amount: ₹${String.format(java.util.Locale.US, "%.2f", deposit.amount)}", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Color(0xFF4ADE80))

                                            if (deposit.utrNumber.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text("12-Digit Bank UTR / Ref:", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                                        Text(deposit.utrNumber, color = Color(0xFFFFD54F), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                            cb.setPrimaryClip(android.content.ClipData.newPlainText("UTR", deposit.utrNumber))
                                                            Toast.makeText(context, "Copied UTR: ${deposit.utrNumber}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Text("Copy UTR", color = Color(0xFF00E676), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }

                                            if (deposit.upiIdUsed.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Paid to Merchant UPI: ${deposit.upiIdUsed}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                            }

                                            if (deposit.screenshotUrl.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                AsyncImage(
                                                    model = deposit.screenshotUrl,
                                                    contentDescription = "Payment Screenshot",
                                                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }

                                            if (deposit.status == "Pending") {
                                                Spacer(modifier = Modifier.height(14.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                                    Button(
                                                        onClick = {
                                                            adminScope.launch(Dispatchers.IO) {
                                                                val res = com.example.payments.SocialIndiaPaymentOrchestrator.adminApprovePaymentOrder(
                                                                    orderId = deposit.orderId.ifBlank { deposit.id },
                                                                    depositId = deposit.id,
                                                                    userId = deposit.userId,
                                                                    amountRupees = deposit.amount,
                                                                    productType = deposit.productType,
                                                                    utrNumber = deposit.utrNumber,
                                                                    creatorId = deposit.creatorId,
                                                                    contentId = deposit.contentId
                                                                )
                                                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                                    if (res.isSuccess) {
                                                                        Toast.makeText(context, "Approved ₹${deposit.amount.toInt()}! Entitlement activated & user notified.", Toast.LENGTH_LONG).show()
                                                                    } else {
                                                                        Toast.makeText(context, "Approval error: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                                                    ) {
                                                        Text("Approve Payment", color = Color.White, fontWeight = FontWeight.Bold)
                                                    }
                                                    Button(
                                                        onClick = {
                                                            adminScope.launch(Dispatchers.IO) {
                                                                com.example.payments.SocialIndiaPaymentOrchestrator.adminRejectPaymentOrder(
                                                                    orderId = deposit.orderId.ifBlank { deposit.id },
                                                                    depositId = deposit.id,
                                                                    userId = deposit.userId,
                                                                    utrNumber = deposit.utrNumber
                                                                )
                                                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                                    Toast.makeText(context, "Payment Rejected & user notified.", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                                                    ) {
                                                        Text("Reject", color = Color.White, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (currentTab == 5) {
                        AdminGiftManagerTab()
                    } else if (currentTab == 6) {
                        AdminBanksTab()
                    } else if (currentTab == 7) {
                        AdminCreatorPayoutsTab()
                    } else if (currentTab == 8) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = onNavigateToAiManager,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D9BF0))
                            ) {
                                Text("Manage AI Tools", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { currentTab = 12 },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Text("Automatic App Update System (APK Manager)", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Payment Gateway Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(16.dp))

                            // Direct UPI Payment Gateway Configuration Card
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF181824)),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⚡", fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Real-Time Indian UPI Gateway Config", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Configure the receiving Merchant UPI IDs, Payee Name, and Gateway Keys. Changes sync immediately across all UPI deep-links and dynamic QR codes in the app.",
                                        fontSize = 12.sp,
                                        color = Color.LightGray
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    OutlinedTextField(
                                        value = adminUpiIdInput,
                                        onValueChange = { adminUpiIdInput = it },
                                        label = { Text("Account 1 (Primary Merchant UPI ID)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF00E676),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = adminSecondaryUpiIdInput,
                                        onValueChange = { adminSecondaryUpiIdInput = it },
                                        label = { Text("Account 2 (Backup Merchant UPI ID)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF00E676),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = adminPayeeNameInput,
                                        onValueChange = { adminPayeeNameInput = it },
                                        label = { Text("Merchant Payee Display Name (e.g. Social India)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF00E676),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = adminGatewayProviderInput,
                                        onValueChange = { adminGatewayProviderInput = it },
                                        label = { Text("Gateway Provider (NPCI_DIRECT_UPI / RAZORPAY_UPI / PHONEPE_PG)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF00E676),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = adminMerchantKeyIdInput,
                                        onValueChange = { adminMerchantKeyIdInput = it },
                                        label = { Text("Merchant Key ID / App ID (Optional)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF00E676),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = adminMerchantSecretInput,
                                        onValueChange = { adminMerchantSecretInput = it },
                                        label = { Text("Merchant Key Secret / Salt (Optional)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF00E676),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = {
                                            isSavingUpiConfig = true
                                            com.example.utils.UpiPaymentManager.updateAdminUpiConfig(
                                                primaryUpiId = adminUpiIdInput,
                                                secondaryUpiId = adminSecondaryUpiIdInput,
                                                payeeName = adminPayeeNameInput,
                                                qrUrl = adminUpiQrUrl,
                                                gatewayProvider = adminGatewayProviderInput,
                                                merchantKeyId = adminMerchantKeyIdInput,
                                                merchantKeySecret = adminMerchantSecretInput
                                            ) { success ->
                                                isSavingUpiConfig = false
                                                Toast.makeText(context, if (success) "Live UPI Gateway Config Saved & Synced!" else "Failed to save UPI Config", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        enabled = !isSavingUpiConfig && adminUpiIdInput.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                                    ) {
                                        if (isSavingUpiConfig) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                                        } else {
                                            Text("Save Live UPI & Gateway Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Text("Current UPI QR Code (Legacy / Offline):", fontSize = 16.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(16.dp))

                            if (adminUpiQrUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = adminUpiQrUrl,
                                    contentDescription = "UPI QR Code",
                                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(200.dp).background(Color.DarkGray, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No QR Code Uploaded", color = Color.LightGray)
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Button(
                                onClick = { qrPickerLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                enabled = !isUploadingQr,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
                            ) {
                                if (isUploadingQr) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("Upload New UPI QR Code", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (currentTab == 9) {
                        AdminZegoCloudTab()
                    } else if (currentTab == 10) {
                        AdminStickerManagementTab()
                    } else if (currentTab == 11) {
                        AdminModerationAndKycTab(adminViewModel = adminViewModel, users = users)
                    } else if (currentTab == 12) {
                        AdminAppUpdateTab(users = users)
                    } else if (currentTab == 13) {
                        AdminReferralTab()
                    }
                }
            }
        }
    }
}

@Composable
fun AdminUserCard(
    user: User,
    adminViewModel: AdminViewModel,
    onToggleVerification: () -> Unit,
    onToggleBan: () -> Unit,
    onToggleAdmin: () -> Unit,
    onApproveMonetization: () -> Unit = {}
) {
    var showStatsManager by remember { mutableStateOf(false) }
    var showModerationManager by remember { mutableStateOf(false) }
    var moderationReasonText by remember { mutableStateOf(user.suspensionReason.ifBlank { "Community Guidelines Violation" }) }
    var customAmountText by remember { mutableStateOf("") }
    var selectedMetricType by remember { mutableStateOf("Followers") } // "Followers", "Likes", "Views"
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                ) {
                    AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        VerificationBadge(user = user, modifier = Modifier.size(16.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (user.isAdmin) {
                            Text("Admin", color = Color(0xFFEE1D52), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (user.verificationRequested && !user.isVerified) {
                            Text("Requested Blue Tick ⚠️", color = Color(0xFFFFB300), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (user.accountStatus != "ACTIVE" || user.isBanned) {
                            Text(
                                text = if (user.isBanned) "BANNED" else user.accountStatus,
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        if (user.isShadowbanned) {
                            Text("SHADOWBANNED", color = Color(0xFFA855F7), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        if (user.strikesCount > 0) {
                            Text("⚡ ${user.strikesCount}/3 Strikes", color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (user.kycVideoVerified) {
                            Text("✓ Video KYC", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("👥 Followers: ${com.example.utils.NumberFormatUtils.formatCount(user.followerCount)} (${user.followerCount})", color = Color.LightGray, fontSize = 12.sp)
                    Text(user.id, color = Color.Gray, fontSize = 10.sp)
                }
            }
            
            if (user.monetizationRequested) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onApproveMonetization,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Approve Monetization", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else if (user.isMonetized) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("✓ Monetization Approved", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onToggleVerification,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (user.isVerified) Color.DarkGray else Color(0xFF1D9BF0)
                    ),
                    modifier = Modifier.weight(1.2f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(if (user.isVerified) "Revoke Tick" else "Verify User", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = onToggleAdmin,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (user.isAdmin) Color.DarkGray else Color(0xFFEE1D52)
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(if (user.isAdmin) "Revoke Admin" else "Make Admin", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = onToggleBan,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (user.isBanned) Color.DarkGray else Color(0xFFFF5252)
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(if (user.isBanned) "Unban ID" else "Ban ID", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Account Control & Moderation Expandable Button
            Button(
                onClick = { showModerationManager = !showModerationManager },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showModerationManager) Color(0xFF3B1D2E) else Color(0xFF281822)
                ),
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (showModerationManager) "Hide Account Moderation & KYC Controls ▲" else "🛡️ Account Control (Suspend / 24h-30d / Shadowban / KYC) ▼",
                    color = Color(0xFFFF80AB),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            androidx.compose.animation.AnimatedVisibility(visible = showModerationManager) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(Color(0xFF141418), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFEE1D52).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Real-Time Account Enforcement & Reason Log", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    OutlinedTextField(
                        value = moderationReasonText,
                        onValueChange = { moderationReasonText = it },
                        label = { Text("Reason Log (Shown on User Lockout Screen)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEE1D52),
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Row 1: Temporary Restrictions (24h / 7d / 30d)
                    Text("Temporary Restrictions (Auto-Countdown):", color = Color.LightGray, fontSize = 11.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("24h" to "RESTRICT_24H", "7 Days" to "RESTRICT_7D", "30 Days" to "RESTRICT_30D").forEach { (label, action) ->
                            OutlinedButton(
                                onClick = {
                                    adminViewModel.setAccountModeration(user.id, action, moderationReasonText)
                                    Toast.makeText(context, "Applied $label restriction to @${user.username}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(34.dp),
                                contentPadding = PaddingValues(0.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                            ) {
                                Text("Restrict $label", color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Row 2: Suspend, Shadowban, Permanent Ban, Restore Active
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                adminViewModel.setAccountModeration(user.id, "SUSPEND", moderationReasonText)
                                Toast.makeText(context, "Suspended @${user.username} in real-time", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Text("Suspend", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val nextAction = if (user.isShadowbanned) "UNSHADOWBAN" else "SHADOWBAN"
                                adminViewModel.setAccountModeration(user.id, nextAction, moderationReasonText)
                                Toast.makeText(context, if (user.isShadowbanned) "Shadowban removed" else "Shadowbanned @${user.username}", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA))
                        ) {
                            Text(if (user.isShadowbanned) "Un-Shadowban" else "Shadowban", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                adminViewModel.setAccountModeration(user.id, "RESTORE_ACTIVE", "")
                                Toast.makeText(context, "Restored @${user.username} to ACTIVE", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text("Restore Active", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Row 3: Strike, Warn, Video KYC Approve/Reject
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                adminViewModel.issueStrike(user.id, moderationReasonText)
                                Toast.makeText(context, "Strike issued to @${user.username}", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(0.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5722))
                        ) {
                            Text("+1 Strike (${user.strikesCount}/3)", color = Color(0xFFFF5722), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                adminViewModel.warnUser(user.id, moderationReasonText)
                                Toast.makeText(context, "Warning sent to @${user.username}", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(0.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
                        ) {
                            Text("Warn User", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                adminViewModel.reviewVideoKyc(user.id, !user.kycVideoVerified)
                                Toast.makeText(context, if (!user.kycVideoVerified) "Video KYC Approved" else "Video KYC Revoked", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(0.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Text(if (user.kycVideoVerified) "Revoke KYC" else "Approve KYC", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { showStatsManager = !showStatsManager },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showStatsManager) Color(0xFF2E2E2E) else Color(0xFF333333)
                ),
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (showStatsManager) "Hide Boost Panel ▲" else "Boost Followers / Likes / Views ▼", 
                    color = Color.White, 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Bold
                )
            }
            
            androidx.compose.animation.AnimatedVisibility(visible = showStatsManager) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text("Boost Actions", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text("👥 Add Followers", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1000, 5000, 10000, 50000).forEach { amount ->
                            OutlinedButton(
                                onClick = { adminViewModel.addFollowers(user.id, amount) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1D9BF0)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1D9BF0).copy(alpha = 0.5f))
                            ) {
                                Text("+${com.example.utils.NumberFormatUtils.formatCount(amount)}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var showVideoPicker by remember { mutableStateOf(false) }
                    var selectedVideo by remember { mutableStateOf<com.example.model.Video?>(null) }
                    
                    Button(
                        onClick = {
                            showVideoPicker = !showVideoPicker
                            if (showVideoPicker) adminViewModel.fetchUserVideos(user.id)
                        },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text(if (selectedVideo == null) "Select Video for Boost" else "Selected: ${selectedVideo?.id?.take(6)}...", color = Color.White, fontSize = 12.sp)
                    }
                    
                    if (showVideoPicker) {
                        val videos by adminViewModel.userVideos.collectAsStateWithLifecycle()
                        if (videos.isEmpty()) {
                            Text("No videos found", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                        } else {
                            androidx.compose.foundation.lazy.LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(videos) { vid ->
                                    Box(modifier = Modifier.size(80.dp, 120.dp).clip(RoundedCornerShape(8.dp)).background(Color.Gray).clickable {
                                        selectedVideo = vid
                                        showVideoPicker = false
                                    }) {
                                        AsyncImage(model = vid.videoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        if (selectedVideo?.id == vid.id) {
                                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.5f)))
                                            androidx.compose.material.icons.Icons.Filled.CheckCircle.let {
                                                Icon(it, contentDescription = null, tint = Color.Green, modifier = Modifier.align(Alignment.Center))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(if (selectedVideo == null) "❤️ Add Likes (All Videos)" else "❤️ Add Likes to Video", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(500, 1000, 5000, 10000).forEach { amount ->
                            OutlinedButton(
                                onClick = {
                                    val currentSelectedVid = selectedVideo
                                    if (currentSelectedVid != null) {
                                        adminViewModel.addLikesToVideo(currentSelectedVid.id, amount)
                                    } else {
                                        adminViewModel.addLikesToAllVideos(user.id, amount)
                                    }
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFE2C55)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFE2C55).copy(alpha = 0.5f))
                            ) {
                                Text("+${com.example.utils.NumberFormatUtils.formatCount(amount)}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(if (selectedVideo == null) "👁️ Add Video Views (All Videos)" else "👁️ Add Views to Video", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1000, 10000, 50000, 100000).forEach { amount ->
                            OutlinedButton(
                                onClick = {
                                    val currentSelectedVid = selectedVideo
                                    if (currentSelectedVid != null) {
                                        adminViewModel.addViewsToVideo(currentSelectedVid.id, amount)
                                    } else {
                                        adminViewModel.addViewsToAllVideos(user.id, amount)
                                    }
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4CAF50)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
                            ) {
                                Text("+${com.example.utils.NumberFormatUtils.formatCount(amount)}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text("Wallet Control", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var amountText by remember { mutableStateOf("") }
                        
                        TextField(
                            value = amountText,
                            onValueChange = { text -> if (text.all { it.isDigit() }) amountText = text },
                            placeholder = { Text("Amount ($)", color = Color.Gray, fontSize = 12.sp) },
                            modifier = Modifier.weight(1.5f).height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF2A2A2A),
                                unfocusedContainerColor = Color(0xFF222222),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )
                        
                        Button(
                            onClick = {
                                val amount = amountText.toDoubleOrNull() ?: 0.0
                                if (amount > 0) {
                                    val newBal = (user.walletBalance ?: 0.0) + amount
                                    com.example.model.RepositoryManager.firestore.collection("users").document(user.id)
                                        .update("walletBalance", newBal)
                                    amountText = ""
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Add Funds", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Current Balance: $${String.format("%.2f", user.walletBalance ?: 0.0)}", color = Color.LightGray, fontSize = 12.sp)
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Custom Boost Amount", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Followers", "Likes", "Views").forEach { tabName ->
                            val isTabSelected = selectedMetricType == tabName
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isTabSelected) Color(0xFFEE1D52) else Color(0xFF2E2E2E))
                                    .clickable { selectedMetricType = tabName }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(tabName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = customAmountText,
                            onValueChange = { text -> if (text.all { it.isDigit() }) customAmountText = text },
                            placeholder = { Text("Enter number...", color = Color.Gray, fontSize = 12.sp) },
                            modifier = Modifier.weight(1.5f).height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF2A2A2A),
                                unfocusedContainerColor = Color(0xFF222222),
                                disabledContainerColor = Color(0xFF222222),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFFEE1D52),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )
                        
                        Button(
                            onClick = {
                                val amount = customAmountText.toIntOrNull() ?: 0
                                if (amount > 0) {
                                    val currentSelectedVid = selectedVideo
                                    when (selectedMetricType) {
                                        "Followers" -> adminViewModel.addFollowers(user.id, amount)
                                        "Likes" -> if (currentSelectedVid != null) adminViewModel.addLikesToVideo(currentSelectedVid.id, amount) else adminViewModel.addLikesToAllVideos(user.id, amount)
                                        "Views" -> if (currentSelectedVid != null) adminViewModel.addViewsToVideo(currentSelectedVid.id, amount) else adminViewModel.addViewsToAllVideos(user.id, amount)
                                    }
                                    customAmountText = ""
                                    com.example.utils.SoundManager.playClickSound()
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Apply", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminGiftManagerTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaRepo = remember { com.example.repository.MediaRepository(context) }
    val gifts by com.example.model.RepositoryManager.giftRepository.observeAllGiftsAdmin()
        .collectAsState(initial = com.example.repository.GiftRepository.DEFAULT_GIFTS)
    val monetizationSettings by com.example.repository.MonetizationRepository.observeMonetizationSettings()
        .collectAsState(initial = com.example.model.MonetizationSettings())

    var showAddDialog by remember { mutableStateOf(false) }
    var giftToEdit by remember { mutableStateOf<com.example.repository.LiveGiftModel?>(null) }
    var giftToDelete by remember { mutableStateOf<com.example.repository.LiveGiftModel?>(null) }
    var showCoinIconDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Coin Icon Customization Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF221A0F)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF332612)),
                        contentAlignment = Alignment.Center
                    ) {
                        com.example.ui.components.CoinIcon(size = 30.dp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Coin & Earnings Icon",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFFFFE082)
                        )
                        Text(
                            text = if (monetizationSettings.coinIconUrl.isNotBlank()) "Custom GIF/Image Active" else "Default 🪙 Active",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                }
                Button(
                    onClick = { showCoinIconDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Change Icon", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Live Stream Gifts (GIFs)",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${gifts.size} total gifts (${gifts.count { it.enabled }} active)",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            com.example.repository.GiftRepository.DEFAULT_GIFTS.forEach {
                                com.example.model.RepositoryManager.giftRepository.saveGift(it)
                            }
                            Toast.makeText(context, "Default GIF gifts restored", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Restore Defaults", fontSize = 12.sp)
                }
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("+ Add GIF Gift", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Gifts List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(gifts, key = { index, g -> "gift_${g.id}_$index" }) { _, gift ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (gift.enabled) Color(0xFF1E1E1E) else Color(0xFF141414)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gift Animated GIF / Icon
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2A2A2A)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (gift.sticker3dUrl.isNotBlank()) {
                                AsyncImage(
                                    model = gift.sticker3dUrl,
                                    contentDescription = gift.name,
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(text = gift.emoji, fontSize = 26.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Gift Details
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = gift.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = if (gift.enabled) Color.White else Color.Gray
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFF333333),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = gift.category,
                                        fontSize = 10.sp,
                                        color = Color(0xFF00F0FF),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    com.example.ui.components.CoinIcon(size = 14.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${gift.coins} Coins",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFFFD700)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Order: ${gift.order}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        // Toggle Enabled Switch
                        Switch(
                            checked = gift.enabled,
                            onCheckedChange = { isEnabled ->
                                scope.launch {
                                    com.example.model.RepositoryManager.giftRepository.toggleGiftEnabled(gift.id, isEnabled)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF4CAF50)
                            )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Edit Button
                        IconButton(onClick = { giftToEdit = gift }, modifier = Modifier.size(36.dp)) {
                            Text("✏️", fontSize = 16.sp)
                        }

                        // Delete Button
                        IconButton(onClick = { giftToDelete = gift }, modifier = Modifier.size(36.dp)) {
                            Text("🗑️", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Gift Dialog with GIF Upload, Presets & Live Preview
    if (showAddDialog || giftToEdit != null) {
        val editing = giftToEdit
        var name by remember { mutableStateOf(editing?.name ?: "") }
        var emoji by remember { mutableStateOf(editing?.emoji ?: "🎁") }
        var sticker3dUrl by remember { mutableStateOf(editing?.sticker3dUrl ?: "https://fonts.gstatic.com/s/e/notoemoji/latest/1f339/512.gif") }
        var coinsText by remember { mutableStateOf((editing?.coins ?: 10).toString()) }
        var category by remember { mutableStateOf(editing?.category ?: "Popular") }
        var orderText by remember { mutableStateOf((editing?.order ?: (gifts.size + 1)).toString()) }
        var isUploadingGiftMedia by remember { mutableStateOf(false) }

        val giftGifPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    isUploadingGiftMedia = true
                    try {
                        val uploaded = mediaRepo.uploadMedia(uri) { _ -> }
                        if (!uploaded.isNullOrEmpty()) {
                            sticker3dUrl = uploaded
                            Toast.makeText(context, "GIF uploaded successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to upload GIF", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Upload error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isUploadingGiftMedia = false
                    }
                }
            }
        }

        val giftFilePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    isUploadingGiftMedia = true
                    try {
                        val uploaded = mediaRepo.uploadMedia(uri) { _ -> }
                        if (!uploaded.isNullOrEmpty()) {
                            sticker3dUrl = uploaded
                            Toast.makeText(context, "GIF uploaded successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to upload GIF", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Upload error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isUploadingGiftMedia = false
                    }
                }
            }
        }

        val gifPresets = listOf(
            Triple("Rose 🌹", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f339/512.gif", "🌹"),
            Triple("Diamond 💎", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f48e/512.gif", "💎"),
            Triple("Crown 👑", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f451/512.gif", "👑"),
            Triple("Rocket 🚀", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f680/512.gif", "🚀"),
            Triple("Fire 🔥", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f525/512.gif", "🔥"),
            Triple("Speed Car 🏎️", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f3ce_fe0f/512.gif", "🏎️"),
            Triple("Star 🌟", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f31f/512.gif", "🌟"),
            Triple("Party 🎉", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f389/512.gif", "🎉"),
            Triple("Trophy 🏆", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f3c6/512.gif", "🏆"),
            Triple("Heart 💖", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f496/512.gif", "💖")
        )

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                giftToEdit = null
            },
            title = {
                Text(
                    text = if (editing != null) "Edit GIF Gift" else "Add New GIF Gift",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        // Live Animated GIF Preview Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF161616))
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (sticker3dUrl.isNotBlank()) {
                                AsyncImage(
                                    model = sticker3dUrl,
                                    contentDescription = "GIF Preview",
                                    modifier = Modifier.size(90.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(emoji, fontSize = 48.sp)
                            }
                            if (isUploadingGiftMedia) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color(0xFFEE1D52))
                            }
                        }
                    }

                    item {
                        // Upload Buttons for Gallery and Files
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Direct GIF Upload:", fontSize = 12.sp, color = Color.Gray)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        giftGifPickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303030)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text("🖼️ GIF Gallery", fontSize = 12.sp, color = Color.White)
                                }
                                Button(
                                    onClick = {
                                        giftFilePickerLauncher.launch("image/*")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3A)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text("📁 Browse Files", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    item {
                        Text("Popular 3D GIF Presets (Tap to select):", fontSize = 11.sp, color = Color.Gray)
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(gifPresets.size) { i ->
                                val (pName, pUrl, pEmoji) = gifPresets[i]
                                Surface(
                                    onClick = {
                                        sticker3dUrl = pUrl
                                        emoji = pEmoji
                                        if (name.isBlank()) name = pName.replace(Regex("[^a-zA-Z ]"), "").trim()
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (sticker3dUrl == pUrl) Color(0xFFEE1D52) else Color(0xFF262626)
                                ) {
                                    Text(
                                        text = pName,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = sticker3dUrl,
                            onValueChange = { sticker3dUrl = it },
                            label = { Text("GIF / Animated Image Direct URL") },
                            placeholder = { Text("https://... (.gif / .webp)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Gift Name (e.g. Royal Crown, Rose)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = emoji,
                                onValueChange = { emoji = it },
                                label = { Text("Emoji Fallback") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = coinsText,
                                onValueChange = { coinsText = it },
                                label = { Text("Coin Cost") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = { category = it },
                                label = { Text("Category (Popular/Luxury/Fun)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = orderText,
                                onValueChange = { orderText = it },
                                label = { Text("Sort Order") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val coins = coinsText.toIntOrNull() ?: 10
                        val order = orderText.toIntOrNull() ?: 1
                        if (name.isNotBlank()) {
                            scope.launch {
                                val gift = com.example.repository.LiveGiftModel(
                                    id = editing?.id ?: "gift_${System.currentTimeMillis()}",
                                    name = name.trim(),
                                    emoji = emoji.trim().ifEmpty { "🎁" },
                                    coins = coins,
                                    category = category.trim(),
                                    order = order,
                                    enabled = editing?.enabled ?: true,
                                    sticker3dUrl = sticker3dUrl.trim()
                                )
                                com.example.model.RepositoryManager.giftRepository.saveGift(gift)
                                Toast.makeText(context, "Gift saved!", Toast.LENGTH_SHORT).show()
                                showAddDialog = false
                                giftToEdit = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
                ) {
                    Text("Save Gift")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    giftToEdit = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Coin Icon Customization Dialog
    if (showCoinIconDialog) {
        var coinUrlInput by remember { mutableStateOf(monetizationSettings.coinIconUrl) }
        var isUploadingCoinMedia by remember { mutableStateOf(false) }

        val coinPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    isUploadingCoinMedia = true
                    try {
                        val uploaded = mediaRepo.uploadMedia(uri) { _ -> }
                        if (!uploaded.isNullOrEmpty()) {
                            coinUrlInput = uploaded
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
        val coinFilePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    isUploadingCoinMedia = true
                    try {
                        val uploaded = mediaRepo.uploadMedia(uri) { _ -> }
                        if (!uploaded.isNullOrEmpty()) {
                            coinUrlInput = uploaded
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

        val coinPresets = listOf(
            Pair("Spinning 3D Gold Coin", "https://fonts.gstatic.com/s/e/notoemoji/latest/1fa99/512.gif"),
            Pair("Diamond Coin", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f48e/512.gif"),
            Pair("Golden Money Bag", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f4b0/512.gif"),
            Pair("Cosmic Star Coin", "https://fonts.gstatic.com/s/e/notoemoji/latest/1f31f/512.gif")
        )

        AlertDialog(
            onDismissRequest = { showCoinIconDialog = false },
            title = {
                Text("Customize Coin & Earnings Icon", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Upload a custom animated GIF or Image to represent Coins across the whole app (Live Gifts, Earnings, Wallet, Messenger).",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )

                    // Live Coin Icon Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1E1E))
                            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        com.example.ui.components.CoinIcon(
                            size = 54.dp,
                            customUrl = coinUrlInput.ifBlank { null }
                        )
                        if (isUploadingCoinMedia) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFFFFD700))
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
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("🖼️ Gallery", fontSize = 12.sp, color = Color.White)
                        }
                        Button(
                            onClick = {
                                coinFilePickerLauncher.launch("image/*")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF383838)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("📁 Browse Files", fontSize = 12.sp, color = Color.White)
                        }
                    }

                    Text("Or select from Coin Presets:", fontSize = 11.sp, color = Color.Gray)
                    coinPresets.forEach { (cName, cUrl) ->
                        Surface(
                            onClick = { coinUrlInput = cUrl },
                            shape = RoundedCornerShape(8.dp),
                            color = if (coinUrlInput == cUrl) Color(0xFFFFB300).copy(alpha = 0.2f) else Color(0xFF242424),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (coinUrlInput == cUrl) Color(0xFFFFB300) else Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = cUrl,
                                    contentDescription = cName,
                                    modifier = Modifier.size(24.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(cName, fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = coinUrlInput,
                        onValueChange = { coinUrlInput = it },
                        label = { Text("Custom GIF / Image URL") },
                        placeholder = { Text("https://... (Leave blank for default 🪙)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val updated = monetizationSettings.copy(coinIconUrl = coinUrlInput.trim())
                            com.example.repository.MonetizationRepository.updateMonetizationSettings(updated)
                            Toast.makeText(context, "Coin Icon updated app-wide!", Toast.LENGTH_SHORT).show()
                            showCoinIconDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                ) {
                    Text("Save Coin Icon", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCoinIconDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    val target = giftToDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { giftToDelete = null },
            title = { Text("Delete Gift", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete \"${target.name} ${target.emoji}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            com.example.model.RepositoryManager.giftRepository.deleteGift(target.id)
                            Toast.makeText(context, "Gift deleted", Toast.LENGTH_SHORT).show()
                            giftToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { giftToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AdminModerationAndKycTab(
    adminViewModel: AdminViewModel,
    users: List<User>
) {
    val context = LocalContext.current
    val reportedItems by adminViewModel.reportedItems.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, REPORTS, APPEALS, KYC

    val appealedUsers = remember(users) {
        users.filter {
            it.reviewStatus == "UNDER_REVIEW" ||
            it.accountStatus != "ACTIVE" ||
            it.isBanned ||
            it.isShadowbanned ||
            it.verificationRequested
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEE1D52).copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Master Moderation, Strike & Video KYC Center",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Real-time queue for reported Posts, Reels, Comments, Stories, DMs, Community Strikes, and Video Selfie KYC Appeals.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "ALL" to "All (${reportedItems.size + appealedUsers.size})",
                            "REPORTS" to "Reports (${reportedItems.size})",
                            "APPEALS" to "Appeals & KYC (${appealedUsers.size})"
                        ).forEach { (key, label) ->
                            val selected = selectedFilter == key
                            Surface(
                                onClick = { selectedFilter = key },
                                shape = RoundedCornerShape(20.dp),
                                color = if (selected) Color(0xFFEE1D52) else Color(0xFF0F172A),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedFilter == "ALL" || selectedFilter == "APPEALS") {
            item {
                Text(
                    text = "Video KYC, Suspension Appeals & Restricted Accounts (${appealedUsers.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            if (appealedUsers.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E1E24)
                    ) {
                        Text(
                            text = "No pending Video KYC or suspension appeals right now.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(appealedUsers, key = { idx, u -> "appeal_${u.id}_$idx" }) { _, u ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF181820))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AsyncImage(
                                        model = u.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.DarkGray),
                                        contentScale = ContentScale.Crop
                                    )
                                    Column {
                                        Text("@${u.username}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            text = "Status: ${u.accountStatus} • Review: ${u.reviewStatus} • Strikes: ${u.strikesCount}/3",
                                            color = Color(0xFFF59E0B),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                if (u.kycVideoVerified) {
                                    Surface(color = Color(0xFF10B981).copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            "KYC Verified",
                                            color = Color(0xFF10B981),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            if (u.reviewReason.isNotBlank() || u.suspensionReason.isNotBlank()) {
                                Text(
                                    text = "Note / Appeal: ${u.reviewReason.ifBlank { u.suspensionReason }}",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        adminViewModel.reviewVideoKyc(u.id, true)
                                        Toast.makeText(context, "Approved KYC & Restored @${u.username}", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                ) {
                                    Text("Approve KYC & Restore", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                OutlinedButton(
                                    onClick = {
                                        adminViewModel.reviewVideoKyc(u.id, false)
                                        Toast.makeText(context, "Rejected appeal for @${u.username}", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                                ) {
                                    Text("Reject Appeal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedFilter == "ALL" || selectedFilter == "REPORTS") {
            item {
                Text(
                    text = "Content, Report & Strike Queue (${reportedItems.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            if (reportedItems.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E1E24)
                    ) {
                        Text(
                            text = "Zero unreviewed content reports! All Posts, Reels, Comments, Stories & DMs are clean.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(reportedItems, key = { idx, r -> "${r.id}_$idx" }) { _, report ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(color = Color(0xFFEE1D52).copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = "${report.targetType} • ${report.reportsCount} Flag(s)",
                                        color = Color(0xFFEE1D52),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = "Creator: @${report.ownerUsername}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = "Reason: ${report.reason}",
                                color = Color(0xFFFBBF24),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            if (report.contentPreview.isNotBlank()) {
                                Text(
                                    text = "\"${report.contentPreview}\"",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        adminViewModel.deleteReportedContent(report, issueStrikeToo = false)
                                        Toast.makeText(context, "Deleted ${report.targetType}", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                                ) {
                                    Text("1-Click Delete", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Button(
                                    onClick = {
                                        adminViewModel.deleteReportedContent(report, issueStrikeToo = true)
                                        Toast.makeText(context, "Deleted & Issued Strike", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                                ) {
                                    Text("Delete + Strike", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (report.ownerId.isNotEmpty()) {
                                            adminViewModel.warnUser(report.ownerId, "Warning regarding your ${report.targetType}: ${report.reason}")
                                            Toast.makeText(context, "Warning sent to @${report.ownerUsername}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
                                ) {
                                    Text("Warn User", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
