package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import com.example.model.MonetizationSettings
import com.example.repository.MonetizationRepository
import com.example.utils.SoundManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class CreatorPayoutSummary(
    val userId: String,
    val username: String,
    val userAvatar: String,
    val walletBalance: Double,
    val bankName: String,
    val bankAccountNumber: String,
    val bankIfscCode: String,
    val bankAccountHolder: String,
    val postCount: Int,
    val totalViews: Long,
    val calculatedEarningsInr: Double
)

data class AdminCreatorPostItem(
    val postId: String,
    val title: String,
    val mediaUrl: String,
    val views: Long,
    val likes: Long,
    val earningsInr: Double,
    val isPaid: Boolean,
    val paidAmount: Double,
    val paidAt: Long?,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCreatorPayoutsTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var creatorSummaries by remember { mutableStateOf<List<CreatorPayoutSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCreator by remember { mutableStateOf<CreatorPayoutSummary?>(null) }
    var creatorPosts by remember { mutableStateOf<List<AdminCreatorPostItem>>(emptyList()) }
    var isLoadingPosts by remember { mutableStateOf(false) }

    // Dialog for paying a specific post
    var selectedPostForPayout by remember { mutableStateOf<AdminCreatorPostItem?>(null) }
    var postPayoutAmountText by remember { mutableStateOf("") }
    var isProcessingPostPayout by remember { mutableStateOf(false) }

    // Dialog for overall creator payout
    var showManualCreditDialog by remember { mutableStateOf<CreatorPayoutSummary?>(null) }
    var manualCreditAmountText by remember { mutableStateOf("") }
    var isProcessingManualCredit by remember { mutableStateOf(false) }

    val loadCreators = {
        scope.launch {
            isLoading = true
            try {
                val db = FirebaseFirestore.getInstance()
                val settingsDeferred = scope.async { MonetizationRepository.getMonetizationSettings() }
                val usersDeferred = scope.async { db.collection("users").limit(100).get().await() }
                val postsDeferred = scope.async { db.collection("posts").limit(400).get().await() }

                val settings = settingsDeferred.await()
                val usersSnap = usersDeferred.await()
                val allPostsSnap = postsDeferred.await()

                // Fast in-memory group by userId (O(1) lookup instead of N sequential network calls)
                val postsByUserId = allPostsSnap.documents.groupBy { (it.get("userId") as? String) ?: "" }

                val list = mutableListOf<CreatorPayoutSummary>()
                for (doc in usersSnap.documents) {
                    try {
                        val uid = doc.id
                        val username = (doc.get("username") as? String) ?: "User"
                        val avatar = (doc.get("avatarUrl") as? String) ?: (doc.get("profilePicUrl") as? String) ?: ""
                        val walletBal = (doc.get("walletBalance") as? Number)?.toDouble()
                            ?: (doc.get("earnings") as? Number)?.toDouble()
                            ?: 0.0
                        val bankName = (doc.get("bankName") as? String) ?: ""
                        val bankAcc = (doc.get("bankAccountNumber") as? String) ?: ""
                        val bankIfsc = (doc.get("bankIfscCode") as? String) ?: ""
                        val bankHolder = (doc.get("bankAccountHolder") as? String) ?: ""

                        val userPosts = postsByUserId[uid] ?: emptyList()
                        val postCount = userPosts.size
                        var totViews = 0L
                        var calcEarnings = 0.0

                        for (pDoc in userPosts) {
                            try {
                                val views = (pDoc.get("views") as? Number)?.toLong()
                                    ?: (pDoc.get("viewsCount") as? Number)?.toLong()
                                    ?: (((pDoc.get("likesCount") as? Number)?.toLong() ?: 0L) * 3L)
                                totViews += views
                                val mediaTypeStr = (pDoc.get("mediaType") as? String) ?: ""
                                val postUrlStr = (pDoc.get("postUrl") as? String) ?: ""
                                val isReel = mediaTypeStr == "reel" || postUrlStr.endsWith(".mp4")
                                val cpm = if (isReel) settings.reelCpmInr else settings.videoCpmInr
                                val earning = (pDoc.get("monetizationEarnings") as? Number)?.toDouble()
                                    ?: ((views / 1000.0) * cpm).coerceAtLeast(0.0)
                                calcEarnings += earning
                            } catch (_: Exception) {}
                        }

                        if (postCount > 0 || walletBal > 0 || bankAcc.isNotEmpty()) {
                            list.add(
                                CreatorPayoutSummary(
                                    userId = uid,
                                    username = username,
                                    userAvatar = avatar,
                                    walletBalance = walletBal,
                                    bankName = bankName,
                                    bankAccountNumber = bankAcc,
                                    bankIfscCode = bankIfsc,
                                    bankAccountHolder = bankHolder,
                                    postCount = postCount,
                                    totalViews = totViews,
                                    calculatedEarningsInr = calcEarnings
                                )
                            )
                        }
                    } catch (_: Exception) {}
                }
                creatorSummaries = list.sortedByDescending { it.walletBalance + it.calculatedEarningsInr }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadCreators()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // High-contrast Informational Banner & Stat Cards
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Per-Video Creator Revenue & Payouts", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Inspect individual video/photo views & earnings. Tap 'Inspect Videos' to release per-video payouts directly into creator Cash Wallets.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val totalCalc = creatorSummaries.sumOf { it.calculatedEarningsInr }
                    val totalWallets = creatorSummaries.sumOf { it.walletBalance }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Total Creator Earnings", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            Text("₹${String.format(Locale.US, "%.2f", totalCalc)}", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("In User Wallets", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            Text("₹${String.format(Locale.US, "%.2f", totalWallets)}", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFEE1D52))
            }
        } else if (creatorSummaries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No creators with uploaded content found", color = Color(0xFF94A3B8))
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = creatorSummaries,
                    key = { index, creator -> "${creator.userId}_$index" }
                ) { _, creator ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedCreator = creator
                                scope.launch {
                                    isLoadingPosts = true
                                    try {
                                        val settings = MonetizationRepository.getMonetizationSettings()
                                        val pSnap = FirebaseFirestore.getInstance().collection("posts")
                                            .whereEqualTo("userId", creator.userId).get().await()
                                        creatorPosts = pSnap.documents.mapNotNull { pDoc ->
                                            try {
                                                val views = (pDoc.get("views") as? Number)?.toLong()
                                                    ?: (pDoc.get("viewsCount") as? Number)?.toLong()
                                                    ?: (((pDoc.get("likesCount") as? Number)?.toLong() ?: 0L) * 3L)
                                                val likes = (pDoc.get("likesCount") as? Number)?.toLong() ?: 0L
                                                val mediaTypeStr = (pDoc.get("mediaType") as? String) ?: ""
                                                val postUrlStr = (pDoc.get("postUrl") as? String) ?: ""
                                                val isReel = mediaTypeStr == "reel" || postUrlStr.endsWith(".mp4")
                                                val cpm = if (isReel) settings.reelCpmInr else settings.videoCpmInr
                                                val calculatedEarning = (pDoc.get("monetizationEarnings") as? Number)?.toDouble()
                                                    ?: ((views / 1000.0) * cpm).coerceAtLeast(0.0)
                                                val isPaid = (pDoc.get("isMonetizationPaid") as? Boolean) ?: false
                                                val paidAmt = (pDoc.get("paidRevenueAmount") as? Number)?.toDouble() ?: 0.0
                                                val paidAt = (pDoc.get("monetizationPaidAt") as? Number)?.toLong()
                                                val ts = when (val rawTs = pDoc.get("timestamp")) {
                                                    is com.google.firebase.Timestamp -> rawTs.toDate().time
                                                    is Number -> rawTs.toLong()
                                                    else -> System.currentTimeMillis()
                                                }

                                                AdminCreatorPostItem(
                                                    postId = pDoc.id,
                                                    title = ((pDoc.get("caption") as? String) ?: "Creator Content").take(45),
                                                    mediaUrl = (pDoc.get("thumbnailUrl") as? String) ?: (pDoc.get("postUrl") as? String) ?: "",
                                                    views = views,
                                                    likes = likes,
                                                    earningsInr = calculatedEarning,
                                                    isPaid = isPaid,
                                                    paidAmount = paidAmt,
                                                    paidAt = paidAt,
                                                    timestamp = ts
                                                )
                                            } catch (_: Exception) {
                                                null
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    isLoadingPosts = false
                                }
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SubcomposeAsyncImage(
                                    model = creator.userAvatar.ifEmpty { "https://picsum.photos/100" },
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color.DarkGray),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(creator.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${creator.postCount} Posts • ${creator.totalViews} Views", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("₹${String.format(Locale.US, "%.2f", creator.calculatedEarningsInr)}", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Wallet: ₹${String.format(Locale.US, "%.2f", creator.walletBalance)}", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Linked Bank Account Row with Actions
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (creator.bankAccountNumber.isNotEmpty()) {
                                        Column {
                                            Text(creator.bankName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("A/C: ••••${creator.bankAccountNumber.takeLast(4)} • IFSC: ${creator.bankIfscCode}", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                        }
                                    } else {
                                        Text("No Bank Linked Yet", color = Color(0xFFF87171), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            selectedCreator = creator
                                            scope.launch {
                                                isLoadingPosts = true
                                                try {
                                                    val settings = MonetizationRepository.getMonetizationSettings()
                                                    val pSnap = FirebaseFirestore.getInstance().collection("posts")
                                                        .whereEqualTo("userId", creator.userId).get().await()
                                                    creatorPosts = pSnap.documents.map { pDoc ->
                                                        val views = pDoc.getLong("views") ?: pDoc.getLong("viewsCount") ?: ((pDoc.getLong("likesCount") ?: 0L) * 3)
                                                        val likes = pDoc.getLong("likesCount") ?: 0L
                                                        val isReel = pDoc.getString("mediaType") == "reel" || (pDoc.getString("postUrl") ?: "").endsWith(".mp4")
                                                        val cpm = if (isReel) settings.reelCpmInr else settings.videoCpmInr
                                                        val calculatedEarning = pDoc.getDouble("monetizationEarnings") ?: ((views / 1000.0) * cpm).coerceAtLeast(0.0)
                                                        val isPaid = pDoc.getBoolean("isMonetizationPaid") ?: false
                                                        val paidAmt = pDoc.getDouble("paidRevenueAmount") ?: 0.0
                                                        val paidAt = pDoc.getLong("monetizationPaidAt")

                                                        AdminCreatorPostItem(
                                                            postId = pDoc.id,
                                                            title = (pDoc.getString("caption") ?: "Creator Content").take(45),
                                                            mediaUrl = pDoc.getString("thumbnailUrl") ?: pDoc.getString("postUrl") ?: "",
                                                            views = views,
                                                            likes = likes,
                                                            earningsInr = calculatedEarning,
                                                            isPaid = isPaid,
                                                            paidAmount = paidAmt,
                                                            paidAt = paidAt,
                                                            timestamp = pDoc.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                                isLoadingPosts = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Inspect Videos", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    Button(
                                        onClick = {
                                            showManualCreditDialog = creator
                                            manualCreditAmountText = String.format(Locale.US, "%.2f", creator.calculatedEarningsInr.takeIf { it > 0 } ?: creator.walletBalance)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Pay ₹", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal 1: Creator Individual Posts & Per-Video Payment Selection (Scroll-safe)
    if (selectedCreator != null) {
        val creator = selectedCreator!!
        Dialog(onDismissRequest = { selectedCreator = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Videos of @${creator.username}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                            Text("Select individual video to pay revenue", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                        IconButton(onClick = { selectedCreator = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0F172A)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Bank: ${creator.bankName.ifEmpty { "Not linked" }} • A/C: ${creator.bankAccountNumber}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("IFSC: ${creator.bankIfscCode} • Holder: ${creator.bankAccountHolder}", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            }
                        }
                    }

                    if (isLoadingPosts) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFFEE1D52))
                        }
                    } else if (creatorPosts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                            Text("No posts uploaded by this user", color = Color(0xFF94A3B8))
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            itemsIndexed(
                                items = creatorPosts,
                                key = { index, post -> "${post.postId}_$index" }
                            ) { _, post ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                    border = if (post.isPaid) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4ADE80).copy(alpha = 0.6f)) else null
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SubcomposeAsyncImage(
                                            model = post.mediaUrl.ifEmpty { "https://picsum.photos/100" },
                                            contentDescription = null,
                                            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(post.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("👀 ${post.views} Views • ❤️ ${post.likes} Likes", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                            if (post.isPaid) {
                                                Text("✅ Payout Confirmed (₹${String.format(Locale.US, "%.2f", post.paidAmount)})", color = Color(0xFF4ADE80), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            } else {
                                                Text("Status: Unpaid Revenue", color = Color(0xFFFBBF24), fontSize = 10.sp)
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("₹${String.format(Locale.US, "%.2f", post.earningsInr)}", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Button(
                                                onClick = {
                                                    selectedPostForPayout = post
                                                    postPayoutAmountText = String.format(Locale.US, "%.2f", post.earningsInr)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (post.isPaid) Color(0xFF334155) else Color(0xFF16A34A)
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text(if (post.isPaid) "Re-pay" else "Pay Video", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { selectedCreator = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Text("Close Inspector", color = Color.White)
                    }
                }
            }
        }
    }

    // Modal 2: Pay for an Individual Video / Post
    if (selectedPostForPayout != null && selectedCreator != null) {
        val post = selectedPostForPayout!!
        val creator = selectedCreator!!
        AlertDialog(
            onDismissRequest = { if (!isProcessingPostPayout) selectedPostForPayout = null },
            title = { Text("Confirm Video Payment", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Creator: @${creator.username}", fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Video: ${post.title}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text("Bank: ${creator.bankName}\nA/C: ${creator.bankAccountNumber}\nIFSC: ${creator.bankIfscCode}", color = Color(0xFF00E5FF), fontSize = 11.sp)

                    OutlinedTextField(
                        value = postPayoutAmountText,
                        onValueChange = { postPayoutAmountText = it },
                        label = { Text("Payout Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = postPayoutAmountText.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            isProcessingPostPayout = true
                            scope.launch {
                                try {
                                    val db = FirebaseFirestore.getInstance()
                                    val batch = db.batch()

                                    // 1. Mark video as paid in posts collection
                                    val postRef = db.collection("posts").document(post.postId)
                                    batch.update(postRef, mapOf(
                                        "isMonetizationPaid" to true,
                                        "paidRevenueAmount" to amt,
                                        "monetizationPaidAt" to System.currentTimeMillis()
                                    ))

                                    // 2. Credit amount into user's walletBalance
                                    val userRef = db.collection("users").document(creator.userId)
                                    batch.update(userRef, "walletBalance", FieldValue.increment(amt))

                                    // 3. Add record in universal wallet transactions
                                    val txnRef = db.collection("wallet_conversions").document()
                                    batch.set(txnRef, mapOf(
                                        "id" to txnRef.id,
                                        "userId" to creator.userId,
                                        "postId" to post.postId,
                                        "type" to "VIDEO_REVENUE",
                                        "inrAmount" to amt,
                                        "title" to "Video Revenue Payout",
                                        "subtitle" to "Admin paid ₹${String.format(Locale.US, "%.2f", amt)} for '${post.title}'",
                                        "timestamp" to System.currentTimeMillis()
                                    ))

                                    batch.commit().await()
                                    SoundManager.playClickSound()
                                    Toast.makeText(context, "₹${amt} paid for video! Creator wallet credited.", Toast.LENGTH_SHORT).show()

                                    // Update local state
                                    creatorPosts = creatorPosts.map {
                                        if (it.postId == post.postId) it.copy(isPaid = true, paidAmount = amt, paidAt = System.currentTimeMillis()) else it
                                    }
                                    selectedPostForPayout = null
                                    loadCreators()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to confirm payment", Toast.LENGTH_SHORT).show()
                                }
                                isProcessingPostPayout = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    if (isProcessingPostPayout) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Confirm & Pay", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPostForPayout = null }) { Text("Cancel", color = Color(0xFF94A3B8)) }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Modal 3: Admin Manual Overall Creator Payout Trigger
    if (showManualCreditDialog != null) {
        val creator = showManualCreditDialog!!
        AlertDialog(
            onDismissRequest = { if (!isProcessingManualCredit) showManualCreditDialog = null },
            title = { Text("Direct Creator Payout", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Creator: @${creator.username}", fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Bank: ${creator.bankName}\nAccount: ${creator.bankAccountNumber}\nIFSC: ${creator.bankIfscCode}", color = Color(0xFF94A3B8), fontSize = 11.sp)

                    OutlinedTextField(
                        value = manualCreditAmountText,
                        onValueChange = { manualCreditAmountText = it },
                        label = { Text("Direct Payout Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = manualCreditAmountText.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            isProcessingManualCredit = true
                            scope.launch {
                                try {
                                    val db = FirebaseFirestore.getInstance()
                                    val batch = db.batch()

                                    val userRef = db.collection("users").document(creator.userId)
                                    batch.update(userRef, "walletBalance", FieldValue.increment(amt))

                                    val txnRef = db.collection("wallet_conversions").document()
                                    batch.set(txnRef, mapOf(
                                        "id" to txnRef.id,
                                        "userId" to creator.userId,
                                        "type" to "VIDEO_REVENUE",
                                        "inrAmount" to amt,
                                        "title" to "Admin Direct Creator Payout",
                                        "subtitle" to "Direct settlement to creator wallet",
                                        "timestamp" to System.currentTimeMillis()
                                    ))

                                    batch.commit().await()
                                    SoundManager.playClickSound()
                                    Toast.makeText(context, "₹${amt} credited to creator wallet!", Toast.LENGTH_SHORT).show()
                                    showManualCreditDialog = null
                                    loadCreators()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Payment failed", Toast.LENGTH_SHORT).show()
                                }
                                isProcessingManualCredit = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    if (isProcessingManualCredit) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Confirm Payout", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualCreditDialog = null }) { Text("Cancel", color = Color(0xFF94A3B8)) }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}
