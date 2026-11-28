package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CreatorAnalyticsSummary
import com.example.model.MonetizationRequest
import com.example.model.RepositoryManager
import com.example.model.User
import com.example.repository.MonetizationRepository
import com.example.utils.NumberFormatUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonetizationScreen(
    onBack: () -> Unit,
    onNavigateToWallet: () -> Unit = {}
) {
    FacebookCreatorMonetizationView(
        onBack = onBack,
        onNavigateToWallet = onNavigateToWallet
    )
}

@Composable
fun LegacyMonetizationScreen(
    onBack: () -> Unit,
    onNavigateToWallet: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var user by remember { mutableStateOf<User?>(null) }
    var analytics by remember { mutableStateOf(CreatorAnalyticsSummary()) }
    var existingRequest by remember { mutableStateOf<MonetizationRequest?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var showTaxDialog by remember { mutableStateOf(false) }

    val refreshData: () -> Unit = {
        coroutineScope.launch {
            val currentUserId = RepositoryManager.getCurrentUserId()
            if (currentUserId.isNotEmpty()) {
                try {
                    val userDoc = RepositoryManager.firestore.collection("users").document(currentUserId).get().await()
                    user = userDoc.toObject(User::class.java)?.copy(id = userDoc.id)
                    existingRequest = MonetizationRepository.getMonetizationRequest(currentUserId)
                    analytics = MonetizationRepository.getCreatorAnalytics(currentUserId, 30)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFEE1D52))
        }
        return
    }

    val currentUser = user ?: User()
    val isMonetized = currentUser.isMonetized
    val monStatus = when {
        isMonetized -> "Approved"
        existingRequest != null -> existingRequest?.status ?: "Under Review"
        currentUser.monetizationRequested -> "Under Review"
        else -> "Eligible to Apply"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Creator Monetization", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToWallet() }) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Wallet")
                    }
                }
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
            // Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMonetized) Color(0xFF1B5E20).copy(alpha = 0.15f) else Color(0xFFEE1D52).copy(alpha = 0.12f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isMonetized) Color(0xFF4CAF50) else Color(0xFFEE1D52).copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isMonetized) Icons.Default.Verified else Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = if (isMonetized) Color(0xFF4CAF50) else Color(0xFFEE1D52),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Monetization Status",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = monStatus,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMonetized) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (!isMonetized && monStatus != "Under Review") {
                        Button(
                            onClick = { showRequestDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Apply", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Earnings Balance Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
                        )
                    )
                    .padding(22.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Creator Balance", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "INR / USD",
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "₹${NumberFormatUtils.formatCount(analytics.totalEstimatedEarnings.toInt())}.00",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onNavigateToWallet() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F2027)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Wallet & Payout", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { onNavigateToWallet() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("History", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            CreatorEarningsCalculatorCard(onNavigateToWallet = onNavigateToWallet)

            Spacer(modifier = Modifier.height(16.dp))
            SettingsGroupTitle("Monetization Streams")

            IncomeSourceCard(
                icon = Icons.Outlined.PlayCircle,
                title = "In-Stream Video & Reels Ads",
                subtitle = "Revenue from ad impressions on qualified views",
                amount = "₹${analytics.videoAdEarnings}",
                color = Color(0xFF2196F3)
            )

            IncomeSourceCard(
                icon = Icons.Outlined.CardGiftcard,
                title = "Live Stream Gifts & Coins",
                subtitle = "Real-time gifts received from audience during live sessions",
                amount = "₹${analytics.giftValueEarnings}",
                color = Color(0xFFE91E63)
            )

            IncomeSourceCard(
                icon = Icons.Outlined.WorkspacePremium,
                title = "Creator Subscriptions",
                subtitle = "Monthly recurring subscriber support",
                amount = "₹${analytics.subscriptionEarnings}",
                color = Color(0xFF9C27B0)
            )

            IncomeSourceCard(
                icon = Icons.Outlined.Handshake,
                title = "Brand Sponsorships",
                subtitle = "Direct brand collaboration programs",
                amount = "₹0.00",
                color = Color(0xFF4CAF50)
            )

            Spacer(modifier = Modifier.height(16.dp))
            SettingsGroupTitle("Monetization Tools & Policies")

            ActionCard(
                icon = Icons.Outlined.AccountBalance,
                title = "Payout Methods & Bank Setup",
                subtitle = "Configure UPI ID, IFSC, or Bank Account for automated payouts",
                onClick = { onNavigateToWallet() }
            )

            ActionCard(
                icon = Icons.Outlined.ReceiptLong,
                title = "Tax & PAN Information",
                subtitle = "View and verify PAN/Tax documents for compliant payouts",
                onClick = { showTaxDialog = true }
            )

            var showPoliciesDialog by remember { mutableStateOf(false) }

            ActionCard(
                icon = Icons.Outlined.Gavel,
                title = "Creator Monetization Policies",
                subtitle = "Community guidelines, originality rules, and payment terms",
                onClick = { showPoliciesDialog = true }
            )

            if (showPoliciesDialog) {
                AlertDialog(
                    onDismissRequest = { showPoliciesDialog = false },
                    title = { Text("Creator Monetization Policies", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("1. Original Content Only: Uploaded Reels, Posts, and Live Streams must be original or properly licensed.", fontSize = 13.sp)
                            Text("2. Authentic Engagement: Artificial view, like, or follower inflation disqualifies accounts from monetization.", fontSize = 13.sp)
                            Text("3. Payout Verification: All UPI and wallet withdrawals are verified against real 12-digit UTRs and admin approval.", fontSize = 13.sp)
                            Text("4. Community Safety: Content must adhere to Social India safety and copyright standards.", fontSize = 13.sp)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showPoliciesDialog = false }) {
                            Text("Understood", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showRequestDialog) {
        MonetizationRequestDialog(
            user = currentUser,
            totalViews = analytics.totalViews.toInt(),
            onDismiss = { showRequestDialog = false },
            onSubmitted = {
                showRequestDialog = false
                refreshData()
            }
        )
    }

    if (showTaxDialog) {
        AlertDialog(
            onDismissRequest = { showTaxDialog = false },
            title = { Text("Tax Information", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tax Status: Verified Indian Resident", fontSize = 14.sp)
                    Text("TDS Rate: 1% as per applicable sections for digital content creation.", fontSize = 13.sp, color = Color.Gray)
                    Text("To update your PAN or GST details, please submit an update via the Monetization application.", fontSize = 13.sp, color = Color.Gray)
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
fun IncomeSourceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    amount: String,
    color: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
            }
            Text(amount, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun CreatorEarningsCalculatorCard(
    onNavigateToWallet: () -> Unit = {}
) {
    var viewsSliderValue by remember { mutableFloatStateOf(25000f) }
    var followersSliderValue by remember { mutableFloatStateOf(1500f) }
    var selectedEngagementMultiplier by remember { mutableFloatStateOf(1.0f) }
    var isExpandedRateGuide by remember { mutableStateOf(false) }

    val views = viewsSliderValue.toLong()
    val followers = followersSliderValue.toLong()

    // Rates according to MonetizationSettings
    val baseCpm = 40.0 // average of ₹35 (reels) and ₹45 (videos) per 1000 views
    val estimatedAdRevenue = (views / 1000.0) * baseCpm * selectedEngagementMultiplier
    val estimatedGiftEarnings = if (followers >= 1000) (followers * 0.15 * 0.70) else 0.0 // 15% active fans, ₹0.70/gift
    val estimatedEngagementBonus = if (selectedEngagementMultiplier > 1.0f) estimatedAdRevenue * 0.15 else 0.0
    val totalEstimatedEarnings = estimatedAdRevenue + estimatedGiftEarnings + estimatedEngagementBonus

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF4CAF50).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Earnings Calculator",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Views, Likes & Followers Payout Guide",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                    modifier = Modifier.clickable { isExpandedRateGuide = !isExpandedRateGuide }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isExpandedRateGuide) "Hide Rates" else "Rate Chart",
                            color = Color(0xFF2E7D32),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (isExpandedRateGuide) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Explanation / Rate Table if expanded
            if (isExpandedRateGuide) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "📊 Payout & Monetization Rates (कमाई दर)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Views: ₹35 – ₹45 per 1,000 views (1K = ₹40 avg, 100K = ₹4,000, 1M = ₹40,000)\n" +
                                   "• Likes & Engagement: >10% likes unlocks +15% CPM bonus and Explore push\n" +
                                   "• Followers Milestones:\n" +
                                   "   - 100 Followers: Basic monetization unlocked\n" +
                                   "   - 1,000 Followers: Live Gifts & Virtual Badges (70% share)\n" +
                                   "   - 5,000 Followers: Paid monthly subscriptions (₹49/mo)\n" +
                                   "   - 10,000+ Followers: Official Brand sponsorships\n" +
                                   "• Minimum Withdrawal: ₹100 direct to Bank/UPI",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Views Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Monthly Views", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    NumberFormatUtils.formatCount(views.toInt()) + " views",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = viewsSliderValue,
                onValueChange = { viewsSliderValue = it },
                valueRange = 1000f..1000000f,
                steps = 99,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFEE1D52),
                    activeTrackColor = Color(0xFFEE1D52)
                )
            )

            // Followers Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Followers Count", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    NumberFormatUtils.formatCount(followers.toInt()) + " followers",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF2196F3)
                )
            }
            Slider(
                value = followersSliderValue,
                onValueChange = { followersSliderValue = it },
                valueRange = 100f..50000f,
                steps = 49,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF2196F3),
                    activeTrackColor = Color(0xFF2196F3)
                )
            )

            // Engagement / Likes Selector
            Spacer(modifier = Modifier.height(6.dp))
            Text("Likes & Engagement Rate", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    1.0f to "Normal (5%)",
                    1.15f to "Good (10% Likes)",
                    1.25f to "Viral (15%+ Likes)"
                ).forEach { (mult, label) ->
                    val isSelected = selectedEngagementMultiplier == mult
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedEngagementMultiplier = mult },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFFE91E63).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFFE91E63) else Color.Transparent
                        )
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(14.dp))

            // Calculated Estimate Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF4CAF50).copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Estimated Monthly Payout", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                            Text(
                                text = "₹${String.format("%,.0f", totalEstimatedEarnings)}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1B5E20)
                            )
                        }
                        Button(
                            onClick = onNavigateToWallet,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Withdraw", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ad Revenue: ₹${String.format("%,.0f", estimatedAdRevenue)}", fontSize = 11.sp, color = Color.Gray)
                        Text("Gifts: ₹${String.format("%,.0f", estimatedGiftEarnings)}", fontSize = 11.sp, color = Color.Gray)
                        if (estimatedEngagementBonus > 0) {
                            Text("Bonus: +₹${String.format("%,.0f", estimatedEngagementBonus)}", fontSize = 11.sp, color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
