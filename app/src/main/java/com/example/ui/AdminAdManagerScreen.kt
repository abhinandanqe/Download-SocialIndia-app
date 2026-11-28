package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.*
import com.example.repository.AdvertisingRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAdManagerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingAds by remember { mutableStateOf<List<Advertisement>>(emptyList()) }
    var allAds by remember { mutableStateOf<List<Advertisement>>(emptyList()) }
    var revenueLedger by remember { mutableStateOf<List<PlatformRevenueLedger>>(emptyList()) }
    var adReports by remember { mutableStateOf<List<AdReport>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Admin tabs: 0: Pending Review, 1: All Ads, 2: Revenue Ledger, 3: User Reports
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabLabels = listOf("Pending Review", "All Ads", "Platform Revenue", "Reports")

    var showRejectDialogForAd by remember { mutableStateOf<Advertisement?>(null) }
    var rejectionReasonInput by remember { mutableStateOf("") }

    val reloadData: () -> Unit = {
        scope.launch {
            try {
                isLoading = true
                pendingAds = AdvertisingRepository.getPendingAdsForReview()
                allAds = AdvertisingRepository.getAllAdsForAdmin()
                revenueLedger = AdvertisingRepository.getPlatformRevenueRecords()
                adReports = AdvertisingRepository.getAdReports()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

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
                userEmail = doc.getString("email").orEmpty().trim()
                isUserAdmin = doc.getBoolean("isAdmin") ?: false
            }

            val isMaster = authEmail.equals("abhinandankumar7867@gmail.com", ignoreCase = true) ||
                           userEmail.equals("abhinandankumar7867@gmail.com", ignoreCase = true)

            isAuthorized = isMaster || isUserAdmin
        } catch (e: Exception) {
            val authEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email.orEmpty()
            isAuthorized = authEmail.equals("abhinandankumar7867@gmail.com", ignoreCase = true)
        } finally {
            checkingAuth = false
            if (isAuthorized == true) {
                reloadData()
            }
        }
    }

    if (checkingAuth) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (isAuthorized == false) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Access Denied", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFEE1D52), modifier = Modifier.size(72.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Admin Rights Required", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "You must have administrator privileges to access advertising campaign moderation and financial records.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Advertising Administration", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Moderation, Compliance & Revenue Ledger", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { reloadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabLabels.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                if (index == 0 && pendingAds.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge { Text("${pendingAds.size}") }
                                }
                            }
                        }
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    0 -> {
                        // Pending Ads Tab
                        if (pendingAds.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(54.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("All caught up!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("No advertisements pending approval at this time.", fontSize = 13.sp, color = Color.Gray)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(pendingAds, key = { it.adId }) { ad ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().testTag("pending_ad_${ad.adId}"),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                                    if (ad.advertiserAvatarUrl.isNotEmpty()) {
                                                        AsyncImage(model = ad.advertiserAvatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(ad.advertiserName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Text("Category: ${ad.advertiserCategory} • Format: ${ad.format}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                StatusBadge(status = ad.status)
                                            }

                                            Text("Headline: ${ad.headline}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            if (ad.primaryText.isNotEmpty()) {
                                                Text(ad.primaryText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                            Text("CTA: ${ad.callToAction} ➔ ${ad.destinationUrl}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                                            // Media preview
                                            val media = ad.mediaUrls.firstOrNull() ?: ad.thumbnailUrl
                                            if (media.isNotEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(180.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color.DarkGray)
                                                ) {
                                                    AsyncImage(model = media, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                }
                                            }

                                            // Actions: Approve or Reject
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        showRejectDialogForAd = ad
                                                        rejectionReasonInput = "Policy violation: Misleading claims"
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Reject")
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            AdvertisingRepository.approveAd(ad.adId, ad.campaignId)
                                                            Toast.makeText(context, "Advertisement Approved & Activated!", Toast.LENGTH_SHORT).show()
                                                            reloadData()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Approve & Activate")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // All Ads Tab
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(allAds, key = { it.adId }) { ad ->
                                AdItemCard(
                                    ad = ad,
                                    onPause = {
                                        scope.launch {
                                            AdvertisingRepository.pauseAd(ad.adId)
                                            Toast.makeText(context, "Ad paused", Toast.LENGTH_SHORT).show()
                                            reloadData()
                                        }
                                    },
                                    onResume = {
                                        scope.launch {
                                            AdvertisingRepository.resumeAd(ad.adId)
                                            Toast.makeText(context, "Ad resumed", Toast.LENGTH_SHORT).show()
                                            reloadData()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    2 -> {
                        // Platform Revenue Ledger Tab
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            var totalGross = 0.0
                            var totalNet = 0.0
                            var totalTax = 0.0
                            for (item in revenueLedger) {
                                totalGross += item.grossAmount
                                totalNet += item.platformRevenue
                                totalTax += item.taxAmount
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Platform Financial Ledger", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Total Gross Ad Spend:")
                                        Text("₹${Math.round(totalGross * 100.0) / 100.0}", fontWeight = FontWeight.Bold)
                                    }
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Total Taxes (GST 18%):")
                                        Text("₹${Math.round(totalTax * 100.0) / 100.0}", fontWeight = FontWeight.Bold)
                                    }
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Net Platform Revenue:")
                                        Text("₹${Math.round(totalNet * 100.0) / 100.0}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            Text("Ledger Entries (${revenueLedger.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                            if (revenueLedger.isEmpty()) {
                                Text("No revenue ledger entries generated yet.", fontSize = 13.sp, color = Color.Gray)
                            } else {
                                revenueLedger.forEach { entry ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Text("Txn: ${entry.transactionId}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("Gross: ₹${entry.grossAmount}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                            }
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Text("Platform Net: ₹${entry.platformRevenue}", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                                                Text("Tax: ₹${entry.taxAmount} | Fee: ₹${entry.paymentProcessingFee}", fontSize = 11.sp, color = Color.Gray)
                                            }
                                            Text(
                                                text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(entry.createdAt)),
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // User Ad Reports Tab
                        if (adReports.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No ad reports submitted by users.", color = Color.Gray, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(adReports, key = { it.reportId }) { rep ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Text("Report: ${rep.reason}", fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 14.sp)
                                                Text(rep.status, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text("Ad ID: ${rep.adId}", fontSize = 12.sp, color = Color.Gray)
                                            Text(
                                                text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(rep.timestamp)),
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Rejection Dialog
        showRejectDialogForAd?.let { adToReject ->
            AlertDialog(
                onDismissRequest = { showRejectDialogForAd = null },
                title = { Text("Reject Advertisement", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Provide feedback to the business explaining the policy reason:", fontSize = 13.sp)
                        OutlinedTextField(
                            value = rejectionReasonInput,
                            onValueChange = { rejectionReasonInput = it },
                            label = { Text("Rejection Reason") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                AdvertisingRepository.rejectAd(adToReject.adId, rejectionReasonInput.trim())
                                showRejectDialogForAd = null
                                Toast.makeText(context, "Ad rejected", Toast.LENGTH_SHORT).show()
                                reloadData()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Confirm Rejection")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRejectDialogForAd = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
