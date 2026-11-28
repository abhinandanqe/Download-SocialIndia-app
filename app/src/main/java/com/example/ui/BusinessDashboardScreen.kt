package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
fun BusinessDashboardScreen(
    onBack: () -> Unit,
    onNavigateToCreateAd: (String?) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentUser by remember { mutableStateOf<User?>(null) }
    var campaigns by remember { mutableStateOf<List<Campaign>>(emptyList()) }
    var ads by remember { mutableStateOf<List<Advertisement>>(emptyList()) }
    var userPosts by remember { mutableStateOf<List<FirebasePost>>(emptyList()) }
    var billingHistory by remember { mutableStateOf<List<BillingTransaction>>(emptyList()) }
    var analytics by remember { mutableStateOf(AdAnalyticsSummary()) }
    var isLoading by remember { mutableStateOf(true) }

    // Dashboard tabs: 0: Overview, 1: Ads & Campaigns, 2: Insights, 3: Budget & Billing, 4: Promotions
    var currentTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Overview", "Ads", "Insights", "Billing", "Promote")

    val loadDashboardData: () -> Unit = {
        scope.launch {
            try {
                val uid = RepositoryManager.getCurrentUserId()
                if (uid.isNotEmpty()) {
                    val userDoc = RepositoryManager.firestore.collection("users").document(uid).get().await()
                    currentUser = userDoc.toObject(User::class.java)?.copy(id = userDoc.id)

                    campaigns = AdvertisingRepository.getAdvertiserCampaigns(uid)
                    ads = AdvertisingRepository.getAdvertiserAds(uid)
                    analytics = AdvertisingRepository.getAdAnalytics(uid)
                    billingHistory = AdvertisingRepository.getBillingTransactions(uid)

                    val postsSnap = RepositoryManager.firestore.collection("posts")
                        .whereEqualTo("userId", uid)
                        .limit(20)
                        .get()
                        .await()
                    userPosts = postsSnap.documents.mapNotNull { it.toObject(FirebasePost::class.java)?.copy(id = it.id) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadDashboardData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Business Dashboard", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = currentUser?.businessName?.ifEmpty { currentUser?.displayName ?: "Enterprise Suite" } ?: "Enterprise Suite",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Business Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToCreateAd(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Create Ad", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("dashboard_create_ad_fab")
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Scrollable or Row Tabs
                PrimaryTabRow(selectedTabIndex = currentTab) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = currentTab == index,
                            onClick = { currentTab = index },
                            text = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                        )
                    }
                }

                when (currentTab) {
                    0 -> {
                        // ================= TAB 0: OVERVIEW =================
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Business Status Card
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (currentUser?.avatarUrl?.isNotEmpty() == true) {
                                            AsyncImage(
                                                model = currentUser?.avatarUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = currentUser?.businessName?.ifEmpty { "Business Account" } ?: "Business Account",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = currentUser?.businessCategory?.ifEmpty { "General Business" } ?: "General Business",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Status: Active Verified Partner",
                                            fontSize = 11.sp,
                                            color = Color(0xFF4CAF50),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onNavigateToSettings,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Edit Profile", fontSize = 11.sp)
                                    }
                                }
                            }

                            // Quick Stats Grid
                            Text("Real Performance Analytics", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricStatCard(
                                    title = "Impressions",
                                    value = "${analytics.totalImpressions}",
                                    subtitle = "Total views",
                                    icon = Icons.Outlined.Visibility,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricStatCard(
                                    title = "Clicks",
                                    value = "${analytics.totalClicks}",
                                    subtitle = "Total actions",
                                    icon = Icons.Outlined.TouchApp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricStatCard(
                                    title = "CTR",
                                    value = "${analytics.clickThroughRate}%",
                                    subtitle = "Click-through rate",
                                    icon = Icons.Outlined.AdsClick,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricStatCard(
                                    title = "Spend",
                                    value = "₹${analytics.totalSpend}",
                                    subtitle = "Total investment",
                                    icon = Icons.Outlined.AccountBalanceWallet,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Active Campaigns section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Recent Campaigns (${campaigns.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                TextButton(onClick = { currentTab = 1 }) {
                                    Text("View All")
                                }
                            }

                            if (campaigns.isEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Outlined.Campaign, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No ad campaigns yet", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("Launch your first advertisement to reach real customers across Social India.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(onClick = { onNavigateToCreateAd(null) }) {
                                            Text("Create First Ad")
                                        }
                                    }
                                }
                            } else {
                                campaigns.take(3).forEach { camp ->
                                    CampaignItemRow(campaign = camp)
                                }
                            }

                            Spacer(modifier = Modifier.height(64.dp))
                        }
                    }

                    1 -> {
                        // ================= TAB 1: ADS & CAMPAIGNS =================
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("All Advertisements (${ads.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                IconButton(onClick = { loadDashboardData() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                }
                            }

                            if (ads.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Outlined.PostAdd, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No advertisements created yet", fontWeight = FontWeight.Medium)
                                        Text("Tap 'Create Ad' to launch a Photo, Video, Story, Reel, or Carousel Ad", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            } else {
                                ads.forEach { adItem ->
                                    AdItemCard(
                                        ad = adItem,
                                        onPause = {
                                            scope.launch {
                                                AdvertisingRepository.pauseAd(adItem.adId)
                                                Toast.makeText(context, "Ad paused", Toast.LENGTH_SHORT).show()
                                                loadDashboardData()
                                            }
                                        },
                                        onResume = {
                                            scope.launch {
                                                AdvertisingRepository.resumeAd(adItem.adId)
                                                Toast.makeText(context, "Ad resumed", Toast.LENGTH_SHORT).show()
                                                loadDashboardData()
                                            }
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(64.dp))
                        }
                    }

                    2 -> {
                        // ================= TAB 2: INSIGHTS =================
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Audience & Interaction Insights", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Estimated Unique Reach", fontSize = 14.sp)
                                        Text("${analytics.totalReach}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    HorizontalDivider()
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Total Engagements", fontSize = 14.sp)
                                        Text("${analytics.totalEngagements}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    HorizontalDivider()
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Profile Visits Driven", fontSize = 14.sp)
                                        Text("${analytics.profileVisits}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    HorizontalDivider()
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Outbound Website Clicks", fontSize = 14.sp)
                                        Text("${analytics.websiteClicks}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    HorizontalDivider()
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Active Campaigns", fontSize = 14.sp)
                                        Text("${analytics.activeCampaignsCount}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    HorizontalDivider()
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Ads In Policy Review", fontSize = 14.sp)
                                        Text("${analytics.pendingAdsCount}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }

                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Social India analytics reflect true viewability events and verified clicks. No artificial metrics or synthetic bots.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(64.dp))
                        }
                    }

                    3 -> {
                        // ================= TAB 3: BUDGET & BILLING =================
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Billing & Platform Ledger", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Payment Gateway Configuration", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Enterprise Direct Ledger Mode (Active)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Text(
                                        "Charges and credits are reconciled with server-side tax (GST 18%) and PG processing fees.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text("Billing Transactions & Invoices (${billingHistory.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                            if (billingHistory.isEmpty()) {
                                Text("No charges or transactions recorded yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                billingHistory.forEach { txn ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Text(txn.invoiceNumber.ifEmpty { txn.transactionId }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text("₹${txn.amount} ${txn.currency}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                                            }
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Text("Method: ${txn.paymentMethod}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    text = txn.status,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (txn.status == "SUCCESS") Color(0xFF4CAF50) else Color(0xFFFFA000)
                                                )
                                            }
                                            Text(
                                                text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(txn.createdAt)),
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(64.dp))
                        }
                    }

                    4 -> {
                        // ================= TAB 4: PROMOTE POSTS =================
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Promote Your Business Posts", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                "Boost organic engagement by turning your top-performing posts into targeted advertisements across India.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (userPosts.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No posts available to promote. Create posts on your profile first!", fontSize = 13.sp, color = Color.Gray)
                                }
                            } else {
                                userPosts.forEach { post ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(70.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.DarkGray)
                                            ) {
                                                AsyncImage(
                                                    model = post.postUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = post.caption.ifEmpty { "Post without caption" },
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 13.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("❤️ ${post.likesCount}", fontSize = 11.sp, color = Color.Gray)
                                                    Text("💬 ${post.commentsCount}", fontSize = 11.sp, color = Color.Gray)
                                                    Text("👁️ ${post.viewsCount}", fontSize = 11.sp, color = Color.Gray)
                                                }
                                            }
                                            Button(
                                                onClick = { onNavigateToCreateAd(post.id) },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Promote", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(64.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun CampaignItemRow(campaign: Campaign) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(campaign.name.ifEmpty { "Ad Campaign" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Objective: ${CampaignObjective.getDisplayName(campaign.objective)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Budget: ₹${campaign.budgetAmount} (${campaign.budgetType})", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            StatusBadge(status = campaign.status)
        }
    }
}

@Composable
fun AdItemCard(
    ad: Advertisement,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray)) {
                    val media = ad.mediaUrls.firstOrNull() ?: ad.thumbnailUrl
                    if (media.isNotEmpty()) {
                        AsyncImage(model = media, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Outlined.Campaign, contentDescription = null, modifier = Modifier.align(Alignment.Center))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(ad.headline.ifEmpty { "Advertisement" }, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Format: ${ad.format} • CTA: ${ad.callToAction}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Views: ${ad.impressionsCount} • Clicks: ${ad.clicksCount} • Spend: ₹${ad.spendAmount}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
                StatusBadge(status = ad.status)
            }

            if (ad.status == AdStatus.REJECTED && ad.rejectionReason.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Rejection Reason: ${ad.rejectionReason}", color = Color.Red, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (ad.status == AdStatus.ACTIVE) {
                    OutlinedButton(onClick = onPause, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("Pause Ad", fontSize = 12.sp)
                    }
                } else if (ad.status == AdStatus.PAUSED) {
                    Button(onClick = onResume, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("Resume Ad", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (bgColor, textColor, label) = when (status) {
        AdStatus.ACTIVE -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Active")
        AdStatus.UNDER_REVIEW -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "Under Review")
        AdStatus.SUBMITTED -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "Submitted")
        AdStatus.PAUSED -> Triple(Color(0xFFECEFF1), Color(0xFF546E7A), "Paused")
        AdStatus.REJECTED -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Rejected")
        AdStatus.COMPLETED -> Triple(Color(0xFFEDE7F6), Color(0xFF4527A0), "Completed")
        else -> Triple(Color(0xFFEEEEEE), Color(0xFF616161), status)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = label, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
