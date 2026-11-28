package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import com.example.model.DepositRequest
import com.example.model.IndianBank
import com.example.model.IndianBanksData
import com.example.model.MonetizationSettings
import com.example.model.RepositoryManager
import com.example.model.UniversalTransaction
import com.example.model.VideoEarningItem
import com.example.model.WithdrawalRequest
import com.example.repository.MonetizationRepository
import com.example.utils.SoundManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    onBack: () -> Unit,
    onNavigateToMonetization: () -> Unit = {},
    onNavigateToExclusiveRequests: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUid = RepositoryManager.getCurrentUserId()
    var userId by remember { mutableStateOf(currentUid) }

    var inrBalance by remember { mutableDoubleStateOf(0.0) }
    var pendingBalance by remember { mutableDoubleStateOf(0.0) }
    var coinsBalance by remember { mutableLongStateOf(0L) }
    var followerCount by remember { mutableLongStateOf(0L) }
    var isUserMonetized by remember { mutableStateOf(false) }
    var monetizationStatus by remember { mutableStateOf("NOT_ELIGIBLE") }
    var monetizationRequested by remember { mutableStateOf(false) }
    val monetizationSettings by MonetizationRepository.observeMonetizationSettings().collectAsState(initial = MonetizationSettings())
    val authMonFlow = remember(userId) {
        val uid = if (userId.isNotEmpty()) userId else RepositoryManager.getCurrentUserId()
        MonetizationRepository.getAuthoritativeMonetizationFlow(uid)
    }
    val authMonDoc by authMonFlow.collectAsState(initial = null)
    val effectiveMonetized = isUserMonetized ||
        monetizationStatus.equals("ACTIVE", ignoreCase = true) ||
        monetizationStatus.equals("APPROVED", ignoreCase = true) ||
        authMonDoc?.status.equals("ACTIVE", ignoreCase = true) ||
        authMonDoc?.status.equals("APPROVED", ignoreCase = true)
    val isMonetizationPending = !effectiveMonetized && (
        monetizationRequested ||
        monetizationStatus.equals("PENDING", ignoreCase = true) ||
        monetizationStatus.equals("REQUESTED", ignoreCase = true) ||
        monetizationStatus.equals("Under Review", ignoreCase = true) ||
        authMonDoc?.status.equals("PENDING", ignoreCase = true) ||
        authMonDoc?.status.equals("REQUESTED", ignoreCase = true)
    )
    val minFollowersReq = monetizationSettings.minFollowersForMonetization.coerceAtLeast(1)
    var isLoading by remember { mutableStateOf(false) }

    // Linked Bank Account & Creator UPI ID state
    var availableBanks by remember { mutableStateOf<List<IndianBank>>(IndianBanksData.DEFAULT_BANKS) }
    var selectedBank by remember { mutableStateOf<IndianBank?>(null) }
    var bankAccountHolder by remember { mutableStateOf("") }
    var bankAccountNumber by remember { mutableStateOf("") }
    var bankIfscCode by remember { mutableStateOf("") }
    var creatorUpiId by remember { mutableStateOf("") }
    var currentUsername by remember { mutableStateOf("") }
    var isBankLinked by remember { mutableStateOf(false) }
    var isUpiLinked by remember { mutableStateOf(false) }

    // History and Lists
    var allTransactions by remember { mutableStateOf<List<UniversalTransaction>>(emptyList()) }
    var videoEarningsList by remember { mutableStateOf<List<VideoEarningItem>>(emptyList()) }

    // Dialogs
    var showBankSelectorDialog by remember { mutableStateOf(false) }
    var showConvertCoinsDialog by remember { mutableStateOf(false) }
    var showConvertInrToCoinsDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var showDepositDialog by remember { mutableStateOf(false) }
    var activeUpiDepositAmount by remember { mutableStateOf<Double?>(null) }
    var selectedVideoDetail by remember { mutableStateOf<VideoEarningItem?>(null) }
    var selectedWalletTab by remember { mutableIntStateOf(0) } // 0: All History, 1: Per-Video Earnings

    // Input States
    var withdrawAmountText by remember { mutableStateOf("") }
    var depositAmountText by remember { mutableStateOf("100") }
    var depositUtrText by remember { mutableStateOf("") }
    var coinsToConvertText by remember { mutableStateOf("") }
    var inrToConvertText by remember { mutableStateOf("") }
    var depositScreenshotUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessingAction by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        depositScreenshotUri = uri
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val refreshWalletData: suspend () -> Unit = {
        val effectiveUid = if (userId.isNotEmpty()) userId else RepositoryManager.getCurrentUserId()
        if (effectiveUid.isNotEmpty()) {
            try {
                availableBanks = IndianBanksData.fetchBanks()
            } catch (_: Exception) {}

            try {
                val userDeferred = coroutineScope.async {
                    RepositoryManager.firestore.collection("users").document(effectiveUid).get().await()
                }
                val withdrawalsDeferred = coroutineScope.async {
                    RepositoryManager.firestore.collection("withdrawals")
                        .whereEqualTo("userId", effectiveUid).get().await()
                }
                val depositsDeferred = coroutineScope.async {
                    RepositoryManager.firestore.collection("deposit_requests")
                        .whereEqualTo("userId", effectiveUid).get().await()
                }
                val rcvGiftsDeferred = coroutineScope.async {
                    RepositoryManager.firestore.collection("gifts")
                        .whereEqualTo("receiverId", effectiveUid).get().await()
                }
                val sentGiftsDeferred = coroutineScope.async {
                    RepositoryManager.firestore.collection("gifts")
                        .whereEqualTo("senderId", effectiveUid).get().await()
                }
                val convDeferred = coroutineScope.async {
                    RepositoryManager.firestore.collection("wallet_conversions")
                        .whereEqualTo("userId", effectiveUid).get().await()
                }
                val postsDeferred = coroutineScope.async {
                    RepositoryManager.firestore.collection("posts")
                        .whereEqualTo("userId", effectiveUid).get().await()
                }

                val exclusiveBuyerDeferred = coroutineScope.async {
                    RepositoryManager.firestore.collection("exclusive_purchases")
                        .whereEqualTo("buyerId", effectiveUid).get().await()
                }
                val exclusiveCreatorDeferred = coroutineScope.async {
                    RepositoryManager.firestore.collection("exclusive_purchases")
                        .whereEqualTo("creatorId", effectiveUid).get().await()
                }
                val walletTxnsDeferred = coroutineScope.async {
                    RepositoryManager.firestore.collection("wallet_transactions")
                        .whereEqualTo("userId", effectiveUid).get().await()
                }

                val walletSnap = com.example.repository.ExclusiveContentRepository.ensureWalletDocument(effectiveUid)
                val userDoc = userDeferred.await()
                if (userDoc.exists()) {
                    val uWallet = userDoc.getDouble("walletBalance") ?: (userDoc.getDouble("earnings") ?: 0.0)
                    val uPend = userDoc.getDouble("pendingBalance") ?: 0.0
                    inrBalance = maxOf(uWallet, walletSnap.availableBalance)
                    pendingBalance = maxOf(uPend, walletSnap.pendingBalance)
                    coinsBalance = userDoc.getLong("coins") ?: (userDoc.getDouble("coins")?.toLong() ?: walletSnap.coinsBalance)
                    followerCount = userDoc.getLong("followerCount") ?: 0L
                    isUserMonetized = userDoc.getBoolean("isMonetized") ?: false
                    monetizationStatus = userDoc.getString("monetizationStatus") ?: if (isUserMonetized) "ACTIVE" else "NOT_ELIGIBLE"
                    monetizationRequested = userDoc.getBoolean("monetizationRequested") ?: false
                    currentUsername = userDoc.getString("username") ?: (userDoc.getString("name") ?: "")
                    val savedBankName = userDoc.getString("bankName") ?: ""
                    bankAccountNumber = userDoc.getString("bankAccountNumber") ?: ""
                    bankIfscCode = userDoc.getString("bankIfscCode") ?: ""
                    bankAccountHolder = userDoc.getString("bankAccountHolder") ?: ""
                    creatorUpiId = userDoc.getString("upiId") ?: (userDoc.getString("creatorUpiId") ?: "")

                    selectedBank = availableBanks.find {
                        it.name.equals(savedBankName, ignoreCase = true) || it.code.equals(savedBankName, ignoreCase = true)
                    } ?: if (savedBankName.isNotEmpty()) availableBanks.firstOrNull() else null

                    isBankLinked = bankAccountNumber.isNotEmpty() && selectedBank != null
                    isUpiLinked = creatorUpiId.contains("@")
                }

                val withdrawalsSnap = withdrawalsDeferred.await()
                val wList = withdrawalsSnap.documents.map { doc ->
                    val req = doc.toObject(WithdrawalRequest::class.java)
                    val idVal = doc.id
                    val ts = req?.timestamp ?: System.currentTimeMillis()
                    val amt = req?.amount ?: 0.0
                    val isUpi = req?.payoutMethod == "UPI" || (!req?.upiId.isNullOrBlank() && req?.accountNumber.isNullOrBlank())
                    val utrSuffix = if (!req?.utrReference.isNullOrBlank()) " • UTR: ${req?.utrReference}" else ""
                    UniversalTransaction(
                        id = idVal,
                        title = if (isUpi) "UPI Creator Payout" else "Bank Creator Payout",
                        subtitle = if (isUpi) "UPI: ${req?.upiId}$utrSuffix" else "${req?.bankName ?: "Bank"} (A/C: ••••${(req?.accountNumber ?: "").takeLast(4)})$utrSuffix",
                        amountText = "-₹${String.format(Locale.US, "%.2f", amt)}",
                        formattedDate = dateFormatter.format(Date(ts)),
                        isCredit = false,
                        status = req?.status ?: "Pending",
                        category = "WITHDRAWAL",
                        timestamp = ts
                    )
                }

                val depositsSnap = depositsDeferred.await()
                val dList = depositsSnap.documents.map { doc ->
                    val req = doc.toObject(DepositRequest::class.java)
                    val idVal = doc.id
                    val ts = req?.timestamp ?: System.currentTimeMillis()
                    val amt = req?.amount ?: 0.0
                    val utrInfo = if (!req?.utrNumber.isNullOrBlank()) "UTR: ${req?.utrNumber}" else "UPI / Bank Transfer"
                    UniversalTransaction(
                        id = idVal,
                        title = "Wallet UPI Top-Up",
                        subtitle = utrInfo,
                        amountText = "+₹${String.format(Locale.US, "%.2f", amt)}",
                        formattedDate = dateFormatter.format(Date(ts)),
                        isCredit = true,
                        status = req?.status ?: "Pending",
                        category = "DEPOSIT",
                        timestamp = ts
                    )
                }

                val giftsSnap = rcvGiftsDeferred.await()
                val gList = giftsSnap.documents.map { doc ->
                    val g = doc.toObject(com.example.model.GiftTransaction::class.java)
                    val idVal = doc.id
                    val ts = g?.timestamp ?: System.currentTimeMillis()
                    UniversalTransaction(
                        id = idVal,
                        title = "Live Gift Received",
                        subtitle = "${g?.giftName ?: "Gift"} ${g?.emoji ?: "🎁"} from @${g?.senderUsername ?: "Fan"}",
                        amountText = "+${g?.coins ?: 0} 🪙",
                        formattedDate = dateFormatter.format(Date(ts)),
                        isCredit = true,
                        status = "Completed",
                        category = "GIFT",
                        timestamp = ts
                    )
                }

                val sentGiftsSnap = sentGiftsDeferred.await()
                val sgList = sentGiftsSnap.documents.map { doc ->
                    val g = doc.toObject(com.example.model.GiftTransaction::class.java)
                    val idVal = doc.id
                    val ts = g?.timestamp ?: System.currentTimeMillis()
                    UniversalTransaction(
                        id = idVal,
                        title = "Live Gift Sent",
                        subtitle = "Sent ${g?.giftName ?: "Gift"} ${g?.emoji ?: "🎁"}",
                        amountText = "-${g?.coins ?: 0} 🪙",
                        formattedDate = dateFormatter.format(Date(ts)),
                        isCredit = false,
                        status = "Completed",
                        category = "GIFT",
                        timestamp = ts
                    )
                }

                val conversionsSnap = convDeferred.await()
                val cList = conversionsSnap.documents.map { doc ->
                    val type = doc.getString("type") ?: "COINS_TO_INR"
                    val inrAmt = doc.getDouble("inrAmount") ?: 0.0
                    val coinsAmt = doc.getLong("coinsAmount") ?: 0L
                    val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    val idVal = doc.id
                    when (type) {
                        "COINS_TO_INR" -> UniversalTransaction(
                            id = idVal,
                            title = "Coins to Cash Conversion",
                            subtitle = "Converted $coinsAmt coins to ₹${String.format(Locale.US, "%.2f", inrAmt)}",
                            amountText = "+₹${String.format(Locale.US, "%.2f", inrAmt)}",
                            formattedDate = dateFormatter.format(Date(ts)),
                            isCredit = true,
                            status = "Completed",
                            category = "CONVERSION",
                            timestamp = ts
                        )
                        "VIDEO_REVENUE" -> UniversalTransaction(
                            id = idVal,
                            title = doc.getString("title") ?: "Video Revenue Payout",
                            subtitle = doc.getString("subtitle") ?: "Creator content earnings credited",
                            amountText = "+₹${String.format(Locale.US, "%.2f", inrAmt)}",
                            formattedDate = dateFormatter.format(Date(ts)),
                            isCredit = true,
                            status = "Completed",
                            category = "VIDEO_REVENUE",
                            timestamp = ts
                        )
                        else -> UniversalTransaction(
                            id = idVal,
                            title = "Cash to Coins Purchase",
                            subtitle = "Purchased $coinsAmt coins for ₹${String.format(Locale.US, "%.2f", inrAmt)}",
                            amountText = "+$coinsAmt 🪙",
                            formattedDate = dateFormatter.format(Date(ts)),
                            isCredit = true,
                            status = "Completed",
                            category = "CONVERSION",
                            timestamp = ts
                        )
                    }
                }

                val exBuyerSnap = exclusiveBuyerDeferred.await()
                val exCreatorSnap = exclusiveCreatorDeferred.await()
                val walletTxnsSnap = walletTxnsDeferred.await()

                val exBuyerList = exBuyerSnap.documents.mapNotNull { doc ->
                    val rec = try { doc.toObject(com.example.repository.ExclusivePurchaseRecord::class.java) } catch (_: Exception) { null }
                        ?: return@mapNotNull null
                    UniversalTransaction(
                        id = "EXB_${doc.id}",
                        title = "Exclusive Unlock (@${rec.creatorUsername.ifBlank { "creator" }})",
                        subtitle = rec.teaserCaption.ifBlank { rec.contentType },
                        amountText = "-₹${String.format(Locale.US, "%.2f", rec.amount)}",
                        formattedDate = dateFormatter.format(Date(rec.timestamp)),
                        isCredit = false,
                        status = rec.status,
                        category = "EXCLUSIVE",
                        timestamp = rec.timestamp
                    )
                }

                val exCreatorList = exCreatorSnap.documents.mapNotNull { doc ->
                    val rec = try { doc.toObject(com.example.repository.ExclusivePurchaseRecord::class.java) } catch (_: Exception) { null }
                        ?: return@mapNotNull null
                    UniversalTransaction(
                        id = "EXC_${doc.id}",
                        title = "Exclusive Sale (@${rec.buyerUsername.ifBlank { "fan" }})",
                        subtitle = "${rec.contentType} • ${rec.status.replace('_', ' ')}",
                        amountText = "+₹${String.format(Locale.US, "%.2f", rec.creatorNetAmount)}",
                        formattedDate = dateFormatter.format(Date(rec.timestamp)),
                        isCredit = true,
                        status = rec.status,
                        category = "EXCLUSIVE",
                        timestamp = rec.timestamp
                    )
                }

                val wTxnList = walletTxnsSnap.documents.mapNotNull { doc ->
                    val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    val amt = doc.getDouble("amount") ?: 0.0
                    val dir = doc.getString("direction") ?: "CREDIT_AVAILABLE"
                    val isCreditDir = !dir.equals("DEBIT", ignoreCase = true)
                    UniversalTransaction(
                        id = "WTX_${doc.id}",
                        title = (doc.getString("type") ?: "Wallet Transaction").replace('_', ' '),
                        subtitle = doc.getString("description") ?: "",
                        amountText = "${if (isCreditDir) "+" else "-"}₹${String.format(Locale.US, "%.2f", amt)}",
                        formattedDate = dateFormatter.format(Date(ts)),
                        isCredit = isCreditDir,
                        status = doc.getString("status") ?: "Completed",
                        category = "EXCLUSIVE",
                        timestamp = ts
                    )
                }

                allTransactions = (wList + dList + gList + sgList + cList + exBuyerList + exCreatorList + wTxnList)
                    .distinctBy { it.id }
                    .sortedByDescending { it.timestamp }

                val canEarnFromVideos = isUserMonetized ||
                    monetizationStatus.equals("ACTIVE", ignoreCase = true) ||
                    monetizationStatus.equals("APPROVED", ignoreCase = true) ||
                    authMonDoc?.status.equals("ACTIVE", ignoreCase = true) ||
                    authMonDoc?.status.equals("APPROVED", ignoreCase = true)

                if (canEarnFromVideos) {
                    val postsSnap = postsDeferred.await()
                    val vList = postsSnap.documents.map { doc ->
                        val views = doc.getLong("views") ?: doc.getLong("viewsCount") ?: ((doc.getLong("likesCount") ?: 0L) * 3)
                        val likes = doc.getLong("likesCount") ?: 0L
                        val isReel = doc.getString("mediaType") == "reel" || (doc.getString("postUrl") ?: "").endsWith(".mp4")
                        val cpm = if (isReel) monetizationSettings.reelCpmInr else monetizationSettings.videoCpmInr
                        val earning = doc.getDouble("monetizationEarnings")
                            ?: ((views / 1000.0) * cpm).coerceAtLeast(0.0)
                        val isPaid = doc.getBoolean("isMonetizationPaid") ?: false
                        VideoEarningItem(
                            postId = doc.id,
                            title = (doc.getString("caption") ?: "Creator Video").take(40),
                            thumbnailUrl = doc.getString("thumbnailUrl") ?: doc.getString("postUrl") ?: "",
                            views = views,
                            likes = likes,
                            earningsInr = earning,
                            isPaid = isPaid,
                            timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()
                        )
                    }.sortedByDescending { it.earningsInr }
                    videoEarningsList = vList
                } else {
                    postsDeferred.cancel()
                    videoEarningsList = emptyList()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("WalletScreen", "Error refreshing wallet data", e)
            }
        }
    }

    LaunchedEffect(userId) {
        isLoading = true
        val effectiveUid = if (userId.isNotEmpty()) userId else RepositoryManager.getCurrentUserId()
        com.example.repository.ExclusiveContentRepository.processExpiredExclusiveRequests(effectiveUid)
        refreshWalletData()
        isLoading = false
    }

    DisposableEffect(userId) {
        val effectiveUid = if (userId.isNotEmpty()) userId else RepositoryManager.getCurrentUserId()
        var reg: com.google.firebase.firestore.ListenerRegistration? = null
        var walletReg: com.google.firebase.firestore.ListenerRegistration? = null
        if (effectiveUid.isNotEmpty()) {
            reg = RepositoryManager.firestore.collection("users").document(effectiveUid)
                .addSnapshotListener { snap, err ->
                    if (err == null && snap != null && snap.exists()) {
                        inrBalance = snap.getDouble("walletBalance") ?: (snap.getDouble("earnings") ?: 0.0)
                        snap.getDouble("pendingBalance")?.let { pendingBalance = it }
                        coinsBalance = snap.getLong("coins") ?: (snap.getDouble("coins")?.toLong() ?: 0L)
                        followerCount = snap.getLong("followerCount") ?: 0L
                        isUserMonetized = snap.getBoolean("isMonetized") ?: false
                        monetizationStatus = snap.getString("monetizationStatus") ?: if (isUserMonetized) "ACTIVE" else "NOT_ELIGIBLE"
                        monetizationRequested = snap.getBoolean("monetizationRequested") ?: false
                    }
                }
            walletReg = RepositoryManager.firestore.collection("wallets").document(effectiveUid)
                .addSnapshotListener { snap, err ->
                    if (err == null && snap != null && snap.exists()) {
                        snap.getDouble("pendingBalance")?.let { pendingBalance = it }
                        snap.getDouble("availableBalance")?.let { avail ->
                            if (avail > 0.0) inrBalance = maxOf(inrBalance, avail)
                        }
                    }
                }
        }
        onDispose {
            reg?.remove()
            walletReg?.remove()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Unified Social Wallet",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Text(
                            "Cash & Coins Multi-Balance",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (coinsBalance > 0L) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E293B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f)),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                com.example.ui.components.CoinIcon(size = 14.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = com.example.utils.NumberFormatUtils.formatCount(coinsBalance.toInt()),
                                    color = Color(0xFFFFD700),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                refreshWalletData()
                                isLoading = false
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFEE1D52))
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. DUAL WALLET DISPLAY CARDS
                item(key = "header_cards") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cash Wallet (INR)
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF22C55E).copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = Color(0xFF22C55E),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cash Wallet", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "₹${String.format(Locale.US, "%.2f", inrBalance)}",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Real Balance in INR", color = Color(0xFF4ADE80), fontSize = 10.sp)
                            }
                        }

                        // Live Coins Wallet (only visible when user holds coins > 0)
                        if (coinsBalance > 0L) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFFD700).copy(alpha = 0.6f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        com.example.ui.components.CoinIcon(size = 18.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Coins Wallet", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        com.example.ui.components.CoinIcon(size = 24.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = com.example.utils.NumberFormatUtils.formatCount(coinsBalance.toInt()),
                                            color = Color(0xFFFFD700),
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Value: ₹${String.format(Locale.US, "%.2f", coinsBalance * monetizationSettings.coinsToInrRate)}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pending Balance + Exclusive Requests Quick Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                SoundManager.playClickSound()
                                onNavigateToExclusiveRequests()
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF261C06),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.HourglassTop, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Pending Balance (48h Escrow)", color = Color(0xFFFBBF24), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "Exclusive Requests • Tap to Approve / Reject",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "₹${String.format(Locale.US, "%.2f", pendingBalance)}",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ChevronRight, contentDescription = "Open Exclusive Requests", tint = Color(0xFFFBBF24))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Monetization-aware Payout Status Banner
                    if (effectiveMonetized) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF162544),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Event, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Creator Payout: ${monetizationSettings.scheduledPayoutDayOfMonth}th of every month",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Monetized creator earnings settle to your linked UPI / Bank.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    } else {
                        val remainingFollowers = (minFollowersReq - followerCount.toInt()).coerceAtLeast(0)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToMonetization() },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF1E293B),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isMonetizationPending) Color(0xFFF59E0B).copy(alpha = 0.55f) else Color(0xFF38BDF8).copy(alpha = 0.45f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isMonetizationPending) Color(0xFFF59E0B).copy(alpha = 0.16f)
                                            else Color(0xFF38BDF8).copy(alpha = 0.16f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isMonetizationPending) Icons.Default.HourglassTop else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isMonetizationPending) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isMonetizationPending) "Monetization Application Under Review"
                                        else if (remainingFollowers == 0) "Eligible for Creator Monetization!"
                                        else "Creator Video Earnings Locked",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isMonetizationPending) "Video payouts unlock automatically after approval."
                                        else if (remainingFollowers == 0) "Apply in Creator Studio to start earning ₹ on videos."
                                        else "${followerCount}/$minFollowersReq followers • Need $remainingFollowers more to apply",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Open Monetization",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4 QUICK ACTION BUTTONS (Clean 2x2 Grid)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    SoundManager.playClickSound()
                                    showConvertCoinsDialog = true
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Coins ➔ Cash", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Button(
                                onClick = {
                                    SoundManager.playClickSound()
                                    showConvertInrToCoinsDialog = true
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Cash ➔ Coins", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    SoundManager.playClickSound()
                                    showDepositDialog = true
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("+ Add Funds", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Button(
                                onClick = {
                                    SoundManager.playClickSound()
                                    showWithdrawDialog = true
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Withdraw", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2. LINKED INDIAN BANK ACCOUNT & CREATOR UPI ID CARD
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Creator Payout Method (UPI / Bank)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                TextButton(onClick = { showBankSelectorDialog = true }) {
                                    Text(if (isBankLinked || isUpiLinked) "Edit" else "Link UPI/Bank", color = Color(0xFFEE1D52), fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            if (isUpiLinked) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E676).copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.QrCode2, contentDescription = "UPI", tint = Color(0xFF00E676))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Instant UPI Payout Linked", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("UPI ID: $creatorUpiId", color = Color.White, fontSize = 12.sp)
                                    }
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = Color(0xFF4ADE80), modifier = Modifier.size(20.dp))
                                }
                                if (isBankLinked) Spacer(modifier = Modifier.height(8.dp))
                            }
                            val activeBank = selectedBank
                            if (isBankLinked && activeBank != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SubcomposeAsyncImage(
                                        model = activeBank.logoUrl,
                                        contentDescription = activeBank.name,
                                        loading = {
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(Color(0xFF334155)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(activeBank.code.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        },
                                        error = {
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(Color(0xFF334155)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(activeBank.code.take(3), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .padding(2.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(activeBank.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("A/C: •••••••••${bankAccountNumber.takeLast(4)}", color = Color.LightGray, fontSize = 12.sp)
                                        Text("IFSC: $bankIfscCode • Name: $bankAccountHolder", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                    }
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = Color(0xFF4ADE80), modifier = Modifier.size(20.dp))
                                }
                            }
                            if (!isBankLinked && !isUpiLinked) {
                                Text(
                                    text = "No Creator UPI ID or Indian Bank account linked yet. Link your UPI ID (e.g. name@ybl) or SBI/HDFC/ICICI bank account to withdraw your Super Tips & Subscription earnings.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 3. TABS: ALL HISTORY & VIDEO EARNINGS BREAKDOWN
                    TabRow(
                        selectedTabIndex = selectedWalletTab,
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color(0xFFEE1D52),
                        indicator = { tabPositions ->
                            if (selectedWalletTab < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedWalletTab]),
                                    color = Color(0xFFEE1D52),
                                    height = 3.dp
                                )
                            }
                        }
                    ) {
                        Tab(
                            selected = selectedWalletTab == 0,
                            onClick = {
                                SoundManager.playClickSound()
                                selectedWalletTab = 0
                                coroutineScope.launch {
                                    lazyListState.scrollToItem(0)
                                }
                            },
                            text = {
                                Text(
                                    "Universal History (${allTransactions.size})",
                                    fontWeight = if (selectedWalletTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedWalletTab == 0) Color(0xFFEE1D52) else Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        )
                        Tab(
                            selected = selectedWalletTab == 1,
                            onClick = {
                                SoundManager.playClickSound()
                                selectedWalletTab = 1
                                coroutineScope.launch {
                                    lazyListState.scrollToItem(0)
                                }
                            },
                            text = {
                                Text(
                                    text = if (effectiveMonetized) "Per-Video Earnings (${videoEarningsList.size})" else "Video Earnings 🔒",
                                    fontWeight = if (selectedWalletTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedWalletTab == 1) Color(0xFFEE1D52) else Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // TAB 0: ALL UNIVERSAL TRANSACTIONS HISTORY (Guaranteed Unique Keys)
                if (selectedWalletTab == 0) {
                    if (allTransactions.isEmpty()) {
                        item(key = "empty_history") {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text("No transactions yet. Start live gifting or upload content!", color = Color(0xFF94A3B8), fontSize = 13.sp)
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = allTransactions,
                            key = { index, txn -> "${txn.id.ifEmpty { "txn" }}_${txn.timestamp}_$index" }
                        ) { _, txn ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when (txn.category) {
                                                        "GIFT" -> Color(0xFFFE2C55).copy(alpha = 0.2f)
                                                        "CONVERSION" -> Color(0xFFFF9800).copy(alpha = 0.2f)
                                                        "WITHDRAWAL" -> Color(0xFFEE1D52).copy(alpha = 0.2f)
                                                        "DEPOSIT" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                                                        "VIDEO_REVENUE" -> Color(0xFF00E5FF).copy(alpha = 0.2f)
                                                        else -> Color(0xFF1E88E5).copy(alpha = 0.2f)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = when (txn.category) {
                                                    "GIFT" -> "🎁"
                                                    "CONVERSION" -> "🔄"
                                                    "WITHDRAWAL" -> "🏦"
                                                    "DEPOSIT" -> "💳"
                                                    "VIDEO_REVENUE" -> "🎬"
                                                    else -> "💰"
                                                },
                                                fontSize = 18.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(txn.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(txn.subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                txn.formattedDate,
                                                color = Color(0xFF64748B),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = txn.amountText.replace("🪙", "").trim(),
                                                color = if (txn.isCredit) Color(0xFF4ADE80) else Color(0xFFFF5252),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            if (txn.amountText.contains("🪙")) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                com.example.ui.components.CoinIcon(size = 14.dp)
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = when (txn.status.lowercase(Locale.ROOT)) {
                                                "completed" -> Color(0xFF4ADE80).copy(alpha = 0.2f)
                                                "pending" -> Color(0xFFFF9800).copy(alpha = 0.2f)
                                                else -> Color(0xFFE53935).copy(alpha = 0.2f)
                                            }
                                        ) {
                                            Text(
                                                text = txn.status,
                                                color = when (txn.status.lowercase(Locale.ROOT)) {
                                                    "completed" -> Color(0xFF4ADE80)
                                                    "pending" -> Color(0xFFFF9800)
                                                    else -> Color(0xFFE53935)
                                                },
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 1: PER-VIDEO REVENUE BREAKDOWN (Strictly Gated for Monetized Creators Only)
                if (selectedWalletTab == 1) {
                    if (!effectiveMonetized) {
                        item(key = "locked_video_earnings") {
                            val progress = (followerCount.toFloat() / minFollowersReq.toFloat()).coerceIn(0f, 1f)
                            val remaining = (minFollowersReq - followerCount.toInt()).coerceAtLeast(0)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.2.dp,
                                    Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFFEE1D52)))
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFFEE1D52).copy(alpha = 0.22f), Color(0xFF38BDF8).copy(alpha = 0.22f))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isMonetizationPending) Icons.Default.HourglassTop else Icons.Default.Lock,
                                            contentDescription = "Locked",
                                            tint = if (isMonetizationPending) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = if (isMonetizationPending) "Monetization Review in Progress" else "Video Earnings Locked",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "Only approved monetized creators can earn ₹ from Videos & Reels. Reach the required followers and apply in Creator Studio to unlock video payouts.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.5.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        lineHeight = 18.sp
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF0F172A),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Follower Requirement",
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "${com.example.utils.NumberFormatUtils.formatCount(followerCount.toInt())} / ${com.example.utils.NumberFormatUtils.formatCount(minFollowersReq)}",
                                                    color = if (remaining == 0) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(4.dp)),
                                                color = if (remaining == 0) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                                                trackColor = Color(0xFF334155)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = if (isMonetizationPending) "Your request is under admin verification."
                                                else if (remaining == 0) "Requirement met! You can apply for monetization now."
                                                else "$remaining more followers needed to unlock monetization apply.",
                                                color = if (remaining == 0) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            SoundManager.playClickSound()
                                            onNavigateToMonetization()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (remaining == 0 && !isMonetizationPending) Color(0xFF16A34A) else Color(0xFF2563EB)
                                        )
                                    ) {
                                        Text(
                                            text = if (isMonetizationPending) "View Application Status"
                                            else if (remaining == 0) "Apply for Monetization →"
                                            else "Open Creator Studio",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else if (videoEarningsList.isEmpty()) {
                        item(key = "empty_videos") {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text("No videos posted yet. Upload videos to earn revenue!", color = Color(0xFF94A3B8), fontSize = 13.sp)
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = videoEarningsList,
                            key = { index, vid -> "${vid.postId.ifEmpty { "vid" }}_$index" }
                        ) { _, vid ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                                    .clickable {
                                        SoundManager.playClickSound()
                                        selectedVideoDetail = vid
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (vid.isPaid) Color(0xFF22C55E).copy(alpha = 0.5f) else Color(0xFF334155))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (vid.thumbnailUrl.isNotBlank()) {
                                            SubcomposeAsyncImage(
                                                model = vid.thumbnailUrl,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color.Black),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFF0F172A)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF64748B),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(vid.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("👀 ${vid.views} Views • ❤️ ${vid.likes} Likes", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                            Text("CPM ₹${monetizationSettings.videoCpmInr.toInt()}/1k", color = Color(0xFF64748B), fontSize = 10.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("₹${String.format(Locale.US, "%.2f", vid.earningsInr)}", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text("Revenue", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // 3D Visual Pill & Admin Paid Status Indicator
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("📊", fontSize = 12.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Tap for 3D Breakdown", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (vid.isPaid) Color(0xFF16A34A).copy(alpha = 0.25f) else Color(0xFFFF9800).copy(alpha = 0.2f),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, if (vid.isPaid) Color(0xFF16A34A) else Color(0xFFFF9800))
                                        ) {
                                            Text(
                                                text = if (vid.isPaid) "Paid & Credited ✅" else "Pending Review ⏳",
                                                color = if (vid.isPaid) Color(0xFF4ADE80) else Color(0xFFFFB74D),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "footer_space") {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }

    // --- DIALOG 1: CREATOR UPI ID & INDIAN BANK SELECTOR FORM ---
    if (showBankSelectorDialog) {
        var tempSelectedBank by remember { mutableStateOf(selectedBank ?: availableBanks.firstOrNull() ?: IndianBanksData.DEFAULT_BANKS.first()) }
        var tempAccountHolder by remember { mutableStateOf(bankAccountHolder) }
        var tempAccountNumber by remember { mutableStateOf(bankAccountNumber) }
        var tempIfsc by remember { mutableStateOf(bankIfscCode) }
        var tempUpiId by remember { mutableStateOf(creatorUpiId) }
        val dialogScrollState = rememberScrollState()

        Dialog(onDismissRequest = { showBankSelectorDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 660.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .verticalScroll(dialogScrollState)
                ) {
                    Text("Link Creator UPI ID / Indian Bank", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                    Text("Link your UPI ID or Bank Account for real payouts", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tempUpiId,
                        onValueChange = { tempUpiId = it.trim() },
                        label = { Text("Creator UPI ID (e.g. yourname@ybl / @okaxis)") },
                        placeholder = { Text("9876543210@paytm") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Or Select Indian Bank (IMPS / NEFT):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Horizontal Bank Selector with real bank logos
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(availableBanks, key = { index, b -> "${b.id}_$index" }) { _, b ->
                            val isSelected = tempSelectedBank.id == b.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF0F172A) else Color(0xFF334155),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00E5FF)) else null,
                                modifier = Modifier
                                    .width(90.dp)
                                    .clickable {
                                        tempSelectedBank = b
                                        if (tempIfsc.isEmpty()) tempIfsc = "${b.code}0001234"
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    SubcomposeAsyncImage(
                                        model = b.logoUrl,
                                        contentDescription = b.name,
                                        loading = {
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(Color(0xFF334155)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(b.code.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        },
                                        error = {
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(Color(0xFF334155)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(b.code.take(3), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .padding(2.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(b.code, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(b.name, color = Color.LightGray, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = tempAccountHolder,
                        onValueChange = { tempAccountHolder = it },
                        label = { Text("Account Holder Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempAccountNumber,
                        onValueChange = { tempAccountNumber = it },
                        label = { Text("Account Number") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempIfsc,
                        onValueChange = { tempIfsc = it.uppercase() },
                        label = { Text("IFSC Code (e.g. SBIN0001234)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showBankSelectorDialog = false }) {
                            Text("Cancel", color = Color(0xFF94A3B8))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val hasValidUpi = tempUpiId.contains("@") && tempUpiId.length >= 5
                                val hasValidBank = tempAccountNumber.isNotBlank() && tempIfsc.isNotBlank()
                                if (hasValidUpi || hasValidBank) {
                                    coroutineScope.launch {
                                        val effectiveUid = if (userId.isNotEmpty()) userId else RepositoryManager.getCurrentUserId()
                                        val updates = mutableMapOf<String, Any>(
                                            "upiId" to tempUpiId.trim(),
                                            "creatorUpiId" to tempUpiId.trim()
                                        )
                                        if (hasValidBank) {
                                            updates["bankName"] = tempSelectedBank.name
                                            updates["bankCode"] = tempSelectedBank.code
                                            updates["bankAccountNumber"] = tempAccountNumber.trim()
                                            updates["bankIfscCode"] = tempIfsc.trim()
                                            updates["bankAccountHolder"] = tempAccountHolder.trim()
                                        }
                                        RepositoryManager.firestore.collection("users").document(effectiveUid).set(
                                            updates,
                                            SetOptions.merge()
                                        ).await()

                                        creatorUpiId = tempUpiId.trim()
                                        isUpiLinked = hasValidUpi
                                        if (hasValidBank) {
                                            selectedBank = tempSelectedBank
                                            bankAccountNumber = tempAccountNumber.trim()
                                            bankIfscCode = tempIfsc.trim()
                                            bankAccountHolder = tempAccountHolder.trim()
                                            isBankLinked = true
                                        }
                                        Toast.makeText(context, "Creator payout details saved!", Toast.LENGTH_SHORT).show()
                                        showBankSelectorDialog = false
                                    }
                                } else {
                                    Toast.makeText(context, "Enter a valid UPI ID (with @) or Bank Account + IFSC", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                        ) {
                            Text("Save Payout Method")
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG 2: CONVERT COINS TO REAL CASH (INR) ---
    if (showConvertCoinsDialog) {
        val rate = monetizationSettings.coinsToInrRate
        AlertDialog(
            onDismissRequest = { showConvertCoinsDialog = false },
            title = { Text("Convert Live Coins to Cash", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Available: $coinsBalance ", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                        com.example.ui.components.CoinIcon(size = 14.dp)
                        Text(" coins", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                    }
                    Text("Conversion Rate: 1 Coin = ₹${rate} INR", color = Color(0xFF94A3B8), fontSize = 12.sp)

                    OutlinedTextField(
                        value = coinsToConvertText,
                        onValueChange = { coinsToConvertText = it },
                        label = { Text("Coins to convert") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val enteredCoins = coinsToConvertText.toLongOrNull() ?: 0L
                    val cashYield = enteredCoins * rate
                    Text(
                        "You will receive: ₹${String.format(Locale.US, "%.2f", cashYield)} in Cash Wallet",
                        color = Color(0xFF4ADE80),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val enteredCoins = coinsToConvertText.toLongOrNull() ?: 0L
                        if (enteredCoins in 1..coinsBalance) {
                            coroutineScope.launch {
                                val cashYield = enteredCoins * rate
                                val effectiveUid = if (userId.isNotEmpty()) userId else RepositoryManager.getCurrentUserId()
                                val batch = RepositoryManager.firestore.batch()

                                val userRef = RepositoryManager.firestore.collection("users").document(effectiveUid)
                                batch.update(userRef, "coins", FieldValue.increment(-enteredCoins))
                                batch.update(userRef, "walletBalance", FieldValue.increment(cashYield))

                                val convRef = RepositoryManager.firestore.collection("wallet_conversions").document()
                                batch.set(convRef, mapOf(
                                    "id" to convRef.id,
                                    "userId" to effectiveUid,
                                    "type" to "COINS_TO_INR",
                                    "coinsAmount" to enteredCoins,
                                    "inrAmount" to cashYield,
                                    "timestamp" to System.currentTimeMillis()
                                ))
                                batch.commit().await()

                                coinsBalance -= enteredCoins
                                inrBalance += cashYield
                                Toast.makeText(context, "Successfully converted $enteredCoins coins into ₹${String.format(Locale.US, "%.2f", cashYield)}!", Toast.LENGTH_SHORT).show()
                                showConvertCoinsDialog = false
                                coinsToConvertText = ""
                                refreshWalletData()
                            }
                        } else {
                            Toast.makeText(context, "Invalid coins amount (Max: $coinsBalance)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                ) {
                    Text("Convert Now", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConvertCoinsDialog = false }) { Text("Cancel", color = Color(0xFF94A3B8)) }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // --- DIALOG 3: CONVERT CASH TO COINS (INR TO COINS) ---
    if (showConvertInrToCoinsDialog) {
        val inrRate = monetizationSettings.inrToCoinsRate
        AlertDialog(
            onDismissRequest = { showConvertInrToCoinsDialog = false },
            title = { Text("Buy Coins with Cash", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Available Cash: ₹${String.format(Locale.US, "%.2f", inrBalance)}", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rate: ₹1 INR = $inrRate ", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        com.example.ui.components.CoinIcon(size = 14.dp)
                        Text(" Coins", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }

                    OutlinedTextField(
                        value = inrToConvertText,
                        onValueChange = { inrToConvertText = it },
                        label = { Text("INR Cash amount to spend (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val enteredInr = inrToConvertText.toDoubleOrNull() ?: 0.0
                    val coinsYield = (enteredInr * inrRate).toLong()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "You will receive: $coinsYield ",
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        com.example.ui.components.CoinIcon(size = 14.dp)
                        Text(
                            " Coins in Coins Wallet",
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val enteredInr = inrToConvertText.toDoubleOrNull() ?: 0.0
                        if (enteredInr > 0 && enteredInr <= inrBalance) {
                            coroutineScope.launch {
                                val coinsYield = (enteredInr * inrRate).toLong()
                                val effectiveUid = if (userId.isNotEmpty()) userId else RepositoryManager.getCurrentUserId()
                                val batch = RepositoryManager.firestore.batch()

                                val userRef = RepositoryManager.firestore.collection("users").document(effectiveUid)
                                batch.update(userRef, "walletBalance", FieldValue.increment(-enteredInr))
                                batch.update(userRef, "coins", FieldValue.increment(coinsYield))

                                val convRef = RepositoryManager.firestore.collection("wallet_conversions").document()
                                batch.set(convRef, mapOf(
                                    "id" to convRef.id,
                                    "userId" to effectiveUid,
                                    "type" to "INR_TO_COINS",
                                    "coinsAmount" to coinsYield,
                                    "inrAmount" to enteredInr,
                                    "timestamp" to System.currentTimeMillis()
                                ))
                                batch.commit().await()

                                inrBalance -= enteredInr
                                coinsBalance += coinsYield
                                Toast.makeText(context, "Successfully purchased $coinsYield coins for ₹${String.format(Locale.US, "%.2f", enteredInr)}!", Toast.LENGTH_SHORT).show()
                                showConvertInrToCoinsDialog = false
                                inrToConvertText = ""
                                refreshWalletData()
                            }
                        } else {
                            Toast.makeText(context, "Invalid amount (Max: ₹${String.format(Locale.US, "%.2f", inrBalance)})", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                ) {
                    Text("Buy Coins", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConvertInrToCoinsDialog = false }) { Text("Cancel", color = Color(0xFF94A3B8)) }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // --- DIALOG 4: WITHDRAW TO CREATOR UPI OR INDIAN BANK ---
    if (showWithdrawDialog) {
        var selectedPayoutType by remember { mutableStateOf(if (isUpiLinked) "UPI" else "BANK") }
        AlertDialog(
            onDismissRequest = { showWithdrawDialog = false },
            title = { Text("Request Creator Payout", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isBankLinked && !isUpiLinked) {
                        Text(
                            "⚠️ No UPI ID or Indian Bank account linked yet. Please tap 'Link UPI/Bank' first.",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isUpiLinked) {
                                FilterChip(
                                    selected = selectedPayoutType == "UPI",
                                    onClick = { selectedPayoutType = "UPI" },
                                    label = { Text("UPI ($creatorUpiId)", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                            if (isBankLinked) {
                                FilterChip(
                                    selected = selectedPayoutType == "BANK",
                                    onClick = { selectedPayoutType = "BANK" },
                                    label = { Text("Bank (••${bankAccountNumber.takeLast(4)})") }
                                )
                            }
                        }
                        if (selectedPayoutType == "UPI" && isUpiLinked) {
                            Text("Destination UPI: $creatorUpiId", color = Color(0xFF00E676), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        } else if (isBankLinked) {
                            Text("Destination Bank: ${selectedBank?.name} (A/C ••••${bankAccountNumber.takeLast(4)})", color = Color(0xFF00E5FF), fontSize = 12.sp)
                        }
                    }

                    Text("Available Cash: ₹${String.format(Locale.US, "%.2f", inrBalance)}", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
                    Text("Min Payout: ₹${monetizationSettings.minWithdrawalInr.toInt()} • Synced live with Admin Payouts", color = Color(0xFF94A3B8), fontSize = 11.sp)

                    OutlinedTextField(
                        value = withdrawAmountText,
                        onValueChange = { withdrawAmountText = it },
                        label = { Text("Amount to withdraw (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = withdrawAmountText.toDoubleOrNull() ?: 0.0
                        if (!isBankLinked && !isUpiLinked) {
                            Toast.makeText(context, "Please link your UPI ID or Bank Account first", Toast.LENGTH_SHORT).show()
                        } else if (amt < monetizationSettings.minWithdrawalInr) {
                            Toast.makeText(context, "Minimum withdrawal is ₹${monetizationSettings.minWithdrawalInr.toInt()}", Toast.LENGTH_SHORT).show()
                        } else if (amt > inrBalance) {
                            Toast.makeText(context, "Insufficient cash balance", Toast.LENGTH_SHORT).show()
                        } else {
                            coroutineScope.launch {
                                val effectiveUid = if (userId.isNotEmpty()) userId else RepositoryManager.getCurrentUserId()
                                val docRef = RepositoryManager.firestore.collection("withdrawals").document()
                                val useUpi = selectedPayoutType == "UPI" && isUpiLinked
                                val req = WithdrawalRequest(
                                    id = docRef.id,
                                    userId = effectiveUid,
                                    username = currentUsername.ifBlank { "Creator_${effectiveUid.take(5)}" },
                                    amount = amt,
                                    payoutMethod = if (useUpi) "UPI" else "BANK",
                                    upiId = if (useUpi) creatorUpiId else "",
                                    bankName = if (useUpi) "UPI Direct ($creatorUpiId)" else (selectedBank?.name ?: "Indian Bank"),
                                    accountNumber = if (useUpi) creatorUpiId else bankAccountNumber,
                                    ifscCode = if (useUpi) "UPI" else bankIfscCode,
                                    accountHolderName = bankAccountHolder.ifBlank { currentUsername },
                                    status = "Pending",
                                    timestamp = System.currentTimeMillis()
                                )
                                docRef.set(req).await()

                                RepositoryManager.firestore.collection("users").document(effectiveUid)
                                    .update("walletBalance", FieldValue.increment(-amt)).await()

                                inrBalance -= amt
                                Toast.makeText(context, "Payout request of ₹$amt submitted to Admin Panel!", Toast.LENGTH_LONG).show()
                                showWithdrawDialog = false
                                withdrawAmountText = ""
                                refreshWalletData()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
                ) {
                    Text("Submit Payout Request", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawDialog = false }) { Text("Cancel", color = Color(0xFF94A3B8)) }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // --- REAL INDIAN UPI PAYMENT BOTTOM SHEET FOR WALLET TOP-UP ---
    activeUpiDepositAmount?.let { topUpAmt ->
        com.example.ui.components.RealUpiPaymentBottomSheet(
            title = "Wallet & Coin Top-Up (₹${topUpAmt.toInt()})",
            subtitle = "Adds ₹${topUpAmt.toInt()} Cash + ${(topUpAmt * 10).toInt()} Coins",
            amountRupees = topUpAmt,
            productType = com.example.model.PaymentProductType.WALLET_DEPOSIT,
            onDismiss = { activeUpiDepositAmount = null },
            onPaymentVerified = { _ ->
                activeUpiDepositAmount = null
                coroutineScope.launch { refreshWalletData() }
            },
            onUtrSubmitted = { _, _ ->
                activeUpiDepositAmount = null
                coroutineScope.launch { refreshWalletData() }
            }
        )
    }

    // --- DIALOG 5: BANK-GRADE ADD FUNDS VIA UPI BOTTOM SHEET ---
    if (showDepositDialog) {
        val depositSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val depositPresets = listOf(50, 100, 200, 500, 1000, 2000)
        val parsedAmount = depositAmountText.toDoubleOrNull() ?: 0.0
        val estimatedCoins = (parsedAmount * monetizationSettings.inrToCoinsRate).toLong()

        ModalBottomSheet(
            onDismissRequest = { showDepositDialog = false },
            sheetState = depositSheetState,
            containerColor = Color(0xFF0F172A),
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 6.dp)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Add Funds via UPI",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Instant wallet credit with verified UTR",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF16A34A).copy(alpha = 0.18f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("100% Safe", color = Color(0xFF4ADE80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Large Interactive Amount Input Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF1E293B),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF334155))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "ENTER AMOUNT (INR)",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "₹",
                                color = Color(0xFF4ADE80),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = depositAmountText,
                                onValueChange = { depositAmountText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(7) },
                                placeholder = { Text("100", color = Color(0xFF64748B), fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                                textStyle = LocalTextStyle.current.copy(
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = Color(0xFF4ADE80)
                                )
                            )
                        }
                        if (parsedAmount >= 10.0) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Wallet Cash + Bonus Value", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFFD700).copy(alpha = 0.14f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        com.example.ui.components.CoinIcon(size = 13.dp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "₹${parsedAmount.toInt()} + ${com.example.utils.NumberFormatUtils.formatCount(estimatedCoins.toInt())} Coins",
                                            color = Color(0xFFFFD700),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2-Row Preset Grid (6 Amounts)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Quick Select",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    depositPresets.chunked(3).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                val isSelected = depositAmountText == preset.toString()
                                val isPopular = preset == 200
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) Color(0xFF16A34A).copy(alpha = 0.24f)
                                            else Color(0xFF1E293B)
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF4ADE80) else Color(0xFF334155),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            SoundManager.playClickSound()
                                            depositAmountText = preset.toString()
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "₹$preset",
                                            color = if (isSelected) Color(0xFF4ADE80) else Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp
                                        )
                                        if (isPopular) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "POPULAR",
                                                color = Color(0xFFFBBF24),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Supported UPI Apps Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("GPay", "PhonePe", "Paytm", "BHIM", "QR/UTR").forEach { appLabel ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E293B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Text(
                                text = appLabel,
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 7.dp)
                            )
                        }
                    }
                }

                // Primary CTA Button
                Button(
                    onClick = {
                        val amt = depositAmountText.toDoubleOrNull() ?: 100.0
                        if (amt >= 10.0) {
                            SoundManager.playClickSound()
                            showDepositDialog = false
                            activeUpiDepositAmount = amt
                        } else {
                            Toast.makeText(context, "Minimum deposit amount is ₹10", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFF16A34A), Color(0xFF00E676)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (parsedAmount >= 10.0) "Proceed to Pay ₹${parsedAmount.toInt()} →" else "Enter Amount (Min ₹10)",
                            color = Color(0xFF052E16),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }

                Text(
                    text = "Secured Indian UPI Gateway • Zero hidden convenience fees",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }
        }
    }

    // --- DIALOG 6: 3D ANIMATED PER-VIDEO REVENUE & ENGAGEMENT BREAKDOWN ---
    selectedVideoDetail?.let { vid ->
        Dialog(onDismissRequest = { selectedVideoDetail = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFEE1D52)))),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎬", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("3D Video Revenue Breakdown", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        }
                        IconButton(onClick = { selectedVideoDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Media preview header card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (vid.thumbnailUrl.isNotBlank()) {
                            SubcomposeAsyncImage(
                                model = vid.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(vid.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Uploaded on ${dateFormatter.format(Date(vid.timestamp))}", color = Color(0xFF94A3B8), fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4 Grid Metric 3D Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Metric 1: Views
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0F172A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("👀 Views", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${vid.views}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        // Metric 2: Likes
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0F172A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("❤️ Likes", color = Color(0xFFF43F5E), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${vid.likes}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Metric 3: CPM Rate
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0F172A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("📈 CPM Rate", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("₹${monetizationSettings.videoCpmInr.toInt()}/1k", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        // Metric 4: Total Revenue
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0F172A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4ADE80).copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("💰 Revenue", color = Color(0xFF4ADE80), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("₹${String.format(Locale.US, "%.2f", vid.earningsInr)}", color = Color(0xFF4ADE80), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Payout Verification Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (vid.isPaid) Color(0xFF14532D).copy(alpha = 0.4f) else Color(0xFF78350F).copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (vid.isPaid) Color(0xFF22C55E) else Color(0xFFF59E0B))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (vid.isPaid) Icons.Default.CheckCircle else Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = if (vid.isPaid) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (vid.isPaid) "PAYOUT CONFIRMED & CREDITED" else "PENDING ADMIN REVIEW",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (vid.isPaid) Color(0xFF4ADE80) else Color(0xFFFBBF24)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (vid.isPaid)
                                    "Admin has reviewed and verified ₹${String.format(Locale.US, "%.2f", vid.earningsInr)} for this video. The money is credited to your Cash Wallet and ready for bank withdrawal."
                                else
                                    "Your video is generating views and CPM revenue. Admin reviews and releases payouts directly to your Cash Wallet every week/month.",
                                color = Color.White,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { selectedVideoDetail = null },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close Details", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
