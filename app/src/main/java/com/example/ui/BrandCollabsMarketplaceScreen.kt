package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CollabBooking
import com.example.model.CreatorRateCard
import com.example.model.RepositoryManager
import com.example.repository.ProCreatorSuiteRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandCollabsMarketplaceScreen(
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = remember { RepositoryManager.getCurrentUserId() }
    val currentUser by RepositoryManager.currentUserState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0=Creators, 1=My Rate Card, 2=Orders & Escrow
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var bookingCreator by remember { mutableStateOf<CreatorRateCard?>(null) }

    // 100% Real-Time Firestore Listeners
    val creators by remember {
        ProCreatorSuiteRepository.observeCreatorRateCards()
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val myBookings by remember(currentUserId) {
        ProCreatorSuiteRepository.observeMyCollabBookings(currentUserId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val filteredCreators = remember(creators, searchQuery, selectedFilter) {
        creators.filter { card ->
            val matchesQuery = searchQuery.isBlank() ||
                card.username.contains(searchQuery, ignoreCase = true) ||
                card.displayName.contains(searchQuery, ignoreCase = true) ||
                card.niche.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "Verified ✓" -> card.isVerified
                else -> card.niche.contains(selectedFilter, ignoreCase = true)
            }
            matchesQuery && matchesFilter
        }
    }

    // My Rate Card editor state synced from live creator list
    var rateCardEnabled by remember { mutableStateOf(true) }
    var niche by remember { mutableStateOf("Reels & Lifestyle") }
    var bioNote by remember { mutableStateOf("Fast turnaround Story shoutouts, Reel promos & collabs") }
    var storyPrice by remember { mutableStateOf("150") }
    var reelPrice by remember { mutableStateOf("400") }
    var postPrice by remember { mutableStateOf("250") }
    var turnaroundDays by remember { mutableStateOf("2") }
    var isSavingRateCard by remember { mutableStateOf(false) }
    var initializedMyCard by remember { mutableStateOf(false) }

    LaunchedEffect(creators, currentUserId) {
        if (!initializedMyCard && currentUserId.isNotBlank()) {
            creators.firstOrNull { it.userId == currentUserId }?.let { mine ->
                rateCardEnabled = mine.enabled
                niche = mine.niche
                bioNote = mine.bioNote
                storyPrice = mine.storyShoutoutCoins.toString()
                reelPrice = mine.reelPromoCoins.toString()
                postPrice = mine.collabPostCoins.toString()
                turnaroundDays = mine.turnaroundDays.toString()
                initializedMyCard = true
            }
        }
    }

    val myAvailableCoins = (currentUser?.coins ?: 0L) + (currentUser?.walletBalance?.toLong() ?: 0L)

    Scaffold(
        containerColor = Color(0xFF07090F),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Brand Collabs Studio",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Real-Time Marketplace • Escrow & Direct Deals",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("collabs_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF1F1908),
                        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.55f)),
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Text(
                            text = "🪙 $myAvailableCoins",
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF07090F))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0A1416), Color(0xFF07090F), Color(0xFF0D0B18)))
                )
                .padding(padding)
                .navigationBarsPadding()
        ) {
            // Escrow Protection Hero Banner
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF0D2820), Color(0xFF14182B))),
                        RoundedCornerShape(18.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = "Escrow", tint = Color(0xFF34D399), modifier = Modifier.size(24.dp))
                    Text(
                        text = "Escrow Protected & Direct Pitch: Lock coins safely OR send a Free Collab Inquiry to any creator instantly.",
                        color = Color(0xFFD1FAE5),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            // Tab Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    0 to "Creators (${creators.size})",
                    1 to "My Rate Card",
                    2 to "Deals (${myBookings.size})"
                ).forEach { (idx, title) ->
                    val active = selectedTab == idx
                    Surface(
                        onClick = { selectedTab = idx },
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) Color(0xFF10B981) else Color(0xFF161826),
                        border = BorderStroke(1.dp, if (active) Color(0xFF34D399) else Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("collabs_tab_$idx")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = title,
                                color = if (active) Color.Black else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // Search + Filter Chips
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search creators, niche, Reels...", color = Color(0xFF8A8AA6), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF34D399)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(listOf("All", "Verified ✓", "Reels", "Lifestyle", "Fashion", "Tech", "Comedy", "Music")) { chip ->
                            val selected = selectedFilter == chip
                            FilterChip(
                                selected = selected,
                                onClick = { selectedFilter = chip },
                                label = { Text(chip, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF10B981),
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0xFF151726),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    if (filteredCreators.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No matching creators found", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Try clearing filters or publish your own Rate Card!", color = Color(0xFF9A9AB2), fontSize = 12.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filteredCreators, key = { it.userId }) { card ->
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = Color(0xFF131524),
                                    border = BorderStroke(
                                        1.dp,
                                        if (card.isVerified) Color(0xFFFFD700).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            CosmeticAvatarFrameRing(
                                                frameId = if (card.isVerified) "frame_royal_gold" else "frame_cyber_neon",
                                                avatarUrl = card.avatarUrl,
                                                size = 50.dp,
                                                onClick = { onNavigateToProfile(card.userId) }
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "@${card.username}",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 15.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (card.isVerified) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        VerificationBadge(isVerified = true, badgeUrl = card.verificationBadgeUrl, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                                Text(
                                                    text = "${card.niche} • ${card.followersCount} followers • ${card.turnaroundDays}d delivery",
                                                    color = Color(0xFF9DE6C5),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = card.bioNote,
                                            color = Color(0xFFD0D0E2),
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            RateChip("Story", card.storyShoutoutCoins, Modifier.weight(1f))
                                            RateChip("Reel Promo", card.reelPromoCoins, Modifier.weight(1f))
                                            RateChip("Collab", card.collabPostCoins, Modifier.weight(1f))
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                if (card.userId == currentUserId) {
                                                    selectedTab = 1
                                                } else {
                                                    bookingCreator = card
                                                }
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (card.userId == currentUserId) Color(0xFF28283D) else Color(0xFF10B981)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(46.dp)
                                                .testTag("book_creator_${card.userId}")
                                        ) {
                                            Icon(
                                                if (card.userId == currentUserId) Icons.Default.Campaign else Icons.Default.Handshake,
                                                contentDescription = null,
                                                tint = if (card.userId == currentUserId) Color.White else Color.Black,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (card.userId == currentUserId) "Edit My Rate Card" else "Book / Send Collab Deal",
                                                color = if (card.userId == currentUserId) Color.White else Color.Black,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = Color(0xFF141524),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.28f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Accept Brand Bookings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text("Live in Social India Brand Marketplace", color = Color(0xFF9A9AB2), fontSize = 11.sp)
                                        }
                                        Switch(checked = rateCardEnabled, onCheckedChange = { rateCardEnabled = it })
                                    }

                                    OutlinedTextField(
                                        value = niche,
                                        onValueChange = { niche = it },
                                        label = { Text("Creator Niche / Category") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = bioNote,
                                        onValueChange = { bioNote = it },
                                        label = { Text("Offer Description for Brands") },
                                        minLines = 2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = storyPrice,
                                            onValueChange = { storyPrice = it.filter(Char::isDigit) },
                                            label = { Text("Story 🪙") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = reelPrice,
                                            onValueChange = { reelPrice = it.filter(Char::isDigit) },
                                            label = { Text("Reel 🪙") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = postPrice,
                                            onValueChange = { postPrice = it.filter(Char::isDigit) },
                                            label = { Text("Collab 🪙") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    OutlinedTextField(
                                        value = turnaroundDays,
                                        onValueChange = { turnaroundDays = it.filter(Char::isDigit) },
                                        label = { Text("Delivery Turnaround (Days)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            if (isSavingRateCard) return@Button
                                            isSavingRateCard = true
                                            scope.launch {
                                                val res = ProCreatorSuiteRepository.saveMyRateCard(
                                                    userId = currentUserId,
                                                    enabled = rateCardEnabled,
                                                    niche = niche,
                                                    bioNote = bioNote,
                                                    storyCoins = storyPrice.toIntOrNull() ?: 150,
                                                    reelCoins = reelPrice.toIntOrNull() ?: 400,
                                                    postCoins = postPrice.toIntOrNull() ?: 250,
                                                    turnaroundDays = turnaroundDays.toIntOrNull() ?: 2
                                                )
                                                isSavingRateCard = false
                                                res.onSuccess {
                                                    Toast.makeText(context, "✅ Rate Card live in Marketplace!", Toast.LENGTH_SHORT).show()
                                                    selectedTab = 0
                                                }.onFailure { err ->
                                                    Toast.makeText(context, err.message ?: "Save failed", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        enabled = !isSavingRateCard,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("save_rate_card_btn")
                                    ) {
                                        Text(
                                            text = if (isSavingRateCard) "Publishing..." else "Save & Publish Rate Card",
                                            color = Color.Black,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    if (myBookings.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Icon(Icons.Default.Handshake, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No Collab Deals Yet", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Book a creator shoutout or send a free collab inquiry!", color = Color(0xFF9A9AB2), fontSize = 12.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(myBookings, key = { it.id }) { booking ->
                                CollabBookingCard(
                                    booking = booking,
                                    isCreator = booking.creatorId == currentUserId,
                                    onUpdateStatus = { newStatus ->
                                        scope.launch {
                                            val res = ProCreatorSuiteRepository.updateCollabBookingStatus(booking.id, newStatus)
                                            res.onSuccess {
                                                val msg = when (newStatus) {
                                                    "COMPLETED" -> "💸 Deal completed & payout credited!"
                                                    "DECLINED" -> "↩️ Declined & refunded to buyer."
                                                    "DELIVERED" -> "📦 Marked Delivered! Waiting for brand release."
                                                    else -> "✅ Deal updated to $newStatus"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }.onFailure { err ->
                                                Toast.makeText(context, err.message ?: "Action failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    bookingCreator?.let { target ->
        BookShoutoutEscrowDialog(
            creator = target,
            availableCoins = myAvailableCoins,
            onDismiss = { bookingCreator = null },
            onConfirmBooking = { pkgType, brief, price ->
                scope.launch {
                    val res = ProCreatorSuiteRepository.createEscrowCollabBooking(
                        brandUserId = currentUserId,
                        brandUsername = currentUser?.username?.ifBlank { "Brand" } ?: "Brand",
                        brandAvatar = currentUser?.avatarUrl ?: "",
                        creator = target,
                        packageType = pkgType,
                        brief = brief,
                        amountCoins = price
                    )
                    res.onSuccess {
                        bookingCreator = null
                        selectedTab = 2
                        Toast.makeText(context, "🔒 $price coins locked in Escrow! Request sent to @${target.username}.", Toast.LENGTH_LONG).show()
                    }.onFailure { err ->
                        Toast.makeText(context, err.message ?: "Booking failed", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onSendFreeInquiry = { pkgType, brief ->
                scope.launch {
                    val res = ProCreatorSuiteRepository.sendDirectCollabInquiry(
                        brandUserId = currentUserId,
                        brandUsername = currentUser?.username?.ifBlank { "Brand" } ?: "Brand",
                        brandAvatar = currentUser?.avatarUrl ?: "",
                        creator = target,
                        packageType = pkgType,
                        brief = brief
                    )
                    res.onSuccess {
                        bookingCreator = null
                        selectedTab = 2
                        Toast.makeText(context, "📩 Collab inquiry sent live to @${target.username}!", Toast.LENGTH_LONG).show()
                    }.onFailure { err ->
                        Toast.makeText(context, err.message ?: "Inquiry failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun RateChip(title: String, coins: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFF1C1E33), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, color = Color(0xFF9A9AB2), fontSize = 10.sp)
        Text(text = "🪙 $coins", color = Color(0xFFFFD700), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    }
}

@Composable
private fun CollabBookingCard(
    booking: CollabBooking,
    isCreator: Boolean,
    onUpdateStatus: (String) -> Unit
) {
    val statusColor = when (booking.status) {
        "COMPLETED" -> Color(0xFF10B981)
        "DECLINED" -> Color(0xFFEF4444)
        "DELIVERED" -> Color(0xFF38BDF8)
        "ACCEPTED" -> Color(0xFFA78BFA)
        "INQUIRY_SENT" -> Color(0xFF00F0FF)
        else -> Color(0xFFFFD700)
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF141524),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCreator) "Incoming from @${booking.brandUsername}" else "Booked @${booking.creatorUsername}",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = booking.status.replace("_", " "),
                        color = statusColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = if (booking.amountCoins > 0) {
                    "${booking.packageType.replace("_", " ")} • 🪙 ${booking.amountCoins} Coins (Escrow)"
                } else {
                    "${booking.packageType.replace("_", " ")} • Direct Collab Proposal"
                },
                color = Color(0xFFFFD700),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = booking.brief,
                color = Color(0xFFD0D0E2),
                fontSize = 12.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isCreator && (booking.status == "ESCROW_HELD" || booking.status == "INQUIRY_SENT")) {
                    Button(
                        onClick = { onUpdateStatus("ACCEPTED") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Accept", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onUpdateStatus("DECLINED") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (booking.amountCoins > 0) "Decline & Refund" else "Decline", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                } else if (isCreator && booking.status == "ACCEPTED") {
                    Button(
                        onClick = { onUpdateStatus("DELIVERED") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mark Campaign Delivered 📦", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                    }
                } else if (!isCreator && (booking.status == "DELIVERED" || booking.status == "ACCEPTED")) {
                    Button(
                        onClick = { onUpdateStatus("COMPLETED") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (booking.amountCoins > 0) "Approve & Release Escrow 💸" else "Mark Deal Completed ✅",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookShoutoutEscrowDialog(
    creator: CreatorRateCard,
    availableCoins: Long,
    onDismiss: () -> Unit,
    onConfirmBooking: (packageType: String, brief: String, amountCoins: Int) -> Unit,
    onSendFreeInquiry: (packageType: String, brief: String) -> Unit
) {
    var selectedPkg by remember { mutableStateOf("STORY_SHOUTOUT") }
    var brief by remember { mutableStateOf("") }
    val selectedPrice = when (selectedPkg) {
        "REEL_PROMO" -> creator.reelPromoCoins
        "COLLAB_POST" -> creator.collabPostCoins
        else -> creator.storyShoutoutCoins
    }
    val hasEnoughCoins = availableCoins >= selectedPrice

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151726),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFD0D0E2),
        title = {
            Column {
                Text("Collab with @${creator.username}", fontWeight = FontWeight.ExtraBold)
                Text("Your Balance: 🪙 $availableCoins Coins", color = Color(0xFFFFD700), fontSize = 12.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select Package:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple("STORY_SHOUTOUT", "24h Story Shoutout", creator.storyShoutoutCoins),
                        Triple("REEL_PROMO", "Dedicated Reel Promo", creator.reelPromoCoins),
                        Triple("COLLAB_POST", "Co-Authored Collab Post", creator.collabPostCoins)
                    ).forEach { (key, label, price) ->
                        val active = selectedPkg == key
                        Surface(
                            onClick = { selectedPkg = key },
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) Color(0xFF123026) else Color(0xFF1E2034),
                            border = BorderStroke(1.dp, if (active) Color(0xFF10B981) else Color.White.copy(alpha = 0.08f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("🪙 $price", color = Color(0xFFFFD700), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = brief,
                    onValueChange = { brief = it },
                    label = { Text("Campaign Brief / Tag / Link") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!hasEnoughCoins) {
                    Text(
                        text = "Tip: You can send a Free Collab Inquiry now and finalize coins later!",
                        color = Color(0xFF00F0FF),
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!hasEnoughCoins) {
                    Button(
                        onClick = { onSendFreeInquiry(selectedPkg, brief) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF))
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send Free Inquiry", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = { onConfirmBooking(selectedPkg, brief, selectedPrice) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Lock $selectedPrice 🪙 Escrow", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFA0A0B8))
            }
        }
    )
}
