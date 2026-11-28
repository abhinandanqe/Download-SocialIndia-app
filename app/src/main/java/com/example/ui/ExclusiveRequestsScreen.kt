package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.RepositoryManager
import com.example.repository.ExclusiveActionResult
import com.example.repository.ExclusiveContentRepository
import com.example.repository.ExclusivePurchaseRecord
import com.example.repository.isExclusiveStatusPending
import com.example.repository.isExclusiveStatusUnlocked
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExclusiveRequestsScreen(
    onBack: () -> Unit,
    onNavigateToPost: (postId: String, contentType: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val creatorId = RepositoryManager.getCurrentUserId()

    val requests by remember(creatorId) {
        ExclusiveContentRepository.observeCreatorExclusiveRequests(creatorId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val walletSnapshot by remember(creatorId) {
        ExclusiveContentRepository.observeWalletDocument(creatorId)
    }.collectAsStateWithLifecycle(initialValue = null)

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Pending, 1: History
    var busyPurchaseId by remember { mutableStateOf<String?>(null) }
    var rejectTarget by remember { mutableStateOf<ExclusivePurchaseRecord?>(null) }
    var rejectReason by remember { mutableStateOf("Creator declined request") }

    // Ticker for live 48h countdown display & initial expired sweep
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(creatorId) {
        ExclusiveContentRepository.ensureWalletDocument(creatorId)
        ExclusiveContentRepository.processExpiredExclusiveRequests(creatorId)
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val pendingList = remember(requests) {
        requests.filter { isExclusiveStatusPending(it.status) }
    }
    val historyList = remember(requests) {
        requests.filter { !isExclusiveStatusPending(it.status) }
    }
    val displayedList = if (selectedTab == 0) pendingList else historyList
    val pendingSum = pendingList.sumOf { it.creatorNetAmount }
    val displayPendingBal = maxOf(walletSnapshot?.pendingBalance ?: 0.0, pendingSum)
    val displayAvailableBal = walletSnapshot?.availableBalance ?: 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Exclusive Requests",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Approve within 48h or auto-refunded",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val count = ExclusiveContentRepository.processExpiredExclusiveRequests(creatorId)
                                val msg = if (count > 0) "Processed $count expired refund(s)" else "Up to date"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF00E5FF))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Balance Summary Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF261C06),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.55f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Pending Balance", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", displayPendingBal)}",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text("${pendingList.size} awaiting action", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0D281D),
                    border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.55f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Available Balance", color = Color(0xFF4ADE80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", displayAvailableBal)}",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text("Ready to withdraw", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    }
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E293B),
                contentColor = ExclusiveGoldStart,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = ExclusiveGoldStart,
                            height = 3.dp
                        )
                    }
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.testTag("tab_pending_requests"),
                    text = {
                        Text(
                            "Pending (${pendingList.size})",
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) ExclusiveGoldStart else Color.White.copy(alpha = 0.7f)
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("tab_history_requests"),
                    text = {
                        Text(
                            "History (${historyList.size})",
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 1) ExclusiveGoldStart else Color.White.copy(alpha = 0.7f)
                        )
                    }
                )
            }

            if (displayedList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Default.MarkEmailRead else Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedTab == 0) "No Pending Exclusive Requests" else "No Past Exclusive Requests",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (selectedTab == 0)
                                "When viewers pay to unlock your exclusive posts, reels, or stories, they appear here for your approval."
                            else
                                "Approved, rejected, and auto-refunded unlock requests appear here.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayedList, key = { it.id }) { req ->
                        ExclusiveRequestCard(
                            request = req,
                            nowMs = nowMs,
                            isBusy = busyPurchaseId == req.id,
                            onPreviewClick = { onNavigateToPost(req.contentId, req.contentType) },
                            onApprove = {
                                busyPurchaseId = req.id
                                scope.launch {
                                    when (val res = ExclusiveContentRepository.approveExclusiveRequest(req.id, creatorId)) {
                                        is ExclusiveActionResult.Success -> {
                                            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                        }
                                        is ExclusiveActionResult.Failure -> {
                                            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    busyPurchaseId = null
                                }
                            },
                            onReject = {
                                rejectReason = "Creator declined request"
                                rejectTarget = req
                            }
                        )
                    }
                }
            }
        }
    }

    val pendingReject = rejectTarget
    if (pendingReject != null) {
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            containerColor = Color(0xFF1E293B),
            title = { Text("Reject & Refund Buyer?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Rejecting @${pendingReject.buyerUsername.ifBlank { "buyer" }}'s request will immediately refund ₹${pendingReject.amount.toInt()} back to their wallet and remove ₹${String.format(Locale.US, "%.2f", pendingReject.creatorNetAmount)} from your Pending balance.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.5.sp
                    )
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { if (it.length <= 120) rejectReason = it },
                        label = { Text("Reason (sent to buyer)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = pendingReject
                        rejectTarget = null
                        busyPurchaseId = target.id
                        scope.launch {
                            when (
                                val res = ExclusiveContentRepository.rejectExclusiveRequest(
                                    purchaseId = target.id,
                                    actorId = creatorId,
                                    reason = rejectReason.ifBlank { "Creator declined request" },
                                    newStatus = "rejected"
                                )
                            ) {
                                is ExclusiveActionResult.Success -> {
                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                }
                                is ExclusiveActionResult.Failure -> {
                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                }
                            }
                            busyPurchaseId = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Reject & Refund", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

@Composable
private fun ExclusiveRequestCard(
    request: ExclusivePurchaseRecord,
    nowMs: Long,
    isBusy: Boolean,
    onPreviewClick: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val isPending = isExclusiveStatusPending(request.status)
    val isApproved = isExclusiveStatusUnlocked(request.status)
    val dateFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(
            1.dp,
            when {
                isPending -> Color(0xFFF59E0B).copy(alpha = 0.55f)
                isApproved -> Color(0xFF22C55E).copy(alpha = 0.45f)
                else -> Color(0xFF38BDF8).copy(alpha = 0.35f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exclusive_request_card_${request.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AsyncImage(
                    model = request.buyerAvatar.ifBlank {
                        "https://api.dicebear.com/7.x/avataaars/png?seed=${request.buyerUsername.ifBlank { request.buyerId }}"
                    },
                    contentDescription = request.buyerUsername,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.dp, ExclusiveGoldStart, CircleShape)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "@${request.buyerUsername.ifBlank { request.buyerId.take(8) }}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${request.contentType} • ${dateFmt.format(Date(request.timestamp))}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${String.format(Locale.US, "%.2f", request.creatorNetAmount)}",
                        color = Color(0xFF4ADE80),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Paid ₹${request.amount.toInt()}",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .clickable { onPreviewClick() }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (request.blurredThumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = request.blurredThumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.title.ifBlank { request.teaserCaption.ifBlank { "Exclusive ${request.contentType}" } },
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isPending) {
                        Text(
                            text = formatCountdownRemaining(request.expiresAt, nowMs),
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = when {
                                isApproved -> "Approved & Credited"
                                request.status.equals("refunded", true) -> "Auto-Refunded (48h)"
                                else -> "Rejected & Refunded"
                            },
                            color = if (isApproved) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isPending) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        enabled = !isBusy,
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("reject_request_${request.id}")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reject", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onApprove,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("approve_request_${request.id}")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Approve", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
